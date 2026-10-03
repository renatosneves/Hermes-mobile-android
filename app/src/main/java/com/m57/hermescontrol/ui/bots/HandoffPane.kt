package com.m57.hermescontrol.ui.bots

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.ui.chat.MarkdownText
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolBubble

/** How a hand-off between bots shows on the Bots home. */
enum class HandoffMode { SPLIT, STRIP, OFF }

/** How long a finished hand-off stays on show before it closes itself. */
internal const val HANDOFF_CLOSE_DELAY_MS = 4_000L

/** Who the hand-off pane is showing, resolved from the roster. */
internal data class HandoffBot(
    val title: String,
    val hue: Color,
    val initials: String,
    val shapeKey: String?,
    val imageUrl: String?,
)

/**
 * The other bot's run, read-only: the brief it got, its replies and its tool steps as each one
 * finishes. Counts down to closing once it's done, unless pinned.
 */
@Composable
internal fun HandoffPane(
    view: HandoffView,
    botFor: (String) -> HandoffBot,
    onSelect: (String) -> Unit,
    sourceTitle: String,
    onClose: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenChat: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state = view.selected
    val bot = botFor(state.target)
    Column(modifier = modifier.background(BotsPalette.Rail).testTag("handoff_pane")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            BotOrb(
                initials = bot.initials,
                hue = bot.hue,
                size = 32.dp,
                working = !state.done,
                shapeKey = bot.shapeKey,
                imageUrl = bot.imageUrl,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(bot.title, color = BotsPalette.Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    stringResource(R.string.handoff_task_from, sourceTitle),
                    color = BotsPalette.Muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HandoffPill(done = state.done)
            IconButton(onClick = onTogglePin, modifier = Modifier.testTag("handoff_pin")) {
                Icon(
                    if (view.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription =
                        stringResource(
                            if (view.pinned) R.string.handoff_unpin else R.string.handoff_pin,
                        ),
                    tint = if (view.pinned) BotsPalette.You else BotsPalette.Muted,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("handoff_close")) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.handoff_close),
                    tint = BotsPalette.Muted,
                )
            }
        }
        CloseCountdown(view)
        if (view.items.size > 1) {
            // Several bots at once (Chief of Staff delegating): one tab each.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("handoff_tabs"),
            ) {
                for (item in view.items) {
                    val tabBot = botFor(item.target)
                    val chosen = item.key == state.key
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (chosen) BotsPalette.Deck2 else BotsPalette.Rail)
                                .border(1.dp, if (chosen) tabBot.hue else BotsPalette.Line, RoundedCornerShape(50))
                                .clickable { onSelect(item.key) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (item.done) BotsPalette.Ok else tabBot.hue),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tabBot.title,
                            color = if (chosen) BotsPalette.Fg else BotsPalette.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(state.key, state.messages.size) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
        }
        if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val waiting =
                    if (state.kind == HandoffKind.BOARD) R.string.handoff_waiting_board else R.string.handoff_waiting
                Text(
                    stringResource(waiting, bot.title),
                    color = BotsPalette.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    when (message.role) {
                        MessageRole.TOOL -> {
                            ToolBubble(message = message)
                        }

                        MessageRole.USER -> {
                            Text(
                                text = message.content,
                                color = BotsPalette.Muted,
                                fontSize = 13.sp,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(BotsPalette.Deck3)
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }

                        else -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BotsPalette.Deck)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                MarkdownText(text = message.content, textColor = BotsPalette.Fg)
                            }
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, BotsPalette.Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.handoff_footer, bot.title, sourceTitle),
                color = BotsPalette.Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (onOpenChat != null) {
                TextButton(onClick = onOpenChat, modifier = Modifier.testTag("handoff_open_chat")) {
                    Text(stringResource(R.string.handoff_open_chat), color = BotsPalette.Fg, fontSize = 12.sp)
                }
            }
        }
    }
}

