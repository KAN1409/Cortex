package com.kareem.cortex;

import android.content.Context;
import java.io.File;

/** Adapter exposing the recovered local Whisper pipeline only as a shadow benchmark candidate. */
public final class LocalWhisperAsrCandidate implements AsrCandidate {
    @Override public String id() { return "local_whisper_recovered"; }
    @Override public String displayName() { return "Recovered Local Whisper"; }
    @Override public boolean isReady(Context context) {
        return MultilingualWhisperTranscriber.modelReady(context.getApplicationContext());
    }

    @Override public void transcribe(Context context, File sourceWav, Callback callback) {
        final long started = android.os.SystemClock.elapsedRealtime();
        MultilingualWhisperTranscriber.transcribe(context, sourceWav,
                new MultilingualWhisperTranscriber.Callback() {
                    @Override public void ok(TranscriptResult result) {
                        callback.ok(result, android.os.SystemClock.elapsedRealtime() - started);
                    }
                    @Override public void fail(Exception error) {
                        callback.fail(error, android.os.SystemClock.elapsedRealtime() - started);
                    }
                });
    }
}
