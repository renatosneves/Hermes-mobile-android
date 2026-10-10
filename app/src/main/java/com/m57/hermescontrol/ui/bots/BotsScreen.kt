package com.m57.hermescontrol.ui.bots

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.diagnostics.ChatTrace
import com.m57.hermescontrol.share.ShareInbox
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.BotsTheme
import com.m57.hermescontrol.theme.DarkStyle
import com.m57.hermescontrol.theme.LightStyle
import com.m57.hermescontrol.theme.LocalToybox
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ToyFonts
import com.m57.hermescontrol.theme.toyDots
import com.m57.hermescontrol.theme.toySticker
import com.m57.hermescontrol.ui.chat.ChatScreen
import com.m57.hermescontrol.ui.chat.ChatViewModel
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.VoiceLiveCalls
import com.m57.hermescontrol.ui.common.DisableDrawerGestures
import com.m57.hermescontrol.ui.common.LocalDrawerGestureController
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.ToastEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Width from which the Bots home shows the chat beside the list (an unfolded Fold, a tablet). */
private val TWO_PANE_MIN_WIDTH = 600.dp

/** How often the roster refreshes so working states stay current. */
private const val REFRESH_INTERVAL_MS = 20_000L

private enum class RailFilter { ALL, WORKING, GROUPS }

private fun ProfileInfo.canonicalSessionId(): String? =
    (canonical_session?.resolved_id ?: canonical_session?.id)?.takeIf { it.isNotBlank() }

private fun nowSeconds(): Double = System.currentTimeMillis() / 1000.0

private fun hueFor(profile: ProfileInfo): Color =
    runCatching { Color(android.graphics.Color.parseColor(BotsPresentation.colorHex(profile))) }
        .getOrDefault(BotsPalette.Hues[BotsPresentation.hueIndex(profile.name, BotsPalette.Hues.size)])

private val Mono = FontFamily.Monospace

