package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import java.util.*;

public final class ProactiveEngine {
    private static final long DAY=86400000L;
    private ProactiveEngine(){}

    public static ArrayList<ProactiveSignal> scan(Context ctx,VaultDb db,int limit){
        ContactSafetyMaintenance.run(db);
        CognitiveStore.ensure(db);
        ArrayList<ProactiveSignal> out=new ArrayList<>();long now=System.currentTimeMillis();

        for(BrainOpenLoop l:SecondBrainEngine.openLoops(db,120)){
            double age=Math.max(0,(now-l.createdAt)/(double)DAY);double p=.80+Math.min(.18,age/120.0)+(empty(l.due)?.0:.12);
            out.add(new ProactiveSignal("OPEN_LOOP",l.action,l.title+(empty(l.due)?"":" • due: "+l.due),empty(l.due)?"Unclosed action":"Action with a due hint",l.itemId,p));
        }

        // Knowledge V2 may promote screenshot-derived commitments directly into the cognitive
        // ledger. These must participate in proactive ranking even when no legacy action row exists.
        Cursor d=db.getReadableDatabase().rawQuery(
                "SELECT id,kind,title,body,confidence,importance,updated_at,metadata_json FROM derived_items "+
                "WHERE state='open' AND kind IN ('ACTION','WAITING') ORDER BY importance DESC,updated_at DESC LIMIT 80",null);
        while(d.moveToNext()){
            long derivedId=d.getLong(0),updated=d.getLong(6);String kind=n(d.getString(1)),title=n(d.getString(2)),body=n(d.getString(3)),meta=n(d.getString(7));
            double confidence=d.getDouble(4);int importance=d.getInt(5);
            if(title.isEmpty()&&body.isEmpty())continue;
            if(noisy(title+" "+body))continue;
            long itemId=linkedMemoryId(db,derivedId,meta);
            double age=Math.max(0,(now-updated)/(double)DAY);
            double priority=.55+Math.min(.25,importance/400.0)+Math.min(.10,age/60.0)+Math.min(.10,confidence*.10);
            String reason="WAITING".equals(kind)?"Cortex is waiting on this":"Structured knowledge marked this as actionable";
            out.add(new ProactiveSignal("WAITING".equals(kind)?"WAITING":"KNOWLEDGE_ACTION",title.isEmpty()?clip(body,120):title,body,reason,itemId,Math.min(1,priority)));
        }
        d.close();

        SharedPreferences sp=ctx.getSharedPreferences("proactive",Context.MODE_PRIVATE);
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,created_at FROM knowledge_items WHERE status='analyzed' AND created_at<? AND NOT (type='CONTACT' AND source='contacts_sync') ORDER BY created_at DESC LIMIT 220",new String[]{String.valueOf(now-7*DAY)});
        while(c.moveToNext()){
            long id=c.getLong(0),created=c.getLong(1);long last=sp.getLong("surfaced_"+id,0);if(last>0&&now-last<7*DAY)continue;
            KnowledgeItem k=db.getById(id);if(k==null)continue;int open=openCount(db,id);double age=Math.max(1,(now-created)/(double)DAY);
            if(open==0&&age<14)continue;
            String body=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);if(body==null)body="";body=body.replaceAll("\\s+"," ").trim();if(body.length()>220)body=body.substring(0,220)+"…";
            double p=.28+Math.min(.38,age/180.0)+(open>0?.25:0)+("AUDIO".equals(k.type)||"SCREENSHOT".equals(k.type)?.06:0);
            String reason=open>0?open+" open action"+(open==1?"":"s")+" tied to this memory":"Not resurfaced recently";
            out.add(new ProactiveSignal(open>0?"FORGOTTEN_LOOP":"RESURFACE","Remember: "+k.title,body,reason,id,p));
        }c.close();

        out.sort((a,b)->Double.compare(b.priority,a.priority));
        LinkedHashMap<String,ProactiveSignal> uniq=new LinkedHashMap<>();
        for(ProactiveSignal s:out){
            String content=LocalSemanticEmbedder.norm(n(s.title)+" "+n(s.body));
            String key=s.kind+"|"+(s.itemId>0?s.itemId:content);
            if(!uniq.containsKey(key))uniq.put(key,s);
        }
        ArrayList<ProactiveSignal> clean=new ArrayList<>(uniq.values());if(clean.size()>limit)return new ArrayList<>(clean.subList(0,limit));return clean;
    }

    public static void markSurfaced(Context ctx,Collection<ProactiveSignal> xs){SharedPreferences.Editor e=ctx.getSharedPreferences("proactive",Context.MODE_PRIVATE).edit();long now=System.currentTimeMillis();for(ProactiveSignal s:xs)if(s.itemId>0)e.putLong("surfaced_"+s.itemId,now);e.apply();}

    private static long linkedMemoryId(VaultDb db,long derivedId,String metadata){
        Cursor c=db.getReadableDatabase().rawQuery("SELECT from_id FROM source_links WHERE from_type='memory' AND to_type='derived' AND to_id=? ORDER BY confidence DESC,id DESC LIMIT 1",new String[]{String.valueOf(derivedId)});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();if(id>0)return id;
        try{org.json.JSONObject o=new org.json.JSONObject(metadata);long evidence=o.optLong("evidence_id",0);if(evidence>0){Cursor x=db.getReadableDatabase().rawQuery("SELECT id FROM knowledge_items WHERE source='knowledge_v2' AND metadata_json LIKE ? ORDER BY updated_at DESC LIMIT 1",new String[]{"%\"evidence_id\":"+evidence+"%"});id=x.moveToFirst()?x.getLong(0):0;x.close();}}catch(Exception ignored){}
        return id;
    }
    private static boolean noisy(String s){String x=n(s).toLowerCase(Locale.ROOT);return x.contains("automations:failed")||x.contains("stacktrace")||x.contains("://")&&x.length()<120||x.contains("cortex is processing");}
    private static int openCount(VaultDb db,long id){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM actions a JOIN knowledge_items k ON k.id=a.item_id WHERE a.item_id=? AND a.status='open' AND NOT (k.type='CONTACT' AND k.source='contacts_sync')",new String[]{String.valueOf(id)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static String clip(String s,int n){String x=n(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
    private static boolean empty(String s){return n(s).isEmpty();}
}
