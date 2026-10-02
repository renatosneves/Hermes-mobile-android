package com.m57.hermescontrol.ui.bots

import androidx.lifecycle.ViewModel
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.diagnostics.ChatTrace
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolStatus
import com.m57.hermescontrol.ui.chat.mapServerMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** One bot's hand-off to another, followed live beside the chat. */
data class HandoffState(
    /** The hand-off step's message id in the source chat. */
    val key: String,
    /** The gateway's id for the step, which survives the chat swapping in its saved copy. */
    val toolCallId: String = "",
    val sourceSessionId: String,
    val source: String?,
    val target: String,
    val startedAtMs: Long,
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val done: Boolean = false,
    /** Done and its last messages fetched: the pane can count down to closing. */
    val settled: Boolean = false,
    val pinned: Boolean = false,
    /** False when you closed the side pane; the strip stays until the hand-off ends. */
    val expanded: Boolean = true,
)

/**
 * Watches the open chat for a hand-off and follows the other bot's session over REST. The other
 * bot runs in its own process, so its work never reaches this chat's live stream; the dashboard
 * saves each step as it finishes, so polling shows it a moment later.
 */
class HandoffViewModel : ViewModel() {
    private val _state = MutableStateFlow<HandoffState?>(null)
    val state: StateFlow<HandoffState?> = _state.asStateFlow()

    /** Hand-offs already shown and closed, so they don't open again. */
    private val finished = mutableSetOf<String>()

    /** Called with the open chat whenever it changes. */
    fun observe(
        sessionId: String?,
        self: String?,
        messages: List<ChatMessage>,
    ) {
        val current = _state.value
        if (current != null && current.sourceSessionId != sessionId) {
            // You moved to another chat: stop following, and don't reopen this one.
            finished += current.key
            _state.value = null
        } else if (current != null) {
            val step =
                messages.firstOrNull { it.id == current.key }
                    ?: current.toolCallId.takeIf { it.isNotEmpty() }?.let { id ->
                        messages.firstOrNull { it.role == MessageRole.TOOL && it.toolCallId == id }
                    }
            if (!current.done && (step == null || step.toolStatus != ToolStatus.RUNNING)) {
                ChatTrace.note("hand-off to ${current.target} finished")
                _state.update { it?.copy(done = true) }
            }
            return
        }
        if (sessionId == null) return
        val (step, target) = HandoffDetector.running(messages, self, finished) ?: return
        ChatTrace.note("hand-off to $target started")
        _state.value =
            HandoffState(
                key = step.id,
                toolCallId = step.toolCallId,
                sourceSessionId = sessionId,
                source = self,
                target = target,
                startedAtMs = step.timestamp,
            )
    }

    /**
     * Follows the current hand-off until it is done and its last steps are in. Runs only while the
     * screen is visible; the caller restarts it when the app comes back.
     */
    suspend fun follow() {
        while (true) {
            val current = _state.value ?: return
            if (current.settled) return
            val wasDone = current.done
            val sessionId = current.sessionId ?: findSession(current)
            if (sessionId != null) {
                val messages = fetchMessages(current.target, sessionId)
                _state.update { state ->
                    if (state?.key != current.key) {
                        state
                    } else {
                        state.copy(
                            sessionId = sessionId,
                            messages = messages ?: state.messages,
                            settled = wasDone && messages != null,
                        )
                    }
                }
            } else if (wasDone && System.currentTimeMillis() - current.startedAtMs > FIND_GIVE_UP_MS) {
                // The run never showed up (it may have failed to start); let the pane close.
                _state.update { if (it?.key == current.key) it.copy(settled = true) else it }
            }
            if (_state.value?.settled == true) return
            delay(POLL_MS)
        }
    }

    private suspend fun findSession(handoff: HandoffState): String? {
        val result =
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                safeApiCall(retries = 0) { ApiClient.hermesApi.getProfileSessions(profile = handoff.target) }
            }
        val sessions = (result as? NetworkResult.Success)?.data?.sessions ?: return null
        return HandoffDetector.pickSession(sessions, handoff.startedAtMs)?.id
    }

    private suspend fun fetchMessages(
        target: String,
        sessionId: String,
    ): List<ChatMessage>? {
        val result =
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                safeApiCall(retries = 0) {
                    ApiClient.hermesApi.getSessionMessages(
                        sessionId,
                        limit = PAGE,
                        order = "latest",
                        profile = target,
                    )
                }
            }
        val page = (result as? NetworkResult.Success)?.data ?: return null
        return runCatching {
            mapServerMessages(
                sessionId = sessionId,
                messages = page.messages,
                offset = 0,
                latestPaging = page.messages.all { it.id != null },
                liveMessages = emptyList(),
            ).filter { it.role != MessageRole.SYSTEM }
        }.getOrNull()
    }

    fun setExpanded(expanded: Boolean) = _state.update { it?.copy(expanded = expanded) }

    fun togglePinned() = _state.update { it?.copy(pinned = !it.pinned) }

    /** Closes the hand-off for good (it finished, or you dismissed it). */
    fun close() {
        _state.value?.let { finished += it.key }
        _state.value = null
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val FIND_GIVE_UP_MS = 30_000L
        const val PAGE = 80
    }
}
