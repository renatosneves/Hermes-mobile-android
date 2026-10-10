package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/**
 * Pictures chosen for groups (and the all-bots room), kept on this phone only, keyed by group name.
 * Safe to use before [init] (tests): it then only changes the in-memory map.
 */
object GroupPictureStore {
    private const val PREFS = "bots_group_pictures"

    private var prefs: SharedPreferences? = null

    /** Snapshot state, so a row showing a picture redraws when it changes. */
    private val pictures = mutableStateMapOf<String, String>()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs?.all?.forEach { (name, value) ->
            if (value is String) pictures[name] = value
        }
    }

    fun get(name: String): String? = pictures[name]

    fun set(
        name: String,
        dataUrl: String,
    ) {
        pictures[name] = dataUrl
        prefs?.edit()?.putString(name, dataUrl)?.apply()
    }

    fun remove(name: String) {
        pictures.remove(name)
        prefs?.edit()?.remove(name)?.apply()
    }
}
