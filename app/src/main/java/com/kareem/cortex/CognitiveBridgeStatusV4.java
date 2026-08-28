package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Read-only product status for the two external cognition inputs visible in Pulse:
 * trusted Second Brain Local Bus evidence and the latest applied ChatGPT Deep Brain pass.
 *
 * This is observability only. It does not create Evidence, change Situation state, or trigger AI.
 */
public final class CognitiveBridgeStatusV4 {
    private CognitiveBridgeStatusV4() {}

    public static Snapshot current(VaultDb db) {
        if (db == null) return Snapshot.empty();
        try {
            CortexLocalBusStoreV1.ensure(db);
            CognitiveDeepBrainStoreV4.ensure(db);
            CognitiveStoreV4.ensure(db);
            return read(db.getReadableDatabase());
        } catch (Throwable ignored) {
            return Snapshot.empty();
        }
    }

    /** Package-visible pure database projection used by regression tests. Schema must already exist. */
    static Snapshot read(SQLiteDatabase sql) {
        if (sql == null) return Snapshot.empty();
        boolean secondBrainSeen = false;
        long secondBrainLastSeenAt = 0L, secondBrainLastEventAt = 0L;
        int accepted = 0, rejected = 0;
        Cursor client = sql.rawQuery(
                "SELECT last_seen_at,last_event_at,accepted_events,rejected_events " +
                "FROM connector_clients WHERE connector_id='second_brain' LIMIT 1", null);
        try {
            if (client.moveToFirst()) {
                secondBrainSeen = true;
                secondBrainLastSeenAt = client.getLong(0);
                secondBrainLastEventAt = client.getLong(1);
                accepted = client.getInt(2);
                rejected = client.getInt(3);
            }
        } finally { client.close(); }

        String latestSource = "", latestEventId = "";
        long latestOccurredAt = 0L, latestReceivedAt = 0L, latestSignalId = 0L;
        Cursor event = sql.rawQuery(
                "SELECT event_id,source_package,occurred_at,received_at,signal_id " +
                "FROM connector_ingest_events WHERE connector_id='second_brain' AND state='ACCEPTED' " +
                "ORDER BY received_at DESC,event_id DESC LIMIT 1", null);
        try {
            if (event.moveToFirst()) {
                latestEventId = n(event.getString(0));
                latestSource = n(event.getString(1));
                latestOccurredAt = event.getLong(2);
                latestReceivedAt = event.getLong(3);
                latestSignalId = event.getLong(4);
            }
        } finally { event.close(); }

        int enrichedEvidence = scalarInt(sql,
                "SELECT COUNT(DISTINCT evidence_id) FROM v4_evidence_analysis " +
                "WHERE analysis_kind='CONNECTOR_ENRICHMENT' AND engine='local_bus:second_brain'");
        int enrichedSituations = scalarInt(sql,
                "SELECT COUNT(*) FROM v4_situations s WHERE s.state NOT IN ('RESOLVED','CANCELLED','DISMISSED') " +
                "AND EXISTS (SELECT 1 FROM v4_provenance sp " +
                "JOIN v4_memory_evidence me ON me.memory_id=sp.source_id " +
                "JOIN v4_evidence_analysis ea ON ea.evidence_id=me.evidence_id " +
                "AND ea.analysis_kind='CONNECTOR_ENRICHMENT' AND ea.engine='local_bus:second_brain' " +
                "WHERE sp.object_type='SITUATION' AND sp.object_id=s.id AND sp.source_type='MEMORY')");

        long latestAppliedAt = scalarLong(sql,
                "SELECT COALESCE(MAX(applied_at),0) FROM v4_deep_brain_requests " +
                "WHERE state='APPLIED' AND applied_at>0");
        int activePriorities = scalarInt(sql,
                "SELECT COUNT(*) FROM v4_deep_brain_priority_items WHERE state='ACTIVE'");
        int activeActions = scalarInt(sql,
                "SELECT COUNT(*) FROM v4_action_proposals WHERE state='PROPOSED' " +
                "AND payload_json LIKE '%\"origin\":\"chatgpt_plus_share\"%'");
        int newOpen = latestAppliedAt > 0
                ? scalarInt(sql,"SELECT COUNT(*) FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED') AND updated_at>"+latestAppliedAt)
                : scalarInt(sql,"SELECT COUNT(*) FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED')");

        return new Snapshot(secondBrainSeen, secondBrainLastSeenAt, secondBrainLastEventAt,
                accepted, rejected, latestEventId, latestSource, latestOccurredAt,
                latestReceivedAt, latestSignalId, enrichedEvidence, enrichedSituations,
                latestAppliedAt, activePriorities, activeActions, newOpen);
    }

    private static int scalarInt(SQLiteDatabase sql, String query) {
        Cursor c = sql.rawQuery(query, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    private static long scalarLong(SQLiteDatabase sql, String query) {
        Cursor c = sql.rawQuery(query, null);
        try { return c.moveToFirst() ? c.getLong(0) : 0L; }
        finally { c.close(); }
    }

    private static String n(String s) { return s == null ? "" : s.trim(); }

    public static final class Snapshot {
        public final boolean secondBrainSeen;
        public final long secondBrainLastSeenAt, secondBrainLastEventAt;
        public final int secondBrainAccepted, secondBrainRejected;
        public final String latestEventId, latestSourcePackage;
        public final long latestOccurredAt, latestReceivedAt, latestSignalId;
        public final int connectorEnrichedEvidence, connectorEnrichedSituations;
        public final long latestChatGptAppliedAt;
        public final int activeChatGptPriorities, activeChatGptActions, newSinceChatGpt;

        Snapshot(boolean seen, long lastSeen, long lastEvent, int accepted, int rejected,
                 String eventId, String source, long occurred, long received, long signalId,
                 int enrichedEvidence, int enrichedSituations, long chatGptAt,
                 int priorities, int actions, int fresh) {
            secondBrainSeen = seen;
            secondBrainLastSeenAt = lastSeen;
            secondBrainLastEventAt = lastEvent;
            secondBrainAccepted = Math.max(0, accepted);
            secondBrainRejected = Math.max(0, rejected);
            latestEventId = n(eventId);
            latestSourcePackage = n(source);
            latestOccurredAt = Math.max(0L, occurred);
            latestReceivedAt = Math.max(0L, received);
            latestSignalId = Math.max(0L, signalId);
            connectorEnrichedEvidence = Math.max(0, enrichedEvidence);
            connectorEnrichedSituations = Math.max(0, enrichedSituations);
            latestChatGptAppliedAt = Math.max(0L, chatGptAt);
            activeChatGptPriorities = Math.max(0, priorities);
            activeChatGptActions = Math.max(0, actions);
            newSinceChatGpt = Math.max(0, fresh);
        }

        static Snapshot empty() {
            return new Snapshot(false,0,0,0,0,"","",0,0,0,0,0,0,0,0,0);
        }

        public boolean hasAnythingToShow() {
            return secondBrainSeen || latestChatGptAppliedAt > 0 || activeChatGptPriorities > 0;
        }
    }
}
