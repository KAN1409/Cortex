package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/**
 * Canonical lifecycle maintenance for the product-facing situation surface.
 * Evidence is append-only; attention is not. Old derived rows are retired when
 * newer grounded state, explicit completion, or expired timing proves they no
 * longer describe the user's current situation.
 */
public final class SituationLifecycle {
    private static final long RELAY_MAX_AGE = 14L * 24L * 60L * 60L * 1000L;
    private static final long EXPIRED_GRACE = 12L * 60L * 60L * 1000L;
    private SituationLifecycle() {}

    public static int reconcile(VaultDb vault) {
        if (vault == null) return 0;
        CognitiveStore.ensure(vault);
        SQLiteDatabase db = vault.getWritableDatabase();
        ArrayList<Row> rows = load(db);
        long now = System.currentTimeMillis();
        int changed = 0;

        for (Row row : rows) {
            if (staleByTime(row, now)
                    || resolvedByNewerEvidence(db, row)
                    || ungroundedLegacyRelayPromotion(db, row, now)) {
                if (retire(db, row.id, "superseded")) changed++;
                row.retired = true;
            }
        }

        // A communication thread is a changing situation, not an append-only task list.
        // When two actionable states clearly concern the same topic, keep the newest state.
        HashMap<Long, Row> newestByThread = new HashMap<>();
        for (Row row : rows) {
            if (row.retired || row.threadId <= 0 || !actionable(row.kind)) continue;
            Row newest = newestByThread.get(row.threadId);
            if (newest == null) {
                newestByThread.put(row.threadId, row);
                continue;
            }
            Row winner = newer(row, newest) ? row : newest;
            Row older = winner == row ? newest : row;
            if (sameTopic(winner, older) && retire(db, older.id, "superseded")) {
                changed++;
                older.retired = true;
            }
            newestByThread.put(row.threadId, winner);
        }
        return changed;
    }

    public static boolean shouldSurface(VaultDb vault, PrimeBriefStore.Item item) {
        if (vault == null || item == null) return false;
        long now = System.currentTimeMillis();
        Row row = new Row(item.id, item.kind, item.title, item.body, item.threadId,
                item.signalId, item.updatedAt);
        if (staleByTime(row, now)) return false;
        SQLiteDatabase db = vault.getReadableDatabase();
        if (resolvedByNewerEvidence(db, row)) return false;
        return !ungroundedLegacyRelayPromotion(db, row, now);
    }

