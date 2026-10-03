package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HandoffViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun step(status: ToolStatus = ToolStatus.RUNNING) =
        ChatMessage(
            id = "step",
            role = MessageRole.TOOL,
            content = """{"name":"terminal","args":{"command":"hermes -p hands chat -q go"}}""",
            toolName = "terminal",
            toolStatus = status,
            toolCallId = "call_1",
        )

    @Test
    fun `a hand-off survives switching to another bot and back`() {
        val vm = HandoffViewModel()
        vm.observe("ask-chat", "ask", listOf(step()))
        assertEquals(
            "hands",
            vm.state.value
                ?.selected
                ?.target,
        )

        vm.setExpanded(false)
        vm.observe("cos-chat", "orchestrator", emptyList())
        assertNull(vm.state.value)

        vm.observe("ask-chat", "ask", listOf(step()))
        val back = vm.state.value
        assertNotNull(back)
        assertFalse("still a strip, as you left it", back!!.expanded)
        assertFalse(back.done)
    }

    @Test
    fun `the step finishing is noted, and closing keeps it closed`() {
        val vm = HandoffViewModel()
        vm.observe("ask-chat", "ask", listOf(step()))
        vm.observe("ask-chat", "ask", listOf(step(ToolStatus.COMPLETED)))
        // The run itself is over only once its session ends; the step is just the trigger.
        assertNotNull(
            vm.state.value!!
                .selected.stepDoneAtMs,
        )

        vm.close()
        vm.observe("ask-chat", "ask", listOf(step()))
        assertNull(vm.state.value)
    }
}
