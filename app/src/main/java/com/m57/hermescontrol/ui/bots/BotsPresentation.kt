package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.ProfileInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Pure helpers behind the Bots home rows, kept apart from Compose so they can be unit-tested. */
internal object BotsPresentation {
    /** Gateway shown in the rail header. Hard-coded for now; later it comes from the server store. */
    const val GATEWAY_HOST = "srv1959645.tail6507df.ts.net"

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

    fun isWorking(
        profile: ProfileInfo,
        nowSeconds: Double,
    ): Boolean {
        val last = lastActive(profile) ?: return false
        return nowSeconds - last <= WORKING_WINDOW_SECONDS
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

    /** What the bot is doing right now, if we know: the worker's or latest session's title. */
    fun currentTask(profile: ProfileInfo): String? =
        (
            profile.worker_session?.title
                ?: profile.canonical_session?.title
                ?: profile.last_session?.title
        )?.trim()?.takeIf { it.isNotBlank() }
}