/**
 * Grok-style Bots home: the bot rail on the left and the selected bot's conversation on the
 * right when there is room. On a narrow screen the rail fills it and a tap opens the chat.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: BotsViewModel = viewModel { BotsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var openBotName by rememberSaveable { mutableStateOf<String?>(null) }
    var openSessionId by rememberSaveable { mutableStateOf<String?>(null) }

    // On a narrow screen (a folded Fold) the chat opens full screen inside this home, with the
    // same look as the unfolded pane; back returns to the list.
    var phoneChatOpen by rememberSaveable { mutableStateOf(false) }
    var editingBot by remember { mutableStateOf<ProfileInfo?>(null) }
    var disbandingGroup by remember { mutableStateOf<GroupInfo?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var now by remember { mutableDoubleStateOf(nowSeconds()) }
    val context = LocalContext.current
    remember {
        BotSeenStore.init(context)
        BotPinStore.init(context)
        BotsThemeStore.init(context)
        BotChatStore.init(context)
        BotsSizeStore.init(context)
        GroupPictureStore.init(context)
    }

    // Horizontal drags on this screen belong to its content; the drawer opens from the menu button.
    DisableDrawerGestures()
    DecorativeMotion.TrackPowerSaver()

    // Poll only while the screen is on show; coming back refreshes straight away.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        var first = true
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!first) {
                now = nowSeconds()
                viewModel.loadBots(isRefresh = true)
            }
            first = false
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                now = nowSeconds()
                viewModel.loadBots(isRefresh = true)
            }
        }
    }

    ToastEffect(toastMessage = state.toastMessage, onClearToast = { viewModel.clearToast() })

    // A bot handing work to another: followed while the screen is on show, closed once it's done.
    val handoffViewModel: HandoffViewModel = viewModel { HandoffViewModel() }
    val handoff by handoffViewModel.state.collectAsStateWithLifecycle()
    var handoffMode by remember { mutableStateOf(HandoffPrefs.mode(context)) }
    var showHandoffSetting by remember { mutableStateOf(false) }
    var handoffSheetOpen by remember { mutableStateOf(false) }
    val handoffFollowing by handoffViewModel.following.collectAsStateWithLifecycle()
    LaunchedEffect(handoffFollowing) {
        if (handoffFollowing) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { handoffViewModel.follow() }
        }
    }
    LaunchedEffect(handoff == null) {
        if (handoff == null) handoffSheetOpen = false
    }
    LaunchedEffect(handoff?.key, handoff?.settled, handoff?.pinned) {
        val h = handoff
        if (h != null && h.settled && !h.pinned) {
            delay(HANDOFF_CLOSE_DELAY_MS)
            handoffViewModel.close()
        }
    }
    LaunchedEffect(handoffMode) {
        if (handoffMode == HandoffMode.OFF) handoffViewModel.closeAll()
    }

    val baseScheme = MaterialTheme.colorScheme
    BotsTheme(baseScheme) {
        BotsDialogs(
            viewModel = viewModel,
            state = state,
            editingBot = editingBot,
            onDismissEdit = { editingBot = null },
            disbandingGroup = disbandingGroup,
            onDismissDisband = { disbandingGroup = null },
            showCreateDialog = showCreateDialog,
            onDismissCreate = { showCreateDialog = false },
            showCreateGroupDialog = showCreateGroupDialog,
            onDismissCreateGroup = { showCreateGroupDialog = false },
        )
        if (showHandoffSetting) {
            HandoffModeDialog(
                current = handoffMode,
                onPick = {
                    handoffMode = it
                    HandoffPrefs.setMode(context, it)
                },
                onDismiss = { showHandoffSetting = false },
            )
        }

        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(BotsPalette.PaneBottom)
                    .testTag("bots_home"),
        ) {
            val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
            val onOpenBot: (ProfileInfo) -> Unit = { profile ->
                scope.launch {
                    // Profile first, so the chat's resume of this bot's session already names it.
                    viewModel.selectBot(profile)
                    openBotName = profile.name
                    // The chat you last used with this bot (a new one you started, say) unless it has
                    // spoken in its main chat since; the list previews the same one.
                    openSessionId = viewModel.uiState.value.chatFor(profile)
                    viewModel.markSeen(profile)
                    if (!twoPane) phoneChatOpen = true
                    // Something shared is waiting: this bot's chat takes it.
                    ShareInbox.arm()
                }
            }
            val rail: @Composable (Modifier, String?) -> Unit = { railModifier, selectedName ->
                Box(railModifier) {
                    BotsScaled {
                        BotsRail(
                            modifier = Modifier.fillMaxSize(),
                            state = state,
                            now = now,
                            selectedName = selectedName,
                            onOpenDrawer = onOpenDrawer,
                            onSearch = viewModel::setSearchQuery,
                            onOpenBot = onOpenBot,
                            onEditBot = { editingBot = it },
                            onTogglePin = { viewModel.togglePin(it.name) },
                            onOpenGroup = { group ->
                                if (group.members.isNotEmpty()) {
                                    NavigationController.navigateTo(com.m57.hermescontrol.GroupChatKey(group.name))
                                }
                            },
                            onOpenAllBots = {
                                NavigationController.navigateTo(
                                    com.m57.hermescontrol.GroupChatKey(BotsPresentation.ALL_BOTS_ROOM),
                                )
                            },
                            onDisbandGroup = { disbandingGroup = it },
                            onCreateBot = { showCreateDialog = true },
                            onCreateGroup = { showCreateGroupDialog = true },
                            onToggleHidden = viewModel::toggleShowHidden,
                            onRefresh = {
                                now = nowSeconds()
                                viewModel.loadBots(isRefresh = true)
                            },
                            onHandoffSetting = { showHandoffSetting = true },
                        )
                    }
                }
            }

            // Nothing picked yet: open on the Chief of Staff, the bot that hands work out.
            LaunchedEffect(twoPane, state.profiles.isNotEmpty()) {
                if (twoPane && openBotName == null) {
                    state.profiles.firstOrNull { it.name == BotsPresentation.DEFAULT_BOT }?.let(onOpenBot)
                }
            }
            // A live call carries on through a fold: keep its chat (and call screen) on show.
            val voiceCall by VoiceLiveCalls.active.collectAsStateWithLifecycle()
            LaunchedEffect(twoPane, voiceCall) {
                if (!twoPane && voiceCall != null && openBotName != null) phoneChatOpen = true
            }
            val shared by ShareInbox.pending.collectAsStateWithLifecycle()
            LaunchedEffect(shared != null) {
                if (shared != null && !twoPane) phoneChatOpen = false
            }
            val selectedName = openBotName ?: state.activeProfileName
            val selected = state.profiles.firstOrNull { it.name == selectedName }
            val chatSessionId = openSessionId ?: selected?.canonicalSessionId()
            val handoffBot: (String) -> HandoffBot = { name ->
                val target = state.profiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val title = target?.effectiveTitle ?: name.replaceFirstChar { it.uppercase() }
                HandoffBot(
                    title = title,
                    hue = target?.let { hueFor(it) } ?: BotsPalette.Muted,
                    initials = BotsPresentation.initials(title),
                    shapeKey = target?.botMeta()?.avatar?.shape,
                    imageUrl = target?.let { state.imageFor(it) },
                )
            }
            val handoffPane: @Composable (Modifier, () -> Unit) -> Unit = { paneModifier, onClose ->
                val view = handoff
                if (view != null) {
                    val h = view.selected
                    val target = state.profiles.firstOrNull { it.name.equals(h.target, ignoreCase = true) }
                    Box(paneModifier) {
                        BotsScaled {
                            HandoffPane(
                                view = view,
                                botFor = handoffBot,
                                onSelect = handoffViewModel::select,
                                sourceTitle = selected?.effectiveTitle ?: h.source.orEmpty(),
                                onClose = onClose,
                                onTogglePin = handoffViewModel::togglePinned,
                                onOpenChat =
                                    if (target != null && h.sessionId != null) {
                                        {
                                            val sessionId = h.sessionId
                                            handoffViewModel.close()
                                            scope.launch {
                                                viewModel.selectBot(target)
                                                openBotName = target.name
                                                openSessionId = sessionId
                                                viewModel.markSeen(target)
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            val botNames = remember(state.profiles) { state.profiles.map { it.name }.toSet() }
            // You moved to another chat with the open bot (new chat, or one from its history): keep it.
            val onSessionChanged: (String, String) -> Unit = { bot, sessionId ->
                if (bot == openBotName || (openBotName == null && bot == selectedName)) {
                    openBotName = bot
                    openSessionId = sessionId
                    viewModel.rememberChat(bot, sessionId)
                }
            }
            val onHandoffMessages: (String?, List<com.m57.hermescontrol.ui.chat.ChatMessage>) -> Unit =
                { sessionId, messages ->
                    if (handoffMode != HandoffMode.OFF) {
                        handoffViewModel.observe(sessionId, selected?.name, messages, botNames)
                    }
                }
            if (handoffSheetOpen && handoff != null) {
                ModalBottomSheet(
                    onDismissRequest = { handoffSheetOpen = false },
                    containerColor = BotsPalette.Rail,
                ) {
                    HandoffSheetContent {
                        handoffPane(Modifier) {
                            handoffSheetOpen = false
                            if (handoff?.done == true) handoffViewModel.close()
                        }
                    }
                }
            }
            if (twoPane) {
                // Dragging a divider changes these fractions of the screen width; saved on release.
                var railFraction by remember {
                    mutableFloatStateOf(BotsLayoutPrefs.railFraction(context) ?: DEFAULT_RAIL_FRACTION)
                }
                var handoffFraction by remember {
                    mutableFloatStateOf(BotsLayoutPrefs.handoffFraction(context) ?: DEFAULT_HANDOFF_FRACTION)
                }
                val density = LocalDensity.current
                val screenWidth = maxWidth
                val maxWidthPx = with(density) { screenWidth.toPx() }
                val railWidth = paneWidth(maxWidth, railFraction, MIN_RAIL_WIDTH)
                val handoffWidth = paneWidth(maxWidth, handoffFraction, MIN_HANDOFF_WIDTH)
                // The bot list can be folded away so the chat takes the whole screen; the choice
                // is kept between launches. With no bot open the list always shows.
                var listHidden by remember { mutableStateOf(BotsLayoutPrefs.listHidden(context)) }
                // Side by side: the list makes way while the other bot's pane is open.
                val split = handoffMode == HandoffMode.SPLIT && handoff?.expanded == true
                val showList = (!listHidden && !split) || selected == null
                Row(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = showList,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                    ) {
                        Row {
                            rail(Modifier.width(railWidth).fillMaxHeight(), selectedName)
                            DragDivider(
                                onDrag = { delta ->
                                    val width = with(density) { railWidth.toPx() } + delta
                                    railFraction = paneFraction(screenWidth, maxWidthPx, width, MIN_RAIL_WIDTH)
                                },
                                onDragEnd = { BotsLayoutPrefs.setRailFraction(context, railFraction) },
                                onReset = {
                                    railFraction = DEFAULT_RAIL_FRACTION
                                    BotsLayoutPrefs.setRailFraction(context, railFraction)
                                },
                            )
                        }
                    }
                    // The open conversation counts as read, including replies arriving while it's open.
                    LaunchedEffect(selected?.name, selected?.let { BotsPresentation.messageCount(it) }) {
                        selected?.let(viewModel::markSeen)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        BotsScaled {
                            BotsChatPane(
                                modifier = Modifier.fillMaxSize(),
                                profile = selected,
                                imageUrl = selected?.let { state.imageFor(it) },
                                needsYou = selected?.name in state.needsYou,
                                sessionId = chatSessionId,
                                now = now,
                                working = selected?.let { state.isWorking(it, now) } == true,
                                task = selected?.let(state::taskFor),
                                lastAt = selected?.let(state::lastMessageTime),
                                baseScheme = baseScheme,
                                listToggle =
                                    NavIcon.Action(
                                        icon = if (showList) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                                        description =
                                            stringResource(
                                                if (showList) R.string.bots_list_hide else R.string.bots_list_show,
                                            ),
                                    ) {
                                        if (split) {
                                            // Asking for the list folds the other bot's pane into the strip.
                                            handoffViewModel.setExpanded(false)
                                            listHidden = false
                                        } else {
                                            listHidden = showList
                                        }
                                        BotsLayoutPrefs.setListHidden(context, listHidden)
                                    },
                                onChatMessages = onHandoffMessages,
                                onSessionChanged = onSessionChanged,
                                handoff = handoff?.takeIf { handoffMode == HandoffMode.STRIP || !it.expanded },
                                handoffBot = handoffBot,
                                onHandoffStrip = {
                                    if (handoffMode == HandoffMode.SPLIT) {
                                        handoffViewModel.setExpanded(true)
                                    } else {
                                        handoffSheetOpen = true
                                    }
                                },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = split && handoff != null,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                    ) {
                        Row {
                            // The pane sits right of its divider, so dragging right makes it narrower.
                            DragDivider(
                                onDrag = { delta ->
                                    val width = with(density) { handoffWidth.toPx() } - delta
                                    handoffFraction = paneFraction(screenWidth, maxWidthPx, width, MIN_HANDOFF_WIDTH)
                                },
                                onDragEnd = { BotsLayoutPrefs.setHandoffFraction(context, handoffFraction) },
                                onReset = {
                                    handoffFraction = DEFAULT_HANDOFF_FRACTION
                                    BotsLayoutPrefs.setHandoffFraction(context, handoffFraction)
                                },
                            )
                            handoffPane(Modifier.width(handoffWidth).fillMaxHeight().statusBarsPadding()) {
                                if (handoff?.done == true) {
                                    handoffViewModel.close()
                                } else {
                                    handoffViewModel.setExpanded(false)
                                }
                            }
                        }
                    }
                }
            } else if (phoneChatOpen && selected != null) {
                val closeChat = {
                    phoneChatOpen = false
                    viewModel.loadBots(isRefresh = true, thenMarkSeen = selected.name)
                }
                BackHandler(onBack = closeChat)
                LaunchedEffect(selected.name, BotsPresentation.messageCount(selected)) {
                    viewModel.markSeen(selected)
                }
                Box(Modifier.fillMaxSize()) {
                    BotsScaled {
                        BotsChatPane(
                            modifier = Modifier.fillMaxSize(),
                            profile = selected,
                            imageUrl = state.imageFor(selected),
                            needsYou = selected.name in state.needsYou,
                            sessionId = chatSessionId,
                            now = now,
                            working = state.isWorking(selected, now),
                            task = state.taskFor(selected),
                            lastAt = state.lastMessageTime(selected),
                            baseScheme = baseScheme,
                            onBack = closeChat,
                            onChatMessages = onHandoffMessages,
                            onSessionChanged = onSessionChanged,
                            handoff = handoff,
                            handoffBot = handoffBot,
                            onHandoffStrip = { handoffSheetOpen = true },
                        )
                    }
                }
            } else {
                rail(Modifier.fillMaxSize(), null)
            }
        }
    }
}

/** Draws its content at the chosen Bots size: dp and sp scale together, so nothing overlaps. */
@Composable
private fun BotsScaled(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * BotsSizeStore.scale, d.fontScale)) {
        content()
    }
}

