package com.m57.hermescontrol.notification.push

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** The endpoint a distributor handed us, with the Web Push keys the server encrypts to. */
data class PushEndpointRecord(
    val url: String,
    val p256dh: String,
    val auth: String,
)

/**
 * Plain SharedPreferences for push state. It is deliberately independent of [com.m57.hermescontrol.data.local.AuthManager]:
 * a push can wake a cold process and must read its settings at once, without waiting for the encrypted store.
 */
object PushStore {
    private const val FILE = "hermes_push"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ENDPOINT_URL = "endpoint_url"
    private const val KEY_ENDPOINT_P256DH = "endpoint_p256dh"
    private const val KEY_ENDPOINT_AUTH = "endpoint_auth"
    private const val KEY_LAST_REGISTER_OK = "last_register_ok_ms"
    private const val KEY_REGISTER_PENDING = "register_pending"
    private const val KEY_DEGRADED = "degraded"
    private const val KEY_SEEN_IDS = "seen_ids"
    private const val KEY_APPROVALS = "type_approval"
    private const val KEY_TASKS = "type_task"
    private const val KEY_MESSAGES = "type_message"
    private const val KEY_SCHEDULED = "type_scheduled"
    private const val KEY_BOT_LEVELS = "bot_levels"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start_min"
    private const val KEY_QUIET_END = "quiet_end_min"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** A random id, created once and kept for the life of the install. */
    @Synchronized
    fun deviceId(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = UUID.randomUUID().toString()
        p.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    fun endpoint(context: Context): PushEndpointRecord? {
        val p = prefs(context)
        val url = p.getString(KEY_ENDPOINT_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val key = p.getString(KEY_ENDPOINT_P256DH, null)?.takeIf { it.isNotBlank() } ?: return null
        val auth = p.getString(KEY_ENDPOINT_AUTH, null)?.takeIf { it.isNotBlank() } ?: return null
        return PushEndpointRecord(url, key, auth)
    }

    /** Stores a new endpoint and marks the registration as owed to the server. */
    fun saveEndpoint(
        context: Context,
        record: PushEndpointRecord,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_ENDPOINT_URL, record.url)
            .putString(KEY_ENDPOINT_P256DH, record.p256dh)
            .putString(KEY_ENDPOINT_AUTH, record.auth)
            .putBoolean(KEY_REGISTER_PENDING, true)
            .putBoolean(KEY_DEGRADED, false)
            .apply()
    }

    fun clearEndpoint(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_ENDPOINT_URL)
            .remove(KEY_ENDPOINT_P256DH)
            .remove(KEY_ENDPOINT_AUTH)
            .remove(KEY_LAST_REGISTER_OK)
            .putBoolean(KEY_REGISTER_PENDING, false)
            .apply()
    }

    fun lastRegisterOkMs(context: Context): Long = prefs(context).getLong(KEY_LAST_REGISTER_OK, 0L)

    fun isRegisterPending(context: Context): Boolean = prefs(context).getBoolean(KEY_REGISTER_PENDING, false)

    fun markRegistered(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        prefs(context)
            .edit()
            .putLong(KEY_LAST_REGISTER_OK, nowMs)
            .putBoolean(KEY_REGISTER_PENDING, false)
            .apply()
    }

    fun markRegisterPending(context: Context) {
        prefs(context).edit().putBoolean(KEY_REGISTER_PENDING, true).apply()
    }

    fun isDegraded(context: Context): Boolean = prefs(context).getBoolean(KEY_DEGRADED, false)

    fun setDegraded(
        context: Context,
        degraded: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_DEGRADED, degraded).apply()
    }

    /** Records [id] as seen; returns true when it had been seen before. */
    @Synchronized
    fun recordSeen(
        context: Context,
        id: String,
    ): Boolean {
        val p = prefs(context)
        val (updated, duplicate) = PushDedupe.record(PushDedupe.decode(p.getString(KEY_SEEN_IDS, null)), id)
        if (!duplicate) p.edit().putString(KEY_SEEN_IDS, PushDedupe.encode(updated)).apply()
        return duplicate
    }

    fun prefsValue(context: Context): PushPrefs {
        val p = prefs(context)
        val defaults = PushPrefs()
        return PushPrefs(
            approvals = p.getBoolean(KEY_APPROVALS, defaults.approvals),
            tasks = p.getBoolean(KEY_TASKS, defaults.tasks),
            messages = p.getBoolean(KEY_MESSAGES, defaults.messages),
            scheduled = p.getBoolean(KEY_SCHEDULED, defaults.scheduled),
            botLevels = PushPrefs.decodeLevels(p.getString(KEY_BOT_LEVELS, null)),
            quietEnabled = p.getBoolean(KEY_QUIET_ENABLED, defaults.quietEnabled),
            quietStartMinute = p.getInt(KEY_QUIET_START, defaults.quietStartMinute),
            quietEndMinute = p.getInt(KEY_QUIET_END, defaults.quietEndMinute),
        )
    }

    fun savePrefs(
        context: Context,
        value: PushPrefs,
    ) {
        prefs(context)
            .edit()
            .putBoolean(KEY_APPROVALS, value.approvals)
            .putBoolean(KEY_TASKS, value.tasks)
            .putBoolean(KEY_MESSAGES, value.messages)
            .putBoolean(KEY_SCHEDULED, value.scheduled)
            .putString(KEY_BOT_LEVELS, PushPrefs.encodeLevels(value.botLevels))
            .putBoolean(KEY_QUIET_ENABLED, value.quietEnabled)
            .putInt(KEY_QUIET_START, value.quietStartMinute)
            .putInt(KEY_QUIET_END, value.quietEndMinute)
            .apply()
    }
}
