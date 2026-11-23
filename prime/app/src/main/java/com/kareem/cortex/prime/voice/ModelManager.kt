package com.kareem.cortex.prime.voice

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed interface ModelStatus {
    data object Missing : ModelStatus
    data class Downloading(val progress: Float) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}

/** Free Whisper model (MIT), downloaded once on unmetered networks, verified by size + SHA-256. */
class ModelManager(private val context: Context, val spec: ModelSpec = WHISPER_SMALL) {
    val file = File(context.filesDir, "models/${spec.fileName}")
    private val _status = MutableStateFlow<ModelStatus>(if (file.isFile && file.length() == spec.sizeBytes) ModelStatus.Ready else ModelStatus.Missing)
    val status: StateFlow<ModelStatus> = _status
    private val dm get() = context.getSystemService(DownloadManager::class.java)
    private val prefs get() = context.getSharedPreferences("cortex_models", Context.MODE_PRIVATE)

    fun isReady() = _status.value == ModelStatus.Ready

    fun start() {
        if (isReady() || prefs.getLong(KEY_ID, -1) >= 0) return
        val dest = File(context.getExternalFilesDir(null), spec.fileName).apply { delete() }
        val req = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle("Cortex voice model").setDescription("Downloading once over Wi-Fi")
            .setAllowedOverMetered(false).setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(dest))
        prefs.edit().putLong(KEY_ID, dm.enqueue(req)).apply()
        _status.value = ModelStatus.Downloading(0f)
    }

    /** Polls the download; on completion verifies and installs. Returns true when the model became Ready. */
    fun refresh(): Boolean {
        val id = prefs.getLong(KEY_ID, -1); if (id < 0) return false
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) { prefs.edit().remove(KEY_ID).apply(); _status.value = ModelStatus.Missing; return false }
            val state = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            when (state) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove(KEY_ID).apply()
                    val dl = File(context.getExternalFilesDir(null), spec.fileName)
                    if (!spec.verify(dl)) { dl.delete(); _status.value = ModelStatus.Failed("Download failed — will retry on Wi-Fi"); return false }
                    file.parentFile?.mkdirs()
                    dl.copyTo(file, overwrite = true); dl.delete()
                    _status.value = ModelStatus.Ready
                    return true
                }
                DownloadManager.STATUS_FAILED -> { prefs.edit().remove(KEY_ID).apply(); _status.value = ModelStatus.Failed("Download failed — will retry on Wi-Fi") }
                else -> _status.value = ModelStatus.Downloading((done.toFloat() / spec.sizeBytes).coerceIn(0f, 1f))
            }
        }
        return false
    }

    companion object {
        private const val KEY_ID = "download_id"
        // Size and SHA-256 come from the Hugging Face API (CI step "Model hashes"); see ledger.
        val WHISPER_SMALL = ModelSpec(
            fileName = "ggml-small-q5_1.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            sizeBytes = 0L,
            sha256 = "",
        )
    }
}