/** Shown while something shared from another app waits for you to pick a bot. */
@Composable
private fun ShareBanner(onCancel: () -> Unit) {
    Row(
        modifier =
            Modifier
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BotsPalette.Deck2)
                .border(1.dp, BotsPalette.Attention, RoundedCornerShape(14.dp))
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.share_pick_bot),
                color = BotsPalette.Fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(stringResource(R.string.share_pick_bot_hint), color = BotsPalette.Muted, fontSize = 12.sp)
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.share_cancel), color = BotsPalette.Attention)
        }
    }
}

private const val DEFAULT_RAIL_FRACTION = 0.42f
private const val DEFAULT_HANDOFF_FRACTION = 0.46f
private val MIN_RAIL_WIDTH = 240.dp
private val MIN_HANDOFF_WIDTH = 280.dp

/** Room always kept for the chat pane between the two side panes. */
private val MIN_CHAT_WIDTH = 320.dp

/** Width of a side pane for [fraction] of the screen, never below [min] nor leaving the chat under its minimum. */
private fun paneWidth(
    maxWidth: Dp,
    fraction: Float,
    min: Dp,
): Dp = (maxWidth * fraction).coerceIn(min, maxOf(maxWidth - MIN_CHAT_WIDTH, min))

/** The fraction of the screen that a pane [widthPx] wide takes, once clamped to the pane's limits. */
private fun paneFraction(
    maxWidth: Dp,
    maxWidthPx: Float,
    widthPx: Float,
    min: Dp,
): Float {
    if (maxWidthPx <= 0f) return 0f
    val minFraction = min.value / maxWidth.value
    val maxFraction = maxOf(maxWidth.value - MIN_CHAT_WIDTH.value, min.value) / maxWidth.value
    return (widthPx / maxWidthPx).coerceIn(minFraction, maxFraction)
}

/**
 * Draggable divider between two panes: a hairline with a grab pill. Drag to resize, double-tap to
 * reset. [onDrag] gets the horizontal movement in pixels.
 */
@Composable
private fun DragDivider(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: () -> Unit = {},
) {
    var dragging by remember { mutableStateOf(false) }
    val description = stringResource(R.string.bots_resize_panels)
    val currentReset by rememberUpdatedState(onReset)
    Box(
        modifier =
            modifier
                .width(16.dp)
                .fillMaxHeight()
                .background(Color.Transparent)
                .semantics { contentDescription = description }
                .testTag("bots_divider")
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentReset() }) }
                .draggable(
                    state = rememberDraggableState { delta -> onDrag(delta) },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        onDragEnd()
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        val toy = LocalToybox.current
        Box(
            modifier =
                Modifier
                    .width(if (toy) 3.dp else 1.dp)
                    .fillMaxHeight()
                    .background(
                        if (toy) {
                            SolidColor(BotsPalette.ToyOutline)
                        } else {
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.2f to BotsPalette.Line,
                                0.8f to BotsPalette.Line,
                                1f to Color.Transparent,
                            )
                        },
                    ),
        )
        if (toy) {
            Box(
                modifier =
                    Modifier
                        .size(width = 9.dp, height = 36.dp)
                        .toySticker(
                            RoundedCornerShape(5.dp),
                            if (dragging) BotsPalette.ToyAccent else BotsPalette.ToyYellow,
                            shadow = null,
                            outline = 2.dp,
                        ),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(width = 4.dp, height = 36.dp)
                        .background(
                            if (dragging) BotsPalette.Fg else BotsPalette.Faint.copy(alpha = 0.8f),
                            RoundedCornerShape(2.dp),
                        ),
            )
        }
    }
}

/** A stable tilt for a bot's Toybox tile, from -3 to 3 degrees, taken from its name. */
private fun toyTilt(name: String): Float = ((name.hashCode() and 0x7fffffff) % 7 - 3).toFloat()

/** A top bar icon button: a plain [IconButton], or in Toybox a 48 dp white sticker. */
@Composable
private fun RailIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalToybox.current) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            modifier =
                modifier
                    .padding(end = 6.dp, bottom = 3.dp)
                    .size(48.dp)
                    .toySticker(shape, BotsPalette.ToyOnAccent, depth = 3.dp)
                    .clip(shape)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier, content = content)
    }
}

