package com.m57.hermescontrol.ui.bots

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.ui.chat.ChatScreen
import com.m57.hermescontrol.ui.chat.ChatViewModel
import com.m57.hermescontrol.ui.chat.VoiceLiveCalls
import com.m57.hermescontrol.ui.common.DisableDrawerGestures
import com.m57.hermescontrol.ui.common.LocalDrawerGestureController
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.ToastEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    remember { BotSeenStore.init(context) }

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
    MaterialTheme(colorScheme = BotsPalette.railScheme(baseScheme)) {
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
                    // The chat you last had open with this bot (a new one you started, say), else its Bot Chat.
                    openSessionId = BotsLayoutPrefs.lastSession(context, profile.name) ?: profile.canonicalSessionId()
                    viewModel.markSeen(profile)
                    if (!twoPane) phoneChatOpen = true
                    // Something shared is waiting: this bot's chat takes it.
                    ShareInbox.arm()
                }
            }
            val rail: @Composable (Modifier, String?) -> Unit = { railModifier, selectedName ->
                BotsRail(
                    modifier = railModifier,
                    state = state,
                    now = now,
                    selectedName = selectedName,
                    onOpenDrawer = onOpenDrawer,
                    onSearch = viewModel::setSearchQuery,
                    onOpenBot = onOpenBot,
                    onEditBot = { editingBot = it },
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
                        modifier = paneModifier,
                    )
                }
            }
            val botNames = remember(state.profiles) { state.profiles.map { it.name }.toSet() }
            // You moved to another chat with the open bot (new chat, or one from its history): keep it.
            val onSessionChanged: (String, String) -> Unit = { bot, sessionId ->
                if (bot == openBotName || (openBotName == null && bot == selectedName)) {
                    openBotName = bot
                    openSessionId = sessionId
                    BotsLayoutPrefs.setLastSession(context, bot, sessionId)
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
                val railWidth = (maxWidth * 0.42f).coerceIn(300.dp, 460.dp)
                // The bot list can be folded away so the chat takes the whole screen; the choice
                // is kept between launches. With no bot open the list always shows.
                var listHidden by remember { mutableStateOf(BotsLayoutPrefs.listHidden(context)) }
                // Side by side: the list makes way while the other bot's pane is open.
                val split = handoffMode == HandoffMode.SPLIT && handoff?.expanded == true
                val showList = (!listHidden && !split) || selected == null
                val handoffWidth = (maxWidth * 0.46f).coerceIn(320.dp, 560.dp)
                Row(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = showList,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                    ) {
                        Row {
                            rail(Modifier.width(railWidth).fillMaxHeight(), selectedName)
                            Hinge()
                        }
                    }
                    // The open conversation counts as read, including replies arriving while it's open.
                    LaunchedEffect(selected?.name, selected?.let { BotsPresentation.messageCount(it) }) {
                        selected?.let(viewModel::markSeen)
                    }
                    BotsChatPane(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        profile = selected,
                        imageUrl = selected?.let { state.imageFor(it) },
                        needsYou = selected?.name in state.needsYou,
                        sessionId = chatSessionId,
                        now = now,
                        working = selected?.let { state.isWorking(it, now) } == true,
                        baseScheme = baseScheme,
                        listToggle =
                            NavIcon.Action(
                                icon = if (showList) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                                description =
                                    stringResource(if (showList) R.string.bots_list_hide else R.string.bots_list_show),
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
                    AnimatedVisibility(
                        visible = split && handoff != null,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                    ) {
                        Row {
                            Hinge()
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
                BotsChatPane(
                    modifier = Modifier.fillMaxSize(),
                    profile = selected,
                    imageUrl = state.imageFor(selected),
                    needsYou = selected.name in state.needsYou,
                    sessionId = chatSessionId,
                    now = now,
                    working = state.isWorking(selected, now),
                    baseScheme = baseScheme,
                    onBack = closeChat,
                    onChatMessages = onHandoffMessages,
                    onSessionChanged = onSessionChanged,
                    handoff = handoff,
                    handoffBot = handoffBot,
                    onHandoffStrip = { handoffSheetOpen = true },
                )
            } else {
                rail(Modifier.fillMaxSize(), null)
            }
        }
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

@Composable
private fun Hinge() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.2f to BotsPalette.Line,
                        0.8f to BotsPalette.Line,
                        1f to Color.Transparent,
                    ),
                ),
    )
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
    // Bots that need you float to the top.
    val bots = state.displayProfiles.sortedByDescending { it.name in state.needsYou }
    val working = bots.filter { state.isWorking(it, now) }
    val groups = state.displayGroups
    val shownBots =
        when (filter) {
            RailFilter.ALL -> bots
            RailFilter.WORKING -> working
            RailFilter.GROUPS -> emptyList()
        }
    val shownGroups = if (filter == RailFilter.WORKING) emptyList() else groups

    Column(
        modifier =
            modifier
                .background(BotsPalette.Rail)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(BotsPalette.GlowTop, Color.Transparent),
                            center = Offset(size.width * 0.3f, 0f),
                            radius = size.width * 0.9f,
                        ),
                    )
                }.statusBarsPadding()
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
            FilterPill(stringResource(R.string.bots_filter_all), bots.size, filter == RailFilter.ALL) {
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

            shownBots.isEmpty() && shownGroups.isEmpty() -> {
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
                    if (filter == RailFilter.ALL && state.searchQuery.isBlank() && bots.size > 1) {
                        item(key = "all_bots_room") {
                            GroupRow(
                                modifier = Modifier.animateItem(),
                                group = GroupInfo(name = stringResource(R.string.bots_all_room), members = bots),
                                subtitle = stringResource(R.string.bots_all_room_hint),
                                onClick = onOpenAllBots,
                                onLongClick = onOpenAllBots,
                            )
                        }
                    }
                    if (shownBots.isNotEmpty() && shownGroups.isNotEmpty()) {
                        item(key = "label_bots") { SectionLabel(stringResource(R.string.bots_tab_all)) }
                    }
                    items(items = shownBots, key = { it.name }) { profile ->
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
                            onClick = { onOpenBot(profile) },
                            onLongClick = { onEditBot(profile) },
                        )
                    }
                    if (shownGroups.isNotEmpty()) {
                        item(key = "label_groups") { SectionLabel(stringResource(R.string.bots_tab_groups)) }
                        items(items = shownGroups, key = { "group_${it.name}" }) { group ->
                            GroupRow(
                                modifier = Modifier.animateItem(),
                                group = group,
                                onClick = { onOpenGroup(group) },
                                onLongClick = { onDisbandGroup(group) },
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
            IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("menu_button")) {
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
            Text(
                text = stringResource(R.string.screen_bots),
                color = BotsPalette.Fg,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
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
                    fontFamily = Mono,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        IconButton(onClick = onToggleSearch, modifier = Modifier.testTag("bots_action_search")) {
            Icon(
                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = stringResource(R.string.bots_search_placeholder),
                tint = BotsPalette.Fg,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("bots_action_menu")) {
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
                for ((pref, label, icon) in THEME_CHOICES) {
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        trailingIcon = {
                            if (theme == pref) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            AuthManager.setThemePreference(pref)
                        },
                        modifier = Modifier.testTag("bots_theme_${pref.name.lowercase()}"),
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

private val THEME_CHOICES =
    listOf(
        Triple(ThemePreference.LIGHT, R.string.bots_theme_day, Icons.Filled.LightMode),
        Triple(ThemePreference.DARK, R.string.bots_theme_night, Icons.Filled.DarkMode),
        Triple(ThemePreference.SYSTEM, R.string.bots_theme_auto, Icons.Filled.BrightnessAuto),
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
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(if (selected) BotsPalette.Deck2 else Color.Transparent)
                .border(1.dp, if (selected) BotsPalette.Deck3 else BotsPalette.Line, shape)
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) BotsPalette.Fg else BotsPalette.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            count.toString(),
            color = if (accent && count > 0) BotsPalette.Ok else BotsPalette.Muted,
            fontFamily = Mono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = BotsPalette.Faint,
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp),
    )
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = hueFor(profile)
    val recent = BotsPresentation.isRecent(profile, now)
    val title = profile.effectiveTitle
    val summary = BotsPresentation.shortSummary(profile.effectiveDescription)
    val handle = BotsPresentation.distinctHandle(profile.name, title)
    val task = BotsPresentation.currentTask(profile)
    val time = BotsPresentation.relativeTime(BotsPresentation.lastActive(profile), now)
    val shape = RoundedCornerShape(14.dp)
    val accent = if (needsYou) BotsPalette.Attention else hue

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    when {
                        selected -> Brush.horizontalGradient(listOf(hue.copy(alpha = 0.2f), Color.Transparent))
                        needsYou -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.1f), Color.Transparent))
                        else -> SolidColor(Color.Transparent)
                    },
                ).drawBehind {
                    if (selected || needsYou) {
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(0f, 14.dp.toPx()),
                            size = Size(3.dp.toPx(), size.height - 28.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
                .testTag("bot_row_${profile.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotOrb(
            initials = BotsPresentation.initials(title),
            hue = hue,
            size = 42.dp,
            working = working,
            presence = if (recent) OrbPresence.RECENT else OrbPresence.IDLE,
            shapeKey = profile.botMeta()?.avatar?.shape,
            imageUrl = imageUrl,
            attention = needsYou,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Line 1: the name (and its @handle when that says something new); the time sits at the end.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        buildAnnotatedString {
                            append(title)
                            if (handle != null) {
                                withStyle(
                                    SpanStyle(
                                        color = BotsPalette.Faint,
                                        fontFamily = Mono,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                ) { append("  $handle") }
                            }
                        },
                    color = BotsPalette.Fg,
                    fontSize = 15.sp,
                    fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (time.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = time,
                        color = if (working || unread > 0) hue else BotsPalette.Faint,
                        fontFamily = Mono,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
            }
            // Below, Telegram style: a status line when it needs you or is working, then the
            // latest message of the conversation (three lines in all).
            val status =
                when {
                    needsYou -> stringResource(R.string.bots_needs_you)
                    working -> stringResource(R.string.bots_working_on, task ?: summary)
                    else -> null
                }
            if (status != null) {
                Text(
                    text = status,
                    color = if (needsYou) BotsPalette.Attention else lerp(hue, BotsPalette.Fg, 0.3f),
                    fontSize = 12.5.sp,
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
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    maxLines = if (status != null) 2 else 3,
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
}

@Composable
private fun UnreadBadge(
    count: Int,
    hue: Color,
) {
    val label = BotsPresentation.unreadLabel(count)
    val description = stringResource(R.string.bots_unread, label)
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(hue)
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = BotsPalette.OnHue,
            fontSize = 10.5.sp,
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                .testTag("group_row_${group.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotOrb(initials = "", hue = BotsPalette.Muted, size = 42.dp, team = true)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.name,
                color = BotsPalette.Fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: group.members.joinToString(" · ") { it.effectiveTitle },
                color = BotsPalette.Muted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    Box(
        modifier =
            modifier
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
                }.testTag("bots_chat_pane"),
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
                    chatViewModel.uiState
                        .map { it.currentSessionId }
                        .distinctUntilChanged()
                        .collect { current ->
                            when {
                                current == null -> Unit
                                current == requested || requested == null -> settled = true
                                settled -> latestSessionChanged(profile.name, current)
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
) {
    val recent = BotsPresentation.isRecent(profile, now)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("bots_pane_title")) {
        BotOrb(
            initials = BotsPresentation.initials(profile.effectiveTitle),
            hue = hue,
            size = 36.dp,
            working = working,
            shapeKey = profile.botMeta()?.avatar?.shape,
            imageUrl = imageUrl,
            attention = needsYou,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = profile.effectiveTitle,
                color = BotsPalette.Fg,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusPill(profile = profile, hue = hue, working = working, recent = recent, now = now, needsYou = needsYou)
        }
    }
}

@Composable
private fun StatusPill(
    profile: ProfileInfo,
    hue: Color,
    working: Boolean,
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
    val time = BotsPresentation.relativeTime(BotsPresentation.lastActive(profile), now)
    val text =
        when {
            needsYou -> stringResource(R.string.bots_waiting_for_you)
            working -> stringResource(R.string.bots_working_on, BotsPresentation.currentTask(profile) ?: profile.name)
            time.isNotEmpty() -> stringResource(R.string.bots_last_active, time)
            else -> "@${profile.name}"
        }
    val color =
        when {
            needsYou -> BotsPalette.Attention
            working -> lerp(hue, BotsPalette.Fg, 0.3f)
            else -> BotsPalette.Muted
        }
    Row(
        modifier =
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
                ).padding(horizontal = 7.dp, vertical = 1.dp),
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
            color = color,
            fontFamily = Mono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
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

    private const val KEY_LAST_SESSION = "last_session:"

    fun lastSession(
        context: android.content.Context,
        bot: String,
    ): String? =
        context
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_LAST_SESSION + bot, null)

    fun setLastSession(
        context: android.content.Context,
        bot: String,
        sessionId: String,
    ) {
        context
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SESSION + bot, sessionId)
            .apply()
    }

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
