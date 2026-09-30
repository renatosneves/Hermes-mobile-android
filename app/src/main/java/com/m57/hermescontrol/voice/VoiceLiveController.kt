package com.m57.hermescontrol.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

enum class VoiceLivePhase { CHECKING, UNAVAILABLE, CONNECTING, LISTENING, THINKING, SPEAKING, ENDED }

data class VoiceLiveUi(
    val phase: VoiceLivePhase = VoiceLivePhase.CHECKING,
    val muted: Boolean = false,
    /** What you said last and what the voice said last, for captions. */
    val userCaption: String = "",
    val voiceCaption: String = "",
    /** Why GPT-Live can't start, or why it ended. */
    val message: String? = null,
    /** Tool Hermes is running for the current request, if any. */
    val working: String? = null,
)

/** Hermes' answer to the current request, as it streams. */
data class VoiceReply(
    val id: String,
    val text: String,
    val pending: Boolean,
)

/** What the voice needs from the chat it sits on. */
interface VoiceLiveHost {
    suspend fun checkAvailable(): Pair<Boolean, String?>

    /** Posts our offer to Hermes; returns the answer SDP. */
    suspend fun exchangeOffer(
        sdp: String,
        history: JsonArray,
    ): String

    fun seedHistory(): JsonArray

    /** Sends [prompt] as your message, with the spoken exchange as extra context for the model. */
    fun submit(
        prompt: String,
        voiceContext: String,
    )

    fun isBusy(): Boolean

    fun interrupt()

    /** The newest assistant reply that started after [sinceMs], if any. */
    fun replySince(sinceMs: Long): VoiceReply?

    fun activeTool(): String?
}

/**
 * The conversation engine for GPT-Live on the phone, following the desktop's
 * `useVoiceLiveConversation`: delegations become Hermes turns on the open chat, and the
 * reply streams back sentence by sentence for the voice to say.
 */
