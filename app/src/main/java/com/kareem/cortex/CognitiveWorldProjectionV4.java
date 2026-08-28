package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only V4 Worlds projection. Never falls back to legacy stores. */
public final class CognitiveWorldProjectionV4 {
    private CognitiveWorldProjectionV4() {}

    public static final class Query {
        public final String text;
        public final CognitiveDomainV4.WorldTypeHint typeHint;
        public final int limit;

        public Query(String text, CognitiveDomainV4.WorldTypeHint typeHint, int limit) {
            this.text = text == null ? "" : text.trim();
            this.typeHint = typeHint;
            this.limit = Math.max(1, Math.min(300, limit));
        }
    }

    public static final class Row {
        public final String id;
        public final String canonicalName;
        public final CognitiveDomainV4.WorldTypeHint typeHint;
        public final CognitiveDomainV4.WorldMaturity maturity;
        public final String summary;
        public final long lastActiveAt;
        public final int aliasCount;
        public final int identityClaimCount;
        public final int memoryCount;
        public final int evidenceCount;
        public final int factCount;
        public final int mergedChildCount;

        Row(String id, String canonicalName, CognitiveDomainV4.WorldTypeHint typeHint,
            CognitiveDomainV4.WorldMaturity maturity, String summary, long lastActiveAt,
            int aliasCount, int identityClaimCount, int memoryCount, int evidenceCount,
            int factCount, int mergedChildCount) {
            this.id = id;
            this.canonicalName = canonicalName;
            this.typeHint = typeHint;
            this.maturity = maturity;
            this.summary = summary == null ? "" : summary;
            this.lastActiveAt = lastActiveAt;
            this.aliasCount = aliasCount;
            this.identityClaimCount = identityClaimCount;
            this.memoryCount = memoryCount;
            this.evidenceCount = evidenceCount;
            this.factCount = factCount;
            this.mergedChildCount = mergedChildCount;
        }
    }

    public static final class Alias {
        public final String value;
        public final String source;
        public final double confidence;
        public final boolean userConfirmed;
        Alias(String value, String source, double confidence, boolean userConfirmed) {
            this.value = value; this.source = source; this.confidence = confidence; this.userConfirmed = userConfirmed;
        }
    }

    public static final class Claim {
        public final CognitiveIdentityV4.ClaimType type;
        public final String value;
        public final CognitiveIdentityV4.ClaimStrength strength;
        public final boolean userConfirmed;
        public final String evidenceId;
        Claim(CognitiveIdentityV4.ClaimType type, String value, CognitiveIdentityV4.ClaimStrength strength,
              boolean userConfirmed, String evidenceId) {
            this.type = type; this.value = value; this.strength = strength; this.userConfirmed = userConfirmed;
            this.evidenceId = evidenceId == null ? "" : evidenceId;
        }
    }

