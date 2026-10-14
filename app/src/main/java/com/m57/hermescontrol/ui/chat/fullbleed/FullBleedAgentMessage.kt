package com.m57.hermescontrol.ui.chat.fullbleed

import android.content.ClipData
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.MessageReaction
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.LocalToybox
import com.m57.hermescontrol.theme.ToyFonts
import com.m57.hermescontrol.theme.toySticker
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.ImageViewerModel
import com.m57.hermescontrol.ui.chat.InlineAttachment
import com.m57.hermescontrol.ui.chat.MarkdownText
import com.m57.hermescontrol.ui.chat.MessageReactionsStore
import com.m57.hermescontrol.ui.chat.MessageSegment
import com.m57.hermescontrol.ui.chat.TokenEstimator
import com.m57.hermescontrol.ui.chat.components.MessageReactionChips
import com.m57.hermescontrol.ui.chat.components.ReasoningCard
import com.m57.hermescontrol.ui.chat.components.rememberCopyFeedback
import com.m57.hermescontrol.ui.chat.formatTimestamp
import com.m57.hermescontrol.ui.chat.markdown.streamingSettledLength
import com.m57.hermescontrol.ui.chat.splitByMedia
import kotlinx.coroutines.launch

/**
 * Full-bleed renderer for ONE agent (assistant) message (issue #866).
 *
 * Unlike [com.m57.hermescontrol.ui.chat.UserBubble], agent prose renders
 * directly on the background — no bubble container, no width cap — with a
 * trailing copy affordance. User messages keep their bubbles; this composable
 * is only used for ASSISTANT messages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FullBleedAgentMessage(
    message: ChatMessage,
    searchQuery: String = "",
    isCurrentMatch: Boolean = false,
    reasoningSearchQuery: String = "",
    isCurrentReasoningMatch: Boolean = false,
    reasoningSearchOffset: Int = 0,
    showReasoning: Boolean = true,
    onOpenAttachment: (Attachment) -> Unit = {},
    onSaveAttachment: (Attachment) -> Unit = {},
    savingAttachmentPath: String? = null,
    openingAttachmentPath: String? = null,
    canSaveAttachment: Boolean = true,
    onImageClick: (ImageViewerModel) -> Unit = {},
    isSpeaking: Boolean = false,
    onToggleSpeak: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    reaction: String? = null,
    onReact: ((String?) -> Unit)? = null,
    onDecision: ((Boolean) -> Unit)? = null,
    messageStatsEnabled: Boolean = false,
    showAssistantMessageTokens: Boolean = true,
    showTokensPerSecond: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val toybox = LocalToybox.current
    val textColor = if (toybox) BotsPalette.ToyText else MaterialTheme.colorScheme.onSurface
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Copy feedback: briefly show ✓ then revert
    var copied by rememberCopyFeedback()
    var menuOpen by remember { mutableStateOf(false) }
    val reactionDescription = reaction?.let { stringResource(R.string.content_desc_reaction, it) }.orEmpty()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(if (toybox) Modifier.padding(end = 4.dp, bottom = 4.dp) else Modifier)
                .testTag("fullbleed_agent_message"),
    ) {
        if (showReasoning && message.reasoningText.isNotBlank()) {
            ReasoningCard(
                reasoningText = message.reasoningText,
                isStreaming = message.isStreaming,
                searchQuery = reasoningSearchQuery,
                isCurrentMatch = isCurrentReasoningMatch,
                searchOffset = reasoningSearchOffset,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        val toyShape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp)
        val bubbleModifier =
            if (toybox) {
                // Up to 88% of the chat's width, drawn as a white sticker with a hard shadow.
                Modifier
                    .layout { measurable, constraints ->
                        val max = (constraints.maxWidth * TOY_BUBBLE_WIDTH_FRACTION).toInt()
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = max))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }.toySticker(toyShape, BotsPalette.ToyWhite)
                    .clip(toyShape)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            } else {
                Modifier.fillMaxWidth()
            }
        val toyTypography =
            MaterialTheme.typography.copy(
                bodyMedium =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = if (toybox) toyTypography else MaterialTheme.typography,
        ) {
            Column(modifier = bubbleModifier) {
                // Defense-in-depth: never render an empty prose block (blank bubble +
                // lone Copy button). Blank rows are tool-call placeholders that slipped
                // through upstream mapping; the parent list renders the live status
                // indicator until the first visible delta lands.
                // Issue #1367: agent media renders where its `MEDIA:` directive sat in the
                // prose; attachments without a recorded position trail the text.
                val segments =
                    remember(message.content, message.attachments) {
                        splitByMedia(message.content, message.attachments)
                    }
                segments.forEach { segment ->
                    when (segment) {
                        is MessageSegment.Text -> {
                            SelectionContainer {
                                if (message.isStreaming) {
                                    // While it's written, draw the settled paragraphs once and redraw only
                                    // the growing tail, so long replies don't re-render on every word.
                                    val cut = streamingSettledLength(segment.text)
                                    Column {
                                        if (cut > 0) {
                                            MarkdownText(
                                                text = segment.text.substring(0, cut).trimEnd(),
                                                textColor = textColor,
                                                isStreaming = false,
                                                searchQuery = searchQuery,
                                                isCurrentMatch = isCurrentMatch,
                                                onImageClick = onImageClick,
                                            )
                                        }
                                        MarkdownText(
                                            text = segment.text.substring(cut),
                                            textColor = textColor,
                                            isStreaming = true,
                                            searchQuery = searchQuery,
                                            isCurrentMatch = isCurrentMatch,
                                            onImageClick = onImageClick,
                                        )
                                    }
                                } else {
                                    MarkdownText(
                                        text = segment.text,
                                        textColor = textColor,
                                        isStreaming = false,
                                        searchQuery = searchQuery,
                                        isCurrentMatch = isCurrentMatch,
                                        onImageClick = onImageClick,
                                    )
                                }
                            }
                        }

                        is MessageSegment.Media -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            InlineAttachment(
                                attachment = segment.attachment,
                                textColor = textColor,
                                onOpen = onOpenAttachment,
                                onSave = onSaveAttachment,
                                savingPath = savingAttachmentPath,
                                openingPath = openingAttachmentPath,
                                canSave = canSaveAttachment,
                                onImageClick = onImageClick,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                if (!message.isStreaming) {
                    MessageReactionChips(
                        if (reaction !=
                            null
                        ) {
                            message.reactions + MessageReaction(reaction, "you")
                        } else {
                            message.reactions
                        },
                        Modifier.padding(top = 2.dp),
                    )
                }

                if (!message.isStreaming && message.content.isNotBlank()) {
                    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
                    val showTokenStat =
                        messageStatsEnabled && showAssistantMessageTokens &&
                            message.tokenCount != null && message.tokenCount > 0
                    val showTpsStat =
                        messageStatsEnabled && showTokensPerSecond &&
                            message.tps != null && message.tps > 0.0
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onReply != null) {
                            ActionPill(onClick = onReply, tag = "fullbleed_reply", yellow = true) {
                                PillLabel(stringResource(R.string.chat_action_reply), onYellow = true)
                            }
                        }
                        ActionPill(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, message.content)))
                                }
                                copied = true
                            },
                            tag = "fullbleed_copy",
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(PILL_ICON),
                                tint = pillContent(),
                            )
                            PillLabel(stringResource(R.string.chat_action_copy))
                        }
                        if (onReact != null) {
                            Box {
                                ActionPill(onClick = { menuOpen = true }, tag = "fullbleed_react") {
                                    if (reaction != null) {
                                        Text(
                                            text = reaction,
                                            fontSize = 16.sp,
                                            modifier = Modifier.semantics { contentDescription = reactionDescription },
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.AddReaction,
                                            contentDescription = null,
                                            modifier = Modifier.size(PILL_ICON),
                                            tint = pillContent(),
                                        )
                                        PillLabel(stringResource(R.string.chat_action_react))
                                    }
                                }
                                ReactionMenu(
                                    expanded = menuOpen,
                                    onDismiss = { menuOpen = false },
                                    selected = reaction,
                                    onReact = { emoji ->
                                        menuOpen = false
                                        onReact(if (emoji == reaction) null else emoji)
                                    },
                                    onDecision =
                                        onDecision?.let { decide ->
                                            { approve ->
                                                menuOpen = false
                                                decide(approve)
                                            }
                                        },
                                )
                            }
                        }
                        if (onToggleSpeak != null) {
                            IconButton(
                                onClick = onToggleSpeak,
                                modifier = Modifier.size(PILL_HEIGHT).testTag("fullbleed_speak"),
                            ) {
                                Icon(
                                    imageVector =
                                        if (isSpeaking) {
                                            Icons.Filled.Stop
                                        } else {
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                    contentDescription =
                                        stringResource(
                                            if (isSpeaking) {
                                                R.string.content_desc_stop_speaking
                                            } else {
                                                R.string.content_desc_speak
                                            },
                                        ),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (showTokenStat) {
                            AssistantStatItem(
                                value =
                                    stringResource(
                                        R.string.chat_msg_tokens,
                                        TokenEstimator.formatTokenCount(message.tokenCount),
                                    ),
                                testTag = "fullbleed_token_count",
                            )
                        }
                        if (toybox) {
                            Text(
                                text = formatTimestamp(message.timestamp, is24Hour),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = BotsPalette.ToyMutedText,
                                modifier = Modifier.testTag("fullbleed_timestamp"),
                            )
                        }
                        if (showTpsStat) {
                            AssistantStatItem(
                                value =
                                    stringResource(
                                        R.string.chat_msg_tps,
                                        TokenEstimator.formatTps(message.tps),
                                    ),
                                testTag = "fullbleed_tps",
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reaction picker for a bot message: a row of personal emoji (the chosen one is highlighted), then
 * Approve and Reject when [onDecision] is set. Drawn as a sticker card in Toybox.
 */