class VoiceLiveController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: VoiceLiveHost,
) {
    private val _ui = MutableStateFlow(VoiceLiveUi())
    val ui: StateFlow<VoiceLiveUi> = _ui.asStateFlow()

    private var transport: VoiceLiveTransport? = null
    private val transcript = mutableListOf<LiveTranscriptFragment>()
    private var utterance = StringBuilder()
    private var utteranceJob: Job? = null
    private var feedJob: Job? = null

    @Volatile private var delegationId: String? = null

    @Volatile private var speaking = false

    /** Checks the server can run GPT-Live, then connects. */
    fun start() {
        scope.launch {
            _ui.update { VoiceLiveUi(phase = VoiceLivePhase.CHECKING) }
            val (available, reason) = runCatching { host.checkAvailable() }.getOrElse { false to it.message }
            if (!available) {
                _ui.update { it.copy(phase = VoiceLivePhase.UNAVAILABLE, message = reason) }
                return@launch
            }
            connect()
        }
    }

    private suspend fun connect() {
        _ui.update { it.copy(phase = VoiceLivePhase.CONNECTING, message = null) }
        val session = VoiceLiveTransport(context, scope, Listener())
        transport = session
        try {
            val history = host.seedHistory()
            session.start { sdp -> host.exchangeOffer(sdp, history) }
            refreshPhase()
        } catch (e: Exception) {
            session.abort()
            transport = null
            _ui.update { it.copy(phase = VoiceLivePhase.ENDED, message = e.message ?: "Could not start voice") }
        }
    }

    fun toggleMute() {
        val muted = !_ui.value.muted
        transport?.setMuted(muted)
        _ui.update { it.copy(muted = muted) }
    }

    /** No explicit turn boundary in full duplex; a nudge tells the voice to answer now. */
    fun answerNow() {
        transport?.instruct("The user has finished speaking. Respond now to what they said.")
    }

    fun end() {
        utteranceJob?.cancel()
        feedJob?.cancel()
        delegationId = null
        transport?.close()
        transport = null
        _ui.update { it.copy(phase = VoiceLivePhase.ENDED, working = null) }
    }

    /** For when the screen goes away: say goodbye to the session and drop it at once. */
    fun dispose() {
        utteranceJob?.cancel()
        feedJob?.cancel()
        delegationId = null
        transport?.let {
            it.close()
            it.abort("close_requested")
        }
        transport = null
    }

    private fun refreshPhase() {
        if (transport == null) return
        val phase =
            when {
                speaking -> VoiceLivePhase.SPEAKING
                delegationId != null -> VoiceLivePhase.THINKING
                else -> VoiceLivePhase.LISTENING
            }
        _ui.update { it.copy(phase = phase) }
    }

    private fun onDelegation(id: String) {
        val session = transport ?: return
        val (prompt, voiceContext) = VoiceLivePlanner.delegationPrompt(VoiceLivePlanner.contextWindow(transcript))
        if (prompt.isNotBlank() && VoiceLivePlanner.isStopCommand(prompt)) {
            end()
            return
        }
        // A newer request supersedes one still running, so the answer matches what you asked last.
        if (host.isBusy()) host.interrupt()
        delegationId = id
        refreshPhase()
        val submittedAt = System.currentTimeMillis()
        runCatching { host.submit(prompt, voiceContext) }.onFailure {
            session.speak(id, "Sorry, I could not reach Hermes for that request.")
            delegationId = null
            refreshPhase()
            return
        }
        feedReply(session, id, submittedAt)
    }

    /** Streams the reply into the voice: finished sentences while it's written, the rest at the end. */
    private fun feedReply(
        session: VoiceLiveTransport,
        id: String,
        submittedAt: Long,
    ) {
        feedJob?.cancel()
        feedJob =
            scope.launch {
                var spokenReplyId: String? = null
                var spokenLength = 0
                var lastTool: String? = null
                var observed = false
                while (isActive && delegationId == id && transport === session) {
                    if (host.isBusy()) observed = true
                    val tool = host.activeTool()
                    if (tool != null && tool != lastTool) {
                        lastTool = tool
                        session.think(id, "Hermes is working: $tool. Not done yet.")
                        _ui.update { it.copy(working = tool) }
                    }
                    val reply = host.replySince(submittedAt)
                    if (reply != null) {
                        observed = true
                        if (reply.id != spokenReplyId) {
                            spokenReplyId = reply.id
                            spokenLength = 0
                        }
                        val spoken = VoiceLivePlanner.speakable(reply.text)
                        if (reply.pending || host.isBusy()) {
                            val boundary = spoken.lastIndexOf(". ", spoken.length - 2)
                            if (boundary + 1 > spokenLength) {
                                session.speak(id, spoken.substring(spokenLength, boundary + 1))
                                spokenLength = boundary + 1
                            }
                        } else {
                            if (spoken.length > spokenLength) session.speak(id, spoken.substring(spokenLength))
                            break
                        }
                    } else if (!host.isBusy() &&
                        (observed || System.currentTimeMillis() - submittedAt > SUBMIT_SETTLE_GRACE_MS)
                    ) {
                        session.think(id, "Hermes finished that request without a spoken result.")
                        break
                    }
                    delay(FEED_TICK_MS)
                }
                if (delegationId == id) delegationId = null
                _ui.update { it.copy(working = null) }
                refreshPhase()
            }
    }

    private inner class Listener : VoiceLiveListener {
        override fun onStarted() {
            scope.launch { refreshPhase() }
        }

        override fun onTranscript(fragment: LiveTranscriptFragment) {
            scope.launch {
                transcript.add(fragment)
                if (transcript.size > 2_000) transcript.subList(0, 500).clear()
                if (fragment.fromUser) {
                    val last = transcript.getOrNull(transcript.size - 2)
                    _ui.update {
                        it.copy(
                            userCaption = if (last?.fromUser == true) it.userCaption + fragment.text else fragment.text,
                        )
                    }
                    // Judge a spoken "stop" once the utterance settles ("stop the container" is a request).
                    utterance.append(fragment.text)
                    utteranceJob?.cancel()
                    utteranceJob =
                        scope.launch {
                            delay(UTTERANCE_SETTLE_MS)
                            val said = utterance.toString()
                            utterance = StringBuilder()
                            if (VoiceLivePlanner.isStopCommand(said)) end()
                        }
                } else {
                    val last = transcript.getOrNull(transcript.size - 2)
                    _ui.update {
                        it.copy(
                            voiceCaption =
                                if (last?.fromUser ==
                                    false
                                ) {
                                    it.voiceCaption + fragment.text
                                } else {
                                    fragment.text
                                },
                        )
                    }
                }
            }
        }

        override fun onDelegation(delegationId: String) {
            scope.launch { onDelegation(delegationId) }
        }

        override fun onSpeakingChange(speaking: Boolean) {
            scope.launch {
                this@VoiceLiveController.speaking = speaking
                refreshPhase()
            }
        }

        override fun onError(message: String) {
            scope.launch { _ui.update { it.copy(message = message) } }
        }

        override fun onClosed(
            reason: String,
            usageSeconds: Double?,
        ) {
            scope.launch {
                transport = null
                delegationId = null
                feedJob?.cancel()
                val note =
                    when (reason) {
                        "close_requested" -> null
                        "connection_lost" -> "The voice connection dropped."
                        else -> reason
                    }
                _ui.update {
                    it.copy(
                        phase = VoiceLivePhase.ENDED,
                        working = null,
                        message = note?.let { n -> usageSeconds?.let { s -> "$n (${s.toInt()}s)" } ?: n } ?: it.message,
                    )
                }
            }
        }
    }

    private companion object {
        const val FEED_TICK_MS = 200L
        const val SUBMIT_SETTLE_GRACE_MS = 15_000L
        const val UTTERANCE_SETTLE_MS = 1_500L
    }
}
