package com.m57.hermescontrol.ui.fleet

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.FleetPalette
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Width at which the screen splits across the hinge (unfolded Fold) instead of using tabs. */
private val TWO_PANE_MIN_WIDTH = 600.dp
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

private enum class CoverTab { LIVE, BOARD, YOU }

/**
 * Fleet: the whole bot fleet at a glance. Who is delegating to whom, what is waiting on
 * you, and the Kanban board moving as bots pick work up and hand it on.
 *
 * Unfolded (≥ 600 dp) the map and the "needs you" column sit on either side of the
 * hinge with the board across the bottom. On the cover screen each part gets a tab.
 */
@Composable
fun FleetScreen(
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: FleetViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HermesScaffold(
        title = { Text(stringResource(R.string.screen_fleet)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        actions = {
            TextButton(onClick = viewModel::cycleSpeed) { Text("${state.speed}×") }
            IconButton(onClick = viewModel::togglePause) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription =
                        stringResource(
                            if (state.isPaused) R.string.fleet_resume else R.string.fleet_pause,
                        ),
                )
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("fleet_screen")) {
            if (maxWidth >= TWO_PANE_MIN_WIDTH) {
                UnfoldedLayout(state, viewModel)
            } else {
                CoverLayout(state, viewModel)
            }
        }
    }
}

@Composable
private fun UnfoldedLayout(
    state: FleetUiState,
    viewModel: FleetViewModel,
) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        StatsRow(state)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Panel(Modifier.weight(1f), stringResource(R.string.fleet_delegation)) {
                DelegationMap(state.snapshot, state.speed, state.isPaused)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                NeedsYouPanel(state.snapshot, viewModel::approve, viewModel::sendBack)
                Panel(Modifier.fillMaxWidth(), stringResource(R.string.fleet_live)) {
                    LiveFeed(state.snapshot, maxItems = 9)
                }
            }
        }
        Panel(Modifier.fillMaxWidth(), stringResource(R.string.fleet_board)) {
            Board(state.snapshot, columnWidth = null)
        }
    }
}

@Composable
private fun CoverLayout(
    state: FleetUiState,
    viewModel: FleetViewModel,
) {
    val spacing = LocalSpacing.current
    var tab by rememberSaveable { mutableIntStateOf(CoverTab.LIVE.ordinal) }
    val waiting = state.snapshot.inColumn(FleetColumn.NEEDS_YOU).size
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                tab == CoverTab.LIVE.ordinal,
                { tab = CoverTab.LIVE.ordinal },
                text = { Text(stringResource(R.string.fleet_live)) },
            )
            Tab(tab == CoverTab.BOARD.ordinal, {
                tab = CoverTab.BOARD.ordinal
            }, text = { Text(stringResource(R.string.fleet_board)) })
            Tab(
                tab == CoverTab.YOU.ordinal,
                { tab = CoverTab.YOU.ordinal },
                text = {
                    Text(
                        if (waiting >
                            0
                        ) {
                            stringResource(R.string.fleet_you_count, waiting)
                        } else {
                            stringResource(R.string.fleet_you)
                        },
                    )
                },
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            StatsRow(state)
            when (CoverTab.entries[tab]) {
                CoverTab.LIVE -> {
                    DelegationMap(state.snapshot, state.speed, state.isPaused)
                    Panel(
                        Modifier.fillMaxWidth(),
                        stringResource(R.string.fleet_live),
                    ) { LiveFeed(state.snapshot, maxItems = 8) }
                }

                CoverTab.BOARD -> {
                    Board(state.snapshot, columnWidth = 260.dp)
                }

                CoverTab.YOU -> {
                    NeedsYouPanel(state.snapshot, viewModel::approve, viewModel::sendBack)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(state: FleetUiState) {
    val status = LocalHermesStatusColors.current
    val snap = state.snapshot
    val working = snap.inColumn(FleetColumn.WORKING).size
    val waiting = snap.inColumn(FleetColumn.NEEDS_YOU).size
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip(stringResource(R.string.fleet_stat_working, working), status.info)
        StatChip(stringResource(R.string.fleet_stat_needs_you, waiting), status.warning, emphasised = waiting > 0)
        StatChip(stringResource(R.string.fleet_stat_done, snap.doneToday), status.success)
        if (state.isDemo) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, status.warning.copy(alpha = 0.6f)),
            ) {
                Text(
                    stringResource(R.string.fleet_demo_data),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = status.warning,
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    text: String,
    dot: Color,
    emphasised: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border =
            BorderStroke(
                1.dp,
                if (emphasised) dot.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(
                text,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (emphasised) dot else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier,
    title: String,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.4f) ?: MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun NeedsYouPanel(
    snapshot: FleetSnapshot,
    onApprove: (Long) -> Unit,
    onSendBack: (Long) -> Unit,
) {
    val warning = LocalHermesStatusColors.current.warning
    val waiting = snapshot.inColumn(FleetColumn.NEEDS_YOU)
    Panel(
        Modifier.fillMaxWidth().testTag("fleet_needs_you"),
        stringResource(R.string.fleet_waiting_on_you),
        accent = warning,
    ) {
        if (waiting.isEmpty()) {
            Text(
                stringResource(R.string.fleet_nothing_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        waiting.forEach { task ->
            androidx.compose.runtime.key(task.id) {
                AskCard(task, snapshot.bot(task.owner), warning, onApprove, onSendBack)
            }
        }
    }
}

@Composable
private fun AskCard(
    task: FleetTask,
    bot: FleetBot?,
    warning: Color,
    onApprove: (Long) -> Unit,
    onSendBack: (Long) -> Unit,
) {
    val ask = task.ask ?: return
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(450)) }
    Surface(
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * -24f
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, warning.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                bot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(FleetPalette.hue(it.hueIndex))) }
                Text(
                    "${bot?.name ?: task.owner} · ${task.title}",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(ask.action, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.heightIn(min = 0.dp)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(warning.copy(alpha = 0.5f)))
                Text(
                    ask.detail,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onApprove(task.id) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = warning,
                            contentColor = LocalHermesStatusColors.current.onWarning,
                        ),
                ) { Text(ask.action.substringBefore(' ')) }
                OutlinedButton(onClick = { onSendBack(task.id) }) { Text(stringResource(R.string.fleet_send_back)) }
            }
        }
    }
}

@Composable
private fun LiveFeed(
    snapshot: FleetSnapshot,
    maxItems: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        snapshot.events.take(maxItems).forEachIndexed { index, event ->
            androidx.compose.runtime.key(event.id) {
                FeedLine(event, snapshot, fade = 1f - index / (maxItems + 2f))
            }
        }
    }
}

@Composable
private fun FeedLine(
    event: FleetEvent,
    snapshot: FleetSnapshot,
    fade: Float,
) {
    val time = remember(event.id) { LocalTime.now().format(CLOCK) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(400)) }
    val text = feedText(event, snapshot, MaterialTheme.colorScheme.onSurface)
    Row(
        Modifier.graphicsLayer {
            alpha = appear.value * fade
            translationY = (1f - appear.value) * -16f
        },
    ) {
        Text(
            time,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun feedText(
    event: FleetEvent,
    snapshot: FleetSnapshot,
    strong: Color,
): AnnotatedString =
    buildAnnotatedString {
        event.parts.forEach { part ->
            when (part) {
                is FeedPart.Plain -> {
                    append(part.text)
                }

                is FeedPart.Strong -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(part.text) }
                }

                is FeedPart.Bot -> {
                    val bot = snapshot.bot(part.botId)
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            color =
                                bot?.let { FleetPalette.hue(it.hueIndex) } ?: strong,
                        ),
                    ) {
                        append(if (bot?.isHub == true) bot.initials else bot?.name ?: part.botId)
                    }
                }
            }
        }
    }

