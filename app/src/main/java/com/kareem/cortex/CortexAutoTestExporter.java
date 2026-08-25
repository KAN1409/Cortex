package com.kareem.cortex;

import android.app.Activity;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Runs the full verification suite and exports human-readable + machine-readable reports safely. */
public final class CortexAutoTestExporter {
    private CortexAutoTestExporter(){}

    public static void runAndShare(Activity a){
        android.widget.Toast.makeText(a,"Running complete Cortex verification…",android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{
            CortexAutoTestSuite.Report report=CortexAutoTestSuite.run(a.getApplicationContext());
            File md=null,json=null;String savedMd="",savedJson="";Throwable err=null;
            try{
                File dir=new File(a.getFilesDir(),"debug_exports/autotests");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create auto-test report directory");
                String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
                md=new File(dir,"CortexAutoTest_"+stamp+".md");json=new File(dir,"CortexAutoTest_"+stamp+".json");
                write(md,report.markdown());write(json,report.json().toString(2));
                savedMd=saveDownload(a,md,"text/markdown");savedJson=saveDownload(a,json,"application/json");
            }catch(Throwable e){err=e;}
            final File fmd=md,fjson=json;final String pmd=savedMd,pjson=savedJson;final Throwable failure=err;
            a.runOnUiThread(()->{
                if(fmd==null||!fmd.exists()){
                    android.widget.Toast.makeText(a,"Automatic verification finished but report export failed: "+safe(failure),android.widget.Toast.LENGTH_LONG).show();return;
                }
                String msg=(report.ok()?"Verification PASS":"Verification found failures")+" · "+report.pass+" pass · "+report.fail+" fail · "+report.blocked+" blocked · "+report.warn+" warn";
                if(!pmd.isEmpty())msg+="\nSaved: "+pmd;
                android.widget.Toast.makeText(a,msg,android.widget.Toast.LENGTH_LONG).show();
                share(a,fmd,fjson);
            });
        },"CortexAutoVerification").start();
    }

    private static void write(File f,String text)throws Exception{try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8),16384)){w.write(text==null?"":text);}}

    private static String saveDownload(Context c,File f,String mime){
        if(Build.VERSION.SDK_INT<29)return"";
        try{
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(MediaStore.Downloads.MIME_TYPE,mime);v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Cortex/AutoTests");v.put(MediaStore.Downloads.IS_PENDING,1);
            Uri u=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)return"";
            try(InputStream in=new FileInputStream(f);OutputStream out=c.getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Downloads output unavailable");byte[] b=new byte[16384];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
            ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(u,done,null,null);return"Downloads/Cortex/AutoTests/"+f.getName();
        }catch(Throwable ignored){return"";}
    }

    private static void share(Activity a,File md,File json){
        try{
            Uri mdUri=FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",md);Uri jsonUri=json!=null&&json.exists()?FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",json):null;
            ArrayList<Uri> uris=new ArrayList<>();uris.add(mdUri);if(jsonUri!=null)uris.add(jsonUri);
            Intent send=new Intent(uris.size()>1?Intent.ACTION_SEND_MULTIPLE:Intent.ACTION_SEND);send.setType("*/*");send.putExtra(Intent.EXTRA_SUBJECT,"Cortex Automated Verification Report");send.putExtra(Intent.EXTRA_TEXT,"Cortex complete automated verification report. Markdown is the human-readable report; JSON contains the same structured results.");if(uris.size()>1)send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);else send.putExtra(Intent.EXTRA_STREAM,mdUri);send.setClipData(ClipData.newRawUri("Cortex auto-test report",mdUri));if(jsonUri!=null)send.getClipData().addItem(new ClipData.Item(jsonUri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            for(ResolveInfo r:a.getPackageManager().queryIntentActivities(send,0))for(Uri u:uris)try{a.grantUriPermission(r.activityInfo.packageName,u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
            a.startActivity(Intent.createChooser(send,"Share Cortex verification report"));
        }catch(Throwable e){android.widget.Toast.makeText(a,"Report is saved in Downloads/Cortex/AutoTests. Share failed: "+safe(e),android.widget.Toast.LENGTH_LONG).show();}
    }

    private static String safe(Throwable e){if(e==null)return"unknown";String m=e.getMessage();return e.getClass().getSimpleName()+(m==null||m.isEmpty()?"":": "+m);}
}
