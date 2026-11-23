package com.kareem.cortex.prime.ui.now

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.LoopState
import com.kareem.cortex.prime.data.db.LoopWithSource
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.LocalMotionEnabled
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.lightBleed
import com.kareem.cortex.prime.ui.theme.mono
import com.kareem.cortex.prime.ui.theme.sans
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun dueLabel(dueAt: Long?, precision: String?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (dueAt == null) return "No date"
    val t = Instant.ofEpochMilli(dueAt).atZone(zone)
    val day = when (t.toLocalDate()) {
        today -> "Today"; today.plusDays(1) -> "Tomorrow"
        else -> if (t.toLocalDate().isBefore(today.plusDays(7))) t.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)) else t.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
    return if (precision == "TIME") "$day · ${t.format(DateTimeFormatter.ofPattern("HH:mm"))}" else day
}

@Composable
fun LoopCard(item: LoopWithSource, highlight: Boolean, onDone: () -> Unit, onLater: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, onArea: () -> Unit, modifier: Modifier = Modifier) {
    val l = item.loop
    val from = when (item.sourceKind) { EvidenceKind.AUDIO -> "From a voice note"; EvidenceKind.IMAGE -> "From a screenshot"; else -> "From a note" }
    Column(modifier.fillMaxWidth().glass().let { if (highlight) it.lightBleed(Cx.Violet, LocalMotionEnabled.current) else it }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (l.state == LoopState.UNCONFIRMED) "Is this a reminder?" else "You might forget", style = sans(13.sp, FontWeight.Medium, Cx.SheenA))
            Spacer(Modifier.weight(1f))
            Text(l.area.name.lowercase().replaceFirstChar { it.uppercase() }, style = sans(12.sp, FontWeight.Medium, Cx.Gold),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Cx.Gold.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).clickable(onClick = onArea).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Text(l.action, style = content(l.action, 19.sp, Cx.Text))
        Text("${dueLabel(l.dueAt, l.duePrecision)} · $from", style = mono(11.sp))
        val quote = l.sourceText.substring(l.spanStart.coerceIn(0, l.sourceText.length), l.spanEnd.coerceIn(0, l.sourceText.length))
        if (quote.isNotBlank() && quote != l.action) Text("«$quote»", style = content(quote, 13.sp), maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.state == LoopState.UNCONFIRMED) {
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Cx.ActionBottom), shape = RoundedCornerShape(20.dp), modifier = Modifier.height(40.dp)) { Text("Yes, remind me", style = sans(13.sp, FontWeight.SemiBold)) }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(20.dp), modifier = Modifier.height(40.dp)) { Text("No", style = sans(13.sp, color = Cx.Text)) }
            } else {
                Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Cx.ActionBottom), shape = RoundedCornerShape(20.dp), modifier = Modifier.height(40.dp)) { Text("Done", style = sans(13.sp, FontWeight.SemiBold)) }
                OutlinedButton(onClick = onLater, shape = RoundedCornerShape(20.dp), modifier = Modifier.height(40.dp)) { Text("Tomorrow", style = sans(13.sp, color = Cx.Text)) }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(20.dp), modifier = Modifier.height(40.dp)) { Text("Not this", style = sans(13.sp, color = Cx.Label)) }
            }
        }
    }
}
