package com.kareem.cortex;

import android.content.Context;

public final class LocalQwenBrain implements CortexBrain {

    private static final int MAX_TOKENS = 220;

    private final Context app;

    public LocalQwenBrain(Context context) {
        this.app = context == null ? null : context.getApplicationContext();
    }

    @Override
    public CognitiveResult classify(CognitiveInput input) throws BrainException {
        return classifyWithTelemetry(input).result;
    }

    public LocalBrainRun classifyWithTelemetry(CognitiveInput input) throws BrainException {
        if (app == null) {
            throw new BrainException("Android context is unavailable");
        }

        if (!LocalModelManager.installed(app)) {
            throw new BrainException("Local Cortex model is not ready");
        }

        try {
            String system = CognitivePromptBuilder.systemPrompt();
            String prompt = CognitivePromptBuilder.build(input);

            LocalLlmBridge.CompletionResult run = LocalLlmBridge.completeCached(
                    LocalModelManager.modelFile(app).getAbsolutePath(),
                    prompt,
                    system,
                    MAX_TOKENS
            );

            CognitiveResult parsed = CognitiveResultParser.parse(run.getText());
            CognitiveResult validated = CognitiveResultValidator.validate(parsed);

            return new LocalBrainRun(
                    validated,
                    run.getText(),
                    run.getDurationMs(),
                    run.getModelLoadMs(),
                    run.getGenerationMs(),
                    run.getTokensGenerated(),
                    run.getTokensPerSecond(),
                    run.getCacheHit()
            );
        } catch (BrainException e) {
            throw e;
        } catch (CognitiveContractException e) {
            throw new BrainException(
                    "Local model returned invalid cognitive output: " + e.getMessage(),
                    e
            );
        } catch (Throwable t) {
            throw new BrainException("Local cognitive inference failed", t);
        }
    }
}
