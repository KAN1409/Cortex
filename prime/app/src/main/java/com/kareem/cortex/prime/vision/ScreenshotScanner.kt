package com.kareem.cortex.prime.vision

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.kareem.cortex.prime.data.evidence.EvidenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Finds screenshots in MediaStore (read-only), newest first, and records them as evidence by reference. */
class ScreenshotScanner(private val context: Context, private val repo: EvidenceRepository) {
    private val prefs = context.getSharedPreferences("cortex_scan", Context.MODE_PRIVATE)

    /** Returns how many new screenshots were recorded. Never copies the image itself. */
    suspend fun scan(limit: Int = 5000): Int = withContext(Dispatchers.IO) {
        val since = prefs.getLong(KEY_LAST, 0L)
        var newest = since; var added = 0
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Images.Media.DATE_ADDED} > ?", arrayOf((since / 1000).toString()),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext() && added < limit) {
                if (!isScreenshot(c.getString(1), c.getString(2))) continue
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                val at = c.getLong(3) * 1000
                newest = maxOf(newest, at)
                val r = runCatching { repo.captureReference(uri.toString(), c.getString(4) ?: "image/png", "Screenshots", at) { context.contentResolver.openInputStream(uri)!! } }
                if (r.getOrNull() is com.kareem.cortex.prime.data.evidence.CaptureResult.Saved) added++
            }
        }
        prefs.edit().putLong(KEY_LAST, newest).apply()
        added
    }

    private companion object { const val KEY_LAST = "last_screenshot_added_ms" }
}
