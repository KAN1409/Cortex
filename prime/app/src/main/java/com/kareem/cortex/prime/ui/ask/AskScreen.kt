package com.kareem.cortex.prime.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.mono
import com.kareem.cortex.prime.ui.theme.sans
import com.kareem.cortex.prime.ui.theme.staggeredRise

data class Cited(val title: String, val quote: String, val when_: String, val source: String)

/** Ask your memory. Answers are only what you captured, each one cited; Cortex never invents an answer. */
@Composable
fun AskScreen(ask: (String) -> List<Cited>) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Cited>?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 22.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ask", style = sans(40.sp, FontWeight.Light), modifier = Modifier.staggeredRise(0))
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(q, { q = it }, singleLine = true, textStyle = content(q, 16.sp, Cx.Text), cursorBrush = SolidColor(Cx.SheenA),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { results = ask(q) }),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).padding(vertical = 16.dp))
            TextButton(onClick = { results = ask(q) }, enabled = q.isNotBlank()) { Text("Ask", style = sans(14.sp, FontWeight.SemiBold, Cx.SheenB)) }
        }
        val r = results
        when {
            r == null -> Text("For example: \"When did I say I'd pay the school fees?\" Cortex answers only from what you captured, and shows where it came from.", style = sans(13.sp, color = Cx.Label))
            r.isEmpty() -> Text("Nothing you captured matches that.", style = sans(15.sp, color = Cx.Text2))
            else -> {
                Text("FROM YOUR MEMORY · ${r.size}", style = mono(10.sp))
                r.forEachIndexed { i, c ->
                    Column(Modifier.fillMaxWidth().glass().staggeredRise(i).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.title, style = content(c.title, 16.sp, Cx.Text))
                        if (c.quote.isNotBlank() && c.quote != c.title) Text(c.quote, style = content(c.quote, 13.sp), maxLines = 4)
                        Text("${c.when_} · ${c.source}", style = mono(10.sp))
                    }
                }
            }
        }
    }
}
