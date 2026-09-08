package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.Locale;

/** Imports a user-selected whisper.cpp GGML model into Cortex private storage. */
public final class LocalAsrModelStore {
    public static final String MODEL_FILENAME="ggml-cortex-local.bin";
    private static final long MIN_BYTES=20L*1024L*1024L;
    private static final String PREF="cortex_local_asr_model";
    private LocalAsrModelStore(){}
    public static File modelFile(Context c){return new File(new File(c.getFilesDir(),"models"),MODEL_FILENAME);}
    public static boolean ready(Context c){File f=modelFile(c);return f.exists()&&f.length()>=MIN_BYTES&&hasGgmlMagic(f);}
    public static String statusText(Context c){File f=modelFile(c);if(!ready(c))return"Local Whisper model: not imported";String n=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("source_name",MODEL_FILENAME);return String.format(Locale.US,"Local Whisper model: %s · %.1f MB · ready",n,f.length()/1048576.0);}
    public static File importModel(Context c,Uri uri)throws Exception{
        if(uri==null)throw new IllegalArgumentException("No model selected");Context app=c.getApplicationContext();ContentResolver cr=app.getContentResolver();String source=queryName(cr,uri);File dir=new File(app.getFilesDir(),"models");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create model directory");File tmp=new File(dir,MODEL_FILENAME+".importing"),dst=modelFile(app);if(tmp.exists())tmp.delete();long n=0;
        try(InputStream in=cr.openInputStream(uri);FileOutputStream out=new FileOutputStream(tmp)){if(in==null)throw new IOException("Could not open model");byte[]b=new byte[1024*1024];for(int r;(r=in.read(b))>0;){out.write(b,0,r);n+=r;}out.getFD().sync();}
        if(n<MIN_BYTES||!hasGgmlMagic(tmp)){tmp.delete();throw new IllegalArgumentException("Selected file is not a supported GGML Whisper model");}if(dst.exists()&&!dst.delete()){tmp.delete();throw new IOException("Could not replace previous model");}if(!tmp.renameTo(dst)){try(InputStream in=new FileInputStream(tmp);FileOutputStream out=new FileOutputStream(dst)){byte[]b=new byte[1024*1024];for(int r;(r=in.read(b))>0;)out.write(b,0,r);out.getFD().sync();}tmp.delete();}
        app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("source_name",source).putLong("bytes",dst.length()).putLong("imported_at",System.currentTimeMillis()).apply();return dst;
    }
    private static boolean hasGgmlMagic(File f){try(FileInputStream in=new FileInputStream(f)){byte[]h=new byte[4];if(in.read(h)!=4)return false;return (h[0]=='l'&&h[1]=='m'&&h[2]=='g'&&h[3]=='g')||(h[0]=='g'&&h[1]=='g'&&h[2]=='m'&&h[3]=='l');}catch(Exception e){return false;}}
    private static String queryName(ContentResolver cr,Uri u){try(Cursor c=cr.query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String s=c.getString(0);if(s!=null&&!s.trim().isEmpty())return s;}}catch(Exception ignored){}return MODEL_FILENAME;}
}
