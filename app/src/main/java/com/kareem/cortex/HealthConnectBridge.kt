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
import java.util.LinkedHashMap
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
    interface DetailedSyncCallback { fun onResult(result: HealthSyncResult) }

    @JvmStatic fun permissionStatus(context: Context, callback: PermissionCallback) {
        val app = context.applicationContext
        scope.launch {
            try {
                val sdk = sdkStatus(app)
                if (sdk != HealthConnectClient.SDK_AVAILABLE) {
                    val state = HealthSyncPolicy.sdk(sdk)
                    withContext(Dispatchers.Main) { callback.onResult(0, requiredReadPermissions().size, state.kind) }
                    return@launch
                }
                val client = HealthConnectClient.getOrCreate(app, PROVIDER)
                val granted = client.permissionController.getGrantedPermissions()
                val required = requiredReadPermissions()
                withContext(Dispatchers.Main) { callback.onResult(required.count { granted.contains(it) }, required.size, null) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { callback.onResult(0, requiredReadPermissions().size, t.javaClass.simpleName + if (t.message.isNullOrBlank()) "" else ": ${t.message}") }
            }
        }
    }

    /** Compatibility API retained for existing callers. */
    @JvmStatic fun syncRecent(context: Context, days: Int, callback: SyncCallback) {
        syncRecentDetailed(context, days, object : DetailedSyncCallback {
            override fun onResult(result: HealthSyncResult) {
                callback.onResult(result.seen, result.added, if (result.success()) null else result.error.ifBlank { result.failureKind })
            }
        })
    }

    /**
     * User-triggered sync with terminal state, partial counts and per-origin provenance.
     * A partially read run is never reported as SUCCESS if any record family aborts the run.
     */
    @JvmStatic fun syncRecentDetailed(context: Context, days: Int, callback: DetailedSyncCallback) {
        val app = context.applicationContext
        scope.launch {
            var db: VaultDb? = null
            var runId = 0L
            var seen = 0
            var added = 0
            val sourceSeen = LinkedHashMap<String, Int>()
            val sourceAdded = LinkedHashMap<String, Int>()
            try {
                db = VaultDb(app)
                HealthStore.ensure(db)
                val sdk = sdkStatus(app)
                if (sdk != HealthConnectClient.SDK_AVAILABLE) {
                    val state = HealthSyncPolicy.sdk(sdk)
                    val result = HealthSyncResult.fail(seen, added, state.state, state.kind, state.kind, state.nextAction, sourceSeen, sourceAdded)
                    HealthStore.finishSyncDetailed(db, 0, "health_connect", result)
                    withContext(Dispatchers.Main) { callback.onResult(result) }
                    return@launch
                }

                val client = HealthConnectClient.getOrCreate(app, PROVIDER)
                val required = requiredReadPermissions()
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(required)) {
                    val result = HealthSyncResult.fail(seen, added, HealthSyncResult.NEEDS_ACCESS, "missing_permission", "Health Connect permissions are incomplete", "Grant all requested Health Connect read scopes, then sync again.", sourceSeen, sourceAdded)
                    HealthStore.finishSyncDetailed(db, 0, "health_connect", result)
                    withContext(Dispatchers.Main) { callback.onResult(result) }
                    return@launch
                }

                runId = HealthStore.beginSync(db, "health_connect")
                val end = Instant.now()
                val start = end.minus(Duration.ofDays(days.coerceIn(1, 30).toLong()))

                for (r in readAll(client, StepsRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName); bump(sourceSeen, source)
                    if (HealthStore.addMetric(db, source, "steps", r.count.toDouble(), "steps", r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) { added++; bump(sourceAdded, source) }
                }
                for (r in readAll(client, HeartRateRecord::class, start, end)) {
                    r.samples.forEach { sample ->
                        seen++
                        val source = sourceKey(r.metadata.dataOrigin.packageName); bump(sourceSeen, source)
                        val ext = r.metadata.id + "#" + sample.time.toEpochMilli()
                        if (HealthStore.addMetric(db, source, "heart_rate", sample.beatsPerMinute.toDouble(), "bpm", sample.time.toEpochMilli(), sample.time.toEpochMilli(), ext, meta(r.metadata.dataOrigin.packageName)) > 0) { added++; bump(sourceAdded, source) }
                    }
                }
                for (r in readAll(client, RestingHeartRateRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName); bump(sourceSeen, source)
                    if (HealthStore.addMetric(db, source, "resting_heart_rate", r.beatsPerMinute.toDouble(), "bpm", r.time.toEpochMilli(), r.time.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) { added++; bump(sourceAdded, source) }
                }
                for (r in readAll(client, OxygenSaturationRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName); bump(sourceSeen, source)
                    if (HealthStore.addMetric(db, source, "oxygen_saturation", r.percentage.value, "%", r.time.toEpochMilli(), r.time.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) { added++; bump(sourceAdded, source) }
                }
                for (r in readAll(client, WeightRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName); bump(sourceSeen, source)
                    if (HealthStore.addMetric(db, source, "weight", r.weight.inKilograms, "kg", r.time.toEpochMilli(), r.time.toEpochMilli(), r.metadata.id, meta(r.metadata.dataOrigin.packageName)) > 0) { added++; bump(sourceAdded, source) }
                }
                for (r in readAll(client, SleepSessionRecord::class, start, end)) {
                    seen++
                    val source = sourceKey(r.metadata.dataOrigin.packageName); bump(sourceSeen, source)
                    val hours = Duration.between(r.startTime, r.endTime).toMinutes().toDouble() / 60.0
                    val title = r.title?.replace("\"", "'") ?: ""
                    val metadata = "{\"origin_package\":\"${escape(r.metadata.dataOrigin.packageName)}\",\"title\":\"${escape(title)}\",\"stages\":${r.stages.size},\"gateway\":\"health_connect\"}"
                    if (HealthStore.addMetric(db, source, "sleep_duration", hours, "h", r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), r.metadata.id, metadata) > 0) { added++; bump(sourceAdded, source) }
                }

                val result = HealthSyncResult.ok(seen, added, sourceSeen, sourceAdded)
                HealthStore.finishSyncDetailed(db, runId, "health_connect", result)
                withContext(Dispatchers.Main) { callback.onResult(result) }
            } catch (t: Throwable) {
                val failure = HealthSyncPolicy.classify(t)
                val error = t.javaClass.simpleName + if (t.message.isNullOrBlank()) "" else ": ${t.message}"
                val result = HealthSyncResult.fail(seen, added, failure.state, failure.kind, error, failure.nextAction, sourceSeen, sourceAdded)
                try { if (db != null) HealthStore.finishSyncDetailed(db, runId, "health_connect", result) } catch (_: Throwable) {}
                withContext(Dispatchers.Main) { callback.onResult(result) }
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

    private fun bump(map: MutableMap<String, Int>, key: String) { map[key] = (map[key] ?: 0) + 1 }
    private fun sourceKey(originPackage: String): String = HealthStore.sourceKeyForOrigin(originPackage)
    private fun meta(originPackage: String): String = "{\"origin_package\":\"${escape(originPackage)}\",\"gateway\":\"health_connect\"}"
    private fun escape(s: String?): String = (s ?: "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
