package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.content.SharedPreferences
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.DarkStyle
import com.m57.hermescontrol.theme.LightStyle

/**
 * Remembers which dark look (navy or charcoal) and light look (plain or Toybox) the Bots home uses. Safe to call
 * [setDarkStyle] before [init] (tests): it then only changes the in-memory palette.
 */
object BotsThemeStore {
    private const val PREFS = "bots_theme"
    private const val KEY = "dark_style"
    private const val KEY_LIGHT = "light_style"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY, null)
        BotsPalette.darkStyle = DarkStyle.entries.firstOrNull { it.name == saved } ?: DarkStyle.NAVY
        val light = prefs?.getString(KEY_LIGHT, null)
        BotsPalette.lightStyle = LightStyle.entries.firstOrNull { it.name == light } ?: LightStyle.PLAIN
    }

    fun setLightStyle(style: LightStyle) {
        BotsPalette.lightStyle = style
        prefs?.edit()?.putString(KEY_LIGHT, style.name)?.apply()
    }

    fun setDarkStyle(style: DarkStyle) {
        BotsPalette.darkStyle = style
        prefs?.edit()?.putString(KEY, style.name)?.apply()
    }
}
