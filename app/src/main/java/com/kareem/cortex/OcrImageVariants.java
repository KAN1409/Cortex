package com.kareem.cortex;

import android.graphics.*;
import java.io.*;

/** Creates temporary OCR-only image variants. Original archive image is never modified. */
public final class OcrImageVariants {
    private OcrImageVariants(){}
    public static File contrast(File src,File dir)throws Exception{return make(src,dir,"contrast",false,true);}
    public static File enlarged(File src,File dir)throws Exception{return make(src,dir,"enlarged",true,true);}

    private static File make(File src,File dir,String name,boolean enlarge,boolean highContrast)throws Exception{
        Bitmap in=BitmapFactory.decodeFile(src.getAbsolutePath());if(in==null)throw new IOException("Cannot decode OCR image");
        Bitmap work=in;
        if(enlarge){int max=3200;float scale=Math.min(2.0f,Math.min((float)max/in.getWidth(),(float)max/in.getHeight()));if(scale>1.05f)work=Bitmap.createScaledBitmap(in,Math.round(in.getWidth()*scale),Math.round(in.getHeight()*scale),true);}
        Bitmap out=Bitmap.createBitmap(work.getWidth(),work.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        if(highContrast){ColorMatrix m=new ColorMatrix();m.setSaturation(0f);ColorMatrix contrast=new ColorMatrix(new float[]{1.45f,0,0,0,-35,0,1.45f,0,0,-35,0,0,1.45f,0,-35,0,0,0,1,0});m.postConcat(contrast);p.setColorFilter(new ColorMatrixColorFilter(m));}
        c.drawBitmap(work,0,0,p);File f=new File(dir,"ocr_"+name+"_"+System.nanoTime()+".png");try(FileOutputStream o=new FileOutputStream(f)){out.compress(Bitmap.CompressFormat.PNG,100,o);}out.recycle();if(work!=in)work.recycle();in.recycle();return f;
    }
}
