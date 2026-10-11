package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LikedMessagesTest {
    private fun msg(
        content: String = "hi",
        rowId: Long? = null,
        restId: String? = null,
    ) = ChatMessage(
        id = "local-id",
        role = MessageRole.ASSISTANT,
        content = content,
        timestamp = 42L,
        serverRowId = rowId,
        restId = restId,
    )

    @Test
    fun prefersServerRowId() {
        assertEquals("s1:r7", likeKey("s1", msg(rowId = 7, restId = "x")))
    }

    @Test
    fun fallsBackToRestId() {
        assertEquals("s1:cabc", likeKey("s1", msg(restId = "abc")))
    }

    @Test
    fun fallsBackToTimestampAndContentHash() {
        assertEquals("s1:t42:${"hi".hashCode()}", likeKey("s1", msg()))
    }

    @Test
    fun scopedBySession() {
        assertNotEquals(likeKey("a", msg(rowId = 1)), likeKey("b", msg(rowId = 1)))
    }
}
