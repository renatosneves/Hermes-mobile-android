package com.m57.hermescontrol.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.LocalToybox
import com.m57.hermescontrol.theme.toySticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Sealed interface for navigation icon types — eliminates boolean-flag anti-pattern. */
sealed interface NavIcon {
    /** Hamburger menu icon that opens the drawer. */
    data class Menu(
        val onOpen: () -> Unit,
    ) : NavIcon

    /** Back arrow icon. */
    data class Back(
        val onBack: () -> Unit,
    ) : NavIcon

    /** Any other single action in the navigation slot, e.g. showing or hiding a side list. */
    data class Action(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val description: String,
        val onClick: () -> Unit,
    ) : NavIcon
}

/**
 * Single source of truth for whether the modal navigation drawer's swipe gestures
 * should be enabled for the currently-composed screen.
 *
 * Each [HermesScaffold] (or [DisableDrawerGestures]) reconciles its preference into
 * the singleton instance via a [SideEffect]; `ModalNavigationDrawer` in `Navigation.kt`
 * reads [enabled] and passes it straight to Material's `gesturesEnabled` parameter.
 *
 * This replaces the old `DRAWER_GESTURE_SCREENS` set + `LaunchedEffect(snapTo(Closed))`
 * workaround documented in issue #619: gesture state now flows from the rendered
 * screen automatically, and the drawer controller closes the drawer itself when a
 * screen opts out — no global allow-list, no defensive close callbacks.
 */
class DrawerGestureController(
    private val drawerState: DrawerState,
    private val scope: CoroutineScope,
) {
    var enabled by mutableStateOf(false)
        private set

    /**
     * Reconcile a screen's desired gesture state.
     *
     * Called from a [SideEffect] on every screen composition. If a screen opts out
     * of gestures while the drawer is open, we close it synchronously so the scrim
     * cannot intercept the next tap (the bug class from PRs #445 / #454 / #455).
     */
    fun reconcile(desired: Boolean) {
        if (desired == enabled) return
        enabled = desired
        if (!desired && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }
}

/**
 * CompositionLocal that exposes the active [DrawerGestureController].
 *
 * Provided by `MainNavigation` and read both by [HermesScaffold] (to reconcile a
 * screen's preference) and by `ModalNavigationDrawer` (to gate gestures).
 */
val LocalDrawerGestureController =
    compositionLocalOf<DrawerGestureController?> {
        null
    }

/**
 * Opt out of drawer gestures for a screen that does NOT use [HermesScaffold].
 *
 * Used by full-bleed entry screens (Landing, AuthLogin) that
 * render their own layout. Scaffold-based screens should instead pass
 * `drawerGesturesEnabled = false` to [HermesScaffold].
 */
@Composable
fun DisableDrawerGestures() {
    val controller = LocalDrawerGestureController.current ?: return
    SideEffect { controller.reconcile(false) }
}

/**
 * Shared Scaffold wrapper that standardizes the TopAppBar.
 *
 * Improvements (v2):
 *  - Scroll-aware top bar (pinned collapse on scroll)
 *  - Uses Spacing tokens internally
 *  - Simplified API: [navigationIcon] replaces the old showBack/onBack/onOpenDrawer trio
 *  - Safe-area aware via Scaffold insets
 *  - Drawer-gesture aware via [drawerGesturesEnabled] (issue #619)
 *
 * Screens that still need a Back arrow instead of a Menu icon can set
 * `navigationIcon = NavIcon.Back { … }` instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesScaffold(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: NavIcon? = null,
    /**
     * Whether the modal navigation drawer's swipe-open/swipe-close gestures should
     * be active while this screen is visible. Defaults to `true` (primary screens).
     *
     * Pass `false` for drill-down sub-pages (e.g. Settings sub-pages) where the
     * drawer should be closed and locked. The [DrawerGestureController] reconciles
     * this preference and closes the drawer automatically if a screen opts out
     * while the drawer is open — see issue #619.
     */
    drawerGesturesEnabled: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    pinTopBar: Boolean = false,
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val gestureController = LocalDrawerGestureController.current
    SideEffect { gestureController?.reconcile(drawerGesturesEnabled) }
    val toybox = LocalToybox.current
    val barColor = if (toybox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val barContentColor = if (toybox) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    val scrollBehavior =
        if (pinTopBar) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { snackbarHost() },
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    when (val icon = navigationIcon) {
                        is NavIcon.Back -> {
                            TopBarIconButton(onClick = icon.onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.content_desc_back),
                                    modifier = Modifier.testTag("back_button"),
                                )
                            }
                        }

                        is NavIcon.Action -> {
                            TopBarIconButton(onClick = icon.onClick) {
                                Icon(
                                    imageVector = icon.icon,
                                    contentDescription = icon.description,
                                    modifier = Modifier.testTag("nav_action_button"),
                                )
                            }
                        }

                        is NavIcon.Menu -> {
                            TopBarIconButton(onClick = icon.onOpen) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.content_desc_open_drawer),
                                    modifier = Modifier.testTag("menu_button"),
                                )
                            }
                        }

                        null -> { /* no navigation icon */ }
                    }
                },
                actions = {
                    if (onRefresh != null) {
                        TopBarIconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.content_desc_refresh),
                                modifier = Modifier.testTag("refresh_button"),
                            )
                        }
                    }
                    actions()
                },
                modifier =
                    if (toybox) {
                        // A 3 dp dark line under the bar.
                        Modifier.drawBehind {
                            val w = 3.dp.toPx()
                            drawRect(
                                BotsPalette.ToyOutline,
                                topLeft = Offset(0f, size.height - w),
                                size = Size(size.width, w),
                            )
                        }
                    } else {
                        Modifier
                    },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = barColor,
                        titleContentColor = barContentColor,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        val refreshContent: @Composable () -> Unit = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            bottom = paddingValues.calculateBottomPadding(),
                        ).dynamicTopBarPadding(scrollBehavior, paddingValues.calculateTopPadding()),
            ) {
                content(paddingValues)
            }
        }

        if (onRefresh != null) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                refreshContent()
            }
        } else {
            refreshContent()
        }
    }
}

/**
 * A top-bar icon button. Plain everywhere except the Bots screen in Toybox, where it is a 48 dp
 * white sticker (radius 18, 3 dp shadow).
 */
@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!LocalToybox.current) {
        IconButton(onClick = onClick, modifier = modifier) { content() }
        return
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier =
            modifier
                .padding(start = 4.dp, end = 7.dp)
                .size(48.dp)
                .toySticker(shape, BotsPalette.ToyWhite, depth = 3.dp)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides BotsPalette.ToyOutline, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.dynamicTopBarPadding(
    scrollBehavior: TopAppBarScrollBehavior,
    baseTopPadding: Dp,
): Modifier =
    this.layout { measurable, constraints ->
        val baseTopPaddingPx = baseTopPadding.roundToPx()
        val heightOffset = scrollBehavior.state.heightOffset.roundToInt()
        val activeTopPadding = (baseTopPaddingPx + heightOffset).coerceAtLeast(0)

        val placeable =
            measurable.measure(
                constraints.copy(
                    maxHeight = (constraints.maxHeight - activeTopPadding).coerceAtLeast(0),
                    minHeight = (constraints.minHeight - activeTopPadding).coerceAtLeast(0),
                ),
            )

        layout(placeable.width, placeable.height + activeTopPadding) {
            placeable.placeRelative(0, activeTopPadding)
        }
    }
