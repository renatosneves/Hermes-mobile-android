package com.m57.hermescontrol.ui.chat.markdown

import com.m57.hermescontrol.ui.chat.fullbleed.wordEndOffsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSplitTest {
    @Test
    fun `no blank line yet means nothing is settled`() {
        assertEquals(0, streamingSettledLength("First paragraph still being writ"))
    }

    @Test
    fun `settles everything up to the last blank line`() {
        val text = "One.\n\nTwo.\n\nThree is still"
        val cut = streamingSettledLength(text)
        assertEquals("One.\n\nTwo.\n\n", text.substring(0, cut))
        assertEquals("Three is still", text.substring(cut))
    }

    @Test
    fun `never cuts inside an open code fence`() {
        val text = "Intro.\n\n```kotlin\nval a = 1\n\nval b = 2"
        assertEquals("Intro.\n\n", text.substring(0, streamingSettledLength(text)))
    }

    @Test
    fun `cuts after a closed code fence`() {
        val text = "```\ncode\n\nmore\n```\n\nAfter"
        assertEquals("After", text.substring(streamingSettledLength(text)))
    }

    @Test
    fun `a trailing blank line is not a cut yet`() {
        assertEquals(0, streamingSettledLength("One.\n"))
    }

    @Test
    fun `word ends match split on spaces`() {
        val text = "alpha beta  gamma"
        val ends = wordEndOffsets(text)
        val words = text.split(" ")
        assertEquals(words.size, ends.size)
        for (n in 1..words.size) {
            assertEquals(words.take(n).joinToString(" "), text.substring(0, ends[n - 1]))
        }
        assertArrayEquals(intArrayOf(0), wordEndOffsets(""))
    }
}
