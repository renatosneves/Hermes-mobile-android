package com.m57.hermescontrol.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.SessionInfo
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** One bot's hand-off to another, followed live. */
data class HandoffState(
    /** The hand-off step's message id in the source chat. */
    val key: String,
    /** The gateway's id for the step, which survives the chat swapping in its saved copy. */
    val toolCallId: String = "",
    val kind: HandoffKind = HandoffKind.CLI,
    val sourceSessionId: String,
    val source: String?,
    val target: String,
    val startedAtMs: Long,
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val done: Boolean = false,
    /** Done and its last messages fetched. */
    val settled: Boolean = false,
)

/** The hand-offs from the chat on show, and how the pane shows them. */
data class HandoffView(
    val items: List<HandoffState>,
    val selected: HandoffState,
    val expanded: Boolean,
    val pinned: Boolean,
) {
    val key: String get() = items.joinToString(",") { it.key }
    val done: Boolean get() = items.all { it.done }

    /** Every hand-off is in: the pane can count down to closing. */
    val settled: Boolean get() = items.all { it.settled }
}

private data class HandoffBook(
    val items: List<HandoffState> = emptyList(),
    /** The chat on show; only its hand-offs are on screen, the rest keep being followed. */
    val sourceSessionId: String? = null,
    val selectedKey: String? = null,
    /** Per source chat: false once you closed the side pane, so it shows as a strip. */
    val collapsed: Set<String> = emptySet(),
    val pinned: Set<String> = emptySet(),
)

/**
 * Watches the open chat for hand-offs and follows each other bot's session over REST. The other
 * bot runs in its own process, so its work never reaches this chat's live stream; the dashboard
 * saves each step as it finishes, so polling shows it a moment later. Hand-offs outlive switching
 * to another bot: they keep being followed and show again when you come back.
 */
class HandoffViewModel : ViewModel() {
    private val book = MutableStateFlow(HandoffBook())

