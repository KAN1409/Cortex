package com.kareem.cortex.prime.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** True when ambient motion may run: system animations on and the in-app reduce-motion override off. */
val LocalMotionEnabled = staticCompositionLocalOf { true }

fun systemAnimationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/** Spec §7.2 entry: rise 22 dp + fade over 1000 ms, staggered 150 ms per index; plain fade when motion is off. */
fun Modifier.staggeredRise(index: Int): Modifier = composed {
    val motion = LocalMotionEnabled.current
    val dy = with(LocalDensity.current) { 22.dp.toPx() }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(durationMillis = if (motion) 1000 else 250, delayMillis = if (motion) index * 150 else 0, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = p.value
        translationY = if (motion) (1f - p.value) * dy else 0f
    }
}
