package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffDetectorTest {
    private val now = 1_800_000_000_000L

    private fun tool(
        command: String,
        status: ToolStatus = ToolStatus.RUNNING,
        id: String = "t1",
    ) = ChatMessage(
        id = id,
        role = MessageRole.TOOL,
        // Same shape as a live tool.start payload, quotes JSON-escaped.
        content = """{"tool_id":"call_1","name":"terminal","args":{"command":"${command.replace("\"", "\\\"")}"}}""",
        toolName = "terminal",
        toolStatus = status,
        timestamp = now - 5_000,
    )

    // tool.complete for kanban_create: args carry the assignee, result the new task.
    private fun boardTask(
        assignee: String,
        id: String,
        status: ToolStatus = ToolStatus.COMPLETED,
    ) = ChatMessage(
        id = id,
        role = MessageRole.TOOL,
        content =
            """{"tool_id":"call_$id","name":"kanban_create","args":{"title":"Draft post","assignee":"$assignee"},""" +
                """"result":"{\"ok\": true, \"task_id\": \"t_9\"}"}""",
        toolName = "kanban_create",
        toolStatus = status,
        timestamp = now - 5_000,
    )

    @Test
    fun `finds the bot a hermes chat command hands work to`() {
        assertEquals(
            "hands",
            HandoffDetector.targetOf(tool("hermes -p hands chat -q \"Message from Ask (@ask): install MiroFish\"")),
        )
        assertEquals("link", HandoffDetector.targetOf(tool("hermes --profile=link chat -q 'hi'")))
        assertEquals("work", HandoffDetector.targetOf(tool("cd /opt && hermes chat -q \"x\" -p work")))
    }

    @Test
    fun `ignores other commands and other roles`() {
        assertNull(HandoffDetector.targetOf(tool("hermes -p hands status")))
        assertNull(HandoffDetector.targetOf(tool("ls -la")))
        assertNull(
            HandoffDetector.targetOf(
                ChatMessage(role = MessageRole.ASSISTANT, content = "I ran hermes -p hands chat for you"),
            ),
        )
    }

    @Test
    fun `only a live CLI hand-off to another bot counts`() {
        val handoff = tool("hermes -p hands chat -q \"go\"")
        val found = HandoffDetector.detect(listOf(handoff), self = "ask", known = emptySet(), nowMs = now)
        assertEquals(listOf("hands" to HandoffKind.CLI), found.map { it.target to it.kind })
        assertTrue(HandoffDetector.detect(listOf(handoff), "hands", emptySet(), now).isEmpty())
        assertTrue(HandoffDetector.detect(listOf(handoff), "ask", setOf("t1"), now).isEmpty())
        assertTrue(
            HandoffDetector
                .detect(
                    listOf(tool("hermes -p hands chat -q go", ToolStatus.COMPLETED)),
                    "ask",
                    emptySet(),
                    now,
                ).isEmpty(),
        )
        assertTrue(
            HandoffDetector.detect(listOf(handoff.copy(isHistoricalCache = true)), "ask", emptySet(), now).isEmpty(),
        )
        assertTrue(HandoffDetector.detect(listOf(handoff), "ask", emptySet(), nowMs = now + 60 * 60_000L).isEmpty())
    }

    @Test
    fun `board tasks from Chief of Staff count once created, one per bot, in order`() {
        val messages = listOf(boardTask("link", "a"), boardTask("ledger", "b"), boardTask("orchestrator", "c"))
        val found = HandoffDetector.detect(messages, self = "orchestrator", known = emptySet(), nowMs = now)
        assertEquals(listOf("link", "ledger"), found.map { it.target })
        assertTrue(found.all { it.kind == HandoffKind.BOARD })
        assertTrue(
            HandoffDetector.detect(listOf(boardTask("link", "x", ToolStatus.FAILED)), "cos", emptySet(), now).isEmpty(),
        )
    }

    @Test
    fun `a routing step that names another bot counts, given the roster`() {
        val bots = setOf("orchestrator", "link", "ledger")
        val route = tool("jev-route link \"Draft the LinkedIn post\"")
        assertEquals("link", HandoffDetector.namedTargetOf(route, bots, self = "orchestrator"))
        assertNull(HandoffDetector.namedTargetOf(tool("cat notes/link.md"), bots, self = "orchestrator"))
        assertNull(HandoffDetector.namedTargetOf(route, emptySet(), self = "orchestrator"))
        val found = HandoffDetector.detect(listOf(route), "orchestrator", emptySet(), now, bots)
        assertEquals(listOf("link" to HandoffKind.CLI), found.map { it.target to it.kind })
    }

    @Test
    fun `picks the run that started after the step, from the expected source`() {
        val start = now
        val sessions =
            listOf(
                SessionInfo(id = "old", started_at = (start - 600_000) / 1000.0, source = "cli"),
                SessionInfo(id = "tg", started_at = (start + 3_000) / 1000.0, source = "telegram"),
                SessionInfo(id = "run", started_at = (start + 1_000) / 1000.0, source = "cli"),
                SessionInfo(id = "task", started_at = (start + 20_000) / 1000.0, source = "kanban"),
                SessionInfo(id = "task2", started_at = (start + 40_000) / 1000.0, source = "kanban"),
            )
        assertEquals("run", HandoffDetector.pickSession(sessions, start)?.id)
        assertEquals("task", HandoffDetector.pickSession(sessions, start, HandoffKind.BOARD)?.id)
        assertEquals(
            "task2",
            HandoffDetector.pickSession(sessions, start, HandoffKind.BOARD, taken = setOf("task"))?.id,
        )
        assertNull(HandoffDetector.pickSession(sessions.take(1), start))
    }

    // tool.complete for message_agent: the ack says queued; the run happens later in the target's Bot Chat.
    private fun dm(
        target: String,
        id: String = "d1",
        callId: String = "call_$id",
    ) = ChatMessage(
        id = id,
        role = MessageRole.TOOL,
        content =
            """{"tool_id":"$callId","name":"message_agent","args":{"target":"$target","message":"Try WhatsApp"},""" +
                """"result":"{\"status\": \"queued\", \"delivery_id\": \"x\"}"}""",
        toolName = "message_agent",
        toolCallId = callId,
        toolStatus = ToolStatus.COMPLETED,
        timestamp = now - 5_000,
    )

    @Test
    fun `a message_agent DM counts once finished, by profile or friendly name`() {
        val bots = setOf("ask", "chief-of-staff", "link")
        assertEquals("chief-of-staff", HandoffDetector.dmTargetOf(dm("Chief of Staff"), bots))
        assertEquals("link", HandoffDetector.dmTargetOf(dm("@link"), bots))
        val found = HandoffDetector.detect(listOf(dm("chief-of-staff")), "ask", emptySet(), now, bots)
        assertEquals(listOf("chief-of-staff" to HandoffKind.BOT_CHAT), found.map { it.target to it.kind })
        // The saved copy of the same step has another id but the same call id: not a second hand-off.
        assertTrue(
            HandoffDetector
                .detect(
                    listOf(dm("chief-of-staff", id = "saved", callId = "call_d1")),
                    "ask",
                    setOf("call_d1"),
                    now,
                    bots,
                ).isEmpty(),
        )
    }

    @Test
    fun `a DM follows the bot's Bot Chat even though it started long ago`() {
        val sessions =
            listOf(
                SessionInfo(id = "tg", started_at = (now + 3_000) / 1000.0, source = "telegram"),
                SessionInfo(id = "chat", title = "Bot Chat", started_at = 1_000.0, last_active = now / 1000.0),
            )
        assertEquals("chat", HandoffDetector.pickSession(sessions, now, HandoffKind.BOT_CHAT)?.id)
    }

    @Test
    fun `only the part of the Bot Chat after the DM is shown`() {
        val old = ChatMessage(id = "1", role = MessageRole.ASSISTANT, content = "earlier", timestamp = now - 3_600_000)
        val delivered =
            ChatMessage(
                id = "2",
                role = MessageRole.USER,
                content = "Message from Ask (@ask): hi",
                timestamp =
                    now + 2_000,
            )
        val reply = ChatMessage(id = "3", role = MessageRole.ASSISTANT, content = "Done", timestamp = now + 9_000)
        assertEquals(listOf("2", "3"), sinceHandoff(listOf(old, delivered, reply), now).map { it.id })
        assertTrue(sinceHandoff(listOf(old), now).isEmpty())
    }
}