@Composable
private fun ReactionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selected: String?,
    onReact: (String) -> Unit,
    onDecision: ((Boolean) -> Unit)?,
) {
    val toybox = LocalToybox.current
    val shape: Shape = RoundedCornerShape(if (toybox) 18.dp else 12.dp)
    val textColor = if (toybox) BotsPalette.ToyText else MaterialTheme.colorScheme.onSurface
    val highlight = if (toybox) BotsPalette.ToyAccent else MaterialTheme.colorScheme.primary
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("fullbleed_react_menu"),
        shape = shape,
        containerColor = if (toybox) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (toybox) 0.dp else 3.dp,
        shadowElevation = if (toybox) 0.dp else 6.dp,
    ) {
        Column(
            modifier =
                if (toybox) {
                    Modifier.padding(end = 4.dp, bottom = 4.dp).toySticker(shape, BotsPalette.ToyWhite).padding(4.dp)
                } else {
                    Modifier
                },
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MessageReactionsStore.PERSONAL.forEach { emoji ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (emoji == selected) {
                                        Modifier.background(highlight.copy(alpha = 0.2f))
                                    } else {
                                        Modifier
                                    },
                                ).clickable { onReact(emoji) }
                                .testTag("fullbleed_react_$emoji"),
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }
            if (onDecision != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_reaction_approve), color = textColor) },
                    onClick = { onDecision(true) },
                    modifier = Modifier.testTag("fullbleed_approve"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_reaction_reject), color = textColor) },
                    onClick = { onDecision(false) },
                    modifier = Modifier.testTag("fullbleed_reject"),
                )
            }
        }
    }
}

