package com.kareem.cortex;

import android.app.Activity;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Reliable shell around the exhaustive exporter: debug evidence must still leave the app when one diagnostic section fails. */
public final class ReliableDebugExporter {
    private static final long MAX_DB_FOR_EXHAUSTIVE=16L*1024L*1024L;
    private static final long MIN_HEAP_HEADROOM_FOR_EXHAUSTIVE=192L*1024L*1024L;
    private ReliableDebugExporter(){}

    public static void exportAndShare(Activity a,VaultDb db){
        android.widget.Toast.makeText(a,"Building Cortex debug package…",android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{
            File file=null;Throwable fullFailure=null;
            try{
                String risk=exhaustiveRisk(a);
                if(!risk.isEmpty())throw new FullExportSkipped(risk);
                file=DebugExporter.build(a,db);
            }catch(Throwable e){
                fullFailure=e;
                try{file=buildRecovery(a,db,e);}catch(Throwable recovery){fullFailure=recovery;}
            }
            final File f=file;final Throwable err=fullFailure;
            if(f==null){a.runOnUiThread(()->android.widget.Toast.makeText(a,"Debug export failed: "+safeMessage(err),android.widget.Toast.LENGTH_LONG).show());return;}
            final String saved=saveDownloadCopy(a,f);
            a.runOnUiThread(()->{
                String msg=saved.isEmpty()?"Debug package created":"Debug package saved to "+saved;
                android.widget.Toast.makeText(a,msg,android.widget.Toast.LENGTH_LONG).show();
                share(a,f);
            });
        },"CortexReliableDebugExport").start();
    }

    private static String exhaustiveRisk(Context c){
        try{
            File db=c.getDatabasePath("cortex.db");long dbBytes=db.exists()?db.length():0;
            Runtime rt=Runtime.getRuntime();long used=rt.totalMemory()-rt.freeMemory();long headroom=Math.max(0,rt.maxMemory()-used);
            if(dbBytes>MAX_DB_FOR_EXHAUSTIVE)return"Database is "+dbBytes+" bytes; exhaustive in-memory JSON is skipped to protect the app heap";
            if(headroom<MIN_HEAP_HEADROOM_FOR_EXHAUSTIVE)return"Only "+headroom+" bytes of Java heap headroom remain; exhaustive export is skipped to avoid OutOfMemoryError";
        }catch(Throwable ignored){}
        return"";
    }

