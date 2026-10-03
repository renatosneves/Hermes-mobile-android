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
}
