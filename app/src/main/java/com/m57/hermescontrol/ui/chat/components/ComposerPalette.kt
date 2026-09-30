package com.m57.hermescontrol.ui.chat.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Resolved colors for the chat composer card and its controls.
 *
 * Control fills are derived from [ColorScheme.onSurface] instead of the
 * surfaceContainer tiers: several presets ship nearly identical tiers
 * (Nord's High and Highest are the same color), which made the flat controls
 * vanish into the card. A fixed onSurface tint keeps them visible in every
 * preset and under dynamic color. Text and icon contrast is gated per preset
 * in ComposerPaletteTest.
 */
internal data class ComposerPalette(
    val card: Color,
    val cardBorder: Color,
    val text: Color,
    val placeholder: Color,
    val control: Color,
    val onControl: Color,
    val onControlVariant: Color,
    val action: Color,
    val onAction: Color,
)

private const val CONTROL_TINT_ALPHA = 0.12f

internal fun composerPalette(scheme: ColorScheme): ComposerPalette {
    val card = scheme.surfaceContainer
    val tint = scheme.onSurface.copy(alpha = CONTROL_TINT_ALPHA)
    // Themed rooms (the Bots chats) paint their own backdrop and leave the surface see-through;
    // an onSurface fill with a see-through glyph showed there as a blank white disc.
    val seeThrough = scheme.surface.alpha < 1f
    return ComposerPalette(
        card = card,
        cardBorder = tint.compositeOver(scheme.background),
        text = scheme.onSurface,
        placeholder = scheme.onSurfaceVariant,
        control = tint.compositeOver(card),
        onControl = scheme.onSurface,
        onControlVariant = scheme.onSurfaceVariant,
        action = if (seeThrough) scheme.primary else scheme.onSurface,
        onAction = if (seeThrough) scheme.onPrimary else scheme.surface,
    )
}

@Composable
@ReadOnlyComposable
internal fun composerPalette(): ComposerPalette = composerPalette(MaterialTheme.colorScheme)
