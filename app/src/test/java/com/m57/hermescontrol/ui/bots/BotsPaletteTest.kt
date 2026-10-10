package com.m57.hermescontrol.ui.bots

import androidx.compose.ui.graphics.Color
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.DarkStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class BotsPaletteTest {
    @After
    fun restore() {
        BotsPalette.isDark = true
        BotsPalette.darkStyle = DarkStyle.NAVY
    }

    @Test
    fun lightGivesLightRail() {
        BotsPalette.isDark = false
        assertEquals(Color(0xFFEAEEF7), BotsPalette.Rail)
    }

    @Test
    fun navyGivesNavyRail() {
        BotsPalette.isDark = true
        BotsPalette.darkStyle = DarkStyle.NAVY
        assertEquals(Color(0xFF0C1230), BotsPalette.Rail)
    }

    @Test
    fun charcoalGivesCharcoalRail() {
        BotsPalette.isDark = true
        BotsPalette.darkStyle = DarkStyle.CHARCOAL
        assertEquals(Color(0xFF1E1E1F), BotsPalette.Rail)
    }
}