/** Height of the action pills under a bot message: compact, still an easy thumb target. */
private val PILL_HEIGHT = 36.dp
private val PILL_ICON = 16.dp

/** Labelled action pill under a bot message; a yellow sticker in Toybox, a tonal surface otherwise. */
@Composable
private fun ActionPill(
    onClick: () -> Unit,
    tag: String,
    yellow: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val toybox = LocalToybox.current
    val shape = RoundedCornerShape(if (toybox) 24.dp else 20.dp)
    val surface =
        if (toybox) {
            Modifier.padding(end = 3.dp, bottom = 3.dp).toySticker(
                shape,
                if (yellow) BotsPalette.ToyYellow else BotsPalette.ToyWhite,
                depth = 3.dp,
                outline = 2.dp,
            )
        } else {
            Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
        }
    Row(
        modifier =
            Modifier
                .then(surface)
                .clip(shape)
                .clickable(onClick = onClick)
                .heightIn(min = PILL_HEIGHT)
                .padding(horizontal = 12.dp)
                .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun pillContent(onYellow: Boolean = false): Color =
    when {
        LocalToybox.current && onYellow -> BotsPalette.ToyOnYellow
        LocalToybox.current -> BotsPalette.ToyText
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun PillLabel(
    text: String,
    onYellow: Boolean = false,
) {
    val toybox = LocalToybox.current
    Text(
        text = text,
        style =
            if (toybox) {
                MaterialTheme.typography.labelMedium.copy(fontFamily = ToyFonts.Body, fontWeight = FontWeight.ExtraBold)
            } else {
                MaterialTheme.typography.labelMedium
            },
        color = pillContent(onYellow),
        maxLines = 1,
    )
}

@Composable
private fun AssistantStatItem(
    value: String,
    testTag: String,
) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(testTag),
    )
}

private const val TOY_BUBBLE_WIDTH_FRACTION = 0.88f
