package com.m57.hermescontrol.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R

/**
 * True inside the Bots screen while the Toybox look is on. Shared chat pieces (bubbles, composer)
 * read it, so the Toybox styling never leaks into the plain chat elsewhere in the app.
 */
val LocalToybox = staticCompositionLocalOf { false }

/** Toybox fonts: rounded Nunito for text, chunky Baloo 2 for names and titles (both SIL OFL). */
object ToyFonts {
    val Body =
        FontFamily(
            Font(R.font.nunito_600, FontWeight.SemiBold),
            Font(R.font.nunito_600, FontWeight.Normal),
            Font(R.font.nunito_600, FontWeight.Medium),
            Font(R.font.nunito_700, FontWeight.Bold),
            Font(R.font.nunito_800, FontWeight.ExtraBold),
            Font(R.font.nunito_800, FontWeight.Black),
        )
    val Display = FontFamily(Font(R.font.baloo2_800, FontWeight.ExtraBold))
}

/**
 * The Toybox sticker look: a hard shadow (a solid copy of [shape] in [shadow], offset [depth]
 * right and down, no blur), then [fill], then a [outline]-wide dark outline. Clip or pad the
 * content yourself; this only draws behind it.
 */
fun Modifier.toySticker(
    shape: Shape,
    fill: Color,
    shadow: Color? = BotsPalette.ToyOutline,
    depth: Dp = 4.dp,
    outline: Dp = 3.dp,
    outlineColor: Color = BotsPalette.ToyOutline,
    dashed: Boolean = false,
): Modifier =
    drawBehind {
        val o = shape.createOutline(size, layoutDirection, this)
        if (shadow != null) {
            translate(left = depth.toPx(), top = depth.toPx()) { drawOutline(o, shadow) }
        }
        drawOutline(o, fill)
        val w = outline.toPx()
        if (w > 0f) {
            val inset =
                shape.createOutline(
                    androidx.compose.ui.geometry
                        .Size(size.width - w, size.height - w),
                    layoutDirection,
                    this,
                )
            translate(left = w / 2f, top = w / 2f) {
                drawOutline(
                    inset,
                    outlineColor,
                    style =
                        androidx.compose.ui.graphics.drawscope.Stroke(
                            width = w,
                            pathEffect =
                                if (dashed) {
                                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(3f * w, 2f * w),
                                    )
                                } else {
                                    null
                                },
                        ),
                )
            }
        }
    }

/** Material type scale with every style set in [ToyFonts.Body], for the Toybox look. */
fun Typography.toybox(): Typography =
    copy(
        displayLarge = displayLarge.copy(fontFamily = ToyFonts.Display),
        displayMedium = displayMedium.copy(fontFamily = ToyFonts.Display),
        displaySmall = displaySmall.copy(fontFamily = ToyFonts.Display),
        headlineLarge = headlineLarge.copy(fontFamily = ToyFonts.Display),
        headlineMedium = headlineMedium.copy(fontFamily = ToyFonts.Display),
        headlineSmall = headlineSmall.copy(fontFamily = ToyFonts.Display),
        titleLarge = titleLarge.copy(fontFamily = ToyFonts.Display),
        titleMedium = titleMedium.copy(fontFamily = ToyFonts.Body),
        titleSmall = titleSmall.copy(fontFamily = ToyFonts.Body),
        bodyLarge = bodyLarge.copy(fontFamily = ToyFonts.Body),
        bodyMedium = bodyMedium.copy(fontFamily = ToyFonts.Body),
        bodySmall = bodySmall.copy(fontFamily = ToyFonts.Body),
        labelLarge = labelLarge.copy(fontFamily = ToyFonts.Body),
        labelMedium = labelMedium.copy(fontFamily = ToyFonts.Body),
        labelSmall = labelSmall.copy(fontFamily = ToyFonts.Body),
    )

/**
 * The Bots screen's theme: the rail colour scheme, and in Toybox the Toybox fonts plus
 * [LocalToybox] so shared chat pieces switch to their Toybox look.
 */
@Composable
fun BotsTheme(
    baseScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    val toybox = BotsPalette.isToybox
    val baseType = MaterialTheme.typography
    MaterialTheme(
        colorScheme = BotsPalette.railScheme(baseScheme),
        typography = if (toybox) baseType.toybox() else baseType,
    ) {
        CompositionLocalProvider(
            LocalToybox provides toybox,
            LocalTextStyle provides
                LocalTextStyle.current.let { if (toybox) it.copy(fontFamily = ToyFonts.Body) else it },
            content = content,
        )
    }
}

/** The Toybox polka-dot background: [bg] with [dot]-coloured dots every [spacing]. */
fun Modifier.toyDots(
    bg: Color,
    dot: Color,
    spacing: Dp = 22.dp,
    radius: Dp = 1.6.dp,
): Modifier =
    drawBehind {
        drawRect(bg)
        val step = spacing.toPx()
        val r = radius.toPx()
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(
                    dot,
                    radius = r,
                    center =
                        androidx.compose.ui.geometry
                            .Offset(x, y),
                )
                x += step
            }
            y += step
        }
    }
