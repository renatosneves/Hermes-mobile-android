package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.content.SharedPreferences
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.DarkStyle

/**
 * Remembers which dark look (navy or charcoal) the Bots home uses at night. Safe to call
 * [setDarkStyle] before [init] (tests): it then only changes the in-memory palette.
 */
object BotsThemeStore {
    private const val PREFS = "bots_theme"
    private const val KEY = "dark_style"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY, null)
        BotsPalette.darkStyle = DarkStyle.entries.firstOrNull { it.name == saved } ?: DarkStyle.NAVY
    }

    fun setDarkStyle(style: DarkStyle) {
        BotsPalette.darkStyle = style
        prefs?.edit()?.putString(KEY, style.name)?.apply()
    }
}
