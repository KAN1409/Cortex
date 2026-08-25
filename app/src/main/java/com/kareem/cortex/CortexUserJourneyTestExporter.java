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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs goal-driven Cortex user journeys and exports the problem report for review. */
public final class CortexUserJourneyTestExporter {
    private CortexUserJourneyTestExporter(){}

    public static void runAndShare(Activity a){
        android.widget.Toast.makeText(a,"Experimental user journeys started · real Cortex flows, isolated sandbox data",android.widget.Toast.LENGTH_LONG).show();
        new Thread(()->{
            File dir=null,md=null,json=null,zip=null;Throwable failure=null;CortexExperimentalUserSuite.Report report=null;
            try{
                String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
                dir=new File(a.getFilesDir(),"debug_exports/user_journeys/"+stamp);if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create user-journey session directory");
                report=CortexExperimentalUserSuite.run(a.getApplicationContext());
                md=new File(dir,"CortexExperimentalUserTest_"+stamp+".md");json=new File(dir,"CortexExperimentalUserTest_"+stamp+".json");zip=new File(dir,"CortexExperimentalUserTest_"+stamp+".zip");
                write(md,report.markdown());write(json,report.json().toString(2));zip(zip,md,json);
                saveDownload(a,md,"text/markdown");saveDownload(a,json,"application/json");saveDownload(a,zip,"application/zip");
            }catch(Throwable e){failure=e;}
            final File fmd=md,fjson=json,fzip=zip;final CortexExperimentalUserSuite.Report rr=report;final Throwable err=failure;
            a.runOnUiThread(()->{
                if(rr==null||fmd==null||!fmd.exists()){android.widget.Toast.makeText(a,"Experimental user test stopped before report: "+safe(err),android.widget.Toast.LENGTH_LONG).show();return;}
                String msg="User journeys complete · "+rr.journeys.size()+" journeys · "+rr.count(CortexExperimentalUserSuite.Status.PASS)+" passed · "+rr.count(CortexExperimentalUserSuite.Status.CONFIRMED_APP_BUG)+" app bugs · "+rr.count(CortexExperimentalUserSuite.Status.QUALITY_PROBLEM)+" quality issues · "+rr.count(CortexExperimentalUserSuite.Status.TEST_GAP)+" gaps";
                android.widget.Toast.makeText(a,msg+"\nSaved in Downloads/Cortex/AutoTests/UserJourneys",android.widget.Toast.LENGTH_LONG).show();share(a,fmd,fjson,fzip);
            });
        },"CortexUserJourneyTestExporter").start();
    }

    private static void write(File f,String s)throws Exception{try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8),16384)){w.write(s==null?"":s);}}
    private static void zip(File out,File...files)throws Exception{try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))){byte[] b=new byte[16384];for(File f:files){if(f==null||!f.exists())continue;z.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new BufferedInputStream(new FileInputStream(f))){for(int n;(n=in.read(b))!=-1;)z.write(b,0,n);}z.closeEntry();}}}
    private static String saveDownload(Context c,File f,String mime){if(Build.VERSION.SDK_INT<29||f==null||!f.exists())return"";try{ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(MediaStore.Downloads.MIME_TYPE,mime);v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Cortex/AutoTests/UserJourneys");v.put(MediaStore.Downloads.IS_PENDING,1);Uri u=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)return"";try(InputStream in=new FileInputStream(f);OutputStream out=c.getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Downloads output unavailable");byte[] b=new byte[16384];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(u,done,null,null);return"Downloads/Cortex/AutoTests/UserJourneys/"+f.getName();}catch(Throwable ignored){return"";}}
    private static void share(Activity a,File md,File json,File zip){try{ArrayList<Uri> uris=new ArrayList<>();for(File f:new File[]{md,json,zip})if(f!=null&&f.exists())uris.add(FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",f));if(uris.isEmpty())return;Intent send=new Intent(Intent.ACTION_SEND_MULTIPLE);send.setType("*/*");send.putExtra(Intent.EXTRA_SUBJECT,"Cortex Experimental User Test");send.putExtra(Intent.EXTRA_TEXT,"Goal-driven Cortex user journeys. Send the Markdown/ZIP back to ChatGPT to diagnose confirmed app problems.");send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);send.setClipData(ClipData.newRawUri("Cortex user journey report",uris.get(0)));for(int i=1;i<uris.size();i++)send.getClipData().addItem(new ClipData.Item(uris.get(i)));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);for(ResolveInfo r:a.getPackageManager().queryIntentActivities(send,0))for(Uri u:uris)try{a.grantUriPermission(r.activityInfo.packageName,u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}a.startActivity(Intent.createChooser(send,"Share Cortex experimental user test"));}catch(Throwable e){android.widget.Toast.makeText(a,"Reports are saved in Downloads/Cortex/AutoTests/UserJourneys. Share failed: "+safe(e),android.widget.Toast.LENGTH_LONG).show();}}
    private static String safe(Throwable e){if(e==null)return"unknown";String m=e.getMessage();return e.getClass().getSimpleName()+(m==null||m.isEmpty()?"":": "+m);}
}
