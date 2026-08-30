package com.kareem.cortex.rebuild;

import java.util.ArrayList;

/** Provider-neutral transcript result preserved from the proven Cortex ASR pipeline. */
public final class TranscriptResult {
    public static final class Segment {
        public final long startMs, endMs;
        public final String text;
        public final float confidence;
        public Segment(long startMs, long endMs, String text, float confidence) {
            this.startMs = startMs; this.endMs = endMs; this.text = text == null ? "" : text; this.confidence = confidence;
        }
    }

    public String text = "";
    public String language = "";
    public String engine = "";
    public String version = "";
    public long durationMs = 0;
    public long processedDurationMs = 0;
    public double coverage = 0.0;
    public String rawTranscript = "";
    public String providerMergedTranscript = "";
    public String qualityWarning = "";
    public String rawProviderResponse = "";
    public final ArrayList<Segment> segments = new ArrayList<>();
}
