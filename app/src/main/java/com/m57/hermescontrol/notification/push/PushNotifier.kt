package com.m57.hermescontrol.notification.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.m57.hermescontrol.ChatScreen
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.session.ActiveSessionHolder
import com.m57.hermescontrol.notification.NotificationHelper
import com.m57.hermescontrol.notification.NotificationReplyReceiver
import java.time.LocalTime

/** Shows, groups and clears the notifications that pushes produce. */
object PushNotifier {
    private const val TAG = "PushNotifier"

    /** Id shared by every push notification; the tag tells them apart. */
    const val NOTIFICATION_ID = 7_100
    private const val SUMMARY_ID = 7_101
    private const val TAG_PREFIX = "hermes_push:"
    private const val EXTRA_KIND = "hermes_push_kind"
    private const val EXTRA_PROFILE = "hermes_push_profile"
    private const val EXTRA_SESSION = "hermes_push_session"
    private const val GROUP_PREFIX = "hermes_push_group:"
    private const val SUMMARY_PREFIX = "summary:"
    private const val MAX_SUMMARY_LINES = 5

    /** Entry point for a decrypted push: dedupe, apply settings, then show or drop it. */
    fun handle(
        context: Context,
        content: ByteArray,
        decrypted: Boolean,
    ) {
        val app = context.applicationContext
        PushRegistrar.onMessageReceived(app)
        if (!decrypted) {
            Log.w(TAG, "dropping a push that the connector could not decrypt")
            return
        }
        val payload = PushPayloadParser.parse(content) ?: return
        if (PushStore.recordSeen(app, payload.id)) return
        val now = LocalTime.now()
        val decision =
            PushDecider.decide(
                payload = payload,
                prefs = PushStore.prefsValue(app),
                chatOpenInForeground = isChatOpen(payload),
                minuteOfDay = now.hour * 60 + now.minute,
            )
        if (!decision.show || !notificationsAllowed(app)) return
        show(app, payload, decision)
    }

    private fun isChatOpen(payload: PushPayload): Boolean =
        PushDecider.isChatOpen(
            appInForeground = NotificationHelper.isAppInForeground(),
            onChatScreen = onChatScreen(),
            openStoredSessionId = openStoredSessionId(),
            payloadSessionId = payload.sessionId,
        )

    private fun onChatScreen(): Boolean = NavigationController.backStack?.lastOrNull() == ChatScreen

    private fun openStoredSessionId(): String? {
        val runtime = ActiveSessionHolder.activeSessionId.value ?: return null
        return ActiveSessionHolder.resolveStoredSessionId(runtime)
    }

    private fun notificationsAllowed(context: Context): Boolean {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && manager(context)?.areNotificationsEnabled() == true
    }

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = manager(context) ?: return

