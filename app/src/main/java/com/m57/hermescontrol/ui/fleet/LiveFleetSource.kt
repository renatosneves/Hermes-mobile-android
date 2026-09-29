package com.m57.hermescontrol.ui.fleet

import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.KanbanProfile
import com.m57.hermescontrol.data.model.KanbanTask

/**
 * Turns successive Kanban board polls into [FleetSnapshot]s. Bots are the Kanban
 * profiles; tasks are the board's cards. Differences between two polls become feed
 * lines and delegation packets: a new card flies from the router to its assignee, a
 * reassignment flies between the two bots, and a card that needs you flies to you.
 *
 * Pure and single-threaded: the caller owns the clock and the polling.
 */
class LiveFleetSource {
    private var bots: List<FleetBot> = emptyList()
    private var tasks: List<FleetTask> = emptyList()
    private val packets = ArrayDeque<FleetPacket>()
    private val events = ArrayDeque<FleetEvent>()
    private val seen = mutableMapOf<String, KanbanTask>()
    private val numericIds = mutableMapOf<String, Long>()
    private var nextId = 1L
    private var polled = false
    private var doneTotal = 0

    /** Current simulation clock in ms. Advanced by the caller so packets can animate between polls. */
    var clockMs: Long = 0L
        private set

    fun advance(deltaMs: Long) {
        clockMs += deltaMs
        packets.removeAll { clockMs > it.startMs + it.durationMs + PACKET_LINGER_MS }
    }

    fun snapshot(): FleetSnapshot =
        FleetSnapshot(
            bots = bots,
            tasks = tasks,
            packets = packets.toList(),
            events = events.toList(),
            clockMs = clockMs,
            doneToday = doneTotal,
        )

    /**
     * Applies one poll. [profiles] may be empty (the endpoint is optional); bots are then
     * derived from the assignees on the board.
     */
    fun apply(
        board: KanbanBoardResponse,
        profiles: List<KanbanProfile>,
        orchestrator: String?,
    ) {
        val cards = board.columns.flatMap { it.tasks }.filterNot { column(it.status) == null }
        bots = buildBots(profiles, cards, orchestrator)
        val hub = bots.firstOrNull { it.isHub }?.id

        val next = cards.sortedBy { it.createdAt ?: 0L }.map { card -> toFleetTask(card, hub) }
        if (polled) diff(cards, hub)
        polled = true

        seen.clear()
        cards.forEach { seen[it.id] = it }
        doneTotal = next.count { it.column == FleetColumn.DONE }
        tasks = trimDone(next)
    }

    private fun diff(
        cards: List<KanbanTask>,
        hub: String?,
    ) {
        cards.forEach { card ->
            val owner = ownerOf(card, hub)
            val before = seen[card.id]
            val col = column(card.status)
            when {
                before == null -> {
                    fly(FLEET_ROUTER_ID, owner, 0)
                    event(FeedPart.Bot(owner), FeedPart.Plain(" picked up "), FeedPart.Strong(card.title))
                }

                ownerOf(before, hub) != owner -> {
                    val from = ownerOf(before, hub)
                    fly(from, owner, 0)
                    event(
                        FeedPart.Bot(from),
                        FeedPart.Plain(" handed "),
                        FeedPart.Strong(card.title),
                        FeedPart.Plain(" to "),
                        FeedPart.Bot(owner),
                    )
                }

                column(before.status) != col -> {
                    when (col) {
                        FleetColumn.WORKING -> {
                            event(FeedPart.Bot(owner), FeedPart.Plain(" started "), FeedPart.Strong(card.title))
                        }

                        FleetColumn.NEEDS_YOU -> {
                            fly(owner, FLEET_YOU_ID, 0)
                            event(FeedPart.Bot(owner), FeedPart.Plain(" needs you: "), FeedPart.Strong(card.title))
                        }

                        FleetColumn.DONE -> {
                            event(FeedPart.Bot(owner), FeedPart.Plain(" finished "), FeedPart.Strong(card.title))
                        }

                        else -> {
                            event(FeedPart.Bot(owner), FeedPart.Plain(" queued "), FeedPart.Strong(card.title))
                        }
                    }
                }
            }
        }
    }

