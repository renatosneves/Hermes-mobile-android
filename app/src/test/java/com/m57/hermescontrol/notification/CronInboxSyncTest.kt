package com.m57.hermescontrol.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CronInboxSyncTest {
    @Test
    fun retryReplacesTheSameNotificationAndServersRemainSeparate() {
        val id = cronInboxNotificationId("http://gateway:18788", "receipt-1")
        assertEquals(id, cronInboxNotificationId("http://gateway:18788", "receipt-1"))
        assertNotEquals(id, cronInboxNotificationId("http://other:18788", "receipt-1"))
        assertNotEquals(id, cronInboxNotificationId("http://gateway:18788", "receipt-2"))
        assertTrue(id > 0)
    }

    @Test
    fun databaseReplacementRestartsDeviceCursorButFirstSyncDoesNotLoop() {
        assertFalse(cronInboxNeedsRestart(null, "new"))
        assertFalse(cronInboxNeedsRestart("same", "same"))
        assertTrue(cronInboxNeedsRestart("old", "new"))
    }
}
