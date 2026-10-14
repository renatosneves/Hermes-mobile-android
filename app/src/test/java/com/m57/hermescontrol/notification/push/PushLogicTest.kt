package com.m57.hermescontrol.notification.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushLogicTest {
    private fun payload(
        type: PushType = PushType.MESSAGE,
        profile: String = "luna",
        sessionId: String? = "s1",
        approvalId: String? = null,
    ) = PushPayload("id-1", type, profile, sessionId, "Luna", "hello", approvalId, 1L)

    private fun bytes(json: String) = json.toByteArray(Charsets.UTF_8)

    // ── payload parsing ───────────────────────────────────────────────

    @Test
    fun parsesFullPayload() {
        val p =
            PushPayloadParser.parse(
                bytes(
                    """{"v":1,"id":"a1","type":"approval","profile":"luna","session_id":"s9",
                    |"title":"Run rm?","body":"rm -rf build","approval_id":"ap7","ts":1700000000000}
                    """.trimMargin(),
                ),
            )
        assertNotNull(p)
        assertEquals("a1", p!!.id)
        assertEquals(PushType.APPROVAL, p.type)
        assertEquals("luna", p.profile)
        assertEquals("s9", p.sessionId)
        assertEquals("ap7", p.approvalId)
        assertEquals(1700000000000L, p.timestampMs)
    }

    @Test
    fun nullSessionAndUnknownFieldsAreFine() {
        val p =
            PushPayloadParser.parse(
                bytes(
                    """{"v":1,"id":"a","type":"scheduled","profile":"x","session_id":null,"title":"t","body":"b","extra":1}""",
                ),
            )
        assertNotNull(p)
        assertNull(p!!.sessionId)
        assertEquals(PushType.SCHEDULED, p.type)
    }

    @Test
    fun truncatesLongTitleAndBody() {
        val long = "x".repeat(500)
        val p =
            PushPayloadParser.parse(
                bytes("""{"id":"a","type":"message","profile":"x","title":"$long","body":"$long"}"""),
            )!!
        assertEquals(PushPayloadParser.MAX_TITLE, p.title.length)
        assertEquals(PushPayloadParser.MAX_BODY, p.body.length)
    }

    @Test
    fun rejectsBadPayloads() {
        assertNull(PushPayloadParser.parse(ByteArray(0)))
        assertNull(PushPayloadParser.parse(bytes("not json")))
        assertNull(PushPayloadParser.parse(bytes("""["array"]""")))
        assertNull(PushPayloadParser.parse(bytes("""{"type":"message","title":"t"}""")))
        assertNull(PushPayloadParser.parse(bytes("""{"id":"a","type":"weird","title":"t"}""")))
        assertNull(PushPayloadParser.parse(bytes("""{"id":"a","type":"message","title":"","body":""}""")))
        assertNull(PushPayloadParser.parse(bytes("""{"v":0,"id":"a","type":"message","title":"t"}""")))
        // An approval with nothing to answer is useless.
        assertNull(PushPayloadParser.parse(bytes("""{"id":"a","type":"approval","title":"t"}""")))
        assertNull(PushPayloadParser.parse(ByteArray(20_000) { 'a'.code.toByte() }))
    }

    @Test
    fun missingProfileFallsBackToDefault() {
        val p = PushPayloadParser.parse(bytes("""{"id":"a","type":"message","title":"t"}"""))!!
        assertEquals("default", p.profile)
    }

    // ── dedupe ────────────────────────────────────────────────────────

    @Test
    fun dedupeDetectsRepeatsAndKeepsOrder() {
        val (first, dupFirst) = PushDedupe.record(emptyList(), "a")
        assertFalse(dupFirst)
        val (second, dupSecond) = PushDedupe.record(first, "a")
        assertTrue(dupSecond)
        assertEquals(listOf("a"), second)
    }

    @Test
    fun dedupeEvictsOldestAtCapacity() {
        var ids = emptyList<String>()
        for (i in 1..5) ids = PushDedupe.record(ids, "id$i", capacity = 3).first
        assertEquals(listOf("id3", "id4", "id5"), ids)
        assertFalse(PushDedupe.record(ids, "id1", capacity = 3).second)
        assertTrue(PushDedupe.record(ids, "id5", capacity = 3).second)
    }

    @Test
    fun dedupeRoundTripsThroughStorageString() {
        val ids = listOf("a", "b", "c")
        assertEquals(ids, PushDedupe.decode(PushDedupe.encode(ids)))
        assertEquals(emptyList<String>(), PushDedupe.decode(null))
        assertEquals(emptyList<String>(), PushDedupe.decode(""))
    }

    // ── quiet hours ───────────────────────────────────────────────────

    @Test
    fun quietHoursSameDayWindow() {
        val start = 13 * 60
        val end = 15 * 60
        assertFalse(QuietHours.isQuiet(12 * 60 + 59, start, end))
        assertTrue(QuietHours.isQuiet(start, start, end))
        assertTrue(QuietHours.isQuiet(14 * 60, start, end))
        assertFalse(QuietHours.isQuiet(end, start, end))
    }

    @Test
    fun quietHoursWrapPastMidnight() {
        val start = 22 * 60
        val end = 7 * 60
        assertTrue(QuietHours.isQuiet(22 * 60, start, end))
        assertTrue(QuietHours.isQuiet(23 * 60 + 59, start, end))
        assertTrue(QuietHours.isQuiet(0, start, end))
        assertTrue(QuietHours.isQuiet(6 * 60 + 59, start, end))
        assertFalse(QuietHours.isQuiet(7 * 60, start, end))
        assertFalse(QuietHours.isQuiet(12 * 60, start, end))
        assertFalse(QuietHours.isQuiet(21 * 60 + 59, start, end))
    }

    @Test
    fun equalBoundsMeanNoQuietWindow() {
        assertFalse(QuietHours.isQuiet(0, 8 * 60, 8 * 60))
        assertFalse(QuietHours.isQuiet(8 * 60, 8 * 60, 8 * 60))
    }

    @Test
    fun formatsMinutesOfDay() {
        assertEquals("22:00", QuietHours.format(22 * 60))
        assertEquals("07:05", QuietHours.format(7 * 60 + 5))
        assertEquals("00:00", QuietHours.format(1440))
    }

    // ── channel and level decision ────────────────────────────────────

    private val noon = 12 * 60
    private val night = 23 * 60

    @Test
    fun channelPerType() {
        assertEquals(PushChannel.APPROVALS, PushDecider.channelFor(PushType.APPROVAL))
        assertEquals(PushChannel.TASKS, PushDecider.channelFor(PushType.TASK))
        assertEquals(PushChannel.MESSAGES, PushDecider.channelFor(PushType.MESSAGE))
        assertEquals(PushChannel.SCHEDULED, PushDecider.channelFor(PushType.SCHEDULED))
    }

    @Test
    fun showsOnTheTypesChannel() {
        val d = PushDecider.decide(payload(PushType.SCHEDULED), PushPrefs(), false, noon)
        assertTrue(d.show)
        assertEquals(PushChannel.SCHEDULED, d.channel)
        assertFalse(d.silent)
    }

    @Test
    fun typeToggleOffDrops() {
        val prefs = PushPrefs(messages = false)
        assertFalse(PushDecider.decide(payload(PushType.MESSAGE), prefs, false, noon).show)
        assertTrue(PushDecider.decide(payload(PushType.TASK), prefs, false, noon).show)
    }

    @Test
    fun botLevelOffDropsEverythingForThatBot() {
        val prefs = PushPrefs(botLevels = mapOf("luna" to BotLevel.OFF))
        for (type in listOf(PushType.APPROVAL, PushType.TASK, PushType.MESSAGE, PushType.SCHEDULED)) {
            assertFalse(PushDecider.decide(payload(type, approvalId = "ap"), prefs, false, noon).show)
        }
        assertTrue(PushDecider.decide(payload(PushType.MESSAGE, profile = "other"), prefs, false, noon).show)
    }

    @Test
    fun botLevelImportantKeepsApprovalsAndTasksOnly() {
        val prefs = PushPrefs(botLevels = mapOf("luna" to BotLevel.IMPORTANT))
        assertTrue(PushDecider.decide(payload(PushType.APPROVAL, approvalId = "ap"), prefs, false, noon).show)
        assertTrue(PushDecider.decide(payload(PushType.TASK), prefs, false, noon).show)
        assertFalse(PushDecider.decide(payload(PushType.MESSAGE), prefs, false, noon).show)
        assertFalse(PushDecider.decide(payload(PushType.SCHEDULED), prefs, false, noon).show)
    }

    @Test
    fun openChatInForegroundDrops() {
        assertFalse(PushDecider.decide(payload(), PushPrefs(), chatOpenInForeground = true, minuteOfDay = noon).show)
    }

    @Test
    fun quietHoursSilenceEverythingButApprovals() {
        val prefs = PushPrefs()
        val message = PushDecider.decide(payload(PushType.MESSAGE), prefs, false, night)
        assertTrue(message.show)
        assertTrue(message.silent)
        val task = PushDecider.decide(payload(PushType.TASK), prefs, false, 3 * 60)
        assertTrue(task.silent)
        val approval = PushDecider.decide(payload(PushType.APPROVAL, approvalId = "ap"), prefs, false, night)
        assertTrue(approval.show)
        assertFalse(approval.silent)
    }

    @Test
    fun quietHoursOffNeverSilences() {
        val prefs = PushPrefs(quietEnabled = false)
        assertFalse(PushDecider.decide(payload(PushType.MESSAGE), prefs, false, night).silent)
    }

    @Test
    fun testPushAlwaysShows() {
        val prefs = PushPrefs(messages = false, botLevels = mapOf("luna" to BotLevel.OFF))
        val d = PushDecider.decide(payload(PushType.TEST), prefs, chatOpenInForeground = true, minuteOfDay = night)
        assertTrue(d.show)
        assertFalse(d.silent)
    }

    @Test
    fun chatOpenNeedsForegroundChatScreenAndSameSession() {
        assertTrue(PushDecider.isChatOpen(true, true, "s1", "s1"))
        assertFalse(PushDecider.isChatOpen(false, true, "s1", "s1"))
        assertFalse(PushDecider.isChatOpen(true, false, "s1", "s1"))
        assertFalse(PushDecider.isChatOpen(true, true, "s2", "s1"))
        assertFalse(PushDecider.isChatOpen(true, true, "s1", null))
        assertFalse(PushDecider.isChatOpen(true, true, null, null))
    }

    // ── prefs serialisation ───────────────────────────────────────────

    @Test
    fun registerJsonCarriesTypesAndBotOverrides() {
        val json =
            PushPrefs(
                tasks = false,
                botLevels = mapOf("b" to BotLevel.OFF, "a" to BotLevel.IMPORTANT),
            ).toRegisterJson().toString()
        assertEquals(
            """{"types":{"approval":true,"task":false,"message":true,"scheduled":true},"bots":{"a":"important","b":"off"}}""",
            json,
        )
    }

    @Test
    fun botLevelsRoundTripAndSkipDefaults() {
        val encoded = PushPrefs.encodeLevels(mapOf("a" to BotLevel.OFF, "b" to BotLevel.ALL))
        assertEquals(mapOf("a" to BotLevel.OFF), PushPrefs.decodeLevels(encoded))
        assertEquals(emptyMap<String, BotLevel>(), PushPrefs.decodeLevels("garbage"))
        assertEquals(emptyMap<String, BotLevel>(), PushPrefs.decodeLevels(null))
        assertEquals(BotLevel.ALL, PushPrefs().levelFor("unknown"))
    }

    // ── safety net interval ───────────────────────────────────────────

    private val now = 10_000_000_000L

    @Test
    fun freshRegistrationIsHealthyAndRare() {
        val healthy = PushSafetyNet.isPushHealthy(true, now - 60_000L, now)
        assertTrue(healthy)
        assertEquals(6 * 60 * 60 * 1000L, PushSafetyNet.intervalMs(healthy))
    }

    @Test
    fun staleOrMissingRegistrationFallsBackToFifteenMinutes() {
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        assertFalse(PushSafetyNet.isPushHealthy(true, now - sevenDays, now))
        assertTrue(PushSafetyNet.isPushHealthy(true, now - sevenDays + 1, now))
        assertFalse(PushSafetyNet.isPushHealthy(false, now - 1, now))
        assertFalse(PushSafetyNet.isPushHealthy(true, 0L, now))
        assertFalse(PushSafetyNet.isPushHealthy(true, now + 5_000L, now))
        assertEquals(15 * 60 * 1000L, PushSafetyNet.intervalMs(false))
    }

    @Test
    fun degradedPushKeepsTheFallback() {
        assertFalse(PushSafetyNet.isPushHealthy(true, now - 1_000L, now, degraded = true))
    }

    @Test
    fun registerRefreshAfterThreeDays() {
        val day = 24 * 60 * 60 * 1000L
        assertFalse(PushSafetyNet.needsRegisterRefresh(now - day, now))
        assertTrue(PushSafetyNet.needsRegisterRefresh(now - 3 * day, now))
        assertTrue(PushSafetyNet.needsRegisterRefresh(0L, now))
    }

    // ── approval choice ───────────────────────────────────────────────

    @Test
    fun approvalButtonsMapToGatewayChoices() {
        assertEquals("once", ApprovalResponder.choiceFor(true))
        assertEquals("deny", ApprovalResponder.choiceFor(false))
    }
}
