package com.m57.hermescontrol.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.m57.hermescontrol.diagnostics.FreezeReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the live voice session reports back. Called on background threads, never the main one. */
interface VoiceLiveListener {
    fun onStarted()

    fun onTranscript(fragment: LiveTranscriptFragment)

    /** GPT-Live wants Hermes to handle something; the delegation carries no text of its own. */
    fun onDelegation(delegationId: String)

    fun onSpeakingChange(speaking: Boolean)

    fun onError(message: String)

    fun onClosed(
        reason: String,
        usageSeconds: Double?,
    )
}

/**
 * The WebRTC side of GPT-Live: microphone up, voice down, and the `oai-events` data channel.
 * The SDP offer goes to Hermes ([exchangeOffer]), which trades it with OpenAI so the API key
 * never reaches the phone. Mirrors `VoiceLiveSession` in the desktop app.
 */
class VoiceLiveTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listener: VoiceLiveListener,
) {
    private var factory: PeerConnectionFactory? = null
    private var audioModule: JavaAudioDeviceModule? = null
    private var peer: PeerConnection? = null
    private var events: DataChannel? = null
    private var micTrack: AudioTrack? = null
    private var speakingJob: Job? = null
    private var closeJob: Job? = null
    private val finalized = AtomicBoolean(false)
    private val eventCounter = AtomicInteger(0)
    private val iceComplete = CompletableDeferred<Unit>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Every WebRTC call runs on this one thread. Its Java API blocks the caller until WebRTC's own
     * threads answer, so calls from the main thread could freeze the app, and a send or stats
     * poll racing the teardown on another thread could touch a connection being disposed.
     */
    private val rtcExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "voice-rtc") }
    private val rtc = rtcExecutor.asCoroutineDispatcher()

    @Volatile var sessionId: String? = null
        private set

    /** Runs [block] on the WebRTC thread; ignored once the session is torn down. */
    private fun onRtc(block: () -> Unit) {
        try {
            rtcExecutor.execute { runCatching(block) }
        } catch (_: RejectedExecutionException) {
            // Already shut down: nothing left to talk to.
        }
    }

    /**
     * Opens the microphone and negotiates the session. [exchangeOffer] posts our SDP offer to
     * Hermes and returns the answer SDP (or throws).
     */
    suspend fun start(exchangeOffer: suspend (String) -> String) = withContext(rtc) { startOnRtc(exchangeOffer) }

    private suspend fun startOnRtc(exchangeOffer: suspend (String) -> String) {
        check(peer == null) { "GPT-Live session already started" }
        ensureInitialised(context)
        routeAudioForCall()

        val module =
            JavaAudioDeviceModule
                .builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
        audioModule = module
        val peerFactory = PeerConnectionFactory.builder().setAudioDeviceModule(module).createPeerConnectionFactory()
        factory = peerFactory

        val config =
            PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }
        val connection =
            peerFactory.createPeerConnection(config, PeerObserver())
                ?: error("Could not create the voice connection")
        peer = connection

        val constraints =
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
        val source = peerFactory.createAudioSource(constraints)
        val track = peerFactory.createAudioTrack("hermes-mic", source)
        micTrack = track
        connection.addTransceiver(
            track,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV),
        )

        // Register the data channel before the offer so its m-line is negotiated.
        val channel = connection.createDataChannel("oai-events", DataChannel.Init())
        events = channel
        channel.registerObserver(ChannelObserver())

        val offer = connection.awaitOffer()
        connection.awaitSetLocal(offer)
        // Trickle is fine: the vendor answers with the candidates it has.
        withTimeoutOrNull(ICE_GATHER_TIMEOUT_MS) { iceComplete.await() }
        val sdp = connection.localDescription?.description ?: error("Missing local SDP offer")

        val answer = exchangeOffer(sdp)
        connection.awaitSetRemote(SessionDescription(SessionDescription.Type.ANSWER, answer))
        startSpeakingProbe(connection)
    }

    /** Quiet progress for the voice ("Hermes is running the tests…"). */
    fun think(
        delegationId: String?,
        content: String,
    ) {
        val text = content.trim().take(VoiceLivePlanner.APPEND_CHAR_LIMIT)
        if (text.isNotEmpty()) onRtc { send("session.thinking.append", delegationId, text, "think") }
    }

    /** A result the voice should say aloud (it paraphrases). */
    fun speak(
        delegationId: String?,
        content: String,
    ) {
        val chunks = VoiceLivePlanner.chunkForCommentary(content)
        onRtc { chunks.forEach { send("session.commentary.append", delegationId, it, "say") } }
    }

    /** Steers the voice for the rest of the conversation. */
    fun instruct(content: String) {
        val text = content.trim().take(VoiceLivePlanner.APPEND_CHAR_LIMIT)
        if (text.isNotEmpty()) onRtc { send("session.instructions.append", null, text, "instr") }
    }

    fun setMuted(muted: Boolean) =
        onRtc {
            // Silence the recorder itself as well as the track: a disabled track alone did not
            // stop the voice hearing you on some phones.
            audioModule?.setMicrophoneMute(muted)
            micTrack?.setEnabled(!muted)
            sendRaw(
                JsonObject(
                    mapOf(
                        "type" to
                            JsonPrimitive(
                                if (muted) "session.input_audio.mute" else "session.input_audio.unmute",
                            ),
                        "event_id" to JsonPrimitive(nextEventId(if (muted) "mute" else "unmute")),
                    ),
                ),
            )
        }

    /** Immediate teardown, for a start that failed half way. */
    fun abort(reason: String = "start_failed") = finish(reason, null)

    /** Graceful close: ask for `session.closed`, tear down after it (or a timeout). */
    fun close() {
        if (finalized.get()) return
        closeJob =
            scope.launch {
                delay(CLOSE_TIMEOUT_MS)
                finish("close_requested", null)
            }
        onRtc {
            if (!finalized.get() && !sendRaw(JsonObject(mapOf("type" to JsonPrimitive("session.close"))))) {
                finish("close_requested", null)
            }
        }
    }

    private fun send(
        type: String,
        delegationId: String?,
        content: String,
        prefix: String,
    ) {
        sendRaw(
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive(type),
                    "content" to JsonPrimitive(content),
                    "delegation_id" to JsonPrimitive(delegationId),
                    "event_id" to JsonPrimitive(nextEventId(prefix)),
                ),
            ),
        )
    }

    /** Only on the WebRTC thread (see [rtc]). */
    private fun sendRaw(event: JsonObject): Boolean {
        if (finalized.get()) return false
        FreezeReporter.note("Voice send ${event.str("type")}")
        val channel = events ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        val bytes = event.toString().toByteArray(Charsets.UTF_8)
        return channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    private fun nextEventId(prefix: String) = "${prefix}_${eventCounter.incrementAndGet()}"

    private fun handleEvent(raw: String) {
        val event = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        event.str("type")?.takeUnless { it.endsWith(".delta") }?.let { FreezeReporter.note("Voice event $it") }
        when (event.str("type")) {
            "session.started" -> {
                sessionId = event.obj("session")?.str("id") ?: sessionId
                listener.onStarted()
            }

            "session.input_transcript.delta", "session.output_transcript.delta" -> {
                listener.onTranscript(
                    LiveTranscriptFragment(
                        fromUser = event.str("type") == "session.input_transcript.delta",
                        text = event.str("delta").orEmpty(),
                        startMs = event.long("start_ms") ?: 0L,
                        endMs = event.long("end_ms") ?: 0L,
                    ),
                )
            }

            "session.delegation.created" -> {
                event.obj("delegation")?.str("id")?.let(listener::onDelegation)
            }

            "error" -> {
                val error = event.obj("error")
                // Late appends after our own close are expected noise.
                if (error?.str("code") == "context_injection_incomplete") return
                listener.onError(error?.str("message") ?: "GPT-Live error")
            }

            "session.closed" -> {
                val usage =
                    event
                        .obj(
                            "usage",
                        )?.get("seconds")
                        ?.let { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() }
                finish(event.str("reason") ?: "closed", usage)
            }
        }
    }

    /** The voice is "speaking" while the incoming audio has level; polled from WebRTC stats. */
    private fun startSpeakingProbe(connection: PeerConnection) {
        speakingJob =
            scope.launch {
                var lastSpeaking = false
                var quietTicks = 0
                while (isActive && !finalized.get()) {
                    val level =
                        withTimeoutOrNull(STATS_TIMEOUT_MS) {
                            withContext(rtc) { if (finalized.get()) null else connection.inboundAudioLevel() }
                        }
                    val loud = (level ?: 0.0) > SPEAKING_LEVEL
                    quietTicks = if (loud) 0 else quietTicks + 1
                    val speaking = loud || (lastSpeaking && quietTicks < QUIET_TICKS_TO_STOP)
                    if (speaking != lastSpeaking) {
                        lastSpeaking = speaking
                        listener.onSpeakingChange(speaking)
                    }
                    delay(SPEAKING_POLL_MS)
                }
            }
    }

    private fun finish(
        reason: String,
        usageSeconds: Double?,
    ) {
        if (!finalized.compareAndSet(false, true)) return
        closeJob?.cancel()
        speakingJob?.cancel()
        // Never dispose a PeerConnection from its own callback thread (it deadlocks): the WebRTC
        // thread runs it after anything already queued, so nothing else touches it meanwhile.
        try {
            rtcExecutor.execute {
                runCatching { teardown(reason, usageSeconds) }
                rtcExecutor.shutdown()
            }
        } catch (_: RejectedExecutionException) {
            listener.onClosed(reason, usageSeconds)
        }
    }

    private fun teardown(
        reason: String,
        usageSeconds: Double?,
    ) {
        runCatching { micTrack?.setEnabled(false) }
        runCatching { events?.unregisterObserver() }
        runCatching { events?.close() }
        runCatching { peer?.close() }
        runCatching { events?.dispose() }
        runCatching { peer?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { audioModule?.release() }
        events = null
        peer = null
        factory = null
        audioModule = null
        restoreAudio()
        listener.onClosed(reason, usageSeconds)
    }

    @Suppress("DEPRECATION")
    private fun routeAudioForCall() {
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Headphones win when connected; otherwise the loudspeaker, not the earpiece.
            val devices = audioManager.availableCommunicationDevices
            val preferred =
                devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            preferred?.let { audioManager.setCommunicationDevice(it) }
        } else {
            audioManager.isSpeakerphoneOn = true
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreAudio() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = previousAudioMode
        }
    }

    private inner class PeerObserver : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state == PeerConnection.IceConnectionState.FAILED) finish("connection_lost", null)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) iceComplete.complete(Unit)
        }

        override fun onIceCandidate(candidate: IceCandidate?) = Unit

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddStream(stream: MediaStream?) = Unit

        override fun onRemoveStream(stream: MediaStream?) = Unit

        override fun onDataChannel(channel: DataChannel?) = Unit

        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(
            receiver: RtpReceiver?,
            streams: Array<out MediaStream>?,
        ) = Unit

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
            if (state == PeerConnection.PeerConnectionState.FAILED ||
                state == PeerConnection.PeerConnectionState.DISCONNECTED
            ) {
                finish("connection_lost", null)
            }
        }
    }

    private inner class ChannelObserver : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit

        override fun onStateChange() {
            if (events?.state() == DataChannel.State.CLOSED && !finalized.get()) finish("connection_lost", null)
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) return
            val data = buffer.data
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            handleEvent(String(bytes, Charsets.UTF_8))
        }
    }

    companion object {
        private const val CLOSE_TIMEOUT_MS = 15_000L
        private const val ICE_GATHER_TIMEOUT_MS = 10_000L
        private const val SPEAKING_POLL_MS = 150L
        private const val SPEAKING_LEVEL = 0.02
        private const val QUIET_TICKS_TO_STOP = 4
        private const val STATS_TIMEOUT_MS = 1_000L

        @Volatile private var initialised = false

        private fun ensureInitialised(context: Context) {
            if (initialised) return
            synchronized(this) {
                if (initialised) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(context.applicationContext)
                        .createInitializationOptions(),
                )
                initialised = true
            }
        }
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private suspend fun PeerConnection.awaitOffer(): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = cont.resume(description)

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String?) =
                    cont.resumeWithException(IllegalStateException(error ?: "Could not create the voice offer"))

                override fun onSetFailure(error: String?) = Unit
            },
            MediaConstraints(),
        )
    }

private suspend fun PeerConnection.awaitSetLocal(description: SessionDescription) =
    awaitSet { observer -> setLocalDescription(observer, description) }

private suspend fun PeerConnection.awaitSetRemote(description: SessionDescription) =
    awaitSet { observer -> setRemoteDescription(observer, description) }

private suspend fun awaitSet(block: (SdpObserver) -> Unit) =
    suspendCancellableCoroutine { cont ->
        block(
            object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) = Unit

                override fun onSetSuccess() = cont.resume(Unit)

                override fun onCreateFailure(error: String?) = Unit

                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(IllegalStateException(error ?: "Voice negotiation failed"))
            },
        )
    }

/** Loudness of the voice coming in (0..1), from the inbound audio RTP stats. */
private suspend fun PeerConnection.inboundAudioLevel(): Double? =
    suspendCancellableCoroutine { cont ->
        getStats { report ->
            val level =
                report.statsMap.values
                    .firstOrNull { it.type == "inbound-rtp" && it.members["kind"] == "audio" }
                    ?.members
                    ?.get("audioLevel") as? Double
            if (cont.isActive) cont.resume(level)
        }
    }
