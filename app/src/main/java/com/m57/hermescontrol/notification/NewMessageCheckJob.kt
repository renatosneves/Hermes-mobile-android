package com.m57.hermescontrol.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.ui.bots.BotsPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Periodic check for new bot messages while the app is closed or in the background. Reads the
 * Bots roster over REST (no server push), and posts one notification per bot whose newest message
 * is the bot's own and newer than what was last seen. Android runs it about every 15 minutes at
 * best; Doze and battery savers can delay it further.
 */
class NewMessageCheckJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val appContext = applicationContext
        running =
            scope.launch {
                try {
                    runCatching { checkOnce(appContext) }
                } finally {
                    jobFinished(params, false)
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        // The next period retries; no backoff-driven retry storm.
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 5710
        private const val PREFS = "bg_message_check"
        private const val KEY_BACKGROUNDED_AT = "app_backgrounded_at"
        private const val PREVIEW_MAX_CHARS = 80
        private const val PAGE_SIZE = 3
        private const val PERIOD_MS = 15 * 60 * 1000L
        private const val FLEX_MS = 5 * 60 * 1000L
        private const val AUTH_WAIT_MS = 10_000L
        private const val REQUEST_TIMEOUT_MS = 20_000L

        /** One notification id per bot, so a newer message replaces the earlier one. */
        private fun notificationId(profileName: String): Int = 20_000 + (profileName.hashCode() and 0xFFFF)

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Remember when the app was last left, so messages seen in the app are not notified later. */
        fun recordAppBackgrounded(
            context: Context,
            nowMs: Long = System.currentTimeMillis(),
        ) {
            prefs(context)
                .edit()
                .putLong(KEY_BACKGROUNDED_AT, nowMs)
                .apply()
        }

        /** Make the scheduled job match the setting: scheduled when on (once), cancelled when off. */
        fun sync(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val enabled = runCatching { AuthManager.isNotifyNewBotMessages() }.getOrDefault(true)
            if (!enabled) {
                scheduler.cancel(JOB_ID)
                return
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val info =
                JobInfo
                    .Builder(JOB_ID, ComponentName(context, NewMessageCheckJob::class.java))
                    .setPeriodic(PERIOD_MS, FLEX_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            scheduler.schedule(info)
        }

        private suspend fun checkOnce(context: Context) {
            if (NotificationHelper.isAppInForeground()) return
            if (!notificationsAllowed(context)) return
            val ready =
                withTimeoutOrNull(AUTH_WAIT_MS) {
                    AuthManager.initializationState.first { it == AuthManager.InitializationState.Ready }
                }
            if (ready == null) return
            if (!AuthManager.isNotifyNewBotMessages()) return
            if (!AuthManager.isGatedMode() && AuthManager.getToken().isNullOrBlank()) return

            try {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) { CronInboxSync.syncOnce(context) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the existing ordinary-message check usable if the inbox request fails.
            }

            val roster =
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    safeApiCall(retries = 0) { ApiClient.hermesApi.getProfiles() }
                }
            val profiles = (roster as? NetworkResult.Success)?.data?.profiles ?: return

            val store = prefs(context)
            val backgroundedAt = store.getLong(KEY_BACKGROUNDED_AT, 0L).takeIf { it > 0L }?.let { it / 1000.0 }
            for (profile in profiles) {
                if (NotificationHelper.isAppInForeground()) return
                checkProfile(context, store, profile, backgroundedAt)
            }
        }

        private fun notificationsAllowed(context: Context): Boolean {
            val granted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return granted && manager?.areNotificationsEnabled() == true
        }

        private suspend fun checkProfile(
            context: Context,
            store: SharedPreferences,
            profile: ProfileInfo,
            backgroundedAt: Double?,
        ) {
            val key = "seen_${profile.name}"
            val lastSeen = if (store.contains(key)) Double.fromBits(store.getLong(key, 0L)) else null
            val latest = profile.canonical_session?.last_active ?: profile.last_session?.last_active
            var preview: String? = null
            var fromBot = false
            var alreadyDelivered = false
            if (NewMessageDecider.hasNewActivity(lastSeen, backgroundedAt, latest)) {
                val sessionId = sessionToRead(profile)
                val page =
                    sessionId?.let {
                        val result =
                            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                                safeApiCall(retries = 0) {
                                    ApiClient.hermesApi.getSessionMessages(
                                        it,
                                        limit = PAGE_SIZE,
                                        order = "latest",
                                        profile = profile.name,
                                    )
                                }
                            }
                        // A failed read keeps the old watermark so the next run tries again.
                        (result as? NetworkResult.Success)?.data?.messages ?: return
                    }
                val newest =
                    page?.asReversed()?.firstOrNull { m ->
                        (m.role == "user" || m.role == "assistant") && m.display_kind == null &&
                            BotsPresentation
                                .previewText(
                                    BotsPresentation.contentText(m.display_content ?: m.content),
                                ).isNotEmpty()
                    }
                fromBot = newest?.role == "assistant"
                alreadyDelivered =
                    CronInboxSync.wasDelivered(
                        context,
                        profile.name,
                        sessionId,
                        newest?.id,
                        newest?.timestampText?.toDoubleOrNull(),
                    )
                preview =
                    newest
                        ?.let {
                            BotsPresentation.previewText(
                                BotsPresentation.contentText(it.display_content ?: it.content),
                            )
                        }?.take(PREVIEW_MAX_CHARS)
                        ?.trimEnd()
            }
            if (!alreadyDelivered && NewMessageDecider.decide(lastSeen, backgroundedAt, latest, fromBot)) {
                sessionToRead(profile)?.let { notify(context, profile, it, preview) }
            }
            NewMessageDecider.nextWatermark(lastSeen, latest)?.let {
                store.edit().putLong(key, it.toRawBits()).apply()
            }
        }

        private fun sessionToRead(profile: ProfileInfo): String? =
            BotsPresentation.chatToOpen(profile, null) ?: profile.last_session?.id?.takeIf { it.isNotBlank() }

        private fun notify(
            context: Context,
            profile: ProfileInfo,
            sessionId: String,
            preview: String?,
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        ChatNotificationService.CHAT_CHANNEL_ID,
                        context.getString(R.string.notif_channel_chat_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = context.getString(R.string.notif_channel_chat_desc) },
                )
            }
            val body =
                context.getString(R.string.notif_new_message) +
                    (preview?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: "")
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_CHAT_FROM_NOTIFICATION
                    component = ComponentName(context, MainActivity::class.java)
                    setPackage(context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(NotificationReplyReceiver.EXTRA_SESSION_ID, sessionId)
                }
            val contentIntent =
                PendingIntent.getActivity(
                    context,
                    notificationId(profile.name),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, ChatNotificationService.CHAT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(profile.effectiveTitle)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .build()
            manager.notify(notificationId(profile.name), notification)
        }
    }
}