    private fun toFleetTask(
        card: KanbanTask,
        hub: String?,
    ): FleetTask {
        val col = column(card.status) ?: FleetColumn.QUEUED
        val summary =
            card.latestSummary
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
        val progress =
            card.progress?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total } ?: PROGRESS_UNKNOWN
        return FleetTask(
            id = numericIds.getOrPut(card.id) { nextId++ },
            title = card.title,
            route = listOf(ownerOf(card, hub)),
            steps = listOf(summary ?: card.status),
            ask =
                if (col == FleetColumn.NEEDS_YOU) {
                    FleetAsk(
                        action = if (card.status.equals("review", true)) "Review" else "Unblock",
                        detail = summary ?: card.title,
                    )
                } else {
                    null
                },
            column = col,
            progress = progress,
            outcome = summary,
        )
    }

    private fun trimDone(all: List<FleetTask>): List<FleetTask> {
        val shown =
            all
                .filter { it.column == FleetColumn.DONE }
                .takeLast(MAX_DONE_SHOWN)
                .map { it.id }
                .toSet()
        return all.filter { it.column != FleetColumn.DONE || it.id in shown }
    }

    private fun buildBots(
        profiles: List<KanbanProfile>,
        cards: List<KanbanTask>,
        orchestrator: String?,
    ): List<FleetBot> {
        val assigned = cards.mapNotNull { it.assignee }.toSet()
        val names =
            buildList {
                // The default profile runs the dashboard; show it only when it has work.
                profiles.filterNot { it.isDefault && it.name !in assigned }.forEach { add(it.name) }
                assigned.forEach { if (it !in this) add(it) }
                if (orchestrator != null && orchestrator.isNotBlank() && orchestrator !in this) add(orchestrator)
            }
        val hub = orchestrator?.takeIf { it in names }
        val described = profiles.associateBy { it.name }
        return names.sorted().mapIndexed { index, name ->
            FleetBot(
                id = name,
                name = displayName(name),
                initials =
                    name
                        .filter { it.isLetterOrDigit() }
                        .take(2)
                        .uppercase()
                        .ifEmpty { "?" },
                role =
                    described[name]
                        ?.description
                        ?.lineSequence()
                        ?.firstOrNull()
                        .orEmpty()
                        .take(ROLE_MAX),
                hueIndex = index,
                isHub = name == hub,
            )
        }
    }

    private fun ownerOf(
        card: KanbanTask,
        hub: String?,
    ): String = card.assignee?.takeIf { it.isNotBlank() } ?: hub ?: FLEET_ROUTER_ID

    private fun fly(
        from: String,
        to: String,
        delayMs: Long,
    ) {
        if (from == to) return
        val hue = bots.firstOrNull { it.id == to || it.id == from }?.hueIndex
        packets += FleetPacket(nextId++, from, to, hue, clockMs + delayMs, FleetSimulator.PACKET_MS)
    }

    private fun event(vararg parts: FeedPart) {
        events.addFirst(FleetEvent(nextId++, clockMs, parts.toList()))
        while (events.size > MAX_EVENTS) events.removeLast()
    }

    companion object {
        /** Sentinel for [FleetTask.progress] when the card reports no step counts. */
        const val PROGRESS_UNKNOWN = -1f
        private const val PACKET_LINGER_MS = 400L
        private const val MAX_DONE_SHOWN = 5
        private const val MAX_EVENTS = 30
        private const val ROLE_MAX = 60

        /** Maps a Kanban status to a Fleet column; archived cards are dropped. */
        fun column(status: String): FleetColumn? =
            when (status.lowercase()) {
                "running" -> FleetColumn.WORKING
                "blocked", "review" -> FleetColumn.NEEDS_YOU
                "done" -> FleetColumn.DONE
                "archived" -> null
                else -> FleetColumn.QUEUED
            }

        fun displayName(profile: String): String =
            profile
                .split('-', '_', ' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
                .ifEmpty { profile }
    }
}
