package com.kareem.cortex;

import android.content.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcelable;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class ShareImporter {
    private final Context ctx; private final VaultDb db;
    public ShareImporter(Context c,VaultDb d){ctx=c;db=d;}

    public int importIntent(Intent i){
        if(i==null)return 0;String action=i.getAction(),mime=i.getType();int n=0;
        if(Intent.ACTION_SEND.equals(action)){
            if(mime!=null&&mime.startsWith("image/")){Uri u=readUri(i);if(u!=null&&saveImage(u,mime)>0)n++;}
            else{String text=i.getStringExtra(Intent.EXTRA_TEXT);if(text!=null&&!text.trim().isEmpty()&&saveText(text,mime)>0)n++;}
        }else if(Intent.ACTION_SEND_MULTIPLE.equals(action)&&mime!=null&&mime.startsWith("image/")){
            ArrayList<Uri> uris=i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(uris!=null)for(Uri u:uris)if(saveImage(u,mime)>0)n++;
        }
        return n;
    }
    private Uri readUri(Intent i){Parcelable p=i.getParcelableExtra(Intent.EXTRA_STREAM);return p instanceof Uri?(Uri)p:null;}
    private long saveText(String text,String mime){String cat=AutoClassifier.category(text,mime);return db.insert("TEXT","android_share",AutoClassifier.title(text,mime),text,cat,AutoClassifier.tags(text,cat),"",Fingerprint.text(text),"{}");}
    private long saveImage(Uri uri,String mime){
        String local=copy(uri,mime);if(local.isEmpty())return 0;String cat=AutoClassifier.category("",mime);String meta=imageMeta(local,mime);
        long id=db.insert("SCREENSHOT","android_share",AutoClassifier.title("",mime),"",cat,"screenshot,image",local,Fingerprint.file(local),meta);
        if(id<0)new File(local).delete();return id;
    }
    private String imageMeta(String path,String mime){
        try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);JSONObject j=new JSONObject();j.put("mime",mime==null?"":mime);j.put("width",o.outWidth);j.put("height",o.outHeight);j.put("bytes",new File(path).length());return j.toString();}catch(Exception e){return "{}";}
    }
    private String copy(Uri uri,String mime){
        try{File dir=new File(ctx.getFilesDir(),"imports");if(!dir.exists())dir.mkdirs();String ext=(mime!=null&&mime.contains("png"))?".png":".jpg";File out=new File(dir,UUID.randomUUID()+ext);try(InputStream in=ctx.getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){byte[] b=new byte[8192];int r;while((r=in.read(b))!=-1)os.write(b,0,r);}return out.getAbsolutePath();}catch(Exception e){return "";}
    }
}
