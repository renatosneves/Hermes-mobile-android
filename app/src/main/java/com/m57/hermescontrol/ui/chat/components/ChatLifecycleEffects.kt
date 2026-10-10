package com.m57.hermescontrol.ui.chat.components

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.diagnostics.ChatTrace
import com.m57.hermescontrol.notification.ReplyNotificationTracker
import com.m57.hermescontrol.notification.correlationScopeId
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.ChatViewModel
import com.m57.hermescontrol.ui.chat.ClarifyUi
import com.m57.hermescontrol.ui.chat.SecretPromptUi
import com.m57.hermescontrol.ui.chat.SudoPromptUi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class Optional<T>(
    val value: T?,
)

@Composable
fun ChatLifecycleEffects(
    sessionId: String?,
    connectionStatus: ConnectionStatus,
    currentSessionId: String?,
    messages: List<ChatMessage>,
    errorMessage: String?,
    backgroundCompleteMessage: String?,
    openError: String?,
    clarifyRequest: ClarifyUi?,
    sudoPrompt: SudoPromptUi?,
    secretPrompt: SecretPromptUi?,
    listState: LazyListState,
    scrollController: ChatScrollController,
    snackbarHostState: SnackbarHostState,
    viewModel: ChatViewModel,
    isOverlayActive: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Switch to session from notification/history
    val pendingNavigation = NavigationController.pendingChatNavigation
    val pendingNewChatNavigation = NavigationController.pendingNewChatNavigation
    // The session this screen was last pointed at. A reconnect switches back to it only when
    // something other than you moved the chat off it (opening a bot switches profile, which
    // wipes the chat); after your own /new, going back would hide what you just sent.
    var appliedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    // Keyed on the chat's own session too, so a wipe that doesn't drop the socket still reopens.
    LaunchedEffect(sessionId, pendingNavigation, pendingNewChatNavigation, connectionStatus, currentSessionId) {
        if (connectionStatus != ConnectionStatus.CONNECTED) return@LaunchedEffect
        val newChatRequest = NavigationController.consumePendingNewChatNavigation()
        val request = NavigationController.consumePendingChatNavigation()
        if (newChatRequest != null) {
            viewModel.createNewSession(byUser = true)
            return@LaunchedEffect
        }
        val target = request?.sessionId ?: sessionId
        if (!target.isNullOrBlank() &&
            (request != null || target != appliedSessionId || viewModel.shouldReopen(target))
        ) {
            appliedSessionId = target
            viewModel.switchSession(target)
        }
        if (request?.scrollToBottom == true) {
            scrollController.jumpToBottom(animated = false)
        }
    }

    // Land instantly at the bottom on a session switch (issue #682), or back where you were
    // reading if you left this chat scrolled up. Then remember where you are, for next time.
    LaunchedEffect(currentSessionId) {
        val id = currentSessionId ?: return@LaunchedEffect
        val saved = ChatPaneMemory.position(id)
        val settling = if (saved != null) scrollController.restorePosition(saved) else scrollController.jumpToBottom()
        settling.join()
        snapshotFlow {
            // Only while this chat's own rows are on screen: during a switch the list briefly
            // shows nothing (or the next chat), which must not overwrite where you were.
            val shown = viewModel.uiState.value
            if (shown.currentSessionId != id || shown.messages.isEmpty() || listState.layoutInfo.totalItemsCount == 0) {
                null
            } else {
                Optional(scrollController.currentPosition())
            }
        }.collect { now -> if (now != null) ChatPaneMemory.savePosition(id, now.value) }
    }

    // Refresh chat state when the app returns to the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        ChatTrace.note("app to background")
                    }

                    Lifecycle.Event.ON_START -> {
                        ChatTrace.note("app to foreground")
                        viewModel.refreshSettings()
                        viewModel.refreshCurrentSession()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val messageMap = remember(messages) { messages.associateBy { it.id } }

    // Auto-dismiss reply notifications when their message is displayed in the viewport
    LaunchedEffect(lifecycleOwner, currentSessionId, messageMap, listState, isOverlayActive) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow<List<ChatMessage>> {
                if (currentSessionId.isNullOrBlank() || isOverlayActive) {
                    emptyList()
                } else {
                    ChatReadObserver.findVisibleAssistantMessages(listState.layoutInfo, messageMap)
                }
            }.distinctUntilChanged()
                .collect { visibleAssistantMsgs ->
                    val scopeId = correlationScopeId()
                    for (msg in visibleAssistantMsgs) {
                        ReplyNotificationTracker.onMessageVisible(
                            context = context,
                            scopeId = scopeId,
                            sessionId = currentSessionId,
                            completionId = msg.completionId,
                        )
                    }
                }
        }
    }

    // Request POST_NOTIFICATIONS permission on Android 13+
    val requestNotificationPermission =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* granted */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(permission)
            }
        }
    }

    // Show error as snackbar
    val clipboard = LocalClipboard.current
    val copyLabel = stringResource(R.string.action_copy_error)
    // Own scope + immediate clear: a later `errorMessage = null` elsewhere must not cancel a visible popup.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            viewModel.clearError()
            snackbarScope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        error,
                        actionLabel = copyLabel,
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, error)))
                }
            }
        }
    }

    // Show background-complete as a non-blocking snackbar (issue #527)
    LaunchedEffect(backgroundCompleteMessage) {
        backgroundCompleteMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearBackgroundComplete()
        }
    }

    // Show attachment-open failures as a non-blocking snackbar (issue #724)
    LaunchedEffect(openError) {
        openError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOpenError()
        }
    }

    // Sudo / secret prompt dialogs (issue #524)
    sudoPrompt?.let { prompt ->
        SudoPromptDialog(
            onConfirm = viewModel::respondToSudo,
            onDismiss = viewModel::dismissSudo,
        )
    }

    secretPrompt?.let { prompt ->
        SecretPromptDialog(
            onConfirm = viewModel::respondToSecret,
            onDismiss = viewModel::dismissSecret,
            envVar = prompt.envVar,
            prompt = prompt.prompt,
        )
    }
}
