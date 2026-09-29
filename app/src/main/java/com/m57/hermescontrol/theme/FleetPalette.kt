package com.m57.hermescontrol.theme

import androidx.compose.ui.graphics.Color

/**
 * Per-bot identity hues for the Fleet screen. Each bot keeps one hue everywhere it
 * appears (map node, board card stripe, feed name) so a hand-off reads at a glance.
 *
 * These are identity colours, not status colours: "needs you" and failures still use
 * [LocalHermesStatusColors]. Tuned to stay legible on both dark and light surfaces.
 */
object FleetPalette {
    val hues =
        listOf(
            Color(0xFFB9A4FF), // lavender
            Color(0xFF4FC3F7), // sky
            Color(0xFF5B8CFF), // blue
            Color(0xFFFF7EB0), // pink
            Color(0xFF3FD0A4), // mint
            Color(0xFFFF9F5A), // orange
            Color(0xFF9C7BFF), // violet
            Color(0xFF2EC4D6), // teal
            Color(0xFF9BD24F), // lime
            Color(0xFFFF6B6B), // coral
            Color(0xFFE87BE0), // magenta
            Color(0xFFD4C84A), // olive gold
        )

    /** Neutral hue for infrastructure nodes such as the router. */
    val router = Color(0xFF8E97C9)

    fun hue(index: Int): Color = hues[Math.floorMod(index, hues.size)]
}
