package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;

/** Inventory-only recursive scanner. Parsing/indexing is intentionally a separate resumable stage. */
public final class WorkVaultScanner {
    public static final String VERSION="work_vault_scanner_004";
    private WorkVaultScanner(){}

    public static Result scan(Context context,VaultDb vault,long sourceId,Uri treeUri){
        Result result=new Result();
        if(context==null||vault==null||sourceId<=0||treeUri==null){result.error="invalid scan request";return result;}
        SQLiteDatabase db=vault.getWritableDatabase();WorkVaultIndexSchema.ensure(db);WorkDocumentProfileStore.ensure(db);
        long now=System.currentTimeMillis();
        long jobId=createJob(db,sourceId,now);
        HashSet<String> seenUris=new HashSet<>();
        try{
            DocumentFile root=DocumentFile.fromTreeUri(context,treeUri);
            if(root==null||!root.exists()||!root.canRead())throw new IllegalStateException("Archive source is not readable");
            ArrayDeque<DocumentFile> stack=new ArrayDeque<>();stack.push(root);
            while(!stack.isEmpty()){
                DocumentFile dir=stack.pop();
                DocumentFile[] children;
                try{children=dir.listFiles();}catch(Throwable t){result.failed++;continue;}
                for(DocumentFile child:children){
                    if(child==null)continue;
                    if(child.isDirectory()){stack.push(child);continue;}
                    if(!child.isFile())continue;
                    result.total++;
                    String uri=child.getUri().toString();
                    if(!uri.isEmpty())seenUris.add(uri);
                    try{
                        upsertFile(db,sourceId,dir.getUri(),child);
                        result.processed++;
                    }catch(Throwable t){result.failed++;}
                    if((result.total%100)==0)updateJob(db,jobId,result,"",false);
                }
            }
            if(shouldReconcileMissing(result.failed,result.error))result.missing=reconcileMissingFiles(db,sourceId,seenUris,System.currentTimeMillis());
            else result.missingReconciliationSkipped=true;
            updateJob(db,jobId,result,"",true);
            WorkVaultSourceStore.scanFinished(vault,sourceId,"");
        }catch(Throwable t){
            result.error=t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();
            result.missingReconciliationSkipped=true;
            updateJob(db,jobId,result,result.error,true);
            WorkVaultSourceStore.scanFinished(vault,sourceId,result.error);
        }
        return result;
    }

    private static void upsertFile(SQLiteDatabase db,long sourceId,Uri parent,DocumentFile file){
        String uri=file.getUri().toString(),name=n(file.getName()),mime=n(file.getType());
        long size=Math.max(0,file.length()),modified=Math.max(0,file.lastModified()),now=System.currentTimeMillis();
        Cursor c=db.rawQuery("SELECT id,size_bytes,modified_at,state FROM work_files WHERE document_uri=? LIMIT 1",new String[]{uri});
        long id=0,oldSize=-1,oldModified=-1;String oldState="";if(c.moveToFirst()){id=c.getLong(0);oldSize=c.getLong(1);oldModified=c.getLong(2);oldState=n(c.getString(3));}c.close();
        ContentValues v=new ContentValues();
        v.put("source_id",sourceId);v.put("parent_uri",parent==null?"":parent.toString());v.put("display_name",name);
        v.put("mime_type",mime);v.put("extension",extension(name));v.put("size_bytes",size);v.put("modified_at",modified);v.put("updated_at",now);
        String state;
        if(id<=0)state="new";
        else if("missing".equals(oldState))state="modified";
        else if(oldSize!=size||oldModified!=modified)state="modified";
        else state=oldState.isEmpty()?"unchanged":oldState;
        v.put("state",state);
        if(id>0){db.update("work_files",v,"id=?",new String[]{String.valueOf(id)});return;}
        v.put("document_uri",uri);v.put("fingerprint","");v.put("parser_version","");v.put("indexed_at",0);v.put("created_at",now);
        db.insertOrThrow("work_files",null,v);
    }

