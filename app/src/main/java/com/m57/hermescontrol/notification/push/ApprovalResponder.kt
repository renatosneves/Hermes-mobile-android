package com.m57.hermescontrol.notification.push

import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.contract.ApprovalRespondParams
import com.m57.hermescontrol.data.ws.contract.DESKTOP_SESSION_SOURCE
import com.m57.hermescontrol.data.ws.contract.RpcMethods
import com.m57.hermescontrol.data.ws.contract.SessionResumeParams
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers a pending approval from a notification button. The app's approval API is the
 * `approval.respond` WebSocket RPC (the same one the chat card uses), so this opens a short-lived
 * socket, resumes the session to learn its runtime id, answers, and lets the socket close again
 * under the normal background policy.
 */
internal object ApprovalResponder {
    private const val AUTH_WAIT_MS = 3_000L
    private const val CONNECT_WAIT_MS = 4_000L
    private const val RPC_TIMEOUT_MS = 3_000L

    /** Maps the button to the gateway's choice, as the chat card does (`once` for a single run). */
    internal fun choiceFor(approve: Boolean): String = if (approve) "once" else "deny"

    suspend fun respond(
        storedSessionId: String,
        approvalId: String,
        profile: String?,
        approve: Boolean,
    ): Boolean {
        val ready =
            withTimeoutOrNull(AUTH_WAIT_MS) {
                AuthManager.initializationState.first { it == AuthManager.InitializationState.Ready }
            }
        if (ready == null) return false
        if (!AuthManager.isGatedMode() && AuthManager.getToken().isNullOrBlank()) return false
        return try {
            HermesWsClient.connect()
            if (!HermesWsClient.awaitConnected(CONNECT_WAIT_MS)) return false
            val resumed =
                HermesWsClient.call(
                    method = RpcMethods.SESSION_RESUME,
                    params =
                        SessionResumeParams(
                            sessionId = storedSessionId,
                            source = DESKTOP_SESSION_SOURCE,
                            omitMessages = true,
                            profile = profile?.takeIf { it.isNotBlank() },
                        ),
                    timeoutMs = RPC_TIMEOUT_MS,
                )
            val runtimeSessionId = resumed.sessionId?.takeIf { it.isNotBlank() } ?: return false
            HermesWsClient.call(
                method = RpcMethods.APPROVAL_RESPOND,
                params =
                    ApprovalRespondParams(
                        sessionId = runtimeSessionId,
                        choice = choiceFor(approve),
                        all = false,
                        requestId = approvalId,
                    ),
                timeoutMs = RPC_TIMEOUT_MS,
            )
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            // Closes the socket unless the app is open, a lease is held, or a reply is pending.
            HermesWsClient.disconnectIfIdleInBackground()
        }
    }
}
