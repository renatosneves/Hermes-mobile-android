package com.m57.hermescontrol.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Evidence for freezes and crashes we can't reproduce off the phone: a short event log, a
 * main-thread watchdog that records what the app was stuck on, and, after a freeze or crash,
 * Android's own report of the last exit. [pendingReport] gathers it into one file to share.
 */
object FreezeReporter {
    private const val MAX_LOG_LINES = 400
    private const val TICK_MS = 500L
    private const val STALL_MS = 2_500L
    private const val MAX_TRACE_CHARS = 200_000
    private const val PREFS = "hermes_diagnostics"
    private const val KEY_REPORTED_AT = "reported_exit_at"

    private var dir: File? = null
    private val log = ArrayDeque<String>()
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile private var watchdog: Thread? = null

    fun init(context: Context) {
        val folder = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        dir = folder
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(folder, "crash.txt").writeText(
                    "Crash on thread ${thread.name} at ${Date()}\n\n${error.stackTraceToString()}\n\n${logText()}",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** One line in the event log (kept in memory, written out with any report). */
    fun note(message: String) {
        val line = "${stamp.format(Date())} [${Thread.currentThread().name}] $message"
        synchronized(log) {
            log.addLast(line)
            while (log.size > MAX_LOG_LINES) log.removeFirst()
        }
    }

    private fun logText(): String = synchronized(log) { "Recent events:\n" + log.joinToString("\n") }

    /** Watches the main thread (during voice calls); a stall writes its stack straight to disk. */
    fun startWatchdog() {
        if (watchdog != null) return
        val main = Handler(Looper.getMainLooper())
        val thread =
            Thread({
                var lastBeat = SystemClock.uptimeMillis()
                var reportedStall = false
                while (!Thread.currentThread().isInterrupted) {
                    main.post { lastBeat = SystemClock.uptimeMillis() }
                    try {
                        Thread.sleep(TICK_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    val stalled = SystemClock.uptimeMillis() - lastBeat
                    if (stalled > STALL_MS && !reportedStall) {
                        reportedStall = true
                        val stack =
                            Looper
                                .getMainLooper()
                                .thread.stackTrace
                                .joinToString("\n") { "    at $it" }
                        note("Main thread stuck for ${stalled}ms")
                        runCatching {
                            File(dir, "freeze.txt").writeText(
                                "Main thread stuck for ${stalled}ms at ${Date()}\n$stack\n\n${allThreads()}\n\n${logText()}",
                            )
                        }
                    } else if (stalled <= STALL_MS) {
                        reportedStall = false
                    }
                }
            }, "freeze-watchdog").apply { isDaemon = true }
        watchdog = thread
        thread.start()
    }

    fun stopWatchdog() {
        watchdog?.interrupt()
        watchdog = null
    }

    private fun allThreads(): String =
        Thread
            .getAllStackTraces()
            .filterKeys {
                it.name.startsWith("voice") || it.name.contains("signaling", true) ||
                    it.name.contains("network", true)
            }.entries
            .joinToString("\n\n") { (t, stack) ->
                "Thread ${t.name} (${t.state})\n" +
                    stack.take(25).joinToString("\n") { "    at $it" }
            }

    /**
     * A report of the last freeze or crash not yet shared, as a file; null when there is none.
     * Reads Android's record of the last exit too (a frozen app the system closed).
     */
    fun pendingReport(context: Context): File? {
        val folder = dir ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val parts = mutableListOf<String>()
        var exitAt = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val am = context.getSystemService(ActivityManager::class.java)
            val exit =
                runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 5) }
                    .getOrNull()
                    .orEmpty()
                    .firstOrNull {
                        it.reason == ApplicationExitInfo.REASON_ANR ||
                            it.reason == ApplicationExitInfo.REASON_CRASH ||
                            it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                    }
            if (exit != null && exit.timestamp > prefs.getLong(KEY_REPORTED_AT, 0L)) {
                exitAt = exit.timestamp
                val trace =
                    if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                        runCatching {
                            exit.traceInputStream?.bufferedReader()?.use { it.readText().take(MAX_TRACE_CHARS) }
                        }.getOrNull()
                    } else {
                        null
                    }
                parts +=
                    "Last exit: ${exitReason(
                        exit.reason,
                    )} at ${Date(exit.timestamp)}\n${exit.description.orEmpty()}\n" +
                    (trace ?: "")
            }
        }
        listOf("freeze.txt", "crash.txt").map { File(folder, it) }.filter { it.exists() }.forEach {
            parts += it.readText()
        }
        if (parts.isEmpty()) return null
        if (exitAt > 0) prefs.edit().putLong(KEY_REPORTED_AT, exitAt).apply()
        val header =
            "Hermes app report ${Date()}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n\n"
        return File(folder, "hermes-report.txt").apply { writeText(header + parts.joinToString("\n\n----\n\n")) }
    }

    /** The report was shared or dismissed: start clean. */
    fun clearReport() {
        val folder = dir ?: return
        listOf("freeze.txt", "crash.txt", "hermes-report.txt").forEach { File(folder, it).delete() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitReason(reason: Int): String =
        when (reason) {
            ApplicationExitInfo.REASON_ANR -> "froze (not responding)"
            ApplicationExitInfo.REASON_CRASH -> "crashed"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crashed (native)"
            else -> "exit $reason"
        }
}
