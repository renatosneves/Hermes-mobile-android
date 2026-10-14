package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.BotRosterMeta
import com.m57.hermescontrol.data.model.CanonicalSessionInfo
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.ProfileSessionSummary
import com.m57.hermescontrol.data.model.ProfileWorkerSummary
import com.m57.hermescontrol.data.model.SessionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingHandoffsTest {
    private val now = 1_000_000.0

    private fun bot(
        name: String,
        title: String? = null,
        canonical: CanonicalSessionInfo? = null,
        last: ProfileSessionSummary? = null,
        worker: ProfileWorkerSummary? = null,
    ) = ProfileInfo(
        name = name,
        ui_meta =
            title?.let {
                mapOf("hermes-bots" to Json.encodeToJsonElement(BotRosterMeta(title = it)))
            },
        canonical_session = canonical,
        last_session = last,
        worker_session = worker,
    )

    private fun botChat(ageSeconds: Double) =
        CanonicalSessionInfo(
            id = "bc",
            title = "Bot Chat",
            last_active =
                now - ageSeconds,
        )

    private fun user(text: String) = SessionMessage(role = "user", content = JsonPrimitive(text))

    private val roster =
        listOf(
            bot("hermes", "Hermes"),
            bot("research", "Research"),
            bot("chief-of-staff", "Chief of Staff"),
        )

    @Test
    fun `a Bot Chat that moved within 15 minutes is a live hand-off`() {
        val c = IncomingHandoffs.candidateOf(bot("builder", canonical = botChat(14 * 60.0)), now)
        assertEquals(HandoffKind.BOT_CHAT, c?.kind)
        assertEquals("bc", c?.sessionId)
    }

    @Test
    fun `a Bot Chat quiet for more than 15 minutes is not`() {
        assertNull(IncomingHandoffs.candidateOf(bot("builder", canonical = botChat(16 * 60.0)), now))
    }

    @Test
    fun `other chats are not hand-offs`() {
        val chat = CanonicalSessionInfo(id = "c", title = "Planning", last_active = now - 10)
        assertNull(IncomingHandoffs.candidateOf(bot("builder", canonical = chat), now))
    }

    @Test
    fun `the Bot Chat may be the last session`() {
        val last = ProfileSessionSummary(id = "l", title = "bot chat", last_active = now - 60)
        assertEquals("l", IncomingHandoffs.candidateOf(bot("builder", last = last), now)?.sessionId)
    }

    @Test
    fun `a kanban worker within 30 minutes is a board hand-off`() {
        val worker = ProfileWorkerSummary(id = "w", source = "kanban", last_active = now - 25 * 60.0)
        val c = IncomingHandoffs.candidateOf(bot("social", worker = worker), now)
        assertEquals(HandoffKind.BOARD, c?.kind)
        assertNull(c?.sessionId)
    }

    @Test
    fun `a stale or non-kanban worker is not`() {
        val old = ProfileWorkerSummary(id = "w", source = "kanban", last_active = now - 31 * 60.0)
        val tg = ProfileWorkerSummary(id = "w", source = "telegram", last_active = now - 10)
        assertNull(IncomingHandoffs.candidateOf(bot("social", worker = old), now))
        assertNull(IncomingHandoffs.candidateOf(bot("social", worker = tg), now))
    }

    @Test
    fun `a Bot Chat wins over a kanban worker`() {
        val worker = ProfileWorkerSummary(id = "w", source = "kanban", last_active = now - 10)
        val c = IncomingHandoffs.candidateOf(bot("x", canonical = botChat(30.0), worker = worker), now)
        assertEquals(HandoffKind.BOT_CHAT, c?.kind)
    }

    @Test
    fun `the sender is read from the newest Message from turn`() {
        val messages =
            listOf(
                user("Message from Hermes: old news"),
                SessionMessage(role = "assistant", content = JsonPrimitive("ok")),
                user("Message from research: please summarise the deck"),
            )
        assertEquals("Research", IncomingHandoffs.senderOf(messages, roster))
    }

    @Test
    fun `the sender matches a friendly title or slug`() {
        assertEquals(
            "Chief of Staff",
            IncomingHandoffs.senderOf(listOf(user("Message from chief-of-staff: hi")), roster),
        )
        assertEquals(
            "Chief of Staff",
            IncomingHandoffs.senderOf(listOf(user("Message from Chief of Staff (cos) hi")), roster),
        )
        assertEquals("Research", IncomingHandoffs.senderOf(listOf(user("Message from @RESEARCH\nplease")), roster))
    }

    @Test
    fun `a sender outside the roster is kept as written`() {
        assertEquals("Intern", IncomingHandoffs.senderOf(listOf(user("Message from Intern: hi")), roster))
    }

    @Test
    fun `a bot is never its own sender and plain chat has none`() {
        assertNull(IncomingHandoffs.senderOf(listOf(user("Message from hermes: hi")), roster, self = "hermes"))
        assertNull(IncomingHandoffs.senderOf(listOf(user("What's the weather?")), roster))
    }

    @Test
    fun `a hand-off lapses once its window passes`() {
        val chat = IncomingHandoff(HandoffKind.BOT_CHAT, "Research", "bc", now - 14 * 60.0)
        assertTrue(chat.isLive(now))
        assertFalse(chat.isLive(now + 2 * 60.0))
        val board = IncomingHandoff(HandoffKind.BOARD, null, null, now - 29 * 60.0)
        assertTrue(board.isLive(now))
        assertFalse(board.isLive(now + 2 * 60.0))
    }

    @Test
    fun `the open-on bot is the most recently active`() {
        val a = bot("a", canonical = CanonicalSessionInfo(id = "1", last_active = 10.0))
        val b = bot("b", canonical = CanonicalSessionInfo(id = "2", last_active = 50.0))
        assertEquals("b", BotsPresentation.mostRecentBot(listOf(a, b), BotsPresentation::lastActive)?.name)
        assertNull(BotsPresentation.mostRecentBot(emptyList()) { null })
    }
}
