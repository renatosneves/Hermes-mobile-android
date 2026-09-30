package com.m57.hermescontrol.ui.bots

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.ui.chat.ChatScreen
import com.m57.hermescontrol.ui.common.DisableDrawerGestures
import com.m57.hermescontrol.ui.common.LocalDrawerGestureController
import com.m57.hermescontrol.ui.common.ToastEffect
import kotlinx.coroutines.delay
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
    var editingBot by remember { mutableStateOf<ProfileInfo?>(null) }
    var disbandingGroup by remember { mutableStateOf<GroupInfo?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var now by remember { mutableDoubleStateOf(nowSeconds()) }

    // Horizontal drags on this screen belong to its content; the drawer opens from the menu button.
    DisableDrawerGestures()

    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            now = nowSeconds()
            viewModel.loadBots(isRefresh = true)
        }
    }

    ToastEffect(toastMessage = state.toastMessage, onClearToast = { viewModel.clearToast() })

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
                    if (twoPane) {
                        openBotName = profile.name
                        openSessionId = profile.canonicalSessionId()
                    }
                    viewModel.selectBot(profile)
                    if (!twoPane) {
                        val sessionId = profile.canonicalSessionId()
                        if (sessionId != null) {
                            NavigationController.openChatSession(sessionId)
                        } else {
                            NavigationController.navigateTo(com.m57.hermescontrol.ChatScreen)
                        }
                    }
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
                    onDisbandGroup = { disbandingGroup = it },
                    onCreateBot = { showCreateDialog = true },
                    onCreateGroup = { showCreateGroupDialog = true },
                    onToggleHidden = viewModel::toggleShowHidden,
                    onRefresh = {
                        now = nowSeconds()
                        viewModel.loadBots(isRefresh = true)
                    },
                )
            }

            if (twoPane) {
                val selectedName = openBotName ?: state.activeProfileName
                val selected = state.profiles.firstOrNull { it.name == selectedName }
                val chatSessionId = openSessionId ?: selected?.canonicalSessionId()
                val railWidth = (maxWidth * 0.38f).coerceIn(280.dp, 420.dp)
                Row(modifier = Modifier.fillMaxSize()) {
                    rail(Modifier.width(railWidth).fillMaxHeight(), selectedName)
                    Hinge()
                    BotsChatPane(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        profile = selected,
                        sessionId = chatSessionId,
                        now = now,
                        baseScheme = baseScheme,
                    )
                }
            } else {
                rail(Modifier.fillMaxSize(), null)
            }
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
    onDisbandGroup: (GroupInfo) -> Unit,
    onCreateBot: () -> Unit,
    onCreateGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(RailFilter.ALL) }
    val bots = state.displayProfiles
    val working = bots.filter { BotsPresentation.isWorking(it, now) }
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
        )

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
                    if (shownBots.isNotEmpty() && shownGroups.isNotEmpty()) {
                        item(key = "label_bots") { SectionLabel(stringResource(R.string.bots_tab_all)) }
                    }
                    items(items = shownBots, key = { it.name }) { profile ->
                        BotRow(
                            modifier = Modifier.animateItem(),
                            profile = profile,
                            now = now,
                            selected = profile.name == selectedName,
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
    showHidden: Boolean,
    hasHidden: Boolean,
    searchOpen: Boolean,
    onOpenDrawer: (() -> Unit)?,
    onToggleSearch: () -> Unit,
    onCreateBot: () -> Unit,
    onCreateGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (refreshing) BotsPalette.You else BotsPalette.Ok),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = BotsPresentation.GATEWAY_LABEL,
                    color = BotsPalette.Muted,
                    fontFamily = Mono,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag("bots_gateway"),
                )
                Text(
                    text = " · " + pluralStringResource(R.plurals.bots_count, botCount, botCount),
                    color = BotsPalette.Faint,
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
            }
        }
    }
}

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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = hueFor(profile)
    val working = BotsPresentation.isWorking(profile, now)
    val recent = BotsPresentation.isRecent(profile, now)
    val title = profile.effectiveTitle
    val summary = BotsPresentation.shortSummary(profile.effectiveDescription)
    val task = BotsPresentation.currentTask(profile)
    val time = BotsPresentation.relativeTime(BotsPresentation.lastActive(profile), now)
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (selected) {
                        Brush.horizontalGradient(listOf(hue.copy(alpha = 0.2f), Color.Transparent))
                    } else {
                        SolidColor(Color.Transparent)
                    },
                ).drawBehind {
                    if (selected) {
                        drawRoundRect(
                            color = hue,
                            topLeft = Offset(0f, 14.dp.toPx()),
                            size = Size(3.dp.toPx(), size.height - 28.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
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
            imageUrl = profile.botMeta()?.avatar?.image_url,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = BotsPalette.Fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "@${profile.name}",
                    color = BotsPalette.Faint,
                    fontFamily = Mono,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
            val line =
                if (working) {
                    stringResource(R.string.bots_working_on, task ?: summary)
                } else {
                    summary
                }
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    color = if (working) lerp(hue, BotsPalette.Fg, 0.3f) else BotsPalette.Muted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (time.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = time,
                color = if (working) hue else BotsPalette.Faint,
                fontFamily = Mono,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: GroupInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                text = group.members.joinToString(" · ") { it.effectiveTitle },
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
    sessionId: String?,
    now: Double,
    baseScheme: androidx.compose.material3.ColorScheme,
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
                CompositionLocalProvider(LocalDrawerGestureController provides null) {
                    ChatScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOpenDrawer = null,
                        sessionId = sessionId,
                        titleOverride = { PaneTitle(profile = profile, hue = hue, now = now) },
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
) {
    val working = BotsPresentation.isWorking(profile, now)
    val recent = BotsPresentation.isRecent(profile, now)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("bots_pane_title")) {
        BotOrb(
            initials = BotsPresentation.initials(profile.effectiveTitle),
            hue = hue,
            size = 36.dp,
            working = working,
            shapeKey = profile.botMeta()?.avatar?.shape,
            imageUrl = profile.botMeta()?.avatar?.image_url,
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
            StatusPill(profile = profile, hue = hue, working = working, recent = recent, now = now)
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
) {
    val shape = RoundedCornerShape(999.dp)
    val glow =
        if (working) {
            val transition = rememberInfiniteTransition(label = "pill-glow")
            val g by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
                label = "pill-glow-value",
            )
            g
        } else {
            0f
        }
    val time = BotsPresentation.relativeTime(BotsPresentation.lastActive(profile), now)
    val text =
        when {
            working -> stringResource(R.string.bots_working_on, BotsPresentation.currentTask(profile) ?: profile.name)
            time.isNotEmpty() -> stringResource(R.string.bots_last_active, time)
            else -> "@${profile.name}"
        }
    val color = if (working) lerp(hue, BotsPalette.Fg, 0.3f) else BotsPalette.Muted
    Row(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .clip(shape)
                .background(BotsPalette.Deck.copy(alpha = 0.8f))
                .border(1.dp, if (working) hue.copy(alpha = 0.45f) else BotsPalette.Line, shape)
                .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .drawBehind {
                        val r = 3.5.dp.toPx()
                        if (working) {
                            drawCircle(hue.copy(alpha = (1f - glow) * 0.5f), radius = r + glow * 3.dp.toPx())
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
        )
    }

    if (showCreateDialog) {
        CreateBotDialog(
            onDismiss = onDismissCreate,
            onCreate = { name, title, description, shape, color, imageUrl ->
                viewModel.createBot(name, title, description, shape, color, imageUrl) { onDismissCreate() }
            },
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
