package com.m57.hermescontrol.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewMessageDeciderTest {
    @Test
    fun firstRunOnlyRecordsBaseline() {
        assertFalse(NewMessageDecider.decide(null, null, 100.0, true))
        assertEquals(100.0, NewMessageDecider.nextWatermark(null, 100.0))
    }

    @Test
    fun notifiesForNewerBotMessage() {
        assertTrue(NewMessageDecider.decide(100.0, 50.0, 200.0, true))
    }

    @Test
    fun missingBackgroundTimeDoesNotBlock() {
        assertTrue(NewMessageDecider.decide(100.0, null, 200.0, true))
    }

    @Test
    fun quietWhenNewestIsFromUser() {
        assertFalse(NewMessageDecider.decide(100.0, 50.0, 200.0, false))
    }

    @Test
    fun quietWhenNotNewerThanWatermark() {
        assertFalse(NewMessageDecider.decide(200.0, 50.0, 200.0, true))
        assertFalse(NewMessageDecider.decide(300.0, 50.0, 200.0, true))
    }

    @Test
    fun quietWhenActivityPredatesLeavingTheApp() {
        assertFalse(NewMessageDecider.decide(100.0, 250.0, 200.0, true))
    }

    @Test
    fun quietWithoutActivity() {
        assertFalse(NewMessageDecider.decide(100.0, null, null, true))
        assertEquals(100.0, NewMessageDecider.nextWatermark(100.0, null))
    }

    @Test
    fun watermarkNeverMovesBackwards() {
        assertEquals(300.0, NewMessageDecider.nextWatermark(300.0, 200.0))
        assertEquals(400.0, NewMessageDecider.nextWatermark(300.0, 400.0))
    }
}
