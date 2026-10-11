package com.m57.hermescontrol.ui.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable per-session key for a liked message: server row, then REST id, then time and content hash. */
fun likeKey(
    sessionId: String,
    message: ChatMessage,
): String =
    "$sessionId:" +
        (
            message.serverRowId?.let { "r$it" }
                ?: message.canonicalRestId?.let { "c$it" }
                ?: "t${message.timestamp}:${message.content.hashCode()}"
        )

/** Phone-only record of which bot messages the user liked; never sent to the server. */
class LikedMessagesStore(
    private val prefs: SharedPreferences,
) {
    private val _liked = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())

    /** Current set of [likeKey] values. */
    val liked: StateFlow<Set<String>> = _liked.asStateFlow()

    /** True when [key] is currently liked. */
    fun isLiked(key: String): Boolean = key in _liked.value

    /** Flips the like for [key] and persists the new set. */
    fun toggle(key: String) {
        val next = _liked.value.let { if (key in it) it - key else it + key }
        _liked.value = next
        prefs.edit().putStringSet(KEY, next).apply()
    }

    companion object {
        private const val PREFS = "liked_messages"
        private const val KEY = "keys"

        /** Opens the store backed by the app's `liked_messages` preferences. */
        fun from(context: Context): LikedMessagesStore =
            LikedMessagesStore(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
