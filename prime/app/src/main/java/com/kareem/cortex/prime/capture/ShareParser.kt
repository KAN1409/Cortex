package com.kareem.cortex.prime.capture

import android.content.Intent
import android.net.Uri
import android.os.Build

sealed interface ShareItem {
    data class Text(val value: String) : ShareItem
    data class Stream(val uri: Uri, val mime: String) : ShareItem
}

object ShareParser {
    private fun supported(mime: String) =
        mime.startsWith("image/") || mime == "application/pdf" || mime.startsWith("audio/") || mime == "text/plain"

    fun parse(intent: Intent, resolveMime: (Uri) -> String?): List<ShareItem> {
        val out = mutableListOf<ShareItem>()
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { out += ShareItem.Text(it) }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(stream(intent))
            Intent.ACTION_SEND_MULTIPLE -> streams(intent)
            else -> emptyList()
        }
        for (u in uris) {
            val mime = resolveMime(u) ?: intent.type ?: continue
            if (supported(mime)) out += ShareItem.Stream(u, mime)
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
}
