package com.kareem.cortex.prime.ui.shell

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.LocalMotionEnabled
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.sans

enum class Tab(val label: String) { NOW("Now"), MEMORY("Memory") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassNavBar(current: Tab, onTab: (Tab) -> Unit, onCapture: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    Row(
        modifier.fillMaxWidth().height(76.dp).glass(RoundedCornerShape(38.dp)).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Tab.NOW, current, onTab)
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            if (motion) {
                val p = rememberInfiniteTransition(label = "ping").animateFloat(1f, 1.7f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "s").value
                Box(Modifier.size(60.dp).scale(p).border(1.dp, Cx.SheenA.copy(alpha = (1.7f - p) / 0.7f * 0.6f), CircleShape))
            }
            // Glossy glass marble: tap or long-press adds an idea (voice arrives in milestone 2).
            Box(
                Modifier.size(62.dp).shadow(22.dp, CircleShape, spotColor = Cx.Blue).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Cx.SheenB.copy(alpha = 0.55f), Cx.Blue.copy(alpha = 0.8f), Color(0xFF141850)), center = Offset(60f, 50f), radius = 190f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .combinedClickable(onClick = onCapture, onLongClick = onCapture)
                    .semantics { contentDescription = "Add an idea" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val s = 3.dp.toPx()
                    drawLine(Color.White, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), s, StrokeCap.Round)
                    drawLine(Color.White, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), s, StrokeCap.Round)
                }
            }
        }
        NavItem(Tab.MEMORY, current, onTab)
    }
}

@Composable
private fun NavItem(tab: Tab, current: Tab, onTab: (Tab) -> Unit) {
    val selected = tab == current
    Column(
        Modifier.width(88.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).clickable { onTab(tab) }.padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tab.label, style = sans(14.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal, if (selected) Cx.Text else Cx.Label))
        if (selected) Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(Cx.SheenA))
    }
}
