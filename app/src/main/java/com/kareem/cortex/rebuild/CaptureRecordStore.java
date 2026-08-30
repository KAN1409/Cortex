package com.kareem.cortex.rebuild;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Read/write helpers for capture history without promoting raw evidence into Memory. */
public final class CaptureRecordStore {
    private CaptureRecordStore(){}

    public static Record get(CortexDb vault,long id){Cursor c=vault.getReadableDatabase().rawQuery("SELECT id,kind,body,state,attachment_path,mime_type,display_name,payload_json,quality_json,occurred_at,created_at FROM evidence WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});try{return c.moveToFirst()?record(c):null;}finally{c.close();}}
    public static List<Record> recent(CortexDb vault,int limit){ArrayList<Record> out=new ArrayList<>();Cursor c=vault.getReadableDatabase().rawQuery("SELECT id,kind,body,state,attachment_path,mime_type,display_name,payload_json,quality_json,occurred_at,created_at FROM evidence WHERE source='user' ORDER BY id DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});try{while(c.moveToNext())out.add(record(c));}finally{c.close();}return out;}
    private static Record record(Cursor c){return new Record(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getLong(9),c.getLong(10));}

    public static void markVisionRunning(CortexDb vault,long id){ContentValues v=new ContentValues();v.put("state","vision_analyzing");vault.getWritableDatabase().update("evidence",v,"id=?",new String[]{String.valueOf(id)});}
    public static void markVisionFailed(CortexDb vault,long id,Exception e){ContentValues v=new ContentValues();v.put("state","vision_failed");try{Record r=get(vault,id);JSONObject q=json(r==null?"{}":r.qualityJson);q.put("vision_error",clip(e==null?"Vision failed":e.getMessage(),320));q.put("vision_failed_at",System.currentTimeMillis());v.put("quality_json",q.toString());}catch(Exception ignored){}vault.getWritableDatabase().update("evidence",v,"id=?",new String[]{String.valueOf(id)});}

    public static void saveVision(CortexDb vault,long id,ImageVisionEngine.Result result)throws Exception{
        Record r=get(vault,id);if(r==null)throw new IllegalArgumentException("Evidence not found");
        JSONObject payload=json(r.payloadJson);payload.put("vision",result.toJson());
        JSONObject quality=json(r.qualityJson);quality.put("vision_grounded",true);quality.put("vision_provider",result.provider);quality.put("vision_model",result.model);quality.remove("vision_error");
        ContentValues v=new ContentValues();v.put("body",result.brainText());v.put("payload_json",payload.toString());v.put("quality_json",quality.toString());v.put("state","vision_analyzed");vault.getWritableDatabase().update("evidence",v,"id=?",new String[]{String.valueOf(id)});
    }

    public static boolean editExtractedText(CortexDb vault,long id,String corrected)throws Exception{
        String clean=corrected==null?"":corrected.trim();Record r=get(vault,id);if(r==null)return false;JSONObject payload=json(r.payloadJson);JSONObject vision=payload.optJSONObject("vision");if(vision==null)vision=new JSONObject();vision.put("extracted_text",clean);vision.put("manual_text_edit",true);vision.put("manual_text_edit_at",System.currentTimeMillis());payload.put("vision",vision);
        JSONObject quality=json(r.qualityJson);quality.put("manual_vision_text_edit",true);quality.put("manual_vision_text_edit_at",System.currentTimeMillis());
        String summary=vision.optString("summary","");String body=brainText(summary,clean,vision);
        retireDerived(vault,id);
        ContentValues v=new ContentValues();v.put("payload_json",payload.toString());v.put("quality_json",quality.toString());v.put("body",body);v.put("state","vision_analyzed");vault.getWritableDatabase().update("evidence",v,"id=?",new String[]{String.valueOf(id)});return true;
    }

    public static void retireDerived(CortexDb vault,long evidenceId){SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();try{
        ArrayList<Long> situations=new ArrayList<>();Cursor c=db.rawQuery("SELECT situation_id FROM situation_evidence WHERE evidence_id=?",new String[]{String.valueOf(evidenceId)});try{while(c.moveToNext())situations.add(c.getLong(0));}finally{c.close();}
        db.delete("situation_evidence","evidence_id=?",new String[]{String.valueOf(evidenceId)});
        for(Long id:situations){Cursor x=db.rawQuery("SELECT 1 FROM situation_evidence WHERE situation_id=? LIMIT 1",new String[]{String.valueOf(id)});boolean any;try{any=x.moveToFirst();}finally{x.close();}if(!any){ContentValues s=new ContentValues();s.put("state","superseded");s.put("updated_at",System.currentTimeMillis());db.update("situations",s,"id=?",new String[]{String.valueOf(id)});}}
        db.delete("world_entity_evidence","evidence_id=?",new String[]{String.valueOf(evidenceId)});db.delete("memories","evidence_id=?",new String[]{String.valueOf(evidenceId)});db.delete("product_feedback","evidence_id=?",new String[]{String.valueOf(evidenceId)});db.delete("brain_evidence_policy","evidence_id=?",new String[]{String.valueOf(evidenceId)});db.delete("brain_runs","evidence_id=?",new String[]{String.valueOf(evidenceId)});db.setTransactionSuccessful();
    }finally{db.endTransaction();}}

    public static boolean deleteCapture(CortexDb vault,long id){Record r=get(vault,id);if(r==null)return false;retireDerived(vault,id);SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();int n;try{db.delete("voice_segments","evidence_id=?",new String[]{String.valueOf(id)});db.delete("voice_transcripts","evidence_id=?",new String[]{String.valueOf(id)});db.delete("ingest_receipts","evidence_id=?",new String[]{String.valueOf(id)});n=db.delete("evidence","id=?",new String[]{String.valueOf(id)});db.setTransactionSuccessful();}finally{db.endTransaction();}if(n>0&&!r.path.isEmpty())try{new File(r.path).delete();}catch(Throwable ignored){}return n>0;}

    public static String visionSummary(Record r){try{JSONObject v=json(r.payloadJson).optJSONObject("vision");return v==null?"":v.optString("summary","");}catch(Exception e){return"";}}
    public static String extractedText(Record r){try{JSONObject v=json(r.payloadJson).optJSONObject("vision");return v==null?"":v.optString("extracted_text","");}catch(Exception e){return"";}}
    public static boolean hasVision(Record r){try{return json(r.payloadJson).optJSONObject("vision")!=null;}catch(Exception e){return false;}}

    private static String brainText(String summary,String extracted,JSONObject vision){StringBuilder b=new StringBuilder("IMAGE EVIDENCE");if(summary!=null&&!summary.trim().isEmpty())b.append("\nVision summary: ").append(summary.trim());if(extracted!=null&&!extracted.trim().isEmpty())b.append("\nVisible text (OCR): ").append(extracted.trim());if(vision!=null){if(vision.optJSONArray("visible_entities")!=null)b.append("\nVisible entities/objects: ").append(vision.optJSONArray("visible_entities"));if(vision.optJSONArray("urls")!=null)b.append("\nVisible URLs: ").append(vision.optJSONArray("urls"));if(vision.optJSONArray("barcodes")!=null)b.append("\nVisible barcodes: ").append(vision.optJSONArray("barcodes"));if(vision.optJSONArray("uncertainties")!=null)b.append("\nUncertainties: ").append(vision.optJSONArray("uncertainties"));}b.append("\nTreat only these visible observations as grounded. Do not infer the user's intent from the image alone.");return b.toString();}
    private static JSONObject json(String s){try{return new JSONObject(s==null||s.trim().isEmpty()?"{}":s);}catch(Exception e){return new JSONObject();}}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n)+"…";}

    public static final class Record{
        public final long id,occurredAt,createdAt;public final String kind,body,state,path,mimeType,displayName,payloadJson,qualityJson;
        Record(long id,String kind,String body,String state,String path,String mime,String name,String payload,String quality,long occurred,long created){this.id=id;this.kind=safe(kind);this.body=safe(body);this.state=safe(state);this.path=safe(path);this.mimeType=safe(mime);this.displayName=safe(name);this.payloadJson=safe(payload);this.qualityJson=safe(quality);this.occurredAt=occurred;this.createdAt=created;}public boolean isImage(){return kind.startsWith("IMAGE")||mimeType.startsWith("image/");}public boolean isVoice(){return kind.startsWith("AUDIO")||mimeType.startsWith("audio/");}private static String safe(String s){return s==null?"":s;}
    }
}
