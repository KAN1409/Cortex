package com.kareem.cortex;

import android.content.Context;
import java.io.File;

/**
 * Shadow-mode ASR candidate contract.
 * Implementations may evaluate the immutable source WAV, but benchmark code must never
 * overwrite the production transcript or mutate/delete the source recording.
 */
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
