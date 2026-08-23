package com.kareem.cortex;

import android.content.*;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public final class ScreenshotIngestor {
    private static final String PREF="cortex_screenshots",KEY_TREE="tree_uri";
    private ScreenshotIngestor(){}

    public static void saveTree(Context c,Uri uri){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_TREE,uri==null?"":uri.toString()).apply();}
    public static Uri tree(Context c){String s=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_TREE,"");return s==null||s.isEmpty()?null:Uri.parse(s);}
    public static String treeLabel(Context c){Uri u=tree(c);if(u==null)return "Not connected";DocumentFile d=DocumentFile.fromTreeUri(c,u);return d==null?u.toString():(d.getName()==null?"Screenshot folder":d.getName());}

    public static Result scan(Context ctx,VaultDb db,int max) throws Exception{
        Uri tree=tree(ctx);if(tree==null)throw new IllegalStateException("Choose the screenshot folder first");DocumentFile root=DocumentFile.fromTreeUri(ctx,tree);if(root==null||!root.exists())throw new IllegalStateException("Screenshot folder is unavailable");
        ArrayList<DocumentFile> images=new ArrayList<>();collect(root,images,0);images.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));int imported=0,duplicates=0,failed=0,seen=0;
        File dir=new File(ctx.getFilesDir(),"screenshot_vault");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create screenshot vault");
        for(DocumentFile f:images){
            if(seen++>=Math.max(1,max))break;
            File tmp=null;
            try{
                String fp="shot:"+sha256(f.getUri().toString()+"|"+f.lastModified()+"|"+f.length());
                long existing=findFingerprint(db,fp);
                if(existing>0){duplicates++;continue;}
                String ext=extension(f.getName(),f.getType());File out=new File(dir,fp.substring(5,21)+ext);
                tmp=new File(dir,fp.substring(5,21)+ext+".part");if(tmp.exists())tmp.delete();
                copy(ctx,f.getUri(),tmp);
                if(!tmp.exists()||tmp.length()<=0)throw new IOException("Screenshot copy produced an empty file");
                if(out.exists()&&!out.delete())throw new IOException("Could not replace screenshot vault file");
                if(!tmp.renameTo(out)){copyFile(tmp,out);tmp.delete();}
                if(!out.exists()||out.length()<=0)throw new IOException("Screenshot vault verification failed");
                String title=f.getName()==null?"Screenshot":f.getName();
                long id=db.insert("SCREENSHOT","screenshot-folder",title,"","Screenshots & Images","screenshot,auto_import",out.getAbsolutePath(),fp,"{\"source_uri\":\""+escape(f.getUri().toString())+"\",\"source_modified\":"+f.lastModified()+",\"source_bytes\":"+f.length()+"}");
                if(id<0){duplicates++;}
                else if(id>0)imported++;
            }catch(Exception e){failed++;if(tmp!=null&&tmp.exists())tmp.delete();}
        }
        if(imported>0)ScreenshotWorkScheduler.kick(ctx);return new Result(imported,duplicates,failed,images.size());
    }

    public static int repairMissingAttachments(Context ctx,VaultDb db,int max){
        Uri tree=tree(ctx);if(tree==null)return 0;DocumentFile root=DocumentFile.fromTreeUri(ctx,tree);if(root==null||!root.exists())return 0;
        HashMap<String,DocumentFile> byUri=new HashMap<>();ArrayList<DocumentFile> files=new ArrayList<>();collect(root,files,0);for(DocumentFile f:files)byUri.put(f.getUri().toString(),f);
        int repaired=0,checked=0;for(KnowledgeItem k:db.lexicalSearch("",5000)){
            if(checked++>=Math.max(1,max))break;if(!"SCREENSHOT".equals(k.type)||!"screenshot-folder".equals(k.source))continue;
            File target=k.attachmentPath==null?null:new File(k.attachmentPath);if(target!=null&&target.exists()&&target.length()>0)continue;
            String sourceUri=jsonValue(k.metadataJson,"source_uri");DocumentFile src=byUri.get(sourceUri);if(src==null)continue;
            try{File dir=new File(ctx.getFilesDir(),"screenshot_vault");if(!dir.exists())dir.mkdirs();File out=target!=null?target:new File(dir,k.fingerprint.substring(5,21)+extension(src.getName(),src.getType()));File tmp=new File(out.getAbsolutePath()+".repair");if(tmp.exists())tmp.delete();copy(ctx,src.getUri(),tmp);if(tmp.length()<=0)throw new IOException("empty repair");if(out.exists())out.delete();if(!tmp.renameTo(out)){copyFile(tmp,out);tmp.delete();}if(out.exists()&&out.length()>0){db.retry(k.id);repaired++;}}catch(Exception ignored){}
        }
        if(repaired>0)ScreenshotWorkScheduler.kick(ctx);return repaired;
    }

    private static long findFingerprint(VaultDb db,String fp){android.database.Cursor c=db.getReadableDatabase().query("knowledge_items",new String[]{"id"},"fingerprint=?",new String[]{fp},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static String jsonValue(String json,String key){if(json==null)return"";try{return new org.json.JSONObject(json).optString(key,"");}catch(Exception e){return"";}}
    private static void collect(DocumentFile dir,ArrayList<DocumentFile> out,int depth){if(depth>2)return;DocumentFile[] xs=dir.listFiles();if(xs==null)return;for(DocumentFile f:xs){if(f.isDirectory())collect(f,out,depth+1);else if(f.isFile()&&isImage(f))out.add(f);}}
    private static boolean isImage(DocumentFile f){String m=f.getType();if(m!=null&&m.startsWith("image/"))return true;String n=f.getName();if(n==null)return false;n=n.toLowerCase(Locale.US);return n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".webp");}
    private static String extension(String name,String mime){String n=name==null?"":name.toLowerCase(Locale.US);if(n.endsWith(".png"))return ".png";if(n.endsWith(".webp"))return ".webp";if(n.endsWith(".jpeg"))return ".jpeg";if(mime!=null&&mime.contains("png"))return ".png";if(mime!=null&&mime.contains("webp"))return ".webp";return ".jpg";}
    private static void copy(Context c,Uri u,File out)throws Exception{InputStream in=c.getContentResolver().openInputStream(u);if(in==null)throw new FileNotFoundException("Cannot open screenshot");try(InputStream x=in;OutputStream y=new BufferedOutputStream(new FileOutputStream(out))){byte[] b=new byte[128*1024];int n;while((n=x.read(b))>0)y.write(b,0,n);}}
    private static void copyFile(File a,File b)throws IOException{try(InputStream in=new BufferedInputStream(new FileInputStream(a));OutputStream out=new BufferedOutputStream(new FileOutputStream(b))){byte[] buf=new byte[128*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}}
    private static String sha256(String s)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] h=d.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte b:h)x.append(String.format(Locale.US,"%02x",b));return x.toString();}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    public static final class Result{public final int imported,duplicates,failed,totalFound;Result(int i,int d,int f,int t){imported=i;duplicates=d;failed=f;totalFound=t;}}
}
