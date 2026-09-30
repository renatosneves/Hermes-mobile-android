package com.m57.hermescontrol.ui.bots.group

import android.content.ClipData
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.LocalChatFontScale
import com.m57.hermescontrol.ui.bots.BotAvatarCache
import com.m57.hermescontrol.ui.bots.group.components.GroupChatComposer
import com.m57.hermescontrol.ui.bots.group.components.GroupChatSettingsDialog
import com.m57.hermescontrol.ui.bots.group.components.GroupMessageCard
import com.m57.hermescontrol.ui.chat.ChatInputPolicy
import com.m57.hermescontrol.ui.chat.MarkdownText
import com.m57.hermescontrol.ui.chat.components.ChatScrollToBottomFab
import com.m57.hermescontrol.ui.chat.components.rememberChatScrollController
import com.m57.hermescontrol.ui.chat.components.tailContentKey
import com.m57.hermescontrol.ui.chat.formatTimestamp
import com.m57.hermescontrol.ui.common.BotAvatar
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.SkeletonListState
import com.m57.hermescontrol.util.BidiUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GroupChatScreen(
    groupName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupChatViewModel = viewModel { GroupChatViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val scrollController = rememberChatScrollController(listState, scrollScope)
    var inputFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var isFocused by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val handleSend = {
        val text = inputFieldValue.text
        if (text.isNotBlank()) {
            scrollController.jumpToBottom()
            viewModel.sendMessage(text)
            inputFieldValue = TextFieldValue("")
        }
    }

    LaunchedEffect(groupName) {
        viewModel.setGroup(groupName)
    }

    LaunchedEffect(Unit) {
        scrollController.observeUserScrollPosition()
    }

    val streamingMessage = state.messages.find { it.isStreaming }
    val tailKey =
        remember(
            state.messages.size,
            streamingMessage?.id,
            streamingMessage?.text?.length,
            state.activeSpeaker,
        ) {
            tailContentKey(
                messages = state.messages,
                streamingMessage = streamingMessage?.text,
                isThinking = state.activeSpeaker != null,
                subagentIndicators = emptyList<Any>(),
                clarifyRequest = null,
            )
        }

    LaunchedEffect(tailKey) {
        scrollController.onTailChanged(
            tailKey = tailKey,
            messageCount = state.messages.size,
        )
    }

    var avatars by remember { mutableStateOf(BotAvatarCache.snapshot()) }
    LaunchedEffect(state.members) {
        if (state.members.isNotEmpty()) avatars = BotAvatarCache.load(state.members)
    }
    val membersByName = remember(state.members) { state.members.associateBy { it.name } }
    val roomRows = remember(state.messages) { roomItems(state.messages) }

    // Same designed space as the Bots home: navy pane, lavender glow, orbs for every bot.
    val baseScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BotsPalette.PaneTop, BotsPalette.PaneBottom)))
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(RoomHue.copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.8f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(BotsPalette.GlowBottom.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(size.width * 0.9f, size.height),
                            radius = size.width * 0.6f,
                        ),
                    )
                }.testTag("group_room"),
    ) {
        MaterialTheme(colorScheme = BotsPalette.chatScheme(baseScheme, RoomHue)) {
            HermesScaffold(
                title = {
                    RoomTitle(
                        name = state.groupName.ifBlank { groupName },
                        members = state.members,
                        activeSpeaker = state.activeSpeaker,
                        avatars = avatars,
                    )
                },
                navigationIcon = NavIcon.Back(onBack),
                actions = {
                    if (state.activeSpeaker != null) {
                        IconButton(
                            onClick = { viewModel.stopGeneration() },
                            modifier = Modifier.testTag("group_chat_stop_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.group_chat_action_stop),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("group_chat_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.group_chat_action_settings),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp)
                            .imePadding(),
                ) {
                    when {
                        state.isLoading -> {
                            SkeletonListState(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            )
                        }

                        state.errorMessage != null -> {
                            ErrorState(
                                message = state.errorMessage ?: "",
                                onRetry = { viewModel.setGroup(groupName) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            )
                        }

                        state.messages.isEmpty() -> {
                            EmptyState(
                                title = stringResource(R.string.group_chat_empty_title),
                                subtitle = stringResource(R.string.group_chat_empty_subtitle),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            )
                        }

                        else -> {
                            val currentDensity = LocalDensity.current
                            val chatFontScale = LocalChatFontScale.current
                            val chatDensity =
                                remember(currentDensity, chatFontScale) {
                                    Density(
                                        density = currentDensity.density,
                                        fontScale = currentDensity.fontScale * chatFontScale,
                                    )
                                }

                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            ) {
                                CompositionLocalProvider(LocalDensity provides chatDensity) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(
                                            items = roomRows,
                                            key = { it.key },
                                        ) { row ->
                                            when (row) {
                                                is RoomItem.Message -> {
                                                    GroupMessageCard(
                                                        message = row.message,
                                                        imageUrl = avatars[row.message.senderName],
                                                    )
                                                }

                                                is RoomItem.Passes -> {
                                                    PassesRow(
                                                        passes = row.messages,
                                                        membersByName = membersByName,
                                                        avatars = avatars,
                                                    )
                                                }
                                            }
                                        }

                                        state.activeSpeaker?.let { speaker ->
                                            item(key = "active_speaker") {
                                                ThinkingRow(
                                                    speaker = speaker,
                                                    member = state.members.firstOrNull { it.effectiveTitle == speaker },
                                                    avatars = avatars,
                                                )
                                            }
                                        }
                                    }
                                }

                                ChatScrollToBottomFab(
                                    show = !scrollController.isFollowingBottom && state.messages.isNotEmpty(),
                                    pendingCount = scrollController.pendingCount,
                                    onScrollToBottom = { scrollController.resumeFollowing() },
                                )
                            }
                        }
                    }

                    // ── COMPOSER ──
                    val groupBots =
                        remember(state.members) {
                            state.members.distinctBy { it.name.lowercase() }
                        }

                    GroupChatComposer(
                        inputFieldValue = inputFieldValue,
                        onInputValueChange = { inputFieldValue = it },
                        groupBots = groupBots,
                        groupName = state.groupName.ifBlank { groupName },
                        activeSpeaker = state.activeSpeaker,
                        isFocused = isFocused,
                        onFocusChanged = { isFocused = it },
                        onSend = handleSend,
                        onStop = { viewModel.stopGeneration() },
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        GroupChatSettingsDialog(
            currentMaxMessages = state.maxBotMessages,
            currentMaxPasses = state.maxContinuationPasses,
            currentSystemPrompt = state.systemPrompt,
            onDismiss = { showSettingsDialog = false },
            onSave = { maxMsgs, maxPasses, prompt ->
                viewModel.updateGroupLimits(maxMsgs, maxPasses, prompt)
                showSettingsDialog = false
            },
        )
    }
}
