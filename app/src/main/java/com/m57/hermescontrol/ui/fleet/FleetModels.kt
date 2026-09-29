package com.m57.hermescontrol.ui.fleet

/** A bot (Hermes profile) as shown on the Fleet screen. */
data class FleetBot(
    val id: String,
    val name: String,
    val initials: String,
    val role: String,
    val hueIndex: Int,
    val isHub: Boolean = false,
)

enum class FleetColumn { QUEUED, WORKING, NEEDS_YOU, DONE }

/** What a bot needs from the user before a task can finish. */
data class FleetAsk(
    val action: String,
    val detail: String,
)

/** A task moving through the fleet, owned by one bot at a time. */
data class FleetTask(
    val id: Long,
    val title: String,
    /** Bots the task passes through, in order. The first is the bot that receives it. */
    val route: List<String>,
    val steps: List<String>,
    val ask: FleetAsk? = null,
    val hop: Int = 0,
    val column: FleetColumn = FleetColumn.QUEUED,
    val progress: Float = 0f,
    /** Simulation clock time at which the task next changes state. */
    val nextAtMs: Long = 0L,
    val stepStartMs: Long = 0L,
    val stepDurationMs: Long = 0L,
    val doneAtMs: Long = 0L,
    val outcome: String? = null,
) {
    val owner: String get() = route[hop]
    val currentStep: String get() = steps.getOrElse(hop) { "working" }
}

/** A delegation hop in flight: drawn as a glowing dot travelling along an edge. */
data class FleetPacket(
    val id: Long,
    val from: String,
    val to: String,
    val hueIndex: Int?,
    val startMs: Long,
    val durationMs: Long,
)

/** One line in the live feed. Bot ids in [parts] are rendered in that bot's hue. */
data class FleetEvent(
    val id: Long,
    val atMs: Long,
    val parts: List<FeedPart>,
)

sealed interface FeedPart {
    data class Plain(
        val text: String,
    ) : FeedPart

    data class Strong(
        val text: String,
    ) : FeedPart

    data class Bot(
        val botId: String,
    ) : FeedPart
}

enum class BotActivity { IDLE, WORKING, WAITING }

data class FleetSnapshot(
    val bots: List<FleetBot>,
    val tasks: List<FleetTask>,
    val packets: List<FleetPacket>,
    val events: List<FleetEvent>,
    val clockMs: Long,
    val doneToday: Int,
) {
    fun activityOf(botId: String): BotActivity =
        when {
            tasks.any { it.owner == botId && it.column == FleetColumn.WORKING } -> BotActivity.WORKING
            tasks.any { it.owner == botId && it.column == FleetColumn.NEEDS_YOU } -> BotActivity.WAITING
            else -> BotActivity.IDLE
        }

    fun inColumn(column: FleetColumn): List<FleetTask> = tasks.filter { it.column == column }

    fun bot(id: String): FleetBot? = bots.firstOrNull { it.id == id }
}

const val FLEET_YOU_ID = "you"
const val FLEET_ROUTER_ID = "router"
