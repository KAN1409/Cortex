package com.kareem.cortex.prime.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Free, on-device OCR: ML Kit (Latin, bundled) plus Tesseract with the Apache-2.0 "fast" Arabic model. */
class HybridOcrEngine(private val context: Context) : OcrEngine {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val tessLock = Mutex()
    private var tess: TessBaseAPI? = null

    private fun open(path: String): InputStream =
        if (path.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(path)) ?: error("image unreadable")
        else File(path).inputStream()

    private fun decodeShrunk(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        val scale = OcrPolicy.scaleFor(bounds.outWidth, bounds.outHeight)
        var sample = 1; while (sample * 2 <= (1f / scale)) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = open(path).use { BitmapFactory.decodeStream(it, null, opts) } ?: error("image could not be decoded")
        val exact = OcrPolicy.scaleFor(bmp.width, bmp.height)
        return if (exact < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * exact).toInt(), (bmp.height * exact).toInt(), true).also { if (it !== bmp) bmp.recycle() } else bmp
    }

    override suspend fun read(path: String): OcrOutput = withContext(Dispatchers.Default) {
        val bmp = withContext(Dispatchers.IO) { decodeShrunk(path) }
        try {
            val latinText = latin.process(InputImage.fromBitmap(bmp, 0)).await().text
            val arabicText = if (OcrPolicy.shouldTryArabic(latinText)) arabic(bmp) else ""
            val merged = OcrPolicy.merge(latinText, arabicText)
            OcrOutput(merged, if (merged.count(OcrPolicy::isArabic) * 2 > merged.count { it.isLetter() }) "ar" else "en")
        } finally { bmp.recycle() }
    }

    private suspend fun arabic(bmp: Bitmap): String = tessLock.withLock {
        withContext(Dispatchers.IO) {
            val api = tess ?: TessBaseAPI().also { a ->
                check(a.init(tessRoot().absolutePath, "ara")) { "Arabic OCR could not start" }
                tess = a
            }
            try {
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                api.setImage(bmp)
                api.getUTF8Text().orEmpty().lines().filter { l -> l.count(OcrPolicy::isArabic) >= 2 }.joinToString("\n")
            } finally { api.clear() }
        }
    }

    /** Copies the bundled traineddata once into app storage (Tesseract needs a real directory). */
    private fun tessRoot(): File {
        val root = File(context.filesDir, "ocr"); val data = File(root, "tessdata/ara.traineddata")
        if (!data.isFile) { data.parentFile?.mkdirs(); context.assets.open("tessdata/ara.traineddata").use { i -> data.outputStream().use { i.copyTo(it) } } }
        return root
    }
}
