package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class BackupExporter {
    private BackupExporter(){}
    public static int write(Context ctx,VaultDb db,Uri dest) throws Exception {
        Cursor cp=db.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)",null);try{if(cp.moveToFirst()){} }finally{cp.close();}
        int count=0;HashSet<String> added=new HashSet<>();
        try(OutputStream raw=ctx.getContentResolver().openOutputStream(dest,"w");ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(raw))){
            putText(z,"README.txt","Cortex v1.0 portable backup\nContains cortex.db, memories.jsonl and local attachments.\nKeep this archive private: it may contain personal data.\n");
            File dbFile=ctx.getDatabasePath("cortex.db");if(dbFile.exists())putFile(z,"database/cortex.db",dbFile);
            StringBuilder jsonl=new StringBuilder();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,type,source,title,raw_text,extracted_text,summary,category,tags,attachment_path,status,metadata_json,created_at,updated_at FROM knowledge_items ORDER BY created_at ASC",null);
            while(c.moveToNext()){
                long id=c.getLong(0);String path=nz(c.getString(9)),zipPath="";File f=path.isEmpty()?null:new File(path);
                if(f!=null&&f.exists()){zipPath="attachments/"+id+"_"+safe(f.getName());if(added.add(path))putFile(z,zipPath,f);}
                JSONObject o=new JSONObject();o.put("id",id);o.put("type",nz(c.getString(1)));o.put("source",nz(c.getString(2)));o.put("title",nz(c.getString(3)));o.put("raw_text",nz(c.getString(4)));o.put("extracted_text",nz(c.getString(5)));o.put("summary",nz(c.getString(6)));o.put("category",nz(c.getString(7)));o.put("tags",nz(c.getString(8)));o.put("attachment_in_backup",zipPath);o.put("status",nz(c.getString(10)));o.put("metadata_json",nz(c.getString(11)));o.put("created_at",c.getLong(12));o.put("updated_at",c.getLong(13));jsonl.append(o.toString()).append('\n');count++;
            }c.close();putText(z,"memories.jsonl",jsonl.toString());
            JSONObject manifest=new JSONObject();manifest.put("format","CORTEX_BACKUP_V1");manifest.put("exported_at",System.currentTimeMillis());manifest.put("memory_count",count);manifest.put("attachment_count",added.size());putText(z,"manifest.json",manifest.toString(2));
        }return count;
    }
    private static void putText(ZipOutputStream z,String name,String text)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes("UTF-8"));z.closeEntry();}
    private static void putFile(ZipOutputStream z,String name,File f)throws Exception{z.putNextEntry(new ZipEntry(name));try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();}
    private static String safe(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}private static String nz(String s){return s==null?"":s;}
}
