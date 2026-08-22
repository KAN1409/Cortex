package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class ShareImporter {
    private final Context ctx;private final VaultDb db;public ShareImporter(Context c,VaultDb d){ctx=c;db=d;}
    public int importIntent(Intent i){
        if(i==null)return 0;String action=i.getAction(),mime=i.getType();int n=0;
        if(Intent.ACTION_SEND.equals(action)){
            Uri u=readUri(i);
            if(mime!=null&&mime.startsWith("image/")&&u!=null){if(saveImage(u,mime)>0)n++;}
            else if(mime!=null&&mime.startsWith("audio/")&&u!=null){if(saveAudio(u,mime)>0)n++;}
            else if(u!=null){if(saveFile(u,mime)>0)n++;}
            else{String text=i.getStringExtra(Intent.EXTRA_TEXT);if(text!=null&&!text.trim().isEmpty()&&saveText(text,mime)>0)n++;}
        }else if(Intent.ACTION_SEND_MULTIPLE.equals(action)){
            ArrayList<Uri> us=i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(us!=null)for(Uri u:us){long id=(mime!=null&&mime.startsWith("image/"))?saveImage(u,mime):(mime!=null&&mime.startsWith("audio/"))?saveAudio(u,mime):saveFile(u,mime);if(id>0)n++;}
        }
        return n;
    }
    public long importAudio(Uri uri,String mime){if(uri==null)return 0;return saveAudio(uri,mime==null?"audio/*":mime);}
    private Uri readUri(Intent i){Parcelable p=i.getParcelableExtra(Intent.EXTRA_STREAM);return p instanceof Uri?(Uri)p:null;}
    private long saveText(String text,String mime){String cat=AutoClassifier.category(text,mime);return db.insert("TEXT","android_share",AutoClassifier.title(text,mime),text,cat,AutoClassifier.tags(text,cat),"",Fingerprint.text(text),"{}");}
    private long saveImage(Uri uri,String mime){String name=displayName(uri,"image");String local=copy(uri,name);if(local.isEmpty())return 0;String cat=AutoClassifier.category("",mime);String meta=imageMeta(local,mime,name);long id=db.insert("SCREENSHOT","android_share",name,"",cat,"screenshot,image",local,Fingerprint.file(local),meta);if(id<0)new File(local).delete();return id;}
    private long saveAudio(Uri uri,String mime){String name=displayName(uri,"audio");String local=copy(uri,name);if(local.isEmpty())return 0;long id=db.insert("AUDIO","audio_import",name,"","Voice & Audio","voice,audio,transcript,imported",local,Fingerprint.file(local),fileMeta(local,mime,name));if(id<0)new File(local).delete();return id;}
    private long saveFile(Uri uri,String mime){String name=displayName(uri,"file");String local=copy(uri,name);if(local.isEmpty())return 0;long id=db.insert("FILE","android_share",name,name,"Files","file,attachment",local,Fingerprint.file(local),fileMeta(local,mime,name));if(id<0)new File(local).delete();return id;}
    private String imageMeta(String path,String mime,String name){try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);JSONObject j=new JSONObject(fileMeta(path,mime,name));j.put("width",o.outWidth);j.put("height",o.outHeight);return j.toString();}catch(Exception e){return "{}";}}
    private String fileMeta(String path,String mime,String name){try{JSONObject j=new JSONObject();j.put("mime",mime==null?"":mime);j.put("name",name);j.put("bytes",new File(path).length());j.put("imported_at",System.currentTimeMillis());return j.toString();}catch(Exception e){return "{}";}}
    private String displayName(Uri u,String fallback){try(Cursor c=ctx.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String x=c.getString(0);if(x!=null&&!x.trim().isEmpty())return x;}}catch(Exception ignored){}return fallback+"_"+System.currentTimeMillis();}
    private String copy(Uri uri,String name){try{File dir=new File(ctx.getFilesDir(),"imports");if(!dir.exists())dir.mkdirs();String safe=name.replaceAll("[^A-Za-z0-9._-]","_");if(safe.length()>80)safe=safe.substring(safe.length()-80);File out=new File(dir,UUID.randomUUID()+"_"+safe);try(InputStream in=ctx.getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){if(in==null)return "";byte[] b=new byte[8192];int r;while((r=in.read(b))!=-1)os.write(b,0,r);}return out.getAbsolutePath();}catch(Exception e){return "";}}
}
