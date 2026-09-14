package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public final class DiscoveryV3Research {
    private DiscoveryV3Research(){}

    public static void enqueueIfNeeded(SQLiteDatabase db,long insightId,String domain,String title,String found,String why){
        Cursor c=db.rawQuery("SELECT 1 FROM discovery_v3_research WHERE insight_id=? AND state IN ('queued','running','complete') LIMIT 1",
                new String[]{String.valueOf(insightId)});
        boolean exists=c.moveToFirst();c.close();if(exists)return;
        String q;
        if("HEALTH".equals(domain))q="Find current reputable medical context relevant to this observation without diagnosing the user: "+title+" — "+found;
        else q="Find current reputable external context that could materially change this assessment: "+title+" — "+found;
        ContentValues v=new ContentValues();long now=System.currentTimeMillis();v.put("insight_id",insightId);v.put("query_text",q);v.put("state","queued");v.put("created_at",now);v.put("updated_at",now);
        db.insert("discovery_v3_research",null,v);
    }
}
