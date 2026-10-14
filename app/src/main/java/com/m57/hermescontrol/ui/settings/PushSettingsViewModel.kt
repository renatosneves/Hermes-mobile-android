package com.m57.hermescontrol.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.notification.push.BotLevel
import com.m57.hermescontrol.notification.push.PushPrefs
import com.m57.hermescontrol.notification.push.PushRegistrar
import com.m57.hermescontrol.notification.push.PushStatus
import com.m57.hermescontrol.notification.push.PushStore
import com.m57.hermescontrol.notification.push.PushTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A bot row in the per-bot push levels list. */
data class PushBotRow(
    val name: String,
    val title: String,
)

data class PushSettingsUiState(
    val status: PushStatus = PushStatus.NotRegistered,
    val prefs: PushPrefs = PushPrefs(),
    val bots: List<PushBotRow> = emptyList(),
    val testing: Boolean = false,
    val testResult: PushTestResult? = null,
)

/** Backs the push block of the Behavior page: status, per-type and per-bot levels, quiet hours, test. */
class PushSettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PushSettingsUiState(prefs = PushStore.prefsValue(application)))
    val uiState: StateFlow<PushSettingsUiState> = _uiState.asStateFlow()

    init {
        PushRegistrar.refreshStatus(application)
        viewModelScope.launch {
            PushRegistrar.status.collect { status -> _uiState.update { it.copy(status = status) } }
        }
        loadBots()
    }

    /** Re-read the distributor state, e.g. after returning from the Play Store or the picker. */
    fun refresh() {
        PushRegistrar.refreshStatus(getApplication())
    }

    private fun loadBots() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = safeApiCall(retries = 0) { ApiClient.hermesApi.getProfiles() }
            val profiles = (result as? NetworkResult.Success)?.data?.profiles ?: return@launch
            val rows = profiles.map { PushBotRow(name = it.name, title = it.effectiveTitle) }
            _uiState.update { it.copy(bots = rows) }
        }
    }

    private fun update(transform: (PushPrefs) -> PushPrefs) {
        val app = getApplication<Application>()
        val next = transform(_uiState.value.prefs)
        PushStore.savePrefs(app, next)
        _uiState.update { it.copy(prefs = next) }
        PushRegistrar.scheduleRegister(app)
    }

    fun setApprovals(enabled: Boolean) = update { it.copy(approvals = enabled) }

    fun setTasks(enabled: Boolean) = update { it.copy(tasks = enabled) }

    fun setMessages(enabled: Boolean) = update { it.copy(messages = enabled) }

    fun setScheduled(enabled: Boolean) = update { it.copy(scheduled = enabled) }

    fun setBotLevel(
        bot: String,
        level: BotLevel,
    ) = update { prefs ->
        val levels = prefs.botLevels.toMutableMap()
        if (level == BotLevel.ALL) levels.remove(bot) else levels[bot] = level
        prefs.copy(botLevels = levels)
    }

    fun setQuietEnabled(enabled: Boolean) = update { it.copy(quietEnabled = enabled) }

    fun setQuietStart(minute: Int) = update { it.copy(quietStartMinute = minute) }

    fun setQuietEnd(minute: Int) = update { it.copy(quietEndMinute = minute) }

    fun sendTest() {
        if (_uiState.value.testing) return
        _uiState.update { it.copy(testing = true, testResult = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = PushRegistrar.sendTest(getApplication())
            _uiState.update { it.copy(testing = false, testResult = result) }
        }
    }
}
