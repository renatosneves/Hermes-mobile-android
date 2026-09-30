package com.m57.hermescontrol.voice

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One piece of the spoken conversation, as GPT-Live transcribes it. */
data class LiveTranscriptFragment(
    val fromUser: Boolean,
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Pure helpers for GPT-Live voice chat, ported from the desktop app
 * (`apps/desktop/src/lib/voice-live.ts` and `use-voice-live-conversation.ts`).
 * GPT-Live talks and listens; each real request is handed to Hermes as a normal turn,
 * and Hermes' reply is fed back for the voice to say.
 */
object VoiceLivePlanner {
    /** Vendor cap is 500 tokens per append; ~4 chars a token with headroom. */
    const val APPEND_CHAR_LIMIT = 1_400

    private const val CONTEXT_WINDOW_MS = 5 * 60_000L
    private const val CONTEXT_MAX_FRAGMENTS = 80

    private val STOP_PHRASES =
        setOf(
            "stop",
            "stop it",
            "stop talking",
            "that's all",
            "that is all",
            "goodbye",
            "bye",
            "end call",
            "end the call",
            "hang up",
        )

    /** Recent conversation, oldest first, bounded by time and count. */
    fun contextWindow(transcript: List<LiveTranscriptFragment>): List<LiveTranscriptFragment> {
        val last = transcript.lastOrNull() ?: return emptyList()
        val floor = last.endMs - CONTEXT_WINDOW_MS
        return transcript.filter { it.endMs >= floor }.takeLast(CONTEXT_MAX_FRAGMENTS)
    }

    /**
     * The Hermes turn for a delegation: `prompt` is what the user last said (the chat row),
     * `context` the recent spoken exchange, which rides the model input only.
     */
    fun delegationPrompt(context: List<LiveTranscriptFragment>): Pair<String, String> {
        val turns = mutableListOf<Pair<Boolean, StringBuilder>>()
        for (fragment in context) {
            val last = turns.lastOrNull()
            if (last != null && last.first == fragment.fromUser) {
                last.second.append(fragment.text)
            } else {
                turns.add(fragment.fromUser to StringBuilder(fragment.text))
            }
        }
        val prompt =
            turns
                .lastOrNull { it.first }
                ?.second
                ?.toString()
                .orEmpty()
                .collapse()
        val transcript =
            turns
                .map { (fromUser, text) ->
                    "${if (fromUser) "User" else "Voice assistant"}: ${text.toString().collapse()}"
                }.filterNot { it.endsWith(": ") }
                .joinToString("\n")
        return prompt.ifBlank { transcript.takeLast(400) } to transcript
    }

    /** Splits a reply into append-sized chunks on sentence boundaries. */
    fun chunkForCommentary(
        text: String,
        limit: Int = APPEND_CHAR_LIMIT,
    ): List<String> {
        val clean = text.collapse()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= limit) return listOf(clean)
        val chunks = mutableListOf<String>()
        var current = ""
        for (sentence in clean.split(Regex("""(?<=[.!?])\s+"""))) {
            if (sentence.length > limit) {
                if (current.isNotEmpty()) {
                    chunks.add(current)
                    current = ""
                }
                sentence.chunked(limit).forEach(chunks::add)
                continue
            }
            val candidate = if (current.isEmpty()) sentence else "$current $sentence"
            if (candidate.length > limit) {
                chunks.add(current)
                current = sentence
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    /** Seed history for a new session from the chat's text turns (newest kept). */
    fun toLiveHistory(
        turns: List<Pair<Boolean, String>>,
        maxMessages: Int = 24,
        maxChars: Int = 6_000,
    ): JsonArray {
        val out = ArrayDeque<JsonObject>()
        var budget = maxChars
        for ((fromUser, raw) in turns.asReversed()) {
            val text = raw.collapse().take(1_200)
            if (text.isEmpty()) continue
            if (out.size >= maxMessages || budget - text.length < 0) break
            budget -= text.length
            out.addFirst(
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("message"),
                        "role" to JsonPrimitive(if (fromUser) "user" else "assistant"),
                        "content" to
                            JsonArray(
                                listOf(
                                    JsonObject(
                                        mapOf(
                                            "type" to JsonPrimitive(if (fromUser) "input_text" else "output_text"),
                                            "text" to JsonPrimitive(text),
                                        ),
                                    ),
                                ),
                            ),
                    ),
                ),
            )
        }
        return JsonArray(out.toList())
    }

    /** Markdown and links read badly aloud; keep the words. */
    fun speakable(text: String): String =
        text
            .replace(Regex("""```[\s\S]*?```"""), " (code omitted) ")
            .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), " ")
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace(Regex("""https?://\S+"""), " link ")
            .replace(Regex("""[*_`#>|~]+"""), "")
            .replace(Regex("""^\s*[-•]\s+""", RegexOption.MULTILINE), "")
            .collapse()

    /** A spoken "stop" (and friends) ends the conversation instead of becoming a request. */
    fun isStopCommand(utterance: String): Boolean {
        val normalised =
            utterance
                .lowercase()
                .replace(Regex("""[^\p{L}\p{N}' ]"""), " ")
                .collapse()
        return normalised in STOP_PHRASES
    }

    private fun String.collapse(): String = replace(Regex("""\s+"""), " ").trim()
}
