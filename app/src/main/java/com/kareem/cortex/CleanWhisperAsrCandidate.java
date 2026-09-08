package com.kareem.cortex;

import android.content.Context;
import java.io.File;

/** Clean, prompt-free Whisper candidate for fair corpus scoring. */
public final class CleanWhisperAsrCandidate implements AsrCandidate {
    @Override public String id(){return "whisper_clean";}
    @Override public String displayName(){return "Clean Whisper";}
    @Override public boolean isReady(Context context){return CleanWhisperTranscriber.modelReady(context);}
    @Override public void transcribe(Context context, File sourceWav, Callback callback){
        final long started=android.os.SystemClock.elapsedRealtime();
        CleanWhisperTranscriber.transcribe(context,sourceWav,new CleanWhisperTranscriber.Callback(){
            @Override public void ok(TranscriptResult result){callback.ok(result,android.os.SystemClock.elapsedRealtime()-started);}
            @Override public void fail(Exception error){callback.fail(error,android.os.SystemClock.elapsedRealtime()-started);}
        });
    }
}
