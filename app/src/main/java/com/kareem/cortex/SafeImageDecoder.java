package com.kareem.cortex;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.File;

/** Memory-bounded image decoding shared by OCR paths. Never decode a camera image at full size. */
public final class SafeImageDecoder {
    private SafeImageDecoder(){}

    public static Bitmap decode(File file,int maxLongEdge,long maxPixels){
        if(file==null||!file.exists()||file.length()<=0)return null;
        Bitmap bmp=null;
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)return null;

            int sample=1;
            while(sample<64){
                long w=Math.max(1,bounds.outWidth/sample);
                long h=Math.max(1,bounds.outHeight/sample);
                if(Math.max(w,h)<=Math.max(512,maxLongEdge)&&w*h<=Math.max(1_000_000L,maxPixels))break;
                sample*=2;
            }

            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inSampleSize=Math.max(1,sample);
            opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
            opts.inDither=false;
            bmp=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
            if(bmp==null)return null;

            int rotation=rotation(file);
            if(rotation==0)return bmp;
            Matrix m=new Matrix();m.postRotate(rotation);
            Bitmap rotated=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true);
            if(rotated!=bmp&&!bmp.isRecycled())bmp.recycle();
            return rotated;
        }catch(Throwable t){
            try{if(bmp!=null&&!bmp.isRecycled())bmp.recycle();}catch(Throwable ignored){}
            return null;
        }
    }

    private static int rotation(File file){
        try{
            ExifInterface exif=new ExifInterface(file.getAbsolutePath());
            int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            if(orientation==ExifInterface.ORIENTATION_ROTATE_90)return 90;
            if(orientation==ExifInterface.ORIENTATION_ROTATE_180)return 180;
            if(orientation==ExifInterface.ORIENTATION_ROTATE_270)return 270;
        }catch(Throwable ignored){}
        return 0;
    }
}
