package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.SessionMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Pure helpers behind the Bots home rows, kept apart from Compose so they can be unit-tested. */
internal object BotsPresentation {
    /** Gateway shown in the rail's menu. Hard-coded for now; later it comes from the server store. */
    const val GATEWAY_HOST = "srv1959645.tail6507df.ts.net"

    /** Short gateway label (the full host does not fit a narrow rail). */
    val GATEWAY_LABEL = GATEWAY_HOST.substringBefore('.')

    /** Default bot hues (same order as the mockup); a bot without a chosen colour gets one by hash. */
    val HUE_HEX =
        listOf(
            "#B9A4FF",
            "#4FC3F7",
            "#5B8CFF",
            "#FF7EB0",
            "#3FD0A4",
            "#FF9F5A",
            "#9C7BFF",
            "#2EC4D6",
            "#9BD24F",
            "#FF6B6B",
            "#E87BE0",
            "#D4C84A",
        )

    /** Colours offered when creating or editing a bot: the default hues first, then deeper tones. */
    val COLOR_OPTIONS =
        HUE_HEX +
            listOf(
                "#4F46E5",
                "#2563EB",
                "#0D9488",
                "#16A34A",
                "#D97706",
                "#EA580C",
                "#DC2626",
                "#DB2777",
                "#9333EA",
                "#94A3B8",
                "#4B5563",
                "#F5F0E6",
            )

    val SHAPES = listOf("circle", "rounded", "square", "hexagon")

    fun defaultHueHex(name: String): String = HUE_HEX[hueIndex(name, HUE_HEX.size)]

    /** The bot's chosen colour, or its default hue. */
    fun colorHex(profile: ProfileInfo): String =
        profile
            .botMeta()
            ?.avatar
            ?.color
            ?.takeIf { it.isNotBlank() } ?: defaultHueHex(profile.name)

    /** A bot counts as working when it has touched a session this recently. */
    const val WORKING_WINDOW_SECONDS = 90.0

    /** Green presence dot: active within the last 15 minutes. */
    const val RECENT_WINDOW_SECONDS = 15 * 60.0

    private val FILLER = setOf("a", "an", "and", "the", "for", "of", "to", "with", "in", "on", "or", "&")

    /** At most three meaningful words from a description ("Gmail: triage, summaries…" → "Gmail triage summaries"). */
    fun shortSummary(
        description: String,
        maxWords: Int = 3,
    ): String {
        val words =
            description
                .replace(Regex("""\(.*?\)"""), " ")
                .split(Regex("""[\s,:;.·|/()—–]+"""))
                .map { it.trim('-', '\'', '"', '…') }
                .filter { it.isNotBlank() }
        val picked = words.take(maxWords).toMutableList()
        while (picked.size > 1 && picked.last().lowercase() in FILLER) picked.removeAt(picked.lastIndex)
        return picked.joinToString(" ")
    }

