package com.m57.hermescontrol.ui.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.repository.KanbanRepository
import com.m57.hermescontrol.data.repository.KanbanRepositoryImpl
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
    /** Set when the live board could not be read; the screen then falls back to demo data. */
    val liveError: String? = null,
)

/**
 * Drives the Fleet screen. It first tries the live Hermes Kanban board (polled, with
 * changes between polls animated as hand-offs). If the dashboard can't be reached it
 * falls back to [FleetSimulator] demo data, clearly labelled as such.
 */
class FleetViewModel(
    private val repository: KanbanRepository = KanbanRepositoryImpl(),
) : ViewModel() {
    private val simulator = FleetSimulator()
    private val live = LiveFleetSource()
    private val _uiState = MutableStateFlow(FleetUiState(snapshot = live.snapshot(), isDemo = false))
    val uiState: StateFlow<FleetUiState> = _uiState.asStateFlow()

    private var loop: Job? = null
    private var liveReady = false

    init {
        startLive()
    }

    private fun startLive() {
        loop?.cancel()
        loop =
            viewModelScope.launch {
                val board =
                    when (val boards = repository.getBoards()) {
                        is NetworkResult.Success -> {
                            boards.data.current ?: boards.data.boards
                                .firstOrNull()
                                ?.slug
                        }

                        is NetworkResult.Failure -> {
                            return@launch startDemo(boards.error.message)
                        }
                    } ?: return@launch startDemo("No Kanban board found")
                val orchestrator =
                    (repository.getOrchestration() as? NetworkResult.Success)
                        ?.data
                        ?.orchestratorProfile
                        ?.takeIf { it.isNotBlank() }
                if (!poll(board, orchestrator)) return@launch
                launch {
                    while (isActive) {
                        delay(FleetSimulator.TICK_MS)
                        if (_uiState.value.isPaused) continue
                        live.advance(FleetSimulator.TICK_MS)
                        _uiState.update { it.copy(snapshot = live.snapshot()) }
                    }
                }
                while (isActive) {
                    delay(POLL_MS)
                    if (!_uiState.value.isPaused) poll(board, orchestrator)
                }
            }
    }

    /** One board read. Returns false (and switches to demo) only if the very first read fails. */
    private suspend fun poll(
        board: String,
        orchestrator: String?,
    ): Boolean {
        val result = repository.getBoard(board)
        if (result is NetworkResult.Failure) {
            if (!liveReady) {
                startDemo(result.error.message)
                return false
            }
            _uiState.update { it.copy(liveError = result.error.message) }
            return true
        }
        val profiles = (repository.getProfiles() as? NetworkResult.Success)?.data?.profiles.orEmpty()
        live.apply((result as NetworkResult.Success).data, profiles, orchestrator)
        liveReady = true
        _uiState.update { it.copy(snapshot = live.snapshot(), isDemo = false, liveError = null) }
        return true
    }

    private fun startDemo(reason: String) {
        _uiState.update { it.copy(snapshot = simulator.snapshot(), isDemo = true, liveError = reason) }
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

    fun retryLive() = startLive()

    fun approve(taskId: Long) {
        if (_uiState.value.isDemo && simulator.approve(taskId)) publish()
    }

    fun sendBack(taskId: Long) {
        if (_uiState.value.isDemo && simulator.sendBack(taskId)) publish()
    }

    private fun publish() = _uiState.update { it.copy(snapshot = simulator.snapshot()) }

    private companion object {
        const val MAX_SPEED = 4
        const val POLL_MS = 4_000L
    }
}
