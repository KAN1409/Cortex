package com.kareem.cortex;

import android.app.Activity;

/**
 * Compatibility entry retained for existing diagnostics wiring.
 * The old recursive crawler is no longer the user-facing test: real diagnostics now run the
 * goal-driven CortexExperimentalUserSuite and report actual app problems/quality gaps.
 */
public final class CortexRobotTestExporter {
    private CortexRobotTestExporter(){}
    public static void runAndShare(Activity a){CortexUserJourneyTestExporter.runAndShare(a);}
}
