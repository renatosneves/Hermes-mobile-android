package com.m57.hermescontrol.ui.fleet

import kotlin.random.Random

/**
 * Demo data source for the Fleet screen: a small, deterministic simulation of bots
 * receiving work, handing it to each other, asking the user for sign-off and finishing.
 *
 * It exists so the Fleet UI can be built and reviewed before the live dashboard feed
 * (Kanban + sessions) is wired in. All state lives on a simulation clock advanced by
 * [tick], so tests can drive it without real time.
 */
class FleetSimulator(
    private val random: Random = Random(SEED),
    val bots: List<FleetBot> = DEMO_BOTS,
    private val templates: List<TaskTemplate> = DEMO_TEMPLATES,
) {
    data class TaskTemplate(
        val title: String,
        val route: List<String>,
        val steps: List<String>,
        val ask: FleetAsk? = null,
    )

    private var clockMs = 0L
    private var nextSpawnMs = FIRST_SPAWN_MS
    private var nextId = 1L
    private var doneToday = SEED_DONE_TODAY
    private val tasks = mutableListOf<FleetTask>()
    private val packets = mutableListOf<FleetPacket>()
    private val events = ArrayDeque<FleetEvent>()

    private val hub: String = bots.first { it.isHub }.id

    init {
        seed()
    }

    fun snapshot(): FleetSnapshot =
        FleetSnapshot(
            bots = bots,
            tasks = tasks.toList(),
            packets = packets.toList(),
            events = events.toList(),
            clockMs = clockMs,
            doneToday = doneToday,
        )

    fun tick(deltaMs: Long) {
        clockMs += deltaMs
        packets.removeAll { clockMs > it.startMs + it.durationMs + PACKET_LINGER_MS }
        if (clockMs >= nextSpawnMs) {
            spawnRandom()
            nextSpawnMs = clockMs + random.nextLong(SPAWN_MIN_MS, SPAWN_MAX_MS)
        }
        tasks.toList().forEach { task ->
            when (task.column) {
                FleetColumn.QUEUED -> {
                    if (clockMs >= task.nextAtMs) startWork(task)
                }

                FleetColumn.WORKING -> {
                    val progress = ((clockMs - task.stepStartMs).toFloat() / task.stepDurationMs).coerceIn(0f, 1f)
                    val updated = task.copy(progress = progress)
                    replace(updated)
                    if (progress >= 1f) finishStep(updated)
                }

                FleetColumn.NEEDS_YOU -> {
                    if (clockMs >= task.nextAtMs) resolve(task.id, approved = true, auto = true)
                }

                FleetColumn.DONE -> {
                    Unit
                }
            }
        }
    }

    /** User approved the task's ask. Returns false if the task is not waiting. */
    fun approve(taskId: Long): Boolean = resolve(taskId, approved = true, auto = false)

    /** User sent the draft back for another pass. Returns false if the task is not waiting. */
    fun sendBack(taskId: Long): Boolean = resolve(taskId, approved = false, auto = false)

    /** Queues a task from a template, as if the user had asked for it. */
    fun submit(template: TaskTemplate): FleetTask {
        val task =
            FleetTask(
                id = nextId++,
                title = template.title,
                route = template.route,
                steps = template.steps,
                ask = template.ask,
                nextAtMs = clockMs + QUEUE_DELAY_MS,
            )
        tasks += task
        fly(FLEET_YOU_ID, FLEET_ROUTER_ID, null, delayMs = 0)
        fly(FLEET_ROUTER_ID, hub, null, delayMs = HOP_DELAY_MS)
        if (task.owner != hub) fly(hub, task.owner, hueOf(task.owner), delayMs = HOP_DELAY_MS * 2)
        log(FeedPart.Plain("You asked: "), FeedPart.Strong(task.title), FeedPart.Plain(" → "), FeedPart.Bot(task.owner))
        return task
    }

    private fun spawnRandom() {
        val live = tasks.filter { it.column != FleetColumn.DONE }
        if (live.size >= MAX_LIVE_TASKS) return
        val busyTitles = live.map { it.title }.toSet()
        val options = templates.filter { it.title !in busyTitles }
        if (options.isEmpty()) return
        submit(options[random.nextInt(options.size)])
    }

    private fun startWork(task: FleetTask) {
        replace(
            task.copy(
                column = FleetColumn.WORKING,
                stepStartMs = clockMs,
                stepDurationMs = random.nextLong(STEP_MIN_MS, STEP_MAX_MS),
                progress = 0f,
            ),
        )
        log(FeedPart.Bot(task.owner), FeedPart.Plain(" picked up "), FeedPart.Strong(task.title))
    }

    private fun finishStep(task: FleetTask) {
        if (task.hop < task.route.lastIndex) {
            val from = task.owner
            val next =
                task.copy(
                    hop = task.hop + 1,
                    stepStartMs = clockMs,
                    stepDurationMs = random.nextLong(STEP_MIN_MS, STEP_MAX_MS),
                    progress = 0f,
                )
            replace(next)
            fly(from, next.owner, hueOf(next.owner), delayMs = 0)
            log(
                FeedPart.Bot(from),
                FeedPart.Plain(" → "),
                FeedPart.Bot(next.owner),
                FeedPart.Plain(": ${next.currentStep}"),
            )
            return
        }
        if (task.ask != null) {
            replace(task.copy(column = FleetColumn.NEEDS_YOU, nextAtMs = clockMs + AUTO_APPROVE_MS))
            fly(task.owner, FLEET_YOU_ID, null, delayMs = 0)
            log(FeedPart.Bot(task.owner), FeedPart.Plain(" needs you: "), FeedPart.Strong(task.ask.action.lowercase()))
            return
        }
        complete(task, null)
        log(FeedPart.Bot(task.owner), FeedPart.Plain(" finished "), FeedPart.Strong(task.title))
    }

    private fun resolve(
        taskId: Long,
        approved: Boolean,
        auto: Boolean,
    ): Boolean {
        val task = tasks.firstOrNull { it.id == taskId && it.column == FleetColumn.NEEDS_YOU } ?: return false
        fly(FLEET_YOU_ID, task.owner, hueOf(task.owner), delayMs = 0)
        if (approved) {
            complete(task, if (auto) "Auto-approved (demo)" else "Approved by you")
            if (auto) {
                log(FeedPart.Plain("Auto-approved for the demo: "), FeedPart.Strong(task.title))
            } else {
                log(
                    FeedPart.Plain("You approved "),
                    FeedPart.Strong(task.title),
                    FeedPart.Plain(" → "),
                    FeedPart.Bot(task.owner),
                )
            }
        } else {
            replace(
                task.copy(
                    column = FleetColumn.WORKING,
                    stepStartMs = clockMs,
                    stepDurationMs = random.nextLong(STEP_MIN_MS, STEP_MAX_MS),
                    progress = 0f,
                ),
            )
            log(
                FeedPart.Plain("You sent "),
                FeedPart.Strong(task.title),
                FeedPart.Plain(" back to "),
                FeedPart.Bot(task.owner),
            )
        }
        return true
    }

    private fun complete(
        task: FleetTask,
        outcome: String?,
    ) {
        replace(task.copy(column = FleetColumn.DONE, progress = 1f, doneAtMs = clockMs, outcome = outcome))
        doneToday++
        val done = tasks.filter { it.column == FleetColumn.DONE }.sortedByDescending { it.doneAtMs }
        done.drop(MAX_DONE_SHOWN).forEach { old -> tasks.removeAll { it.id == old.id } }
    }

    private fun replace(task: FleetTask) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = task
    }

    private fun fly(
        from: String,
        to: String,
        hueIndex: Int?,
        delayMs: Long,
    ) {
        packets += FleetPacket(nextId++, from, to, hueIndex, clockMs + delayMs, PACKET_MS)
    }

    private fun hueOf(botId: String): Int? = bots.firstOrNull { it.id == botId }?.hueIndex

    private fun log(vararg parts: FeedPart) {
        events.addFirst(FleetEvent(nextId++, clockMs, parts.toList()))
        while (events.size > MAX_EVENTS) events.removeLast()
    }

    private fun seed() {
        fun template(title: String) = templates.first { it.title == title }

        fun add(
            title: String,
            column: FleetColumn,
            hop: Int = 0,
            progress: Float = 0f,
        ) {
            val t = template(title)
            tasks +=
                FleetTask(
                    id = nextId++,
                    title = t.title,
                    route = t.route,
                    steps = t.steps,
                    ask = t.ask,
                    hop = hop,
                    column = column,
                    progress = progress,
                    nextAtMs = if (column == FleetColumn.NEEDS_YOU) SEED_WAIT_MS else QUEUE_DELAY_MS,
                    stepStartMs = -(progress * SEED_STEP_MS).toLong(),
                    stepDurationMs = SEED_STEP_MS,
                    doneAtMs = -1,
                )
        }
        add("Find 2025 tax receipt", FleetColumn.DONE, progress = 1f)
        add("Clear newsletters from inbox", FleetColumn.DONE, progress = 1f)
        add("Portfolio check before open", FleetColumn.WORKING, progress = 0.55f)
        add("Tuesday meeting pack", FleetColumn.WORKING, hop = 1, progress = 0.2f)
        add("Reply to landlord about boiler", FleetColumn.NEEDS_YOU)
        add("Lunch plan under 700 kcal", FleetColumn.QUEUED)
        log(FeedPart.Bot("inbox"), FeedPart.Plain(" needs you: "), FeedPart.Strong("send reply"))
        log(
            FeedPart.Bot("cos"),
            FeedPart.Plain(" → "),
            FeedPart.Bot("work"),
            FeedPart.Plain(": pulling agenda from Outlook"),
        )
        log(FeedPart.Bot("ledger"), FeedPart.Plain(" picked up "), FeedPart.Strong("Portfolio check before open"))
    }

    companion object {
        const val SEED = 42
        const val TICK_MS = 50L
        const val PACKET_MS = 900L
        private const val PACKET_LINGER_MS = 400L
        private const val HOP_DELAY_MS = 650L
        private const val FIRST_SPAWN_MS = 800L
        private const val SPAWN_MIN_MS = 3_500L
        private const val SPAWN_MAX_MS = 6_000L
        private const val QUEUE_DELAY_MS = 1_600L
        private const val STEP_MIN_MS = 3_500L
        private const val STEP_MAX_MS = 6_500L
        private const val SEED_STEP_MS = 5_000L
        private const val SEED_WAIT_MS = 30_000L
        private const val AUTO_APPROVE_MS = 30_000L
        private const val MAX_LIVE_TASKS = 7
        private const val MAX_DONE_SHOWN = 5
        private const val MAX_EVENTS = 30
        private const val SEED_DONE_TODAY = 11

        val DEMO_BOTS =
            listOf(
                FleetBot("cos", "Chief of Staff", "CoS", "Plans, splits and hands out work", 0, isHub = true),
                FleetBot("ask", "Ask", "As", "Research and quick answers", 1),
                FleetBot("work", "Work", "Wk", "SKF work, meetings, travel", 2),
                FleetBot("hands", "Hands", "Hd", "Uses your Mac and Chrome", 3),
                FleetBot("link", "Link", "Ln", "X and LinkedIn posts", 4),
                FleetBot("inbox", "Inbox", "In", "Gmail triage and replies", 5),
                FleetBot("vault", "Vault", "Vt", "Files and documents", 6),
                FleetBot("roam", "Roam", "Rm", "Trips, flights, hotels", 7),
                FleetBot("nutri", "Nutri", "Nu", "Meals and nutrition", 8),
                FleetBot("psite", "PSite", "PS", "Your personal website", 9),
                FleetBot("spend", "Spend", "Sp", "Card spend and budgets", 10),
                FleetBot("ledger", "Ledger", "Lg", "Portfolio and net worth", 11),
            )

        val DEMO_TEMPLATES =
            listOf(
                TaskTemplate(
                    "Tuesday meeting pack",
                    listOf("cos", "work", "vault"),
                    listOf("splitting the ask", "pulling agenda from Outlook", "finding last quarter notes"),
                ),
                TaskTemplate(
                    "X post: weekend long read",
                    listOf("cos", "link"),
                    listOf("routing", "drafting 2 variants"),
                    FleetAsk(
                        "Approve post text",
                        "\"Reliability isn't a feature you add later. Three lessons from a year of bearing data…\"",
                    ),
                ),
                TaskTemplate(
                    "Reply to landlord about boiler",
                    listOf("inbox"),
                    listOf("drafting reply"),
                    FleetAsk("Send reply", "\"Thanks, Thursday 10:00 works. I'll leave the key with the concierge.\""),
                ),
                TaskTemplate("Portfolio check before open", listOf("ledger"), listOf("fetching Yahoo prices")),
                TaskTemplate(
                    "Card spend this week",
                    listOf("spend", "ledger"),
                    listOf("categorising 23 transactions", "updating net worth"),
                ),
                TaskTemplate(
                    "Gothenburg hotel options",
                    listOf("cos", "roam"),
                    listOf("routing", "comparing 14 hotels"),
                    FleetAsk("Pick a hotel", "3 options near SKF HQ, €142–€188 a night, all free cancellation"),
                ),
                TaskTemplate("Sync latest X posts to site", listOf("psite"), listOf("building page")),
                TaskTemplate("Find 2025 tax receipt", listOf("vault"), listOf("searching 4 folders")),
                TaskTemplate("Lunch plan under 700 kcal", listOf("nutri"), listOf("checking the fridge list")),
                TaskTemplate(
                    "SKF expense form on the Mac",
                    listOf("cos", "work", "hands"),
                    listOf("routing", "matching receipts", "filling the form in Chrome"),
                    FleetAsk("Confirm submit", "€312.40 across 4 receipts, cost centre 7713"),
                ),
                TaskTemplate("Summarise this week's AI news", listOf("ask"), listOf("reading 18 sources")),
                TaskTemplate("Clear newsletters from inbox", listOf("inbox"), listOf("trashing 41 newsletters")),
                TaskTemplate(
                    "Flight options Lisbon in Nov",
                    listOf("cos", "roam", "spend"),
                    listOf("routing", "searching fares", "checking travel budget"),
                ),
            )
    }
}
