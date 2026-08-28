package com.kareem.cortex;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Retention invariants for canonical V4 objects. No deletion is enabled yet. */
public final class CognitiveRetentionV4 {
    private CognitiveRetentionV4(){}

    /** A retained pinned Memory must not lose the Evidence required to verify it. */
    public static int reconcilePinnedEvidence(VaultDb db){if(db==null)throw new IllegalArgumentException("db required");CognitiveStoreV4.ensure(db);return reconcilePinnedEvidence(db.getWritableDatabase());}

    static int reconcilePinnedEvidence(SQLiteDatabase sql){
        if(sql==null)throw new IllegalArgumentException("db required");
        ContentValues v=new ContentValues();v.put("retention_class",CognitiveDomainV4.RetentionClass.PINNED.name());v.put("expires_at",0);v.put("updated_at",System.currentTimeMillis());
        return sql.update("v4_evidence",v,"id IN (SELECT me.evidence_id FROM v4_memory_evidence me JOIN v4_memories m ON m.id=me.memory_id WHERE m.state='ACTIVE' AND m.pinned=1)",null);
    }
}