    fun initials(title: String): String {
        val words = title.split(Regex("""[\s_\-]+""")).filter { it.isNotBlank() }
        val raw =
            when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}"
                words.size == 1 -> words[0].take(2)
                else -> "?"
            }
        return raw.uppercase()
    }

    fun hueIndex(
        name: String,
        count: Int,
    ): Int {
        var hash = 0
        for (ch in name) hash = hash * 31 + ch.code
        return Math.floorMod(hash, count)
    }

    fun lastActive(profile: ProfileInfo): Double? =
        listOfNotNull(
            profile.worker_session?.last_active,
            profile.canonical_session?.last_active,
            profile.last_session?.last_active,
        ).maxOrNull()

    /**
     * Whether the bot is doing something right now. Sessions Hermes reports live ([live]: session
     * key to "working" / "starting" / "waiting" / "idle") are taken at their word, so a chat that
     * finished a moment ago no longer shows as working. Sessions running elsewhere (Telegram,
     * Kanban workers) fall back to "touched in the last [WORKING_WINDOW_SECONDS]".
     */
    fun isWorking(
        profile: ProfileInfo,
        nowSeconds: Double,
        live: Map<String, String> = emptyMap(),
    ): Boolean {
        val sessions =
            listOfNotNull(
                profile.canonical_session?.let {
                    setOfNotNull(it.id, it.resolved_id) to it.last_active
                },
                profile.last_session?.let { setOf(it.id) to it.last_active },
                profile.worker_session?.let { setOf(it.id) to it.last_active },
            )
        return sessions.any { (keys, lastActive) ->
            val status = keys.firstNotNullOfOrNull { live[it] }
            if (status != null) {
                status == "working" || status == "starting"
            } else {
                lastActive != null && nowSeconds - lastActive <= WORKING_WINDOW_SECONDS
            }
        }
    }

    fun isRecent(
        profile: ProfileInfo,
        nowSeconds: Double,
    ): Boolean {
        val last = lastActive(profile) ?: return false
        return nowSeconds - last <= RECENT_WINDOW_SECONDS
    }

    /** "now", "5m", "3h", or a short weekday for older activity. */
    fun relativeTime(
        lastActiveSeconds: Double?,
        nowSeconds: Double,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (lastActiveSeconds == null || lastActiveSeconds <= 0) return ""
        val minutes = ((nowSeconds - lastActiveSeconds) / 60).toLong()
        return when {
            minutes < 1 -> {
                "now"
            }

            minutes < 60 -> {
                "${minutes}m"
            }

            minutes < 24 * 60 -> {
                "${minutes / 60}h"
            }

            minutes < 7 * 24 * 60 -> {
                Instant
                    .ofEpochSecond(lastActiveSeconds.toLong())
                    .atZone(zone)
                    .dayOfWeek
                    .getDisplayName(TextStyle.SHORT, locale)
            }

            else -> {
                "${minutes / (24 * 60)}d"
            }
        }
    }

    /** Name of the pinned room that seats every visible bot (the group chat falls back to all bots). */
    const val ALL_BOTS_ROOM = "All bots"

    /** Messages in the bot's main conversation, used to count what's new since you last looked. */
    fun messageCount(profile: ProfileInfo): Int? =
        profile.canonical_session?.message_count ?: profile.last_session?.message_count

    /** New messages since [seen]; nothing when we have no baseline yet. */
    fun unreadCount(
        profile: ProfileInfo,
        seen: Int?,
    ): Int {
        val count = messageCount(profile) ?: return 0
        if (seen == null) return 0
        return (count - seen).coerceAtLeast(0)
    }

    fun unreadLabel(count: Int): String = if (count > 9) "9+" else count.toString()

    /** Every session id the roster knows for this bot, to match live sessions back to it. */
    fun sessionKeys(profile: ProfileInfo): Set<String> =
        setOfNotNull(
            profile.canonical_session?.id,
            profile.canonical_session?.resolved_id,
            profile.last_session?.id,
            profile.worker_session?.id,
        ).filter { it.isNotBlank() }.toSet()

    /** Bots with a live session waiting on you (an approval or a question). */
    fun needsYou(
        profiles: List<ProfileInfo>,
        waitingSessionKeys: Set<String>,
    ): Set<String> {
        if (waitingSessionKeys.isEmpty()) return emptySet()
        return profiles.filter { p -> sessionKeys(p).any { it in waitingSessionKeys } }.map { it.name }.toSet()
    }

    /** Prompt for "Generate avatar": a simple icon that suits the bot's job. */
    fun avatarPrompt(
        title: String,
        description: String,
    ): String {
        val role = shortSummary(description, maxWords = 8).ifBlank { "a helpful assistant" }
        return "A friendly, minimal avatar icon for an AI assistant called \"${title.ifBlank { "Bot" }}\" " +
            "whose job is: $role. Flat vector style, one bold centred symbol, soft gradient background, " +
            "no text, no letters, square."
    }

    /** The @handle, only when it adds something the title doesn't already say ("Work" / @work adds nothing). */
    fun distinctHandle(
        name: String,
        title: String,
    ): String? {
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        return if (key(name) == key(title)) null else "@$name"
    }

    /** The bot the Bots home opens on, and the one voice talks to, when you haven't picked one. */
    const val DEFAULT_BOT = "chief-of-staff"

    /** Longest message preview kept for a row (the row shows up to three lines of it). */
    private const val PREVIEW_MAX_CHARS = 240

    /** Plain text of a message's content: a string, or the text parts of a content list. */
    fun contentText(content: JsonElement?): String =
        when (content) {
            is JsonPrimitive -> {
                if (content.isString) content.content else ""
            }

            is JsonArray -> {
                content
                    .mapNotNull { part ->
                        when (part) {
                            is JsonPrimitive -> part.content.takeIf { part.isString }
                            is JsonObject -> (part["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                            else -> null
                        }
                    }.joinToString(" ")
            }

            else -> {
                ""
            }
        }

    /** Markdown and extra whitespace stripped, so a preview reads like a chat line. */
    fun previewText(raw: String): String =
        raw
            .replace(Regex("""```[\s\S]*?```"""), " ")
            .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), " ")
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace(Regex("""[*_`#>|~]+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .let { if (it.length > PREVIEW_MAX_CHARS) it.take(PREVIEW_MAX_CHARS).trimEnd() + "…" else it }

    /**
     * Telegram-style preview from a page of the newest messages: the latest thing you or the bot
     * said, prefixed "You: " when it was you. Null when there is nothing to show.
     */
    fun latestPreview(messages: List<SessionMessage>): String? {
        val last =
            messages
                .asReversed()
                .firstOrNull { m ->
                    (m.role == "user" || m.role == "assistant") && m.display_kind == null &&
                        previewText(contentText(m.display_content ?: m.content)).isNotEmpty()
                } ?: return null
        val text = previewText(contentText(last.display_content ?: last.content))
        return if (last.role == "user") "You: $text" else text
    }

    /** What the bot is doing right now, if we know: the worker's or latest session's title. */
    fun currentTask(profile: ProfileInfo): String? =
        (
            profile.worker_session?.title
                ?: profile.canonical_session?.title
                ?: profile.last_session?.title
        )?.trim()?.takeIf { it.isNotBlank() }
}
