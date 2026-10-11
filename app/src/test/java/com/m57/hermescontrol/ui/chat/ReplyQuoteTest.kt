package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyQuoteTest {
    @Test
    fun excerptCollapsesWhitespace() {
        assertEquals("a b c", replyExcerpt("  a\n\n b\t c  "))
    }

    @Test
    fun excerptTruncatesWithEllipsis() {
        val out = replyExcerpt("x".repeat(200), max = 10)
        assertEquals("x".repeat(10) + "…", out)
    }

    @Test
    fun excerptStripsLeadingQuoteLines() {
        assertEquals("answer", replyExcerpt("> old\n> older\n\nanswer"))
    }

    @Test
    fun buildReplyTextNullTargetUnchanged() {
        assertEquals("hi", buildReplyText(null, "hi"))
    }

    @Test
    fun buildReplyTextPrefixesQuote() {
        assertEquals("> hello\n\nhi", buildReplyText(ReplyTarget(true, "hello"), "hi"))
    }

    @Test
    fun splitNoQuoteIsNull() {
        assertNull(splitLeadingQuote("plain text"))
        assertNull(splitLeadingQuote("text\n> not leading"))
    }

    @Test
    fun splitSingleQuote() {
        assertEquals("hello" to "hi", splitLeadingQuote("> hello\n\nhi"))
    }

    @Test
    fun splitMultiLineQuote() {
        assertEquals("a b" to "rest", splitLeadingQuote("> a\n> b\n\nrest"))
    }

    @Test
    fun replyToReplyDoesNotNest() {
        val first = buildReplyText(ReplyTarget(false, "bot said"), "my reply")
        val second = buildReplyText(ReplyTarget(false, replyExcerpt(first)), "again")
        assertEquals("my reply", replyExcerpt(first.substringAfter("\n\n")))
        assertEquals("my reply" to "again", splitLeadingQuote(second)?.let { it.first to it.second })
    }
}
