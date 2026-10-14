package com.m57.hermescontrol.notification.push

import android.util.Log
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** Receives UnifiedPush events from the distributor app (ntfy) and routes them. */
class HermesPushService : PushService() {
    override fun onNewEndpoint(
        endpoint: PushEndpoint,
        instance: String,
    ) {
        PushRegistrar.onNewEndpoint(this, endpoint)
    }

    override fun onMessage(
        message: PushMessage,
        instance: String,
    ) {
        PushNotifier.handle(this, message.content, message.decrypted)
    }

    override fun onRegistrationFailed(
        reason: FailedReason,
        instance: String,
    ) {
        // The next app start asks the distributor again; there is no timer-driven retry.
        Log.w("HermesPushService", "registration failed: $reason")
        PushRegistrar.refreshStatus(this)
    }

    override fun onUnregistered(instance: String) {
        PushRegistrar.onUnregistered(this)
    }

    override fun onTempUnavailable(instance: String) {
        PushRegistrar.onTempUnavailable(this)
    }
}