    private static int reconcileMissingFiles(SQLiteDatabase db,long sourceId,HashSet<String> seen,long now){
        int missing=0;ArrayDeque<Long> victims=new ArrayDeque<>();
        Cursor c=db.rawQuery("SELECT id,document_uri,state FROM work_files WHERE source_id=?",new String[]{String.valueOf(sourceId)});
        try{
            while(c.moveToNext()){
                long id=c.getLong(0);String uri=n(c.getString(1)),state=n(c.getString(2));
                if(seen.contains(uri)||"missing".equals(state))continue;
                victims.add(id);
            }
        }finally{c.close();}
        if(victims.isEmpty())return 0;
        db.beginTransaction();
        try{
            while(!victims.isEmpty()){
                long id=victims.removeFirst();
                WorkProcurementLinker.cleanupForReindex(db,id);
                db.delete("work_document_profiles","file_id=?",new String[]{String.valueOf(id)});
                ContentValues v=new ContentValues();v.put("state","missing");v.put("active_version_id",0);v.put("updated_at",now);
                if(db.update("work_files",v,"id=?",new String[]{String.valueOf(id)})==1)missing++;
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return missing;
    }

    static boolean shouldReconcileMissing(int failed,String error){return failed==0&&n(error).isEmpty();}

    private static long createJob(SQLiteDatabase db,long sourceId,long now){
        ContentValues v=new ContentValues();v.put("source_id",sourceId);v.put("state","running");v.put("started_at",now);v.put("updated_at",now);return db.insertOrThrow("work_index_jobs",null,v);
    }
    private static void updateJob(SQLiteDatabase db,long jobId,Result r,String error,boolean done){
        ContentValues v=new ContentValues();v.put("total_files",r.total);v.put("processed_files",r.processed);v.put("failed_files",r.failed);v.put("updated_at",System.currentTimeMillis());v.put("error",n(error));
        if(done){v.put("state",r.error.isEmpty()?"inventory_complete":"failed");v.put("completed_at",System.currentTimeMillis());}
        db.update("work_index_jobs",v,"id=?",new String[]{String.valueOf(jobId)});
    }

    public static Counts counts(VaultDb vault){
        SQLiteDatabase db=vault.getReadableDatabase();WorkVaultIndexSchema.ensure(db);Counts out=new Counts();
        Cursor c=db.rawQuery("SELECT COUNT(*),SUM(CASE WHEN state='new' THEN 1 ELSE 0 END),SUM(CASE WHEN state='modified' THEN 1 ELSE 0 END),SUM(CASE WHEN state='needs_ocr' THEN 1 ELSE 0 END),SUM(size_bytes) FROM work_files WHERE state<>'missing'",null);
        if(c.moveToFirst()){out.files=c.getLong(0);out.newFiles=c.isNull(1)?0:c.getLong(1);out.modifiedFiles=c.isNull(2)?0:c.getLong(2);out.needsOcrFiles=c.isNull(3)?0:c.getLong(3);out.bytes=c.isNull(4)?0:c.getLong(4);}c.close();
        c=db.rawQuery("SELECT COUNT(*) FROM work_projects WHERE state='active'",null);if(c.moveToFirst())out.projects=c.getLong(0);c.close();
        c=db.rawQuery("SELECT COUNT(*) FROM work_price_records p JOIN work_files f ON f.id=p.file_id WHERE f.active_version_id>0 AND p.version_id=f.active_version_id",null);if(c.moveToFirst())out.prices=c.getLong(0);c.close();
        c=db.rawQuery("SELECT COUNT(*) FROM work_followup_records u JOIN work_files f ON f.id=u.file_id WHERE f.active_version_id>0 AND u.version_id=f.active_version_id",null);if(c.moveToFirst())out.followUps=c.getLong(0);c.close();
        c=db.rawQuery("SELECT COUNT(*) FROM work_followup_records u JOIN work_files f ON f.id=u.file_id WHERE f.active_version_id>0 AND u.version_id=f.active_version_id AND u.status_normalized IN ('open','on_hold','other','unknown')",null);if(c.moveToFirst())out.openFollowUps=c.getLong(0);c.close();
        return out;
    }

    public static final class Result{public int total,processed,failed,missing;public boolean missingReconciliationSkipped;public String error="";}
    public static final class Counts{public long files,newFiles,modifiedFiles,needsOcrFiles,bytes,projects,prices,followUps,openFollowUps;}
    private static String extension(String name){int i=name.lastIndexOf('.');return i<0||i==name.length()-1?"":name.substring(i+1).toLowerCase(Locale.ROOT);}
    private static String n(String s){return s==null?"":s.trim();}
}
