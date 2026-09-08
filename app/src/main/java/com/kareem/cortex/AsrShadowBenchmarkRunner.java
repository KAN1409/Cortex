package com.kareem.cortex;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Runs a candidate against an immutable Cortex WAV and writes a separate benchmark report.
 * It never updates VaultDb and never deletes or modifies the source WAV.
 */
public final class AsrShadowBenchmarkRunner {
    public interface Callback {
        void ok(Result result);
        void fail(Exception error);
    }

    public static final class Result {
        public final String candidateId;
        public final String candidateText;
        public final long latencyMs;
        public final AsrBenchmarkMetrics.Score score;
        public final File reportFile;

        Result(String candidateId, String candidateText, long latencyMs,
               AsrBenchmarkMetrics.Score score, File reportFile) {
            this.candidateId = candidateId;
            this.candidateText = candidateText;
            this.latencyMs = latencyMs;
            this.score = score;
            this.reportFile = reportFile;
        }
    }

    private AsrShadowBenchmarkRunner() {}

    public static void run(Context context, long itemId, File sourceWav,
                           String referenceTranscript, AsrCandidate candidate,
                           Callback callback) {
        try {
            if (sourceWav == null || !sourceWav.exists() || !sourceWav.isFile())
                throw new IllegalArgumentException("Original WAV is missing");
            if (referenceTranscript == null || referenceTranscript.trim().isEmpty())
                throw new IllegalArgumentException("Reference transcript is missing");
            if (candidate == null) throw new IllegalArgumentException("ASR candidate is missing");
            if (!candidate.isReady(context))
                throw new IllegalStateException(candidate.displayName() + " is not ready on this device");

            final long originalLength = sourceWav.length();
            final long originalModified = sourceWav.lastModified();
            candidate.transcribe(context, sourceWav, new AsrCandidate.Callback() {
                @Override public void ok(TranscriptResult transcript, long latencyMs) {
                    try {
                        assertSourceUntouched(sourceWav, originalLength, originalModified);
                        if (transcript == null || transcript.text == null || transcript.text.trim().isEmpty())
                            throw new IllegalStateException("Candidate returned an empty transcript");
                        AsrBenchmarkMetrics.Score score = AsrBenchmarkMetrics.score(referenceTranscript, transcript.text);
                        File report = writeReport(context, itemId, sourceWav, referenceTranscript,
                                candidate, transcript, latencyMs, score);
                        callback.ok(new Result(candidate.id(), transcript.text, latencyMs, score, report));
                    } catch (Exception e) {
                        callback.fail(e);
                    }
                }

                @Override public void fail(Exception error, long latencyMs) {
                    try {
                        assertSourceUntouched(sourceWav, originalLength, originalModified);
                    } catch (Exception sourceError) {
                        callback.fail(sourceError);
                        return;
                    }
                    callback.fail(error);
                }
            });
        } catch (Exception e) {
            callback.fail(e);
        }
    }

    private static void assertSourceUntouched(File source, long length, long modified) {
        if (!source.exists()) throw new IllegalStateException("ASR candidate deleted the original WAV");
        if (source.length() != length)
            throw new IllegalStateException("ASR candidate modified the original WAV length");
        if (source.lastModified() != modified)
            throw new IllegalStateException("ASR candidate modified the original WAV timestamp");
    }

    private static File writeReport(Context context, long itemId, File sourceWav,
                                    String reference, AsrCandidate candidate,
                                    TranscriptResult transcript, long latencyMs,
                                    AsrBenchmarkMetrics.Score s) throws Exception {
        File dir = new File(context.getFilesDir(), "asr_benchmarks");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create benchmark directory");
        File out = new File(dir, "voice_" + itemId + "_" + candidate.id() + "_" + System.currentTimeMillis() + ".json");

        JSONObject metrics = new JSONObject();
        metrics.put("wer", s.wer);
        metrics.put("cer", s.cer);
        metrics.put("arabic_wer", s.arabicWer);
        metrics.put("latin_wer", s.latinWer);
        metrics.put("number_recall", s.numberRecall);
        metrics.put("reference_script_switches", s.referenceScriptSwitches);
        metrics.put("hypothesis_script_switches", s.hypothesisScriptSwitches);
        metrics.put("adjacent_duplicate_words", s.duplicateAdjacentWords);

        JSONObject json = new JSONObject();
        json.put("schema", "CORTEX_ASR_SHADOW_V1");
        json.put("item_id", itemId);
        json.put("source_wav", sourceWav.getAbsolutePath());
        json.put("source_bytes", sourceWav.length());
        json.put("reference_kind", "current_working_transcript_not_human_gold");
        json.put("reference_transcript", reference);
        json.put("candidate_id", candidate.id());
        json.put("candidate_name", candidate.displayName());
        json.put("candidate_engine", transcript.engine == null ? "" : transcript.engine);
        json.put("candidate_version", transcript.version == null ? "" : transcript.version);
        json.put("candidate_transcript", transcript.text);
        json.put("latency_ms", latencyMs);
        json.put("metrics", metrics);

        try (FileOutputStream stream = new FileOutputStream(out)) {
            stream.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }
}
