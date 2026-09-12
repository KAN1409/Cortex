package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONObject;
import java.util.*;

/** Resumable file-by-file indexer. A failed document never invalidates the rest of the archive. */
public final class WorkVaultIndexer {
    public static final String VERSION="work_vault_indexer_001";
    private WorkVaultIndexer(){}

    public static Result indexPending(Context context,VaultDb vault,long sourceId){
        Result out=new Result();SQLiteDatabase db=vault.getWritableDatabase();WorkVaultIndexSchema.ensure(db);
        ArrayList<FileRow> rows=new ArrayList<>();
        Cursor c=db.rawQuery("SELECT id,document_uri,display_name,extension,COALESCE(fingerprint,'') FROM work_files WHERE source_id=? AND state IN ('new','modified','parse_failed') ORDER BY id ASC",new String[]{String.valueOf(sourceId)});
        while(c.moveToNext())rows.add(new FileRow(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)));c.close();
        out.total=rows.size();
        for(FileRow f:rows){
            if(Thread.currentThread().isInterrupted()){out.interrupted=true;break;}
            if(!WorkDocumentParser.supported(f.ext)){mark(db,f.id,"unsupported","",0);out.unsupported++;continue;}
            try{
                WorkParsedDocument parsed=WorkDocumentParser.parse(context.getApplicationContext(),Uri.parse(f.uri),f.ext);
                WorkStructuredExtractor.Result structured=WorkStructuredExtractor.extract(parsed);
                persist(db,f,parsed,structured);
                out.indexed++;
                if(parsed.needsOcr)out.needsOcr++;
            }catch(Throwable t){mark(db,f.id,"parse_failed",VERSION,0);out.failed++;out.lastError=t.getClass().getSimpleName()+": "+safe(t.getMessage());}
        }
        return out;
    }

    private static void persist(SQLiteDatabase db,FileRow f,WorkParsedDocument parsed,WorkStructuredExtractor.Result structured)throws Exception{
        long now=System.currentTimeMillis();db.beginTransaction();try{
            WorkVaultIndexSchema.ensure(db);
            ContentValues vv=new ContentValues();vv.put("file_id",f.id);vv.put("fingerprint",f.fingerprint.isEmpty()?"unknown-"+now:f.fingerprint);vv.put("parser_version",parsed.parserVersion);vv.put("state",parsed.needsOcr?"partial_needs_ocr":"complete");vv.put("parsed_at",now);vv.put("error","");
            long versionId=db.insertWithOnConflict("work_file_versions",null,vv,SQLiteDatabase.CONFLICT_IGNORE);
            if(versionId<=0){Cursor q=db.rawQuery("SELECT id FROM work_file_versions WHERE file_id=? AND fingerprint=? AND parser_version=? LIMIT 1",new String[]{String.valueOf(f.id),f.fingerprint.isEmpty()?"unknown-"+now:f.fingerprint,parsed.parserVersion});versionId=q.moveToFirst()?q.getLong(0):0;q.close();}

            if(versionId>0){db.delete("work_chunks","version_id=?",new String[]{String.valueOf(versionId)});int i=0;for(WorkParsedDocument.Block b:parsed.blocks){if(b==null||safe(b.text).isEmpty())continue;ContentValues x=new ContentValues();x.put("file_id",f.id);x.put("version_id",versionId);x.put("chunk_index",i++);x.put("chunk_kind",safe(b.kind));x.put("chunk_text",b.text);x.put("sheet_name",safe(b.sheetName));x.put("page_number",b.pageNumber);x.put("slide_number",b.slideNumber);x.put("row_number",b.rowNumber);JSONObject loc=new JSONObject();loc.put("cells",new JSONObject(b.cells));loc.put("formulas",new JSONObject(b.formulas));x.put("location_json",loc.toString());x.put("created_at",now);db.insert("work_chunks",null,x);}}

            db.delete("work_facts","file_id=?",new String[]{String.valueOf(f.id)});
            db.delete("work_procurement_refs","file_id=?",new String[]{String.valueOf(f.id)});
            db.delete("work_price_records","file_id=?",new String[]{String.valueOf(f.id)});
            db.delete("work_relations","source_file_id=?",new String[]{String.valueOf(f.id)});

            long projectId=0;
            if(!structured.projects.isEmpty())projectId=upsertProject(db,structured.projects.get(0).name,now);
            for(WorkStructuredExtractor.Project p:structured.projects){long pid=upsertProject(db,p.name,now);fact(db,f.id,pid,"PROJECT","project",p.name,null,"","",p.source,.92,now);}
            for(WorkStructuredExtractor.Ref ref:structured.refs){ContentValues x=new ContentValues();x.put("file_id",f.id);x.put("project_id",projectId);x.put("ref_type",ref.type);x.put("ref_value",ref.value);x.put("normalized_value",ref.value.toUpperCase(Locale.ROOT));x.put("confidence",.94);x.put("created_at",now);db.insertWithOnConflict("work_procurement_refs",null,x,SQLiteDatabase.CONFLICT_IGNORE);fact(db,f.id,projectId,ref.type,ref.type,ref.value,null,"","",ref.source,.94,now);}
            for(WorkStructuredExtractor.Price p:structured.prices){ContentValues x=new ContentValues();x.put("file_id",f.id);x.put("project_id",projectId);x.put("item_name",p.item);x.put("vendor_name",p.vendor);if(p.quantity!=null)x.put("quantity",p.quantity);x.put("unit",p.unit);if(p.unitPrice!=null)x.put("unit_price",p.unitPrice);if(p.totalPrice!=null)x.put("total_price",p.totalPrice);x.put("currency",p.currency);x.put("sheet_name",safe(p.source.sheetName));x.put("page_number",p.source.pageNumber);x.put("row_number",p.source.rowNumber);x.put("confidence",.86);x.put("created_at",now);long priceId=db.insert("work_price_records",null,x);if(!p.vendor.isEmpty()){long vendorId=upsertEntity(db,"VENDOR",p.vendor,now);relation(db,"PRICE",priceId,"ENTITY",vendorId,"vendor",.90,f.id,now);}}

            mark(db,f.id,parsed.needsOcr?"needs_ocr":"indexed",parsed.parserVersion,now);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private static void fact(SQLiteDatabase db,long file,long project,String type,String key,String text,Double number,String unit,String currency,WorkParsedDocument.Block b,double confidence,long now){ContentValues x=new ContentValues();x.put("file_id",file);x.put("project_id",project);x.put("fact_type",type);x.put("fact_key",key);x.put("text_value",text);if(number!=null)x.put("numeric_value",number);x.put("unit",unit);x.put("currency",currency);if(b!=null){x.put("sheet_name",safe(b.sheetName));x.put("page_number",b.pageNumber);x.put("slide_number",b.slideNumber);x.put("row_number",b.rowNumber);}x.put("confidence",confidence);x.put("extractor_version",WorkStructuredExtractor.VERSION);x.put("created_at",now);db.insert("work_facts",null,x);}
    private static long upsertProject(SQLiteDatabase db,String name,long now){String n=safe(name),key=norm(n);if(n.isEmpty())return 0;Cursor c=db.rawQuery("SELECT id FROM work_projects WHERE normalized_key=? LIMIT 1",new String[]{key});long id=c.moveToFirst()?c.getLong(0):0;c.close();ContentValues v=new ContentValues();v.put("canonical_name",n);v.put("normalized_key",key);v.put("state","active");v.put("updated_at",now);if(id>0){db.update("work_projects",v,"id=?",new String[]{String.valueOf(id)});return id;}v.put("created_at",now);return db.insert("work_projects",null,v);}
    private static long upsertEntity(SQLiteDatabase db,String kind,String name,long now){String n=safe(name),key=kind.toLowerCase(Locale.ROOT)+"|"+norm(n);if(n.isEmpty())return 0;Cursor c=db.rawQuery("SELECT id FROM work_entities WHERE normalized_key=? LIMIT 1",new String[]{key});long id=c.moveToFirst()?c.getLong(0):0;c.close();ContentValues v=new ContentValues();v.put("kind",kind);v.put("canonical_name",n);v.put("normalized_key",key);v.put("metadata_json","{}");v.put("updated_at",now);if(id>0){db.update("work_entities",v,"id=?",new String[]{String.valueOf(id)});return id;}v.put("created_at",now);return db.insert("work_entities",null,v);}
    private static void relation(SQLiteDatabase db,String ft,long fid,String tt,long tid,String rel,double conf,long file,long now){if(fid<=0||tid<=0)return;ContentValues x=new ContentValues();x.put("from_type",ft);x.put("from_id",fid);x.put("to_type",tt);x.put("to_id",tid);x.put("relation",rel);x.put("confidence",conf);x.put("source_file_id",file);x.put("created_at",now);db.insert("work_relations",null,x);}
    private static void mark(SQLiteDatabase db,long id,String state,String parser,long indexedAt){ContentValues v=new ContentValues();v.put("state",state);v.put("parser_version",parser);if(indexedAt>0)v.put("indexed_at",indexedAt);v.put("updated_at",System.currentTimeMillis());db.update("work_files",v,"id=?",new String[]{String.valueOf(id)});}
    private static String norm(String s){return safe(s).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    private static String safe(String s){return s==null?"":s.trim();}

    private static final class FileRow{final long id;final String uri,name,ext,fingerprint;FileRow(long id,String u,String n,String e,String f){this.id=id;uri=u;name=n;ext=e==null?"":e;fingerprint=f==null?"":f;}}
    public static final class Result{public int total,indexed,failed,unsupported,needsOcr;public boolean interrupted;public String lastError="";}
}
