package com.kareem.cortex.prime.ui.ideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.connect.Cluster
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.LocalMotionEnabled
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.lightBleed
import com.kareem.cortex.prime.ui.theme.mono
import com.kareem.cortex.prime.ui.theme.sans
import com.kareem.cortex.prime.ui.theme.staggeredRise

/** Ideas taking shape: related captures from different days, grouped on the phone. */
@Composable
fun IdeasScreen(clusters: List<Cluster>, titleOf: (Long) -> String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Ideas", style = sans(40.sp, FontWeight.Light), modifier = Modifier.staggeredRise(0)) }
        if (clusters.isEmpty()) item {
            Text("When three or more of your notes, voice notes or screenshots are about the same thing, they come together here.", style = sans(14.sp, color = Cx.Text2), modifier = Modifier.staggeredRise(1))
        }
        itemsIndexed(clusters) { i, c ->
            Column(Modifier.fillMaxWidth().glass().lightBleed(Cx.Gold, LocalMotionEnabled.current).staggeredRise(1 + minOf(i, 5)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TAKING SHAPE · ${c.memberIds.size}", style = mono(10.sp, Cx.Gold))
                Text(c.title, style = sans(20.sp, FontWeight.Medium))
                c.memberIds.takeLast(4).reversed().forEach { id -> val t = titleOf(id); Text("· $t", style = content(t, 13.sp), maxLines = 1) }
            }
        }
    }
}
