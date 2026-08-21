package com.kareem.cortex;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** Imports a user-selected whisper.cpp GGML model into Cortex private storage. */
public final class LocalAsrModelStore {
    public static final String MODEL_FILENAME="ggml-codeswitch-medium-q8_0.bin";
    public static final long MIN_MODEL_BYTES=600_000_000L;
    private static final int GGML_MAGIC=0x67676d6c;
    private static final String PREF="cortex_local_asr_model";
    private LocalAsrModelStore(){}

    public static File modelFile(Context c){return new File(new File(c.getFilesDir(),"models"),MODEL_FILENAME);}

    public static boolean ready(Context c){
        File f=modelFile(c);
        return f.exists()&&f.isFile()&&f.length()>=MIN_MODEL_BYTES&&hasGgmlMagic(f);
    }

    public static String statusText(Context c){
        File f=modelFile(c);
        if(!ready(c))return "Model: not selected";
        String name=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("source_name",MODEL_FILENAME);
        return String.format(Locale.US,"Model: %s • %.1f MB • Ready",name,f.length()/1048576.0);
    }

    public static File importModel(Context c,Uri uri)throws Exception{
        if(uri==null)throw new IllegalArgumentException("No model file selected");
        Context app=c.getApplicationContext();ContentResolver cr=app.getContentResolver();
        String sourceName=queryName(cr,uri);long expected=querySize(cr,uri);
        if(expected>0&&expected<MIN_MODEL_BYTES)throw new IllegalArgumentException("Selected file is too small for the Medium q8_0 model ("+expected+" bytes)");
        File dir=new File(app.getFilesDir(),"models");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Could not create Cortex model directory");
        if(expected>0&&dir.getUsableSpace()<expected+100L*1024L*1024L)throw new IllegalStateException("Not enough free storage to import the local ASR model");
        File dst=modelFile(app),tmp=new File(dir,MODEL_FILENAME+".importing");if(tmp.exists())tmp.delete();
        long written=0;WhisperRuntimeState.beginModelImport(app,sourceName,expected);
        try(InputStream in=cr.openInputStream(uri);FileOutputStream out=new FileOutputStream(tmp,false)){
            if(in==null)throw new IllegalStateException("Could not open selected model file");
            byte[] buf=new byte[1024*1024];while(true){int n=in.read(buf);if(n<=0)break;out.write(buf,0,n);written+=n;WhisperRuntimeState.copyProgress(app,written,expected);}
            out.getFD().sync();
        }catch(Exception e){tmp.delete();WhisperRuntimeState.error(app,e);throw e;}
        if(written<MIN_MODEL_BYTES){tmp.delete();Exception e=new IllegalStateException("Local ASR model copy incomplete ("+written+" bytes)");WhisperRuntimeState.error(app,e);throw e;}
        if(expected>0&&written!=expected){tmp.delete();Exception e=new IllegalStateException("Local ASR model size mismatch ("+written+"/"+expected+" bytes)");WhisperRuntimeState.error(app,e);throw e;}
        if(!hasGgmlMagic(tmp)){tmp.delete();Exception e=new IllegalArgumentException("Selected file is not a valid GGML whisper.cpp model");WhisperRuntimeState.error(app,e);throw e;}
        if(dst.exists()&&!dst.delete()){tmp.delete();Exception e=new IllegalStateException("Could not replace previous local ASR model");WhisperRuntimeState.error(app,e);throw e;}
        if(!tmp.renameTo(dst)){try(InputStream in=new FileInputStream(tmp);FileOutputStream out=new FileOutputStream(dst,false)){byte[] b=new byte[1024*1024];while(true){int n=in.read(b);if(n<=0)break;out.write(b,0,n);}out.getFD().sync();}tmp.delete();}
        app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("source_name",sourceName).putLong("bytes",dst.length()).putLong("imported_at",System.currentTimeMillis()).apply();
        WhisperRuntimeState.modelReady(app,sourceName,dst.length());return dst;
    }

    /** whisper.cpp writes 0x67676d6c as a little-endian uint32, so file bytes are 'l','m','g','g'. */
    static boolean isGgmlHeader(byte[] h){
        if(h==null||h.length<4)return false;
        int magic=(h[0]&0xff)|((h[1]&0xff)<<8)|((h[2]&0xff)<<16)|((h[3]&0xff)<<24);
        return magic==GGML_MAGIC;
    }

    private static boolean hasGgmlMagic(File f){
        if(f==null||!f.exists()||f.length()<4)return false;
        try(FileInputStream in=new FileInputStream(f)){byte[] h=new byte[4];return in.read(h)==4&&isGgmlHeader(h);}catch(Exception e){return false;}
    }
    private static String queryName(ContentResolver cr,Uri u){
        try(Cursor cur=cr.query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cur!=null&&cur.moveToFirst()){String s=cur.getString(0);if(s!=null&&!s.trim().isEmpty())return s;}}catch(Exception ignored){}return MODEL_FILENAME;
    }
    private static long querySize(ContentResolver cr,Uri u){
        try(Cursor cur=cr.query(u,new String[]{OpenableColumns.SIZE},null,null,null)){if(cur!=null&&cur.moveToFirst()&&!cur.isNull(0))return cur.getLong(0);}catch(Exception ignored){}return -1L;
    }
}
