package com.kareem.cortex.prime.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Spec §7.1 glass: 8.5%→2.5% white body, bright top edge, dark bottom edge, deep shadow. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp)): Modifier = this
    .shadow(24.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.65f), spotColor = Color.Black.copy(alpha = 0.65f))
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.085f), Color.White.copy(alpha = 0.025f))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f))), shape)

/** Coloured light glowing from beneath a card; breathes 0.55→1.0 over 5 s when motion is on. */
fun Modifier.lightBleed(color: Color, breathing: Boolean): Modifier = composed {
    val alpha = if (breathing) {
        rememberInfiniteTransition(label = "bleed").animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
        ).value
    } else 0.8f
    drawBehind {
        val c = Offset(size.width * 0.5f, size.height * 1.05f)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.75f * alpha), color.copy(alpha = 0.22f * alpha), Color.Transparent), c, size.width * 0.55f), size.width * 0.55f, c)
    }
}

/** Spec §7.1 horizon: a black planet whose rim glows blue→violet at the bottom of the screen. */
@Composable
fun HorizonBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        // top ambient glow
        drawCircle(Brush.radialGradient(listOf(Cx.Blue.copy(alpha = 0.28f), Cx.Violet.copy(alpha = 0.10f), Color.Transparent), Offset(w / 2, -h * 0.08f), w * 0.8f), w * 0.8f, Offset(w / 2, -h * 0.08f))
        // planet rim glow
        val r = w * 1.35f; val center = Offset(w / 2, h + r * 0.78f)
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Cx.Violet.copy(alpha = 0.45f), Cx.Blue.copy(alpha = 0.75f), Color.Transparent), center, r * 1.12f, ), r * 1.12f, center)
        drawCircle(Color.Black, r, center)
        drawArc(Color(0xCCC8DCFF), 180f, 180f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}
