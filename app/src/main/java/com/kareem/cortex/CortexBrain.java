package com.kareem.cortex;

/** Single provider abstraction for cognition, Pulse synthesis and Ask-style answers. */
public interface CortexBrain {
    /** Typed notification cognition path. */
    CognitiveResult classify(CognitiveInput input) throws BrainException;

    /** Lower-level completion path retained for Pulse/Ask and migration compatibility. */
    BrainCompletion classify(BrainRequest input) throws BrainException;
    BrainCompletion synthesizePulse(BrainRequest input) throws BrainException;
    BrainCompletion answer(BrainRequest input) throws BrainException;
    boolean isAvailable();
    String provider();
    String model();
}
