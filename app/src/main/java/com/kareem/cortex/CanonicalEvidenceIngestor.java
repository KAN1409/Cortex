package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/**
 * Canonical ingress boundary for captured visual/document evidence.
 * Captured text becomes immutable evidence + a semantic event, never an action merely because OCR
 * contains imperative-looking words. Attention requires a later grounded situation and final judge.
 */
public final class CanonicalEvidenceIngestor {
    public static final String VERSION="canonical_evidence_ingestor_001";
    private CanonicalEvidenceIngestor(){}

    public static final class Result {
        public final long rawObservationId,streamId,semanticEventId;
        Result(long raw,long stream,long semantic){rawObservationId=raw;streamId=stream;semanticEventId=semantic;}
    }

    public static Result ingestVisual(VaultDb vault,String sourceKey,String observationKey,String title,String body,long occurredAt,JSONObject metadata){
        if(vault==null)throw new IllegalArgumentException("vault == null");
        SQLiteDatabase db=vault.getWritableDatabase();UniversalEventStore.ensure(db);
        String source=n(sourceKey);if(source.isEmpty())source="visual";
        String observation=n(observationKey);if(observation.isEmpty())observation=Fingerprint.text(source+"|"+n(title)+"|"+n(body));
        JSONObject meta=metadata==null?new JSONObject():metadata;
        try{meta.put("canonical_ingress",VERSION);meta.put("attention_authority","none_at_evidence_stage");}catch(Exception ignored){}

        long existingRaw=findRaw(db,"visual_evidence",source,observation);
        if(existingRaw>0){long existingSemantic=findSemantic(db,existingRaw);long existingStream=findStream(db,existingSemantic);return new Result(existingRaw,existingStream,existingSemantic);}

        long when=occurredAt>0?occurredAt:System.currentTimeMillis();
        long raw=UniversalEventStore.appendRaw(db,"visual_evidence",source,observation,"captured_artifact","visual_memory","ocr_evidence",title,body,meta,when);
        UniversalEventStore.stage(db,raw,0,"CAPTURED","complete","canonical_evidence_ingestor","Immutable captured evidence stored","");

        String external=source+"|"+observation;String hash=Fingerprint.text(n(title)+"\n"+n(body)+"\n"+meta.toString());
        long stream=UniversalEventStore.upsertStream(db,"visual_evidence",external,"active",hash,title,body,"visual_memory","ocr_evidence",when,true,meta);
        UniversalEventStore.stage(db,raw,0,"NORMALIZED","complete","canonical_evidence_ingestor","Visual evidence attached to canonical stream","");

        String summary=clip(n(body).isEmpty()?n(title):n(body),480);
        long semantic=UniversalEventStore.insertSemantic(db,raw,stream,1,"captured_artifact","evidence",n(title),summary,.92,"complete",true,"deterministic","visual/OCR content is evidence; no obligation inferred at capture",when);
        UniversalEventStore.stage(db,raw,semantic,"UNDERSTANDING","complete","canonical_evidence_ingestor","Captured artifact represented semantically without inventing an action","");
        UniversalEventStore.stage(db,raw,semantic,"ATTENTION_BOUNDARY","complete","evidence_boundary","Evidence is not attention-authoritative; situation correlation and CortexAttentionJudge are required","");
        UniversalEventStore.stage(db,raw,semantic,"COMPLETE","complete","canonical_evidence_ingestor","Canonical evidence ingress complete","");
        return new Result(raw,stream,semantic);
    }

    private static long findRaw(SQLiteDatabase db,String sourceType,String sourceKey,String observationKey){
        Cursor c=db.rawQuery("SELECT id FROM ue_raw_observations WHERE source_type=? AND source_key=? AND source_observation_key=? ORDER BY id DESC LIMIT 1",new String[]{sourceType,sourceKey,observationKey});
        try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}
    }
    private static long findSemantic(SQLiteDatabase db,long raw){Cursor c=db.rawQuery("SELECT id FROM ue_semantic_events WHERE raw_observation_id=? AND superseded_by=0 ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(raw)});try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static long findStream(SQLiteDatabase db,long semantic){if(semantic<=0)return 0;Cursor c=db.rawQuery("SELECT stream_id FROM ue_semantic_events WHERE id=? LIMIT 1",new String[]{String.valueOf(semantic)});try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static String clip(String s,int max){String x=n(s);return x.length()<=max?x:x.substring(0,max)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
}
