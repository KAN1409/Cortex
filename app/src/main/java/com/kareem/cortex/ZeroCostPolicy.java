package com.kareem.cortex;

/** Product invariant for the v64 recovery line: Cortex must never require or call a user-billed AI API. */
public final class ZeroCostPolicy {
    private ZeroCostPolicy(){}
    /** Method (rather than a compile-time constant) keeps legacy fallback code buildable but unreachable in production routing. */
    public static boolean enforced(){return true;}
    public static boolean userBilledAiApisAllowed(){return false;}
    public static String label(){return "Zero paid API · local/on-device first";}
}
