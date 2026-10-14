package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.content.SharedPreferences

/**
 * The bots pinned to the top of the Bots list, in the order they were pinned. Falls back to
 * memory until [init] runs (tests).
 */
object BotPinStore {
    private const val PREFS = "bots_pins"
    private const val KEY_ORDER = "order"

    private var prefs: SharedPreferences? = null
    private var memory: List<String> = emptyList()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY_ORDER, null)?.let { stored ->
            memory = stored.split("\n").filter { it.isNotBlank() }
        }
    }

    fun all(): List<String> = memory

    fun pin(name: String) {
        if (name in memory) return
        save(memory + name)
    }

    fun unpin(name: String) {
        if (name !in memory) return
        save(memory - name)
    }

    private fun save(order: List<String>) {
        memory = order
        prefs?.edit()?.putString(KEY_ORDER, order.joinToString("\n"))?.apply()
    }
}
