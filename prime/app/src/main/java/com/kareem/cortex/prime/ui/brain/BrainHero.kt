package com.kareem.cortex.prime.ui.brain

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kareem.cortex.prime.R
import kotlin.math.sin
import kotlin.random.Random

/** Spec §7.3: the pre-rendered glass brain (transparent animated WebP), floating, with drifting motes. */
@Composable
fun BrainHero(sizeDp: Dp, animate: Boolean, modifier: Modifier = Modifier, fast: Boolean = false) {
    val context = LocalContext.current
    val drawable = remember { ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, if (fast) R.raw.brain_fast else R.raw.brain_loop)) }
    val t = rememberInfiniteTransition(label = "brain")
    val phase = if (animate) t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "p").value else 0f
    val floatPx = with(LocalDensity.current) { 6.dp.toPx() }
    val motes = remember { List(12) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Box(modifier.size(sizeDp, sizeDp * 0.84f)) {
        AndroidView(
            factory = { ImageView(it).apply { setImageDrawable(drawable) } },
            update = { (drawable as? AnimatedImageDrawable)?.let { d -> if (animate) d.start() else d.stop() } },
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = sin(phase * 2 * Math.PI).toFloat() * floatPx },
        )
        if (animate) Canvas(Modifier.fillMaxSize()) {
            motes.forEachIndexed { i, (x, y, s) ->
                val p = (phase + s) % 1f
                val alpha = if (p < 0.2f) p / 0.2f else if (p > 0.8f) (1f - p) / 0.2f else 1f
                val c = if (i % 3 == 0) Color(0xFFC9A8FF) else if (i % 3 == 1) Color(0xFF8FB8FF) else Color.White
                val o = Offset(size.width * x + p * 14f, size.height * y - p * 26f)
                drawCircle(c.copy(alpha = alpha * 0.35f), radius = 2.5f + s * 3f + 4f, center = o)
                drawCircle(c.copy(alpha = alpha), radius = 1.2f + s * 1.6f, center = o)
            }
        }
    }
}
