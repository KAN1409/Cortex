package com.kareem.cortex.prime.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kareem.cortex.prime.AppGraph
import com.kareem.cortex.prime.data.evidence.CaptureResult
import com.kareem.cortex.prime.ui.brain.BrainHero
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.LocalMotionEnabled
import com.kareem.cortex.prime.ui.theme.SheenText
import com.kareem.cortex.prime.ui.theme.content
import com.kareem.cortex.prime.ui.theme.glass
import com.kareem.cortex.prime.ui.theme.mono
import com.kareem.cortex.prime.ui.theme.sans
import com.kareem.cortex.prime.ui.theme.staggeredRise
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface Phase {
    data object Listening : Phase
    data class Saved(val evidenceId: Long) : Phase
    data class Message(val text: String) : Phase
}

@Composable
fun RecordScreen(graph: AppGraph, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val motion = LocalMotionEnabled.current
    val recorder = remember { AudioRecorder(context.cacheDir.resolve("rec")) }
    var phase by remember { mutableStateOf<Phase>(Phase.Listening) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val bars = remember { mutableStateListOf<Float>().apply { repeat(32) { add(0.05f) } } }

    DisposableEffect(Unit) { recorder.start(); onDispose { if (phase == Phase.Listening) recorder.stop() } }
    LaunchedEffect(phase) {
        val start = System.currentTimeMillis()
        while (phase == Phase.Listening) { delay(80); elapsed = System.currentTimeMillis() - start; bars.removeAt(0); bars.add(recorder.amplitude().coerceIn(0.04f, 1f)) }
    }

    fun stop() {
        when (val r = recorder.stop()) {
            RecordingResult.TooShort -> phase = Phase.Message("Too short to save")
            is RecordingResult.Saved -> scope.launch {
                val saved = graph.evidence.captureStream({ r.file.inputStream() }, "audio/wav", "Cortex voice")
                r.file.delete()
                if (!graph.models.isReady()) graph.models.start()
                graph.kickJobs()
                phase = when (saved) { is CaptureResult.Saved -> Phase.Saved(saved.id); is CaptureResult.Rejected -> Phase.Message(saved.message) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF293EB4).copy(alpha = 0.55f), Color(0xFF0A0C28), Color.Black), center = Offset(540f, 640f), radius = 1400f))) {
        when (val p = phase) {
            Phase.Listening -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LISTENING · ON THIS PHONE", style = mono(11.sp, Cx.SheenB), modifier = Modifier.padding(top = 24.dp))
                Spacer(Modifier.height(24.dp))
                BrainHero(320.dp, animate = motion, fast = true)
                Canvas(Modifier.fillMaxWidth().height(64.dp).padding(top = 12.dp)) {
                    val w = size.width / bars.size
                    bars.forEachIndexed { i, a ->
                        val h = size.height * a; val x = i * w + w / 2
                        drawLine(Brush.verticalGradient(listOf(Cx.SheenA, Cx.Blue, Cx.Violet)), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), w * 0.45f, StrokeCap.Round)
                    }
                }
                Text("%d:%02d".format(elapsed / 60_000, elapsed / 1000 % 60), style = mono(16.sp, Cx.Text), modifier = Modifier.padding(top = 10.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(72.dp).glass(CircleShape).clickable { stop() }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(22.dp).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFBFD0FF))), RoundedCornerShape(6.dp)))
                }
                Text("Tap to stop", style = sans(13.sp, color = Cx.Label), modifier = Modifier.padding(top = 10.dp, bottom = 16.dp))
            }
            is Phase.Saved -> UnderstoodSheet(graph, p.evidenceId, onClose)
            is Phase.Message -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.text, style = sans(22.sp, FontWeight.Light))
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Cx.ActionBottom), shape = RoundedCornerShape(26.dp), modifier = Modifier.padding(top = 20.dp)) { Text("Close", style = sans(15.sp, FontWeight.SemiBold)) }
            }
        }
    }
}

/** Slides up after a note is saved. Shows only what is true: transcript when ready, otherwise the real state. */
@Composable
private fun UnderstoodSheet(graph: AppGraph, evidenceId: Long, onClose: () -> Unit) {
    val transcript by remember(evidenceId) { graph.db.perception().observeForEvidence(evidenceId).map { l -> l.firstOrNull { it.kind == "transcript" }?.text } }.collectAsState(null)
    val model by graph.models.status.collectAsState()
    LaunchedEffect(Unit) { while (true) { graph.models.refresh(); delay(1500) } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Spacer(Modifier.weight(0.3f))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(36.dp)).staggeredRise(0).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val t = transcript
            if (t != null) {
                SheenText("Understood.", 32.sp, LocalMotionEnabled.current)
                Text(t.ifBlank { "No speech found" }, style = content(t, 17.sp, Cx.Text))
                Text("Reminders from your notes arrive in a later update.", style = sans(13.sp, color = Cx.Label))
            } else {
                Text("Saved.", style = sans(32.sp, FontWeight.Light))
                Text(
                    when (val m = model) {
                        ModelStatus.Ready -> "Transcribing on your phone…"
                        is ModelStatus.Downloading -> "Downloading the voice model once over Wi-Fi (${(m.progress * 100).toInt()}%). Your note will be transcribed automatically."
                        is ModelStatus.Failed -> m.reason
                        ModelStatus.Missing -> "Waiting for Wi-Fi to download the voice model. Your note is safe and will be transcribed automatically."
                    },
                    style = sans(15.sp, color = Cx.Text2),
                )
            }
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Cx.ActionBottom), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done", style = sans(15.sp, FontWeight.SemiBold)) }
        }
        Spacer(Modifier.weight(1f))
    }
}