    private static ArrayList<Row> load(SQLiteDatabase db) {
        ArrayList<Row> out = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT id,kind,title,body,thread_id,anchor_signal_id,updated_at " +
                "FROM derived_items WHERE state IN ('open','pending') " +
                "AND kind IN ('ACTION','WAITING','DECISION','REMINDER','ALERT','CHANGE') " +
                "ORDER BY updated_at DESC,id DESC LIMIT 1200", null);
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2),
                    c.getString(3), c.getLong(4), c.getLong(5), c.getLong(6)));
        } finally { c.close(); }
        return out;
    }

    private static boolean staleByTime(Row row, long now) {
        String text = n(row.title) + " " + n(row.body);
        long anchor = row.updatedAt > 0 ? row.updatedAt : now;
        long target = TemporalResolver.resolveForAttention(text, anchor);
        if (target > 0 && now - target > EXPIRED_GRACE) return true;
        // Relay-derived work must be refreshed by newer evidence. It cannot haunt Now forever.
        return row.signalId > 0 && actionable(row.kind) && now - anchor > RELAY_MAX_AGE;
    }

    private static boolean resolvedByNewerEvidence(SQLiteDatabase db, Row row) {
        if (row.threadId <= 0 || row.signalId <= 0 || !actionable(row.kind)) return false;
        Cursor c = db.rawQuery(
                "SELECT title,body FROM raw_signals WHERE thread_id=? AND id>? " +
                "ORDER BY id DESC LIMIT 8",
                new String[]{String.valueOf(row.threadId), String.valueOf(row.signalId)});
        try {
            while (c.moveToNext()) {
                String text = norm(n(c.getString(0)) + " " + n(c.getString(1)));
                if (explicitCompletion(text)) return true;
            }
        } finally { c.close(); }
        return false;
    }

    private static boolean ungroundedLegacyRelayPromotion(SQLiteDatabase db, Row row, long now) {
        if (row.signalId <= 0 || !actionable(row.kind)) return false;
        Cursor c = db.rawQuery(
                "SELECT cognitive_version,final_reason,metadata_json,title,body FROM raw_signals WHERE id=? LIMIT 1",
                new String[]{String.valueOf(row.signalId)});
        try {
            if (!c.moveToFirst()) return false;
            String version = n(c.getString(0)).toLowerCase(Locale.ROOT);
            String reason = n(c.getString(1)).toLowerCase(Locale.ROOT);
            String meta = n(c.getString(2));
            boolean relay = meta.contains("\"source_connector\":\"second_brain\"")
                    || meta.contains("\"capture_mode\":\"relay_local_bus\"");
            boolean legacy = version.startsWith("legacy") || reason.contains("legacy fallback")
                    || reason.contains("fallback (");
            if (!relay || !legacy) return false;
            String text = norm(n(c.getString(3)) + " " + n(c.getString(4)));
            if ("ACTION".equalsIgnoreCase(row.kind)) return !explicitUserRequest(text);
            if ("WAITING".equalsIgnoreCase(row.kind)) return !explicitOtherCommitment(text);
            if ("DECISION".equalsIgnoreCase(row.kind)) return !explicitDecision(text);
            return false;
        } finally { c.close(); }
    }

    private static boolean retire(SQLiteDatabase db, long id, String state) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        v.put("updated_at", System.currentTimeMillis());
        return db.update("derived_items", v, "id=? AND state IN ('open','pending')",
                new String[]{String.valueOf(id)}) > 0;
    }

    private static boolean newer(Row a, Row b) {
        return a.updatedAt > b.updatedAt || (a.updatedAt == b.updatedAt && a.id > b.id);
    }

    private static boolean sameTopic(Row a, Row b) {
        Set<String> x = tokens(a.title + " " + a.body), y = tokens(b.title + " " + b.body);
        if (x.isEmpty() || y.isEmpty()) return false;
        int intersection = 0;
        for (String token : x) if (y.contains(token)) intersection++;
        int union = x.size() + y.size() - intersection;
        return union > 0 && intersection / (double) union >= 0.25;
    }

    private static Set<String> tokens(String value) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String token : norm(value).split(" ")) {
            if (token.length() < 3 || stop(token)) continue;
            out.add(token);
        }
        return out;
    }

    private static boolean stop(String x) {
        return has(x,"the","and","for","with","from","this","that","you","your","في","من","على","الى","إلى","ده","دي","هو","هي","كان","كانت");
    }

    private static boolean explicitCompletion(String t) {
        return has(t,"completed successfully","has been completed","task completed","done successfully",
                "finished successfully","resolved successfully","تم بنجاح","تم الانتهاء","تم التنفيذ",
                "اتعملت بنجاح","خلصت بنجاح","تمت المهمه","تمت المهمة");
    }

    private static boolean explicitUserRequest(String t) {
        return has(t,"please send","can you send","could you send","please review","can you review",
                "please confirm","can you confirm","need you to","action required","محتاج منك","ممكن تبعت",
                "ابعتلي","ابعت لي","لو سمحت","راجع","أكد","اكد","مطلوب منك");
    }

    private static boolean explicitOtherCommitment(String t) {
        return has(t,"i will send","i'll send","i will reply","i'll reply","i will get back","i'll get back",
                "هبعتلك","هابعتلك","هرد عليك","هرجعلك","هراجع وارجعلك","هكلمك لما");
    }

    private static boolean explicitDecision(String t) {
        return has(t,"approved","has been approved","rejected","has been rejected","تمت الموافقه",
                "تمت الموافقة","تم الرفض");
    }

    private static boolean actionable(String kind) {
        String k = n(kind).toUpperCase(Locale.ROOT);
        return k.equals("ACTION") || k.equals("WAITING") || k.equals("DECISION") || k.equals("REMINDER");
    }

    private static String norm(String s) { return MasterRelevanceFilter.ruleNorm(n(s)); }
    private static boolean has(String s, String... xs) {
        for (String x : xs) if (s.contains(norm(x))) return true;
        return false;
    }
    private static String n(String s) { return s == null ? "" : s.trim(); }

    private static final class Row {
        final long id, threadId, signalId, updatedAt;
        final String kind, title, body;
        boolean retired;
        Row(long id, String kind, String title, String body, long threadId, long signalId, long updatedAt) {
            this.id=id;this.kind=n(kind);this.title=n(title);this.body=n(body);this.threadId=threadId;
            this.signalId=signalId;this.updatedAt=updatedAt;
        }
    }
}
