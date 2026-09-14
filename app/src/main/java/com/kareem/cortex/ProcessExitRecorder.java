package com.kareem.cortex;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persists Android process-exit diagnosis separately from the Java uncaught-exception recorder.
 * This catches failures CrashRecorder cannot see: native crashes, ANRs, LMK/system termination and
 * initialization failures. The newest exit and the newest actionable failure are both preserved so
 * an APK update cannot hide a crash that happened immediately before the update.
 */
@SuppressLint("NewApi")
public final class ProcessExitRecorder {
    private static final String FILE = "last_process_exit.txt";
    private ProcessExitRecorder() {}

    public static void captureHistoricalExit(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 30) return;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 8);
            if (exits == null || exits.isEmpty()) return;
            ApplicationExitInfo newest = exits.get(0);
            ApplicationExitInfo actionable = null;
            for (ApplicationExitInfo x : exits) {
                if (x != null && isActionableFailure(x.getReason())) { actionable = x; break; }
            }
            write(context.getApplicationContext(), exits, newest, actionable);
        } catch (Throwable ignored) {
            // Diagnostics must never become a startup dependency.
        }
    }

    public static String read(Context context, int maxChars) {
        File f = new File(context.getFilesDir(), FILE);
        if (!f.exists()) return "";
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder b = new StringBuilder();
            String line;
            int cap = Math.max(1000, maxChars);
            while ((line = r.readLine()) != null && b.length() < cap) b.append(line).append('\n');
            String s = b.toString();
            return s.length() <= maxChars ? s : s.substring(0, maxChars) + "\n…";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static void clear(Context context) {
        try {
            File f = new File(context.getFilesDir(), FILE);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {}
    }

    private static void write(Context context, List<ApplicationExitInfo> exits, ApplicationExitInfo newest,
                              ApplicationExitInfo actionable) throws Exception {
        File target = new File(context.getFilesDir(), FILE);
        File tmp = new File(context.getFilesDir(), FILE + ".part");
        try (FileOutputStream fos = new FileOutputStream(tmp, false);
             PrintWriter p = new PrintWriter(new OutputStreamWriter(fos, "UTF-8"))) {
            p.println("CORTEX_PROCESS_EXIT_V2");
            p.println("recorded_at=" + time(System.currentTimeMillis()));
            p.println("current_version_name=" + BuildConfig.VERSION_NAME);
            p.println("current_version_code=" + BuildConfig.VERSION_CODE);
            p.println("sdk=" + Build.VERSION.SDK_INT);
            p.println("device=" + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
            p.println("history_count=" + (exits == null ? 0 : exits.size()));

            p.println();
            p.println("--- NEWEST EXIT ---");
            writeSummary(p, newest);

            p.println();
            p.println("--- NEWEST ACTIONABLE FAILURE ---");
            if (actionable == null) p.println("none"); else writeSummary(p, actionable);

            p.println();
            p.println("--- RECENT EXIT HISTORY ---");
            if (exits != null) {
                int i = 0;
                for (ApplicationExitInfo info : exits) {
                    if (info == null) continue;
                    p.println("#" + i++ + " time=" + time(info.getTimestamp()) +
                            " reason=" + reasonName(info.getReason()) +
                            " status=" + info.getStatus() +
                            " importance=" + info.getImportance() +
                            " pss_kb=" + info.getPss() +
                            " rss_kb=" + info.getRss() +
                            " process=" + safe(info.getProcessName()) +
                            " description=" + safe(info.getDescription()));
                }
            }

            ApplicationExitInfo traceSource = actionable != null ? actionable : newest;
            if (traceSource != null && Build.VERSION.SDK_INT >= 31) {
                try (InputStream in = traceSource.getTraceInputStream()) {
                    if (in != null) {
                        p.println();
                        p.println("--- ANDROID EXIT TRACE (" + reasonName(traceSource.getReason()) + ") ---");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                        String line;
                        int chars = 0;
                        while ((line = reader.readLine()) != null && chars < 120000) {
                            p.println(line);
                            chars += line.length() + 1;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            p.flush();
            fos.getFD().sync();
        }
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) {
            try (InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) != -1;) out.write(buf, 0, n);
            }
            tmp.delete();
        }
    }

    private static void writeSummary(PrintWriter p, ApplicationExitInfo info) {
        if (info == null) { p.println("none"); return; }
        p.println("exit_time=" + time(info.getTimestamp()));
        p.println("reason=" + reasonName(info.getReason()));
        p.println("reason_code=" + info.getReason());
        p.println("status=" + info.getStatus());
        p.println("importance=" + info.getImportance());
        p.println("pss_kb=" + info.getPss());
        p.println("rss_kb=" + info.getRss());
        p.println("process=" + safe(info.getProcessName()));
        p.println("description=" + safe(info.getDescription()));
    }

    private static boolean isActionableFailure(int reason) {
        return reason == ApplicationExitInfo.REASON_CRASH
                || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                || reason == ApplicationExitInfo.REASON_ANR
                || reason == ApplicationExitInfo.REASON_LOW_MEMORY
                || reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                || reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
                || reason == ApplicationExitInfo.REASON_SIGNALED;
    }

    static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH: return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_OTHER: return "OTHER";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            default: return "UNKNOWN_" + reason;
        }
    }

    private static String time(long at) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date(at));
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
    }
}
