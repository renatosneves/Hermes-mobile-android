package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `only a live hand-off to another bot counts`() {
        val handoff = tool("hermes -p hands chat -q \"go\"")
        assertEquals(
            "hands",
            HandoffDetector.running(listOf(handoff), self = "ask", ignored = emptySet(), nowMs = now)?.second,
        )
        assertNull(HandoffDetector.running(listOf(handoff), self = "hands", ignored = emptySet(), nowMs = now))
        assertNull(HandoffDetector.running(listOf(handoff), self = "ask", ignored = setOf("t1"), nowMs = now))
        assertNull(
            HandoffDetector.running(
                listOf(tool("hermes -p hands chat -q go", status = ToolStatus.COMPLETED)),
                self = "ask",
                ignored = emptySet(),
                nowMs = now,
            ),
        )
        assertNull(HandoffDetector.running(listOf(handoff.copy(isHistoricalCache = true)), "ask", emptySet(), now))
        assertNull(HandoffDetector.running(listOf(handoff), "ask", emptySet(), nowMs = now + 60 * 60_000L))
    }

    @Test
    fun `picks the newest session started after the step, preferring the CLI run`() {
        val start = now
        val sessions =
            listOf(
                SessionInfo(id = "old", started_at = (start - 600_000) / 1000.0, source = "cli"),
                SessionInfo(id = "tg", started_at = (start + 3_000) / 1000.0, source = "telegram"),
                SessionInfo(id = "run", started_at = (start + 1_000) / 1000.0, source = "cli"),
            )
        assertEquals("run", HandoffDetector.pickSession(sessions, start)?.id)
        assertNull(HandoffDetector.pickSession(sessions.take(1), start))
    }
}
