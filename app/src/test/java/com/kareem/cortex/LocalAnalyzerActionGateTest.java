package com.kareem.cortex;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class LocalAnalyzerActionGateTest {
    @Test public void negativeFollowUpSentenceDoesNotBecomeAction(){AnalysisResult r=LocalAnalyzer.analyze("No explicit follow-up or action was detected.","text/plain");assertTrue(r.actions.isEmpty());}
    @Test public void uiChromeDoesNotBecomeAction(){AnalysisResult r=LocalAnalyzer.analyze("Open Cancel Save Settings Download. Review important information.","text/plain");assertTrue(r.actions.isEmpty());}
}
