package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import java.util.*;

/** Builds compact, grounded, cloud-eligible context for one attention candidate. */
public final class AttentionContextBuilder {
    private AttentionContextBuilder(){}
    public static final class Pack {public final String text;public final int evidenceCount;Pack(String t,int n){text=t;evidenceCount=n;}public boolean usable(){return evidenceCount>0&&!text.trim().isEmpty();}}

    public static Pack build(Context context,VaultDb db,PrimeBriefStore.Item item){
        if(context==null||db==null||item==null||item.id<=0)return new Pack("",0);CognitiveStore.ensure(db);LinkedHashMap<Long,KnowledgeItem> evidence=new LinkedHashMap<>();
        Cursor links=db.getReadableDatabase().query("source_links",new String[]{"from_id"},"from_type='memory' AND to_type='derived' AND to_id=?",new String[]{String.valueOf(item.id)},null,null,"created_at DESC","8");
        while(links.moveToNext()){KnowledgeItem k=db.getById(links.getLong(0));if(k!=null&&CloudEvidencePolicy.canSend(context,k))evidence.put(k.id,k);}links.close();
        if(evidence.isEmpty())return new Pack("",0);

        StringBuilder out=new StringBuilder();out.append("CANDIDATE\nKind: ").append(item.kind).append("\nTitle: ").append(clip(item.title,180)).append("\nBody: ").append(clip(item.body,700)).append("\nSource: ").append(clip(item.source,120)).append("\nImportance: ").append(item.importance).append("\nClassifier confidence: ").append(String.format(Locale.US,"%.2f",item.confidence)).append("\n\nGROUNDED EVIDENCE\n");
        int n=0;for(KnowledgeItem k:evidence.values()){String body=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);out.append("[E").append(++n).append("] ").append(clip(k.title,120)).append("\n").append(clip(body,550)).append("\n");if(n>=5)break;}

        if(item.threadId>0){Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"kind","title","body","updated_at"},"thread_id=? AND id<>?",new String[]{String.valueOf(item.threadId),String.valueOf(item.id)},null,null,"updated_at DESC","4");int i=0;while(c.moveToNext()){if(i++==0)out.append("\nRELATED DERIVED CONTEXT\n");out.append("- ").append(c.getString(0)).append(": ").append(clip(!empty(c.getString(2))?c.getString(2):c.getString(1),300)).append('\n');}c.close();}
        return new Pack(out.toString().trim(),n);
    }

    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
