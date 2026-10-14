package com.m57.hermescontrol.notification.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Handles the Approve and Reject buttons of an approval push notification. */
class PushActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ANSWER_APPROVAL = "com.m57.hermescontrol.ACTION_PUSH_ANSWER_APPROVAL"
        const val EXTRA_APPROVAL_ID = "approval_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_APPROVE = "approve"
        const val EXTRA_TAG = "tag"
        const val EXTRA_TITLE = "title"
        private const val TAG = "PushActionReceiver"

        /** A receiver may run about ten seconds after goAsync(); stay inside it. */
        private const val DEADLINE_MS = 9_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ANSWER_APPROVAL) return
        val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val tag = intent.getStringExtra(EXTRA_TAG)
        if (approvalId.isNullOrBlank() || sessionId.isNullOrBlank() || tag.isNullOrBlank()) return
        val profile = intent.getStringExtra(EXTRA_PROFILE)
        val approve = intent.getBooleanExtra(EXTRA_APPROVE, false)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val app = context.applicationContext
        val pending = goAsync()
        PushNotifier.showApprovalProgress(app, tag, title, sessionId, failed = false)
        scope.launch {
            try {
                val ok =
                    withTimeoutOrNull(DEADLINE_MS) {
                        ApprovalResponder.respond(sessionId, approvalId, profile, approve)
                    } == true
                if (ok) {
                    PushNotifier.cancelTag(app, tag)
                } else {
                    PushNotifier.showApprovalProgress(app, tag, title, sessionId, failed = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "approval answer failed", e)
                PushNotifier.showApprovalProgress(app, tag, title, sessionId, failed = true)
            } finally {
                pending.finish()
            }
        }
    }
}
