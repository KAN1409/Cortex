package com.kareem.cortex.prime.ui.theme

import androidx.compose.ui.graphics.Color

fun Long.c(): Color = Color(this)

object Cx {
    val Bg = CortexColors.Bg.c()
    val Text = CortexColors.TextPrimary.c()
    val Text2 = CortexColors.TextSecondary.c()
    val Label = CortexColors.Label.c()
    val Blue = CortexColors.Blue.c()
    val Violet = CortexColors.Violet.c()
    val Gold = CortexColors.Gold.c()
    val SheenA = CortexColors.SheenA.c()
    val SheenB = CortexColors.SheenB.c()
    val ActionTop = CortexColors.ActionTop.c()
    val ActionBottom = CortexColors.ActionBottom.c()
}
