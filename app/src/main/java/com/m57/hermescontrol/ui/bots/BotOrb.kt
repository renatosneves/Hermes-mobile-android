package com.m57.hermescontrol.ui.bots

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.LocalToybox
import com.m57.hermescontrol.theme.ToyFonts
import com.m57.hermescontrol.theme.toySticker
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
    selected: Boolean = false,
    tilt: Float = 0f,
) {
    if (LocalToybox.current) {
        ToyTile(initials, hue, modifier, size, working, presence, team, imageUrl, attention, selected, tilt)
        return
    }
    val shape = remember(shapeKey, size) { resolveAvatarShape(shapeKey, size) }
    // Animated values are read only while drawing, so a spinning ring redraws without recomposing.
    val motion = DecorativeMotion.enabled
    val pulse: State<Float>? =
        if (attention && motion) {
            rememberInfiniteTransition(label = "orb-attention").animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "orb-attention-alpha",
            )
        } else {
            null
        }
    val spin: State<Float>? =
        if (working && motion) {
            rememberInfiniteTransition(label = "orb-spin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                label = "orb-angle",
            )
        } else {
            null
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
                                    0f to lerp(hue, BotsPalette.Highlight, 0.7f),
                                    0.38f to hue,
                                    1f to lerp(hue, BotsPalette.OnHue, 0.55f),
                                    center = Offset(center.x - r * 0.36f, center.y - r * 0.44f),
                                    radius = r * 1.7f,
                                ),
                            )
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            // A picture is drawn above the body (below), so it can spill past the orb.
            if (!imageUrl.isNullOrBlank()) {
                Unit
            } else if (team) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = BotsPalette.OnHue,
                    modifier = Modifier.size(size * 0.5f),
                )
            } else {
                val fontSize = with(LocalDensity.current) { (size * 0.34f).toSp() }
                Text(
                    text = initials,
                    color = BotsPalette.OnHue,
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                )
            }
        }
        if (!imageUrl.isNullOrBlank()) {
            // A picture on a plain or see-through background is shown whole (Fit, never zoomed or
            // clipped, so nothing is cut); any other fills the orb shape.
            val request = rememberAvatarRequest(imageUrl)
            var breakout by remember(imageUrl) { mutableStateOf(false) }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = if (breakout) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { state -> breakout = hasClearCorners(state.result.image.toBitmap()) },
                modifier = if (breakout) Modifier.size(size) else Modifier.size(size).clip(shape),
            )
        }
        // Working ring and presence dot sit above the body.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val orbRadius = (size.toPx()) / 2f
            val c = center
            if (attention) {
                drawCircle(
                    color = BotsPalette.Attention.copy(alpha = pulse?.value ?: 1f),
                    radius = orbRadius + 3.dp.toPx(),
                    center = c,
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else if (working) {
                val ringRadius = orbRadius + 4.dp.toPx()
                val stroke = 2.dp.toPx()
                rotate(spin?.value ?: 0f, pivot = c) {
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

@Composable
private fun rememberAvatarRequest(imageUrl: String): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(imageUrl) {
        ImageRequest
            .Builder(context)
            .data(imageUrl)
            .transformations(AvatarCutoutTransformation())
            .allowHardware(false)
            .build()
    }
}

/**
 * Toybox avatar: a pastel sticker tile (outline, hard shadow) holding the whole picture, fitted
 * and clipped to the inner corners so nothing is cut. Without a picture it shows the initials.
 */
@Composable
private fun ToyTile(
    initials: String,
    hue: Color,
    modifier: Modifier,
    size: Dp,
    working: Boolean,
    presence: OrbPresence,
    team: Boolean,
    imageUrl: String?,
    attention: Boolean,
    selected: Boolean,
    tilt: Float,
) {
    val corner = size * 0.25f
    val tileShape = RoundedCornerShape(corner)
    val inner = size * 0.075f
    // Small tiles (member strips, thinking rows) get a lighter outline and shadow.
    val line = (size * 0.06f).coerceIn(1.5.dp, 3.dp)
    val depth = (size * 0.07f).coerceIn(2.dp, 4.dp)
    val innerShape = RoundedCornerShape((corner - inner).coerceAtLeast(2.dp))
    val pulse: State<Float>? =
        if (attention && DecorativeMotion.enabled) {
            rememberInfiniteTransition(label = "tile-attention").animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "tile-attention-alpha",
            )
        } else {
            null
        }
    Box(modifier = modifier.size(size + 10.dp).testTag("bot_orb"), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(size).graphicsLayer { rotationZ = tilt }) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .toySticker(
                            shape = tileShape,
                            fill = BotsPalette.toyTile(hue),
                            shadow = if (selected) BotsPalette.ToyAccent else BotsPalette.ToyOutline,
                            depth = depth,
                            outline = line,
                        ).padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = rememberAvatarRequest(imageUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(innerShape),
                    )
                } else if (team) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = BotsPalette.ToyOutline,
                        modifier = Modifier.size(size * 0.5f),
                    )
                } else {
                    val fontSize = with(LocalDensity.current) { (size * 0.36f).toSp() }
                    Text(
                        text = initials,
                        color = BotsPalette.ToyOutline,
                        fontSize = fontSize,
                        fontFamily = ToyFonts.Display,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
            if (attention) {
                Box(
                    modifier =
                        Modifier
                            .requiredSize(size + 6.dp)
                            .align(Alignment.Center)
                            .border(
                                2.dp,
                                BotsPalette.Attention.copy(alpha = pulse?.value ?: 1f),
                                RoundedCornerShape(corner + 3.dp),
                            ),
                )
            }
        }
        if (working && size >= 40.dp) {
            Text(
                text = stringResource(R.string.bots_busy_tag),
                color = BotsPalette.ToyOutline,
                fontSize = 9.sp,
                fontFamily = ToyFonts.Display,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .graphicsLayer { rotationZ = 6f }
                        .toySticker(RoundedCornerShape(50), BotsPalette.ToyYellow, shadow = null, outline = 1.5.dp)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (presence != OrbPresence.NONE) {
            val dot = (size * 0.26f).coerceAtLeast(10.dp)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(dot + line * 2)
                        .background(
                            if (presence == OrbPresence.RECENT) BotsPalette.Ok else BotsPalette.Idle,
                            CircleShape,
                        ).border(line, BotsPalette.ToyOutline, CircleShape),
            )
        }
    }
}

private fun hasClearCorners(bitmap: android.graphics.Bitmap): Boolean {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 2 || h < 2) return false
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    return AvatarCutout.hasClearCorners(px, w, h)
}
