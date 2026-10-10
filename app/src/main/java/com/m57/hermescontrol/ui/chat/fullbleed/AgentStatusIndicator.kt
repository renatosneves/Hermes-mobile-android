package com.m57.hermescontrol.ui.chat.fullbleed

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.LocalToybox
import com.m57.hermescontrol.theme.toySticker
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.StreamingState
import com.m57.hermescontrol.ui.chat.ToolSchemaRegistry
import com.m57.hermescontrol.ui.chat.components.TypingIndicator
import com.m57.hermescontrol.ui.chat.isToolRunning
import com.m57.hermescontrol.ui.chat.isUserTurnBoundary

/** Live status for an agent turn before visible assistant prose is available. */
internal sealed interface AgentStatus {
    data object Typing : AgentStatus

    data object Thinking : AgentStatus

    data class Tool(
        val name: String?,
    ) : AgentStatus
}

/**
 * Derives the one status that should occupy the agent turn's live tail.
 *
 * Tool execution wins over generic states, and visible streaming prose wins
 * over waiting/reasoning states. This keeps one indicator on screen at a time
 * without introducing a second generation state store.
 */
internal fun deriveAgentStatus(
    isAgentTyping: Boolean,
    streamingState: StreamingState,
    messages: List<ChatMessage>,
): AgentStatus? {
    if (!isAgentTyping) return null

    val currentTurnStart = messages.indexOfLast { it.isUserTurnBoundary() }
    val currentTurnMessages =
        if (currentTurnStart >= 0) {
            messages.drop(currentTurnStart + 1)
        } else {
            emptyList()
        }
    val activeTool =
        currentTurnMessages
            .asReversed()
            .firstOrNull {
                it.role == MessageRole.TOOL &&
                    it.isToolRunning
            }
    val hasVisibleStreamingContent = streamingState.streamingMessage?.content?.isNotBlank() == true

    return when {
        activeTool != null -> AgentStatus.Tool(activeTool.toolName)
        hasVisibleStreamingContent -> null
        streamingState.isThinking || streamingState.isReasoning -> AgentStatus.Thinking
        isAgentTyping -> AgentStatus.Typing
        else -> null
    }
}

/** Maps existing canonical tool names to compact, user-facing activity text. */
internal fun toolStatusLabelRes(toolName: String?): Int =
    when (toolName?.lowercase()) {
        "web_search",
        "search_files",
        "session_search",
        "tool_search",
        "x_search",
        -> R.string.chat_agent_status_searching

        "read_file",
        -> R.string.chat_agent_status_reading

        "web_extract",
        "browser_navigate",
        "browser_click",
        "browser_type",
        "browser_scroll",
        "browser_back",
        "browser_press",
        "browser_snapshot",
        "browser_get_images",
        "browser_console",
        "browser_cdp",
        "browser_dialog",
        "browser_vision",
        -> R.string.chat_agent_status_browsing

        "terminal",
        "execute_code",
        "process",
        "process_manage",
        -> R.string.chat_agent_status_running_command

        else -> R.string.chat_agent_status_using_tool
    }

@Composable
internal fun AgentStatusIndicator(
    status: AgentStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        AgentStatus.Typing -> {
            if (LocalToybox.current) {
                ToyStatusPill(text = null, icon = null, modifier = modifier.testTag("typing_indicator"))
            } else {
                TypingIndicator(modifier = modifier)
            }
        }

        AgentStatus.Thinking -> {
            StatusText(
                text = stringResource(R.string.chat_agent_status_thinking),
                icon = null,
                modifier = modifier.testTag("agent_status_thinking"),
            )
        }

        is AgentStatus.Tool -> {
            val config = ToolSchemaRegistry.getDisplayConfig(status.name)
            val labelRes = toolStatusLabelRes(status.name)
            StatusText(
                text = stringResource(labelRes),
                icon = config.icon,
                modifier = modifier.testTag("agent_status_tool"),
            )
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    if (LocalToybox.current) {
        ToyStatusPill(text = text, icon = icon, modifier = modifier)
        return
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Toybox status: a white sticker pill with three coloured dots that bounce (when animations are
 * on), then the status text if there is one.
 */
@Composable
private fun ToyStatusPill(
    text: String?,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.chat_agent_status_typing)
    val shape = RoundedCornerShape(20.dp)
    val context = LocalContext.current
    val animate =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
    val transition = rememberInfiniteTransition(label = "toy_status_dots")
    val colors = listOf(BotsPalette.ToyTalk, BotsPalette.ToyYellow, BotsPalette.ToyAccent)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .toySticker(shape, BotsPalette.ToyWhite)
                    .clip(shape)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics { if (text == null) contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            colors.forEachIndexed { i, color ->
                val bounce =
                    if (animate) {
                        transition
                            .animateFloat(
                                initialValue = 0f,
                                targetValue = -5f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation = tween(350, delayMillis = i * 120, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse,
                                    ),
                                label = "toy_dot_$i",
                            ).value
                    } else {
                        0f
                    }
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .graphicsLayer { translationY = bounce.dp.toPx() }
                            .clip(CircleShape)
                            .background(color),
                )
            }
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = BotsPalette.ToyOutline)
            }
            if (text != null) {
                Text(
                    text = text,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        ),
                    color = BotsPalette.ToyOutline,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
