package com.kareem.cortex.prime.ui.memory

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.mono
import com.kareem.cortex.prime.ui.theme.sans
import com.kareem.cortex.prime.ui.theme.staggeredRise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MemoryScreen(rows: Map<LocalDate, List<MemoryRow>>, onRetry: (Long) -> Unit, modifier: Modifier = Modifier, screenshotsAllowed: Boolean = true, onAllowScreenshots: () -> Unit = {}) {
    val today = LocalDate.now()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Memory", style = sans(40.sp, FontWeight.Light), modifier = Modifier.staggeredRise(0).padding(bottom = 6.dp)) }
        if (!screenshotsAllowed) item {
            Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
                Text("Let Cortex read your screenshots", style = sans(16.sp, FontWeight.Medium))
                Text("Read-only, on this phone. Nothing is uploaded.", style = sans(13.sp, color = Cx.Label), modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = onAllowScreenshots) { Text("Allow", style = sans(14.sp, FontWeight.SemiBold, Cx.SheenB)) }
            }
        }
        if (rows.isEmpty()) item {
            Text("Everything you capture appears here, newest first.", style = sans(14.sp, color = Cx.Text2), modifier = Modifier.staggeredRise(1))
        }
        var index = 1
        rows.forEach { (day, list) ->
            item(key = "h$day") {
                val label = when (day) { today -> "TODAY"; today.minusDays(1) -> "YESTERDAY"; else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase() }
                Text(label, style = mono(10.sp), modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            }
            itemsIndexed(list, key = { _, r -> r.id }) { _, r ->
                val i = index++
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).staggeredRise(minOf(i, 6)).padding(16.dp)) {
                    Text(r.title, style = content(r.title, 16.sp, Cx.Text), maxLines = 2)
                    r.preview?.let { Text(it, style = content(it, 13.sp), maxLines = 3, modifier = Modifier.padding(top = 4.dp)) }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val c = when (r.label) { ProcessingLabel.FAILED -> Cx.Gold; ProcessingLabel.DONE -> Cx.SheenA; else -> Cx.Label }
                        Text(r.label.text, style = sans(12.sp, FontWeight.Medium, c), modifier = Modifier.border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                        Spacer(Modifier.weight(1f))
                        if (r.label == ProcessingLabel.FAILED) TextButton(onClick = { onRetry(r.id) }) { Text("Retry", style = sans(13.sp, FontWeight.SemiBold, Cx.SheenB)) }
                    }
                    if (r.label == ProcessingLabel.FAILED && r.error != null) Text("Why: ${r.error}", style = sans(12.sp, color = Cx.Label), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
