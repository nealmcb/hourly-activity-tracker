package net.nealmcb.hourlyactivitytracker

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Feature name for Wear OS hardware detection
private const val FEATURE_WATCH = "android.hardware.type.watch"

private const val TAG = "HealthConnectManager"

data class HourlySteps(
    val hour: Int,
    val steps: Long
)

/** Result of the Health Connect availability check, with a human-readable reason when unavailable. */
sealed class AvailabilityResult {
    data object Available : AvailabilityResult()
    data class Unavailable(val reason: String) : AvailabilityResult()
}

class HealthConnectManager(private val application: Application) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(application) }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }

    /**
     * Checks whether Health Connect is available on this device.
     *
     * Returns [AvailabilityResult.Available] if ready, or [AvailabilityResult.Unavailable]
     * with a diagnostic reason string if not.
     *
     * Background: `HealthConnectClient.getSdkStatus()` in connect-client 1.1.0-rc01 has a known
     * issue where it may return SDK_UNAVAILABLE on Wear OS even though Health Connect is integrated
     * into the platform (Wear OS 4 / API 33+). As a workaround, when getSdkStatus() reports
     * unavailable on a watch, we attempt to create the client directly via getOrCreate().
     */
    fun checkAvailability(): AvailabilityResult {
        Log.i(TAG, "checkAvailability: model=${Build.MODEL} manufacturer=${Build.MANUFACTURER}" +
                " api=${Build.VERSION.SDK_INT}")

        val status = HealthConnectClient.getSdkStatus(application)
        val statusStr = sdkStatusName(status)
        Log.i(TAG, "getSdkStatus=$statusStr")

        if (status == HealthConnectClient.SDK_AVAILABLE) {
            Log.i(TAG, "Health Connect available (getSdkStatus=SDK_AVAILABLE)")
            return AvailabilityResult.Available
        }

        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val reason = "Health Connect app update required (sdkStatus=$statusStr)"
            Log.w(TAG, reason)
            return AvailabilityResult.Unavailable(reason)
        }

        // On Wear OS, Health Connect is a platform component (Wear OS 4 / API 33+).
        // getSdkStatus() may incorrectly report unavailable; try getOrCreate() as a fallback.
        val isWatch = application.packageManager.hasSystemFeature(FEATURE_WATCH)
        Log.i(TAG, "sdkStatus=$statusStr isWatch=$isWatch; trying getOrCreate() fallback on watch")

        if (isWatch) {
            return try {
                HealthConnectClient.getOrCreate(application)
                Log.i(TAG, "Health Connect available via getOrCreate() fallback on Wear OS")
                AvailabilityResult.Available
            } catch (e: Exception) {
                val reason = "getOrCreate() failed on Wear OS" +
                        " (${e::class.simpleName}: ${e.message})" +
                        " sdkStatus=$statusStr"
                Log.w(TAG, reason, e)
                AvailabilityResult.Unavailable(reason)
            }
        }

        val reason = "Health Connect not available (sdkStatus=$statusStr, isWatch=false)"
        Log.w(TAG, reason)
        return AvailabilityResult.Unavailable(reason)
    }

    private fun sdkStatusName(status: Int): String = when (status) {
        HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE($status)"
        HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE($status)"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            "SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED($status)"
        else -> "UNKNOWN($status)"
    }

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            val hasAll = granted.containsAll(PERMISSIONS)
            Log.i(TAG, "hasAllPermissions: granted=$granted required=$PERMISSIONS result=$hasAll")
            hasAll
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }

    /**
     * Returns a list of [HourlySteps] for each hour of [date].
     * Hours with no recorded steps are included with a count of 0.
     */
    suspend fun getHourlySteps(date: LocalDate): List<HourlySteps> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant()
        val endOfDay = date.atTime(LocalTime.MAX).atZone(zoneId).toInstant()

        return try {
            val request = AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                timeRangeSlicer = Duration.ofHours(1)
            )
            val results = healthConnectClient.aggregateGroupByDuration(request)

            // Build a map from hour -> steps, defaulting missing hours to 0
            val stepsByHour = results.associate { bucket ->
                val hour = bucket.startTime.atZone(zoneId).hour
                val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L
                hour to steps
            }

            (0..23).map { hour ->
                HourlySteps(hour = hour, steps = stepsByHour.getOrDefault(hour, 0L))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading hourly steps", e)
            (0..23).map { HourlySteps(hour = it, steps = 0L) }
        }
    }
}
