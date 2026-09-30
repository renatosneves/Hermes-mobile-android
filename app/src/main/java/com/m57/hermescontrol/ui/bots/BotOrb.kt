package com.m57.hermescontrol.ui.bots

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.ui.common.resolveAvatarShape

enum class OrbPresence { NONE, RECENT, IDLE }

/**
 * A bot's avatar on the Bots home: a lit orb in the bot's hue with its initials, a spinning
 * arc while it works, a pulsing amber ring when it needs you, and an optional presence dot.
 */
@Composable
fun BotOrb(
    initials: String,
    hue: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    working: Boolean = false,
    presence: OrbPresence = OrbPresence.NONE,
    team: Boolean = false,
    shapeKey: String? = null,
    imageUrl: String? = null,
    attention: Boolean = false,
) {
    val shape = remember(shapeKey, size) { resolveAvatarShape(shapeKey, size) }
    val pulse =
        if (attention) {
            val transition = rememberInfiniteTransition(label = "orb-attention")
            val value by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "orb-attention-alpha",
            )
            value
        } else {
            0f
        }
    val spin =
        if (working) {
            val transition = rememberInfiniteTransition(label = "orb-spin")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                label = "orb-angle",
            )
            angle
        } else {
            0f
        }

    Box(modifier = modifier.size(size + 10.dp).testTag("bot_orb"), contentAlignment = Alignment.Center) {
        // Soft hue shadow under the orb.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val orbRadius = (size.toPx()) / 2f
            val c = center
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(hue.copy(alpha = 0.35f), Color.Transparent),
                        center = c + Offset(0f, orbRadius * 0.35f),
                        radius = orbRadius * 1.25f,
                    ),
                radius = orbRadius * 1.25f,
                center = c + Offset(0f, orbRadius * 0.35f),
            )
        }
        // The orb body, clipped to the bot's chosen shape.
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(shape)
                    .drawBehind {
                        val r = this.size.minDimension / 2f
                        if (team) {
                            drawRect(Brush.sweepGradient(BotsPalette.Team, center = center))
                        } else {
                            drawRect(
                                Brush.radialGradient(
                                    0f to lerp(hue, BotsPalette.Fg, 0.7f),
                                    0.38f to hue,
                                    1f to lerp(hue, BotsPalette.Ink, 0.55f),
                                    center = Offset(center.x - r * 0.36f, center.y - r * 0.44f),
                                    radius = r * 1.7f,
                                ),
                            )
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (team) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = BotsPalette.Ink,
                    modifier = Modifier.size(size * 0.5f),
                )
            } else {
                val fontSize = with(LocalDensity.current) { (size * 0.34f).toSp() }
                Text(
                    text = initials,
                    color = BotsPalette.Ink,
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                )
            }
        }
        // Working ring and presence dot sit above the body.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val orbRadius = (size.toPx()) / 2f
            val c = center
            if (attention) {
                drawCircle(
                    color = BotsPalette.Attention.copy(alpha = pulse),
                    radius = orbRadius + 3.dp.toPx(),
                    center = c,
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else if (working) {
                val ringRadius = orbRadius + 4.dp.toPx()
                val stroke = 2.dp.toPx()
                rotate(spin, pivot = c) {
                    drawArc(
                        brush =
                            Brush.sweepGradient(
                                0f to Color.Transparent,
                                0.35f to hue.copy(alpha = 0.4f),
                                0.5f to hue,
                                1f to Color.Transparent,
                                center = c,
                            ),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(c.x - ringRadius, c.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = stroke),
                    )
                }
            }
            if (presence != OrbPresence.NONE) {
                val dotRadius = (size.toPx() * 0.14f).coerceAtLeast(4.dp.toPx())
                val dotCenter = Offset(c.x + orbRadius * 0.72f, c.y + orbRadius * 0.72f)
                drawCircle(BotsPalette.Rail, radius = dotRadius + 2.dp.toPx(), center = dotCenter)
                drawCircle(
                    if (presence == OrbPresence.RECENT) BotsPalette.Ok else BotsPalette.Idle,
                    radius = dotRadius,
                    center = dotCenter,
                )
            }
        }
    }
}