/** A thin bar that drains while a finished hand-off waits to close. */
@Composable
private fun CloseCountdown(view: HandoffView) {
    val counting = view.settled && !view.pinned
    val progress = remember(view.key) { Animatable(0f) }
    LaunchedEffect(counting) {
        if (counting) {
            progress.snapTo(1f)
            progress.animateTo(0f, tween(HANDOFF_CLOSE_DELAY_MS.toInt(), easing = LinearEasing))
        } else {
            progress.snapTo(0f)
        }
    }
    Box(Modifier.fillMaxWidth().height(3.dp).background(BotsPalette.Line)) {
        Box(Modifier.fillMaxWidth(progress.value).fillMaxHeight().background(BotsPalette.Ok))
    }
}

@Composable
private fun HandoffPill(done: Boolean) {
    val color = if (done) BotsPalette.Ok else BotsPalette.Attention
    Text(
        text = stringResource(if (done) R.string.handoff_done else R.string.handoff_live).uppercase(),
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The slim line above the chat on the cover screen, or when the side pane is closed. */
@Composable
internal fun HandoffStrip(
    view: HandoffView,
    botFor: (String) -> HandoffBot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = view.selected
    val bot = botFor(state.target)
    val names =
        view.items
            .map { botFor(it.target).title }
            .distinct()
            .joinToString(", ")
    val last = state.messages.lastOrNull { it.role != MessageRole.USER }
    val line =
        when {
            view.done -> {
                stringResource(R.string.handoff_finished_line)
            }

            last == null -> {
                stringResource(R.string.handoff_starting)
            }

            last.role == MessageRole.TOOL -> {
                last.toolName ?: stringResource(R.string.handoff_step)
            }

            else -> {
                last.content
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
            }
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BotsPalette.Deck)
                .border(1.dp, bot.hue.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("handoff_strip"),
    ) {
        BotOrb(
            initials = bot.initials,
            hue = bot.hue,
            size = 26.dp,
            working = !view.done,
            shapeKey = bot.shapeKey,
            imageUrl = bot.imageUrl,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (view.done) R.string.handoff_finished else R.string.handoff_working, names),
                color = BotsPalette.Fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(line, color = BotsPalette.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        HandoffPill(done = view.done)
    }
}

/** Picks how hand-offs show: side by side, strip only, or not at all. */
@Composable
internal fun HandoffModeDialog(
    current: HandoffMode,
    onPick: (HandoffMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val options =
        listOf(
            HandoffMode.SPLIT to (R.string.handoff_mode_split to R.string.handoff_mode_split_desc),
            HandoffMode.STRIP to (R.string.handoff_mode_strip to R.string.handoff_mode_strip_desc),
            HandoffMode.OFF to (R.string.handoff_mode_off to R.string.handoff_mode_off_desc),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.handoff_view_setting)) },
        text = {
            Column {
                for ((mode, labels) in options) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = mode == current, role = Role.RadioButton) { onPick(mode) }
                                .padding(vertical = 6.dp)
                                .testTag("handoff_mode_${mode.name.lowercase()}"),
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(labels.first))
                            Text(stringResource(labels.second), fontSize = 12.sp, color = BotsPalette.Muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.handoff_done_button)) } },
    )
}

/** The hand-off view choice, kept between launches. Side by side by default. */
internal object HandoffPrefs {
    private const val PREFS = "bots_layout"
    private const val KEY_MODE = "handoff_view"

    fun mode(context: android.content.Context): HandoffMode =
        context
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
            ?.let { saved -> HandoffMode.entries.firstOrNull { it.name == saved } }
            ?: HandoffMode.SPLIT

    fun setMode(
        context: android.content.Context,
        mode: HandoffMode,
    ) {
        context
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}

/** The full-height pane as a sheet, for the cover screen and strip-only mode. */
@Composable
internal fun HandoffSheetContent(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) { content() }
}
