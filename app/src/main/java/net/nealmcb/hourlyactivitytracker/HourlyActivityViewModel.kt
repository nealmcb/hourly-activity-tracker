package net.nealmcb.hourlyactivitytracker

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "HourlyActivityViewModel"

sealed interface UiState {
    data object Loading : UiState
    data object PermissionRequired : UiState
    /** Health Connect is not available on this device; [reason] explains why. */
    data class HealthConnectUnavailable(val reason: String) : UiState
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
            Log.i(TAG, "checkAvailabilityAndLoad: starting")
            val availability = try {
                healthConnectManager.checkAvailability()
            } catch (e: Exception) {
                val reason = "Unexpected error checking Health Connect: ${e::class.simpleName}: ${e.message}"
                Log.e(TAG, reason, e)
                _uiState.value = UiState.HealthConnectUnavailable(reason)
                return@launch
            }

            if (availability is AvailabilityResult.Unavailable) {
                Log.w(TAG, "Health Connect unavailable → HealthConnectUnavailable state; reason=${availability.reason}")
                _uiState.value = UiState.HealthConnectUnavailable(availability.reason)
                return@launch
            }

            Log.i(TAG, "Health Connect available; checking permissions")
            if (!healthConnectManager.hasAllPermissions()) {
                Log.i(TAG, "Permissions not granted → PermissionRequired state")
                _uiState.value = UiState.PermissionRequired
                return@launch
            }
            Log.i(TAG, "Permissions granted; loading step data")
            loadTodaySteps()
        }
    }

    fun loadTodaySteps() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val today = LocalDate.now()
                val hourlySteps = healthConnectManager.getHourlySteps(today)
                Log.i(TAG, "Step data loaded for $today (${hourlySteps.size} hours)")
                _uiState.value = UiState.Success(date = today, hourlySteps = hourlySteps)
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                Log.e(TAG, "Error loading step data → Error state: $msg", e)
                _uiState.value = UiState.Error(msg)
            }
        }
    }

    fun onPermissionsGranted() {
        Log.i(TAG, "onPermissionsGranted: re-checking availability")
        checkAvailabilityAndLoad()
    }
}