@Composable
private fun BotsRail(
    modifier: Modifier,
    state: BotsUiState,
    now: Double,
    selectedName: String?,
    onOpenDrawer: (() -> Unit)?,
    onSearch: (String) -> Unit,
    onOpenBot: (ProfileInfo) -> Unit,
    onEditBot: (ProfileInfo) -> Unit,
    onTogglePin: (ProfileInfo) -> Unit,
    onOpenGroup: (GroupInfo) -> Unit,
    onOpenAllBots: () -> Unit,
    onDisbandGroup: (GroupInfo) -> Unit,
    onCreateBot: () -> Unit,
    onCreateGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
    onHandoffSetting: () -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(RailFilter.ALL) }
    // Picking a picture for a group: which one it is for, then the chosen image stored on this phone.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pictureTarget by remember { mutableStateOf<String?>(null) }
    val picturePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val target = pictureTarget
            if (uri != null && target != null) {
                scope.launch {
                    val encoded = withContext(Dispatchers.IO) { encodeBotImage(context, uri) }
                    if (encoded != null) GroupPictureStore.set(target, encoded)
                }
            }
        }
    val pickPicture: (String) -> Unit = { name ->
        pictureTarget = name
        picturePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    // Bots that need you float to the top.
    val bots = state.displayProfiles.sortedByDescending { it.name in state.needsYou }
    // Pinned bots sit in their own row, so the counts and the all-bots room add them back in.
    val pinnedBots = if (state.searchQuery.isBlank()) state.pinnedProfiles else emptyList()
    val everyBot = bots + pinnedBots
    val working = everyBot.filter { state.isWorking(it, now) }
    // The room with every bot sits at the start of the pinned row, at the top.
    val showAllRoom = filter == RailFilter.ALL && state.searchQuery.isBlank() && everyBot.size > 1
    val showPinned = filter == RailFilter.ALL && (pinnedBots.isNotEmpty() || showAllRoom)
    var agentsCollapsed by rememberSaveable { mutableStateOf(false) }
    val groups = state.displayGroups
    val shownBots =
        when (filter) {
            RailFilter.ALL -> bots
            RailFilter.WORKING -> working
            RailFilter.GROUPS -> emptyList()
        }
    val shownGroups = if (filter == RailFilter.WORKING) emptyList() else groups

    val toy = LocalToybox.current
    Column(
        modifier =
            modifier
                .then(
                    if (toy) {
                        Modifier.toyDots(BotsPalette.Ink, BotsPalette.ToyDot, 22.dp)
                    } else {
                        Modifier
                            .background(BotsPalette.Rail)
                            .drawBehind {
                                drawRect(
                                    Brush.radialGradient(
                                        colors = listOf(BotsPalette.GlowTop, Color.Transparent),
                                        center = Offset(size.width * 0.3f, 0f),
                                        radius = size.width * 0.9f,
                                    ),
                                )
                            }
                    },
                ).statusBarsPadding()
                .testTag("bots_rail"),
    ) {
        RailHeader(
            botCount = state.profiles.size,
            refreshing = state.isRefreshing,
            online = state.errorMessage == null,
            showHidden = state.showHidden,
            hasHidden = state.hasHiddenBots,
            searchOpen = searchOpen,
            onOpenDrawer = onOpenDrawer,
            onToggleSearch = {
                if (searchOpen) onSearch("")
                searchOpen = !searchOpen
            },
            onCreateBot = onCreateBot,
            onCreateGroup = onCreateGroup,
            onToggleHidden = onToggleHidden,
            onRefresh = onRefresh,
            onHandoffSetting = onHandoffSetting,
        )

        val shared by ShareInbox.pending.collectAsStateWithLifecycle()
        AnimatedVisibility(
            visible = shared != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ShareBanner(onCancel = ShareInbox::cancel)
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SearchField(query = state.searchQuery, onQueryChange = onSearch)
        }

        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterPill(stringResource(R.string.bots_filter_all), everyBot.size, filter == RailFilter.ALL) {
                filter = RailFilter.ALL
            }
            FilterPill(
                stringResource(R.string.bots_filter_working),
                working.size,
                filter == RailFilter.WORKING,
                accent = true,
            ) { filter = RailFilter.WORKING }
            if (groups.isNotEmpty()) {
                FilterPill(stringResource(R.string.bots_tab_groups), groups.size, filter == RailFilter.GROUPS) {
                    filter = RailFilter.GROUPS
                }
            }
        }

        when {
            state.isLoading && state.profiles.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BotsPalette.Hues[0], strokeWidth = 2.dp)
                }
            }

            state.errorMessage != null && state.profiles.isEmpty() -> {
                RailMessage(
                    state.errorMessage ?: "",
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRefresh,
                )
            }

            shownBots.isEmpty() && shownGroups.isEmpty() && !showPinned -> {
                RailMessage(
                    text =
                        when {
                            filter == RailFilter.WORKING -> stringResource(R.string.bots_nobody_working)
                            state.searchQuery.isNotBlank() -> stringResource(R.string.bots_no_match, state.searchQuery)
                            else -> stringResource(R.string.bots_empty_description)
                        },
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (showPinned) {
                        item(key = "pinned") {
                            // Tiles in rows of two: the all-bots room first (when shown), then pinned bots.
                            val tiles =
                                buildList<@Composable (Modifier) -> Unit> {
                                    if (showAllRoom) {
                                        add { tileModifier ->
                                            AllBotsTile(
                                                onClick = onOpenAllBots,
                                                onChangePicture = { pickPicture(BotsPresentation.ALL_BOTS_ROOM) },
                                                modifier = tileModifier,
                                            )
                                        }
                                    }
                                    for (profile in pinnedBots) {
                                        add { tileModifier ->
                                            PinnedBot(
                                                modifier = tileModifier,
                                                profile = profile,
                                                now = now,
                                                selected = profile.name == selectedName,
                                                imageUrl = state.imageFor(profile),
                                                needsYou = profile.name in state.needsYou,
                                                unread =
                                                    if (profile.name == selectedName) {
                                                        0
                                                    } else {
                                                        BotsPresentation.unreadCount(
                                                            profile,
                                                            state.seenCounts[profile.name],
                                                        )
                                                    },
                                                working = state.isWorking(profile, now),
                                                onOpenBot = { onOpenBot(profile) },
                                                onTogglePin = { onTogglePin(profile) },
                                                onEditBot = { onEditBot(profile) },
                                            )
                                        }
                                    }
                                }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).animateItem(),
                                verticalArrangement = Arrangement.spacedBy(if (toy) 18.dp else 4.dp),
                            ) {
                                for (pair in tiles.chunked(2)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(if (toy) 18.dp else 4.dp)) {
                                        for (tile in pair) tile(Modifier.weight(1f))
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    val aboveBots = showPinned
                    if (shownBots.isNotEmpty() && (shownGroups.isNotEmpty() || aboveBots)) {
                        item(key = "label_bots") {
                            SectionLabel(
                                text = stringResource(R.string.bots_section_agents),
                                collapsed = agentsCollapsed,
                                onToggle = { agentsCollapsed = !agentsCollapsed },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    items(items = if (agentsCollapsed) emptyList() else shownBots, key = { it.name }) { profile ->
                        BotRow(
                            modifier = Modifier.animateItem(),
                            profile = profile,
                            now = now,
                            selected = profile.name == selectedName,
                            imageUrl = state.imageFor(profile),
                            needsYou = profile.name in state.needsYou,
                            unread =
                                if (profile.name == selectedName) {
                                    0
                                } else {
                                    BotsPresentation.unreadCount(profile, state.seenCounts[profile.name])
                                },
                            preview = state.previewFor(profile),
                            working = state.isWorking(profile, now),
                            task = state.taskFor(profile),
                            lastAt = state.lastMessageTime(profile),
                            pinned = profile.name in state.pinned,
                            onClick = { onOpenBot(profile) },
                            onTogglePin = { onTogglePin(profile) },
                            onEdit = { onEditBot(profile) },
                        )
                    }
                    if (shownGroups.isNotEmpty()) {
                        item(key = "label_groups") { SectionLabel(stringResource(R.string.bots_tab_groups)) }
                        items(items = shownGroups, key = { "group_${it.name}" }) { group ->
                            GroupRow(
                                modifier = Modifier.animateItem(),
                                group = group,
                                onClick = { onOpenGroup(group) },
                                onChangePicture = { pickPicture(group.name) },
                                onDisband = { onDisbandGroup(group) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailHeader(
    botCount: Int,
    refreshing: Boolean,
    online: Boolean,
    showHidden: Boolean,
    hasHidden: Boolean,
    searchOpen: Boolean,
    onOpenDrawer: (() -> Unit)?,
    onToggleSearch: () -> Unit,
    onCreateBot: () -> Unit,
    onCreateGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
    onHandoffSetting: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenDrawer != null) {
            RailIconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("menu_button")) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.content_desc_open_drawer),
                    tint = BotsPalette.Fg,
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 2.dp)) {
            val toy = LocalToybox.current
            Text(
                text = stringResource(if (toy) R.string.bots_toy_title else R.string.screen_bots),
                color = BotsPalette.Fg,
                fontSize = if (toy) 30.sp else 24.sp,
                fontFamily = if (toy) ToyFonts.Display else null,
                fontWeight = if (toy) FontWeight.ExtraBold else FontWeight.Bold,
                letterSpacing = if (toy) 0.sp else (-0.3).sp,
                maxLines = 1,
                softWrap = false,
            )
            // Connection dot and bot count; the gateway's name lives in the menu.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !online -> BotsPalette.Offline
                                refreshing -> BotsPalette.You
                                else -> BotsPalette.Ok
                            },
                        ).testTag("bots_gateway_dot"),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = pluralStringResource(R.plurals.bots_count, botCount, botCount),
                    color = BotsPalette.Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        RailIconButton(onClick = onToggleSearch, modifier = Modifier.testTag("bots_action_search")) {
            Icon(
                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = stringResource(R.string.bots_search_placeholder),
                tint = BotsPalette.Fg,
            )
        }
        Box {
            RailIconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("bots_action_menu")) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.bots_more),
                    tint = BotsPalette.Fg,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (online) R.string.bots_gateway_online else R.string.bots_gateway_offline,
                                BotsPresentation.GATEWAY_LABEL,
                            ),
                            fontFamily = Mono,
                            fontSize = 12.sp,
                        )
                    },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (online) BotsPalette.Ok else BotsPalette.Offline),
                        )
                    },
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.testTag("bots_gateway"),
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.bots_action_create)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onCreateBot()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.bots_action_create_group)) },
                    leadingIcon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onCreateGroup()
                    },
                )
                if (hasHidden) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (showHidden) R.string.bots_hide_hidden else R.string.bots_show_hidden,
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleHidden()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.content_desc_refresh)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.handoff_view_setting)) },
                    leadingIcon = { Icon(Icons.Filled.VerticalSplit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onHandoffSetting()
                    },
                    modifier = Modifier.testTag("bots_handoff_setting"),
                )
                // Day, night, or whatever the phone is set to; the same setting as Settings > Appearance.
                val theme by AuthManager.themePreferenceFlow.collectAsState()
                HorizontalDivider(color = BotsPalette.Line)
                for (choice in THEME_CHOICES) {
                    val selected =
                        when (choice.pref) {
                            ThemePreference.DARK -> {
                                theme == ThemePreference.DARK &&
                                    BotsPalette.darkStyle == choice.style
                            }

                            ThemePreference.LIGHT -> {
                                theme == ThemePreference.LIGHT &&
                                    BotsPalette.lightStyle == choice.lightStyle
                            }

                            else -> {
                                theme == choice.pref
                            }
                        }
                    DropdownMenuItem(
                        text = { Text(stringResource(choice.label)) },
                        leadingIcon = { Icon(choice.icon, contentDescription = null) },
                        trailingIcon = {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            AuthManager.setThemePreference(choice.pref)
                            choice.style?.let { BotsThemeStore.setDarkStyle(it) }
                            choice.lightStyle?.let { BotsThemeStore.setLightStyle(it) }
                        },
                        modifier = Modifier.testTag(choice.tag),
                    )
                }
                HorizontalDivider(color = BotsPalette.Line)
                for ((scale, label) in BotsSizeStore.SIZES) {
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { Icon(Icons.Filled.FormatSize, contentDescription = null) },
                        trailingIcon = {
                            if (BotsSizeStore.scale == scale) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            BotsSizeStore.set(scale)
                        },
                        modifier = Modifier.testTag("bots_size_${sizeTag(scale)}"),
                    )
                }
                HorizontalDivider(color = BotsPalette.Line)
                // What the chat recorded, for tracing a problem seen on the phone.
                val context = LocalContext.current
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_report_send)) },
                    leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        ChatTrace.share(context)
                    },
                )
            }
        }
    }
}

