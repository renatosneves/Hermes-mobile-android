package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.content.SharedPreferences

/**
 * The chat you last moved to with each bot (a new one, or one from its history) and when, so the
 * Bots home reopens it. Kept in the same file the layout prefs used, so earlier choices carry over.
 */
object BotChatStore {
    private const val PREFS = "bots_layout"
    private const val KEY_SESSION = "last_session:"
    private const val KEY_AT = "last_session_at:"

    private var prefs: SharedPreferences? = null
    private val memory = mutableMapOf<String, SavedChat>()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun all(): Map<String, SavedChat> {
        val stored = prefs?.all.orEmpty()
        val saved =
            stored
                .mapNotNull { (key, value) ->
                    if (!key.startsWith(KEY_SESSION)) return@mapNotNull null
                    val bot = key.removePrefix(KEY_SESSION)
                    val id = (value as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    // Saved before times were kept: treat as old, so the bot's newer activity wins.
                    val at = (stored[KEY_AT + bot] as? Long)?.div(1000.0) ?: 0.0
                    bot to SavedChat(id, at)
                }.toMap()
        return saved + memory
    }

    fun put(
        bot: String,
        sessionId: String,
        atMs: Long = System.currentTimeMillis(),
    ) {
        memory[bot] = SavedChat(sessionId, atMs / 1000.0)
        prefs
            ?.edit()
            ?.putString(KEY_SESSION + bot, sessionId)
            ?.putLong(KEY_AT + bot, atMs)
            ?.apply()
    }
}

/** A chat you moved to with a bot, and when you last used it (epoch seconds). */
data class SavedChat(
    val sessionId: String,
    val at: Double,
)
