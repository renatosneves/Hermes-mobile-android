package com.m57.hermescontrol.ui.chat.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPaneMemoryTest {
    @After
    fun tearDown() = ChatPaneMemory.clear()

    @Test
    fun `each chat keeps its own draft`() {
        ChatPaneMemory.saveDraft("cos", "ask about the invoice")
        ChatPaneMemory.saveDraft("ledger", "")

        assertEquals("ask about the invoice", ChatPaneMemory.draft("cos"))
        assertEquals("", ChatPaneMemory.draft("ledger"))
    }

    @Test
    fun `a blank draft or a chat left at the bottom is forgotten`() {
        ChatPaneMemory.saveDraft("cos", "hello")
        ChatPaneMemory.saveDraft("cos", "  ")
        ChatPaneMemory.savePosition("cos", ChatPaneMemory.Position(3, 10, "user-1"))
        ChatPaneMemory.savePosition("cos", null)

        assertEquals("", ChatPaneMemory.draft("cos"))
        assertNull(ChatPaneMemory.position("cos"))
    }

    @Test
    fun `only the most recent chats are kept`() {
        repeat(40) { ChatPaneMemory.saveDraft("s$it", "draft $it") }

        assertEquals("", ChatPaneMemory.draft("s0"))
        assertEquals("draft 39", ChatPaneMemory.draft("s39"))
    }
}
