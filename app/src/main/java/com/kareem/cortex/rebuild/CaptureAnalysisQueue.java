package com.kareem.cortex.rebuild;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fresh equivalent of the old single-lane Cortex audio analysis queue.
 * It keeps ASR off the UI thread and enforces the same 240 second voice watchdog.
 */
public final class CaptureAnalysisQueue {
    public interface Callback { void complete(long evidenceId, TranscriptResult result, Exception error); }
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> new Thread(r, "cortex-capture-analysis"));
    private static final ExecutorService ASR = Executors.newCachedThreadPool(r -> new Thread(r, "cortex-asr"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long AUDIO_TIMEOUT_SEC = 240;

    private CaptureAnalysisQueue() {}

    public static void analyzeVoice(Context context, long evidenceId, Callback callback) {
        Context app = context.getApplicationContext();
        QUEUE.execute(() -> {
            CortexDb db = null;
            TranscriptResult result = null;
            Exception error = null;
            try {
                db = new CortexDb(app);
                CortexDb.AttachmentEvidence evidence = db.attachmentEvidence(evidenceId);
                if (evidence == null || evidence.path.isEmpty()) throw new IllegalArgumentException("Voice evidence file missing");
                File audio = new File(evidence.path);
                if (!audio.exists() || audio.length() == 0) throw new IllegalArgumentException("Voice audio file missing");
                db.markEvidenceState(evidenceId, "transcribing", "Voice recording · transcription in progress");
                Future<TranscriptResult> future = ASR.submit((Callable<TranscriptResult>) () -> VoiceTranscriptionEngine.transcribe(app, audio));
                try { result = future.get(AUDIO_TIMEOUT_SEC, TimeUnit.SECONDS); }
                catch (TimeoutException timeout) { future.cancel(true); throw new TimeoutException("Audio transcription timed out"); }
                db.saveVoiceTranscript(evidenceId, result);
            } catch (Exception e) {
                error = e;
                if (db != null) {
                    String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    db.markEvidenceState(evidenceId, "transcription_failed", "Voice recording · transcription failed: " + compact(detail));
                }
            } finally { if (db != null) try { db.close(); } catch (Throwable ignored) {} }
            TranscriptResult finalResult = result; Exception finalError = error;
            if (callback != null) MAIN.post(() -> callback.complete(evidenceId, finalResult, finalError));
        });
    }

    private static String compact(String value) {
        String v = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return v.length() <= 180 ? v : v.substring(0, 180) + "…";
    }
}
