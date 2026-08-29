package com.kareem.cortex;

import android.content.Context;

import java.util.function.BooleanSupplier;

/** Hybrid local cognitive brain: deterministic obvious cases, Qwen for ambiguity. */
public final class LocalQwenBrain implements CortexBrain {

    private static final int MAX_TOKENS = 96;

    private final Context app;

    public LocalQwenBrain(Context context) {
        this.app = context == null ? null : context.getApplicationContext();
    }

    @Override
    public CognitiveResult classify(CognitiveInput input) throws BrainException {
        return classifyWithTelemetry(input).result;
    }

    public LocalBrainRun classifyWithTelemetry(CognitiveInput input) throws BrainException {
        return classifyWithTelemetry(
                input,
                LocalInferenceCoordinator.Priority.INTERACTIVE,
                null,
                null
        );
    }

    public LocalBrainRun classifyWithTelemetry(
            CognitiveInput input,
            LocalInferenceCoordinator.Priority priority
    ) throws BrainException {
        return classifyWithTelemetry(input, priority, null, null);
    }

    public LocalBrainRun classifyWithTelemetry(
            CognitiveInput input,
            LocalInferenceCoordinator.Priority priority,
            LocalInferenceCoordinator.NativeStartListener listener,
            BooleanSupplier cancelled
    ) throws BrainException {
        if (app == null) {
            throw new BrainException("Android context is unavailable");
        }
        if (input == null) {
            throw new BrainException("CognitiveInput is null");
        }

        try {
            long deterministicStarted = System.currentTimeMillis();
            CognitiveResult fast = HybridFastClassifier.classify(input);
            if (fast != null) {
                CognitiveResult validated = CognitiveResultValidator.validate(fast);
                long finished = System.currentTimeMillis();
                String raw = "{\"path\":\"deterministic_fast\",\"disposition\":\""
                        + validated.disposition.name()
                        + "\"}";
                return new LocalBrainRun(
                        validated,
                        raw,
                        Math.max(0L, finished - deterministicStarted),
                        0L,
                        0L,
                        0,
                        0f,
                        false,
                        deterministicStarted,
                        0L,
                        0L,
                        0L,
                        0L,
                        Math.max(0L, finished - deterministicStarted),
                        0,
                        FastCognitivePromptBuilder.WIRE_SCHEMA
                );
            }

            if (!LocalModelManager.installed(app)) {
                throw new BrainException("Local Cortex model is not ready");
            }

            String system = FastCognitivePromptBuilder.systemPrompt();
            String prompt = FastCognitivePromptBuilder.build(input);
            int promptChars = system.length() + prompt.length();

            LocalInferenceCoordinator.Result<LocalLlmBridge.CompletionResult> coordinated =
                    LocalInferenceCoordinator.execute(
                            priority,
                            listener,
                            cancelled,
                            () -> LocalLlmBridge.completeCached(
                                    LocalModelManager.modelFile(app).getAbsolutePath(),
                                    prompt,
                                    system,
                                    MAX_TOKENS
                            )
                    );

            LocalLlmBridge.CompletionResult run = coordinated.value;
            CognitiveResult parsed;
            try {
                parsed = FastCognitiveResultParser.parse(run.getText(), input.latestText);
            } catch (CognitiveContractException error) {
                throw new BrainException(
                        "Local model returned invalid cognitive output: "
                                + error.getMessage()
                                + " raw="
                                + diagnosticRaw(run.getText()),
                        error
                );
            }
            CognitiveResult validated = CognitiveResultValidator.validate(parsed);
            long totalMs = Math.max(0L, System.currentTimeMillis() - coordinated.enqueuedAt);

            return new LocalBrainRun(
                    validated,
                    run.getText(),
                    run.getDurationMs(),
                    run.getModelLoadMs(),
                    run.getGenerationMs(),
                    run.getTokensGenerated(),
                    run.getTokensPerSecond(),
                    run.getCacheHit(),
                    coordinated.enqueuedAt,
                    coordinated.nativeStartedAt,
                    coordinated.nativeFinishedAt,
                    coordinated.queueWaitMs,
                    coordinated.nativeTotalMs,
                    totalMs,
                    promptChars,
                    FastCognitivePromptBuilder.WIRE_SCHEMA
            );
        } catch (BrainException error) {
            throw error;
        } catch (CognitiveContractException error) {
            throw new BrainException(
                    "Local model returned invalid cognitive output: " + error.getMessage(),
                    error
            );
        } catch (Throwable error) {
            throw new BrainException("Local cognitive inference failed", error);
        }
    }

    private static String diagnosticRaw(String raw) {
        if (raw == null) return "<null>";
        String compact = raw
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        if (compact.length() > 600) {
            compact = compact.substring(0, 600) + "…";
        }
        return compact.isEmpty() ? "<empty>" : compact;
    }
}
