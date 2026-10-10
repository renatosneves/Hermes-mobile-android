package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.m57.hermescontrol.R

/**
 * How big the Bots home draws: a scale applied to its dp and sp together. Safe to call [set] before
 * [init] (tests): it then only changes the in-memory value.
 */
object BotsSizeStore {
    private const val PREFS = "bots_size"
    private const val KEY = "scale"

    /** The scales on offer, with their menu labels. */
    val SIZES =
        listOf(
            0.9f to R.string.bots_size_small,
            1.0f to R.string.bots_size_default,
            1.12f to R.string.bots_size_large,
            1.25f to R.string.bots_size_xlarge,
        )

    const val DEFAULT = 1.12f

    private var prefs: SharedPreferences? = null

    /** The current scale; snapshot state, so the UI follows a change at once. */
    var scale by mutableFloatStateOf(DEFAULT)
        private set

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs?.getFloat(KEY, DEFAULT) ?: DEFAULT
        scale = if (SIZES.any { it.first == saved }) saved else DEFAULT
    }

    fun set(value: Float) {
        scale = value
        prefs?.edit()?.putFloat(KEY, value)?.apply()
    }
}
