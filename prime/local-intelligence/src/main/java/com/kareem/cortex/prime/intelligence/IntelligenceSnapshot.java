package com.kareem.cortex.prime.intelligence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IntelligenceSnapshot {
    public static final class ThreadProposal {
        public final String key;
        public final String label;
        public final List<String> evidenceIds;
        public final long latestEpochMs;
        public final String latestSnippet;

        public ThreadProposal(String key, String label, List<String> evidenceIds, long latestEpochMs, String latestSnippet) {
            this.key = key;
            this.label = label;
            this.evidenceIds = Collections.unmodifiableList(new ArrayList<>(evidenceIds));
            this.latestEpochMs = latestEpochMs;
            this.latestSnippet = latestSnippet == null ? "" : latestSnippet;
        }
    }

    public static final class SignalProposal {
        public final String kind;
        public final String label;
        public final String evidenceId;
        public final double confidence;

        public SignalProposal(String kind, String label, String evidenceId, double confidence) {
            this.kind = kind;
            this.label = label;
            this.evidenceId = evidenceId;
            this.confidence = confidence;
        }
    }

    public final List<ThreadProposal> threads;
    public final List<String> people;
    public final List<SignalProposal> taskCandidates;
    public final List<SignalProposal> temporalHints;

    public IntelligenceSnapshot(
            List<ThreadProposal> threads,
            List<String> people,
            List<SignalProposal> taskCandidates,
            List<SignalProposal> temporalHints
    ) {
        this.threads = Collections.unmodifiableList(new ArrayList<>(threads));
        this.people = Collections.unmodifiableList(new ArrayList<>(people));
        this.taskCandidates = Collections.unmodifiableList(new ArrayList<>(taskCandidates));
        this.temporalHints = Collections.unmodifiableList(new ArrayList<>(temporalHints));
    }
}
