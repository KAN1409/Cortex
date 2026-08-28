package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Builds one shareable JSON containing the exhaustive dump plus interpreted live pipeline state. */
public final class CortexIntensiveDiagnosticExporter {
    private CortexIntensiveDiagnosticExporter(){}

    public static void exportAndShare(Activity activity,VaultDb db){
        if(activity==null||db==null)return;
        android.widget.Toast.makeText(activity,"Building intensive Cortex diagnostic…",android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                File base=DebugExporter.build(activity,db);String raw=read(base);JSONObject root=new JSONObject(raw);
                root.put("runtime_pipeline_v2",CortexRuntimeDiagnosticV1.snapshot(activity.getApplicationContext(),db));
                root.put("cognitive_v2_invariants",CognitiveInvariantDiagnosticsV2.snapshot(activity.getApplicationContext(),db));
                root.put("diagnostic_mode","INTENSIVE_RUNTIME_AND_FULL_DATABASE");
                root.put("diagnostic_note","Regression tests are retained. This export exposes ASR, Cortex Relay ACK delivery, Local Qwen Cognitive V2 states/runs/derived intelligence, zero-blank-state acceptance, canonical V4 Situation/Pulse and autonomous Gemini scheduling/gating/failure state.");
                File dir=base.getParentFile();String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());File out=new File(dir,"CortexIntensiveDiagnostic_"+stamp+".json");try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out),StandardCharsets.UTF_8),65536)){w.write(root.toString(2));}try{base.delete();}catch(Throwable ignored){}activity.runOnUiThread(()->share(activity,out));
            }catch(Throwable e){activity.runOnUiThread(()->android.widget.Toast.makeText(activity,"Intensive diagnostic failed: "+safe(e),android.widget.Toast.LENGTH_LONG).show());}
        },"CortexIntensiveDiagnosticExport").start();
    }

    private static void share(Activity a,File f){try{Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",f);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/json");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);a.startActivity(Intent.createChooser(i,"Share Cortex intensive diagnostic"));}catch(Throwable e){android.widget.Toast.makeText(a,"Diagnostic saved but share failed: "+safe(e),android.widget.Toast.LENGTH_LONG).show();}}
    private static String read(File f)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}return out.toString("UTF-8");}
    private static String safe(Throwable e){if(e==null)return"unknown";String x=e.getMessage();return x==null||x.trim().isEmpty()?e.getClass().getSimpleName():x.trim();}
}
