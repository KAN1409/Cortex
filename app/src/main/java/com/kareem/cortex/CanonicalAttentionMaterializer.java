package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Materializes FINAL CortexAttentionJudge outcomes into the canonical attention ledger. */
public final class CanonicalAttentionMaterializer {
    public static final String VERSION="canonical_attention_materializer_002";
    private CanonicalAttentionMaterializer(){}

    public static final class Result{
        public final Set<Long> selectedSituations;
        public final int candidates,selected;
        Result(Set<Long>s,int c,int n){selectedSituations=s;candidates=c;selected=n;}
    }

    public static Result run(Context context,VaultDb vault){
        Set<Long> selected=new HashSet<>();if(context==null||vault==null)return new Result(selected,0,0);
        Context app=context.getApplicationContext();CognitiveStore.ensure(vault);SQLiteDatabase db=vault.getWritableDatabase();UniversalEventStore.ensure(db);CortexJudgmentTraceStore.ensure(db);CortexV91Authority.enforceLive(db);
        List<AttentionDecisionEngine.Candidate> candidates;
        try{candidates=CognitiveShadowStore.loadCandidates(db,System.currentTimeMillis());}catch(Throwable t){candidates=new ArrayList<>();}
        CortexAttentionJudge.RuntimeContext rt=CortexAttentionJudge.RuntimeContext.neutral();
        List<CortexAttentionJudge.Judgment> ranked=CortexAttentionJudge.rankForNow(app,candidates,rt,CortexPersonalPolicy.maxNowItems(app));
        for(CortexAttentionJudge.Judgment j:ranked)if(j!=null&&j.candidate!=null&&j.surfaceNow)selected.add(j.candidate.situationId);

        Map<Long,String> finalState=new HashMap<>();Set<Long> actuallySelected=new HashSet<>();
        for(AttentionDecisionEngine.Candidate c:candidates){
            if(c==null)continue;
            CortexAttentionJudge.Judgment j=CortexAttentionJudge.evaluate(app,c,rt);
            boolean wantsNow=selected.contains(c.situationId)&&j.surfaceNow;
            EventRef ref=wantsNow?latestGroundedEvent(db,c.situationId):new EventRef(0,"");
            boolean selectedNow=wantsNow&&ref.eventId>0;
            String decision=CortexJudgmentTraceStore.finalDecision(j,selectedNow);
            CortexJudgmentTraceStore.record(db,j,selectedNow);
            finalState.put(c.situationId,decision);
            if(selectedNow){actuallySelected.add(c.situationId);materializeNow(db,c,j,ref);}
        }
        reconcileExisting(db,actuallySelected,finalState);
        CortexV91Authority.enforceLive(db);
        CortexJudgmentTraceStore.prune(db);
        return new Result(actuallySelected,candidates.size(),actuallySelected.size());
    }

    private static void materializeNow(SQLiteDatabase db,AttentionDecisionEngine.Candidate c,CortexAttentionJudge.Judgment j,EventRef ref){
        if(ref.eventId<=0)return;String kind=kind(c);String title=CanonicalPresentation.cleanTitle("notification",c.type,c.subject,c.subject);String body=CanonicalPresentation.cleanBody(c.summary);
        long id=UniversalEventStore.attention(db,ref.eventId,c.situationId,kind,title,body,Math.max(1,Math.min(100,(int)Math.round(j.score*100.0))),c.confidence,ref.sourceKey,"FINAL_JUDGE: "+j.reason);
        if(id<=0)return;long now=System.currentTimeMillis();ContentValues u=new ContentValues();u.put("state","open");u.put("resolved_at",0);u.put("reason","FINAL_JUDGE: "+j.reason);u.put("updated_at",now);db.update("ue_attention_items",u,"id=?",new String[]{String.valueOf(id)});
        ContentValues d=new ContentValues();d.put("state","open");d.put("resolved_at",0);d.put("updated_at",now);db.update("derived_items",d,"id=? AND candidate_kind='UE_ATTENTION'",new String[]{String.valueOf(UniversalEventStore.ATTENTION_COMPAT_OFFSET+id)});
    }

    private static void reconcileExisting(SQLiteDatabase db,Set<Long> selected,Map<Long,String> finalState){
        Cursor c=db.rawQuery("SELECT id,situation_id,state FROM ue_attention_items WHERE state IN ('open','deferred','silent','suppressed')",null);long now=System.currentTimeMillis();
        try{while(c.moveToNext()){
            long id=c.getLong(0),situation=c.getLong(1);if(selected.contains(situation))continue;
            String decision=finalState.get(situation);if(decision==null)decision="SUPPRESS";
            String state="LATER".equals(decision)?"deferred":("SILENT".equals(decision)?"silent":"suppressed");
            ContentValues v=new ContentValues();v.put("state",state);v.put("resolved_at","deferred".equals(state)?0:now);v.put("updated_at",now);db.update("ue_attention_items",v,"id=?",new String[]{String.valueOf(id)});
            ContentValues d=new ContentValues();d.put("state",state);d.put("resolved_at","deferred".equals(state)?0:now);d.put("updated_at",now);db.update("derived_items",d,"id=? AND candidate_kind='UE_ATTENTION'",new String[]{String.valueOf(UniversalEventStore.ATTENTION_COMPAT_OFFSET+id)});
        }}finally{c.close();}
    }

    /** Picks the newest semantic member that is independently allowed to establish attention. */
    private static EventRef latestGroundedEvent(SQLiteDatabase db,long situationId){
        String sql="SELECT e.id,COALESCE(r.source_key,''),COALESCE(r.source_type,''),COALESCE(r.event_type,''),COALESCE(r.technical_type,''),COALESCE(e.semantic_type,''),COALESCE(e.intent,''),COALESCE(e.subject,''),COALESCE(e.summary,'') " +
                "FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "WHERE m.situation_id=? AND e.semantic_state='complete' AND e.superseded_by=0 ORDER BY e.occurred_at DESC,e.id DESC LIMIT 24";
        Cursor c=db.rawQuery(sql,new String[]{String.valueOf(situationId)});
        try{while(c.moveToNext()){
            CortexProvenanceGate.Result p=CortexProvenanceGate.evaluate(c.getString(2),c.getString(1),c.getString(3),c.getString(4),c.getString(7),c.getString(8),c.getString(5),c.getString(6));
            if(p.attentionEligible)return new EventRef(c.getLong(0),c.getString(1));
        }}finally{c.close();}
        return new EventRef(0,"");
    }

    private static String kind(AttentionDecisionEngine.Candidate c){
        String type=n(c.type).toLowerCase(Locale.ROOT),state=n(c.state).toLowerCase(Locale.ROOT);
        if(type.contains("decision")||type.contains("approval")||type.contains("choice"))return"DECISION";
        if(c.linkedOpenCommitment||type.contains("commitment")||type.contains("waiting")||type.contains("follow_up")||type.contains("pending_response")||state.contains("waiting"))return"WAITING";
        return"ACTION";
    }
    private static String n(String s){return s==null?"":s.trim();}
    private static final class EventRef{final long eventId;final String sourceKey;EventRef(long i,String s){eventId=i;sourceKey=n(s);}}
}
