package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Body of `POST /api/plugins/hermes-push/register` (push contract v1). */
@Serializable
data class PushRegisterRequest(
    val device_id: String,
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val prefs: JsonObject,
)

/** Body of `POST /api/plugins/hermes-push/unregister` and `/test`. */
@Serializable
data class PushDeviceRequest(
    val device_id: String,
)

/** Reply of the hermes-push routes: `ok`, plus the push service HTTP status for `/test`. */
@Serializable
data class PushOkResponse(
    val ok: Boolean = false,
    val status: Int? = null,
)
