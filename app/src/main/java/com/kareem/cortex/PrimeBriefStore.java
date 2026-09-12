package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Pure read model for Now/Brief. Final-Judge attention is never re-judged here. */
public final class PrimeBriefStore {
    private PrimeBriefStore(){}
    public static final class Item {public final long id,threadId,signalId,updatedAt;public final String kind,title,body,source,state;public final double confidence;public final int importance;Item(long id,String kind,String title,String body,String source,String state,double confidence,int importance,long threadId,long signalId,long updatedAt){this.id=id;this.kind=n(kind);this.title=n(title);this.body=n(body);this.source=n(source);this.state=n(state);this.confidence=confidence;this.importance=importance;this.threadId=threadId;this.signalId=signalId;this.updatedAt=updatedAt;}}
    public static final class Snapshot {public final ArrayList<KnowledgeItem> recent;public final ArrayList<Item> actions,waiting,decisions,changes,worthKnowing;public final ArrayList<ReviewQueueStore.Item> reviews;Snapshot(ArrayList<KnowledgeItem> recent,ArrayList<Item>a,ArrayList<Item>w,ArrayList<Item>d,ArrayList<Item>c,ArrayList<Item>k,ArrayList<ReviewQueueStore.Item>r){this.recent=recent;actions=a;waiting=w;decisions=d;changes=c;worthKnowing=k;reviews=r;}public boolean empty(){return recent.isEmpty()&&actions.isEmpty()&&waiting.isEmpty()&&decisions.isEmpty()&&reviews.isEmpty()&&changes.isEmpty()&&worthKnowing.isEmpty();}}

    public static Snapshot load(VaultDb db){
        CognitiveStore.ensure(db);CortexV91Authority.enforceLive(db.getWritableDatabase());
        return new Snapshot(intentionalCaptures(db,6),attention(db,"ACTION",12),attention(db,"WAITING",12),attention(db,"DECISION",8),situationChanges(db,12),legacyWorthKnowing(db,8),ReviewQueueStore.pending(db,12));
    }

    private static ArrayList<KnowledgeItem> intentionalCaptures(VaultDb db,int limit){ArrayList<KnowledgeItem> out=new ArrayList<>();for(KnowledgeItem k:db.captureSearch("",120)){String s=n(k.source);if(!("manual".equals(s)||"quick_capture".equals(s)||"android_share".equals(s)))continue;out.add(k);if(out.size()>=limit)break;}return out;}

    /**
     * Now reads only the canonical attention ledger materialized by CortexAttentionJudge.
     * No legacy projection policy, no legacy derived fallback, no second content/noise classifier.
     */
    private static ArrayList<Item> attention(VaultDb db,String kind,int limit){
        ArrayList<Item> out=new ArrayList<>();SQLiteDatabase s=db.getReadableDatabase();CortexJudgmentTraceStore.ensure(s);
        String sql="SELECT a.id,a.kind,a.title,a.body,a.source_key,a.state,a.confidence,a.priority,a.situation_id,a.semantic_event_id,a.updated_at " +
                "FROM ue_attention_items a WHERE a.kind=? AND a.state='open' AND COALESCE(a.reason,'') LIKE 'FINAL_JUDGE:%' " +
                "AND EXISTS(SELECT 1 FROM cortex_judgment_trace t WHERE t.id=(SELECT t2.id FROM cortex_judgment_trace t2 WHERE t2.situation_id=a.situation_id ORDER BY t2.id DESC LIMIT 1) AND t.final_decision='NOW') " +
                "ORDER BY a.priority DESC,a.updated_at DESC LIMIT ?";
        Cursor c=s.rawQuery(sql,new String[]{kind,String.valueOf(Math.max(1,limit))});
        while(c.moveToNext())out.add(new Item(UniversalEventStore.ATTENTION_COMPAT_OFFSET+c.getLong(0),c.getString(1),c.getString(2),c.getString(3),"final_judge|"+n(c.getString(4)),c.getString(5),c.getDouble(6),c.getInt(7),c.getLong(8),0,c.getLong(10)));
        c.close();return dedupe(out,limit);
    }

