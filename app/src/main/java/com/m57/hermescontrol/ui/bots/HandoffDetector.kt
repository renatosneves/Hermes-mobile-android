package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolStatus

/**
 * Spots a bot handing work to another bot. A hand-off is a terminal step that runs the Hermes
 * CLI as the other bot (`hermes -p hands chat -q "Message from Ask…"`), which starts a separate
 * session in that bot's history. Pure, so the rules are unit-tested.
 */
internal object HandoffDetector {
    // `hermes -p hands chat …` / `hermes --profile=hands chat …`
    private val PROFILE_FIRST =
        Regex("""\bhermes\s+(?:-p|--profile)[\s=]+\\?["']?([A-Za-z0-9_.-]+)\\?["']?\s+chat\b""")

    // `hermes chat … -p hands …`
    private val CHAT_FIRST =
        Regex("""\bhermes\s+chat\b[^\n;|&]*?\s(?:-p|--profile)[\s=]+\\?["']?([A-Za-z0-9_.-]+)""")

    /** Steps older than this that still read "running" are leftovers, not live hand-offs. */
    private const val STALE_MS = 30 * 60_000L

    /** How far back the hand-off search looks; a hand-off is always near the end of the chat. */
    private const val TAIL = 40

    /** The bot a tool step hands work to, or null when the step is not a hand-off. */
    fun targetOf(message: ChatMessage): String? {
        if (message.role != MessageRole.TOOL) return null
        val content = message.content
        if (!content.contains("hermes")) return null
        val match = PROFILE_FIRST.find(content) ?: CHAT_FIRST.find(content) ?: return null
        return match.groupValues[1].takeIf { it.isNotBlank() }
    }

    /** The newest hand-off still running in [messages], skipping hand-offs to [self] and [ignored] ones. */
    fun running(
        messages: List<ChatMessage>,
        self: String?,
        ignored: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<ChatMessage, String>? {
        for (message in messages.asReversed().take(TAIL)) {
            if (message.toolStatus != ToolStatus.RUNNING || message.isHistoricalCache) continue
            if (message.id in ignored || nowMs - message.timestamp > STALE_MS) continue
            val target = targetOf(message) ?: continue
            if (target.equals(self, ignoreCase = true)) continue
            return message to target
        }
        return null
    }

    /**
     * The session the hand-off started in the other bot: its newest session that began after the
     * step did (allowing for the phone's clock being a little off). A CLI session wins over others.
     */
    fun pickSession(
        sessions: List<SessionInfo>,
        startedAtMs: Long,
    ): SessionInfo? {
        val earliest = (startedAtMs - CLOCK_SLACK_MS) / 1000.0
        val fresh = sessions.filter { (it.started_at ?: 0.0) >= earliest }
        return fresh
            .sortedByDescending { it.started_at ?: 0.0 }
            .let { list -> list.firstOrNull { it.source == "cli" } ?: list.firstOrNull() }
    }

    private const val CLOCK_SLACK_MS = 120_000L
}
