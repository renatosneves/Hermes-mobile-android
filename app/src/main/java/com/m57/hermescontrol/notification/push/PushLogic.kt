package com.m57.hermescontrol.notification.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The kinds of push the Hermes server sends (contract v1). */
enum class PushType(
    val wire: String,
) {
    APPROVAL("approval"),
    TASK("task"),
    MESSAGE("message"),
    SCHEDULED("scheduled"),
    TEST("test"),
    ;

    companion object {
        fun fromWire(value: String?): PushType? = entries.firstOrNull { it.wire == value }
    }
}

/** How much a bot may notify: everything, approvals and failed or blocked tasks only, or nothing. */
enum class BotLevel(
    val wire: String,
) {
    ALL("all"),
    IMPORTANT("important"),
    OFF("off"),
    ;

    companion object {
        fun fromWire(value: String?): BotLevel = entries.firstOrNull { it.wire == value } ?: ALL
    }
}

/** A decrypted push message. */
data class PushPayload(
    val id: String,
    val type: PushType,
    val profile: String,
    val sessionId: String?,
    val title: String,
    val body: String,
    val approvalId: String?,
    val timestampMs: Long,
)

/** Parses the plaintext JSON the server encrypts into each push. Lenient to unknown fields. */
object PushPayloadParser {
    const val MAX_TITLE = 80
    const val MAX_BODY = 240
    private const val MAX_BYTES = 8 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(bytes: ByteArray): PushPayload? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val obj =
            runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject }
                .getOrNull() ?: return null
        val version = (obj["v"] as? JsonPrimitive)?.longOrNull ?: 1L
        if (version < 1L) return null
        val id = obj.string("id")?.filterNot { it.isISOControl() }?.takeIf { it.isNotBlank() } ?: return null
        val type = PushType.fromWire(obj.string("type")) ?: return null
        val profile = obj.string("profile")?.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE
        val title =
            obj
                .string("title")
                .orEmpty()
                .trim()
                .take(MAX_TITLE)
        val body =
            obj
                .string("body")
                .orEmpty()
                .trim()
                .take(MAX_BODY)
        if (title.isEmpty() && body.isEmpty()) return null
        val approvalId = obj.string("approval_id")?.takeIf { it.isNotBlank() }
        // An approval that cannot be answered would be a dead-end notification.
        if (type == PushType.APPROVAL && approvalId == null) return null
        return PushPayload(
            id = id,
            type = type,
            profile = profile,
            sessionId = obj.string("session_id")?.takeIf { it.isNotBlank() },
            title = title,
            body = body,
            approvalId = approvalId,
            timestampMs = (obj["ts"] as? JsonPrimitive)?.longOrNull ?: 0L,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    const val DEFAULT_PROFILE = "default"
}

/** Push settings that travel to the server with `/register` and also drive local decisions. */
data class PushPrefs(
    val approvals: Boolean = true,
    val tasks: Boolean = true,
    val messages: Boolean = true,
    val scheduled: Boolean = true,
    val botLevels: Map<String, BotLevel> = emptyMap(),
    val quietEnabled: Boolean = true,
    val quietStartMinute: Int = DEFAULT_QUIET_START,
    val quietEndMinute: Int = DEFAULT_QUIET_END,
) {
    fun levelFor(profile: String): BotLevel = botLevels[profile] ?: BotLevel.ALL

    fun typeEnabled(type: PushType): Boolean =
        when (type) {
            PushType.APPROVAL -> approvals
            PushType.TASK -> tasks
            PushType.MESSAGE -> messages
            PushType.SCHEDULED -> scheduled
            PushType.TEST -> true
        }

    /** The `prefs` object of the register request: `{types:{...}, bots:{...}}`. */
    fun toRegisterJson(): JsonObject =
        buildJsonObject {
            put(
                "types",
                buildJsonObject {
                    put(PushType.APPROVAL.wire, approvals)
                    put(PushType.TASK.wire, tasks)
                    put(PushType.MESSAGE.wire, messages)
                    put(PushType.SCHEDULED.wire, scheduled)
                },
            )
            put(
                "bots",
                buildJsonObject {
                    botLevels.toSortedMap().forEach { (name, level) -> put(name, level.wire) }
                },
            )
        }

    companion object {
        const val DEFAULT_QUIET_START = 22 * 60
        const val DEFAULT_QUIET_END = 7 * 60

        /** Bot levels persisted as a JSON object string; unreadable input yields no overrides. */
        fun decodeLevels(raw: String?): Map<String, BotLevel> {
            if (raw.isNullOrBlank()) return emptyMap()
            val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
            return obj
                .mapNotNull { (name, value) ->
                    val level = BotLevel.fromWire((value as? JsonPrimitive)?.contentOrNull)
                    if (level == BotLevel.ALL) null else name to level
                }.toMap()
        }

        fun encodeLevels(levels: Map<String, BotLevel>): String =
            buildJsonObject {
                levels.toSortedMap().forEach { (name, level) ->
                    if (level != BotLevel.ALL) put(name, level.wire)
                }
            }.toString()
    }
}

/** Quiet hours: a daily window that may wrap past midnight. */
object QuietHours {
    const val MINUTES_PER_DAY = 24 * 60

