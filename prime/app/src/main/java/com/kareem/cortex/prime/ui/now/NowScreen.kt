package com.kareem.cortex.prime.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.ui.brain.BrainHero
import com.kareem.cortex.prime.ui.memory.MemoryRow
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.LocalMotionEnabled
import com.kareem.cortex.prime.ui.theme.SheenText
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.lightBleed
import com.kareem.cortex.prime.ui.theme.mono
import com.kareem.cortex.prime.ui.theme.sans
import com.kareem.cortex.prime.ui.theme.staggeredRise
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NowScreen(
    name: String?, recent: List<MemoryRow>, modifier: Modifier = Modifier,
    loops: List<com.kareem.cortex.prime.data.db.LoopWithSource> = emptyList(), pending: Int = 0,
    onDone: (Long) -> Unit = {}, onLater: (Long) -> Unit = {}, onConfirm: (Long) -> Unit = {}, onDismiss: (Long) -> Unit = {},
    onArea: (Long, com.kareem.cortex.prime.data.db.Area) -> Unit = { _, _ -> },
) {
    val motion = LocalMotionEnabled.current
    val now = LocalDateTime.now()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).glass(CircleShape).staggeredRise(0), contentAlignment = Alignment.Center) {
                Text(name?.trim()?.takeIf { it.isNotEmpty() }?.take(1)?.uppercase() ?: "·", style = sans(15.sp, FontWeight.SemiBold))
            }
            Spacer(Modifier.weight(1f))
            Text(now.format(DateTimeFormatter.ofPattern("EEE · d MMM", Locale.ENGLISH)).uppercase(), style = mono(11.sp), modifier = Modifier.staggeredRise(0))
        }
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            BrainHero(230.dp, animate = motion, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp).staggeredRise(1)) {
                val g = greeting(now.hour, name)
                val parts = g.split(", ", limit = 2)
                SheenText(parts[0] + if (parts.size > 1) "," else "", 40.sp, motion)
                if (parts.size > 1) Text(parts[1], style = sans(40.sp, FontWeight.Light))
            }
        }
        if (loops.isNotEmpty()) {
            Text("YOU MIGHT FORGET · ${loops.size}", style = mono(10.sp), modifier = Modifier.padding(top = 8.dp, bottom = 10.dp, start = 4.dp).staggeredRise(2))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                loops.take(6).forEachIndexed { i, it ->
                    val id = it.loop.id
                    LoopCard(it, highlight = i == 0, onDone = { onDone(id) }, onLater = { onLater(id) }, onConfirm = { onConfirm(id) }, onDismiss = { onDismiss(id) }, onArea = { onArea(id, it.loop.area) }, modifier = Modifier.staggeredRise(2 + i))
                }
            }
        }
        Text("RECENTLY CAPTURED", style = mono(10.sp), modifier = Modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp).staggeredRise(3))
        if (recent.isEmpty()) {
            Box(Modifier.fillMaxWidth().glass().staggeredRise(3).padding(18.dp)) {
                Text("Nothing captured yet. Tap the glass button to record a voice note, long-press it to write an idea, or share something into Cortex.", style = sans(14.sp, color = Cx.Text2))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recent.forEachIndexed { i, r ->
                    Column(Modifier.fillMaxWidth().glass().lightBleed(if (i == 0) Cx.Violet else Cx.Blue, motion).staggeredRise(3 + i).padding(16.dp)) {
                        Text(r.title, style = content(r.title, 16.sp, Cx.Text), maxLines = 2)
                        Text(r.label.text, style = mono(10.sp), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp, bottom = 120.dp).staggeredRise(6), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(Cx.Gold))
            Text(
                when {
                    pending > 0 -> "Cortex is still reading $pending ${if (pending == 1) "item" else "items"}."
                    loops.isEmpty() -> "Nothing you're likely to forget right now."
                    else -> "Everything captured has been read."
                },
                style = sans(13.sp, color = Cx.Label), modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
