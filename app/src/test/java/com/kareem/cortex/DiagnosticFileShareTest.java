package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DiagnosticFileShareTest {
    @Test public void writesFullPayloadToDebugExportsWithoutTruncation() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        StringBuilder b=new StringBuilder("CORTEX_PROCESS_EXIT_V1\nreason=CRASH_NATIVE\n");
        for(int i=0;i<5000;i++) b.append("native-frame-").append(i).append(" abcdefghijklmnopqrstuvwxyz\n");
        String payload=b.toString();
        File f=DiagnosticFileShare.writeExport(context,"process exit report.txt",payload);
        assertTrue(f.exists());
        assertTrue(f.getAbsolutePath().contains("debug_exports"));
        assertEquals("process_exit_report.txt",f.getName());
        String restored=new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertEquals(payload,restored);
    }

    @Test public void shareSummaryIsBoundedAndKeepsCriticalExitFields() {
        StringBuilder b=new StringBuilder();
        b.append("CORTEX_PROCESS_EXIT_V1\n");
        b.append("recorded_at=2026-09-09 22:40:00 +0300\n");
        b.append("reason=CRASH_NATIVE\nreason_code=5\nstatus=6\nimportance=100\n");
        for(int i=0;i<10000;i++) b.append("very long native trace line ").append(i).append('\n');
        String summary=DiagnosticFileShare.summary(b.toString());
        assertTrue(summary.length()<=1800);
        assertTrue(summary.contains("reason=CRASH_NATIVE"));
        assertTrue(summary.contains("reason_code=5"));
        assertTrue(summary.contains("status=6"));
        assertFalse(summary.contains("very long native trace line 9999"));
    }

    @Test public void jsonDiagnosticsUseApplicationJsonMimeType() {
        assertEquals("application/json",DiagnosticFileShare.mimeType("cortex-attention-trace.json"));
        assertEquals("application/json",DiagnosticFileShare.mimeType("TRACE.JSON"));
        assertEquals("text/plain",DiagnosticFileShare.mimeType("cortex-java-crash.txt"));
    }
}
