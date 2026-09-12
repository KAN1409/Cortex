package com.kareem.cortex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight real PCM/WAV overview. Samples the recorded 16-bit mono file; it never invents transcript/evidence. */
public final class CortexWaveformView extends View {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-waveform");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private final Paint idle=new Paint(Paint.ANTI_ALIAS_FLAG),played=new Paint(Paint.ANTI_ALIAS_FLAG);private volatile float[] bars;private float progress;private int accent=CortexUi.LIME;private String path="";
    public CortexWaveformView(Context c){super(c);idle.setStrokeCap(Paint.Cap.ROUND);played.setStrokeCap(Paint.Cap.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void setAccent(int c){accent=c;invalidate();}public void setProgress(float p){progress=Math.max(0,Math.min(1,p));invalidate();}
    public void setAudioPath(String p){String x=p==null?"":p;if(x.equals(path))return;path=x;bars=null;invalidate();if(x.isEmpty())return;IO.execute(()->{float[] data=readBars(x,54);post(()->{if(x.equals(path)){bars=data;invalidate();}});});}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;float d=getResources().getDisplayMetrics().density;idle.setStrokeWidth(Math.max(1.5f*d,w/180f));played.setStrokeWidth(idle.getStrokeWidth());idle.setColor(Color.argb(90,190,198,184));played.setColor(accent);float[] a=bars;if(a==null||a.length==0)a=fallback();float gap=w/(a.length+1f),mid=h*.5f;for(int i=0;i<a.length;i++){float x=gap*(i+1),amp=Math.max(h*.08f,a[i]*h*.43f);Paint p=(i/(float)Math.max(1,a.length-1))<=progress?played:idle;c.drawLine(x,mid-amp,x,mid+amp,p);}}
    private float[] fallback(){float[] x=new float[42];for(int i=0;i<x.length;i++)x[i]=.10f+.12f*(float)Math.abs(Math.sin(i*.73));return x;}
    private static float[] readBars(String path,int n){float[] out=new float[n];RandomAccessFile f=null;try{File file=new File(path);if(!file.isFile()||file.length()<48)return out;f=new RandomAccessFile(file,"r");long dataStart=44,data=Math.max(0,f.length()-dataStart);if(data<2)return out;byte[] buf=new byte[256];for(int i=0;i<n;i++){long center=dataStart+(data*i/Math.max(1,n));long pos=Math.max(dataStart,Math.min(dataStart+data-2,center));pos-=pos%2;f.seek(pos);int got=f.read(buf,0,(int)Math.min(buf.length,dataStart+data-pos));long sum=0;int samples=0;for(int j=0;j+1<got;j+=2){int v=(short)((buf[j]&255)|((buf[j+1]&255)<<8));sum+=Math.abs(v);samples++;}out[i]=samples==0?.08f:Math.min(1f,(sum/(float)samples)/12000f);if(out[i]<.06f)out[i]=.06f;}return out;}catch(Throwable ignored){return out;}finally{if(f!=null)try{f.close();}catch(Throwable ignored){}}}
    public static long durationMs(String path){RandomAccessFile f=null;try{File file=new File(path==null?"":path);if(!file.isFile()||file.length()<44)return 0;f=new RandomAccessFile(file,"r");f.seek(24);long sampleRate=le32(f);f.seek(34);long bits=le16(f);f.seek(22);long channels=le16(f);long bytes=Math.max(0,file.length()-44);long rate=sampleRate*Math.max(1,channels)*Math.max(8,bits)/8L;return rate<=0?0:(bytes*1000L/rate);}catch(Throwable ignored){return 0;}finally{if(f!=null)try{f.close();}catch(Throwable ignored){}}}
    private static long le16(RandomAccessFile f)throws Exception{return(f.read()&255)|((f.read()&255)<<8);}private static long le32(RandomAccessFile f)throws Exception{return(f.read()&255)|((f.read()&255)<<8)|((f.read()&255)<<16)|((long)(f.read()&255)<<24);}
}
