package com.m57.hermescontrol.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A running log of what the chat did (sends, receipts, reconnects, history reloads, and any
 * sent message that vanished from the screen), kept on disk so it survives the app being
 * closed while the phone is locked. Shared on request from the Bots menu, so a problem seen on
 * the phone can be traced exactly instead of guessed at. Message text is cut to a few words.
 */
object ChatTrace {
    private const val MAX_LINES = 1_500
    private const val FILE = "chat-trace.txt"

    private val writer = Executors.newSingleThreadExecutor { Thread(it, "chat-trace").apply { isDaemon = true } }
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var file: File? = null

    fun init(context: Context) {
        val target = File(File(context.cacheDir, "diagnostics").apply { mkdirs() }, FILE)
        file = target
        writer.execute {
            runCatching {
                if (target.exists()) {
                    val kept = target.readLines().takeLast(MAX_LINES / 2)
                    target.writeText(kept.joinToString("\n", postfix = "\n"))
                }
            }
        }
        note("App started")
    }

    fun note(message: String) {
        val target = file ?: return
        val line = "${stamp.format(Date())} $message\n"
        writer.execute { runCatching { target.appendText(line) } }
    }

    /** First few words of a message, enough to recognise it in the log. */
    fun snippet(text: String?): String {
        val clean = text.orEmpty().replace(Regex("\\s+"), " ").trim()
        return "\"${clean.take(24)}${if (clean.length > 24) "…" else ""}\" (${clean.length} chars)"
    }

    /** The log as a file to share, with the freeze reporter's recent events appended. */
    fun export(context: Context): File? {
        val target = file ?: return null
        val done = writer.submit { }
        runCatching { done.get() }
        if (!target.exists()) return null
        return File(target.parentFile, "hermes-chat-report.txt").apply {
            writeText(
                "Hermes chat report, ${Date()}\n" +
                    "App ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}\n\n" +
                    target.readText(),
            )
        }
    }

    /** Opens the share sheet with the report attached. */
    fun share(context: Context) {
        val report = runCatching { export(context) }.getOrNull() ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, report.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
