package com.m57.hermescontrol.notification

/**
 * Pure rules for the background new-message check: whether a bot's latest activity deserves a
 * notification. All times are epoch seconds, as the roster reports them.
 */
internal object NewMessageDecider {
    /**
     * Whether a bot's activity is new enough to be worth reading its messages. The first run for
     * a bot ([lastSeenAt] null) only records a baseline, so it never qualifies.
     */
    fun hasNewActivity(
        lastSeenAt: Double?,
        appBackgroundedAt: Double?,
        latestActivityAt: Double?,
    ): Boolean {
        if (lastSeenAt == null || latestActivityAt == null) return false
        return latestActivityAt > lastSeenAt && latestActivityAt > (appBackgroundedAt ?: 0.0)
    }

    /**
     * Whether to notify: the activity is new, after the app was last left (so messages already
     * seen in the app stay quiet), and the newest message is the bot's own.
     */
    fun decide(
        lastSeenAt: Double?,
        appBackgroundedAt: Double?,
        latestActivityAt: Double?,
        newestFromBot: Boolean,
    ): Boolean = newestFromBot && hasNewActivity(lastSeenAt, appBackgroundedAt, latestActivityAt)

    /** The watermark to store after a check: never moves backwards, and starts at the baseline. */
    fun nextWatermark(
        lastSeenAt: Double?,
        latestActivityAt: Double?,
    ): Double? =
        when {
            latestActivityAt == null -> lastSeenAt
            lastSeenAt == null -> latestActivityAt
            else -> maxOf(lastSeenAt, latestActivityAt)
        }
}