private fun sizeTag(scale: Float): String =
    when (scale) {
        0.9f -> "small"
        1.0f -> "default"
        1.12f -> "large"
        else -> "xlarge"
    }

/** One entry of the theme menu: the stored preference, the dark style it selects (if any), and its look. */
private data class ThemeChoice(
    val pref: ThemePreference,
    val style: DarkStyle?,
    val label: Int,
    val icon: ImageVector,
    val tag: String,
    val lightStyle: LightStyle? = null,
)

private val THEME_CHOICES =
    listOf(
        ThemeChoice(
            ThemePreference.LIGHT,
            null,
            R.string.bots_theme_day,
            Icons.Filled.LightMode,
            "bots_theme_light",
            LightStyle.PLAIN,
        ),
        ThemeChoice(
            ThemePreference.LIGHT,
            null,
            R.string.bots_theme_toybox,
            Icons.Filled.Toys,
            "bots_theme_toybox",
            LightStyle.TOYBOX,
        ),
        ThemeChoice(
            ThemePreference.DARK,
            DarkStyle.NAVY,
            R.string.bots_theme_night,
            Icons.Filled.DarkMode,
            "bots_theme_dark",
        ),
        ThemeChoice(
            ThemePreference.DARK,
            DarkStyle.CHARCOAL,
            R.string.bots_theme_charcoal,
            Icons.Filled.Contrast,
            "bots_theme_charcoal",
        ),
        ThemeChoice(
            ThemePreference.SYSTEM,
            null,
            R.string.bots_theme_auto,
            Icons.Filled.BrightnessAuto,
            "bots_theme_system",
        ),
    )

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BotsPalette.Deck)
                .border(1.dp, BotsPalette.Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = BotsPalette.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.bots_search_placeholder), color = BotsPalette.Muted, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = BotsPalette.Fg, fontSize = 14.sp),
                cursorBrush = SolidColor(BotsPalette.Hues[0]),
                modifier = Modifier.fillMaxWidth().testTag("bots_search_field"),
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val toy = LocalToybox.current
    Row(
        modifier =
            if (toy) {
                // Selected: dark fill, no shadow; the others are white stickers with a shadow.
                Modifier
                    .padding(end = 3.dp, bottom = 3.dp)
                    .toySticker(
                        shape,
                        if (selected) BotsPalette.ToyOutline else BotsPalette.ToyOnAccent,
                        shadow = if (selected) null else BotsPalette.ToyOutline,
                        depth = 3.dp,
                    ).clip(shape)
                    .combinedClickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            } else {
                Modifier
                    .clip(shape)
                    .background(if (selected) BotsPalette.Deck2 else Color.Transparent)
                    .border(1.dp, if (selected) BotsPalette.Deck3 else BotsPalette.Line, shape)
                    .combinedClickable(onClick = onClick)
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color =
                when {
                    toy -> if (selected) BotsPalette.ToyOnAccent else BotsPalette.ToyOutline
                    selected -> BotsPalette.Fg
                    else -> BotsPalette.Muted
                },
            fontSize = 12.sp,
            fontWeight = if (toy) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            count.toString(),
            color =
                when {
                    toy -> if (selected) BotsPalette.ToyOnAccent else BotsPalette.ToyOutline
                    accent && count > 0 -> BotsPalette.Ok
                    else -> BotsPalette.Muted
                },
            fontSize = 11.sp,
            fontWeight = if (toy) FontWeight.ExtraBold else FontWeight.Bold,
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    collapsed: Boolean? = null,
    onToggle: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (collapsed != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(start = 12.dp, top = 14.dp, bottom = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = BotsPalette.Muted,
            fontSize = 15.sp,
            fontWeight = if (LocalToybox.current) FontWeight.ExtraBold else FontWeight.Medium,
            letterSpacing = 0.sp,
        )
        if (collapsed != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = BotsPalette.Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RailMessage(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = BotsPalette.Muted, fontSize = 13.sp)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = BotsPalette.Fg) }
        }
    }
}

