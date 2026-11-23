package com.kareem.cortex.prime.capture

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareParserTest {
    private val uriA = Uri.parse("content://x/a.png")
    private val uriB = Uri.parse("content://x/b.png")
    private val uriZip = Uri.parse("content://x/c.zip")

    private fun send(text: String? = null, stream: Uri? = null) = Intent(Intent.ACTION_SEND).apply {
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        stream?.let { putExtra(Intent.EXTRA_STREAM, it) }
    }

    @Test fun sendTextIsText() =
        assertEquals(listOf(ShareItem.Text("hi")), ShareParser.parse(send(text = "hi")) { null })

    @Test fun sendMultipleImages() {
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uriA, uriB))
        assertEquals(2, ShareParser.parse(i) { "image/png" }.size)
    }

    @Test fun unsupportedMimeYieldsNothing() =
        assertTrue(ShareParser.parse(send(stream = uriZip)) { "application/zip" }.isEmpty())

    @Test fun textAndStreamBothKept() =
        assertEquals(2, ShareParser.parse(send(text = "receipt", stream = uriA)) { "image/jpeg" }.size)
}
