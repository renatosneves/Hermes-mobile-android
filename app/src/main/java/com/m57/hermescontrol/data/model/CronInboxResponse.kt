package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CronInboxResponse(
    val epoch: String,
    val cursor: Long,
    val has_more: Boolean = false,
    val reset: Boolean = false,
    val deliveries: List<CronInboxDelivery> = emptyList(),
)

@Serializable
data class CronInboxDelivery(
    val sequence: Long,
    val id: String,
    val profile: String,
    val session_id: String,
    val text: String,
    val created: Double = 0.0,
    val server_message_id: Int = 0,
)