/** Long-press menu of a bot, on its row and on its pinned tile. */
@Composable
private fun BotMenu(
    expanded: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (pinned) R.string.bots_unpin else R.string.bots_pin)) },
            leadingIcon = {
                Icon(if (pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onTogglePin()
            },
            modifier = Modifier.testTag("bot_menu_pin"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.bots_edit)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                onDismiss()
                onEdit()
            },
            modifier = Modifier.testTag("bot_menu_edit"),
        )
    }
}

/** Size of a pinned bot's Toybox tile. */
private val TOY_TILE_SIZE = 96.dp

/** The room with every bot, as the first tile of the pinned row. */
@Composable
private fun AllBotsTile(
    onClick: () -> Unit,
    onChangePicture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                    .padding(vertical = 8.dp)
                    .testTag("pinned_all_bots"),
        ) {
            val toy = LocalToybox.current
            BotOrb(
                initials = "",
                hue = BotsPalette.Muted,
                size = if (toy) TOY_TILE_SIZE else 68.dp,
                team = true,
                imageUrl = GroupPictureStore.get(BotsPresentation.ALL_BOTS_ROOM),
                tilt = toyTilt(BotsPresentation.ALL_BOTS_ROOM),
            )
            Text(
                text = stringResource(R.string.bots_all_room),
                color = if (toy) BotsPalette.ToyOutline else BotsPalette.Muted,
                fontSize = if (toy) 18.sp else 15.sp,
                fontFamily = if (toy) ToyFonts.Display else null,
                fontWeight = if (toy) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        GroupPictureMenu(
            expanded = menuOpen,
            name = BotsPresentation.ALL_BOTS_ROOM,
            onDismiss = { menuOpen = false },
            onChangePicture = onChangePicture,
        )
    }
}

/** Long-press menu for a room's picture (and, for a group, disbanding it). */
@Composable
private fun GroupPictureMenu(
    expanded: Boolean,
    name: String,
    onDismiss: () -> Unit,
    onChangePicture: () -> Unit,
    onDisband: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.bots_group_picture_change)) },
            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
            onClick = {
                onDismiss()
                onChangePicture()
            },
            modifier = Modifier.testTag("group_menu_picture"),
        )
        if (GroupPictureStore.get(name) != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bots_group_picture_remove)) },
                leadingIcon = { Icon(Icons.Filled.HideImage, contentDescription = null) },
                onClick = {
                    onDismiss()
                    GroupPictureStore.remove(name)
                },
                modifier = Modifier.testTag("group_menu_picture_remove"),
            )
        }
        if (onDisband != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bots_group_disband)) },
                leadingIcon = { Icon(Icons.Filled.GroupRemove, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onDisband()
                },
                modifier = Modifier.testTag("group_menu_disband"),
            )
        }
    }
}

