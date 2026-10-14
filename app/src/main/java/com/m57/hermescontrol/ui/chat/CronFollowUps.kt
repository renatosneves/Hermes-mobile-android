package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.WsEvent

/**
 * A scheduled (cron) job posted a follow-up into a bot's chat. The gateway marks it with
 * `source = "cron"` on `message.complete` and `background.complete`.
 *
 * It does not belong to a prompt the user sent, so it must never be reduced as the end of a live
 * turn (that would overwrite a reply still streaming). The chat reloads history instead, which
 * keeps exactly one copy per stored message.
 */
internal object CronFollowUps {
    private const val CRON = "cron"

    fun isCron(event: WsEvent): Boolean =
        when (event) {
            is WsEvent.MessageComplete -> event.rawPayload?.get("source") == CRON
            is WsEvent.BackgroundComplete -> event.data?.get("source") == CRON
            else -> false
        }

    /** Profile the follow-up was written for, when the gateway says. */
    fun profileOf(event: WsEvent): String? =
        when (event) {
            is WsEvent.MessageComplete -> event.rawPayload?.get("profile") as? String
            is WsEvent.BackgroundComplete -> event.data?.get("profile") as? String
            else -> null
        }?.takeIf { it.isNotBlank() }

    /**
     * Whether the open chat should reload for this follow-up: it names this session, or it names
     * no session but this chat's bot. A follow-up for another bot is left to that bot's chat.
     */
    fun concernsOpenChat(
        event: WsEvent,
        isCurrentSession: (String) -> Boolean,
        openProfile: String?,
    ): Boolean {
        val sessionId = (event as? WsEvent.MessageComplete)?.sessionId
        if (sessionId != null) return isCurrentSession(sessionId)
        val profile = profileOf(event) ?: return true
        return openProfile == null || profile == openProfile
    }
}
