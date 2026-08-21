package com.kareem.cortex;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves the original Cortex WAV unchanged so it can be shared as ASR ground truth. */
public final class VoiceExporter {
    private VoiceExporter(){}

    public static String export(Context ctx,File source,long itemId) throws Exception {
        if(source==null||!source.exists())throw new FileNotFoundException("Original voice file is missing");
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());
        String name="CortexVoice-"+stamp+"-"+itemId+".wav";
        if(Build.VERSION.SDK_INT>=29){
            ContentResolver cr=ctx.getContentResolver();
            ContentValues v=new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,name);
            v.put(MediaStore.Downloads.MIME_TYPE,"audio/wav");
            v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/CortexVoice");
            v.put(MediaStore.Downloads.IS_PENDING,1);
            Uri uri=cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(uri==null)throw new IOException("Could not create Downloads file");
            try(OutputStream out=cr.openOutputStream(uri);InputStream in=new FileInputStream(source)){
                if(out==null)throw new IOException("Could not open Downloads output");
                byte[] b=new byte[64*1024];int n;while((n=in.read(b))>0)out.write(b,0,n);
            }catch(Exception e){cr.delete(uri,null,null);throw e;}
            ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);cr.update(uri,done,null,null);
            return "Downloads/CortexVoice/"+name;
        }
        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"CortexVoice");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create Downloads/CortexVoice");
        File outFile=new File(dir,name);
        try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(outFile)){
            byte[] b=new byte[64*1024];int n;while((n=in.read(b))>0)out.write(b,0,n);
        }
        return outFile.getAbsolutePath();
    }
}