/**
 * The Kanban board. Cards are movable content inside a lookahead scope, so when a task
 * changes column its card physically flies from one column to the other.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Board(
    snapshot: FleetSnapshot,
    columnWidth: androidx.compose.ui.unit.Dp?,
) {
    val cards = remember { mutableMapOf<Long, @Composable (FleetTask, Modifier) -> Unit>() }
    val live = snapshot.tasks.map { it.id }.toSet()
    cards.keys.retainAll(live)
    LookaheadScope {
        val row = if (columnWidth != null) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()
        Row(row.testTag("fleet_board"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FleetColumn.entries.forEach { column ->
                val colModifier = if (columnWidth != null) Modifier.width(columnWidth) else Modifier.weight(1f)
                BoardColumn(colModifier, column, snapshot.inColumn(column).sortedByDescending { it.id }) { task ->
                    val card =
                        cards.getOrPut(task.id) {
                            movableContentOf { t: FleetTask, m: Modifier -> TaskCard(t, snapshot, m) }
                        }
                    card(task, Modifier.animateBounds(this@LookaheadScope))
                }
            }
        }
    }
}

@Composable
private fun BoardColumn(
    modifier: Modifier,
    column: FleetColumn,
    tasks: List<FleetTask>,
    card: @Composable (FleetTask) -> Unit,
) {
    val warning = LocalHermesStatusColors.current.warning
    val isAsk = column == FleetColumn.NEEDS_YOU
    Surface(
        modifier = modifier.heightIn(min = 180.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border =
            BorderStroke(
                1.dp,
                if (isAsk) warning.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    stringResource(column.label()).uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isAsk) warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${tasks.size}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
            tasks.forEach { task -> androidx.compose.runtime.key(task.id) { card(task) } }
        }
    }
}

private fun FleetColumn.label(): Int =
    when (this) {
        FleetColumn.QUEUED -> R.string.fleet_col_queued
        FleetColumn.WORKING -> R.string.fleet_col_working
        FleetColumn.NEEDS_YOU -> R.string.fleet_col_needs_you
        FleetColumn.DONE -> R.string.fleet_col_done
    }

@Composable
private fun TaskCard(
    task: FleetTask,
    snapshot: FleetSnapshot,
    modifier: Modifier,
) {
    val bot = snapshot.bot(task.owner)
    val hue = bot?.let { FleetPalette.hue(it.hueIndex) } ?: MaterialTheme.colorScheme.primary
    val warning = LocalHermesStatusColors.current.warning
    val done = task.column == FleetColumn.DONE
    Surface(
        modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (done) 0.62f else 1f },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border =
            BorderStroke(
                1.dp,
                if (task.column ==
                    FleetColumn.NEEDS_YOU
                ) {
                    warning.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(hue))
            Column(
                Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(hue))
                    Text(
                        bot?.name ?: task.owner,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    task.route.take(task.hop + 1).forEachIndexed { i, id ->
                        val b = snapshot.bot(id)
                        if (i >
                            0
                        ) {
                            Text(
                                "›",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            b?.initials ?: id,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color =
                                b?.let { FleetPalette.hue(it.hueIndex) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (task.column) {
                    FleetColumn.WORKING -> {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = hue,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            drawStopIndicator = {},
                        )
                    }

                    FleetColumn.NEEDS_YOU -> {
                        task.ask?.let { Text(it.action, style = MaterialTheme.typography.labelSmall, color = warning) }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }
}
