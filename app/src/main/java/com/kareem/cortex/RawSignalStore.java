package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;

/** Temporary/raw signal layer. Only an applied authoritative decision may enter durable Cortex memory. */
public final class RawSignalStore {
    private static final String FAST_POLICY = "relevance_fast_004";
    private static final String TIER0_POLICY = "cognitive_tier0_v2_001";
    private static final String THREAD_POLICY = "thread_authority";
    private static final String CONNECTOR_POLICY = "trusted_connector_enrichment_v1";
    private RawSignalStore() {}

    public static void ensure(VaultDb db) { CognitiveStore.ensure(db); }

    public static long capture(VaultDb db, MasterRelevanceFilter.Signal signal) {
        ensure(db);
        cleanup(db);
        String contentHash = Fingerprint.text(signal.text());
        String fp = Fingerprint.text(signal.kind + "|" + signal.source + "|" + signal.title + "|" + signal.body + "|" + (signal.occurredAt / 60000));
        long existing = find(db, fp);
        if (existing > 0) return existing;

        boolean notificationV2 = signal != null && "notification".equalsIgnoreCase(signal.kind);
        MasterRelevanceFilter.Decision fast = notificationV2 ? MasterRelevanceFilter.evaluateTier0(signal) : fastDecision(signal);
        CognitiveSignalV2.SignalFamily family = notificationV2 ? CognitiveSignalV2.classify(signal) : CognitiveSignalV2.SignalFamily.UNKNOWN;
        boolean sensitive = notificationV2 && MasterRelevanceFilter.sensitiveSignal(signal);
        CognitiveSignalV2.CognitiveState cognitiveState = !notificationV2 ? null
                : (sensitive ? CognitiveSignalV2.CognitiveState.SENSITIVE_BLOCKED
                : (fast.disposition == MasterRelevanceFilter.Disposition.IGNORE
                ? CognitiveSignalV2.CognitiveState.IGNORED_NOISE
                : CognitiveSignalV2.CognitiveState.PENDING_ADJUDICATION));

        long now = System.currentTimeMillis();
        long retention = retentionUntil(now, fast.disposition);
        ContentValues v = new ContentValues();
        v.put("kind", signal.kind);
        v.put("source", signal.source);
        v.put("title", signal.title);
        v.put("body", signal.body);
        v.put("metadata_json", signal.metadataJson);
        v.put("fingerprint", fp);
        v.put("content_hash", contentHash);
        v.put("state", "filtered");
        v.put("disposition", fast.disposition.name());
        v.put("importance", fast.importance);
        v.put("confidence", fast.confidence);
        v.put("policy_version", notificationV2 ? TIER0_POLICY : FAST_POLICY);
        v.put("filter_engine", notificationV2 ? "tier0_hard_noise_gate" : "deterministic_fast_gate");
        v.put("reason", fast.reason);
        if (notificationV2) {
            v.put("signal_family", family.name());
            v.put("cognitive_state", cognitiveState.name());
            v.put("cognitive_run_id", 0);
            v.put("final_reason", cognitiveState == CognitiveSignalV2.CognitiveState.PENDING_ADJUDICATION
                    ? "admitted by Tier 0; awaiting Cortex cognitive adjudication" : fast.reason);
        }
        v.put("occurred_at", signal.occurredAt > 0 ? signal.occurredAt : now);
        v.put("retention_until", retention);
        v.put("created_at", now);
        v.put("updated_at", now);
        long signalId = db.getWritableDatabase().insert("raw_signals", null, v);
        if (signalId <= 0) {
            DiagnosticsLog.warn(db, "RawSignalStore", "capture_insert", "failed", "RAW_SIGNAL_INSERT", 0, 0, 0, 0, 0, null);
            return signalId;
        }

        // V4 Evidence is immutable capture truth. Cognitive outcomes are separate and may change.
        CognitiveMemoryForwardBridgeV4.captureRawSignal(db, signalId, signal, contentHash, now);

        // Obvious noise and sensitive credentials stop before aggregation/model work but still have
        // an explicit terminal cognitive_state for diagnostics.
        if (notificationV2 && cognitiveState != CognitiveSignalV2.CognitiveState.PENDING_ADJUDICATION) return signalId;

        long threadId = SignalThreadStore.attach(db, signalId, signal);
        if (notificationV2) return signalId;

        // Compatibility path for non-notification evidence while notification cognition migrates to V2.
        MasterRelevanceFilter.Decision authority = fast;
        boolean threadAuthority = false;
        if (threadId > 0) {
            MasterRelevanceFilter.Decision threaded = ThreadRelevanceEngine.onSignal(db, threadId, signalId);
            if (threaded != null) {
                authority = threaded;
                threadAuthority = true;
            }
        }

        if (authority.durable() && (!threadAuthority || RelevanceDecisionStatusStore.isApplied(db, signalId))) {
            promote(db, signalId, threadId, signal, authority, !threadAuthority, threadAuthority ? THREAD_POLICY : FAST_POLICY);
        }
        return signalId;
    }

