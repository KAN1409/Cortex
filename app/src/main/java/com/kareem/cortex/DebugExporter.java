package com.kareem.cortex;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Development-only snapshot exporter. Never exports provider API keys or binary attachments. */
public final class DebugExporter {
    public static final int SCHEMA_VERSION=1;
    private DebugExporter(){}

    public static void exportAndShare(Activity a,VaultDb db){
        android.widget.Toast.makeText(a,"Building Cortex debug snapshot…",android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                File f=build(a,db);
                a.runOnUiThread(()->share(a,f));
            }catch(Exception e){
                a.runOnUiThread(()->android.widget.Toast.makeText(a,"Debug export failed: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    public static File build(Context c,VaultDb db)throws Exception{
        FeatureStore.ensure(db);TemporalResolver.ensure(db);CoreBrainEngine.ensure(db);
        JSONObject root=new JSONObject();
        root.put("schema_version",SCHEMA_VERSION);
        root.put("generated_at",iso(System.currentTimeMillis()));
        root.put("purpose","Cortex development diagnostic snapshot");
        root.put("privacy_note","API keys and binary attachments are intentionally excluded. Paths, raw text, OCR, transcripts, analyses and structured database records are included.");
        root.put("app",app(c));
        root.put("device",device());
        root.put("environment",environment(c));
        SQLiteDatabase s=db.getReadableDatabase();
        root.put("database_health",databaseHealth(s));
        root.put("feature_status",featureStatus(c,s));
        root.put("memory_stats",memoryStats(s));
        root.put("screenshot_pipeline",screenshotStats(s));
        root.put("smart_inbox_stats",smartInboxStats(db,s));
        root.put("temporal_stats",temporalStats(s));
        root.put("self_checks",selfChecks(c,db,s));

        JSONObject data=new JSONObject();
        String[] full={"knowledge_items","analyses","entities","actions","action_temporal","vision_fields","smart_inbox","correction_rules","screenshot_learning","relations","memory_facets","context_packs","context_pack_items","integration_log","examples"};
        for(String table:full)if(tableExists(s,table))data.put(table,dumpTable(s,table));
        // Semantic vectors can make the diagnostic package enormous; preserve complete index metadata, not vector BLOB bytes.
        if(tableExists(s,"memory_chunks"))data.put("memory_chunks",dumpTable(s,"memory_chunks"));
        if(tableExists(s,"embeddings"))data.put("embeddings_metadata",dumpQuery(s,"SELECT id,chunk_id,provider,version,dims,updated_at,length(vector) AS vector_bytes FROM embeddings ORDER BY id"));
        root.put("data",data);

        File dir=new File(c.getFilesDir(),"debug_exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create debug export directory");
        String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        File out=new File(dir,"CortexDebug_v"+versionCode(c)+"_"+stamp+".json");
        try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out),java.nio.charset.StandardCharsets.UTF_8))){w.write(root.toString(2));}
        return out;
    }

    private static JSONObject app(Context c)throws Exception{JSONObject o=new JSONObject();o.put("package",c.getPackageName());o.put("version_name",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);o.put("version_code",versionCode(c));o.put("db_file_bytes",c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0);return o;}
    private static long versionCode(Context c){try{android.content.pm.PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}catch(Exception e){return 0;}}
    private static JSONObject device()throws Exception{JSONObject o=new JSONObject();o.put("manufacturer",Build.MANUFACTURER);o.put("model",Build.MODEL);o.put("device",Build.DEVICE);o.put("android_release",Build.VERSION.RELEASE);o.put("sdk",Build.VERSION.SDK_INT);o.put("abis",new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));o.put("timezone",TimeZone.getDefault().getID());return o;}

    private static JSONObject environment(Context c)throws Exception{
        JSONObject o=new JSONObject();LocalModelManager.Status m=LocalModelManager.status(c);JSONObject model=new JSONObject();
        model.put("name",LocalModelManager.MODEL_NAME);model.put("file",LocalModelManager.MODEL_FILE);model.put("download_url",LocalModelManager.MODEL_URL);model.put("status",m.label);model.put("progress_percent",m.percent);model.put("downloaded_bytes",m.done);model.put("total_bytes",m.total);model.put("file_bytes",LocalModelManager.size(c));model.put("file_present",LocalModelManager.filePresent(c));model.put("sha_verified",m.verified);model.put("runtime_installed",LocalModelManager.runtimeInstalled(c));model.put("inference_ready",LocalModelManager.installed(c));model.put("expected_sha256",LocalModelManager.SHA256);o.put("local_model",model);
        JSONObject shot=new JSONObject();shot.put("folder_connected",ScreenshotIngestor.tree(c)!=null);shot.put("folder_label",ScreenshotIngestor.treeLabel(c));o.put("screenshot_source",shot);
        JSONObject cloud=new JSONObject();cloud.put("gemini_configured",GeminiKeyStore.has(c));cloud.put("groq_configured",GroqKeyStore.has(c));cloud.put("secrets_exported",false);o.put("cloud",cloud);
        JSONObject ocr=new JSONObject();ocr.put("mlkit_latin","configured");ocr.put("tesseract_arabic","configured");o.put("ocr",ocr);return o;
    }

    private static JSONObject featureStatus(Context c,SQLiteDatabase s)throws Exception{
        JSONObject o=new JSONObject();
        o.put("universal_capture","active");o.put("automatic_understanding","active_partial");o.put("ask_cortex","active_partial");o.put("needs_attention","active");o.put("reminders_dates",tableExists(s,"action_temporal")?"active_partial":"inactive");o.put("timeline","active_partial");o.put("notification_memory","active_partial");o.put("people_memory",tableExists(s,"memory_facets")?"active_partial":"inactive");o.put("projects_context_packs",tableExists(s,"context_packs")?"active_partial":"inactive");o.put("prompt_library","active_partial");o.put("screenshot_intelligence",tableExists(s,"vision_fields")?"active_partial":"inactive");o.put("memory_connections",tableExists(s,"relations")?"active_partial":"inactive");o.put("smart_inbox","active");o.put("semantic_search",tableExists(s,"embeddings")?"active_partial":"inactive");o.put("daily_weekly_brief","active_partial");o.put("corrections_learning",tableExists(s,"correction_rules")?"active_partial":"inactive");o.put("privacy_controls","partial");o.put("backup_restore_export","debug_export_active");o.put("integrations","partial");o.put("proactive_cortex","partial");o.put("local_llm_runtime",LocalModelManager.installed(c)?"active":"not_yet_active");return o;
    }

    private static JSONObject databaseHealth(SQLiteDatabase s)throws Exception{JSONObject o=new JSONObject();JSONArray tables=new JSONArray();Cursor c=s.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",null);while(c.moveToNext()){String t=c.getString(0);JSONObject x=new JSONObject();x.put("table",t);x.put("rows",count(s,"SELECT COUNT(*) FROM "+t));tables.put(x);}c.close();o.put("tables",tables);o.put("total_tables",tables.length());return o;}
    private static JSONObject memoryStats(SQLiteDatabase s)throws Exception{JSONObject o=new JSONObject();o.put("total",count(s,"SELECT COUNT(*) FROM knowledge_items"));o.put("by_type",group(s,"SELECT type,COUNT(*) FROM knowledge_items GROUP BY type ORDER BY COUNT(*) DESC"));o.put("by_status",group(s,"SELECT status,COUNT(*) FROM knowledge_items GROUP BY status ORDER BY COUNT(*) DESC"));o.put("by_source",group(s,"SELECT source,COUNT(*) FROM knowledge_items GROUP BY source ORDER BY COUNT(*) DESC"));o.put("with_attachment",count(s,"SELECT COUNT(*) FROM knowledge_items WHERE attachment_path IS NOT NULL AND TRIM(attachment_path)<>''"));o.put("with_extracted_text",count(s,"SELECT COUNT(*) FROM knowledge_items WHERE extracted_text IS NOT NULL AND TRIM(extracted_text)<>''"));return o;}
    private static JSONObject screenshotStats(SQLiteDatabase s)throws Exception{JSONObject o=new JSONObject();String w="type IN ('SCREENSHOT','IMAGE')";o.put("total",count(s,"SELECT COUNT(*) FROM knowledge_items WHERE "+w));o.put("by_status",group(s,"SELECT status,COUNT(*) FROM knowledge_items WHERE "+w+" GROUP BY status ORDER BY COUNT(*) DESC"));o.put("ocr_text_present",count(s,"SELECT COUNT(*) FROM knowledge_items WHERE "+w+" AND extracted_text IS NOT NULL AND TRIM(extracted_text)<>''"));o.put("ocr_text_empty",count(s,"SELECT COUNT(*) FROM knowledge_items WHERE "+w+" AND status='analyzed' AND (extracted_text IS NULL OR TRIM(extracted_text)='')"));o.put("structured_fields",tableExists(s,"vision_fields")?count(s,"SELECT COUNT(*) FROM vision_fields"):0);o.put("with_actions",count(s,"SELECT COUNT(DISTINCT k.id) FROM knowledge_items k JOIN actions a ON a.item_id=k.id WHERE k.type IN ('SCREENSHOT','IMAGE') AND a.status='open'"));o.put("technical_failures",count(s,"SELECT COUNT(*) FROM knowledge_items WHERE "+w+" AND status IN ('analysis_failed','failed_retryable')"));return o;}
    private static JSONObject smartInboxStats(VaultDb db,SQLiteDatabase s)throws Exception{JSONObject o=new JSONObject();o.put("needs_count",FeatureStore.needs(db,5000).size());o.put("inbox_count",FeatureStore.inbox(db,5000).size());if(tableExists(s,"smart_inbox"))o.put("by_bucket",group(s,"SELECT bucket,COUNT(*) FROM smart_inbox GROUP BY bucket ORDER BY COUNT(*) DESC"));return o;}
    private static JSONObject temporalStats(SQLiteDatabase s)throws Exception{JSONObject o=new JSONObject();if(!tableExists(s,"action_temporal")){o.put("active",false);return o;}o.put("active",true);o.put("resolved_actions",count(s,"SELECT COUNT(*) FROM action_temporal WHERE resolved_at>0"));o.put("open_actions_with_due",count(s,"SELECT COUNT(*) FROM actions WHERE status='open' AND due_text IS NOT NULL AND TRIM(due_text)<>''"));o.put("relative_due_still_stored",count(s,"SELECT COUNT(*) FROM actions WHERE status='open' AND (due_text LIKE '%بكر%' OR lower(due_text) LIKE '%tomorrow%' OR due_text LIKE '%النهاردة%' OR lower(due_text) LIKE '%today%')"));return o;}

    private static JSONArray selfChecks(Context c,VaultDb db,SQLiteDatabase s)throws Exception{JSONArray a=new JSONArray();
        check(a,"model_download_present",LocalModelManager.filePresent(c),LocalModelManager.filePresent(c)?"GGUF-sized model file exists":"Model file is absent/incomplete","info");
        check(a,"model_sha_verified",LocalModelManager.verified(c),LocalModelManager.verified(c)?"SHA-256 verification passed":"File is not yet cryptographically verified","warning");
        check(a,"local_inference_ready",LocalModelManager.installed(c),LocalModelManager.installed(c)?"Local model runtime passed readiness":"Native local inference runtime is not active yet","warning");
        int suspicious=(int)count(s,"SELECT COUNT(DISTINCT k.id) FROM knowledge_items k JOIN actions a ON a.item_id=k.id WHERE k.type IN ('SCREENSHOT','IMAGE') AND a.status='open' AND k.source='screenshot-folder'");check(a,"screenshot_generated_open_actions",suspicious==0,"Folder screenshots with open actions: "+suspicious,suspicious==0?"ok":"warning");
        int failures=(int)count(s,"SELECT COUNT(*) FROM knowledge_items WHERE type IN ('SCREENSHOT','IMAGE') AND status IN ('analysis_failed','failed_retryable')");check(a,"screenshot_ocr_failures",failures==0,"Screenshot/image analysis failures: "+failures,failures==0?"ok":"warning");
        int unresolved=(int)count(s,"SELECT COUNT(*) FROM actions WHERE status='open' AND (due_text LIKE '%بكر%' OR lower(due_text) LIKE '%tomorrow%')");check(a,"relative_due_unresolved",unresolved==0,"Open actions still carrying relative tomorrow text: "+unresolved,unresolved==0?"ok":"warning");
        int empty=(int)count(s,"SELECT COUNT(*) FROM knowledge_items WHERE type IN ('SCREENSHOT','IMAGE') AND status='analyzed' AND (extracted_text IS NULL OR TRIM(extracted_text)='')");check(a,"analyzed_screenshots_without_ocr_text",empty==0,"Analyzed images with empty OCR text: "+empty,empty==0?"ok":"info");
        int missing=0;Cursor q=s.rawQuery("SELECT attachment_path FROM knowledge_items WHERE attachment_path IS NOT NULL AND TRIM(attachment_path)<>''",null);while(q.moveToNext()){String p=q.getString(0);if(p!=null&&!new File(p).exists())missing++;}q.close();check(a,"missing_attachment_files",missing==0,"Missing attachment files: "+missing,missing==0?"ok":"warning");return a;}
    private static void check(JSONArray a,String id,boolean pass,String detail,String severity)throws Exception{JSONObject o=new JSONObject();o.put("id",id);o.put("pass",pass);o.put("severity",severity);o.put("detail",detail);a.put(o);}

    private static JSONArray dumpTable(SQLiteDatabase s,String table)throws Exception{return dumpQuery(s,"SELECT * FROM "+table+" ORDER BY 1");}
    private static JSONArray dumpQuery(SQLiteDatabase s,String sql)throws Exception{JSONArray out=new JSONArray();Cursor c=s.rawQuery(sql,null);String[] cols=c.getColumnNames();while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<cols.length;i++){if(c.isNull(i)){row.put(cols[i],JSONObject.NULL);continue;}int type=c.getType(i);if(type==Cursor.FIELD_TYPE_INTEGER)row.put(cols[i],c.getLong(i));else if(type==Cursor.FIELD_TYPE_FLOAT)row.put(cols[i],c.getDouble(i));else if(type==Cursor.FIELD_TYPE_BLOB)row.put(cols[i],"<BLOB "+c.getBlob(i).length+" bytes>");else row.put(cols[i],c.getString(i));}out.put(row);}c.close();return out;}
    private static JSONArray group(SQLiteDatabase s,String sql)throws Exception{JSONArray out=new JSONArray();Cursor c=s.rawQuery(sql,null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("value",c.isNull(0)?"<null>":c.getString(0));o.put("count",c.getLong(1));out.put(o);}c.close();return out;}
    private static long count(SQLiteDatabase s,String sql){Cursor c=s.rawQuery(sql,null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static boolean tableExists(SQLiteDatabase s,String t){Cursor c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{t});boolean b=c.moveToFirst();c.close();return b;}
    private static String iso(long ms){return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date(ms));}

    private static void share(Activity a,File f){try{Uri u=FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",f);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/json");i.putExtra(Intent.EXTRA_STREAM,u);i.putExtra(Intent.EXTRA_SUBJECT,"Cortex Debug Snapshot");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);a.startActivity(Intent.createChooser(i,"Share Cortex debug snapshot"));}catch(Exception e){android.widget.Toast.makeText(a,"Share failed: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}
}
