package com.kareem.cortex;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public final class CortexTrueScenarioCatalogTest {
    @Test public void catalog_has1000UniqueSemanticCases() throws Exception {
        assertEquals(1000, CortexTrueScenarioCatalog.TOTAL);
        Set<String> ids = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        int fileCases = 0;
        int explicitReferenceBoundaries = 0;
        for (int i=0;i<CortexTrueScenarioCatalog.TOTAL;i++) {
            CortexTrueScenarioCatalog.Scenario s = CortexTrueScenarioCatalog.get(i);
            JSONObject j = s.toJson();
            assertTrue(ids.add(j.getString("caseId")));
            String signature = j.getString("domain") + "|" + j.getString("intent") + "|" + j.getString("complication");
            assertTrue("duplicate semantic signature: "+signature, signatures.add(signature));
            if (j.optBoolean("fileDomain",false)) fileCases++;
            String boundary=j.getJSONObject("referenceTruth").optString("expectedBoundary","");
            if (!"OPEN_JUDGMENT".equals(boundary)) explicitReferenceBoundaries++;
        }
        assertEquals(1000, ids.size());
        assertEquals(1000, signatures.size());
        assertTrue("file-flow coverage too small", fileCases >= 250);
        assertTrue("reference boundary coverage too small", explicitReferenceBoundaries >= 400);
    }

    @Test public void fileCasesContainOpenAndVerificationEvidence() throws Exception {
        int checked=0;
        for(int i=0;i<CortexTrueScenarioCatalog.TOTAL;i++){
            JSONObject j=CortexTrueScenarioCatalog.get(i).toJson();
            if(!j.optBoolean("fileDomain",false)) continue;
            JSONObject file=j.getJSONObject("originalEvidence").getJSONObject("file");
            assertTrue(file.has("declaredMime"));
            assertTrue(file.has("detectedMime"));
            assertTrue(file.has("openSucceeded"));
            assertTrue(file.has("parserSucceeded"));
            assertTrue(file.has("permissionGranted"));
            assertTrue(file.has("versionConflict"));
            checked++;
        }
        assertTrue(checked>=250);
    }
}
