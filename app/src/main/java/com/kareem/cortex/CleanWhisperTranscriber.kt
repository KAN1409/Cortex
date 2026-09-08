package com.kareem.cortex

import android.content.Context
import android.os.Build
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Clean benchmark profile: no lexical prompt, correction dictionary, rescue rule or sample-specific hint. */
class CleanWhisperTranscriber private constructor() {
    interface Callback { fun ok(result: TranscriptResult); fun fail(error: Exception) }
    companion object {
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        @JvmStatic fun modelReady(context:Context)=LocalAsrModelStore.ready(context.applicationContext)
        @JvmStatic fun transcribe(context:Context,audio:File,callback:Callback){val app=context.applicationContext;scope.launch {try{
            if(!audio.exists())throw IllegalArgumentException("Audio file not found")
            if(!Build.SUPPORTED_ABIS.any{it=="arm64-v8a"})throw UnsupportedOperationException("Local Whisper requires arm64-v8a")
            if(!audio.name.lowercase(Locale.US).endsWith(".wav"))throw UnsupportedOperationException("Cortex local benchmark expects WAV audio")
            if(!modelReady(app))throw IllegalStateException("No local Whisper model imported")
            val model=Whisper.loadModel(app,LocalAsrModelStore.modelFile(app).absolutePath)
            try {val decoded=Whisper.transcribe(model,audio.absolutePath,WhisperConfig(language="auto",translate=false,threads=Runtime.getRuntime().availableProcessors().coerceIn(2,6),maxSegmentLength=0,printTimestamps=true,initialPrompt="",suppressNonSpeechTokens=true));val out=TranscriptResult();out.text=decoded.text?.trim().orEmpty();if(out.text.isEmpty())throw IllegalStateException("Local Whisper returned an empty transcript");out.language="auto";out.engine="whisper_cpp_clean";out.version="clean-1";out.durationMs=wavDurationMs(audio);for(s in decoded.segments){val t=s.text?.trim().orEmpty();if(t.isNotEmpty())out.segments.add(TranscriptResult.Segment(s.startMs,s.endMs,t,-1f))};callback.ok(out)}finally{Whisper.releaseModel(model)}
        }catch(e:Exception){callback.fail(e)}}}
        private fun wavDurationMs(f:File):Long=try{val bytes=(f.length()-44L).coerceAtLeast(0L);bytes*1000L/(16000L*2L)}catch(_:Exception){0L}
    }
}
