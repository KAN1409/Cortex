package com.kareem.cortex.prime.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.sans

/** Quick text idea. Save stays disabled until there is something real to save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaSheet(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = androidx.compose.ui.graphics.Color(0xFF0B0D16), shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 18.dp).navigationBarsPadding()) {
            Text("New idea", style = sans(26.sp, FontWeight.Light))
            Text("Write it the way you'd say it — Arabic, English or both.", style = sans(13.sp, color = Cx.Label), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = content(text, 17.sp, Cx.Text), cursorBrush = SolidColor(Cx.SheenA),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).glass(RoundedCornerShape(22.dp)).padding(16.dp),
            )
            Button(
                onClick = { onSave(text) }, enabled = ideaSaveEnabled(text),
                colors = ButtonDefaults.buttonColors(containerColor = Cx.ActionBottom, disabledContainerColor = Cx.ActionBottom.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
            ) { Text("Save to Cortex", style = sans(15.sp, FontWeight.SemiBold)) }
        }
    }
}
