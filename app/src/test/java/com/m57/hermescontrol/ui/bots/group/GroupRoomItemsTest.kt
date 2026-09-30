package com.m57.hermescontrol.ui.bots.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRoomItemsTest {
    private fun bot(
        id: String,
        name: String = id,
    ) = GroupChatMessage(id = id, senderName = name, senderDisplayName = name, isUser = false, text = "hi")

    private fun pass(id: String) =
        GroupChatMessage(
            id = id,
            senderName = id,
            senderDisplayName = id,
            isUser = false,
            text = "",
            isSystem = true,
            isPass = true,
        )

    @Test
    fun `consecutive passes fold into one row`() {
        val rows = roomItems(listOf(bot("ceo"), pass("ask"), bot("cos"), pass("hands"), pass("inbox"), pass("link")))
        assertEquals(4, rows.size)
        assertTrue(rows[1] is RoomItem.Message)
        val run = rows[3] as RoomItem.Passes
        assertEquals(listOf("hands", "inbox", "link"), run.messages.map { it.id })
        assertEquals("passes_hands", run.key)
    }

    @Test
    fun `a trailing single pass stays a normal message`() {
        val rows = roomItems(listOf(bot("ceo"), pass("ask")))
        assertEquals(listOf("ceo", "ask"), rows.map { it.key })
    }
}
