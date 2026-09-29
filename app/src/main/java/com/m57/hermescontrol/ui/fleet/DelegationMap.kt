package com.m57.hermescontrol.ui.fleet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.m57.hermescontrol.theme.FleetPalette
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val MAP_W = 400f
private const val MAP_H = 440f
private const val CX = 200f
private const val CY = 240f
private const val ORBIT = 150f
private const val ARC_START_DEG = 40f
private const val ARC_SPAN_DEG = 280f

/** Node positions on a 400 × 440 design grid: you at the top, the router below, the hub in the middle. */
internal fun mapPositions(bots: List<FleetBot>): Map<String, Offset> {
    val result = mutableMapOf(FLEET_YOU_ID to Offset(CX, 32f), FLEET_ROUTER_ID to Offset(CX, 112f))
    val hub = bots.firstOrNull { it.isHub }
    if (hub != null) result[hub.id] = Offset(CX, CY)
    val ring = bots.filterNot { it.isHub }
    ring.forEachIndexed { i, bot ->
        val step = if (ring.size > 1) ARC_SPAN_DEG / (ring.size - 1) else 0f
        val a = (ARC_START_DEG + i * step) * PI.toFloat() / 180f
        result[bot.id] = Offset(CX + ORBIT * sin(a), CY - ORBIT * cos(a))
    }
    return result
}

/**
 * The delegation constellation: you → router → Chief of Staff → bots. Tasks travel as
 * glowing dots along the edges; working bots spin a dashed ring, and bots waiting on
 * you pulse in the "needs you" colour.
 */
@Composable
fun DelegationMap(
    snapshot: FleetSnapshot,
    speed: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val needsYou = LocalHermesStatusColors.current.warning
    val measurer = rememberTextMeasurer()
    val positions = remember(snapshot.bots) { mapPositions(snapshot.bots) }
    val hub = remember(snapshot.bots) { snapshot.bots.firstOrNull { it.isHub }?.id }

    // Interpolate between simulation ticks so packets glide at the display frame rate.
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frameMs = it }
    }
    val anchorFrame = remember(snapshot.clockMs) { frameMs }
    val lead = if (isPaused) 0L else ((frameMs - anchorFrame) * speed).coerceIn(0L, 200L)
    val now = snapshot.clockMs + lead

    val transition = rememberInfiniteTransition(label = "fleet-map")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "pulse",
    )

    val waitingCount = snapshot.inColumn(FleetColumn.NEEDS_YOU).size
    val working = snapshot.bots.count { snapshot.activityOf(it.id) == BotActivity.WORKING }
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(MAP_W / MAP_H)
                .testTag("fleet_map")
                .semantics {
                    contentDescription = "Delegation map: $working bots working, $waitingCount waiting on you"
                },
    ) {
        val scale = size.width / MAP_W

        fun p(id: String) = positions[id]?.times(scale)

        // Orbit and edges
        drawCircle(
            color = colors.outlineVariant,
            radius = ORBIT * scale,
            center = Offset(CX, CY) * scale,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 12f))),
        )

        fun edge(
            a: String,
            b: String,
            color: Color,
            width: Float,
        ) {
            val s = p(a)
            val e = p(b)
            if (s != null && e != null) drawLine(color, s, e, strokeWidth = width)
        }
        edge(FLEET_YOU_ID, FLEET_ROUTER_ID, colors.outlineVariant, 1.5f)
        if (hub != null) {
            edge(FLEET_ROUTER_ID, hub, colors.outlineVariant, 1.5f)
            snapshot.bots.filterNot { it.isHub }.forEach { bot ->
                val active = snapshot.activityOf(bot.id) == BotActivity.WORKING
                edge(
                    hub,
                    bot.id,
                    if (active) FleetPalette.hue(bot.hueIndex).copy(alpha = 0.7f) else colors.outlineVariant,
                    if (active) 3f else 1.5f,
                )
            }
        }

        // Packets in flight
        snapshot.packets.forEach { packet ->
            val u = ((now - packet.startMs).toFloat() / packet.durationMs).coerceIn(0f, 1f)
            if (now < packet.startMs) return@forEach
            val s = p(packet.from) ?: return@forEach
            val e = p(packet.to) ?: return@forEach
            val color = packet.hueIndex?.let { FleetPalette.hue(it) } ?: needsYou
            val ease = if (u < 0.5f) 2 * u * u else 1 - (-2 * u + 2).pow(2) / 2
            val mid = (s + e) / 2f
            val ctrl = mid + (Offset(CX, CY) * scale - mid) * 0.35f
            val trail = 12
            for (i in 0..trail) {
                val t = (ease - i * 0.025f).coerceAtLeast(0f)
                val q = quad(s, ctrl, e, t)
                drawCircle(
                    color.copy(alpha = (1f - i / trail.toFloat()) * 0.5f),
                    radius =
                        3f * scale * (1f - i / (trail * 1.5f)),
                    center = q,
                )
            }
            val head = quad(s, ctrl, e, ease)
            drawCircle(color.copy(alpha = 0.25f), radius = 11f * scale, center = head)
            drawCircle(color, radius = 5f * scale, center = head)
        }

        // Nodes
        drawNode(
            measurer,
            p(FLEET_YOU_ID)!!,
            17f * scale,
            needsYou,
            "R",
            "Renato",
            if (waitingCount >
                0
            ) {
                "$waitingCount to approve"
            } else {
                null
            },
            colors.surfaceContainerHigh,
            colors.onSurface,
            colors.onSurfaceVariant,
            scale,
        )
        if (waitingCount > 0) pulseRing(p(FLEET_YOU_ID)!!, 17f * scale, needsYou, pulse)
        drawNode(
            measurer,
            p(FLEET_ROUTER_ID)!!,
            12f * scale,
            FleetPalette.router,
            "J",
            "Jev router",
            null,
            colors.surfaceContainerHigh,
            colors.onSurface,
            colors.onSurfaceVariant,
            scale,
        )
        snapshot.bots.forEach { bot ->
            val center = p(bot.id) ?: return@forEach
            val r = (if (bot.isHub) 26f else 18f) * scale
            val hue = FleetPalette.hue(bot.hueIndex)
            val activity = snapshot.activityOf(bot.id)
            val task =
                snapshot.tasks.firstOrNull {
                    it.owner == bot.id && (it.column == FleetColumn.WORKING || it.column == FleetColumn.NEEDS_YOU)
                }
            val sub =
                when (activity) {
                    BotActivity.WORKING -> task?.currentStep?.take(22)
                    BotActivity.WAITING -> "waiting on you"
                    BotActivity.IDLE -> null
                }
            when (activity) {
                BotActivity.WORKING -> {
                    rotate(spin, pivot = center) {
                        drawCircle(
                            hue,
                            radius = r + 5f * scale,
                            center = center,
                            style =
                                Stroke(
                                    width = 2f * scale,
                                    pathEffect =
                                        PathEffect.dashPathEffect(
                                            floatArrayOf(6f * scale, 8f * scale),
                                        ),
                                ),
                        )
                    }
                }

                BotActivity.WAITING -> {
                    pulseRing(center, r, needsYou, pulse)
                }

                BotActivity.IDLE -> {
                    Unit
                }
            }
            drawNode(
                measurer,
                center,
                r,
                hue,
                bot.initials,
                bot.name,
                sub,
                colors.surfaceContainerHigh,
                colors.onSurface,
                if (activity == BotActivity.WAITING) needsYou else colors.onSurfaceVariant,
                scale,
            )
        }
    }
}

