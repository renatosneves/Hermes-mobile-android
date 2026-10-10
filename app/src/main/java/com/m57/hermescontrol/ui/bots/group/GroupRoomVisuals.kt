package com.m57.hermescontrol.ui.bots.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.BotAvatarMeta
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.parseHexColor
import com.m57.hermescontrol.ui.bots.BotOrb
import com.m57.hermescontrol.ui.bots.BotsPresentation
import com.m57.hermescontrol.ui.bots.GroupPictureStore

/** Accent for group rooms: the lavender the Bots home opens with. */
internal val RoomHue: Color get() = BotsPalette.Hues[0]

/** A bot's hue in a room: its chosen colour, else the same default the Bots list gives it. */
internal fun roomHue(
    name: String,
    avatar: BotAvatarMeta?,
): Color =
    parseHexColor(
        avatar?.color?.takeIf { it.isNotBlank() } ?: BotsPresentation.defaultHueHex(name),
        BotsPalette.Hues[BotsPresentation.hueIndex(name, BotsPalette.Hues.size)],
    )

/** One row of the room: a message, or a run of bots that passed shown as a single line. */
internal sealed interface RoomItem {
    val key: String

    data class Message(
        val message: GroupChatMessage,
    ) : RoomItem {
        override val key: String get() = message.id
    }

    data class Passes(
        val messages: List<GroupChatMessage>,
    ) : RoomItem {
        override val key: String get() = "passes_${messages.first().id}"
    }
}

/** Folds consecutive "passed" notes into one row so a quiet room doesn't fill the screen. */
internal fun roomItems(messages: List<GroupChatMessage>): List<RoomItem> {
    val items = mutableListOf<RoomItem>()
    val run = mutableListOf<GroupChatMessage>()

    fun flush() {
        if (run.size == 1) items.add(RoomItem.Message(run.single()))
        if (run.size > 1) items.add(RoomItem.Passes(run.toList()))
        run.clear()
    }
    for (message in messages) {
        if (message.isSystem && message.isPass) {
            run.add(message)
        } else {
            flush()
            items.add(RoomItem.Message(message))
        }
    }
    flush()
    return items
}

/** Title for a room: team orb, name, and the members as a strip of small orbs. */
@Composable
internal fun RoomTitle(
    name: String,
    members: List<ProfileInfo>,
    activeSpeaker: String?,
    avatars: Map<String, String>,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("group_room_title")) {
        BotOrb(
            initials = "",
            hue = RoomHue,
            size = 34.dp,
            team = true,
            working = activeSpeaker != null,
            imageUrl = GroupPictureStore.get(name),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = name,
                color = BotsPalette.Fg,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (members.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrbStrip(members = members, avatars = avatars, activeSpeaker = activeSpeaker, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.group_chat_members_count, members.size),
                        color = BotsPalette.Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Overlapping small orbs; the one speaking spins. */
@Composable
internal fun OrbStrip(
    members: List<ProfileInfo>,
    avatars: Map<String, String>,
    activeSpeaker: String?,
    size: Dp,
    max: Int = 8,
) {
    val shown = members.take(max)
    // BotOrb reserves 5 dp around itself for its ring; overlap past that too.
    Row(
        horizontalArrangement = Arrangement.spacedBy(-(10.dp + size * 0.3f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { member ->
            BotOrb(
                initials = BotsPresentation.initials(member.effectiveTitle),
                hue = roomHue(member.name, member.botMeta()?.avatar),
                size = size,
                working = member.effectiveTitle == activeSpeaker,
                shapeKey = member.botMeta()?.avatar?.shape,
                imageUrl = avatars[member.name],
            )
        }
        if (members.size > max) {
            Text(
                text = "+${members.size - max}",
                color = BotsPalette.Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 10.dp + size * 0.3f + 2.dp),
            )
        }
    }
}

/** "8 passed" with the bots that passed as tiny orbs. */
@Composable
internal fun PassesRow(
    passes: List<GroupChatMessage>,
    membersByName: Map<String, ProfileInfo>,
    avatars: Map<String, String>,
) {
    val names = passes.map { it.senderDisplayName.ifBlank { it.senderName } }
    val shape = RoundedCornerShape(999.dp)
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier =
                Modifier
                    .clip(shape)
                    .background(BotsPalette.Deck.copy(alpha = 0.7f))
                    .border(1.dp, BotsPalette.Line, shape)
                    .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)
                    .testTag("group_passes_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val passers = passes.map { membersByName[it.senderName] ?: ProfileInfo(name = it.senderName) }
            OrbStrip(members = passers, avatars = avatars, activeSpeaker = null, size = 14.dp, max = 6)
            Icon(
                Icons.Filled.FastForward,
                contentDescription = null,
                tint = BotsPalette.Faint,
                modifier = Modifier.padding(start = 2.dp).width(12.dp),
            )
            Text(
                text = stringResource(R.string.group_chat_passes, passes.size, names.joinToString(", ")),
                color = BotsPalette.Muted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Spend is thinking…" with the speaker's orb spinning. */
@Composable
internal fun ThinkingRow(
    speaker: String,
    member: ProfileInfo?,
    avatars: Map<String, String>,
) {
    val hue = member?.let { roomHue(it.name, it.botMeta()?.avatar) } ?: RoomHue
    Row(
        modifier = Modifier.padding(start = 2.dp, top = 4.dp).testTag("group_thinking_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotOrb(
            initials = BotsPresentation.initials(speaker),
            hue = hue,
            size = 26.dp,
            working = true,
            shapeKey = member?.botMeta()?.avatar?.shape,
            imageUrl = member?.let { avatars[it.name] },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.group_chat_thinking, speaker),
            color = lerp(hue, BotsPalette.Fg, 0.25f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
