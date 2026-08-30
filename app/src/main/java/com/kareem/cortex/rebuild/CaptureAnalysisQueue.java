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
 * Single-lane capture analysis. ASR is perception only; after an accepted transcript the same
 * grounded evidence is synchronously handed to the Cortex brain. The callback reports both stages
 * separately so a successful transcript can never masquerade as a successful brain decision.
 */
public final class CaptureAnalysisQueue {
    public interface Callback { void complete(long evidenceId, Outcome outcome); }
    public static final class Outcome {
        public final TranscriptResult transcript;
        public final BrainStore.ApplyResult brainResult;
        public final Exception transcriptionError;
        public final Exception brainError;
        Outcome(TranscriptResult transcript, BrainStore.ApplyResult brainResult, Exception transcriptionError, Exception brainError) {
            this.transcript=transcript; this.brainResult=brainResult; this.transcriptionError=transcriptionError; this.brainError=brainError;
        }
        public boolean transcriptionSucceeded(){return transcript!=null&&transcriptionError==null;}
        public boolean brainSucceeded(){return brainResult!=null&&brainError==null;}
    }

    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> new Thread(r, "cortex-capture-analysis"));
    private static final ExecutorService ASR = Executors.newCachedThreadPool(r -> new Thread(r, "cortex-asr"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long AUDIO_TIMEOUT_SEC = 75;

    private CaptureAnalysisQueue() {}

    public static void analyzeVoice(Context context, long evidenceId, Callback callback) {
        Context app = context.getApplicationContext();
        QUEUE.execute(() -> {
            CortexDb db = null;
            TranscriptResult transcript = null;
            BrainStore.ApplyResult brain = null;
            Exception transcriptionError = null;
            Exception brainError = null;
            try {
                db = new CortexDb(app);
                CortexDb.AttachmentEvidence evidence = db.attachmentEvidence(evidenceId);
                if (evidence == null || evidence.path.isEmpty()) throw new IllegalArgumentException("Voice evidence file missing");
                File audio = new File(evidence.path);
                if (!audio.exists() || audio.length() == 0) throw new IllegalArgumentException("Voice audio file missing");
                db.markEvidenceState(evidenceId, "transcribing", "Voice recording · transcription in progress");
                Future<TranscriptResult> future = ASR.submit((Callable<TranscriptResult>) () -> VoiceTranscriptionEngine.transcribe(app, audio));
                try {
                    transcript = future.get(AUDIO_TIMEOUT_SEC, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    future.cancel(true);
                    throw new TimeoutException("ASR did not finish within " + AUDIO_TIMEOUT_SEC + " seconds; recording preserved for retry");
                }
                db.saveVoiceTranscript(evidenceId, transcript);
            } catch (Exception e) {
                transcriptionError = e;
                if (db != null) {
                    String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    db.markEvidenceState(evidenceId, "transcription_failed", "Voice recording · transcription failed: " + compact(detail));
                }
            }

            if (transcriptionError == null && transcript != null && db != null) {
                try {
                    BrainStore.ensure(db);
                    BrainStore.markRunning(db, evidenceId);
                    String snapshot = BrainStore.contextSnapshot(db, 12);
                    CortexDb.AttachmentEvidence grounded = db.attachmentEvidence(evidenceId);
                    BrainIntakeEngine.Decision decision = CortexBrainRouter.understand(app, grounded, transcript.text, snapshot);
                    brain = BrainStore.apply(db, evidenceId, decision);
                } catch (Exception e) {
                    brainError = e;
                    try { BrainStore.markFailed(db, evidenceId, e); } catch (Throwable ignored) {}
                }
            }

            if (db != null) try { db.close(); } catch (Throwable ignored) {}
            Outcome outcome = new Outcome(transcript, brain, transcriptionError, brainError);
            if (callback != null) MAIN.post(() -> callback.complete(evidenceId, outcome));
        });
    }

    private static String compact(String value) {
        String v = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return v.length() <= 180 ? v : v.substring(0, 180) + "…";
    }
}
