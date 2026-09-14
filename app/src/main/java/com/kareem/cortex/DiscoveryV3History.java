package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.SimpleDateFormat;
import java.util.*;

public final class DiscoveryV3History {
    private DiscoveryV3History(){}

    public static void rebuild(SQLiteDatabase db,long situationId){
        DiscoveryV3Schema.ensure(db);
        Cursor c=db.rawQuery(
                "SELECT k.id,k.created_at,k.title,k.summary,k.extracted_text,k.source "+
                "FROM discovery_v3_evidence e JOIN knowledge_items k ON k.id=e.item_id "+
                "WHERE e.situation_id=? ORDER BY k.created_at ASC,k.id ASC LIMIT 80",
                new String[]{String.valueOf(situationId)});
        ArrayList<Row> rows=new ArrayList<>();
        while(c.moveToNext())rows.add(new Row(c.getLong(0),c.getLong(1),s(c,2),s(c,3),s(c,4),s(c,5)));
        c.close();if(rows.isEmpty())return;

        StringBuilder b=new StringBuilder();
        SimpleDateFormat f=new SimpleDateFormat("dd MMM yyyy · HH:mm",Locale.getDefault());
        b.append("Cortex history\n");
        for(Row r:rows){
            String body=!r.summary.isEmpty()?r.summary:r.extracted;
            body=body.replaceAll("\\s+"," ").trim();
            if(body.length()>360)body=body.substring(0,360)+"…";
            b.append("\n").append(f.format(new Date(r.when))).append("\n");
            b.append(r.title.isEmpty()?"Evidence":r.title);
            if(!body.isEmpty())b.append("\n").append(body);
            b.append("\nEvidence #").append(r.id);
            if(!r.source.isEmpty())b.append(" · ").append(r.source);
            b.append("\n");
        }
        b.append("\nCortex note\nMissing evidence is treated as unknown, not as proof that something did not happen.");

        String text=b.toString().trim();
        Cursor last=db.rawQuery("SELECT revision,body FROM discovery_v3_history WHERE situation_id=? ORDER BY revision DESC LIMIT 1",
                new String[]{String.valueOf(situationId)});
        int rev=0;String prev="";if(last.moveToFirst()){rev=last.getInt(0);prev=s(last,1);}last.close();
        if(text.equals(prev))return;
        ContentValues v=new ContentValues();v.put("situation_id",situationId);v.put("revision",rev+1);v.put("body",text);
        v.put("evidence_count",rows.size());v.put("created_at",System.currentTimeMillis());
        db.insertOrThrow("discovery_v3_history",null,v);
    }

    public static String latest(SQLiteDatabase db,long sid){
        Cursor c=db.rawQuery("SELECT body FROM discovery_v3_history WHERE situation_id=? ORDER BY revision DESC LIMIT 1",
                new String[]{String.valueOf(sid)});
        String x=c.moveToFirst()?s(c,0):"";c.close();return x;
    }
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class Row{final long id,when;final String title,summary,extracted,source;Row(long i,long w,String t,String s,String e,String so){id=i;when=w;title=t;summary=s;extracted=e;source=so;}}
}
