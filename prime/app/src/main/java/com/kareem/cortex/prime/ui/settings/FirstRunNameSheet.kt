package com.kareem.cortex.prime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.sans

/** Asked once; "" (Skip) also counts as answered. Never read from the account. */
@Composable
fun FirstRunNameSheet(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).fillMaxWidth().glass().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What should Cortex call you?", style = sans(22.sp, FontWeight.Light))
            BasicTextField(name, { name = it }, singleLine = true, textStyle = content(name, 18.sp, Cx.Text), cursorBrush = SolidColor(Cx.SheenA),
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDone("") }) { Text("Skip", style = sans(14.sp, color = Cx.Label)) }
                Button(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank(), shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cx.ActionBottom), modifier = Modifier.height(46.dp)) { Text("Continue", style = sans(14.sp, FontWeight.SemiBold)) }
            }
        }
    }
}
