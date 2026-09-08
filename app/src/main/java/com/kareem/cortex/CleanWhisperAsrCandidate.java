package com.kareem.cortex;

import android.content.Context;
import java.io.File;

public final class CleanWhisperAsrCandidate implements AsrCandidate {
    public String id(){return "whisper_clean";}
    public String displayName(){return "Clean Local Whisper";}
    public boolean isReady(Context c){return CleanWhisperTranscriber.modelReady(c);}
    public void transcribe(Context c,File wav,Callback cb){long started=android.os.SystemClock.elapsedRealtime();CleanWhisperTranscriber.transcribe(c,wav,new CleanWhisperTranscriber.Callback(){public void ok(TranscriptResult r){cb.ok(r,android.os.SystemClock.elapsedRealtime()-started);}public void fail(Exception e){cb.fail(e,android.os.SystemClock.elapsedRealtime()-started);}});}
}
