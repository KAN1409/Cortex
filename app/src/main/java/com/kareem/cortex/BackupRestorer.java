package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** One-time signing migration restore. Restores cortex.db and original local attachments. */
public final class BackupRestorer {
    public static final class Result { public final int memories,attachments; Result(int m,int a){memories=m;attachments=a;} }
    private BackupRestorer(){}

    public static Result restore(Context ctx, Uri source) throws Exception {
        File work=new File(ctx.getCacheDir(),"cortex_restore_"+System.currentTimeMillis());
        if(!work.mkdirs())throw new IOException("Could not create restore workspace");
        File zipFile=new File(work,"backup.zip");
        try(InputStream in=ctx.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(zipFile)){
            if(in==null)throw new FileNotFoundException("Could not open backup");copy(in,out);
        }
        HashMap<Long,String> attachmentEntries=new HashMap<>();
        int expectedMemories=0;
        File restoredDb=new File(work,"cortex.db");
        try(ZipFile z=new ZipFile(zipFile)){
            ZipEntry manifestEntry=z.getEntry("manifest.json");
            ZipEntry dbEntry=z.getEntry("database/cortex.db");
            ZipEntry jsonEntry=z.getEntry("memories.jsonl");
            if(manifestEntry==null||dbEntry==null||jsonEntry==null)throw new IllegalArgumentException("Not a Cortex portable backup");
            JSONObject manifest=new JSONObject(readText(z,manifestEntry));
            if(!"CORTEX_BACKUP_V1".equals(manifest.optString("format")))throw new IllegalArgumentException("Unsupported Cortex backup format");
            expectedMemories=manifest.optInt("memory_count",0);
            try(InputStream in=z.getInputStream(dbEntry);OutputStream out=new FileOutputStream(restoredDb)){copy(in,out);}
            String jsonl=readText(z,jsonEntry);
            for(String line:jsonl.split("\\r?\\n")){
                if(line.trim().isEmpty())continue;
                JSONObject o=new JSONObject(line);long id=o.optLong("id",-1);String p=o.optString("attachment_in_backup","");
                if(id>0&&!p.isEmpty())attachmentEntries.put(id,p);
            }
        }
        if(restoredDb.length()<4096)throw new IllegalStateException("Backup database is incomplete");

        File dbFile=ctx.getDatabasePath("cortex.db");File parent=dbFile.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Could not create database directory");
        deleteQuietly(new File(dbFile.getAbsolutePath()+"-wal"));deleteQuietly(new File(dbFile.getAbsolutePath()+"-shm"));deleteQuietly(dbFile);
        try(InputStream in=new FileInputStream(restoredDb);OutputStream out=new FileOutputStream(dbFile)){copy(in,out);}

        int restoredAttachments=0;
        String dataRoot=new File(ctx.getApplicationInfo().dataDir).getCanonicalPath()+File.separator;
        try(ZipFile z=new ZipFile(zipFile);SQLiteDatabase db=SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            Cursor c=db.rawQuery("SELECT id,attachment_path FROM knowledge_items WHERE attachment_path IS NOT NULL AND attachment_path!=''",null);
            try{
                while(c.moveToNext()){
                    long id=c.getLong(0);String targetPath=c.getString(1);String entryName=attachmentEntries.get(id);
                    if(entryName==null||targetPath==null)continue;
                    File target=new File(targetPath);String canonical=target.getCanonicalPath();
                    if(!canonical.startsWith(dataRoot))continue;
                    ZipEntry e=z.getEntry(entryName);if(e==null||e.isDirectory())continue;
                    File dir=target.getParentFile();if(dir!=null&&!dir.exists()&&!dir.mkdirs())continue;
                    try(InputStream in=z.getInputStream(e);OutputStream out=new FileOutputStream(target)){copy(in,out);}restoredAttachments++;
                }
            }finally{c.close();}
        }
        int actual=countMemories(dbFile);
        if(expectedMemories>0&&actual==0)throw new IllegalStateException("Restored database contains no memories");
        deleteTree(work);
        return new Result(actual,restoredAttachments);
    }

    private static int countMemories(File dbFile){SQLiteDatabase db=null;Cursor c=null;try{db=SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);c=db.rawQuery("SELECT COUNT(*) FROM knowledge_items",null);return c.moveToFirst()?c.getInt(0):0;}finally{if(c!=null)c.close();if(db!=null)db.close();}}
    private static String readText(ZipFile z,ZipEntry e)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=z.getInputStream(e)){copy(in,out);}return out.toString("UTF-8");}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();}
    private static void deleteQuietly(File f){try{if(f.exists())f.delete();}catch(Exception ignored){}}
    private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] xs=f.listFiles();if(xs!=null)for(File x:xs)deleteTree(x);}f.delete();}
}
