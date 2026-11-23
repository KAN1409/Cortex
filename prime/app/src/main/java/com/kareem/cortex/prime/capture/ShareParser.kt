package com.kareem.cortex.prime.capture

import android.content.Intent
import android.net.Uri

sealed interface ShareItem {
    data class Text(val value: String) : ShareItem
    data class Stream(val uri: Uri, val mime: String) : ShareItem
}

object ShareParser {
    fun parse(intent: Intent, resolveMime: (Uri) -> String?): List<ShareItem> = TODO()
}