    /** True when [minuteOfDay] falls in `[start, end)`. Equal bounds mean an empty window. */
    fun isQuiet(
        minuteOfDay: Int,
        startMinute: Int,
        endMinute: Int,
    ): Boolean =
        when {
            startMinute == endMinute -> false
            startMinute < endMinute -> minuteOfDay in startMinute until endMinute
            else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
        }

    fun format(minuteOfDay: Int): String {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }
}

/** Notification channels push uses; ids are stable, names come from resources. */
enum class PushChannel(
    val id: String,
) {
    APPROVALS("hermes_push_approvals"),
    TASKS("hermes_push_tasks"),
    MESSAGES("hermes_push_messages"),
    SCHEDULED("hermes_push_scheduled"),
}

/** What to do with one received push. */
data class PushDecision(
    val show: Boolean,
    val channel: PushChannel = PushChannel.MESSAGES,
    val silent: Boolean = false,
)

/** Pure rules deciding whether and how a push is shown. */
object PushDecider {
    private val DROP = PushDecision(show = false)

    fun channelFor(type: PushType): PushChannel =
        when (type) {
            PushType.APPROVAL -> PushChannel.APPROVALS
            PushType.TASK -> PushChannel.TASKS
            PushType.MESSAGE, PushType.TEST -> PushChannel.MESSAGES
            PushType.SCHEDULED -> PushChannel.SCHEDULED
        }

    fun decide(
        payload: PushPayload,
        prefs: PushPrefs,
        chatOpenInForeground: Boolean,
        minuteOfDay: Int,
    ): PushDecision {
        val channel = channelFor(payload.type)
        // A test push proves the pipeline end to end, so it always shows and never stays quiet.
        if (payload.type == PushType.TEST) return PushDecision(show = true, channel = channel)
        if (!prefs.typeEnabled(payload.type)) return DROP
        when (prefs.levelFor(payload.profile)) {
            BotLevel.OFF -> {
                return DROP
            }

            BotLevel.IMPORTANT -> {
                if (payload.type == PushType.MESSAGE || payload.type == PushType.SCHEDULED) return DROP
            }

            BotLevel.ALL -> {
                Unit
            }
        }
        if (chatOpenInForeground) return DROP
        val quiet =
            prefs.quietEnabled &&
                payload.type != PushType.APPROVAL &&
                QuietHours.isQuiet(minuteOfDay, prefs.quietStartMinute, prefs.quietEndMinute)
        return PushDecision(show = true, channel = channel, silent = quiet)
    }

    /** Whether the bot's chat is on screen: the app is in front, on the chat, showing this stored session. */
    fun isChatOpen(
        appInForeground: Boolean,
        onChatScreen: Boolean,
        openStoredSessionId: String?,
        payloadSessionId: String?,
    ): Boolean =
        appInForeground &&
            onChatScreen &&
            !payloadSessionId.isNullOrBlank() &&
            payloadSessionId == openStoredSessionId
}

/** Remembers the most recent push ids so a redelivered push is not shown twice. */
object PushDedupe {
    const val CAPACITY = 64
    private const val SEPARATOR = '\n'

    fun decode(raw: String?): List<String> = raw.orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }

    fun encode(ids: List<String>): String = ids.joinToString(SEPARATOR.toString())

    /** The new list (newest last, oldest evicted) and whether [id] was already known. */
    fun record(
        known: List<String>,
        id: String,
        capacity: Int = CAPACITY,
    ): Pair<List<String>, Boolean> {
        if (id in known) return known to true
        return (known + id).takeLast(capacity) to false
    }
}

/** How often the REST new-message check runs given the state of push. */
object PushSafetyNet {
    const val FALLBACK_INTERVAL_MS = 15 * 60 * 1000L
    const val FALLBACK_FLEX_MS = 5 * 60 * 1000L
    const val RARE_INTERVAL_MS = 6 * 60 * 60 * 1000L
    const val RARE_FLEX_MS = 60 * 60 * 1000L
    const val FRESH_REGISTRATION_MS = 7 * 24 * 60 * 60 * 1000L

    /** Re-register when the last success is older than this, so the server keeps a live endpoint. */
    const val REFRESH_REGISTRATION_MS = 3 * 24 * 60 * 60 * 1000L

    /**
     * Push counts as working only with an endpoint, a recent successful register, and a push
     * service that has not reported itself unavailable.
     */
    fun isPushHealthy(
        hasEndpoint: Boolean,
        lastRegisterOkMs: Long,
        nowMs: Long,
        degraded: Boolean = false,
    ): Boolean {
        if (!hasEndpoint || degraded || lastRegisterOkMs <= 0L) return false
        val age = nowMs - lastRegisterOkMs
        return age in 0 until FRESH_REGISTRATION_MS
    }

    fun intervalMs(pushHealthy: Boolean): Long = if (pushHealthy) RARE_INTERVAL_MS else FALLBACK_INTERVAL_MS

    fun flexMs(pushHealthy: Boolean): Long = if (pushHealthy) RARE_FLEX_MS else FALLBACK_FLEX_MS

    fun needsRegisterRefresh(
        lastRegisterOkMs: Long,
        nowMs: Long,
    ): Boolean = lastRegisterOkMs <= 0L || (nowMs - lastRegisterOkMs) !in 0 until REFRESH_REGISTRATION_MS
}
