package com.kareem.cortex.prime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.mono
import com.kareem.cortex.prime.ui.theme.sans

data class SettingsState(val name: String, val reduceMotion: Boolean, val model: String, val screenshots: Boolean, val exactAlarms: Boolean, val message: String?)

@Composable
fun SettingsScreen(
    s: SettingsState, onClose: () -> Unit, onName: (String) -> Unit, onReduceMotion: (Boolean) -> Unit, onDownloadModel: () -> Unit,
    onScreenshots: () -> Unit, onBackup: (CharArray) -> Unit, onRestore: (CharArray) -> Unit,
) {
    var name by remember(s.name) { mutableStateOf(s.name) }
    var pass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = sans(34.sp, FontWeight.Light), modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", style = sans(15.sp, FontWeight.SemiBold, Cx.SheenB)) }
        }
        s.message?.let { Text(it, style = sans(14.sp, FontWeight.Medium, Cx.Gold)) }
        Section("YOU") {
            BasicTextField(name, { name = it }, singleLine = true, textStyle = content(name, 16.sp, Cx.Text), cursorBrush = SolidColor(Cx.SheenA), modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp))
            TextButton(onClick = { onName(name.trim()) }) { Text("Save name", style = sans(14.sp, FontWeight.SemiBold, Cx.SheenB)) }
        }
        Section("MOTION") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reduce motion", style = sans(15.sp), modifier = Modifier.weight(1f))
                Switch(s.reduceMotion, onReduceMotion, colors = SwitchDefaults.colors(checkedTrackColor = Cx.ActionBottom))
            }
        }
        Section("ON THIS PHONE") {
            Text("Voice model: ${s.model}", style = sans(14.sp, color = Cx.Text2))
            TextButton(onClick = onDownloadModel) { Text("Download voice model (Wi-Fi, 190 MB, once)", style = sans(14.sp, FontWeight.SemiBold, Cx.SheenB)) }
            Text("Screenshots: ${if (s.screenshots) "Cortex can read them" else "Not allowed yet"}", style = sans(14.sp, color = Cx.Text2))
            if (!s.screenshots) TextButton(onClick = onScreenshots) { Text("Allow screenshots", style = sans(14.sp, FontWeight.SemiBold, Cx.SheenB)) }
            Text("Reminders: ${if (s.exactAlarms) "exact times" else "approximate times (exact alarms not allowed)"}", style = sans(14.sp, color = Cx.Text2))
        }
        Section("BACKUP TO GOOGLE DRIVE") {
            Text("An encrypted file you save yourself — pick Drive in the file picker. Nothing is uploaded automatically.", style = sans(13.sp, color = Cx.Label))
            BasicTextField(pass, { pass = it }, singleLine = true, textStyle = sans(16.sp), visualTransformation = PasswordVisualTransformation(), cursorBrush = SolidColor(Cx.SheenA),
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp), decorationBox = { inner -> if (pass.isEmpty()) Text("Backup passphrase", style = sans(15.sp, color = Cx.Label)); inner() })
            Row {
                TextButton(onClick = { onBackup(pass.toCharArray()) }, enabled = pass.length >= 6) { Text("Back up…", style = sans(14.sp, FontWeight.SemiBold, Cx.SheenB)) }
                TextButton(onClick = { onRestore(pass.toCharArray()) }, enabled = pass.length >= 6) { Text("Restore…", style = sans(14.sp, FontWeight.SemiBold, Cx.Gold)) }
            }
            Text("Passphrase of at least 6 characters. Without it the backup can't be opened.", style = sans(12.sp, color = Cx.Label))
        }
    }
}

@Composable
private fun Section(title: String, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = mono(10.sp)); body() }
}
