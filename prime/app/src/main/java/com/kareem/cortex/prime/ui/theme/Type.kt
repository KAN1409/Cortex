package com.kareem.cortex.prime.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.kareem.cortex.prime.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

/** Bundled OFL fonts (Geist, Geist Mono, Readex Pro), variable weight; no network or Play services needed. */
object CortexType {
    val Sans = variable(R.font.geist, 200, 300, 400, 500, 600)
    val Mono = variable(R.font.geist_mono, 400, 500)
    val Arabic = variable(R.font.readex_pro, 300, 400, 500)
}
