package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.SimpleDateFormat;
import java.util.*;

/** Writes a readable, revisioned history for one situation from exact linked evidence. */
public final class DiscoveryHistoryWriter {
    private DiscoveryHistoryWriter(){}

    public static long rebuild(SQLiteDatabase db,long situationId){
        DiscoverySchema.ensure(db);
        ArrayList<Row> rows=new ArrayList<>();
        Cursor c=db.rawQuery(
                "SELECT k.id,k.created_at,k.title,k.summary,k.extracted_text,k.source,d.space "+
                "FROM discovery_situation_evidence se JOIN knowledge_items k ON k.id=se.item_id "+
                "JOIN discovery_annotations d ON d.item_id=k.id WHERE se.situation_id=? ORDER BY k.created_at ASC,k.id ASC",
                new String[]{String.valueOf(situationId)});
        while(c.moveToNext())rows.add(new Row(c.getLong(0),c.getLong(1),s(c,2),s(c,3),s(c,4),s(c,5),s(c,6)));
        c.close();
        if(rows.isEmpty())return 0;

        StringBuilder h=new StringBuilder();
        h.append("History\n");
        SimpleDateFormat day=new SimpleDateFormat("dd MMM yyyy · HH:mm",Locale.getDefault());
        for(Row r:rows){
            String body=!r.summary.isEmpty()?r.summary:r.extracted;
            if(body.length()>500)body=body.substring(0,500)+"…";
            h.append("\n").append(day.format(new Date(r.when))).append("\n");
            h.append(r.title.isEmpty()?"Evidence":r.title);
            if(!body.isEmpty())h.append("\n").append(body);
            if(!r.source.isEmpty())h.append("\nSource: ").append(r.source);
            h.append("\nEvidence #").append(r.id).append("\n");
        }

        String text=h.toString().trim();
        Cursor last=db.rawQuery("SELECT id,revision,history_text,evidence_count FROM discovery_history_revisions WHERE situation_id=? ORDER BY revision DESC LIMIT 1",new String[]{String.valueOf(situationId)});
        long lastId=0;int revision=0,oldCount=0;String oldText="";
        if(last.moveToFirst()){lastId=last.getLong(0);revision=last.getInt(1);oldText=s(last,2);oldCount=last.getInt(3);}
        last.close();
        if(text.equals(oldText))return lastId;

        ContentValues v=new ContentValues();
        v.put("situation_id",situationId);v.put("revision",revision+1);v.put("history_text",text);
        v.put("evidence_count",rows.size());
        int delta=rows.size()-oldCount;
        v.put("change_summary",revision==0?"Initial history from "+rows.size()+" evidence item(s).":(delta>0?delta+" new evidence item(s) changed this history.":"History interpretation changed."));
        v.put("created_at",System.currentTimeMillis());
        return db.insertOrThrow("discovery_history_revisions",null,v);
    }

    public static String latest(SQLiteDatabase db,long situationId){
        Cursor c=db.rawQuery("SELECT history_text FROM discovery_history_revisions WHERE situation_id=? ORDER BY revision DESC LIMIT 1",new String[]{String.valueOf(situationId)});
        String x=c.moveToFirst()?s(c,0):"";c.close();return x;
    }

    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class Row{
        final long id,when;final String title,summary,extracted,source,space;
        Row(long i,long w,String t,String s,String e,String so,String sp){id=i;when=w;title=t;summary=s;extracted=e;source=so;space=sp;}
    }
}