/** A pinned bot: a big orb with its name underneath, in the row at the top of the list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedBot(
    modifier: Modifier,
    profile: ProfileInfo,
    now: Double,
    selected: Boolean,
    imageUrl: String?,
    needsYou: Boolean,
    unread: Int,
    working: Boolean,
    onOpenBot: () -> Unit,
    onTogglePin: () -> Unit,
    onEditBot: () -> Unit,
) {
    val hue = hueFor(profile)
    val title = profile.effectiveTitle
    val toy = LocalToybox.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpenBot, onLongClick = { menuOpen = true })
                    .padding(vertical = 8.dp)
                    .testTag("pinned_bot_${profile.name}"),
        ) {
            Box {
                BotOrb(
                    initials = BotsPresentation.initials(title),
                    hue = hue,
                    size = if (toy) TOY_TILE_SIZE else 68.dp,
                    working = working,
                    presence = if (BotsPresentation.isRecent(profile, now)) OrbPresence.RECENT else OrbPresence.IDLE,
                    shapeKey = profile.botMeta()?.avatar?.shape,
                    imageUrl = imageUrl,
                    attention = needsYou,
                    selected = selected,
                    tilt = toyTilt(profile.name),
                )
                if (unread > 0 && toy) {
                    UnreadBadge(count = unread, hue = hue, modifier = Modifier.align(Alignment.TopStart))
                } else if (unread > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(hue),
                    )
                }
            }
            Text(
                text = title,
                color =
                    when {
                        toy -> if (selected) BotsPalette.ToyOnAccent else BotsPalette.ToyOutline
                        selected -> BotsPalette.Fg
                        else -> BotsPalette.Muted
                    },
                fontSize = if (toy) 18.sp else 15.sp,
                fontFamily = if (toy) ToyFonts.Display else null,
                fontWeight = if (toy) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(top = if (toy) 10.dp else 6.dp)
                        .then(
                            if (toy && selected) {
                                Modifier
                                    .toySticker(
                                        RoundedCornerShape(999.dp),
                                        BotsPalette.ToyAccent,
                                        shadow = null,
                                        outline = 2.dp,
                                    ).padding(horizontal = 12.dp, vertical = 1.dp)
                            } else {
                                Modifier
                            },
                        ),
            )
        }
        BotMenu(
            expanded = menuOpen,
            pinned = true,
            onDismiss = { menuOpen = false },
            onTogglePin = onTogglePin,
            onEdit = onEditBot,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BotRow(
    profile: ProfileInfo,
    now: Double,
    selected: Boolean,
    imageUrl: String?,
    needsYou: Boolean,
    unread: Int,
    preview: String,
    working: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    task: String? = null,
    lastAt: Double? = null,
) {
    val hue = hueFor(profile)
    val recent = BotsPresentation.isRecent(profile, now)
    val title = profile.effectiveTitle
    val handle = BotsPresentation.distinctHandle(profile.name, title)
    val time = BotsPresentation.relativeTime(lastAt, now)
    val shape = RoundedCornerShape(14.dp)
    val accent = if (needsYou) BotsPalette.Attention else hue
    val toy = LocalToybox.current
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // No clip: a wide picture may spill past the row.
                    .then(
                        if (toy) {
                            // The selected row is a white sticker card.
                            Modifier
                                .padding(end = 3.dp, bottom = 3.dp)
                                .then(
                                    if (selected) {
                                        Modifier.toySticker(
                                            RoundedCornerShape(20.dp),
                                            BotsPalette.ToyOnAccent,
                                            depth = 3.dp,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                        } else {
                            Modifier
                                .background(
                                    when {
                                        selected -> {
                                            Brush.horizontalGradient(listOf(hue.copy(alpha = 0.2f), Color.Transparent))
                                        }

                                        needsYou -> {
                                            Brush.horizontalGradient(
                                                listOf(accent.copy(alpha = 0.1f), Color.Transparent),
                                            )
                                        }

                                        else -> {
                                            SolidColor(Color.Transparent)
                                        }
                                    },
                                    shape,
                                ).drawBehind {
                                    if (selected || needsYou) {
                                        drawRoundRect(
                                            color = accent,
                                            topLeft = Offset(0f, 14.dp.toPx()),
                                            size = Size(3.dp.toPx(), size.height - 28.dp.toPx()),
                                            cornerRadius = CornerRadius(3.dp.toPx()),
                                        )
                                    }
                                }
                        },
                    ).combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                    .testTag("bot_row_${profile.name}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotOrb(
                initials = BotsPresentation.initials(title),
                hue = hue,
                size = 52.dp,
                working = working,
                presence = if (recent) OrbPresence.RECENT else OrbPresence.IDLE,
                shapeKey = profile.botMeta()?.avatar?.shape,
                imageUrl = imageUrl,
                attention = needsYou,
                selected = selected,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: the name, its @handle as a small chip when that says something new; the time sits at the end.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = BotsPalette.Fg,
                            fontSize = if (toy) 19.sp else 17.sp,
                            fontFamily = if (toy) ToyFonts.Display else null,
                            fontWeight = if (toy || unread > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (handle != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = handle,
                                color = BotsPalette.Muted,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                modifier =
                                    Modifier
                                        .background(BotsPalette.Deck2, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    if (time.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = time,
                            color = if (working || unread > 0) hue else BotsPalette.Faint,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
                // Below, GrokBot style: a status line when it needs you or is working, then one
                // line of the latest message of the conversation.
                val status =
                    when {
                        needsYou -> {
                            stringResource(R.string.bots_needs_you)
                        }

                        working -> {
                            task?.let { stringResource(R.string.bots_working_on, it) }
                                ?: stringResource(R.string.bots_working)
                        }

                        else -> {
                            null
                        }
                    }
                if (status != null) {
                    Text(
                        text = status,
                        color = if (needsYou) BotsPalette.Attention else lerp(hue, BotsPalette.Fg, 0.3f),
                        fontSize = 14.sp,
                        fontWeight = if (needsYou) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    val youPrefix = stringResource(R.string.bots_preview_you)
                    val line =
                        when {
                            preview.isBlank() -> {
                                AnnotatedString(stringResource(R.string.bots_preview_empty))
                            }

                            preview.startsWith(youPrefix) -> {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = BotsPalette.Fg)) { append(youPrefix) }
                                    append(preview.removePrefix(youPrefix))
                                }
                            }

                            else -> {
                                AnnotatedString(preview)
                            }
                        }
                    Text(
                        text = line,
                        color = if (preview.isBlank()) BotsPalette.Faint else BotsPalette.Muted,
                        fontSize = 14.5.sp,
                        lineHeight = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread > 0) {
                        Spacer(Modifier.width(6.dp))
                        UnreadBadge(count = unread, hue = hue)
                    }
                }
            }
        }
        BotMenu(
            expanded = menuOpen,
            pinned = pinned,
            onDismiss = { menuOpen = false },
            onTogglePin = onTogglePin,
            onEdit = onEdit,
        )
    }
}

@Composable
private fun UnreadBadge(
    count: Int,
    hue: Color,
    modifier: Modifier = Modifier,
) {
    val label = BotsPresentation.unreadLabel(count)
    val description = stringResource(R.string.bots_unread, label)
    val toy = LocalToybox.current
    Box(
        modifier =
            modifier
                .then(
                    if (toy) {
                        Modifier.toySticker(
                            RoundedCornerShape(999.dp),
                            BotsPalette.ToyPink,
                            shadow = null,
                            outline = 2.dp,
                        )
                    } else {
                        Modifier.clip(RoundedCornerShape(999.dp)).background(hue)
                    },
                ).padding(horizontal = 6.dp, vertical = 1.dp)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (toy) BotsPalette.ToyOnAccent else BotsPalette.OnHue,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: GroupInfo,
    onClick: () -> Unit,
    onChangePicture: () -> Unit,
    onDisband: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                .testTag("group_row_${group.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotOrb(
            initials = "",
            hue = BotsPalette.Muted,
            size = 52.dp,
            team = true,
            imageUrl = GroupPictureStore.get(group.name),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.name,
                color = BotsPalette.Fg,
                fontSize = 17.sp,
                fontFamily = if (LocalToybox.current) ToyFonts.Display else null,
                fontWeight = if (LocalToybox.current) FontWeight.ExtraBold else FontWeight.SemiBold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: group.members.joinToString(" · ") { it.effectiveTitle },
                color = BotsPalette.Muted,
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    GroupPictureMenu(
        expanded = menuOpen,
        name = group.name,
        onDismiss = { menuOpen = false },
        onChangePicture = onChangePicture,
        onDisband = onDisband,
    )
}

@Composable
private fun BotsChatPane(
    modifier: Modifier,
    profile: ProfileInfo?,
    imageUrl: String?,
    needsYou: Boolean,
    sessionId: String?,
    now: Double,
    working: Boolean,
    task: String? = null,
    lastAt: Double? = null,
    baseScheme: androidx.compose.material3.ColorScheme,
    onBack: (() -> Unit)? = null,
    listToggle: NavIcon.Action? = null,
    onChatMessages: ((String?, List<com.m57.hermescontrol.ui.chat.ChatMessage>) -> Unit)? = null,
    onSessionChanged: (bot: String, sessionId: String) -> Unit = { _, _ -> },
    handoff: HandoffView? = null,
    handoffBot: (String) -> HandoffBot = { error("no hand-off") },
    onHandoffStrip: () -> Unit = {},
) {
    val targetHue = profile?.let { hueFor(it) } ?: BotsPalette.Muted
    val hue by animateColorAsState(targetHue, animationSpec = tween(600), label = "pane-hue")
    val toy = LocalToybox.current
    Box(
        modifier =
            modifier
                .then(
                    if (toy) {
                        Modifier.toyDots(BotsPalette.ToyChatBg, BotsPalette.ToyChatDot, 20.dp)
                    } else {
                        Modifier
                            .background(Brush.verticalGradient(listOf(BotsPalette.PaneTop, BotsPalette.PaneBottom)))
                            .drawBehind {
                                drawRect(
                                    Brush.radialGradient(
                                        colors = listOf(hue.copy(alpha = 0.22f), Color.Transparent),
                                        center = Offset(size.width / 2f, 0f),
                                        radius = size.width * 0.75f,
                                    ),
                                )
                                drawRect(
                                    Brush.radialGradient(
                                        colors = listOf(BotsPalette.GlowBottom.copy(alpha = 0.6f), Color.Transparent),
                                        center = Offset(size.width * 0.9f, size.height),
                                        radius = size.width * 0.6f,
                                    ),
                                )
                            }
                    },
                ).testTag("bots_chat_pane"),
    ) {
        if (profile == null) {
            Text(
                text = stringResource(R.string.bots_pick_one),
                color = BotsPalette.Muted,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            MaterialTheme(colorScheme = BotsPalette.chatScheme(baseScheme, targetHue)) {
                // The rail owns drawer gestures for this screen; the embedded chat must not
                // reconcile its own preference over it (issue #619).
                val chatViewModel: ChatViewModel = viewModel()
                // Once the chat shows the session asked for, a later switch is yours (new chat, history).
                // Before that, ids belong to the previous bot's chat still being swapped out.
                val latestSessionChanged by rememberUpdatedState(onSessionChanged)
                val requested by rememberUpdatedState(sessionId)
                LaunchedEffect(chatViewModel, profile.name) {
                    var settled = false
                    var lastId: String? = null
                    var lastSent = 0
                    chatViewModel.uiState
                        .map { ui -> ui.currentSessionId to ui.messages.count { it.role == MessageRole.USER } }
                        .distinctUntilChanged()
                        .collect { (current, sent) ->
                            when {
                                current == null -> Unit

                                !settled && (current == requested || requested == null) -> settled = true

                                !settled -> Unit

                                // Another chat, or you wrote in this one: it is the chat to reopen.
                                current != lastId || sent > lastSent -> latestSessionChanged(profile.name, current)
                            }
                            if (current != null && settled) {
                                if (current != lastId) lastSent = sent
                                lastId = current
                                lastSent = maxOf(lastSent, sent)
                            }
                        }
                }
                if (onChatMessages != null) {
                    val latest by rememberUpdatedState(onChatMessages)
                    LaunchedEffect(chatViewModel) {
                        chatViewModel.uiState
                            .map { it.currentSessionId to it.messages }
                            .distinctUntilChanged()
                            .collect { (sessionId, messages) -> latest(sessionId, messages) }
                    }
                }
                CompositionLocalProvider(LocalDrawerGestureController provides null) {
                    ChatScreen(
                        viewModel = chatViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onOpenDrawer = null,
                        sessionId = sessionId,
                        onBack = onBack,
                        navigationAction = listToggle,
                        voiceTitle = profile.effectiveTitle,
                        voiceImageUrl = imageUrl,
                        titleOverride = {
                            PaneTitle(
                                profile = profile,
                                hue = hue,
                                now = now,
                                imageUrl = imageUrl,
                                needsYou = needsYou,
                                working = working,
                                task = task,
                                lastAt = lastAt,
                            )
                        },
                    )
                }
                // The other bot's progress, under the chat's top bar; a tap opens it in full.
                if (handoff != null) {
                    HandoffStrip(
                        view = handoff,
                        botFor = handoffBot,
                        onClick = onHandoffStrip,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(start = 12.dp, end = 12.dp, top = 68.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaneTitle(
    profile: ProfileInfo,
    hue: Color,
    now: Double,
    imageUrl: String?,
    needsYou: Boolean,
    working: Boolean,
    task: String? = null,
    lastAt: Double? = null,
) {
    val recent = BotsPresentation.isRecent(profile, now)
    val toy = LocalToybox.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("bots_pane_title")) {
        BotOrb(
            initials = BotsPresentation.initials(profile.effectiveTitle),
            hue = hue,
            size = if (toy) 56.dp else 36.dp,
            working = working,
            shapeKey = profile.botMeta()?.avatar?.shape,
            imageUrl = imageUrl,
            attention = needsYou,
            tilt = -4f,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = profile.effectiveTitle,
                color = BotsPalette.Fg,
                fontSize = if (toy) 26.sp else 19.sp,
                fontFamily = if (toy) ToyFonts.Display else null,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (toy) 0.sp else (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusPill(
                profile = profile,
                hue = hue,
                working = working,
                recent = recent,
                now = now,
                needsYou = needsYou,
                task = task,
                lastAt = lastAt,
            )
        }
    }
}

@Composable
private fun StatusPill(
    profile: ProfileInfo,
    hue: Color,
    working: Boolean,
    task: String? = null,
    lastAt: Double? = null,
    recent: Boolean,
    now: Double,
    needsYou: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    val glow: State<Float>? =
        if (working && DecorativeMotion.enabled) {
            rememberInfiniteTransition(label = "pill-glow").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
                label = "pill-glow-value",
            )
        } else {
            null
        }
    val time = BotsPresentation.relativeTime(lastAt, now)
    val text =
        when {
            needsYou -> {
                stringResource(R.string.bots_waiting_for_you)
            }

            working -> {
                task?.let { stringResource(R.string.bots_working_on, it) }
                    ?: stringResource(R.string.bots_working)
            }

            time.isNotEmpty() -> {
                stringResource(R.string.bots_last_active, time)
            }

            else -> {
                "@${profile.name}"
            }
        }
    val color =
        when {
            needsYou -> BotsPalette.Attention
            working -> lerp(hue, BotsPalette.Fg, 0.3f)
            else -> BotsPalette.Muted
        }
    val toy = LocalToybox.current
    Row(
        modifier =
            if (toy) {
                Modifier
                    .padding(top = 2.dp, end = 3.dp, bottom = 3.dp)
                    .toySticker(shape, BotsPalette.ToyOnAccent, depth = 3.dp, outline = 2.dp)
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            } else {
                Modifier
                    .padding(top = 2.dp)
                    .clip(shape)
                    .background(BotsPalette.Deck.copy(alpha = 0.8f))
                    .border(
                        1.dp,
                        when {
                            needsYou -> BotsPalette.Attention.copy(alpha = 0.6f)
                            working -> hue.copy(alpha = 0.45f)
                            else -> BotsPalette.Line
                        },
                        shape,
                    ).padding(horizontal = 7.dp, vertical = 1.dp)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .drawBehind {
                        val r = 3.5.dp.toPx()
                        if (working) {
                            val g = glow?.value ?: 0.5f
                            drawCircle(hue.copy(alpha = (1f - g) * 0.5f), radius = r + g * 3.dp.toPx())
                        }
                        drawCircle(
                            when {
                                working -> hue
                                recent -> BotsPalette.Ok
                                else -> BotsPalette.Idle
                            },
                            radius = r,
                        )
                    },
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            color = if (toy) BotsPalette.ToyOutline else color,
            fontSize = if (toy) 12.sp else 12.5.sp,
            fontWeight = if (toy) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BotsDialogs(
    viewModel: BotsViewModel,
    state: BotsUiState,
    editingBot: ProfileInfo?,
    onDismissEdit: () -> Unit,
    disbandingGroup: GroupInfo?,
    onDismissDisband: () -> Unit,
    showCreateDialog: Boolean,
    onDismissCreate: () -> Unit,
    showCreateGroupDialog: Boolean,
    onDismissCreateGroup: () -> Unit,
) {
    disbandingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = onDismissDisband,
            title = {
                Text(
                    text = stringResource(R.string.bots_group_disband_confirm_title, group.name),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(stringResource(R.string.bots_group_disband_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.disbandGroupChat(group.name) { onDismissDisband() } },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(stringResource(R.string.bots_group_disband))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDisband) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    editingBot?.let { bot ->
        EditBotBottomSheet(
            bot = bot,
            onDismiss = onDismissEdit,
            onSave = { title, description, shape, color, imageUrl ->
                viewModel.updateBotMeta(bot.name, title, description, shape, color, imageUrl) { onDismissEdit() }
            },
            onDelete = { viewModel.deleteBot(bot.name) { onDismissEdit() } },
            currentImage = state.imageFor(bot),
            onGenerate = viewModel::generateAvatar,
        )
    }

    if (showCreateDialog) {
        CreateBotDialog(
            onDismiss = onDismissCreate,
            onCreate = { name, title, description, shape, color, imageUrl ->
                viewModel.createBot(name, title, description, shape, color, imageUrl) { onDismissCreate() }
            },
            onGenerate = viewModel::generateAvatar,
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupChatDialog(
            availableBots = state.profiles,
            onDismiss = onDismissCreateGroup,
            onCreateGroup = { groupName, botNames ->
                viewModel.createGroupChat(groupName, botNames) { onDismissCreateGroup() }
            },
        )
    }
}

/** Layout choices on the Bots home that should outlast the app being closed. */
private object BotsLayoutPrefs {
    private const val PREFS = "bots_layout"
    private const val KEY_LIST_HIDDEN = "list_hidden"
    private const val KEY_RAIL_FRACTION = "rail_fraction"
    private const val KEY_HANDOFF_FRACTION = "handoff_fraction"

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun railFraction(context: android.content.Context): Float? = fraction(context, KEY_RAIL_FRACTION)

    fun setRailFraction(
        context: android.content.Context,
        value: Float,
    ) = prefs(context).edit().putFloat(KEY_RAIL_FRACTION, value).apply()

    fun handoffFraction(context: android.content.Context): Float? = fraction(context, KEY_HANDOFF_FRACTION)

    fun setHandoffFraction(
        context: android.content.Context,
        value: Float,
    ) = prefs(context).edit().putFloat(KEY_HANDOFF_FRACTION, value).apply()

    private fun fraction(
        context: android.content.Context,
        key: String,
    ): Float? = prefs(context).takeIf { it.contains(key) }?.getFloat(key, 0f)

    fun listHidden(context: android.content.Context): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_LIST_HIDDEN, false)

    fun setListHidden(
        context: android.content.Context,
        hidden: Boolean,
    ) {
        context
            .getSharedPreferences(
                PREFS,
                android.content.Context.MODE_PRIVATE,
            ).edit()
            .putBoolean(KEY_LIST_HIDDEN, hidden)
            .apply()
    }
}
