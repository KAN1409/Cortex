package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Runtime truth firewall for Context provenance and episode lifecycle.
 *
 * It performs only bounded repairs that are mechanically provable from Cortex's own ledgers:
 * - raw evidence explicitly labelled EPHEMERAL/noise cannot remain supports_context authority;
 * - only the current ACTIVE PRIMARY Context may own an ACTIVE episode.
 *
 * No memory, derived item, snapshot or user content is deleted here.
 */
public final class ContextTruthIntegrity {
    private static final String NOISE_AUTHORITY_WHERE=
        "from_type='raw_signal' AND to_type='context' AND relation='supports_context' "+
        "AND lower(COALESCE(metadata_json,'')) LIKE '%\"memory_tier\":\"ephemeral\"%' "+
        "AND lower(COALESCE(metadata_json,'')) LIKE '%noise%'";
    private static final String STALE_EPISODE_WHERE=
        "state='ACTIVE' AND ended_at=0 AND NOT EXISTS ("+
        "SELECT 1 FROM context_stack_state s JOIN contexts c ON c.id=s.context_id "+
        "WHERE s.context_id=context_episodes.context_id AND s.role='PRIMARY' AND c.lifecycle='ACTIVE')";

    public static final class ReconcileResult {
        public final int noiseLinksRemoved,staleEpisodesClosed;
        ReconcileResult(int links,int episodes){noiseLinksRemoved=Math.max(0,links);staleEpisodesClosed=Math.max(0,episodes);}
        public int repairs(){return noiseLinksRemoved+staleEpisodesClosed;}
        public String summary(){return "noise_links_removed="+noiseLinksRemoved+" · stale_episodes_closed="+staleEpisodesClosed;}
    }

    private ContextTruthIntegrity(){}

    public static ReconcileResult reconcile(VaultDb db){
        if(db==null)return new ReconcileResult(0,0);ContextSchema.ensure(db);CognitiveStore.ensure(db);
        SQLiteDatabase sql=db.getWritableDatabase();int removed=0,closed=0;
        try{removed=sql.delete("source_links",NOISE_AUTHORITY_WHERE,null);}catch(Throwable ignored){}
        try{
            ContentValues v=new ContentValues();v.put("state","ENDED");v.put("transition","RECONCILE");
            v.put("reason","Truth integrity: only current ACTIVE PRIMARY owns an active episode");v.put("ended_at",System.currentTimeMillis());
            closed=sql.update("context_episodes",v,STALE_EPISODE_WHERE,null);
        }catch(Throwable ignored){}
        return new ReconcileResult(removed,closed);
    }

    public static int noiseAuthorityCount(VaultDb db){return count(db,"SELECT COUNT(*) FROM source_links WHERE "+NOISE_AUTHORITY_WHERE);}
    public static int staleActiveEpisodeCount(VaultDb db){return count(db,"SELECT COUNT(*) FROM context_episodes WHERE "+STALE_EPISODE_WHERE);}

    private static int count(VaultDb db,String sql){
        if(db==null)return 0;Cursor c=null;try{ContextSchema.ensure(db);CognitiveStore.ensure(db);c=db.getReadableDatabase().rawQuery(sql,null);return c.moveToFirst()?Math.max(0,c.getInt(0)):0;}catch(Throwable ignored){return 0;}finally{if(c!=null)c.close();}
    }
}
