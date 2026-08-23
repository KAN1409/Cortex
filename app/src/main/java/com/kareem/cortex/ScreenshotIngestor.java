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
        for(DocumentFile f:images){if(seen++>=Math.max(1,max))break;try{String fp="shot:"+sha256(f.getUri().toString()+"|"+f.lastModified()+"|"+f.length());String ext=extension(f.getName(),f.getType());File out=new File(dir,fp.substring(5,21)+ext);if(!out.exists())copy(ctx,f.getUri(),out);String title=f.getName()==null?"Screenshot":f.getName();long id=db.insert("SCREENSHOT","screenshot-folder",title,"","Screenshots & Images","screenshot,auto_import",out.getAbsolutePath(),fp,"{\"source_uri\":\""+escape(f.getUri().toString())+"\",\"source_modified\":"+f.lastModified()+"}");if(id<0){duplicates++;if(out.exists())out.delete();}else if(id>0)imported++;}catch(Exception e){failed++;}}
        if(imported>0)AnalysisQueue.kick(ctx,db,null);return new Result(imported,duplicates,failed,images.size());
    }

    private static void collect(DocumentFile dir,ArrayList<DocumentFile> out,int depth){if(depth>2)return;DocumentFile[] xs=dir.listFiles();if(xs==null)return;for(DocumentFile f:xs){if(f.isDirectory())collect(f,out,depth+1);else if(f.isFile()&&isImage(f))out.add(f);}}
    private static boolean isImage(DocumentFile f){String m=f.getType();if(m!=null&&m.startsWith("image/"))return true;String n=f.getName();if(n==null)return false;n=n.toLowerCase(Locale.US);return n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".webp");}
    private static String extension(String name,String mime){String n=name==null?"":name.toLowerCase(Locale.US);if(n.endsWith(".png"))return ".png";if(n.endsWith(".webp"))return ".webp";if(n.endsWith(".jpeg"))return ".jpeg";if(mime!=null&&mime.contains("png"))return ".png";if(mime!=null&&mime.contains("webp"))return ".webp";return ".jpg";}
    private static void copy(Context c,Uri u,File out)throws Exception{InputStream in=c.getContentResolver().openInputStream(u);if(in==null)throw new FileNotFoundException("Cannot open screenshot");try(InputStream x=in;OutputStream y=new FileOutputStream(out)){byte[] b=new byte[64*1024];int n;while((n=x.read(b))>0)y.write(b,0,n);}}
    private static String sha256(String s)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] h=d.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte b:h)x.append(String.format(Locale.US,"%02x",b));return x.toString();}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    public static final class Result{public final int imported,duplicates,failed,totalFound;Result(int i,int d,int f,int t){imported=i;duplicates=d;failed=f;totalFound=t;}}
}
