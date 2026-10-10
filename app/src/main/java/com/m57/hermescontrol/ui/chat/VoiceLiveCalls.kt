package com.m57.hermescontrol.ui.chat

import android.content.Context
import android.util.Log
import com.m57.hermescontrol.diagnostics.FreezeReporter
import com.m57.hermescontrol.voice.VoiceLiveController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one live voice call, kept outside any screen: folding or unfolding the phone rebuilds the
 * screens, and a call owned by one of them used to hang up with it.
 */
object VoiceLiveCalls {
    private const val TAG = "VoiceLiveCalls"

    class Call(
        val owner: ChatViewModel,
        val controller: VoiceLiveController,
        val title: String,
        val imageUrl: String?,
        /** The call screen is tucked away so you can answer in the chat; the call carries on. */
        val minimised: Boolean = false,
    )

    // A failure inside the call ends the call; it must never take the whole app down with it.
    private val failureHandler =
        CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "Voice call failed", error)
            FreezeReporter.note("Voice call failed: ${error.stackTraceToString().take(2_000)}")
            _active.value?.controller?.dispose()
            _active.value = null
        }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + failureHandler)
    private val _active = MutableStateFlow<Call?>(null)
    val active: StateFlow<Call?> = _active.asStateFlow()

    /** Opens a call with the bot behind [owner] (or returns the one already open on it). */
    fun open(
        context: Context,
        owner: ChatViewModel,
        title: String,
        imageUrl: String?,
    ): Call {
        _active.value?.let { current ->
            if (current.owner === owner) {
                setMinimised(false)
                return _active.value ?: current
            }
            hangUp()
        }
        val controller =
            VoiceLiveController(
                context.applicationContext,
                scope,
                ChatVoiceLiveHost(owner),
                persona = persona(title),
            )
        FreezeReporter.startWatchdog()
        FreezeReporter.note("Voice call opened with $title")
        return Call(owner, controller, title, imageUrl).also { _active.value = it }
    }

    /** Tucks the call screen away (to answer Hermes in the chat) or brings it back. */
    fun setMinimised(minimised: Boolean) {
        _active.value?.let { _active.value = Call(it.owner, it.controller, it.title, it.imageUrl, minimised) }
    }

    /** Ends the call politely (the session gets its close) and drops the screen. */
    fun hangUp() {
        val call = _active.value ?: return
        _active.value = null
        FreezeReporter.note("Voice call hung up")
        FreezeReporter.stopWatchdog()
        call.controller.end()
    }

    /** The chat behind the call is gone for good: drop the call at once. */
    fun ownerCleared(owner: ChatViewModel) {
        val call = _active.value ?: return
        if (call.owner !== owner) return
        _active.value = null
        call.controller.dispose()
    }

    /** Who the voice is: the bot you called, not the server's default "Hermes". */
    fun persona(title: String): String =
        "In this conversation you are the voice of \"$title\", one of the user's Hermes bots. " +
            "Introduce and refer to yourself as $title, never as Hermes. Every request you delegate " +
            "goes to $title, which does the work with its own tools and knowledge."
}
