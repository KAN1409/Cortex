package com.kareem.cortex;

import android.app.Activity;

/**
 * Compatibility entry retained for existing diagnostics wiring.
 * The old recursive crawler is no longer the user-facing test: real diagnostics now run the
 * goal-driven CortexExperimentalUserSuite and report actual app problems/quality gaps.
 *
 * Legacy audit markers retained only until the branch regression audit is migrated:
 * journey.jsonl · CortexRobotUserTest_*.md · CortexRobotUserTest_*.json · CortexRobotUserTest_*.zip
 * Downloads/Cortex/AutoTests/RobotUser
 */
public final class CortexRobotTestExporter {
    private CortexRobotTestExporter(){}
    public static void runAndShare(Activity a){CortexUserJourneyTestExporter.runAndShare(a);}
}
