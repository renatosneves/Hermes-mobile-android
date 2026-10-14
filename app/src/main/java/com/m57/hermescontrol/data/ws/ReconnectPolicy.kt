package com.m57.hermescontrol.data.ws

/**
 * Backoff for WebSocket reconnects. The first retry after a healthy connection drops goes out
 * almost at once (the gateway usually answers in tens of milliseconds); only repeated failures
 * back off, from [INITIAL_BACKOFF_MS] up to [MAX_BACKOFF_MS].
 */
internal class ReconnectPolicy(
    private val random: () -> Double = { Math.random() },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    @Volatile
    private var backoffMs: Long = INITIAL_BACKOFF_MS

    /** The current backoff ceiling. Setting it puts the policy mid-backoff, past the immediate retry. */
    var currentBackoff: Long
        get() = backoffMs
        set(value) {
            backoffMs = value
            firstAttempt = false
            holdBackoff = false
        }
    private var initialBackoffMs: Long = INITIAL_BACKOFF_MS
    private var testBackoffOverrideMs: Long? = null

    /** True until the first retry after a [reset]: that retry skips the backoff. */
    @Volatile
    private var firstAttempt: Boolean = true

    /** Set when the last delay was the immediate first retry, so [advance] leaves the ceiling alone. */
    @Volatile
    private var holdBackoff: Boolean = false

    /** When the current socket opened; null once its drop has been accounted for. */
    @Volatile
    private var openedAtMs: Long? = null

    /**
     * A socket opened. The backoff resets only if it then stays up for [STABLE_CONNECTION_MS], so a
     * server that accepts and drops at once cannot pull the client into a tight retry loop.
     */
    fun onOpened() {
        openedAtMs = nowMs()
    }

    fun nextDelayMs(): Long {
        val override = testBackoffOverrideMs
        if (override != null) return override
        openedAtMs?.let { opened ->
            openedAtMs = null
            if (nowMs() - opened >= STABLE_CONNECTION_MS) reset()
        }
        if (firstAttempt) {
            firstAttempt = false
            holdBackoff = true
            return (random() * FIRST_RETRY_JITTER_MS).toLong()
        }
        val ceiling = backoffMs
        if (ceiling <= 0L) return 0L
        return (random() * ceiling).toLong().coerceAtLeast(BACKOFF_BASE_MS).coerceAtMost(ceiling)
    }

    fun advance() {
        // The immediate first retry does not count as a backoff step.
        if (holdBackoff) {
            holdBackoff = false
            return
        }
        backoffMs = (backoffMs * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_BACKOFF_MS)
    }

    fun reset() {
        backoffMs = initialBackoffMs
        firstAttempt = true
        holdBackoff = false
        openedAtMs = null
    }

    fun setBackoffForTest(initialMillis: Long) {
        initialBackoffMs = initialMillis
        backoffMs = initialMillis
        testBackoffOverrideMs = initialMillis
    }

    companion object {
        internal const val FIRST_RETRY_JITTER_MS = 150L
        internal const val STABLE_CONNECTION_MS = 5_000L
        internal const val INITIAL_BACKOFF_MS = 500L
        internal const val BACKOFF_BASE_MS = 250L
        internal const val MAX_BACKOFF_MS = 15_000L
        private const val BACKOFF_MULTIPLIER = 2.0
    }
}