private fun quad(
    a: Offset,
    c: Offset,
    b: Offset,
    t: Float,
): Offset {
    val m = 1 - t
    return a * (m * m) + c * (2 * m * t) + b * (t * t)
}

private fun DrawScope.pulseRing(
    center: Offset,
    radius: Float,
    color: Color,
    phase: Float,
) {
    drawCircle(
        color.copy(alpha = 0.9f * (1f - phase)),
        radius = radius * (1f + 0.7f * phase),
        center = center,
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawNode(
    measurer: TextMeasurer,
    center: Offset,
    radius: Float,
    hue: Color,
    initials: String,
    label: String,
    sub: String?,
    fill: Color,
    labelColor: Color,
    subColor: Color,
    scale: Float,
) {
    drawCircle(fill, radius, center)
    drawCircle(hue, radius, center, style = Stroke(width = 1.8f * scale))
    val init =
        measurer.measure(
            initials,
            TextStyle(color = hue, fontSize = (12 * scale).sp, fontWeight = FontWeight.ExtraBold),
        )
    drawText(init, topLeft = center - Offset(init.size.width / 2f, init.size.height / 2f))
    val name =
        measurer.measure(
            label,
            TextStyle(color = labelColor, fontSize = (10.5f * scale).sp, fontWeight = FontWeight.SemiBold),
        )
    drawText(name, topLeft = Offset(center.x - name.size.width / 2f, center.y + radius + 2f * scale))
    if (sub != null) {
        val s = measurer.measure(sub, TextStyle(color = subColor, fontSize = (9f * scale).sp))
        drawText(s, topLeft = Offset(center.x - s.size.width / 2f, center.y + radius + 2f * scale + name.size.height))
    }
}