    /** Brief remains a material situation-transition log; it is not the Now authority. */
    private static ArrayList<Item> situationChanges(VaultDb db,int limit){ArrayList<Item> out=new ArrayList<>();SQLiteDatabase s=db.getReadableDatabase();if(!table(s,"ue_situation_transitions")||!table(s,"ue_projection_decisions"))return out;long since=System.currentTimeMillis()-48L*60L*60L*1000L;String sql="SELECT st.id,st.kind,st.title,st.summary,st.confidence,st.situation_id,st.semantic_event_id,st.occurred_at,COALESCE(e.semantic_type,''),COALESCE(r.source_key,'') FROM ue_situation_transitions st LEFT JOIN ue_semantic_events e ON e.id=st.semantic_event_id LEFT JOIN ue_raw_observations r ON r.id=e.raw_observation_id WHERE st.occurred_at>=? AND st.kind IN ('OPENED','MATERIAL_UPDATE','ESCALATED','DEESCALATED','DEADLINE_APPROACHING','RESOLVED','REOPENED') AND EXISTS(SELECT 1 FROM ue_projection_decisions pd WHERE pd.semantic_event_id=st.semantic_event_id AND pd.projection='BRIEF' AND pd.eligible=1 AND pd.policy_version=?) ORDER BY st.occurred_at DESC LIMIT ?";Cursor c=s.rawQuery(sql,new String[]{String.valueOf(since),StatefulMeaningPolicy.VERSION,String.valueOf(limit*4)});while(c.moveToNext()){String transition=c.getString(1),title=CanonicalPresentation.cleanTitle("situation",c.getString(8),c.getString(2),c.getString(2)),body=CanonicalPresentation.cleanBody(c.getString(3));int importance="ESCALATED".equals(transition)?90:("OPENED".equals(transition)?65:("RESOLVED".equals(transition)?45:60));out.add(new Item(c.getLong(0),"CHANGE",title,body,c.getString(9),transition.toLowerCase(Locale.ROOT),c.getDouble(4),importance,c.getLong(5),0,c.getLong(7)));}c.close();return dedupe(out,limit);}

    private static ArrayList<Item> legacyWorthKnowing(VaultDb db,int limit){ArrayList<Item> out=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state='open' AND COALESCE(candidate_kind,'')<>'UE_ATTENTION' AND kind IN ('IDEA','OPPORTUNITY','INSIGHT','HYPOTHESIS') AND NOT EXISTS(SELECT 1 FROM raw_signals r WHERE r.id=anchor_signal_id AND r.kind='notification') ORDER BY importance DESC,updated_at DESC LIMIT ?",new String[]{String.valueOf(limit*3)});while(c.moveToNext())out.add(fromLegacy(c));c.close();return dedupe(out,limit);}
    private static Item fromLegacy(Cursor c){return new Item(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getDouble(6),c.getInt(7),c.getLong(8),c.getLong(9),c.getLong(10));}

    private static ArrayList<Item> dedupe(ArrayList<Item> xs,int limit){ArrayList<Item> ordered=new ArrayList<>(xs);ordered.sort((a,b)->{int z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.updatedAt,a.updatedAt);});ArrayList<Item> out=new ArrayList<>();for(Item x:ordered){int match=-1;for(int i=0;i<out.size();i++)if(sameProjection(out.get(i),x)){match=i;break;}if(match<0)out.add(x);else if(better(x,out.get(match)))out.set(match,x);}out.sort((a,b)->{int z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.updatedAt,a.updatedAt);});if(out.size()>limit)return new ArrayList<>(out.subList(0,limit));return out;}
    private static boolean better(Item a,Item b){if(a.importance!=b.importance)return a.importance>b.importance;if(Double.compare(a.confidence,b.confidence)!=0)return a.confidence>b.confidence;return a.updatedAt>b.updatedAt;}
    private static boolean sameProjection(Item a,Item b){if(!a.kind.equals(b.kind))return false;if(a.threadId>0&&b.threadId>0&&a.threadId==b.threadId)return true;if(Math.abs(a.updatedAt-b.updatedAt)>6L*60L*60L*1000L)return false;Set<String> aa=topicTokens(a),bb=topicTokens(b);if(aa.size()<3||bb.size()<3)return false;int shared=0;for(String x:aa)if(bb.contains(x))shared++;double overlap=(double)shared/(double)Math.min(aa.size(),bb.size());return shared>=3&&overlap>=0.55;}
    private static Set<String> topicTokens(Item x){String s=LocalSemanticEmbedder.norm(n(x.title)+" "+n(x.body)).toLowerCase(Locale.ROOT);String[] parts=s.split("[^\\p{L}\\p{N}]+");HashSet<String> out=new HashSet<>();for(String p:parts){String w=stem(p);if(w.length()<4||STOP.contains(w))continue;out.add(w);}return out;}
    private static String stem(String w){String x=n(w).toLowerCase(Locale.ROOT);if(x.length()>7&&x.endsWith("ing"))x=x.substring(0,x.length()-3);else if(x.length()>6&&x.endsWith("ed"))x=x.substring(0,x.length()-2);else if(x.length()>5&&x.endsWith("s"))x=x.substring(0,x.length()-1);return x;}
    private static boolean table(SQLiteDatabase db,String name){Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{name});boolean yes=c.moveToFirst();c.close();return yes;}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("that","this","with","from","were","was","are","the","and","for","your","user","some","have","has","into","than","then","been","being","received","indicating","recommending","about","after","before","على","الى","إلى","هذا","هذه","التي","الذي","كان","كانت","تم","من","في"));
    private static String n(String s){return s==null?"":s.trim();}
}
