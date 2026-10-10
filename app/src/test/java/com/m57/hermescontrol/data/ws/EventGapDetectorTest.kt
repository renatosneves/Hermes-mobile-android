package com.m57.hermescontrol.data.ws

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventGapDetectorTest {
    @Test
    fun `keeping up never reports a gap`() {
        var published = 0L
        val gaps = EventGapDetector(capacity = 4) { published }
        repeat(100) {
            published++
            assertFalse(gaps.onReceived())
        }
    }

    @Test
    fun `a backlog within the buffer is not a gap`() {
        var published = 0L
        val gaps = EventGapDetector(capacity = 4) { published }
        published += 5 // one being handled, four waiting
        assertFalse(gaps.onReceived())
    }

    @Test
    fun `falling further behind than the buffer reports one gap, then recovers`() {
        var published = 0L
        val gaps = EventGapDetector(capacity = 4) { published }
        published += 10 // six dropped
        assertTrue(gaps.onReceived())
        repeat(3) { assertFalse(gaps.onReceived()) } // the rest of the buffer drains
        published++
        assertFalse(gaps.onReceived())
    }
}
