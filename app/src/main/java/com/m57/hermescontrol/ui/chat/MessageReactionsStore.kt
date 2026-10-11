package com.m57.hermescontrol.ui.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable per-session key for a reacted message: server row, then REST id, then time and content hash. */
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

/** Phone-only record of the one emoji reaction per bot message; never sent to the server. */
class MessageReactionsStore(
    private val prefs: SharedPreferences,
) {
    private val _reactions = MutableStateFlow(load())

    /** Current map of [likeKey] to emoji. */
    val reactions: StateFlow<Map<String, String>> = _reactions.asStateFlow()

    /** The emoji reacted on [key], or null. */
    fun reactionFor(key: String): String? = _reactions.value[key]

    /** Sets [key]'s reaction to [emoji], or clears it when [emoji] is null or not a known reaction. */
    fun set(
        key: String,
        emoji: String?,
    ) {
        val next = _reactions.value.let { if (emoji in ALLOWED) it + (key to emoji!!) else it - key }
        if (next == _reactions.value) return
        _reactions.value = next
        prefs.edit().putStringSet(KEY, encode(next)).apply()
    }

    private fun load(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        prefs.getStringSet(LEGACY_KEY, null)?.forEach { map[it] = LIKE }
        prefs.getStringSet(KEY, null).orEmpty().forEach { entry ->
            val at = entry.indexOf(SEP)
            if (at > 0 && entry.substring(at + 1) in ALLOWED) map[entry.substring(0, at)] = entry.substring(at + 1)
        }
        if (prefs.contains(LEGACY_KEY)) {
            prefs
                .edit()
                .putStringSet(KEY, encode(map))
                .remove(LEGACY_KEY)
                .apply()
        }
        return map
    }

    private fun encode(map: Map<String, String>): Set<String> =
        map.mapTo(HashSet()) { (key, emoji) -> "$key$SEP$emoji" }

    companion object {
        /** The reactions a user can pick for themselves. */
        val PERSONAL: List<String> = listOf("👍", "❤️", "😂")

        /** Emoji stored when the user approves a message. */
        const val APPROVE = "✅"

        /** Emoji stored when the user rejects a message. */
        const val REJECT = "❌"

        private const val LIKE = "👍"
        private val ALLOWED = PERSONAL + APPROVE + REJECT
        private const val SEP = '\u0001'
        private const val PREFS = "liked_messages"
        private const val KEY = "reactions"
        private const val LEGACY_KEY = "keys"

        /** Opens the store backed by the app's `liked_messages` preferences. */
        fun from(context: Context): MessageReactionsStore =
            MessageReactionsStore(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
