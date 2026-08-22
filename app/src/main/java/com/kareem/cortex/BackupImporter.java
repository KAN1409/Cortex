package com.kareem.cortex;

import android.content.*;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class BackupImporter {
    private BackupImporter(){}
    public static int restore(Context ctx,VaultDb db,Uri src)throws Exception{
        File tmp=new File(ctx.getCacheDir(),"cortex_restore_"+System.currentTimeMillis());tmp.mkdirs();File memories=null;HashMap<String,File> attachments=new HashMap<>();
        try(InputStream raw=ctx.getContentResolver().openInputStream(src);ZipInputStream z=new ZipInputStream(new BufferedInputStream(raw))){
            ZipEntry e;byte[] b=new byte[16384];while((e=z.getNextEntry())!=null){String name=e.getName();if(e.isDirectory()){z.closeEntry();continue;}File out=new File(tmp,safePath(name));File parent=out.getParentFile();if(parent!=null)parent.mkdirs();try(OutputStream os=new FileOutputStream(out)){int n;while((n=z.read(b))!=-1)os.write(b,0,n);}if("memories.jsonl".equals(name))memories=out;else if(name.startsWith("attachments/"))attachments.put(name,out);z.closeEntry();}
        }
        if(memories==null||!memories.exists())throw new IOException("Not a Cortex backup: memories.jsonl missing");
        int count=0;try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(memories),"UTF-8"))){String line;while((line=r.readLine())!=null){line=line.trim();if(line.isEmpty())continue;JSONObject o=new JSONObject(line);String raw=o.optString("raw_text",""),ex=o.optString("extracted_text",""),title=o.optString("title","Restored memory"),att=o.optString("attachment_in_backup","");String local="";if(!att.isEmpty()&&attachments.containsKey(att)){File srcFile=attachments.get(att);File dir=new File(ctx.getFilesDir(),"restored");dir.mkdirs();File dst=new File(dir,UUID.randomUUID()+"_"+srcFile.getName());copy(srcFile,dst);local=dst.getAbsolutePath();}
                String fp=!local.isEmpty()?Fingerprint.file(local):Fingerprint.text((raw.isEmpty()?ex:raw)+"|"+title);long id=db.insert(o.optString("type","TEXT"),"backup_restore",title,raw,o.optString("category","Notes"),o.optString("tags","restored"),local,fp,o.optString("metadata_json","{}"));if(id<0)continue;ContentValues v=new ContentValues();v.put("extracted_text",ex);v.put("summary",o.optString("summary",""));v.put("status",o.optString("status","analyzed"));v.put("created_at",o.optLong("created_at",System.currentTimeMillis()));v.put("updated_at",o.optLong("updated_at",System.currentTimeMillis()));db.getWritableDatabase().update("knowledge_items",v,"id=?",new String[]{String.valueOf(id)});count++;}
        }delete(tmp);return count;
    }
    private static void copy(File a,File b)throws Exception{try(InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b)){byte[] x=new byte[16384];int n;while((n=in.read(x))!=-1)out.write(x,0,n);}}
    private static String safePath(String s){return s.replace("..","_").replace('\\','/');}
    private static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] xs=f.listFiles();if(xs!=null)for(File x:xs)delete(x);}f.delete();}
}