        fun channel(
            id: PushChannel,
            name: Int,
            desc: Int,
            importance: Int,
        ) = NotificationChannel(id.id, context.getString(name), importance).apply {
            description = context.getString(desc)
        }
        manager.createNotificationChannels(
            listOf(
                channel(
                    PushChannel.APPROVALS,
                    R.string.push_channel_approvals_name,
                    R.string.push_channel_approvals_desc,
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                channel(
                    PushChannel.TASKS,
                    R.string.push_channel_tasks_name,
                    R.string.push_channel_tasks_desc,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    PushChannel.MESSAGES,
                    R.string.push_channel_messages_name,
                    R.string.push_channel_messages_desc,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    PushChannel.SCHEDULED,
                    R.string.push_channel_scheduled_name,
                    R.string.push_channel_scheduled_desc,
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    /** The tag of one push notification: a newer push for the same thing replaces the older one. */
    internal fun tagFor(payload: PushPayload): String =
        when (payload.type) {
            PushType.APPROVAL -> "${TAG_PREFIX}approval:${payload.approvalId}"
            PushType.TEST -> "${TAG_PREFIX}test"
            else -> "$TAG_PREFIX${payload.type.wire}:${payload.profile}:${payload.sessionId.orEmpty()}"
        }

    private fun show(
        context: Context,
        payload: PushPayload,
        decision: PushDecision,
    ) {
        val manager = manager(context) ?: return
        ensureChannels(context)
        val tag = tagFor(payload)
        val title = payload.title.ifEmpty { payload.profile }
        val grouped = payload.type == PushType.MESSAGE
        val builder =
            NotificationCompat
                .Builder(context, decision.channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(payload.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(categoryFor(payload.type))
                .setPriority(priorityFor(decision.channel))
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(openChatIntent(context, tag, payload.sessionId))
                .addExtras(
                    Bundle().apply {
                        putString(EXTRA_KIND, payload.type.wire)
                        putString(EXTRA_PROFILE, payload.profile)
                        putString(EXTRA_SESSION, payload.sessionId)
                    },
                )
        if (payload.timestampMs > 0L) builder.setWhen(payload.timestampMs).setShowWhen(true)
        if (decision.silent) builder.setSilent(true)
        if (grouped) {
            builder
                .setGroup(GROUP_PREFIX + payload.profile)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        }
        when (payload.type) {
            PushType.APPROVAL -> addApprovalActions(context, builder, payload, tag)
            PushType.MESSAGE, PushType.SCHEDULED -> addReplyAction(context, builder, payload, tag)
            else -> Unit
        }
        manager.notify(tag, NOTIFICATION_ID, builder.build())
        if (grouped) postSummary(context, manager, payload, decision)
    }

    private fun categoryFor(type: PushType): String =
        when (type) {
            PushType.APPROVAL -> NotificationCompat.CATEGORY_REMINDER
            PushType.TASK -> NotificationCompat.CATEGORY_PROGRESS
            else -> NotificationCompat.CATEGORY_MESSAGE
        }

    private fun priorityFor(channel: PushChannel): Int =
        when (channel) {
            PushChannel.APPROVALS -> NotificationCompat.PRIORITY_HIGH
            PushChannel.SCHEDULED -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

    private fun postSummary(
        context: Context,
        manager: NotificationManager,
        payload: PushPayload,
        decision: PushDecision,
    ) {
        val group = GROUP_PREFIX + payload.profile
        val children = groupChildren(manager, payload.profile)
        val inbox = NotificationCompat.InboxStyle()
        children.take(MAX_SUMMARY_LINES).forEach { inbox.addLine(it.extras.getCharSequence(Notification.EXTRA_TEXT)) }
        val summary =
            NotificationCompat
                .Builder(context, decision.channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(payload.profile)
                .setContentText(payload.body)
                .setStyle(inbox)
                .setNumber(children.size.coerceAtLeast(1))
                .setGroup(group)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openChatIntent(context, SUMMARY_PREFIX + payload.profile, payload.sessionId))
                .build()
        manager.notify(SUMMARY_PREFIX + payload.profile, SUMMARY_ID, summary)
    }

    private fun groupChildren(
        manager: NotificationManager,
        profile: String,
    ) = runCatching {
        manager.activeNotifications.filter {
            it.id == NOTIFICATION_ID &&
                it.tag?.startsWith(TAG_PREFIX) == true &&
                it.notification.extras.getString(EXTRA_PROFILE) == profile &&
                it.notification.extras.getString(EXTRA_KIND) == PushType.MESSAGE.wire
        }
    }.getOrDefault(emptyList()).map { it.notification }

    private fun openChatIntent(
        context: Context,
        key: String,
        sessionId: String?,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_CHAT_FROM_NOTIFICATION
                component = ComponentName(context, MainActivity::class.java)
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!sessionId.isNullOrBlank()) putExtra(NotificationReplyReceiver.EXTRA_SESSION_ID, sessionId)
            }
        return PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun addReplyAction(
        context: Context,
        builder: NotificationCompat.Builder,
        payload: PushPayload,
        tag: String,
    ) {
        val sessionId = payload.sessionId ?: return
        val remoteInput =
            RemoteInput
                .Builder(NotificationReplyReceiver.KEY_TEXT_REPLY)
                .setLabel(context.getString(R.string.notif_reply_placeholder))
                .build()
        val intent =
            Intent(context, NotificationReplyReceiver::class.java).apply {
                action = "${context.packageName}.ACTION_NOTIFICATION_REPLY"
                component = ComponentName(context, NotificationReplyReceiver::class.java)
                setPackage(context.packageName)
                putExtra(NotificationReplyReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(NotificationReplyReceiver.EXTRA_PROFILE, payload.profile)
                putExtra(NotificationReplyReceiver.EXTRA_DISMISS_TAG, tag)
                putExtra(NotificationReplyReceiver.EXTRA_DISMISS_ID, NOTIFICATION_ID)
            }
        val pending =
            PendingIntent.getBroadcast(
                context,
                ("reply:$tag").hashCode(),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        builder.addAction(
            NotificationCompat.Action
                .Builder(R.drawable.ic_notification, context.getString(R.string.action_reply), pending)
                .addRemoteInput(remoteInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .build(),
        )
    }

    private fun addApprovalActions(
        context: Context,
        builder: NotificationCompat.Builder,
        payload: PushPayload,
        tag: String,
    ) {
        val approvalId = payload.approvalId ?: return
        val sessionId = payload.sessionId ?: return

        fun action(
            approve: Boolean,
            label: Int,
        ): NotificationCompat.Action {
            val intent =
                Intent(context, PushActionReceiver::class.java).apply {
                    action = PushActionReceiver.ACTION_ANSWER_APPROVAL
                    component = ComponentName(context, PushActionReceiver::class.java)
                    setPackage(context.packageName)
                    putExtra(PushActionReceiver.EXTRA_APPROVAL_ID, approvalId)
                    putExtra(PushActionReceiver.EXTRA_SESSION_ID, sessionId)
                    putExtra(PushActionReceiver.EXTRA_PROFILE, payload.profile)
                    putExtra(PushActionReceiver.EXTRA_APPROVE, approve)
                    putExtra(PushActionReceiver.EXTRA_TAG, tag)
                    putExtra(PushActionReceiver.EXTRA_TITLE, payload.title)
                }
            val pending =
                PendingIntent.getBroadcast(
                    context,
                    ("approval:$approve:$tag").hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            return NotificationCompat.Action
                .Builder(R.drawable.ic_notification, context.getString(label), pending)
                .build()
        }
        builder.addAction(action(approve = true, label = R.string.push_action_approve))
        builder.addAction(action(approve = false, label = R.string.push_action_reject))
    }

    /** Shows that an approval answer is being sent, or that it failed, in place of the original. */
    internal fun showApprovalProgress(
        context: Context,
        tag: String,
        title: String,
        sessionId: String?,
        failed: Boolean,
    ) {
        val manager = manager(context) ?: return
        ensureChannels(context)
        val text = context.getString(if (failed) R.string.push_approval_failed else R.string.push_approval_sending)
        val builder =
            NotificationCompat
                .Builder(context, PushChannel.APPROVALS.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setAutoCancel(failed)
                .setOngoing(!failed)
                .setContentIntent(openChatIntent(context, tag, sessionId))
        if (!failed) builder.setProgress(0, 0, true)
        manager.notify(tag, NOTIFICATION_ID, builder.build())
    }

    fun cancelTag(
        context: Context,
        tag: String,
    ) {
        manager(context)?.cancel(tag, NOTIFICATION_ID)
    }

    /** Clears every push notification for the chat that is now on screen, and a bot summary left empty. */
    fun cancelForOpenChat(context: Context) {
        if (!NotificationHelper.isAppInForeground() || !onChatScreen()) return
        val stored = openStoredSessionId() ?: return
        cancelForSession(context, stored)
    }

    fun cancelForSession(
        context: Context,
        storedSessionId: String,
    ) {
        val manager = manager(context) ?: return
        val active = runCatching { manager.activeNotifications }.getOrNull() ?: return
        val profiles = mutableSetOf<String>()
        for (sbn in active) {
            val extras = sbn.notification.extras
            if (sbn.id != NOTIFICATION_ID || sbn.tag?.startsWith(TAG_PREFIX) != true) continue
            if (extras.getString(EXTRA_SESSION) != storedSessionId) continue
            extras.getString(EXTRA_PROFILE)?.let(profiles::add)
            manager.cancel(sbn.tag, sbn.id)
        }
        for (profile in profiles) {
            if (groupChildren(manager, profile).isEmpty()) manager.cancel(SUMMARY_PREFIX + profile, SUMMARY_ID)
        }
    }
}
