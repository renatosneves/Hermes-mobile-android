package com.m57.hermescontrol.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The dark look of the Bots home. */
enum class DarkStyle { NAVY, CHARCOAL }

/**
 * Palette for the Grok-style Bots home (bot rail plus conversation pane): deep navy or neutral
 * charcoal at night (see [DarkStyle]), blue-white by day. The bot hues are the same in all of them.
 */
object BotsPalette {
    /** Set by [HermesControlTheme]; snapshot state, so every screen recolours when the theme flips. */
    var isDark by mutableStateOf(true)

    /** Which dark look is used at night: the original navy or neutral charcoal. */
    var darkStyle by mutableStateOf(DarkStyle.NAVY)

    private fun pick(
        dark: Color,
        light: Color,
        charcoal: Color = dark,
    ): Color =
        when {
            !isDark -> light
            darkStyle == DarkStyle.CHARCOAL -> charcoal
            else -> dark
        }

    /** Text on a bot hue (bubbles, buttons): dark in both themes, as the hues are bright. */
    val OnHue = Color(0xFF0A0F24)

    /** Light text and orb highlights that stay light in both themes. */
    val Highlight = Color(0xFFEAEDFF)

    val Ink get() =
        pick(
            Color(0xFF0A0F24),
            Color(0xFFF3F5FA),
            Color(0xFF0F0F10),
        )
    val Rail get() =
        pick(
            Color(0xFF0C1230),
            Color(0xFFEAEEF7),
            Color(0xFF1E1E1F),
        )
    val PaneTop get() =
        pick(
            Color(0xFF0E1430),
            Color(0xFFFBFCFE),
            Color(0xFF171718),
        )
    val PaneBottom get() =
        pick(
            Color(0xFF0B1028),
            Color(0xFFF3F5FA),
            Color(0xFF141415),
        )
    val Deck get() =
        pick(
            Color(0xFF121936),
            Color(0xFFFFFFFF),
            Color(0xFF242426),
        )
    val Deck2 get() =
        pick(
            Color(0xFF1A2247),
            Color(0xFFEEF1F8),
            Color(0xFF2D2D30),
        )
    val Deck3 get() =
        pick(
            Color(0xFF222B57),
            Color(0xFFDCE3F7),
            Color(0xFF3A3A3D),
        )
    val Line get() =
        pick(
            Color(0xFF2A3469),
            Color(0xFFD6DCEA),
            Color(0xFF333336),
        )
    val Fg get() =
        pick(
            Color(0xFFEAEDFF),
            Color(0xFF141A33),
            Color(0xFFF2F2F3),
        )
    val Muted get() =
        pick(
            Color(0xFF8E97C9),
            Color(0xFF535C82),
            Color(0xFFA3A3A8),
        )
    val Faint get() =
        pick(
            Color(0xFF5E6799),
            Color(0xFF68709A),
            Color(0xFF7C7C82),
        )
    val Idle get() =
        pick(
            Color(0xFF4A5388),
            Color(0xFFA3ABCB),
            Color(0xFF56565C),
        )
    val Ok get() =
        pick(
            Color(0xFF5FE0A8),
            Color(0xFF14875A),
            Color(0xFF5FE0A8),
        )
    val You get() =
        pick(
            Color(0xFFFFB547),
            Color(0xFFB45F00),
            Color(0xFFFFB547),
        )

    /** "Needs you": a bot waiting on an approval or an answer. */
    val Attention get() =
        pick(
            Color(0xFFFFB547),
            Color(0xFFB45F00),
            Color(0xFFFFB547),
        )

    /** Gateway unreachable. */
    val Offline get() =
        pick(
            Color(0xFFFF6B6B),
            Color(0xFFC92A2A),
            Color(0xFFFF6B6B),
        )
    val GlowTop get() =
        pick(
            Color(0xFF1B2458),
            Color(0xFFE3E9FB),
            Color(0xFF1E1E1F),
        )
    val GlowBottom get() =
        pick(
            Color(0xFF2A1B45),
            Color(0xFFF1E6F5),
            Color(0xFF171718),
        )

    /** One hue per bot, picked by a stable hash of its handle. */
    val Hues =
        listOf(
            Color(0xFFB9A4FF),
            Color(0xFF4FC3F7),
            Color(0xFF5B8CFF),
            Color(0xFFFF7EB0),
            Color(0xFF3FD0A4),
            Color(0xFFFF9F5A),
            Color(0xFF9C7BFF),
            Color(0xFF2EC4D6),
            Color(0xFF9BD24F),
            Color(0xFFFF6B6B),
            Color(0xFFE87BE0),
            Color(0xFFD4C84A),
        )

    /** Conic sweep used for group ("team") orbs. */
    val Team =
        listOf(
            Color(0xFFB9A4FF),
            Color(0xFF4FC3F7),
            Color(0xFF3FD0A4),
            Color(0xFFFF9F5A),
            Color(0xFFFF7EB0),
            Color(0xFFB9A4FF),
        )

    /** Colour scheme for the embedded chat: transparent surfaces so the pane glow shows through, accent = bot hue. */
    fun chatScheme(
        base: ColorScheme,
        hue: Color,
    ): ColorScheme =
        base.copy(
            primary = hue,
            onPrimary = OnHue,
            primaryContainer = Deck3,
            onPrimaryContainer = Fg,
            secondary = hue,
            onSecondary = OnHue,
            secondaryContainer = Deck2,
            onSecondaryContainer = Fg,
            background = Color.Transparent,
            onBackground = Fg,
            surface = Color.Transparent,
            onSurface = Fg,
            surfaceVariant = Deck2,
            onSurfaceVariant = Muted,
            surfaceContainerLowest = Ink,
            surfaceContainerLow = Deck,
            surfaceContainer = Deck,
            surfaceContainerHigh = Deck2,
            surfaceContainerHighest = Deck3,
            outline = Line,
            outlineVariant = Line,
        )

    /** Colour scheme for the rail: navy surfaces, Material widgets (menus, dialogs) stay legible. */
    fun railScheme(base: ColorScheme): ColorScheme =
        base.copy(
            primary = Hues[0],
            onPrimary = OnHue,
            background = Rail,
            onBackground = Fg,
            surface = Rail,
            onSurface = Fg,
            surfaceVariant = Deck2,
            onSurfaceVariant = Muted,
            surfaceContainerLowest = Ink,
            surfaceContainerLow = Deck,
            surfaceContainer = Deck,
            surfaceContainerHigh = Deck2,
            surfaceContainerHighest = Deck3,
            outline = Line,
            outlineVariant = Line,
        )
}
