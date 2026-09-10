package com.kareem.cortex.visualmemory.data.ocr

import android.content.Context
import android.net.Uri

class HybridOcrEngine(context: Context) : OcrEngine {
    override val id: String = ID

    private val latin = MlKitOcrEngine(context)
    private val arabic = TesseractArabicOcrEngine(context)

    override suspend fun recognize(uri: Uri): OcrResult {
        val latinResult = runCatching { latin.recognize(uri) }
        val latinText = latinResult.getOrNull()?.rawText.orEmpty()

        val shouldTryArabic =
            containsArabicScript(latinText) ||
            normalizeOcrText(latinText).length < MIN_LATIN_SIGNAL_LENGTH

        val arabicResult = if (shouldTryArabic) {
            runCatching { arabic.recognize(uri) }
        } else {
            Result.success(OcrResult("", ""))
        }

        if (latinResult.isFailure && arabicResult.isFailure) {
            val latinError = latinResult.exceptionOrNull()?.message.orEmpty()
            val arabicError = arabicResult.exceptionOrNull()?.message.orEmpty()
            error("Both OCR engines failed. Latin: $latinError; Arabic: $arabicError")
        }

        val merged = mergeDistinctText(
            latinText,
            arabicResult.getOrNull()?.rawText.orEmpty()
        )
        return OcrResult(
            rawText = merged,
            normalizedText = normalizeOcrText(merged)
        )
    }

    override fun close() {
        latin.close()
        arabic.close()
    }

    private fun mergeDistinctText(latinText: String, arabicText: String): String {
        val seen = linkedSetOf<String>()
        return sequenceOf(latinText, arabicText)
            .flatMap { it.lineSequence() }
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter { line -> seen.add(normalizeOcrText(line)) }
            .joinToString("\n")
    }

    private fun containsArabicScript(text: String): Boolean =
        text.count(::isArabicLetter) >= MIN_ARABIC_SIGNAL_LETTERS

    private fun isArabicLetter(char: Char): Boolean {
        if (!char.isLetter()) return false
        return Character.UnicodeBlock.of(char) in ARABIC_BLOCKS
    }

    companion object {
        const val ID = "hybrid-mlkit16.0.1-tesseract5.5.1-ara-best-v3-gated"
        private const val MIN_LATIN_SIGNAL_LENGTH = 12
        private const val MIN_ARABIC_SIGNAL_LETTERS = 2

        private val ARABIC_BLOCKS = setOf(
            Character.UnicodeBlock.ARABIC,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B,
            Character.UnicodeBlock.ARABIC_SUPPLEMENT,
            Character.UnicodeBlock.ARABIC_EXTENDED_A
        )
    }
}
