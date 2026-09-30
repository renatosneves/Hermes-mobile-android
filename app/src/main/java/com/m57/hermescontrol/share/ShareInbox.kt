package com.m57.hermescontrol.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.m57.hermescontrol.data.model.Attachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Text and files another app shared to Hermes, waiting for you to pick the bot that gets them. */
data class SharedContent(
    val text: String,
    val attachments: List<Attachment>,
)

/**
 * Holds what arrived through Android's share sheet. Sharing only fills the chosen bot's
 * composer; nothing is sent until you press send, so a foreign app can't post on your behalf.
 */
object ShareInbox {
    private val _pending = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()

    /** Set once you've picked a bot: the next chat that opens takes the content. */
    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    fun isShare(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

    // Outlives the activity, so a recreation mid-copy doesn't drop the share.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Copies the shared files into the app's cache (the sender's grant can lapse), holds them,
     * then calls [onReady].
     */
    fun receive(
        context: Context,
        intent: Intent,
        onReady: () -> Unit,
    ) {
        scope.launch {
            val content = withContext(Dispatchers.IO) { read(context, intent) } ?: return@launch
            _armed.value = false
            _pending.value = content
            onReady()
        }
    }

    fun arm() {
        if (_pending.value != null) _armed.value = true
    }

    /** Hands the content to the chat that asked, once. */
    fun take(): SharedContent? {
        if (!_armed.value) return null
        val content = _pending.value
        _pending.value = null
        _armed.value = false
        return content
    }

    fun cancel() {
        _pending.value = null
        _armed.value = false
    }

    private fun read(
        context: Context,
        intent: Intent,
    ): SharedContent? {
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val body =
            intent
                .getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.trim()
                .orEmpty()
        val text =
            when {
                subject.isEmpty() || body.contains(subject) -> body
                body.isEmpty() -> subject
                else -> "$subject\n$body"
            }
        val attachments = streams(intent).mapNotNull { copy(context, it) }
        if (text.isEmpty() && attachments.isEmpty()) return null
        return SharedContent(text, attachments)
    }

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> =
        when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }.orEmpty()
            }

            else -> {
                listOfNotNull(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    },
                )
            }
        }.take(MAX_FILES)

    private fun copy(
        context: Context,
        uri: Uri,
    ): Attachment? =
        runCatching {
            val resolver = context.contentResolver
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "shared"
            var size = -1L
            resolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    }
                }
            if (size > MAX_BYTES) return@runCatching null
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)}")
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            Attachment(
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString(),
                name = name,
                mimeType = resolver.getType(uri) ?: "application/octet-stream",
                size = file.length(),
            )
        }.onFailure { Log.w("ShareInbox", "Skipping unreadable shared file", it) }.getOrNull()

    private const val MAX_FILES = 10
    private const val MAX_BYTES = 100L * 1024 * 1024
}
