package com.m57.hermescontrol.ui.chat.components

/**
 * What each chat looked like when you left it: the unsent text and where you'd scrolled to.
 * Opening a bot again picks up exactly there. Kept in memory for this run of the app only,
 * for the most recent chats.
 */
object ChatPaneMemory {
    /** A place in the chat list; [key] confirms the row is still the one you were reading. */
    data class Position(
        val index: Int,
        val offset: Int,
        val key: Any?,
    )

    private val drafts = lru<String>()
    private val positions = lru<Position>()

    fun draft(sessionId: String): String = synchronized(this) { drafts[sessionId].orEmpty() }

    fun saveDraft(
        sessionId: String,
        text: String,
    ) = synchronized(this) {
        if (text.isBlank()) drafts.remove(sessionId) else drafts[sessionId] = text
    }

    /** Null when you were at the bottom, which is where a chat opens anyway. */
    fun position(sessionId: String): Position? = synchronized(this) { positions[sessionId] }

    fun savePosition(
        sessionId: String,
        position: Position?,
    ) = synchronized(this) {
        if (position == null) positions.remove(sessionId) else positions[sessionId] = position
    }

    internal fun clear() =
        synchronized(this) {
            drafts.clear()
            positions.clear()
        }

    private fun <T> lru(): LinkedHashMap<String, T> =
        object : LinkedHashMap<String, T>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = size > MAX_CHATS
        }

    private const val MAX_CHATS = 32
}
