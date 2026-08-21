package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import java.util.*;

public final class ProactiveEngine {
    private static final long DAY=86400000L;
    private ProactiveEngine(){}

    public static ArrayList<ProactiveSignal> scan(Context ctx,VaultDb db,int limit){
        ArrayList<ProactiveSignal> out=new ArrayList<>();long now=System.currentTimeMillis();
        for(BrainOpenLoop l:SecondBrainEngine.openLoops(db,120)){
            double age=Math.max(0,(now-l.createdAt)/(double)DAY);double p=.80+Math.min(.18,age/120.0)+(empty(l.due)?.0:.12);
            out.add(new ProactiveSignal("OPEN_LOOP",l.action, l.title+(empty(l.due)?"":" • due: "+l.due),empty(l.due)?"Unclosed action":"Action with a due hint",l.itemId,p));
        }
        SharedPreferences sp=ctx.getSharedPreferences("proactive",Context.MODE_PRIVATE);
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,created_at FROM knowledge_items WHERE status='analyzed' AND created_at<? ORDER BY created_at DESC LIMIT 220",new String[]{String.valueOf(now-7*DAY)});
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
        LinkedHashMap<String,ProactiveSignal> uniq=new LinkedHashMap<>();for(ProactiveSignal s:out){String key=s.kind+"|"+s.itemId+"|"+LocalSemanticEmbedder.norm(s.title);if(!uniq.containsKey(key))uniq.put(key,s);}
        ArrayList<ProactiveSignal> clean=new ArrayList<>(uniq.values());if(clean.size()>limit)return new ArrayList<>(clean.subList(0,limit));return clean;
    }

    public static void markSurfaced(Context ctx,Collection<ProactiveSignal> xs){SharedPreferences.Editor e=ctx.getSharedPreferences("proactive",Context.MODE_PRIVATE).edit();long now=System.currentTimeMillis();for(ProactiveSignal s:xs)if(s.itemId>0)e.putLong("surfaced_"+s.itemId,now);e.apply();}
    private static int openCount(VaultDb db,long id){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM actions WHERE item_id=? AND status='open'",new String[]{String.valueOf(id)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
