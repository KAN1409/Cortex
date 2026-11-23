package com.kareem.cortex.prime.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

fun sans(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Cx.Text) =
    TextStyle(fontFamily = CortexType.Sans, fontSize = size, fontWeight = weight, color = color, textDirection = TextDirection.Content)

fun mono(size: TextUnit, color: Color = Cx.Label) =
    TextStyle(fontFamily = CortexType.Mono, fontSize = size, color = color, letterSpacing = 1.6.sp)

/** User content: picks Readex Pro and RTL automatically when the text is Arabic. */
fun content(text: String, size: TextUnit, color: Color = Cx.Text2): TextStyle {
    val arabic = text.any { it in '؀'..'ۿ' }
    return TextStyle(fontFamily = if (arabic) CortexType.Arabic else CortexType.Sans, fontSize = size, color = color, textDirection = TextDirection.Content, lineHeight = size * 1.55f)
}

/** Spec §7.1 greeting sheen: ice-blue → white → blue gradient sliding across the text. */
@Composable
fun SheenText(text: String, size: TextUnit, animate: Boolean, modifier: Modifier = Modifier) {
    val x = if (animate) rememberInfiniteTransition(label = "sheen").animateFloat(-600f, 900f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "x").value else 0f
    val brush = Brush.linearGradient(listOf(Cx.SheenA, Color(0xFFC9F6FF), Color.White, Cx.SheenB, Cx.SheenA), start = Offset(x, 0f), end = Offset(x + 700f, 0f))
    Text(text, modifier, style = TextStyle(brush = brush, fontFamily = CortexType.Sans, fontSize = size, fontWeight = FontWeight.ExtraLight, letterSpacing = (-1).sp))
}
