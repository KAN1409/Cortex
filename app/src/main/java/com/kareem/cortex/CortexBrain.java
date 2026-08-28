package com.kareem.cortex;

/** Single provider abstraction for classification, Pulse synthesis and Ask-style answers. */
public interface CortexBrain {
    BrainCompletion classify(BrainRequest input) throws BrainException;
    BrainCompletion synthesizePulse(BrainRequest input) throws BrainException;
    BrainCompletion answer(BrainRequest input) throws BrainException;
    boolean isAvailable();
    String provider();
    String model();
}
