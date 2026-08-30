package com.kareem.cortex.rebuild;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** User-controlled lifecycle and provenance operations for Cortex Situations. */
public final class SituationActions {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>()\\[\\]{}]+", Pattern.CASE_INSENSITIVE);
    private SituationActions() {}

    public static void ensure(CortexDb vault) {
        SQLiteDatabase db = vault.getWritableDatabase();
        db.execSQL("CREATE TABLE IF NOT EXISTS situation_snooze(situation_id INTEGER PRIMARY KEY,snoozed_until INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS situation_feedback(situation_id INTEGER PRIMARY KEY,feedback TEXT NOT NULL,updated_at INTEGER NOT NULL)");
    }

    public static List<CortexDb.Row> activeSituations(CortexDb vault, int limit) {
        ensure(vault);
        ArrayList<CortexDb.Row> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        Cursor c = vault.getReadableDatabase().rawQuery(
                "SELECT s.id,s.title,s.summary,s.updated_at,s.attention FROM situations s " +
                "LEFT JOIN situation_snooze z ON z.situation_id=s.id " +
                "WHERE s.state='active' AND (z.snoozed_until IS NULL OR z.snoozed_until<=?) " +
                "ORDER BY CASE s.attention WHEN 'needs_attention' THEN 0 WHEN 'watching' THEN 1 ELSE 2 END,s.updated_at DESC LIMIT ?",
                new String[]{String.valueOf(now), String.valueOf(Math.max(1, limit))});
        try { while (c.moveToNext()) out.add(new CortexDb.Row(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4))); }
        finally { c.close(); }
        return out;
    }

    public static void done(CortexDb vault,long situationId){setState(vault,situationId,"resolved");}
    public static void dismiss(CortexDb vault,long situationId){setState(vault,situationId,"dismissed");}
    public static void deleteSituation(CortexDb vault,long situationId){setState(vault,situationId,"deleted");}
    private static void setState(CortexDb vault,long id,String state){ContentValues v=new ContentValues();v.put("state",state);v.put("updated_at",System.currentTimeMillis());vault.getWritableDatabase().update("situations",v,"id=?",new String[]{String.valueOf(id)});}

    public static void snooze(CortexDb vault,long situationId,long until){ensure(vault);ContentValues v=new ContentValues();v.put("situation_id",situationId);v.put("snoozed_until",until);v.put("updated_at",System.currentTimeMillis());vault.getWritableDatabase().insertWithOnConflict("situation_snooze",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public static void edit(CortexDb vault,long situationId,String title,String summary){ContentValues v=new ContentValues();if(!clean(title).isEmpty())v.put("title",clean(title));v.put("summary",clean(summary));v.put("updated_at",System.currentTimeMillis());vault.getWritableDatabase().update("situations",v,"id=?",new String[]{String.valueOf(situationId)});}
    public static void priority(CortexDb vault,long situationId,String attention){String a=clean(attention).toLowerCase(Locale.ROOT);if(!a.equals("needs_attention")&&!a.equals("watching"))a="quiet";ContentValues v=new ContentValues();v.put("attention",a);v.put("updated_at",System.currentTimeMillis());vault.getWritableDatabase().update("situations",v,"id=?",new String[]{String.valueOf(situationId)});}
    public static void markMisunderstood(CortexDb vault,long situationId){ensure(vault);ContentValues v=new ContentValues();v.put("situation_id",situationId);v.put("feedback","misunderstood");v.put("updated_at",System.currentTimeMillis());vault.getWritableDatabase().insertWithOnConflict("situation_feedback",null,v,SQLiteDatabase.CONFLICT_REPLACE);}

    public static Source source(CortexDb vault,long situationId){
        ensure(vault);
        Cursor c=vault.getReadableDatabase().rawQuery(
                "SELECT e.id,e.kind,e.body,e.source,e.source_package,e.protocol,e.occurred_at,e.payload_json,e.attachment_path,e.mime_type,e.display_name,COALESCE(v.transcript,'') " +
                "FROM situation_evidence se JOIN evidence e ON e.id=se.evidence_id LEFT JOIN voice_transcripts v ON v.evidence_id=e.id " +
                "WHERE se.situation_id=? ORDER BY e.occurred_at DESC,e.id DESC LIMIT 1",new String[]{String.valueOf(situationId)});
        try{if(!c.moveToFirst())return null;String body=c.getString(2),transcript=c.getString(11);String url=findUrl(!transcript.isEmpty()?transcript:body);if(url.isEmpty())url=findUrl(c.getString(7));return new Source(c.getLong(0),c.getString(1),body,c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),transcript,url);}finally{c.close();}
    }

    public static List<CortexDb.Row> mergeCandidates(CortexDb vault,long excludeId,int limit){ArrayList<CortexDb.Row> out=new ArrayList<>();for(CortexDb.Row r:activeSituations(vault,limit+1))if(r.id!=excludeId)out.add(r);return out;}
    public static void mergeInto(CortexDb vault,long fromId,long toId){if(fromId==toId)return;SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();try{Cursor c=db.rawQuery("SELECT evidence_id,relation FROM situation_evidence WHERE situation_id=?",new String[]{String.valueOf(fromId)});try{while(c.moveToNext()){ContentValues v=new ContentValues();v.put("situation_id",toId);v.put("evidence_id",c.getLong(0));v.put("relation",c.getString(1));db.insertWithOnConflict("situation_evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);}}finally{c.close();}ContentValues s=new ContentValues();s.put("state","merged");s.put("updated_at",System.currentTimeMillis());db.update("situations",s,"id=?",new String[]{String.valueOf(fromId)});db.setTransactionSuccessful();}finally{db.endTransaction();}}

    public static boolean keepInMemory(CortexDb vault,long situationId){Source s=source(vault,situationId);if(s==null)return false;SQLiteDatabase db=vault.getWritableDatabase();Cursor c=db.rawQuery("SELECT 1 FROM memories WHERE evidence_id=? LIMIT 1",new String[]{String.valueOf(s.evidenceId)});try{if(c.moveToFirst())return true;}finally{c.close();}Cursor sc=db.rawQuery("SELECT title,summary FROM situations WHERE id=? LIMIT 1",new String[]{String.valueOf(situationId)});try{if(!sc.moveToFirst())return false;ContentValues m=new ContentValues();m.put("evidence_id",s.evidenceId);m.put("title",sc.getString(0));m.put("body",sc.getString(1));m.put("created_at",System.currentTimeMillis());return db.insert("memories",null,m)>0;}finally{sc.close();}}

    /** Manual correction is authoritative evidence: old derived cognition is detached and rerun. */
    public static boolean editTranscriptAndReset(CortexDb vault,long evidenceId,String corrected){
        String text=clean(corrected);if(text.isEmpty())return false;SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();try{
            ContentValues t=new ContentValues();t.put("transcript",text);t.put("engine","manual-correction");t.put("version","user-edit-v1");int n=db.update("voice_transcripts",t,"evidence_id=?",new String[]{String.valueOf(evidenceId)});if(n==0)return false;
            Cursor q=db.rawQuery("SELECT quality_json FROM evidence WHERE id=? LIMIT 1",new String[]{String.valueOf(evidenceId)});String quality="{}";try{if(q.moveToFirst())quality=q.getString(0);}finally{q.close();}try{JSONObject j=new JSONObject(quality==null?"{}":quality);j.put("manual_transcript_edit",true);j.put("manual_transcript_edit_at",System.currentTimeMillis());quality=j.toString();}catch(Exception ignored){quality="{\"manual_transcript_edit\":true}";}
            ContentValues e=new ContentValues();e.put("body",text);e.put("state","transcribed");e.put("quality_json",quality);db.update("evidence",e,"id=?",new String[]{String.valueOf(evidenceId)});
            resetDerived(db,evidenceId);
            db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }

    public static boolean resetForReprocess(CortexDb vault,long evidenceId){SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();try{Cursor c=db.rawQuery("SELECT 1 FROM voice_transcripts WHERE evidence_id=? LIMIT 1",new String[]{String.valueOf(evidenceId)});boolean ok;try{ok=c.moveToFirst();}finally{c.close();}if(!ok)return false;resetDerived(db,evidenceId);ContentValues e=new ContentValues();e.put("state","transcribed");db.update("evidence",e,"id=?",new String[]{String.valueOf(evidenceId)});db.setTransactionSuccessful();return true;}finally{db.endTransaction();}}

    private static void resetDerived(SQLiteDatabase db,long evidenceId){
        ArrayList<Long> situations=new ArrayList<>();Cursor c=db.rawQuery("SELECT situation_id FROM situation_evidence WHERE evidence_id=?",new String[]{String.valueOf(evidenceId)});try{while(c.moveToNext())situations.add(c.getLong(0));}finally{c.close();}
        db.delete("situation_evidence","evidence_id=?",new String[]{String.valueOf(evidenceId)});
        for(Long id:situations){Cursor x=db.rawQuery("SELECT 1 FROM situation_evidence WHERE situation_id=? LIMIT 1",new String[]{String.valueOf(id)});boolean any;try{any=x.moveToFirst();}finally{x.close();}if(!any){ContentValues s=new ContentValues();s.put("state","superseded");s.put("updated_at",System.currentTimeMillis());db.update("situations",s,"id=?",new String[]{String.valueOf(id)});}}
        db.delete("world_entity_evidence","evidence_id=?",new String[]{String.valueOf(evidenceId)});
        db.delete("memories","evidence_id=?",new String[]{String.valueOf(evidenceId)});
        db.delete("product_feedback","evidence_id=?",new String[]{String.valueOf(evidenceId)});
        db.delete("brain_evidence_policy","evidence_id=?",new String[]{String.valueOf(evidenceId)});
        db.delete("brain_runs","evidence_id=?",new String[]{String.valueOf(evidenceId)});
    }

    public static void replaceEvidenceSituations(CortexDb vault,long evidenceId,List<SituationDecomposer.Spec> specs){if(specs==null||specs.isEmpty())return;SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();try{
        ArrayList<Long> old=new ArrayList<>();Cursor c=db.rawQuery("SELECT situation_id FROM situation_evidence WHERE evidence_id=?",new String[]{String.valueOf(evidenceId)});try{while(c.moveToNext())old.add(c.getLong(0));}finally{c.close();}db.delete("situation_evidence","evidence_id=?",new String[]{String.valueOf(evidenceId)});
        for(Long id:old){Cursor x=db.rawQuery("SELECT 1 FROM situation_evidence WHERE situation_id=? LIMIT 1",new String[]{String.valueOf(id)});boolean any;try{any=x.moveToFirst();}finally{x.close();}if(!any){ContentValues v=new ContentValues();v.put("state","superseded");v.put("updated_at",System.currentTimeMillis());db.update("situations",v,"id=?",new String[]{String.valueOf(id)});}}
        for(SituationDecomposer.Spec spec:specs)upsert(db,evidenceId,spec);
        db.setTransactionSuccessful();
    }finally{db.endTransaction();}}

    private static long upsert(SQLiteDatabase db,long evidenceId,SituationDecomposer.Spec spec){String key=canonical(spec.canonicalKey.isEmpty()?spec.title:spec.canonicalKey);long id=0;Cursor c=db.rawQuery("SELECT situation_id FROM brain_situation_keys WHERE canonical_key=? LIMIT 1",new String[]{key});try{if(c.moveToFirst())id=c.getLong(0);}finally{c.close();}if(id==0){String norm=canonical(spec.title);c=db.rawQuery("SELECT id FROM situations WHERE state='active' AND lower(replace(replace(title,' ', '_'),'-','_'))=? LIMIT 1",new String[]{norm});try{if(c.moveToFirst())id=c.getLong(0);}finally{c.close();}}
        ContentValues v=new ContentValues();v.put("title",spec.title);v.put("summary",spec.summary);v.put("state","active");v.put("attention",spec.attention);v.put("updated_at",System.currentTimeMillis());if(id>0){db.update("situations",v,"id=?",new String[]{String.valueOf(id)});}else{id=db.insertOrThrow("situations",null,v);}ContentValues k=new ContentValues();k.put("canonical_key",key);k.put("situation_id",id);db.insertWithOnConflict("brain_situation_keys",null,k,SQLiteDatabase.CONFLICT_REPLACE);ContentValues l=new ContentValues();l.put("situation_id",id);l.put("evidence_id",evidenceId);l.put("relation","supports");db.insertWithOnConflict("situation_evidence",null,l,SQLiteDatabase.CONFLICT_IGNORE);return id;}

    public static boolean deleteEvidence(CortexDb vault,long evidenceId){SQLiteDatabase db=vault.getWritableDatabase();String path="";Cursor p=db.rawQuery("SELECT attachment_path FROM evidence WHERE id=? LIMIT 1",new String[]{String.valueOf(evidenceId)});try{if(p.moveToFirst())path=p.getString(0);}finally{p.close();}db.beginTransaction();try{resetDerived(db,evidenceId);db.delete("voice_segments","evidence_id=?",new String[]{String.valueOf(evidenceId)});db.delete("voice_transcripts","evidence_id=?",new String[]{String.valueOf(evidenceId)});db.delete("ingest_receipts","evidence_id=?",new String[]{String.valueOf(evidenceId)});int n=db.delete("evidence","id=?",new String[]{String.valueOf(evidenceId)});db.setTransactionSuccessful();if(n>0&&!clean(path).isEmpty())try{new File(path).delete();}catch(Throwable ignored){}return n>0;}finally{db.endTransaction();}}

    public static String findUrl(String text){Matcher m=URL.matcher(text==null?"":text);if(!m.find())return"";String u=m.group();while(u.endsWith(".")||u.endsWith(",")||u.endsWith(")")||u.endsWith("]"))u=u.substring(0,u.length()-1);return u;}
    private static String canonical(String s){String x=clean(s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{L}]+","_").replaceAll("^_+|_+$","");return x.isEmpty()?"situation":x;}
    private static String clean(String s){return s==null?"":s.trim();}

    public static final class Source{
        public final long evidenceId,occurredAt;public final String kind,body,source,sourcePackage,protocol,payloadJson,path,mimeType,displayName,transcript,url;
        Source(long evidenceId,String kind,String body,String source,String sourcePackage,String protocol,long occurredAt,String payloadJson,String path,String mimeType,String displayName,String transcript,String url){this.evidenceId=evidenceId;this.kind=clean(kind);this.body=clean(body);this.source=clean(source);this.sourcePackage=clean(sourcePackage);this.protocol=clean(protocol);this.occurredAt=occurredAt;this.payloadJson=payloadJson==null?"{}":payloadJson;this.path=clean(path);this.mimeType=clean(mimeType);this.displayName=clean(displayName);this.transcript=clean(transcript);this.url=clean(url);}public boolean isVoice(){return kind.startsWith("AUDIO")||mimeType.startsWith("audio/");}public String displayText(){return !transcript.isEmpty()?transcript:body;}}
}
