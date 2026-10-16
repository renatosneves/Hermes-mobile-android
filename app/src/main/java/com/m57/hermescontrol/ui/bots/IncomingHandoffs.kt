package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.SessionMessage

/**
 * A bot that has been handed work from somewhere other than the open chat (Telegram, a schedule,
 * another bot's tool call). [sender] is who handed it over, when known; [sessionId] is the Bot Chat
 * the DM landed in, so tapping the bot can open it ([HandoffKind.BOT_CHAT] only).
 */
data class IncomingHandoff(
    val kind: HandoffKind,
    val sender: String?,
    val sessionId: String?,
    /** When the session last moved (epoch seconds): the hand-off lapses once it goes quiet. */
    val lastActive: Double,
) {
    /** Whether the hand-off still counts at [nowSeconds]. */
    fun isLive(nowSeconds: Double): Boolean = nowSeconds - lastActive <= IncomingHandoffs.windowFor(kind)
}

/** What the roster says about a bot's possible hand-off, before the sender is known. */
data class HandoffCandidate(
    val kind: HandoffKind,
    val sessionId: String?,
    val lastActive: Double,
)

/**
 * Spots hand-offs arriving at each bot from the roster the Bots screen already polls. Pure, so
 * the rules are unit-tested. A `message_agent` DM is delivered into the target's "Bot Chat"
 * session as a user turn starting "Message from <sender>"; a board task starts a worker session
 * with source "kanban".
 */
internal object IncomingHandoffs {
    /** A Bot Chat that moved this recently is a live hand-off. */
    const val BOT_CHAT_WINDOW_SECONDS = 15 * 60.0

    /** A board worker started this recently is a live hand-off. */
    const val KANBAN_WINDOW_SECONDS = 30 * 60.0

    private const val KANBAN_SOURCE = "kanban"

    // "Message from Research: …", "Message from Chief of Staff (chief-of-staff) …", "Message from @ask …".
    private val MESSAGE_FROM = Regex("""^\s*Message from\s+@?([^:\n(\[—–,]{1,64})""", RegexOption.IGNORE_CASE)
    private val NON_SLUG = Regex("[^a-z0-9]+")

    fun windowFor(kind: HandoffKind): Double =
        if (kind == HandoffKind.BOARD) KANBAN_WINDOW_SECONDS else BOT_CHAT_WINDOW_SECONDS

    private fun slugOf(name: String): String = name.lowercase().replace(NON_SLUG, "-").trim('-')

    /**
     * The hand-off [profile] is working on at [nowSeconds], judged from the roster alone: a live
     * Bot Chat first (it names its sender), else a running board worker. Null when neither is.
     */
    fun candidateOf(
        profile: ProfileInfo,
        nowSeconds: Double,
    ): HandoffCandidate? {
        val chats =
            listOfNotNull(
                profile.canonical_session?.let {
                    Triple(it.resolved_id ?: it.id, it.title ?: it.root_title, it.last_active)
                },
                profile.last_session?.let { Triple(it.id, it.title, it.last_active) },
            )
        val chat =
            chats.firstOrNull { (id, title, lastActive) ->
                id.isNotBlank() &&
                    title.equals(HandoffDetector.BOT_CHAT_TITLE, ignoreCase = true) &&
                    lastActive != null &&
                    nowSeconds - lastActive <= BOT_CHAT_WINDOW_SECONDS
            }
        if (chat != null) return HandoffCandidate(HandoffKind.BOT_CHAT, chat.first, chat.third!!)
        val worker = profile.worker_session ?: return null
        val lastActive = worker.last_active ?: return null
        if (!worker.source.equals(KANBAN_SOURCE, ignoreCase = true)) return null
        if (nowSeconds - lastActive > KANBAN_WINDOW_SECONDS) return null
        return HandoffCandidate(HandoffKind.BOARD, null, lastActive)
    }

    /**
     * Who sent the DM: the name after "Message from" in the newest user message of [messages]
     * (oldest first), matched to the [roster] by name or friendly title, ignoring case and
     * punctuation. A name the roster doesn't know is kept as written; the bot itself is never
     * its own sender.
     */
    fun senderOf(
        messages: List<SessionMessage>,
        roster: List<ProfileInfo>,
        self: String? = null,
    ): String? {
        for (message in messages.asReversed()) {
            if (message.role != "user") continue
            val text = BotsPresentation.contentText(message.display_content ?: message.content)
            val raw =
                MESSAGE_FROM
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.trimEnd('.', '…') ?: continue
            if (raw.isEmpty()) continue
            val bot = matchBot(raw, roster)
            if (bot != null && bot.name.equals(self, ignoreCase = true)) return null
            return bot?.effectiveTitle ?: raw
        }
        return null
    }

    /** The roster bot [raw] names: exactly, by slug, or as the start of a longer phrase. */
    private fun matchBot(
        raw: String,
        roster: List<ProfileInfo>,
    ): ProfileInfo? {
        val slug = slugOf(raw)
        if (slug.isEmpty()) return null
        roster.firstOrNull { slugOf(it.name) == slug || slugOf(it.effectiveTitle) == slug }?.let { return it }
        // "Research agent says": the longest bot name that starts the phrase on a word boundary.
        return roster
            .flatMap { bot -> listOf(bot.name, bot.effectiveTitle).map { slugOf(it) to bot } }
            .filter { (key, _) -> key.isNotEmpty() && (slug == key || slug.startsWith("$key-")) }
            .maxByOrNull { it.first.length }
            ?.second
    }
}
