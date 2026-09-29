package com.m57.hermescontrol.ui.bots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.ui.chat.ChatScreen
import com.m57.hermescontrol.ui.common.BotAvatar
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.LocalDrawerGestureController
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.SkeletonListState
import com.m57.hermescontrol.ui.common.ToastEffect
import kotlinx.coroutines.launch

/** Width from which the Bots home shows the chat beside the list (an unfolded Fold, a tablet). */
private val TWO_PANE_MIN_WIDTH = 600.dp

private fun ProfileInfo.canonicalSessionId(): String? =
    (canonical_session?.resolved_id ?: canonical_session?.id)?.takeIf { it.isNotBlank() }

/**
 * Grok-style Bots home: the bot list on the left and the selected bot's conversation on the
 * right when there is room. On a narrow screen the list fills it and a tap opens the chat.
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

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("bots_home")) {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
        val onOpenBot: (ProfileInfo) -> Unit = { profile ->
            scope.launch {
                viewModel.selectBot(profile)
                val sessionId = profile.canonicalSessionId()
                if (twoPane) {
                    openBotName = profile.name
                    openSessionId = sessionId
                } else if (sessionId != null) {
                    NavigationController.openChatSession(sessionId)
                } else {
                    NavigationController.navigateTo(com.m57.hermescontrol.ChatScreen)
                }
            }
        }

        if (twoPane) {
            val selectedName = openBotName ?: state.activeProfileName
            val chatSessionId =
                openSessionId ?: state.profiles.firstOrNull { it.name == selectedName }?.canonicalSessionId()
            Row(modifier = Modifier.fillMaxSize()) {
                BotsListPane(
                    modifier = Modifier.weight(0.38f).fillMaxHeight(),
                    onOpenDrawer = onOpenDrawer,
                    viewModel = viewModel,
                    selectedBotName = selectedName,
                    compact = true,
                    onOpenBot = onOpenBot,
                )
                VerticalDivider()
                // The list pane owns drawer gestures for this screen; the embedded chat must not
                // reconcile its own preference over it (issue #619).
                CompositionLocalProvider(LocalDrawerGestureController provides null) {
                    ChatScreen(
                        modifier = Modifier.weight(0.62f).fillMaxHeight().testTag("bots_chat_pane"),
                        onOpenDrawer = null,
                        sessionId = chatSessionId,
                    )
                }
            }
        } else {
            BotsListPane(
                modifier = Modifier.fillMaxSize(),
                onOpenDrawer = onOpenDrawer,
                viewModel = viewModel,
                selectedBotName = null,
                compact = false,
                onOpenBot = onOpenBot,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotsListPane(
    modifier: Modifier,
    onOpenDrawer: (() -> Unit)?,
    viewModel: BotsViewModel,
    selectedBotName: String?,
    compact: Boolean,
    onOpenBot: (ProfileInfo) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = state.selectedTab.ordinal) { BotsTab.entries.size }
    var isSearchActive by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingBot by remember { mutableStateOf<ProfileInfo?>(null) }
    var disbandingGroup by remember { mutableStateOf<GroupInfo?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        val targetTab = BotsTab.entries.getOrNull(pagerState.currentPage) ?: BotsTab.BOTS
        if (state.selectedTab != targetTab) {
            viewModel.setSelectedTab(targetTab)
        }
    }

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab.ordinal) {
            pagerState.animateScrollToPage(state.selectedTab.ordinal)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadBots()
    }

    disbandingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { disbandingGroup = null },
            title = {
                Text(
                    text = stringResource(R.string.bots_group_disband_confirm_title, group.name),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(stringResource(R.string.bots_group_disband_confirm_msg))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disbandGroupChat(group.name) {
                            disbandingGroup = null
                        }
                    },
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
                TextButton(onClick = { disbandingGroup = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    editingBot?.let { bot ->
        EditBotBottomSheet(
            bot = bot,
            onDismiss = { editingBot = null },
            onSave = { title, description, shape, color ->
                viewModel.updateBotMeta(bot.name, title, description, shape, color) {
                    editingBot = null
                }
            },
            onDelete = {
                viewModel.deleteBot(bot.name) {
                    editingBot = null
                }
            },
        )
    }

    if (showCreateDialog) {
        CreateBotDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, title, description, shape, color ->
                viewModel.createBot(name, title, description, shape, color) {
                    showCreateDialog = false
                }
            },
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupChatDialog(
            availableBots = state.profiles,
            onDismiss = { showCreateGroupDialog = false },
            onCreateGroup = { groupName, botNames ->
                viewModel.createGroupChat(groupName, botNames) {
                    showCreateGroupDialog = false
                }
            },
        )
    }

    ToastEffect(
        toastMessage = state.toastMessage,
        onClearToast = { viewModel.clearToast() },
    )

    HermesScaffold(
        modifier = modifier,
        drawerGesturesEnabled = pagerState.currentPage == 0,
        title = {
            if (isSearchActive) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.bots_search_placeholder)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (state.searchQuery.isNotEmpty()) {
                                    viewModel.setSearchQuery("")
                                } else {
                                    isSearchActive = false
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("bots_search_field"),
                )
            } else {
                Text(
                    text = stringResource(R.string.screen_bots),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        actions = {
            if (!isSearchActive) {
                IconButton(
                    onClick = { showCreateGroupDialog = true },
                    modifier = Modifier.testTag("bots_action_create_group"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GroupAdd,
                        contentDescription = stringResource(R.string.bots_action_create_group),
                    )
                }
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("bots_action_create"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.bots_action_create),
                    )
                }
                IconButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier.testTag("bots_action_search"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.bots_search_placeholder),
                    )
                }
            }
            if (state.hasHiddenBots) {
                IconButton(
                    onClick = { viewModel.toggleShowHidden() },
                    modifier = Modifier.testTag("bots_action_toggle_hidden"),
                ) {
                    Icon(
                        imageVector =
                            if (state.showHidden) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                        contentDescription =
                            if (state.showHidden) {
                                stringResource(R.string.bots_hide_hidden)
                            } else {
                                stringResource(R.string.bots_show_hidden)
                            },
                    )
                }
            }
        },
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.loadBots(isRefresh = true) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = pagerState.currentPage == BotsTab.BOTS.ordinal,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(BotsTab.BOTS.ordinal)
                        }
                    },
                    text = { Text(stringResource(R.string.bots_tab_all)) },
                    icon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
                )
                Tab(
                    selected = pagerState.currentPage == BotsTab.GROUPS.ordinal,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(BotsTab.GROUPS.ordinal)
                        }
                    },
                    text = {
                        Text(
                            if (state.allGroups.isNotEmpty()) {
                                "${stringResource(R.string.bots_tab_groups)} (${state.allGroups.size})"
                            } else {
                                stringResource(R.string.bots_tab_groups)
                            },
                        )
                    },
                    icon = { Icon(Icons.Filled.Group, contentDescription = null) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (BotsTab.entries.getOrElse(page) { BotsTab.BOTS }) {
                    BotsTab.BOTS -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(pagerState.currentPage, onOpenDrawer) {
                                        if (pagerState.currentPage == 0) {
                                            awaitEachGesture {
                                                awaitFirstDown(pass = PointerEventPass.Initial)
                                                var totalDragX = 0f
                                                while (true) {
                                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull() ?: break
                                                    if (!change.pressed) break
                                                    val dx = change.position.x - change.previousPosition.x
                                                    if (dx > 0) {
                                                        totalDragX += dx
                                                        if (totalDragX > 75f) {
                                                            onOpenDrawer?.invoke()
                                                            break
                                                        }
                                                    } else if (dx < -10f) {
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    },
                        ) {
                            when {
                                state.isLoading && state.profiles.isEmpty() -> {
                                    SkeletonListState()
                                }

                                state.errorMessage != null && state.profiles.isEmpty() -> {
                                    ErrorState(
                                        message = state.errorMessage ?: "",
                                        onRetry = { viewModel.loadBots() },
                                    )
                                }

                                state.displayProfiles.isEmpty() -> {
                                    EmptyState(
                                        title = stringResource(R.string.bots_empty_title),
                                        subtitle = stringResource(R.string.bots_empty_description),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding =
                                            PaddingValues(
                                                start = 16.dp,
                                                end = 16.dp,
                                                top = 8.dp,
                                                bottom = 24.dp,
                                            ),
                                        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 10.dp),
                                    ) {
                                        if (state.activeNowBots.isNotEmpty() && !isSearchActive) {
                                            item(key = "active_now_strip") {
                                                ActiveNowStrip(
                                                    activeBots = state.activeNowBots,
                                                    onSelectBot = onOpenBot,
                                                )
                                            }
                                        }

                                        items(
                                            items = state.displayProfiles,
                                            key = { it.name },
                                        ) { profile ->
                                            BotCard(
                                                profile = profile,
                                                isActiveProfile = profile.name == state.activeProfileName,
                                                isSelected = profile.name == selectedBotName,
                                                compact = compact,
                                                onClick = { onOpenBot(profile) },
                                                onEditClick = {
                                                    editingBot = profile
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    BotsTab.GROUPS -> {
                        when {
                            state.displayGroups.isEmpty() -> {
                                EmptyState(
                                    title = stringResource(R.string.bots_groups_empty_title),
                                    subtitle = stringResource(R.string.bots_groups_empty_desc),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding =
                                        PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 24.dp,
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(
                                        items = state.displayGroups,
                                        key = { it.name },
                                    ) { group ->
                                        GroupCard(
                                            group = group,
                                            onClick = {
                                                if (group.members.isNotEmpty()) {
                                                    NavigationController.navigateTo(
                                                        com.m57.hermescontrol.GroupChatKey(group.name),
                                                    )
                                                }
                                            },
                                            onDisbandClick = {
                                                disbandingGroup = group
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveNowStrip(
    activeBots: List<ProfileInfo>,
    onSelectBot: (ProfileInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.bots_active_now),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            activeBots.forEach { bot ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelectBot(bot) }
                            .testTag("bot_active_chip_${bot.name}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BotAvatar(
                            name = bot.name,
                            avatar = bot.botMeta()?.avatar,
                            size = 24.dp,
                            isActive = true,
                            showPresence = true,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bot.effectiveTitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun BotCard(
    profile: ProfileInfo,
    isActiveProfile: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    compact: Boolean = false,
) {
    val botMeta = profile.botMeta()
    val isHidden = profile.isHidden
    val hasWorker = profile.worker_session != null

    // Compact = the Grok-style rail beside the chat: flat rows, the open bot highlighted.
    Card(
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        compact && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        compact -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        isActiveProfile -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surface
                    },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 0.dp else 1.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .testTag("bot_card_${profile.name}"),
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotAvatar(
                name = profile.name,
                avatar = botMeta?.avatar,
                size = 44.dp,
                isActive = isActiveProfile || hasWorker,
                showPresence = true,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = profile.effectiveTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isHidden) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = stringResource(R.string.bots_hidden_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (profile.effectiveTitle != profile.name) {
                    Text(
                        text = "@${profile.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (profile.effectiveDescription.isNotBlank()) {
                    Text(
                        text = profile.effectiveDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val preview = profile.canonical_session?.preview ?: profile.last_session?.preview
                if (!preview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.testTag("bot_edit_button_${profile.name}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.bots_edit_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupInfo,
    onClick: () -> Unit,
    onDisbandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .testTag("group_card_${group.name}"),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.bots_group_members_count, group.members.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (group.members.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group.members.take(4).forEach { member ->
                            BotAvatar(
                                name = member.name,
                                avatar = member.botMeta()?.avatar,
                                size = 20.dp,
                                showPresence = false,
                            )
                        }
                        if (group.members.size > 4) {
                            Text(
                                text = "+${group.members.size - 4}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onDisbandClick,
                modifier = Modifier.testTag("group_disband_button_${group.name}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.bots_group_disband),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
