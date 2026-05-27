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
