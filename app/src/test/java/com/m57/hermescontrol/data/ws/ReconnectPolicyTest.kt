package com.m57.hermescontrol.data.ws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    /** Spends the immediate first retry so the tests below exercise the backoff itself. */
    private fun ReconnectPolicy.skipFirstRetry(): ReconnectPolicy =
        apply {
            nextDelayMs()
            advance()
        }

    @Test
    fun defaultInitialBackoffIs500() {
        val policy = ReconnectPolicy()
        assertEquals(500L, policy.currentBackoff)
    }

    @Test
    fun firstRetryAfterResetIsNearlyImmediate() {
        val policy = ReconnectPolicy(random = { 0.999 })
        assertTrue(policy.nextDelayMs() < ReconnectPolicy.FIRST_RETRY_JITTER_MS)
        policy.advance()
        // The immediate retry is not a backoff step.
        assertEquals(500L, policy.currentBackoff)

        policy.reset()
        assertTrue(policy.nextDelayMs() < ReconnectPolicy.FIRST_RETRY_JITTER_MS)
    }

    @Test
    fun onlyAStableConnectionEarnsAnotherImmediateRetry() {
        var now = 0L
        val policy = ReconnectPolicy(random = { 0.999 }, nowMs = { now }).skipFirstRetry()

        // Opened and dropped at once: keep backing off.
        policy.onOpened()
        now += 100L
        assertEquals(499L, policy.nextDelayMs())
        policy.advance()

        // Stayed up long enough: the next drop retries at once again.
        policy.onOpened()
        now += ReconnectPolicy.STABLE_CONNECTION_MS
        assertTrue(policy.nextDelayMs() < ReconnectPolicy.FIRST_RETRY_JITTER_MS)
        policy.advance()
        assertEquals(500L, policy.currentBackoff)
    }

    @Test
    fun nextDelayMsUsesInjectedRandomAndFloorsAt250() {
        val minPolicy = ReconnectPolicy(random = { 0.0 }).skipFirstRetry()
        assertEquals(250L, minPolicy.nextDelayMs())

        val midPolicy = ReconnectPolicy(random = { 0.6 }).skipFirstRetry()
        assertEquals(300L, midPolicy.nextDelayMs())

        val maxPolicy = ReconnectPolicy(random = { 0.999 }).skipFirstRetry()
        assertEquals(499L, maxPolicy.nextDelayMs())
    }

    @Test
    fun nextDelayMsRespectsCeilingCapsBelowFloorAndZero() {
        val zeroPolicy = ReconnectPolicy(random = { 0.5 }).skipFirstRetry()
        zeroPolicy.currentBackoff = 0L
        assertEquals(0L, zeroPolicy.nextDelayMs())

        val negativePolicy = ReconnectPolicy(random = { 0.5 }).skipFirstRetry()
        negativePolicy.currentBackoff = -100L
        assertEquals(0L, negativePolicy.nextDelayMs())

        val lowCeilingPolicy = ReconnectPolicy(random = { 0.0 }).skipFirstRetry()
        lowCeilingPolicy.currentBackoff = 100L
        assertEquals(100L, lowCeilingPolicy.nextDelayMs())
    }

    @Test
    fun advanceDoublesBackoffUpToCapOf15000() {
        val policy = ReconnectPolicy().skipFirstRetry()
        assertEquals(500L, policy.currentBackoff)

        val expected = listOf(1000L, 2000L, 4000L, 8000L, 15000L, 15000L)
        for (value in expected) {
            policy.advance()
            assertEquals(value, policy.currentBackoff)
        }
    }

    @Test
    fun resetRestoresInitialBackoffOf500() {
        val policy = ReconnectPolicy().skipFirstRetry()
        policy.advance()
        policy.advance()
        assertEquals(2000L, policy.currentBackoff)

        policy.reset()
        assertEquals(500L, policy.currentBackoff)
    }

    @Test
    fun setBackoffForTestOverridesCurrentAndInitialBackoffDeterministically() {
        val policy = ReconnectPolicy(random = { 0.5 })
        policy.setBackoffForTest(1500L)

        assertEquals(1500L, policy.currentBackoff)
        assertEquals(1500L, policy.nextDelayMs())

        policy.advance()
        assertEquals(1500L, policy.nextDelayMs())

        policy.reset()
        assertEquals(1500L, policy.nextDelayMs())
    }
}
