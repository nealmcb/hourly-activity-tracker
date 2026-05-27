package net.nealmcb.hourlyactivitytracker

import android.app.Application
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

class HealthConnectManager(application: Application) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(application) }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }

    /**
     * Returns true if Health Connect is available on this device.
     *
     * `HealthConnectClient.getSdkStatus()` in connect-client 1.1.0-rc01 has a known issue where
     * it may return SDK_UNAVAILABLE on Wear OS even though Health Connect is integrated into the
     * platform (Wear OS 4 / API 33+).  As a workaround, when getSdkStatus() reports unavailable
     * on a watch, we attempt to create the client directly — if that succeeds, HC is available.
     */
    fun isAvailable(): Boolean {
        val status = HealthConnectClient.getSdkStatus(application)
        if (status == HealthConnectClient.SDK_AVAILABLE) return true

        // On Wear OS, Health Connect is a platform component (Wear OS 4 / API 33+).
        // If getSdkStatus() missed it, try getOrCreate() as a fallback.
        val isWatch = application.packageManager.hasSystemFeature(FEATURE_WATCH)
        Log.d(TAG, "getSdkStatus=$status  isWatch=$isWatch")
        if (isWatch) {
            return try {
                HealthConnectClient.getOrCreate(application)
                Log.d(TAG, "Health Connect available via direct client creation on Wear OS")
                true
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "Health Connect not available on this Wear OS device", e)
                false
            }
        }
        return false
    }

    suspend fun hasAllPermissions(): Boolean {
        return try {
            healthConnectClient.permissionController
                .getGrantedPermissions()
                .containsAll(PERMISSIONS)
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
