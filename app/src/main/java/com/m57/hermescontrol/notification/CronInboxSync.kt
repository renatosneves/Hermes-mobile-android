package com.m57.hermescontrol.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.CronInboxDelivery
import com.m57.hermescontrol.data.remote.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

internal fun cronInboxNeedsRestart(
    savedEpoch: String?,
    receivedEpoch: String,
): Boolean = savedEpoch != null && savedEpoch != receivedEpoch

internal fun cronInboxNotificationId(
    server: String,
    deliveryId: String,
): Int =
    (
        MessageDigest
            .getInstance("SHA-256")
            .digest("$server|$deliveryId".toByteArray())
            .take(4)
            .fold(0) { result, byte -> (result shl 8) or (byte.toInt() and 255) } and 0x3fffffff
    ) + 100

/** Device-owned cursor; desktop acknowledgements never consume these deliveries. */
object CronInboxSync {
    const val EXTRA_PROFILE = "cron_profile"
    const val EXTRA_SERVER = "cron_server"
    private const val CHANNEL = "hermes_scheduled_updates"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val changedSessions = changes.asSharedFlow()
    private var job: Job? = null

    private val mutex = Mutex()

    @Synchronized
    fun start(context: Context) {
        if (job != null) return
        val app = context.applicationContext
        job =
            scope.launch {
                while (true) {
                    val foreground = NotificationHelper.isAppInForeground()
                    if (foreground || AuthManager.isKeepConnectedInBackground()) {
                        try {
                            syncOnce(app)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Preserve the cursor and retry. Never log message or auth contents.
                        }
                    }
                    delay(30_000)
                }
            }
    }

    private fun serverKey(server: String): String =
        MessageDigest.getInstance("SHA-256").digest(server.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun deliveryKey(
        profile: String,
        session: String,
        message: Int,
        timestamp: Double,
    ): String = "$profile|$session|$message|$timestamp"

    fun wasDelivered(
        context: Context,
        profile: String,
        session: String?,
        message: Int?,
        timestamp: Double?,
    ): Boolean {
        if (session == null || message == null || timestamp == null) return false
        val server =
            AuthManager.contextFlow.value
                ?.baseUrl
                ?.trimEnd('/') ?: return false
        return context
            .getSharedPreferences("cron_inbox_cursors", Context.MODE_PRIVATE)
            .getStringSet("${serverKey(server)}.delivered", emptySet())
            ?.contains(deliveryKey(profile, session, message, timestamp)) == true
    }

    /** Also called by beta.48's existing periodic JobScheduler worker. */
    suspend fun syncOnce(context: Context) =
        mutex.withLock {
            val connection = AuthManager.contextFlow.value ?: return@withLock
            val server = connection.baseUrl.trimEnd('/')
            val prefs = context.getSharedPreferences("cron_inbox_cursors", Context.MODE_PRIVATE)
            val key = serverKey(server)
            var cursor = prefs.getLong("$key.cursor", 0)
            var epoch = prefs.getString("$key.epoch", null)
            val cutoff = if (epoch == null) System.currentTimeMillis() / 1000 - 86400 else null
            val delivered = prefs.getStringSet("$key.delivered", emptySet()).orEmpty().toMutableSet()
            for (pageNumber in 0 until 10) {
                val response = ApiClient.hermesApi.getCronInbox(cursor)
                if (!response.isSuccessful) return@withLock
                val page = response.body() ?: return@withLock
                // A response from the previous account/connection must not notify or advance this one.
                if (connection != AuthManager.contextFlow.value) return@withLock
                if (cronInboxNeedsRestart(epoch, page.epoch)) {
                    cursor = 0
                    epoch = page.epoch
                    delivered.clear()
                    continue
                }
                for (delivery in page.deliveries) {
                    delivered.add(
                        deliveryKey(
                            delivery.profile,
                            delivery.session_id,
                            delivery.server_message_id,
                            delivery.created,
                        ),
                    )
                    if (cutoff != null && delivery.created < cutoff) continue
                    changes.emit(delivery.session_id)
                    if (AuthManager.isNotifyNewBotMessages() && !post(context, server, delivery)) return@withLock
                }
                // Oldest identities may be pruned: the legacy checker only reads its newest three rows.
                val retained = delivered.toList().takeLast(2000).toSet()
                check(
                    prefs
                        .edit()
                        .putLong("$key.cursor", page.cursor)
                        .putString("$key.epoch", page.epoch)
                        .putStringSet("$key.delivered", retained)
                        .commit(),
                )
                cursor = page.cursor
                epoch = page.epoch
                if (!page.has_more) return@withLock
            }
        }

    private fun post(
        context: Context,
        server: String,
        delivery: CronInboxDelivery,
    ): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notif_cron_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val id = cronInboxNotificationId(server, delivery.id)
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_CHAT_FROM_NOTIFICATION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationReplyReceiver.EXTRA_SESSION_ID, delivery.session_id)
                putExtra(EXTRA_PROFILE, delivery.profile)
                putExtra(EXTRA_SERVER, server)
            }
        val pending =
            PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_cron_title, delivery.profile))
                .setContentText(delivery.text.take(200))
                .setStyle(NotificationCompat.BigTextStyle().bigText(delivery.text.take(2000)))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        manager.notify(id, notification)
        return true
    }
}
