package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.WsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronFollowUpsTest {
    private fun complete(
        sessionId: String?,
        payload: Map<String, Any?>,
    ) = WsEvent.MessageComplete("hi", sessionId, rawPayload = payload, completionId = "c1")

    @Test
    fun onlyCronSourcedEventsCount() {
        assertTrue(CronFollowUps.isCron(complete("s1", mapOf("source" to "cron"))))
        assertFalse(CronFollowUps.isCron(complete("s1", mapOf("text" to "hi"))))
        assertTrue(CronFollowUps.isCron(WsEvent.BackgroundComplete(mapOf("source" to "cron"))))
        assertFalse(CronFollowUps.isCron(WsEvent.BackgroundComplete(mapOf("label" to "backup"))))
    }

    @Test
    fun aSessionScopedFollowUpReloadsOnlyItsOwnChat() {
        val event = complete("s1", mapOf("source" to "cron", "profile" to "research"))
        assertTrue(CronFollowUps.concernsOpenChat(event, { it == "s1" }, "roam"))
        assertFalse(CronFollowUps.concernsOpenChat(event, { it == "s2" }, "research"))
    }

    @Test
    fun anUnscopedFollowUpMatchesByBot() {
        val event = WsEvent.BackgroundComplete(mapOf("source" to "cron", "profile" to "research"))
        assertTrue(CronFollowUps.concernsOpenChat(event, { false }, "research"))
        assertFalse(CronFollowUps.concernsOpenChat(event, { false }, "roam"))
        assertEquals("research", CronFollowUps.profileOf(event))
        val anonymous = WsEvent.BackgroundComplete(mapOf("source" to "cron"))
        assertTrue(CronFollowUps.concernsOpenChat(anonymous, { false }, "roam"))
    }
}
