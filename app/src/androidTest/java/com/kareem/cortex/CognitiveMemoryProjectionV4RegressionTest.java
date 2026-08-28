package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveMemoryProjectionV4RegressionTest {

    @Test public void lexicalSearchFindsOcrAnalysisWithoutInventingMemoryText() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            CognitiveSchemaV4.ensure(db);long now=System.currentTimeMillis();
            seed(db,"ev_camera","mem_camera","IMAGE","com.gallery","","Sony Alpha A7 IV £2,199",now,now+86_400_000L,false);
            List<CognitiveMemoryProjectionV4.Row> hits=CognitiveMemoryProjectionV4.search(db,new CognitiveMemoryProjectionV4.Query().text("Alpha A7 IV"));
            assertEquals(1,hits.size());assertEquals("mem_camera",hits.get(0).id);assertEquals(1,hits.get(0).evidenceCount);
            List<CognitiveMemoryProjectionV4.EvidenceRow> evidence=CognitiveMemoryProjectionV4.evidence(db,"mem_camera");
            assertEquals(1,evidence.size());assertEquals("",evidence.get(0).originalText);assertEquals(1,evidence.get(0).analyses.size());assertEquals("OCR",evidence.get(0).analyses.get(0).kind);
        }finally{db.close();}
    }

    @Test public void sourceAndKindFiltersRemainHardConstraints() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            CognitiveSchemaV4.ensure(db);long now=System.currentTimeMillis();
            seed(db,"ev_a","mem_a","IMAGE","com.amazon","camera","Sony Alpha A7 IV",now,now+86_400_000L,false);
            assertEquals(1,CognitiveMemoryProjectionV4.search(db,new CognitiveMemoryProjectionV4.Query().text("camera").source("com.amazon").kind(CognitiveDomainV4.MemoryKind.IMAGE)).size());
            assertEquals(0,CognitiveMemoryProjectionV4.search(db,new CognitiveMemoryProjectionV4.Query().text("camera").source("com.instagram.android")).size());
            assertEquals(0,CognitiveMemoryProjectionV4.search(db,new CognitiveMemoryProjectionV4.Query().text("camera").kind(CognitiveDomainV4.MemoryKind.VOICE)).size());
        }finally{db.close();}
    }

    @Test public void searchTreatsPercentAndUnderscoreAsLiteralText() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            CognitiveSchemaV4.ensure(db);long now=System.currentTimeMillis();
            seed(db,"ev_literal","mem_literal","NOTE","notes","normal text","",now,now+86_400_000L,false);
            assertEquals(0,CognitiveMemoryProjectionV4.search(db,new CognitiveMemoryProjectionV4.Query().text("%" )).size());
            assertEquals(0,CognitiveMemoryProjectionV4.search(db,new CognitiveMemoryProjectionV4.Query().text("_" )).size());
        }finally{db.close();}
    }

    @Test public void expiredMemoryIsHiddenButPinnedMemorySurvives() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            CognitiveSchemaV4.ensure(db);long now=System.currentTimeMillis();
            seed(db,"ev_old","mem_old","NOTE","notes","old unpinned","",now-1000,now-1,false);
            seed(db,"ev_pin","mem_pin","NOTE","notes","old pinned","",now-900,now-1,true);
            List<CognitiveMemoryProjectionV4.Row> hits=CognitiveMemoryProjectionV4.search(db,new CognitiveMemoryProjectionV4.Query());
            assertEquals(1,hits.size());assertEquals("mem_pin",hits.get(0).id);assertTrue(hits.get(0).pinned);
        }finally{db.close();}
    }

    @Test public void legacyTypeMappingKeepsSourceSemantics() {
        assertEquals(CognitiveDomainV4.EvidenceSourceType.IMAGE,CognitiveMemoryBackfillV4.sourceTypeForKnowledge("SCREENSHOT"));
        assertEquals(CognitiveDomainV4.MemoryKind.VOICE,CognitiveMemoryBackfillV4.memoryKind("VOICE_NOTE"));
        assertEquals(CognitiveDomainV4.EpisodeKind.CONVERSATION,CognitiveMemoryBackfillV4.episodeKind("communication"));
        assertEquals(CognitiveDomainV4.EvidenceSourceType.SCREEN,CognitiveMemoryBackfillV4.sourceTypeForRaw("screen_context"));
    }

    private static void seed(SQLiteDatabase db,String evidenceId,String memoryId,String kind,String source,String original,String analysis,long startedAt,long expiresAt,boolean pinned){
        long now=System.currentTimeMillis();ContentValues e=new ContentValues();e.put("id",evidenceId);e.put("identity_key","identity_"+evidenceId);e.put("source_type","IMAGE".equals(kind)?"IMAGE":"NOTE");e.put("source_package",source);e.put("occurred_at",startedAt);e.put("captured_at",startedAt);e.put("original_text",original);e.put("normalized_text",CognitiveIdentityV4.normalizeText(original));e.put("content_hash",Fingerprint.text(original+analysis));e.put("sensitivity","NORMAL");e.put("retention_class",pinned?"PINNED":"EPISODIC_90_DAY");e.put("expires_at",expiresAt);e.put("processing_state","READY");e.put("created_at",now);e.put("updated_at",now);assertTrue(db.insert("v4_evidence",null,e)>0);
        if(analysis!=null&&!analysis.isEmpty()){ContentValues a=new ContentValues();a.put("id","an_"+evidenceId);a.put("evidence_id",evidenceId);a.put("analysis_kind","OCR");a.put("engine","test");a.put("version","1");a.put("output_text",analysis);a.put("content_hash",Fingerprint.text(analysis));a.put("created_at",now);assertTrue(db.insert("v4_evidence_analysis",null,a)>0);}
        ContentValues m=new ContentValues();m.put("id",memoryId);m.put("identity_key","identity_"+memoryId);m.put("kind",kind);m.put("title","");m.put("body",original);m.put("source_package",source);m.put("started_at",startedAt);m.put("ended_at",0);m.put("importance",0.5);m.put("pinned",pinned?1:0);m.put("retention_class",pinned?"PINNED":"EPISODIC_90_DAY");m.put("expires_at",expiresAt);m.put("state","ACTIVE");m.put("created_at",now);m.put("updated_at",now);assertTrue(db.insert("v4_memories",null,m)>0);
        ContentValues link=new ContentValues();link.put("memory_id",memoryId);link.put("evidence_id",evidenceId);link.put("role","supports");link.put("ordinal",0);link.put("created_at",now);assertTrue(db.insert("v4_memory_evidence",null,link)>0);
    }
}
