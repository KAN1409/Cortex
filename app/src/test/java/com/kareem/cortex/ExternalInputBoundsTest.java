package com.kareem.cortex;

import org.junit.Test;

import static org.junit.Assert.*;

/** Regression coverage for externally supplied share/teacher payload resource bounds. */
public final class ExternalInputBoundsTest {
    @Test public void shareImporter_hasFiniteExternalResourceBounds(){
        assertTrue(ShareImporter.MAX_SHARED_ITEMS > 0 && ShareImporter.MAX_SHARED_ITEMS <= 64);
        assertTrue(ShareImporter.MAX_SHARED_TEXT_CHARS > 0 && ShareImporter.MAX_SHARED_TEXT_CHARS <= 2_000_000);
        assertTrue(ShareImporter.MAX_SHARED_BYTES > 0 && ShareImporter.MAX_SHARED_BYTES <= 1024L*1024L*1024L);
    }

    @Test public void teacherRejectsOversizedPayloadBeforeContextUse(){
        String oversized="x".repeat(CortexChatGptAppTeacher.MAX_POLICY_TEXT_CHARS+1);
        CortexChatGptAppTeacher.ImportResult result=CortexChatGptAppTeacher.importText(null,oversized);
        assertFalse(result.ok);
        assertEquals("Policy text outside bounds",result.error);
    }

    @Test public void teacherBoundIsFiniteAndAboveNormalPolicySize(){
        assertTrue(CortexChatGptAppTeacher.MAX_POLICY_TEXT_CHARS >= 70_000);
        assertTrue(CortexChatGptAppTeacher.MAX_POLICY_TEXT_CHARS <= 512_000);
    }
}
