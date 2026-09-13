package com.kareem.cortex;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Read-only user-visible diagnostics for recent rejected bridge verdicts. */
public final class BridgeRejectionDiagnostics {
    private BridgeRejectionDiagnostics() {}

    public static String render(Context context) {
        try {
            File dir = new File(new File(context.getFilesDir(), "chatgpt_bridge"), "rejected");
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null || files.length == 0) return "";
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (File file : files) {
                if (unique.size() >= 10) break;
                try {
                    JSONObject wrapper = new JSONObject(readAll(file));
                    String reason = wrapper.optString("reason", "UNKNOWN");
                    JSONObject msg = wrapper.optJSONObject("message");
                    String requestId = msg == null ? "" : msg.optString("requestId", "");
                    String testId = msg == null ? "" : msg.optString("testId", "");
                    String line = reason;
                    if (!testId.isEmpty()) line += " · " + testId;
                    else if (!requestId.isEmpty()) line += " · " + shortId(requestId);
                    unique.add(line);
                } catch (Throwable ignored) {}
            }
            if (unique.isEmpty()) return "";
            StringBuilder out = new StringBuilder("Bridge rejection diagnostics:");
            for (String line : unique) out.append("\n• ").append(line);
            return out.toString();
        } catch (Throwable t) {
            return "Bridge rejection diagnostics unavailable · " + t.getClass().getSimpleName();
        }
    }

    private static String shortId(String id) {
        if (id == null) return "";
        return id.length() <= 12 ? id : id.substring(0, 12);
    }

    private static String readAll(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
