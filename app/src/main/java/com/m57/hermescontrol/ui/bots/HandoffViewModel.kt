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
    /** The other bot's run is over. */
    val done: Boolean = false,
    /** The CLI step that started it has finished (the run itself may still be going). */
    val stepDoneAtMs: Long? = null,
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
        bots: Set<String> = emptySet(),
    ) {
        traceToolSteps(messages)
        book.update { b ->
            var items =
                b.items.map { item ->
                    if (item.sourceSessionId != sessionId || item.stepDoneAtMs != null ||
                        item.kind != HandoffKind.CLI
                    ) {
                        item
                    } else {
                        val step =
                            messages.firstOrNull { it.id == item.key }
                                ?: item.toolCallId.takeIf { it.isNotEmpty() }?.let { id ->
                                    messages.firstOrNull { it.role == MessageRole.TOOL && it.toolCallId == id }
                                }
                        // A step that is gone may just be the chat reloading; the run's end settles it then.
                        if (step != null && step.toolStatus != ToolStatus.RUNNING) {
                            ChatTrace.note("hand-off step to ${item.target} finished")
                            item.copy(stepDoneAtMs = System.currentTimeMillis())
                        } else {
                            item
                        }
                    }
                }
            if (sessionId != null) {
                val known = finished + items.map { it.key } + items.map { it.toolCallId }.filter { it.isNotEmpty() }
                val found = HandoffDetector.detect(messages, self, known, bots = bots)
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
        if (item.kind == HandoffKind.BOT_CHAT) {
            stepBotChat(item, session, sessionId, now)
            return
        }
        val ended = session?.ended_at != null
        // A run that has gone quiet for a while is over even if it never stamped its end.
        val idle =
            session?.let { (it.last_active ?: it.started_at ?: 0.0) * 1000 < now - IDLE_MS } == true &&
                now - item.startedAtMs > IDLE_MS
        val stepDoneAt = item.stepDoneAtMs
        val giveUpMs = if (item.kind == HandoffKind.BOARD) BOARD_GIVE_UP_MS else CLI_GIVE_UP_MS
        val neverStarted =
            sessionId == null &&
                (now - item.startedAtMs > giveUpMs || (stepDoneAt != null && now - stepDoneAt > CLI_GIVE_UP_MS))
        // Decided before fetching, so the fetch below is guaranteed to hold the run's last steps.
        val doneNow = item.done || ended || idle
        val messages = sessionId?.let { fetchMessages(item.target, it) }
        update(item.key) { current ->
            current.copy(
                sessionId = sessionId,
                messages = messages ?: current.messages,
                done = doneNow || neverStarted,
                settled = (doneNow && messages != null) || neverStarted,
            )
        }
    }

    /**
     * A DM lands in the other bot's long-lived Bot Chat, so only what came after the DM is this
     * hand-off, and the run is over once the bot has answered and the chat has gone quiet.
     */
    private suspend fun stepBotChat(
        item: HandoffState,
        session: SessionInfo?,
        sessionId: String?,
        now: Long,
    ) {
        val all = sessionId?.let { fetchMessages(item.target, it) }
        val shown = all?.let { sinceHandoff(it, item.startedAtMs) }
        val replied =
            shown != null &&
                shown.any { it.role == MessageRole.ASSISTANT } &&
                shown.lastOrNull()?.role == MessageRole.ASSISTANT
        val quiet = session?.let { (it.last_active ?: 0.0) * 1000 < now - BOT_CHAT_QUIET_MS } == true
        val doneNow = item.done || (replied && quiet)
        val neverStarted = shown.isNullOrEmpty() && now - item.startedAtMs > BOARD_GIVE_UP_MS
        if (doneNow && !item.done) ChatTrace.note("hand-off to ${item.target} answered (bot chat)")
        update(item.key) { current ->
            current.copy(
                sessionId = sessionId,
                messages = shown ?: current.messages,
                done = doneNow || neverStarted,
                settled = (doneNow && shown != null) || neverStarted,
            )
        }
    }

    /** Notes each new tool step's name and first command word, to trace hand-offs not yet spotted. */
    private fun traceToolSteps(messages: List<ChatMessage>) {
        for (message in messages.asReversed().take(TRACE_TAIL)) {
            if (message.role != MessageRole.TOOL || message.toolStatus != ToolStatus.RUNNING) continue
            if (message.isHistoricalCache || !traced.add(message.id)) continue
            val first = COMMAND_HEAD.find(message.content)?.groupValues?.get(1)
            ChatTrace.note("tool step ${message.toolName ?: "?"}${first?.let { ": $it" }.orEmpty()}")
        }
        if (traced.size > 500) traced.clear()
    }

    private val traced = mutableSetOf<String>()

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
            finished += gone.map { it.key } + gone.map { it.toolCallId }.filter { it.isNotEmpty() }
            b.copy(items = kept, collapsed = b.collapsed - source, pinned = b.pinned - source)
        }

    /** Drops every hand-off (the view was switched off). */
    fun closeAll() =
        book.update { b ->
            finished += b.items.map { it.key } + b.items.map { it.toolCallId }.filter { it.isNotEmpty() }
            HandoffBook(sourceSessionId = b.sourceSessionId)
        }

    private companion object {
        const val POLL_MS = 2_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val CLI_GIVE_UP_MS = 60_000L

        /** The board can queue a task behind others before its bot starts. */
        const val BOARD_GIVE_UP_MS = 10 * 60_000L
        const val PAGE = 80
        const val IDLE_MS = 3 * 60_000L
        const val TRACE_TAIL = 10

        /** A Bot Chat that answered and stayed still this long is done with the hand-off. */
        const val BOT_CHAT_QUIET_MS = 15_000L

        /** The program a terminal step runs, never its arguments. */
        val COMMAND_HEAD = Regex(""""command"\s*:\s*"([A-Za-z0-9_./-]{1,40})""")
    }
}

/** Allowance for the phone's clock being a little off from the server's. */
private const val CLOCK_SLACK_MS = 60_000L

/**
 * The part of a bot's Bot Chat that belongs to a DM sent at [startedAtMs]: from the delivered
 * "Message from …" turn on, or failing that from the first message after the DM.
 */
internal fun sinceHandoff(
    messages: List<ChatMessage>,
    startedAtMs: Long,
): List<ChatMessage> {
    val earliest = startedAtMs - CLOCK_SLACK_MS
    val start =
        messages
            .indexOfFirst {
                it.role == MessageRole.USER && it.timestamp >= earliest && it.content.startsWith("Message from")
            }.takeIf { it >= 0 }
            ?: messages.indexOfFirst { it.timestamp >= earliest }.takeIf { it >= 0 }
            ?: return emptyList()
    return messages.drop(start)
}
