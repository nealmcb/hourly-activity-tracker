package net.nealmcb.hourlyactivitytracker

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface UiState {
    data object Loading : UiState
    data object PermissionRequired : UiState
    data object HealthConnectUnavailable : UiState
    data class Success(
        val date: LocalDate,
        val hourlySteps: List<HourlySteps>
    ) : UiState
    data class Error(val message: String) : UiState
}

const val HIGHLIGHT_THRESHOLD = 250L

class HourlyActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnectManager = HealthConnectManager(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun checkAvailabilityAndLoad() {
        viewModelScope.launch {
            val availability = HealthConnectClient.getSdkStatus(getApplication())
            if (availability != HealthConnectClient.SDK_AVAILABLE) {
                _uiState.value = UiState.HealthConnectUnavailable
                return@launch
            }
            if (!healthConnectManager.hasAllPermissions()) {
                _uiState.value = UiState.PermissionRequired
                return@launch
            }
            loadTodaySteps()
        }
    }

    fun loadTodaySteps() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val today = LocalDate.now()
                val hourlySteps = healthConnectManager.getHourlySteps(today)
                _uiState.value = UiState.Success(date = today, hourlySteps = hourlySteps)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onPermissionsGranted() {
        checkAvailabilityAndLoad()
    }
}