    private static File buildRecovery(Context c,VaultDb db,Throwable cause)throws Exception{
        JSONObject root=new JSONObject();
        root.put("schema_version",7);
        root.put("recovery_export",true);
        root.put("generated_at",System.currentTimeMillis());
        root.put("full_export_skipped",cause instanceof FullExportSkipped);
        root.put("full_export_failure",error(cause));
        root.put("memory_preflight",memoryPreflight(c));
        root.put("package",c.getPackageName());
        root.put("android_sdk",Build.VERSION.SDK_INT);
        root.put("device",Build.MANUFACTURER+" "+Build.MODEL);

        // Capture both the configured primary provider and the effective route after failover. A primary
        // OX 429 marks the cooldown; the immediate second health check should therefore exercise Gemini
        // when it is configured, matching the route Brain/Useful Next Moves will actually use.
        try{
            ExternalBrainProvider.HealthReport primary=ExternalBrainProvider.healthCheck(c);
            root.put("brain_health_primary",primary.human());
            ExternalBrainProvider.HealthReport effective=primary;
            if(!primary.ok&&primary.httpCode==429&&GeminiKeyStore.has(c))effective=ExternalBrainProvider.healthCheck(c);
            root.put("brain_provider",ExternalBrainProvider.configurationHint(c));
            root.put("brain_health",effective.human());
            root.put("brain_failover_active",primary.httpCode==429&&effective.ok&&"gemini".equalsIgnoreCase(effective.provider));
        }catch(Throwable e){
            root.put("brain_provider",safe(()->ExternalBrainProvider.configurationHint(c)));
            root.put("brain_health","ERROR: "+error(e));
        }

        root.put("access_gates",access(c));
        root.put("local_model",safe(()->LocalLlmRuntime.state(c).state+" · "+LocalLlmRuntime.state(c).error));
        root.put("db_file_bytes",c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0);
        if(db!=null){
            try{root.put("capability_matrix",capabilities(c,db));}catch(Throwable e){root.put("capability_matrix_error",error(e));}
            try{CortexAuditStore.Run r=CortexAuditStore.latest(db);if(r!=null)root.put("latest_audit","#"+r.id+" · "+r.status+" · "+r.progress()+"%");}catch(Throwable e){root.put("audit_error",error(e));}
            try{ContextDiagnostics.Report r=ContextDiagnostics.build(db,24L*60L*60L*1000L,80);root.put("context_replay",new JSONObject(r.json));}catch(Throwable e){root.put("context_replay_error",error(e));}
        }
        File dir=new File(c.getFilesDir(),"debug_exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create debug export directory");
        File out=new File(dir,"CortexDebug_RECOVERY_"+stamp()+".json");
        try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out),StandardCharsets.UTF_8),16384)){w.write(root.toString(2));}
        return out;
    }

    private static JSONObject memoryPreflight(Context c)throws Exception{
        Runtime rt=Runtime.getRuntime();long used=rt.totalMemory()-rt.freeMemory();JSONObject o=new JSONObject();
        o.put("db_file_bytes",c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0);
        o.put("heap_max_bytes",rt.maxMemory());o.put("heap_used_bytes",used);o.put("heap_headroom_bytes",Math.max(0,rt.maxMemory()-used));
        o.put("exhaustive_db_limit_bytes",MAX_DB_FOR_EXHAUSTIVE);o.put("exhaustive_min_headroom_bytes",MIN_HEAP_HEADROOM_FOR_EXHAUSTIVE);return o;
    }

    private static JSONArray capabilities(Context c,VaultDb db){JSONArray a=new JSONArray();for(CortexCapabilityRegistry.Capability cap:CortexCapabilityRegistry.all()){try{CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(c,db,cap);JSONObject o=new JSONObject();o.put("number",cap.number);o.put("key",cap.key);o.put("title",cap.title);o.put("status",s.status);o.put("detail",s.detail);a.put(o);}catch(Throwable e){try{a.put(new JSONObject().put("number",cap.number).put("key",cap.key).put("error",error(e)));}catch(Exception ignored){}}}return a;}
    private static JSONArray access(Context c){JSONArray a=new JSONArray();try{for(AccessGateRegistry.Gate g:AccessGateRegistry.snapshot(c)){JSONObject o=new JSONObject();o.put("key",g.key);o.put("title",g.title);o.put("active",g.active);o.put("recommended",g.recommended);o.put("status",g.status);a.put(o);}}catch(Throwable e){try{a.put(new JSONObject().put("error",error(e)));}catch(Exception ignored){}}return a;}

    private static String saveDownloadCopy(Context c,File f){
        if(Build.VERSION.SDK_INT<29)return"";
        try{
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(MediaStore.Downloads.MIME_TYPE,"application/json");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Cortex");v.put(MediaStore.Downloads.IS_PENDING,1);
            Uri u=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)return"";
            try(InputStream in=new FileInputStream(f);OutputStream out=c.getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Downloads output unavailable");byte[] b=new byte[16384];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
            ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(u,done,null,null);return"Downloads/Cortex/"+f.getName();
        }catch(Throwable e){return"";}
    }

    private static void share(Activity a,File f){
        try{
            Uri u=FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",f);
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/json");send.putExtra(Intent.EXTRA_STREAM,u);send.putExtra(Intent.EXTRA_SUBJECT,"Cortex Debug Snapshot");send.setClipData(ClipData.newRawUri("Cortex debug package",u));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            List<ResolveInfo> targets=a.getPackageManager().queryIntentActivities(send,0);for(ResolveInfo r:targets)try{a.grantUriPermission(r.activityInfo.packageName,u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
            if(targets.isEmpty()){android.widget.Toast.makeText(a,"No share target found. The file is still saved in Downloads/Cortex.",android.widget.Toast.LENGTH_LONG).show();return;}
            a.startActivity(Intent.createChooser(send,"Share Cortex debug snapshot"));
        }catch(Throwable e){android.widget.Toast.makeText(a,"Share failed, but the debug file was saved to Downloads/Cortex: "+safeMessage(e),android.widget.Toast.LENGTH_LONG).show();}
    }

    private interface UnsafeString{String get()throws Exception;}
    private static final class FullExportSkipped extends IOException{FullExportSkipped(String message){super(message);}}
    private static String safe(UnsafeString x){try{return String.valueOf(x.get());}catch(Throwable e){return"ERROR: "+error(e);}}
    private static String error(Throwable e){if(e==null)return"unknown";String m=e.getMessage();return e.getClass().getSimpleName()+(m==null||m.trim().isEmpty()?"":": "+m);}
    private static String safeMessage(Throwable e){return e==null?"unknown error":error(e);}
    private static String stamp(){return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());}
}
