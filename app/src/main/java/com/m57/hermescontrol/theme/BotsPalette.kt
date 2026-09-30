package com.m57.hermescontrol.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Deep-navy palette for the Grok-style Bots home (bot rail plus conversation pane).
 * Always dark: the Bots home is a designed space, not a wallpaper-tinted one.
 */
object BotsPalette {
    val Ink = Color(0xFF0A0F24)
    val Rail = Color(0xFF0C1230)
    val PaneTop = Color(0xFF0E1430)
    val PaneBottom = Color(0xFF0B1028)
    val Deck = Color(0xFF121936)
    val Deck2 = Color(0xFF1A2247)
    val Deck3 = Color(0xFF222B57)
    val Line = Color(0xFF2A3469)
    val Fg = Color(0xFFEAEDFF)
    val Muted = Color(0xFF8E97C9)
    val Faint = Color(0xFF5E6799)
    val Idle = Color(0xFF4A5388)
    val Ok = Color(0xFF5FE0A8)
    val You = Color(0xFFFFB547)

    /** "Needs you": a bot waiting on an approval or an answer. */
    val Attention = Color(0xFFFFB547)

    /** Gateway unreachable. */
    val Offline = Color(0xFFFF6B6B)
    val GlowTop = Color(0xFF1B2458)
    val GlowBottom = Color(0xFF2A1B45)

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
            onPrimary = Ink,
            primaryContainer = Deck3,
            onPrimaryContainer = Fg,
            secondary = hue,
            onSecondary = Ink,
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
            onPrimary = Ink,
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
