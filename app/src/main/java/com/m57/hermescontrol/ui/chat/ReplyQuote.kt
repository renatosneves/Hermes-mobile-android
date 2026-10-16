package com.m57.hermescontrol.ui.chat

/** The message a pending reply quotes: who wrote it and a one-line excerpt. */
data class ReplyTarget(
    val fromBot: Boolean,
    val excerpt: String,
)

private const val QUOTE_PREFIX = "> "
private val WHITESPACE = Regex("\\s+")

private fun isQuoteLine(line: String): Boolean = line.startsWith(QUOTE_PREFIX) || line == ">"

/** One-line excerpt of [content] for quoting; leading quote lines are dropped so replies never nest. */
fun replyExcerpt(
    content: String,
    max: Int = 160,
): String {
    val body = content.lines().dropWhile(::isQuoteLine).joinToString("\n")
    val flat = body.replace(WHITESPACE, " ").trim()
    return if (flat.length > max) flat.take(max).trimEnd() + "…" else flat
}

/** Prefixes [text] with the quoted excerpt of [target]; unchanged when there is no target. */
fun buildReplyText(
    target: ReplyTarget?,
    text: String,
): String = if (target == null) text else QUOTE_PREFIX + target.excerpt + "\n\n" + text

/** Splits a leading `> ` quote block off [content] into (quote, rest), or null when there is none. */
fun splitLeadingQuote(content: String): Pair<String, String>? {
    val lines = content.lines()
    val quoted = lines.takeWhile { it.startsWith(QUOTE_PREFIX) }
    if (quoted.isEmpty()) return null
    val quote = quoted.joinToString(" ") { it.removePrefix(QUOTE_PREFIX).trim() }
    val rest = lines.drop(quoted.size).joinToString("\n").trim()
    return quote to rest
}