    /** Explicit screen understanding is evidence/context only; UI text can never auto-create durable intelligence. */
    private static MasterRelevanceFilter.Decision fastDecision(MasterRelevanceFilter.Signal s) {
        if (s != null && "screen_context".equalsIgnoreCase(s.kind)) {
            return new MasterRelevanceFilter.Decision(
                    MasterRelevanceFilter.Disposition.CONTEXT,
                    38,
                    "explicit screen evidence; short-lived context until the user asks or promotes it",
                    "",
                    0.94);
        }
        return MasterRelevanceFilter.evaluateFast(s);
    }

    /**
     * A richer trusted Relay revision must be reconsidered by Cortex, not deterministically promoted.
     * The immutable Raw Signal/Evidence remains the same physical event; CONNECTOR_ENRICHMENT carries
     * the richer text and CognitiveAdjudicatorV2 reads it when the signal is re-queued.
     */
    public static void markTrustedEnrichmentPending(VaultDb db,long signalId,MasterRelevanceFilter.Signal enriched){
        if(db==null||signalId<=0||enriched==null||!"notification".equalsIgnoreCase(enriched.kind))return;ensure(db);
        Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"cognitive_state"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");String state="";try{if(c.moveToFirst())state=c.getString(0)==null?"":c.getString(0);}finally{c.close();}
        if(CognitiveSignalV2.CognitiveState.IGNORED_NOISE.name().equals(state)||CognitiveSignalV2.CognitiveState.SENSITIVE_BLOCKED.name().equals(state))return;
        ContentValues v=new ContentValues();v.put("signal_family",CognitiveSignalV2.classify(enriched).name());v.put("cognitive_state",CognitiveSignalV2.CognitiveState.PENDING_ADJUDICATION.name());v.put("cognitive_run_id",0);v.put("final_reason","trusted Relay enrichment added context; cognitive re-adjudication queued");v.put("policy_version",TIER0_POLICY);v.put("filter_engine","tier0_hard_noise_gate");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});
    }

    /**
     * Legacy compatibility hook. Notifications no longer receive semantic promotion here; callers
     * should mark enrichment pending and enqueue CognitiveAdjudicatorV2 instead.
     */
    public static long promoteTrustedEnrichment(VaultDb db,long signalId,long threadId,MasterRelevanceFilter.Signal enriched){
        if(db==null||signalId<=0||enriched==null)return 0;ensure(db);
        if("notification".equalsIgnoreCase(enriched.kind)){markTrustedEnrichmentPending(db,signalId,enriched);return 0;}
        long existing=promotedItemId(db,signalId);if(existing>0)return existing;
        boolean threadEligible=threadId>0&&relevanceThread(db,threadId);
        String recent=threadEligible?SignalThreadStore.recentContext(db,threadId,8):"";
        MasterRelevanceFilter.Decision base=evaluateTrustedEnrichment(enriched,recent,threadEligible);
        MasterRelevanceFilter.Decision d=threadEligible?AdaptiveRelevanceLearning.adapt(db,enriched.source,base):base;
        if(!d.durable())return 0;
        ContentValues v=new ContentValues();v.put("disposition",d.disposition.name());v.put("importance",d.importance);v.put("confidence",d.confidence);v.put("policy_version",CONNECTOR_POLICY);v.put("filter_engine","trusted_connector_enrichment");v.put("reason",d.reason);v.put("updated_at",System.currentTimeMillis());
        db.getWritableDatabase().update("raw_signals",v,"id=? AND promoted_item_id=0",new String[]{String.valueOf(signalId)});
        long item=promote(db,signalId,threadId,enriched,d,false,CONNECTOR_POLICY);
        if(item>0)DiagnosticsLog.info(db,"RawSignalStore","connector_enrichment_promoted",d.disposition.name(),item,threadId,signalId,0,0,0,null);
        return item;
    }

    /** Pure policy hooks kept visible to regression tests. */
    static MasterRelevanceFilter.Decision evaluateTrustedEnrichment(MasterRelevanceFilter.Signal enriched,String recentContext){
        return evaluateTrustedEnrichment(enriched,recentContext,communicationLike(enriched));
    }
    static MasterRelevanceFilter.Decision evaluateTrustedEnrichment(MasterRelevanceFilter.Signal enriched,String recentContext,boolean threadEligible){
        MasterRelevanceFilter.Decision fast=fastDecision(enriched);if(fast.durable())return fast;
        if(threadEligible)return MasterRelevanceFilter.evaluateThread(enriched.body,recentContext==null?"":recentContext);
        return fast;
    }

    private static boolean relevanceThread(VaultDb db,long threadId){
        Cursor c=db.getReadableDatabase().query("signal_threads",new String[]{"kind"},"id=?",new String[]{String.valueOf(threadId)},null,null,null,"1");
        try{if(!c.moveToFirst())return false;String kind=c.getString(0);return "communication".equals(kind)||"email".equals(kind);}finally{c.close();}
    }
    private static boolean communicationLike(MasterRelevanceFilter.Signal s){
        if(s==null)return false;String src=s.source==null?"":s.source.toLowerCase(java.util.Locale.ROOT);
        if(src.contains("whatsapp")||src.contains("telegram")||src.contains("messenger")||src.contains("messaging")||src.contains("messages")||src.contains("signal")||src.contains("sms")||src.contains("gmail")||src.contains("outlook")||src.contains("mail")||src.contains("slack")||src.contains("teams")||src.contains("discord"))return true;
        try{String kind=new JSONObject(s.metadataJson==null?"":s.metadataJson).optString("notification_kind","").toLowerCase(java.util.Locale.ROOT);return "message".equals(kind)||"email".equals(kind);}catch(Throwable ignored){return false;}
    }

    /**
     * Materialize the raw signal as a knowledge item for legacy/non-notification compatibility.
     */
    private static long promote(
            VaultDb db,
            long signalId,
            long threadId,
            MasterRelevanceFilter.Signal s,
            MasterRelevanceFilter.Decision d,
            boolean createDerived,
            String policyVersion) {
        try {
            String policy=(policyVersion==null||policyVersion.trim().isEmpty())?(createDerived?FAST_POLICY:THREAD_POLICY):policyVersion.trim();
            JSONObject meta = new JSONObject();
            meta.put("raw_signal_id", signalId);
            if (threadId > 0) meta.put("thread_id", threadId);
            meta.put("source", s.source);
            meta.put("occurred_at", s.occurredAt);
            meta.put("relevance_disposition", d.disposition.name());
            meta.put("importance", d.importance);
            meta.put("filter_reason", d.reason);
            meta.put("policy_version", policy);
            if (!s.metadataJson.isEmpty()) meta.put("source_metadata", new JSONObject(s.metadataJson));

            String title = s.title.isEmpty() ? friendlyTitle(s) : s.title;
            String tags = "signal," + s.kind.toLowerCase() + ",importance_" + d.importance;
            long inserted = db.insert(
                    typeFor(s),
                    s.source,
                    title,
                    s.body,
                    categoryFor(s, d),
                    tags,
                    "",
                    Fingerprint.text("promoted-signal|" + signalId),
                    meta.toString());
            long itemId = inserted < 0 ? -inserted : inserted;
            if (itemId > 0) {
                ContentValues u = new ContentValues();
                u.put("promoted_item_id", itemId);
                u.put("state", "promoted");
                u.put("retention_until", 0);
                u.put("updated_at", System.currentTimeMillis());
                db.getWritableDatabase().update("raw_signals", u, "id=?", new String[]{String.valueOf(signalId)});
                CognitiveStore.link(
                        db,
                        "raw_signal",
                        signalId,
                        "memory",
                        itemId,
                        "promoted_to",
                        1.0,
                        "{\"policy\":\"" + policy + "\"}");
                if (threadId > 0) {
                    CognitiveStore.link(db, "memory", itemId, "thread", threadId, "from_thread", 1.0, "");
                }
                if (createDerived && (d.disposition == MasterRelevanceFilter.Disposition.ACTION
                        || d.disposition == MasterRelevanceFilter.Disposition.WAITING
                        || d.disposition == MasterRelevanceFilter.Disposition.DECISION)) {
                    long derived = CognitiveStore.addDerived(
                            db,
                            d.disposition.name(),
                            title,
                            s.body,
                            "open",
                            d.confidence,
                            d.importance,
                            Fingerprint.text("derived|" + d.disposition.name() + "|" + signalId),
                            meta.toString());
                    if (derived > 0) {
                        CognitiveStore.setDerivedRouting(db, derived, s.source, threadId, signalId, d.disposition.name());
                        CognitiveStore.link(db, "raw_signal", signalId, "derived", derived, "supports", 1.0, "");
                        CognitiveStore.link(db, "derived", derived, "memory", itemId, "grounded_by", 1.0, "");
                        if (threadId > 0) {
                            CognitiveStore.link(db, "derived", derived, "thread", threadId, "derived_from_thread", 1.0, "");
                        }
                    }
                }
            }
            return itemId;
        } catch (Throwable e) {
            DiagnosticsLog.error(db, "RawSignalStore", "promote", e, "RAW_SIGNAL_PROMOTE", 0, threadId, signalId, 0, 0, null);
            return 0;
        }
    }

    public static long promotedItemId(VaultDb db, long signalId) {
        ensure(db);
        Cursor c = db.getReadableDatabase().query(
                "raw_signals", new String[]{"promoted_item_id"}, "id=?",
                new String[]{String.valueOf(signalId)}, null, null, null, "1");
        long id = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return id;
    }

    public static long threadId(VaultDb db, long signalId) {
        ensure(db);
        Cursor c = db.getReadableDatabase().query(
                "raw_signals", new String[]{"thread_id"}, "id=?",
                new String[]{String.valueOf(signalId)}, null, null, null, "1");
        long id = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return id;
    }

    public static String cognitiveState(VaultDb db,long signalId){
        ensure(db);Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"cognitive_state"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");try{return c.moveToFirst()&&c.getString(0)!=null?c.getString(0):"";}finally{c.close();}
    }

    public static void cleanup(VaultDb db) {
        ensure(db);
        long now = System.currentTimeMillis();
        String where = "promoted_item_id=0 AND retention_until>0 AND retention_until<? " +
                "AND NOT EXISTS (SELECT 1 FROM source_links l JOIN derived_items d ON d.id=l.to_id " +
                "WHERE l.from_type='raw_signal' AND l.from_id=raw_signals.id AND l.to_type='derived' " +
                "AND d.state IN ('pending','open'))";
        db.getWritableDatabase().delete("raw_signals", where, new String[]{String.valueOf(now)});
    }

    private static long find(VaultDb db, String fp) {
        Cursor c = db.getReadableDatabase().query(
                "raw_signals", new String[]{"id"}, "fingerprint=?", new String[]{fp}, null, null, null, "1");
        long id = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return id;
    }

    private static long retentionUntil(long now, MasterRelevanceFilter.Disposition d) {
        if (d == MasterRelevanceFilter.Disposition.IGNORE) return now + 6L * 60 * 60 * 1000;
        if (d == MasterRelevanceFilter.Disposition.CONTEXT || d == MasterRelevanceFilter.Disposition.REVIEW) {
            return now + 7L * 24 * 60 * 60 * 1000;
        }
        return 0;
    }

    private static String typeFor(MasterRelevanceFilter.Signal s) {
        return "notification".equalsIgnoreCase(s.kind) ? "NOTIFICATION" : "SIGNAL";
    }

    private static String categoryFor(MasterRelevanceFilter.Signal s, MasterRelevanceFilter.Decision d) {
        if (d.disposition == MasterRelevanceFilter.Disposition.ACTION) return "Actions";
        if (d.disposition == MasterRelevanceFilter.Disposition.WAITING) return "Waiting";
        if (d.disposition == MasterRelevanceFilter.Disposition.DECISION) return "Decisions";
        return "Memory";
    }

    private static String friendlyTitle(MasterRelevanceFilter.Signal s) {
        return "notification".equalsIgnoreCase(s.kind) ? "Notification" : "Signal";
    }
}
