package com.m57.hermescontrol.ui.chat.fullbleed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.LocalToybox
import com.m57.hermescontrol.theme.toySticker
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.SystemBubble
import com.m57.hermescontrol.ui.chat.ToolBubble
import com.m57.hermescontrol.ui.chat.ToolStatus
import com.m57.hermescontrol.ui.chat.isToolRunning

/**
 * Tool-row treatment inside the full-bleed renderer (issue #866).
 *
 * ToolBubble is ALREADY a compact dimmed card (surfaceContainerHigh, 8dp
 * radius, collapsed summary with expand/copy/raw-json/risk-chip) — distinct
 * from agent prose by design. In full-bleed mode we reuse it verbatim so the
 * verified tool-rendering logic is never duplicated, adding only a 16dp
 * start indent so tool rows align with the full-bleed prose margin.
 */
@Composable
internal fun FullBleedToolRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    isCurrentMatch: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
                .testTag("fullbleed_tool_row"),
    ) {
        ToolBubble(message, searchQuery = searchQuery, isCurrentMatch = isCurrentMatch)
    }
}

/**
 * System-event treatment inside the full-bleed renderer (issue #866).
 *
 * Reuses [SystemBubble] verbatim — it already renders as a centered, dimmed,
 * italic caption (with approval buttons and the Self-improvement review card
 * handling intact), which keeps system events visually distinct from prose.
 */
@Composable
internal fun FullBleedSystemEvent(
    message: ChatMessage,
    onRespondApproval: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SystemBubble(
        message = message,
        onRespondApproval = onRespondApproval,
        modifier = modifier.testTag("fullbleed_system_event"),
    )
}

/**
 * One line standing for all of a turn's tool steps, ChatGPT/Claude style: while the bot works it
 * names the step under way, afterwards it counts them; tapping it shows or hides the steps.
 */
@Composable
internal fun ToolStepsSummary(
    steps: List<ChatMessage>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = steps.lastOrNull { it.isToolRunning }
    val failed = steps.count { it.toolStatus == ToolStatus.FAILED }
    val toybox = LocalToybox.current
    val night = toybox && BotsPalette.isToyNight
    val muted =
        when {
            night -> BotsPalette.ToyStepText
            toybox -> BotsPalette.ToyOutline
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val toyShape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .then(
                    if (toybox) {
                        // A yellow tag with a dashed outline (a yellow outline at night).
                        Modifier
                            .toySticker(
                                toyShape,
                                BotsPalette.ToyStep,
                                shadow = null,
                                outlineColor = if (night) BotsPalette.ToyYellow else BotsPalette.ToyOutline,
                                dashed = true,
                            ).clip(toyShape)
                            .clickable(onClick = onToggle)
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggle)
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 8.dp)
                    },
                ).testTag("tool_steps_summary"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (running != null) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = muted)
        } else if (toybox) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Filled.Build, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
        }
        val text =
            buildString {
                append(pluralStringResource(R.plurals.tool_steps_count, steps.size, steps.size))
                if (running != null) {
                    append(" · ")
                    append(stringResource(R.string.tool_steps_running, running.toolName ?: "…"))
                }
                if (failed > 0) {
                    append(" · ")
                    append(pluralStringResource(R.plurals.tool_steps_failed, failed, failed))
                }
            }
        Text(
            text = text,
            style =
                if (toybox) {
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                } else {
                    MaterialTheme.typography.labelLarge
                },
            color = if (failed > 0) MaterialTheme.colorScheme.error else muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector =
                when {
                    toybox && !expanded -> Icons.Filled.ChevronRight
                    expanded -> Icons.Filled.ExpandLess
                    else -> Icons.Filled.ExpandMore
                },
            contentDescription =
                stringResource(if (expanded) R.string.tool_steps_hide else R.string.tool_steps_show),
            tint = muted,
            modifier = Modifier.size(18.dp),
        )
    }
}
