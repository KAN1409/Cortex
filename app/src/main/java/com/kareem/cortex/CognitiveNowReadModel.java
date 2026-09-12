package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

/** Read-only Now projection from the canonical FINAL-JUDGE attention ledger. */
public final class CognitiveNowReadModel {
    public static final String VERSION="cognitive_now_read_model_002";
    private CognitiveNowReadModel(){}

    public static java.util.List<PrimeBriefStore.Item> load(SQLiteDatabase db,int limit){
        ArrayList<PrimeBriefStore.Item> out=new ArrayList<>();if(db==null)return out;UniversalEventStore.ensure(db);CortexJudgmentTraceStore.ensure(db);CortexV91Authority.enforceLive(db);
        int cap=Math.max(1,Math.min(12,limit));
        String sql="SELECT a.id,a.kind,a.title,a.body,a.source_key,a.confidence,a.priority,a.situation_id,a.updated_at " +
                "FROM ue_attention_items a WHERE a.state='open' AND COALESCE(a.reason,'') LIKE 'FINAL_JUDGE:%' " +
                "AND EXISTS(SELECT 1 FROM cortex_judgment_trace t WHERE t.id=(SELECT t2.id FROM cortex_judgment_trace t2 WHERE t2.situation_id=a.situation_id ORDER BY t2.id DESC LIMIT 1) AND t.final_decision='NOW') " +
                "ORDER BY a.priority DESC,a.updated_at DESC LIMIT ?";
        Cursor c=db.rawQuery(sql,new String[]{String.valueOf(cap)});
        try{while(c.moveToNext())out.add(new PrimeBriefStore.Item(UniversalEventStore.ATTENTION_COMPAT_OFFSET+c.getLong(0),c.getString(1),c.getString(2),c.getString(3),"final_judge|"+nz(c.getString(4)),"open",Math.max(0,Math.min(1,c.getDouble(5))),c.getInt(6),c.getLong(7),0,c.getLong(8)));}finally{c.close();}
        return out;
    }
    private static String nz(String s){return s==null?"":s.trim();}
}
