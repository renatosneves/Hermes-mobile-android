package com.m57.hermescontrol.ui.chat

/** Two copies of one send further apart than this are treated as separate messages. */
internal const val ECHO_WINDOW_MS = 120_000L

/**
 * Hides a second copy of a sent message that lands after the bot's reply.
 *
 * After a reconnect or a context compaction the transcript can hold the same user message twice:
 * once above the reply and once below it, both stamped with the original send time. A copy counts as
 * an echo only when all of these hold, so a message the user really sent again always stays:
 * - the nearest earlier user message has the same text and attachment count;
 * - the two were stamped within [ECHO_WINDOW_MS] of each other;
 * - at least one non-user row sits between them;
 * - the copy is stamped no later than the first of those rows, which a genuinely new message
 *   sent after the reply started could never be.
 */
internal fun messagesWithoutEchoedSends(messages: List<ChatMessage>): List<ChatMessage> {
    var previousUser: ChatMessage? = null
    var oldestBetween = 0L
    var rowsBetween = 0
    var hidden: MutableSet<String>? = null
    for (message in messages) {
        if (message.role != MessageRole.USER) {
            rowsBetween++
            val at = message.timestamp
            if (at > 0L && (oldestBetween == 0L || at < oldestBetween)) oldestBetween = at
            continue
        }
        val previous = previousUser
        if (previous != null && rowsBetween > 0 && message.isEchoOf(previous, oldestBetween)) {
            (hidden ?: mutableSetOf<String>().also { hidden = it }).add(message.id)
            // The original stays the anchor, so a third copy is checked against it too.
        } else {
            previousUser = message
        }
        rowsBetween = 0
        oldestBetween = 0L
    }
    val echoes = hidden ?: return messages
    return messages.filterNot { it.id in echoes }
}

private fun ChatMessage.isEchoOf(
    original: ChatMessage,
    firstReplyAt: Long,
): Boolean =
    timestamp > 0L &&
        original.timestamp > 0L &&
        firstReplyAt > 0L &&
        timestamp <= firstReplyAt &&
        kotlin.math.abs(timestamp - original.timestamp) <= ECHO_WINDOW_MS &&
        attachments.orEmpty().size == original.attachments.orEmpty().size &&
        content.normalisedForEcho().isNotEmpty() &&
        content.normalisedForEcho() == original.content.normalisedForEcho()

private fun String.normalisedForEcho(): String = trim().replace(Regex("\\s+"), " ")
