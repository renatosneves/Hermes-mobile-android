package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers, per bot, how many messages its conversation had when you last looked, so the
 * Bots list can show a count of what's new. Falls back to memory until [init] runs (tests).
 */
object BotSeenStore {
    private const val PREFS = "hermes_bots_seen"

    private var prefs: SharedPreferences? = null
    private val memory = mutableMapOf<String, Int>()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun all(): Map<String, Int> {
        val stored =
            prefs
                ?.all
                .orEmpty()
                .mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }
                .toMap()
        return stored + memory
    }

    fun put(
        name: String,
        count: Int,
    ) {
        memory[name] = count
        prefs?.edit()?.putInt(name, count)?.apply()
    }
}
