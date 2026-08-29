package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;

/** Product-facing Relay health derived from authenticated Local Bus history, not only a live Messenger object. */
public final class RelayHealthStore {
    private static final String CONNECTOR = "second_brain";
    private RelayHealthStore() {}

    public static final class Status {
        public final boolean sessionLive, seen, recentEvidence;
        public final long lastSeenAt, lastEventAt;
        public final int acceptedEvents, rejectedEvents;

        Status(boolean sessionLive, boolean seen, boolean recentEvidence, long lastSeenAt, long lastEventAt,
               int acceptedEvents, int rejectedEvents) {
            this.sessionLive=sessionLive;this.seen=seen;this.recentEvidence=recentEvidence;
            this.lastSeenAt=lastSeenAt;this.lastEventAt=lastEventAt;
            this.acceptedEvents=acceptedEvents;this.rejectedEvents=rejectedEvents;
        }

        public boolean healthy() { return sessionLive || recentEvidence || seen; }
        public String label() {
            if (sessionLive) return "Relay live";
            if (recentEvidence) return "Relay active";
            if (seen) return "Relay ready";
            return "Relay not seen";
        }
    }

    public static Status read(Context context, VaultDb db) {
        boolean live = CortexRelayBridgeV2.isRelayV2Connected();
        if (db == null) return new Status(live, live, false, 0, 0, 0, 0);
        long seenAt=0,eventAt=0;int accepted=0,rejected=0;
        try {
            CortexLocalBusStoreV1.ensure(db);
            Cursor c=db.getReadableDatabase().rawQuery(
                    "SELECT last_seen_at,last_event_at,accepted_events,rejected_events FROM connector_clients WHERE connector_id=? LIMIT 1",
                    new String[]{CONNECTOR});
            try {
                if(c.moveToFirst()){seenAt=c.getLong(0);eventAt=c.getLong(1);accepted=c.getInt(2);rejected=c.getInt(3);}
            } finally { c.close(); }
        } catch (Throwable ignored) {}
        long now=System.currentTimeMillis();
        boolean recentEvent=accepted>0&&eventAt>0&&now-eventAt<=48L*60L*60L*1000L;
        boolean recentSeen=seenAt>0&&now-seenAt<=7L*24L*60L*60L*1000L;
        return new Status(live, live||recentSeen||accepted>0, recentEvent, seenAt, eventAt, accepted, rejected);
    }
}
