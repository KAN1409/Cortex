package com.kareem.cortex;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Correlation and bounded replay guard for the Relay V2 control-result channel.
 *
 * TRUST BOUNDARY. A control result arriving from Relay is an inbound claim about work Cortex
 * asked for. Before any such claim is allowed to become the authoritative "last result" that
 * Cortex shows or reasons over, it must name an outstanding request id that Cortex itself
 * minted, of the matching kind, inside the TTL, and it must not already have been answered.
 *
 * Everything is process-local and bounded:
 *   - at most {@link #MAX_OUTSTANDING} requests may be in flight; the oldest is evicted first;
 *   - an outstanding request expires after {@link #TTL_MS} whether or not a result arrives;
 *   - the last {@link #MAX_ANSWERED} answered ids are retained solely so a second copy of the
 *     same result is classified DUPLICATE_REPLAY rather than silently re-accepted;
 *   - nothing here retains payload bytes, only ids, kinds and timestamps.
 *
 * A verdict other than {@link Verdict#ACCEPTED_FIRST} means the result is DIAGNOSTIC ONLY:
 * callers must record it as an observation and must not let it influence authoritative state.
 */
public final class CortexRelayControlCorrelatorV2 {
    /** Maximum concurrently outstanding control requests. Oldest is evicted when exceeded. */
    public static final int MAX_OUTSTANDING = 64;
    /** Answered ids retained for replay detection. */
    public static final int MAX_ANSWERED = 256;
    /** An outstanding request older than this can no longer be correlated. */
    public static final long TTL_MS = 10L * 60L * 1000L;
    /** Bound on an accepted request_id, mirroring the protocol's own request_id bound. */
    public static final int MAX_REQUEST_ID_CHARS = 180;

    public enum Verdict {
        /** Correlated to a live outstanding request of the right kind. Authoritative. */
        ACCEPTED_FIRST,
        /** This exact request id was already answered. Diagnostic only. */
        DUPLICATE_REPLAY,
        /** No such outstanding request (never issued, evicted, or expired). Diagnostic only. */
        UNKNOWN_REQUEST,
        /** Outstanding id exists but the result is for a different control kind. Diagnostic only. */
        KIND_MISMATCH,
        /** Result carried no usable request_id at all. Diagnostic only. */
        MISSING_REQUEST_ID;

        /** True only for the one verdict permitted to influence authoritative state. */
        public boolean authoritative() { return this == ACCEPTED_FIRST; }
    }

    private static final class Outstanding {
        final String kind;
        final long issuedAt;
        Outstanding(String kind, long issuedAt) { this.kind = kind; this.issuedAt = issuedAt; }
    }

    private static final LinkedHashMap<String, Outstanding> OUTSTANDING = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Long> ANSWERED = new LinkedHashMap<>();

    private CortexRelayControlCorrelatorV2() {}

    /**
     * Record that Cortex has just sent a control request of {@code kind} bearing {@code requestId}.
     * Returns false if the id is unusable or is already outstanding (an id is never reused).
     */
    public static synchronized boolean registerOutstanding(String requestId, String kind, long nowMs) {
        String id = normalise(requestId);
        String k = normalise(kind);
        if (id.isEmpty() || k.isEmpty()) return false;
        expire(nowMs);
        if (OUTSTANDING.containsKey(id) || ANSWERED.containsKey(id)) return false;
        while (OUTSTANDING.size() >= MAX_OUTSTANDING) {
            Iterator<String> it = OUTSTANDING.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
        OUTSTANDING.put(id, new Outstanding(k, nowMs));
        return true;
    }

    /**
     * Classify an inbound control result. Consumes the outstanding request on ACCEPTED_FIRST so a
     * replayed copy of the same result is bounded to exactly one authoritative effect.
     */
    public static synchronized Verdict correlate(String requestId, String kind, long nowMs) {
        String id = normalise(requestId);
        String k = normalise(kind);
        if (id.isEmpty() || id.length() > MAX_REQUEST_ID_CHARS) return Verdict.MISSING_REQUEST_ID;
        expire(nowMs);
        if (ANSWERED.containsKey(id)) return Verdict.DUPLICATE_REPLAY;
        Outstanding pending = OUTSTANDING.get(id);
        if (pending == null) return Verdict.UNKNOWN_REQUEST;
        if (!pending.kind.equals(k)) return Verdict.KIND_MISMATCH;
        OUTSTANDING.remove(id);
        remember(id, nowMs);
        return Verdict.ACCEPTED_FIRST;
    }

    /** Outstanding request count after expiry, for diagnostics. */
    public static synchronized int outstandingCount(long nowMs) {
        expire(nowMs);
        return OUTSTANDING.size();
    }

    /** Drop all correlation state. Called when the authenticated V2 session ends. */
    public static synchronized void reset() {
        OUTSTANDING.clear();
        ANSWERED.clear();
    }

    private static void remember(String id, long nowMs) {
        ANSWERED.put(id, nowMs);
        while (ANSWERED.size() > MAX_ANSWERED) {
            Iterator<String> it = ANSWERED.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
    }

    private static void expire(long nowMs) {
        Iterator<Map.Entry<String, Outstanding>> it = OUTSTANDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Outstanding> e = it.next();
            long age = nowMs - e.getValue().issuedAt;
            if (age > TTL_MS || age < -TTL_MS) it.remove();
        }
    }

    private static String normalise(String value) { return value == null ? "" : value.trim(); }
}
