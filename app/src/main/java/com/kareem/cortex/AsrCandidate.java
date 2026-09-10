package com.kareem.cortex;

import android.content.Context;
import java.io.File;

/** Shadow-only ASR candidate. It must never modify the source WAV or stored Cortex transcript. */
public interface AsrCandidate {
    String id();
    String displayName();
    boolean isReady(Context context);
    void transcribe(Context context, File sourceWav, Callback callback);

    interface Callback {
        void ok(TranscriptResult result, long latencyMs);
        void fail(Exception error, long latencyMs);
    }
}
