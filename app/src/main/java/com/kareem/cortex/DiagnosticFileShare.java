package com.kareem.cortex;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Shares large diagnostics as attachments so chat apps do not reject oversized message bodies. */
public final class DiagnosticFileShare {
    private static final int SUMMARY_LIMIT = 1800;
    private DiagnosticFileShare() {}

    public static void share(Activity activity, String subject, String chooserTitle, String fileName, String text) throws Exception {
        if (activity == null) throw new IllegalArgumentException("activity");
        String payload = text == null ? "" : text;
        File file = writeExport(activity, fileName, payload);
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".feedback.files", file);

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, summary(payload));
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newUri(activity.getContentResolver(), file.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(send, chooserTitle));
    }

    static File writeExport(android.content.Context context, String fileName, String text) throws Exception {
        String safeName = sanitizeFileName(fileName);
        File dir = new File(context.getFilesDir(), "debug_exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create debug_exports");
        File file = new File(dir, safeName);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        return file;
    }

    static String summary(String text) {
        if (text == null || text.trim().isEmpty()) return "Cortex diagnostic attached as a text file.";
        String normalized = text.replace('\r', '\n');
        String[] lines = normalized.split("\\n+");
        StringBuilder b = new StringBuilder("Cortex diagnostic attached. Key fields:\n");
        int added = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            boolean key = line.startsWith("CORTEX_") || line.startsWith("recorded_at=") || line.startsWith("exit_time=") ||
                    line.startsWith("reason=") || line.startsWith("reason_code=") || line.startsWith("status=") ||
                    line.startsWith("importance=") || line.startsWith("process=") || line.startsWith("description=") ||
                    line.startsWith("sdk=") || line.startsWith("device=") || line.startsWith("version") || line.startsWith("schema");
            if (!key && added >= 8) continue;
            if (b.length() + line.length() + 1 > SUMMARY_LIMIT) break;
            b.append(line).append('\n');
            added++;
            if (added >= 14) break;
        }
        if (added == 0) {
            String compact = text.trim();
            if (compact.length() > SUMMARY_LIMIT - 80) compact = compact.substring(0, SUMMARY_LIMIT - 80) + "…";
            b.append(compact);
        }
        return b.toString();
    }

    static String sanitizeFileName(String name) {
        String n = name == null ? "cortex-diagnostic.txt" : name.trim();
        if (n.isEmpty()) n = "cortex-diagnostic.txt";
        n = n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!n.endsWith(".txt") && !n.endsWith(".json")) n += ".txt";
        return n;
    }
}
