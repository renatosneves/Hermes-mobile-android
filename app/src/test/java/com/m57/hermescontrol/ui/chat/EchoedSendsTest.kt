package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoedSendsTest {
    private val t0 = 1_760_000_000_000L

    private fun user(
        id: String,
        text: String,
        at: Long,
    ) = ChatMessage(id = id, role = MessageRole.USER, content = text, timestamp = at)

    private fun bot(
        id: String,
        at: Long,
    ) = ChatMessage(id = id, role = MessageRole.ASSISTANT, content = "reply $id", timestamp = at)

    private fun ids(messages: List<ChatMessage>) = messages.map { it.id }

    @Test
    fun copyBelowTheReplyWithTheOriginalTimeIsHidden() {
        val list =
            listOf(
                user("u1", "Por favor, continue.", t0),
                bot("b1", t0 + 60_000),
                user("u2", "Por favor, continue. ", t0),
            )
        assertEquals(listOf("u1", "b1"), ids(messagesWithoutEchoedSends(list)))
    }

    @Test
    fun theSameTextSentAgainAfterTheReplyStays() {
        val list = listOf(user("u1", "continue", t0), bot("b1", t0 + 30_000), user("u2", "continue", t0 + 45_000))
        assertEquals(listOf("u1", "b1", "u2"), ids(messagesWithoutEchoedSends(list)))
    }

    @Test
    fun backToBackDuplicatesWithNoReplyBetweenStay() {
        val list = listOf(user("u1", "hi", t0), user("u2", "hi", t0))
        assertEquals(listOf("u1", "u2"), ids(messagesWithoutEchoedSends(list)))
    }

    @Test
    fun differentTextOrDistantTimesStay() {
        val other = listOf(user("u1", "a", t0), bot("b1", t0 + 60_000), user("u2", "b", t0))
        assertEquals(listOf("u1", "b1", "u2"), ids(messagesWithoutEchoedSends(other)))
        val distant = listOf(user("u1", "a", t0), bot("b1", t0 + 600_000), user("u2", "a", t0 + 300_000))
        assertEquals(listOf("u1", "b1", "u2"), ids(messagesWithoutEchoedSends(distant)))
    }

    @Test
    fun aThirdCopyIsHiddenAndTheNextRealMessageStays() {
        val list =
            listOf(
                user("u1", "go", t0),
                bot("b1", t0 + 60_000),
                user("u2", "go", t0),
                bot("b2", t0 + 90_000),
                user("u3", "go", t0 + 1_000),
                user("u4", "next", t0 + 120_000),
            )
        assertEquals(listOf("u1", "b1", "b2", "u4"), ids(messagesWithoutEchoedSends(list)))
    }

    @Test
    fun unknownTimestampsNeverHide() {
        val list = listOf(user("u1", "a", 0L), bot("b1", 0L), user("u2", "a", 0L))
        assertEquals(listOf("u1", "b1", "u2"), ids(messagesWithoutEchoedSends(list)))
    }
}
