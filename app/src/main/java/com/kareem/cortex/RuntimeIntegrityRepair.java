package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;

/** Idempotent, non-destructive normalization of known archive-integrity snags. */
public final class RuntimeIntegrityRepair {
    public static final String VERSION="runtime_integrity_repair_001";
    private RuntimeIntegrityRepair(){}

    public static final class Result {
        public int screenshotsRecovered,missingReferencesTombstoned,duplicateGroups,duplicateAliases;
        public String summary(){return "screenshotsRecovered="+screenshotsRecovered+" · missingTombstoned="+missingReferencesTombstoned+" · duplicateGroups="+duplicateGroups+" · duplicateAliases="+duplicateAliases;}
    }

    public static Result run(Context context,VaultDb vault){
        Result out=new Result();if(context==null||vault==null)return out;
        try{out.screenshotsRecovered=ScreenshotIngestor.repairMissingAttachments(context.getApplicationContext(),vault,10000);}catch(Throwable ignored){}
        SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();
        try{
            normalizeMissingReferences(db,out);
            normalizeDuplicateFingerprints(db,out);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        try{db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_ki_fingerprint_unique ON knowledge_items(fingerprint) WHERE fingerprint IS NOT NULL AND fingerprint<>''");}catch(Throwable ignored){}
        try{DiagnosticsLog.info(vault,"RuntimeIntegrityRepair","run",out.summary(),0,0,0,0,0,0,null);}catch(Throwable ignored){}
        return out;
    }

    private static void normalizeMissingReferences(SQLiteDatabase db,Result out){
        Cursor c=db.rawQuery("SELECT id,attachment_path,metadata_json,status FROM knowledge_items WHERE COALESCE(attachment_path,'')<>''",null);
        try{
            while(c.moveToNext()){
                long id=c.getLong(0);String path=n(c.getString(1));if(path.isEmpty())continue;
                File f=new File(path);if(f.isFile()&&f.length()>0)continue;
                String raw=n(c.getString(2)),status=n(c.getString(3));JSONObject meta=parse(raw);
                try{meta.put("attachment_state","source_missing");meta.put("attachment_previous_path",path);meta.put("attachment_repair_version",VERSION);if(!meta.has("attachment_missing_since"))meta.put("attachment_missing_since",System.currentTimeMillis());}catch(Throwable ignored){}
                ContentValues v=new ContentValues();v.put("attachment_path","");v.put("metadata_json",meta.toString());v.put("updated_at",System.currentTimeMillis());
                if("queued".equals(status)||"analyzing".equals(status)||"failed_retryable".equals(status)){v.put("status","attachment_missing");v.put("analysis_error","Attachment source is unavailable; original path preserved in metadata");}
                db.update("knowledge_items",v,"id=?",new String[]{String.valueOf(id)});out.missingReferencesTombstoned++;
            }
        }finally{c.close();}
    }

    private static void normalizeDuplicateFingerprints(SQLiteDatabase db,Result out){
        Cursor g=db.rawQuery("SELECT fingerprint FROM knowledge_items WHERE COALESCE(fingerprint,'')<>'' GROUP BY fingerprint HAVING COUNT(*)>1",null);ArrayList<String> fps=new ArrayList<>();
        try{while(g.moveToNext())fps.add(n(g.getString(0)));}finally{g.close();}
        for(String fp:fps){
            Cursor c=db.rawQuery("SELECT id,metadata_json FROM knowledge_items WHERE fingerprint=? ORDER BY id ASC",new String[]{fp});long canonical=0;ArrayList<Long> aliases=new ArrayList<>();ArrayList<String> metadata=new ArrayList<>();
            try{while(c.moveToNext()){long id=c.getLong(0);if(canonical==0)canonical=id;else{aliases.add(id);metadata.add(n(c.getString(1)));}}}finally{c.close();}
            if(canonical==0||aliases.isEmpty())continue;out.duplicateGroups++;
            for(int i=0;i<aliases.size();i++){
                long id=aliases.get(i);JSONObject meta=parse(metadata.get(i));try{meta.put("dedup_state","alias");meta.put("deduplicated_into_id",canonical);meta.put("deduplicated_fingerprint",fp);meta.put("dedup_version",VERSION);meta.put("deduplicated_at",System.currentTimeMillis());}catch(Throwable ignored){}
                ContentValues v=new ContentValues();v.put("fingerprint","");v.put("status","deduplicated");v.put("metadata_json",meta.toString());v.put("analysis_error","Duplicate identity retained as alias of item #"+canonical);v.put("updated_at",System.currentTimeMillis());db.update("knowledge_items",v,"id=?",new String[]{String.valueOf(id)});out.duplicateAliases++;
            }
        }
    }

    private static JSONObject parse(String raw){try{return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);}catch(Throwable e){JSONObject x=new JSONObject();try{x.put("legacy_metadata_unparsed",raw==null?"":raw);}catch(Throwable ignored){}return x;}}
    private static String n(String s){return s==null?"":s.trim();}
}
