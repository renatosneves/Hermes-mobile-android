package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolStatus

/** How one bot passed work to another. */
enum class HandoffKind {
    /** A terminal step running `hermes -p <bot> chat`; the step lasts as long as the other bot works. */
    CLI,

    /** A `kanban_create` task with an assignee; the board starts the other bot a little later. */
    BOARD,
}

/** A hand-off spotted in a chat: the step that made it and the bot it went to. */
internal data class DetectedHandoff(
    val message: ChatMessage,
    val target: String,
    val kind: HandoffKind,
)

/**
 * Spots a bot handing work to another. Two ways exist: a terminal step that runs the Hermes CLI
 * as the other bot (`hermes -p hands chat -q "Message from Ask…"`), and a board task assigned to
 * the other bot (`kanban_create`, how Chief of Staff delegates). Either starts a separate session
 * in that bot's history. Pure, so the rules are unit-tested.
 */
internal object HandoffDetector {
    // `hermes -p hands chat …` / `hermes --profile=hands chat …`
    private val PROFILE_FIRST =
        Regex("""\bhermes\s+(?:-p|--profile)[\s=]+\\?["']?([A-Za-z0-9_.-]+)\\?["']?\s+chat\b""")

    // `hermes chat … -p hands …`
    private val CHAT_FIRST =
        Regex("""\bhermes\s+chat\b[^\n;|&]*?\s(?:-p|--profile)[\s=]+\\?["']?([A-Za-z0-9_.-]+)""")

    private val ASSIGNEE = Regex(""""assignee"\s*:\s*"([A-Za-z0-9_.-]+)"""")

    private const val BOARD_TOOL = "kanban_create"

    /** Steps older than this are leftovers, not live hand-offs. */
    private const val STALE_MS = 30 * 60_000L

    /** How far back the search looks; a hand-off is always near the end of the chat. */
    private const val TAIL = 40

    /** The bot a CLI step hands work to, or null when the step is not one. */
    fun targetOf(message: ChatMessage): String? {
        if (message.role != MessageRole.TOOL) return null
        val content = message.content
        if (!content.contains("hermes")) return null
        val match = PROFILE_FIRST.find(content) ?: CHAT_FIRST.find(content) ?: return null
        return match.groupValues[1].takeIf { it.isNotBlank() }
    }

    /** The bot a board task is assigned to, or null when the step is not a `kanban_create`. */
    fun boardTargetOf(message: ChatMessage): String? {
        if (message.role != MessageRole.TOOL) return null
        val isCreate = message.toolName == BOARD_TOOL || message.content.contains("\"$BOARD_TOOL\"")
        if (!isCreate) return null
        return ASSIGNEE.find(message.content)?.groupValues?.get(1)
    }

    /**
     * Hand-offs in [messages] not yet [known], oldest first. A CLI step counts only while it runs;
     * a board task counts once created, since the other bot starts after the step has finished.
     */
    fun detect(
        messages: List<ChatMessage>,
        self: String?,
        known: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<DetectedHandoff> {
        val found = mutableListOf<DetectedHandoff>()
        for (message in messages.asReversed().take(TAIL)) {
            if (message.role != MessageRole.TOOL || message.isHistoricalCache) continue
            if (message.id in known || nowMs - message.timestamp > STALE_MS) continue
            val cli = if (message.toolStatus == ToolStatus.RUNNING) targetOf(message) else null
            val board = if (cli == null && message.toolStatus != ToolStatus.FAILED) boardTargetOf(message) else null
            val target = cli ?: board ?: continue
            if (target.equals(self, ignoreCase = true)) continue
            found += DetectedHandoff(message, target, if (cli != null) HandoffKind.CLI else HandoffKind.BOARD)
        }
        return found.asReversed()
    }

    /**
     * The session a hand-off started in the other bot: its newest session that began after the
     * step did (allowing for the phone's clock being a little off), from the expected source first.
     */
    fun pickSession(
        sessions: List<SessionInfo>,
        startedAtMs: Long,
        kind: HandoffKind = HandoffKind.CLI,
        taken: Set<String> = emptySet(),
    ): SessionInfo? {
        val earliest = (startedAtMs - CLOCK_SLACK_MS) / 1000.0
        val source = if (kind == HandoffKind.BOARD) "kanban" else "cli"
        val fresh =
            sessions
                .filter { (it.started_at ?: 0.0) >= earliest && it.id !in taken }
                // Oldest first: two hand-offs to the same bot take its runs in order.
                .sortedBy { it.started_at ?: 0.0 }
        return fresh.firstOrNull { it.source == source } ?: fresh.firstOrNull()
    }

    private const val CLOCK_SLACK_MS = 60_000L
}
