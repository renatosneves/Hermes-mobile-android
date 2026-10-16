package com.m57.hermescontrol.voice

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLivePlannerTest {
    private fun frag(
        user: Boolean,
        text: String,
        at: Long,
    ) = LiveTranscriptFragment(user, text, at, at + 500)

    @Test
    fun delegationPromptUsesLastUserTurnAndKeepsTranscript() {
        val (prompt, context) =
            VoiceLivePlanner.delegationPrompt(
                listOf(
                    frag(true, "What's on ", 0),
                    frag(true, "my calendar?", 600),
                    frag(false, "Let me check.", 1_200),
                    frag(true, "  And the weather  ", 2_000),
                ),
            )
        assertEquals("And the weather", prompt)
        assertEquals(
            "User: What's on my calendar?\nVoice assistant: Let me check.\nUser: And the weather",
            context,
        )
    }

    @Test
    fun contextWindowDropsOldFragments() {
        val window =
            VoiceLivePlanner.contextWindow(
                listOf(frag(true, "old", 0), frag(true, "new", 6 * 60_000L)),
            )
        assertEquals(listOf("new"), window.map { it.text })
    }

    @Test
    fun chunksStayUnderLimitOnSentenceBoundaries() {
        val chunks = VoiceLivePlanner.chunkForCommentary("One two. Three four. Five six.", limit = 12)
        assertEquals(listOf("One two.", "Three four.", "Five six."), chunks)
        assertTrue(VoiceLivePlanner.chunkForCommentary("x".repeat(30), limit = 12).all { it.length <= 12 })
        assertTrue(VoiceLivePlanner.chunkForCommentary("   ").isEmpty())
    }

    @Test
    fun historyKeepsNewestTurnsWithinBudget() {
        val history =
            VoiceLivePlanner.toLiveHistory(
                listOf(true to "first", false to "second", true to "third"),
                maxMessages = 2,
            )
        assertEquals(2, history.size)
        val first = history[0].jsonObject
        assertEquals("assistant", first["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun speakableStripsMarkdownAndLinks() {
        assertEquals(
            "Read the docs now",
            VoiceLivePlanner.speakable("**Read** [the docs](https://x.y) now"),
        )
    }

    @Test
    fun stopCommandsAreRecognised() {
        assertTrue(VoiceLivePlanner.isStopCommand("Stop!"))
        assertTrue(VoiceLivePlanner.isStopCommand("that's all"))
        assertFalse(VoiceLivePlanner.isStopCommand("stop the build on the server"))
    }
}
