package com.kareem.cortex;

import android.database.Cursor;
import java.text.SimpleDateFormat;
import java.util.*;

public final class BriefEngine {
    private BriefEngine(){}

    public static String daily(VaultDb db){return build(db,24L*60*60*1000,"DAILY MEMORY BRIEF");}
    public static String weekly(VaultDb db){return build(db,7L*24*60*60*1000,"WEEKLY MEMORY BRIEF");}

    private static String build(VaultDb db,long window,String title){
        long since=System.currentTimeMillis()-window;ArrayList<KnowledgeItem> items=new ArrayList<>();
        Cursor c=db.getReadableDatabase().query("knowledge_items",null,"created_at>=?",new String[]{String.valueOf(since)},null,null,"created_at DESC","200");
        while(c.moveToNext()){
            long id=c.getLong(c.getColumnIndexOrThrow("id"));KnowledgeItem k=db.getById(id);if(k!=null)items.add(k);
        }c.close();
        LinkedHashMap<String,Integer> cats=new LinkedHashMap<>();ArrayList<String> important=new ArrayList<>(),waiting=new ArrayList<>();
        for(KnowledgeItem k:items){String cat=k.category==null||k.category.trim().isEmpty()?"Other":k.category;cats.put(cat,cats.getOrDefault(cat,0)+1);String t=((k.title==null?"":k.title)+" "+(k.summary==null?"":k.summary)+" "+(k.extractedText==null?"":k.extractedText)).toLowerCase(Locale.US);if(!db.actions(k.id).isEmpty()||"failed_retryable".equals(k.status)||"analysis_failed".equals(k.status))important.add(k.title);if(t.contains("waiting")||t.contains("مستني")||t.contains("pending")||t.contains("نتيجة"))waiting.add(k.title);}
        ArrayList<BrainOpenLoop> loops=SecondBrainEngine.openLoops(db,30);
        StringBuilder b=new StringBuilder();b.append(title).append("\n").append(new SimpleDateFormat("dd MMM yyyy • HH:mm",Locale.getDefault()).format(new Date())).append("\n\n");
        b.append("Captured: ").append(items.size()).append(" memories");if(!cats.isEmpty()){b.append("\n\nTOP AREAS\n");for(Map.Entry<String,Integer> e:cats.entrySet())b.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');}
        if(!loops.isEmpty()){b.append("\nOPEN LOOPS\n");for(int i=0;i<Math.min(10,loops.size());i++){BrainOpenLoop l=loops.get(i);b.append("• ").append(l.action);if(l.due!=null&&!l.due.trim().isEmpty())b.append(" — ").append(l.due);b.append('\n');}}
        if(!important.isEmpty()){b.append("\nNEEDS ATTENTION\n");for(int i=0;i<Math.min(8,important.size());i++)b.append("• ").append(important.get(i)).append('\n');}
        if(!waiting.isEmpty()){b.append("\nWAITING / FOLLOW-UP\n");for(int i=0;i<Math.min(8,waiting.size());i++)b.append("• ").append(waiting.get(i)).append('\n');}
        if(!items.isEmpty()){b.append("\nRECENT\n");for(int i=0;i<Math.min(8,items.size());i++){KnowledgeItem k=items.get(i);String s=k.summary==null?"":k.summary.trim();if(s.length()>160)s=s.substring(0,160)+"…";b.append("• ").append(k.title);if(!s.isEmpty())b.append(" — ").append(s);b.append('\n');}}
        return b.toString().trim();
    }
}
