package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only v70 Now projection from the latest completed cognitive shadow run. */
public final class CognitiveNowReadModel {
    public static final String VERSION = "cognitive_now_read_model_001";
    private CognitiveNowReadModel() {}

    public static List<PrimeBriefStore.Item> load(SQLiteDatabase db, int limit) {
        ArrayList<PrimeBriefStore.Item> out = new ArrayList<>();
        if (db == null || !table(db, "ue_cognitive_shadow_runs") || !table(db, "ue_cognitive_shadow_decisions")) return out;
        int cap = Math.max(1, Math.min(12, limit));
        String sql =
                "SELECT d.situation_id,d.cognitive_score,COALESCE(d.cognitive_reason,'')," +
                "COALESCE((SELECT e.semantic_type FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id WHERE m.situation_id=d.situation_id AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1),'')," +
                "COALESCE((SELECT e.intent FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id WHERE m.situation_id=d.situation_id AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1),'')," +
                "COALESCE((SELECT e.subject FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id WHERE m.situation_id=d.situation_id AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1),'')," +
                "COALESCE((SELECT e.summary FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id WHERE m.situation_id=d.situation_id AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1),'')," +
                "COALESCE((SELECT e.confidence FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id WHERE m.situation_id=d.situation_id AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1),0)," +
                "COALESCE((SELECT e.occurred_at FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id WHERE m.situation_id=d.situation_id AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1),0)," +
                "COALESCE((SELECT r.source_key FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id JOIN ue_raw_observations r ON r.id=e.raw_observation_id WHERE m.situation_id=d.situation_id AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1),'') " +
                "FROM ue_cognitive_shadow_decisions d WHERE d.run_id=(SELECT id FROM ue_cognitive_shadow_runs WHERE completed_at>0 ORDER BY id DESC LIMIT 1) " +
                "AND d.cognitive_surface=1 ORDER BY d.cognitive_rank ASC,d.cognitive_score DESC LIMIT ?";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(cap)});
        try {
            while (c.moveToNext()) {
                long situationId = c.getLong(0);
                double score = c.getDouble(1);
                String reason = nz(c.getString(2));
                String type = nz(c.getString(3));
                String intent = nz(c.getString(4));
                String subject = nz(c.getString(5));
                String summary = nz(c.getString(6));
                double confidence = c.getDouble(7);
                long at = c.getLong(8);
                String source = nz(c.getString(9));
                if(AttentionNoisePolicy.suppress(source,subject,summary,type,intent)) continue;
                String kind = kind(type, intent, summary);
                String title = CanonicalPresentation.cleanTitle("notification", type,
                        subject.isEmpty() ? source : subject, subject);
                String body = CanonicalPresentation.cleanBody(summary);
                int priority = Math.max(1, Math.min(100, (int)Math.round(score * 100.0)));
                out.add(new PrimeBriefStore.Item(9_000_000_000L + situationId, kind, title, body,
                        source, "open", Math.max(0, Math.min(1, confidence)), priority,
                        situationId, 0, at));
            }
        } finally {
            c.close();
        }
        return out;
    }

    private static String kind(String type, String intent, String summary) {
        String x = (nz(type) + " " + nz(intent) + " " + nz(summary)).toLowerCase(Locale.ROOT);
        if (x.contains("decision")) return "DECISION";
        if (x.contains("commitment") || x.contains("waiting") || x.contains("awaiting") || x.contains("pending")) return "WAITING";
        if (x.contains("request") || x.contains("security") || x.contains("deadline") || x.contains("payment") || x.contains("missed call") || x.contains("reminder")) return "ACTION";
        return "INSIGHT";
    }

    private static boolean table(SQLiteDatabase db, String name) {
        Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", new String[]{name});
        boolean yes = c.moveToFirst();
        c.close();
        return yes;
    }
    private static String nz(String s) { return s == null ? "" : s.trim(); }
}
