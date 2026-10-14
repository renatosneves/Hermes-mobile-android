package com.m57.hermescontrol.ui.bots

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Whether the Bots home may run its endless decorative motion (spinning working rings, pulsing
 * dots). Each one redraws the screen every frame, so it stops while battery saver is on.
 */
object DecorativeMotion {
    var enabled by mutableStateOf(true)
        private set

    /** Keeps [enabled] in step with battery saver while the calling screen is shown. */
    @Composable
    fun TrackPowerSaver() {
        val context = LocalContext.current
        DisposableEffect(context) {
            val power = context.getSystemService(PowerManager::class.java)

            fun refresh() {
                enabled = power?.isPowerSaveMode != true
            }
            refresh()
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) = refresh()
                }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }
}
