package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/** `GET /api/audio/voice-live/status`: whether GPT-Live can start (never carries the key). */
@Serializable
data class VoiceLiveStatusResponse(
    val ok: Boolean = false,
    val mode: String? = null,
    val available: Boolean = false,
    val reason: String? = null,
    val model: String? = null,
    val voice: String? = null,
)

/** `POST /api/audio/voice-live/session`: our WebRTC offer plus chat history to seed the voice. */
@Serializable
data class VoiceLiveSessionRequest(
    val sdp: String,
    val history: JsonArray = JsonArray(emptyList()),
)

@Serializable
data class VoiceLiveSessionResponse(
    val ok: Boolean = false,
    val session: VoiceLiveSessionId? = null,
    val transport: VoiceLiveTransportAnswer? = null,
)

@Serializable
data class VoiceLiveSessionId(
    val id: String? = null,
)

@Serializable
data class VoiceLiveTransportAnswer(
    val type: String? = null,
    val sdp: String? = null,
)
