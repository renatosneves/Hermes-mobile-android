package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.CanonicalSessionInfo
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.ProfileWorkerSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class BotsPresentationTest {
    @Test
    fun `short summary keeps three meaningful words`() {
        assertEquals("Gmail triage summaries", BotsPresentation.shortSummary("Gmail: triage, summaries and replies"))
        assertEquals("LinkedIn and X", BotsPresentation.shortSummary("LinkedIn and X (drafts only) posts"))
        assertEquals("Orchestrator", BotsPresentation.shortSummary("Orchestrator for the fleet"))
        assertEquals("Travel flights hotels", BotsPresentation.shortSummary("Travel: flights, hotels, visas"))
        assertEquals("", BotsPresentation.shortSummary(""))
    }

    @Test
    fun `initials use two words or the first two letters`() {
        assertEquals("CO", BotsPresentation.initials("chief-of-staff"))
        assertEquals("IN", BotsPresentation.initials("Inbox"))
        assertEquals("CS", BotsPresentation.initials("Chief Staff"))
    }

    @Test
    fun `hue index is stable and in range`() {
        val first = BotsPresentation.hueIndex("inbox", 12)
        assertEquals(first, BotsPresentation.hueIndex("inbox", 12))
        assertTrue(first in 0 until 12)
    }

    @Test
    fun `working and presence follow last activity`() {
        val now = 10_000.0
        val busy = ProfileInfo(name = "a", worker_session = ProfileWorkerSummary(id = "w", last_active = now - 30))
        val recent =
            ProfileInfo(name = "b", canonical_session = CanonicalSessionInfo(id = "s", last_active = now - 600))
        val quiet = ProfileInfo(name = "c")
        assertTrue(BotsPresentation.isWorking(busy, now))
        assertFalse(BotsPresentation.isWorking(recent, now))
        assertTrue(BotsPresentation.isRecent(recent, now))
        assertFalse(BotsPresentation.isRecent(quiet, now))
        assertFalse(BotsPresentation.isWorking(quiet, now))
    }

    @Test
    fun `relative time buckets`() {
        val now = 1_000_000.0
        val utc = ZoneOffset.UTC
        assertEquals("", BotsPresentation.relativeTime(null, now, utc, Locale.UK))
        assertEquals("now", BotsPresentation.relativeTime(now - 20, now, utc, Locale.UK))
        assertEquals("5m", BotsPresentation.relativeTime(now - 300, now, utc, Locale.UK))
        assertEquals("3h", BotsPresentation.relativeTime(now - 3 * 3600, now, utc, Locale.UK))
        assertEquals("9d", BotsPresentation.relativeTime(now - 9 * 86_400, now, utc, Locale.UK))
    }

    @Test
    fun `colour options include every default hue and a bot's chosen colour wins`() {
        assertTrue(BotsPresentation.COLOR_OPTIONS.containsAll(BotsPresentation.HUE_HEX))
        assertEquals(24, BotsPresentation.COLOR_OPTIONS.distinct().size)
        val plain = ProfileInfo(name = "inbox")
        assertEquals(BotsPresentation.defaultHueHex("inbox"), BotsPresentation.colorHex(plain))
    }

    @Test
    fun `gateway label is the short host`() {
        assertEquals("srv1959645", BotsPresentation.GATEWAY_LABEL)
    }

    @Test
    fun `unread counts messages since last look and needs a baseline`() {
        val bot = ProfileInfo(name = "a", canonical_session = CanonicalSessionInfo(id = "s", message_count = 12))
        assertEquals(0, BotsPresentation.unreadCount(bot, null))
        assertEquals(3, BotsPresentation.unreadCount(bot, 9))
        assertEquals(0, BotsPresentation.unreadCount(bot, 15))
        assertEquals("9+", BotsPresentation.unreadLabel(14))
        assertEquals("4", BotsPresentation.unreadLabel(4))
    }

    @Test
    fun `needs you matches waiting live sessions to their bot`() {
        val inbox =
            ProfileInfo(name = "inbox", canonical_session = CanonicalSessionInfo(id = "c1", resolved_id = "c2"))
        val link = ProfileInfo(name = "link", worker_session = ProfileWorkerSummary(id = "w9"))
        val profiles = listOf(inbox, link)
        assertEquals(setOf("inbox"), BotsPresentation.needsYou(profiles, setOf("c2")))
        assertEquals(setOf("link"), BotsPresentation.needsYou(profiles, setOf("w9", "other")))
        assertTrue(BotsPresentation.needsYou(profiles, emptySet()).isEmpty())
    }

    @Test
    fun `avatar prompt names the bot and its job without text`() {
        val prompt = BotsPresentation.avatarPrompt("Inbox", "Gmail: triage, summaries and replies")
        assertTrue(prompt.contains("\"Inbox\""))
        assertTrue(prompt.contains("Gmail triage summaries"))
        assertTrue(prompt.contains("no text"))
    }
}
