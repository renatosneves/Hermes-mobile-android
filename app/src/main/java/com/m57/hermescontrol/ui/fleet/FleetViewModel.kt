package com.m57.hermescontrol.ui.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FleetUiState(
    val snapshot: FleetSnapshot,
    val isPaused: Boolean = false,
    val speed: Int = 1,
    val isDemo: Boolean = true,
)

/**
 * Drives the Fleet screen. For now it runs [FleetSimulator] (demo data); the live
 * dashboard feed replaces the simulator behind the same [FleetUiState].
 */
class FleetViewModel : ViewModel() {
    private val simulator = FleetSimulator()
    private val _uiState = MutableStateFlow(FleetUiState(snapshot = simulator.snapshot()))
    val uiState: StateFlow<FleetUiState> = _uiState.asStateFlow()

    private var loop: Job? = null

    init {
        start()
    }

    private fun start() {
        loop?.cancel()
        loop =
            viewModelScope.launch {
                while (isActive) {
                    delay(FleetSimulator.TICK_MS)
                    val state = _uiState.value
                    if (state.isPaused) continue
                    simulator.tick(FleetSimulator.TICK_MS * state.speed)
                    publish()
                }
            }
    }

    fun togglePause() = _uiState.update { it.copy(isPaused = !it.isPaused) }

    fun cycleSpeed() = _uiState.update { it.copy(speed = if (it.speed >= MAX_SPEED) 1 else it.speed * 2) }

    fun approve(taskId: Long) {
        if (simulator.approve(taskId)) publish()
    }

    fun sendBack(taskId: Long) {
        if (simulator.sendBack(taskId)) publish()
    }

    private fun publish() = _uiState.update { it.copy(snapshot = simulator.snapshot()) }

    private companion object {
        const val MAX_SPEED = 4
    }
}
