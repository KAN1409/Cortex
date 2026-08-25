package com.kareem.cortex

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.contracts.HealthPermissionsRequestContract
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import androidx.health.connect.client.records.Record

/**
 * Read-only Health Connect gateway. Samsung Health can sync Galaxy/Samsung Health data into
 * Health Connect; Cortex keeps each record's data-origin package as provenance.
 */
object HealthConnectBridge {
    const val PROVIDER = "com.google.android.apps.healthdata"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JvmStatic fun sdkStatus(context: Context): Int = try {
        HealthConnectClient.getSdkStatus(context, PROVIDER)
    } catch (_: Throwable) {
        HealthConnectClient.SDK_UNAVAILABLE
    }

    @JvmStatic fun requiredReadPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    @JvmStatic fun permissionIntent(context: Context): Intent =
        HealthPermissionsRequestContract(PROVIDER).createIntent(context, requiredReadPermissions())

    interface PermissionCallback { fun onResult(granted: Int, total: Int, error: String?) }
    interface SyncCallback { fun onResult(seen: Int, added: Int, error: String?) }

    @JvmStatic fun permissionStatus(context: Context, callback: PermissionCallback) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (sdkStatus(app) != HealthConnectClient.SDK_AVAILABLE) {
                    withContext(Dispatchers.Main) { callback.onResult(0, requiredReadPermissions().size, "Health Connect unavailable") }
                    return@launch
                }
                val client = HealthConnectClient.getOrCreate(app, PROVIDER)
                val granted = client.permissionController.getGrantedPermissions()
                val required = requiredReadPermissions()
                withContext(Dispatchers.Main) { callback.onResult(required.count { granted.contains(it) }, required.size, null) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { callback.onResult(0, requiredReadPermissions().size, t.javaClass.simpleName) }
            }
        }
    }

    /** User-triggered 30-day sync. Background/history expansion is a separate permission gate. */
    @JvmStatic fun syncRecent(context: Context, days: Int, callback: SyncCallback) {
        val app = context.applicationContext
        scope.launch {
            var db: VaultDb? = null
            var runId = 0L
            var seen = 0
            var added = 0
            try {
                if (sdkStatus(app) != HealthConnectClient.SDK_AVAILABLE) throw IllegalStateException("Health Connect unavailable")
                val client = HealthConnectClient.getOrCreate(app, PROVIDER)
                val required = requiredReadPermissions()
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(required)) throw SecurityException("Health Connect permissions are incomplete")
                db = VaultDb(app)
                HealthStore.ensure(db)
                runId = HealthStore.beginSync(db, "health_connect")
                val end = Instant.now()
                val start = end.minus(Duration.ofDays(days.coerceIn(1, 30).toLong()))

                for (r in readAll(client, StepsRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName)
                    if (HealthStore.addMetric(db, source, "steps", r.count.toDouble(), "steps", r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) added++
                }
                for (r in readAll(client, HeartRateRecord::class, start, end)) {
                    r.samples.forEach { sample ->
                        seen++
                        val source = sourceKey(r.metadata.dataOrigin.packageName)
                        val ext = r.metadata.id + "#" + sample.time.toEpochMilli()
                        if (HealthStore.addMetric(db, source, "heart_rate", sample.beatsPerMinute.toDouble(), "bpm", sample.time.toEpochMilli(), sample.time.toEpochMilli(), ext, meta(r.metadata.dataOrigin.packageName)) > 0) added++
                    }
                }
                for (r in readAll(client, RestingHeartRateRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName)
                    if (HealthStore.addMetric(db, source, "resting_heart_rate", r.beatsPerMinute.toDouble(), "bpm", r.time.toEpochMilli(), r.time.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) added++
                }
                for (r in readAll(client, OxygenSaturationRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName)
                    if (HealthStore.addMetric(db, source, "oxygen_saturation", r.percentage.value, "%", r.time.toEpochMilli(), r.time.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) added++
                }
                for (r in readAll(client, WeightRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName)
                    if (HealthStore.addMetric(db, source, "weight", r.weight.inKilograms, "kg", r.time.toEpochMilli(), r.time.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) added++
                }
                for (r in readAll(client, SleepSessionRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName)
                    val hours = Duration.between(r.startTime, r.endTime).toMinutes().toDouble() / 60.0
                    val title = r.title?.replace("\"", "'") ?: ""
                    val metadata = "{\"origin_package\":\"${escape(r.metadata.dataOrigin.packageName)}\",\"title\":\"${escape(title)}\",\"stages\":${r.stages.size}}"
                    if (HealthStore.addMetric(db, source, "sleep_duration", hours, "h", r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), r.metadata.id, metadata) > 0) added++
                }

                HealthStore.finishSync(db, runId, "health_connect", seen, added, null)
                withContext(Dispatchers.Main) { callback.onResult(seen, added, null) }
            } catch (t: Throwable) {
                try { if (db != null && runId > 0) HealthStore.finishSync(db, runId, "health_connect", seen, added, t.javaClass.simpleName + ": " + (t.message ?: "")) } catch (_: Throwable) {}
                withContext(Dispatchers.Main) { callback.onResult(seen, added, t.javaClass.simpleName + if (t.message.isNullOrBlank()) "" else ": ${t.message}") }
            } finally {
                try { db?.close() } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        var pages = 0
        do {
            val response = client.readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(start, end), pageSize = 1000, pageToken = token))
            out.addAll(response.records)
            token = response.pageToken
            pages++
        } while (token != null && pages < 20)
        return out
    }

    private fun sourceKey(originPackage: String): String = HealthStore.sourceKeyForOrigin(originPackage)
    private fun meta(originPackage: String): String = "{\"origin_package\":\"${escape(originPackage)}\",\"gateway\":\"health_connect\"}"
    private fun escape(s: String?): String = (s ?: "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
