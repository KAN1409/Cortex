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

/** Runs the experimental UI robot and exports a human-readable trace plus structured evidence. */
public final class CortexRobotTestExporter {
    private CortexRobotTestExporter(){}

    public static void runAndShare(Activity a){
        android.widget.Toast.makeText(a,"Robot user test started · Cortex data is isolated in a sandbox",android.widget.Toast.LENGTH_LONG).show();
        new Thread(()->{
            File session=null,journal=null,md=null,json=null,zip=null;Throwable failure=null;CortexRobotUserTest.Report report=null;
            try{
                String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());session=new File(a.getFilesDir(),"debug_exports/robot_tests/"+stamp);if(!session.exists()&&!session.mkdirs())throw new IOException("Could not create robot-test session directory");
                journal=new File(session,"journey.jsonl");final File jf=journal;
                report=CortexRobotUserTest.run(a,step->appendJournal(jf,step));
                md=new File(session,"CortexRobotUserTest_"+stamp+".md");json=new File(session,"CortexRobotUserTest_"+stamp+".json");zip=new File(session,"CortexRobotUserTest_"+stamp+".zip");
                write(md,report.markdown());write(json,report.json().toString(2));zip(zip,md,json,journal);
                saveDownload(a,md,"text/markdown");saveDownload(a,json,"application/json");saveDownload(a,zip,"application/zip");
            }catch(Throwable e){failure=e;}
            final File fmd=md,fjson=json,fzip=zip;final CortexRobotUserTest.Report rr=report;final Throwable err=failure;
            a.runOnUiThread(()->{
                if(rr==null||fmd==null||!fmd.exists()){
                    android.widget.Toast.makeText(a,"Robot test stopped before final report: "+safe(err)+". Check the saved journal/debug folder.",android.widget.Toast.LENGTH_LONG).show();return;
                }
                String msg=(rr.complete?"Robot exploration complete":"Robot exploration stopped with limits/errors")+" · "+rr.steps.size()+" steps · "+rr.screenCount+" screens · "+rr.failed+" failed · "+rr.guarded+" guarded";
                android.widget.Toast.makeText(a,msg+"\nSaved in Downloads/Cortex/AutoTests/RobotUser",android.widget.Toast.LENGTH_LONG).show();share(a,fmd,fjson,fzip);
            });
        },"CortexRobotTestExporter").start();
    }

    private static synchronized void appendJournal(File f,CortexRobotUserTest.Step step){try{if(f==null)return;CortexRobotReportSanitizer.sanitize(step);try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f,true),StandardCharsets.UTF_8),4096)){w.write(step.json().toString());w.write('\n');}}catch(Throwable ignored){}}
    private static void write(File f,String s)throws Exception{try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8),16384)){w.write(s==null?"":s);}}
    private static void zip(File out,File... files)throws Exception{try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))){byte[] b=new byte[16384];for(File f:files){if(f==null||!f.exists())continue;z.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new BufferedInputStream(new FileInputStream(f))){for(int n;(n=in.read(b))!=-1;)z.write(b,0,n);}z.closeEntry();}}}

    private static String saveDownload(Context c,File f,String mime){if(Build.VERSION.SDK_INT<29||f==null||!f.exists())return"";try{ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(MediaStore.Downloads.MIME_TYPE,mime);v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Cortex/AutoTests/RobotUser");v.put(MediaStore.Downloads.IS_PENDING,1);Uri u=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)return"";try(InputStream in=new FileInputStream(f);OutputStream out=c.getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Downloads output unavailable");byte[] b=new byte[16384];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(u,done,null,null);return"Downloads/Cortex/AutoTests/RobotUser/"+f.getName();}catch(Throwable ignored){return"";}}

    private static void share(Activity a,File md,File json,File zip){try{ArrayList<Uri> uris=new ArrayList<>();for(File f:new File[]{md,json,zip})if(f!=null&&f.exists())uris.add(FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",f));if(uris.isEmpty())return;Intent send=new Intent(uris.size()>1?Intent.ACTION_SEND_MULTIPLE:Intent.ACTION_SEND);send.setType("*/*");send.putExtra(Intent.EXTRA_SUBJECT,"Cortex Robot User Test");send.putExtra(Intent.EXTRA_TEXT,"Experimental user-journey test. Send the Markdown/ZIP back to ChatGPT for analysis.");if(uris.size()>1)send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);else send.putExtra(Intent.EXTRA_STREAM,uris.get(0));send.setClipData(ClipData.newRawUri("Cortex robot report",uris.get(0)));for(int i=1;i<uris.size();i++)send.getClipData().addItem(new ClipData.Item(uris.get(i)));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);for(ResolveInfo r:a.getPackageManager().queryIntentActivities(send,0))for(Uri u:uris)try{a.grantUriPermission(r.activityInfo.packageName,u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}a.startActivity(Intent.createChooser(send,"Share Cortex robot-user report"));}catch(Throwable e){android.widget.Toast.makeText(a,"Reports are saved in Downloads/Cortex/AutoTests/RobotUser. Share failed: "+safe(e),android.widget.Toast.LENGTH_LONG).show();}}
    private static String safe(Throwable e){if(e==null)return"unknown";String m=e.getMessage();return e.getClass().getSimpleName()+(m==null||m.isEmpty()?"":": "+m);}
}