    val state: StateFlow<HandoffView?> =
        book
            .map(::viewOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True while any hand-off, on screen or not, still needs following. */
    val following: StateFlow<Boolean> =
        book
            .map { b -> b.items.any { !it.settled } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Hand-offs already shown and closed, so they don't open again. */
    private val finished = mutableSetOf<String>()

    private fun viewOf(b: HandoffBook): HandoffView? {
        val source = b.sourceSessionId ?: return null
        val items = b.items.filter { it.sourceSessionId == source }
        if (items.isEmpty()) return null
        val selected = items.firstOrNull { it.key == b.selectedKey } ?: items.firstOrNull { !it.done } ?: items.last()
        return HandoffView(items, selected, expanded = source !in b.collapsed, pinned = source in b.pinned)
    }

    /** Called with the open chat whenever it changes. */
    fun observe(
        sessionId: String?,
        self: String?,
        messages: List<ChatMessage>,
    ) {
        book.update { b ->
            var items =
                b.items.map { item ->
                    if (item.sourceSessionId != sessionId || item.done || item.kind != HandoffKind.CLI) {
                        item
                    } else {
                        val step =
                            messages.firstOrNull { it.id == item.key }
                                ?: item.toolCallId.takeIf { it.isNotEmpty() }?.let { id ->
                                    messages.firstOrNull { it.role == MessageRole.TOOL && it.toolCallId == id }
                                }
                        // A step that is gone may just be the chat reloading; the run's end settles it then.
                        if (step != null && step.toolStatus != ToolStatus.RUNNING) {
                            ChatTrace.note("hand-off to ${item.target} finished")
                            item.copy(done = true)
                        } else {
                            item
                        }
                    }
                }
            if (sessionId != null) {
                val known = finished + items.map { it.key }
                val found = HandoffDetector.detect(messages, self, known)
                for (h in found) {
                    ChatTrace.note("hand-off to ${h.target} started (${h.kind.name.lowercase()})")
                    items = items +
                        HandoffState(
                            key = h.message.id,
                            toolCallId = h.message.toolCallId,
                            kind = h.kind,
                            sourceSessionId = sessionId,
                            source = self,
                            target = h.target,
                            startedAtMs = h.message.timestamp,
                        )
                }
                val selected = if (found.isNotEmpty()) found.last().message.id else b.selectedKey
                b.copy(items = items, sourceSessionId = sessionId, selectedKey = selected)
            } else {
                b.copy(items = items, sourceSessionId = null)
            }
        }
    }

    /**
     * Follows every unfinished hand-off until each is done and its last steps are in. Runs only
     * while the screen is visible; the caller restarts it when the app comes back.
     */
    suspend fun follow() {
        while (true) {
            val pending = book.value.items.filter { !it.settled }
            if (pending.isEmpty()) return
            for (item in pending) step(item)
            delay(POLL_MS)
        }
    }

    private suspend fun step(item: HandoffState) {
        val now = System.currentTimeMillis()
        val sessions = listSessions(item.target)
        val taken =
            book.value.items
                .filter { it.key != item.key }
                .mapNotNull { it.sessionId }
                .toSet()
        val session: SessionInfo? =
            sessions?.let { list ->
                item.sessionId?.let { id -> list.firstOrNull { it.id == id } }
                    ?: if (item.sessionId ==
                        null
                    ) {
                        HandoffDetector.pickSession(list, item.startedAtMs, item.kind, taken)
                    } else {
                        null
                    }
            }
        val sessionId = item.sessionId ?: session?.id
        val ended = session?.ended_at != null
        val messages = sessionId?.let { fetchMessages(item.target, it) }
        val waitedTooLong =
            sessionId == null &&
                now - item.startedAtMs > if (item.kind == HandoffKind.BOARD) BOARD_GIVE_UP_MS else CLI_GIVE_UP_MS
        update(item.key) { current ->
            val done = current.done || ended || waitedTooLong
            current.copy(
                sessionId = sessionId,
                messages = messages ?: current.messages,
                done = done,
                // Settled only by a fetch that began after the end was known, so nothing is missing.
                settled = ((item.done || ended) && messages != null) || waitedTooLong,
            )
        }
    }

    private fun update(
        key: String,
        transform: (HandoffState) -> HandoffState,
    ) = book.update { b -> b.copy(items = b.items.map { if (it.key == key) transform(it) else it }) }

    private suspend fun listSessions(target: String): List<SessionInfo>? {
        val result =
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                safeApiCall(retries = 0) { ApiClient.hermesApi.getProfileSessions(profile = target) }
            }
        return (result as? NetworkResult.Success)?.data?.sessions
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

    fun select(key: String) = book.update { it.copy(selectedKey = key) }

    /** Opens the side pane again, or folds it into the strip, for the chat on show. */
    fun setExpanded(expanded: Boolean) =
        book.update { b ->
            val source = b.sourceSessionId ?: return@update b
            b.copy(collapsed = if (expanded) b.collapsed - source else b.collapsed + source)
        }

    fun togglePinned() =
        book.update { b ->
            val source = b.sourceSessionId ?: return@update b
            b.copy(pinned = if (source in b.pinned) b.pinned - source else b.pinned + source)
        }

    /** Closes the chat on show's hand-offs for good (they finished, or you dismissed them). */
    fun close() =
        book.update { b ->
            val source = b.sourceSessionId ?: return@update b
            val (gone, kept) = b.items.partition { it.sourceSessionId == source }
            finished += gone.map { it.key }
            b.copy(items = kept, collapsed = b.collapsed - source, pinned = b.pinned - source)
        }

    /** Drops every hand-off (the view was switched off). */
    fun closeAll() =
        book.update { b ->
            finished += b.items.map { it.key }
            HandoffBook(sourceSessionId = b.sourceSessionId)
        }

    private companion object {
        const val POLL_MS = 2_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val CLI_GIVE_UP_MS = 60_000L

        /** The board can queue a task behind others before its bot starts. */
        const val BOARD_GIVE_UP_MS = 10 * 60_000L
        const val PAGE = 80
    }
}
