package com.kareem.cortex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ArabicOcr {
    public interface Callback { void done(String text,String status); }
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static final String MODEL_URL="https://github.com/tesseract-ocr/tessdata_fast/raw/4.1.0/ara.traineddata";
    private static final long MIN_MODEL_BYTES=500_000;
    private ArabicOcr(){}

    public static void recognize(Context context,File image,Callback cb){
        Context app=context.getApplicationContext();
        EXEC.execute(()->{
            Bitmap bmp=null;TessBaseAPI tess=null;
            try{
                File model=ensureModel(app);
                if(model==null){cb.done("","Arabic model unavailable");return;}
                bmp=BitmapFactory.decodeFile(image.getAbsolutePath());
                if(bmp==null){cb.done("","Arabic OCR could not decode image");return;}
                tess=new TessBaseAPI();
                String dataPath=new File(app.getFilesDir(),"tesseract").getAbsolutePath();
                if(!tess.init(dataPath,"ara")){cb.done("","Arabic OCR init failed");return;}
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                tess.setImage(bmp);
                String text=tess.getUTF8Text();
                cb.done(text==null?"":text.trim(),"Arabic OCR ready");
            }catch(Exception e){
                cb.done("","Arabic OCR unavailable: "+e.getClass().getSimpleName());
            }finally{
                try{if(tess!=null)tess.recycle();}catch(Exception ignored){}
                try{if(bmp!=null&&!bmp.isRecycled())bmp.recycle();}catch(Exception ignored){}
            }
        });
    }

    private static File ensureModel(Context ctx)throws Exception{
        File root=new File(ctx.getFilesDir(),"tesseract");File dir=new File(root,"tessdata");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create tessdata directory");
        File target=new File(dir,"ara.traineddata");if(target.exists()&&target.length()>=MIN_MODEL_BYTES)return target;
        File tmp=new File(dir,"ara.traineddata.part");if(tmp.exists())tmp.delete();
        HttpURLConnection c=(HttpURLConnection)new URL(MODEL_URL).openConnection();
        c.setConnectTimeout(12_000);c.setReadTimeout(45_000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Cortex/0.5 Android");
        int code=c.getResponseCode();if(code<200||code>=300){c.disconnect();throw new IOException("Model HTTP "+code);}
        try(InputStream in=new BufferedInputStream(c.getInputStream());OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){
            byte[] buf=new byte[32*1024];int n;long total=0;while((n=in.read(buf))!=-1){out.write(buf,0,n);total+=n;if(total>25_000_000)throw new IOException("Unexpected model size");}
        }finally{c.disconnect();}
        if(tmp.length()<MIN_MODEL_BYTES){tmp.delete();throw new IOException("Arabic model incomplete");}
        if(target.exists()&&!target.delete())throw new IOException("Cannot replace Arabic model");
        if(!tmp.renameTo(target)){copy(tmp,target);tmp.delete();}
        return target;
    }

    private static void copy(File a,File b)throws IOException{
        try(InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b)){byte[] buf=new byte[32*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
    }
}
