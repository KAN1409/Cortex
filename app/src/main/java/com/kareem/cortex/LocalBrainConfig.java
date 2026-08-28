package com.kareem.cortex;

/** Default local inference profile for the Galaxy S26 Ultra 12 GB target. */
public final class LocalBrainConfig {
    public static final int CONTEXT_SIZE = 3072;
    public static final int MAX_OUTPUT_TOKENS = 160;
    public static final float TEMPERATURE = 0.7f;
    public static final float TOP_P = 0.8f;
    public static final int TOP_K = 20;
    public static final int THREADS = 4;
    public static final int MAX_QUEUE = 100;
    public static final int MAX_BATCH_SIGNALS = 6;
    public static final int MAX_THREAD_HISTORY = 5;
    public static final int MAX_INPUT_CHARS = 6000;
    public static final long MICRO_BATCH_MS = 2500L;
    public static final long IDLE_WARM_MS = 5L * 60L * 1000L;
    public static final double ACCEPT_LOCAL_CONFIDENCE = 0.78;
    public static final double TRY_DEEP_CONFIDENCE = 0.55;

    private LocalBrainConfig() {}
}