    public static List<Row> query(VaultDb db, Query query) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        return query(db.getReadableDatabase(), query);
    }

    static List<Row> query(SQLiteDatabase sql, Query query) {
        if (sql == null) throw new IllegalArgumentException("db required");
        Query q = query == null ? new Query("", null, 100) : query;
        ArrayList<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("w.status='ACTIVE'");
        if (q.typeHint != null) { where.append(" AND w.type_hint=?"); args.add(q.typeHint.name()); }
        if (!q.text.isEmpty()) {
            where.append(" AND (LOWER(w.canonical_name) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(w.summary,'')) LIKE ? ESCAPE '\\' OR EXISTS (")
                    .append("SELECT 1 FROM v4_world_aliases a WHERE a.world_id=w.id AND a.normalized_alias LIKE ? ESCAPE '\\'))");
            String like = "%" + escapeLike(CognitiveIdentityV4.normalizeText(q.text)) + "%";
            args.add(like); args.add(like); args.add(like);
        }
        args.add(String.valueOf(q.limit));

        String statement = "SELECT w.id,w.canonical_name,w.type_hint,w.maturity,COALESCE(w.summary,''),w.last_active_at," +
                "(SELECT COUNT(*) FROM v4_world_aliases a WHERE a.world_id=w.id)," +
                "(SELECT COUNT(*) FROM v4_world_identity_claims c WHERE c.world_id=w.id)," +
                "(SELECT COUNT(DISTINCT r.source_id) FROM v4_relations r WHERE r.state='ACTIVE' AND r.target_type='WORLD' AND r.target_id=w.id AND r.source_type='MEMORY')," +
                "(SELECT COUNT(DISTINCT r.source_id) FROM v4_relations r WHERE r.state='ACTIVE' AND r.target_type='WORLD' AND r.target_id=w.id AND r.source_type='EVIDENCE')," +
                "(SELECT COUNT(*) FROM v4_facts f WHERE f.subject_world_id=w.id AND f.status='ACTIVE')," +
                "(SELECT COUNT(*) FROM v4_world_merges m WHERE m.parent_world_id=w.id AND m.state='ACTIVE') " +
                "FROM v4_worlds w WHERE " + where + " ORDER BY w.last_active_at DESC,w.id ASC LIMIT ?";
        Cursor c = sql.rawQuery(statement, args.toArray(new String[0]));
        ArrayList<Row> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                out.add(new Row(c.getString(0), c.getString(1), enumType(c.getString(2)), enumMaturity(c.getString(3)),
                        c.getString(4), c.getLong(5), c.getInt(6), c.getInt(7), c.getInt(8), c.getInt(9), c.getInt(10), c.getInt(11)));
            }
        } finally { c.close(); }
        return Collections.unmodifiableList(out);
    }

    public static List<Alias> aliases(VaultDb db, String worldId) {
        String id = CognitiveStoreV4.canonicalWorldId(db, worldId);
        ArrayList<Alias> out = new ArrayList<>();
        Cursor c = db.getReadableDatabase().query("v4_world_aliases",
                new String[]{"alias","source","confidence","user_confirmed"}, "world_id=?", new String[]{id},
                null, null, "user_confirmed DESC,confidence DESC,alias ASC");
        try { while (c.moveToNext()) out.add(new Alias(c.getString(0), c.getString(1), c.getDouble(2), c.getInt(3) != 0)); }
        finally { c.close(); }
        return Collections.unmodifiableList(out);
    }

    public static List<Claim> claims(VaultDb db, String worldId) {
        String id = CognitiveStoreV4.canonicalWorldId(db, worldId);
        ArrayList<Claim> out = new ArrayList<>();
        Cursor c = db.getReadableDatabase().query("v4_world_identity_claims",
                new String[]{"claim_type","claim_value","strength","user_confirmed","evidence_id"}, "world_id=?", new String[]{id},
                null, null, "user_confirmed DESC,strength ASC,claim_type ASC,id ASC");
        try {
            while (c.moveToNext()) {
                try {
                    out.add(new Claim(CognitiveIdentityV4.ClaimType.valueOf(c.getString(0)), c.getString(1),
                            CognitiveIdentityV4.ClaimStrength.valueOf(c.getString(2)), c.getInt(3) != 0, c.getString(4)));
                } catch (Throwable ignored) {}
            }
        } finally { c.close(); }
        return Collections.unmodifiableList(out);
    }

    private static CognitiveDomainV4.WorldTypeHint enumType(String s) {
        try { return CognitiveDomainV4.WorldTypeHint.valueOf(s); } catch (Throwable ignored) { return CognitiveDomainV4.WorldTypeHint.OTHER; }
    }
    private static CognitiveDomainV4.WorldMaturity enumMaturity(String s) {
        try { return CognitiveDomainV4.WorldMaturity.valueOf(s); } catch (Throwable ignored) { return CognitiveDomainV4.WorldMaturity.EMERGING; }
    }
    private static String escapeLike(String s) { return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
}
