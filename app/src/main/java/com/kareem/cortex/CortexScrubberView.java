package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Approved waveform-first playback control: matte, red/orange active audio, white playhead, no purple/glass. */
public final class CortexScrubberView extends View {
    public interface Listener { void onSeek(float fraction,boolean finished); }
    private final Paint bars=new Paint(Paint.ANTI_ALIAS_FLAG),active=new Paint(Paint.ANTI_ALIAS_FLAG),head=new Paint(Paint.ANTI_ALIAS_FLAG),track=new Paint(Paint.ANTI_ALIAS_FLAG),thumb=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;private float progress=0f;private boolean dragging=false;private Listener listener;

    public CortexScrubberView(Context c){this(c,null);}
    public CortexScrubberView(Context c,AttributeSet a){super(c,a);d=getResources().getDisplayMetrics().density;setLayerType(LAYER_TYPE_SOFTWARE,null);
        bars.setColor(Color.rgb(73,73,77));bars.setStrokeWidth(1.55f*d);bars.setStrokeCap(Paint.Cap.ROUND);
        active.setColor(CortexUi.RED);active.setStrokeWidth(1.75f*d);active.setStrokeCap(Paint.Cap.ROUND);
        head.setColor(Color.rgb(244,243,239));head.setStrokeWidth(1.2f*d);head.setStrokeCap(Paint.Cap.ROUND);head.setShadowLayer(4*d,0,0,Color.argb(105,255,255,255));
        track.setColor(Color.rgb(44,44,47));track.setStrokeWidth(3*d);track.setStrokeCap(Paint.Cap.ROUND);
        thumb.setColor(CortexUi.RED);thumb.setStyle(Paint.Style.FILL);thumb.setShadowLayer(5*d,0,2*d,Color.argb(135,0,0,0));
    }
    public void setListener(Listener l){listener=l;}public void setProgress(float p){if(dragging)return;progress=clamp(p);invalidate();}public float getProgress(){return progress;}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float pad=9*d,w=Math.max(1,getWidth()-2*pad),cy=getHeight()*.46f;int count=Math.max(28,Math.min(72,(int)(w/(5*d))));float dx=w/Math.max(1,count-1),xp=pad+w*progress;
        for(int i=0;i<count;i++){float x=pad+i*dx;float t=i/(float)Math.max(1,count-1);float wave=(float)(.24+.58*Math.abs(Math.sin(i*.63)+.38*Math.sin(i*1.77+.8))/.0+0);}
        // deterministic waveform envelope; kept separate for clarity and compile-safety.
        for(int i=0;i<count;i++){
            float x=pad+i*dx;float a=(float)Math.abs(Math.sin(i*.57)+.45*Math.sin(i*1.43+.7));a=Math.min(1f,.20f+a*.48f);float hh=Math.max(2*d,(getHeight()*.33f)*a);
            Paint p=(x<=xp)?active:bars;
            if(x<=xp&&xp-x<Math.max(14*d,dx*3)){float q=Math.max(0f,Math.min(1f,(xp-x)/Math.max(1f,16*d)));p.setColor(blend(Color.rgb(244,243,239),CortexUi.RED,q));}
            else if(p==active)p.setColor(CortexUi.RED);
            c.drawLine(x,cy-hh,x,cy+hh,p);
        }
        float y=getHeight()*.88f;c.drawLine(pad,y,pad+w,y,track);if(progress>0){Paint pr=new Paint(track);pr.setColor(CortexUi.RED);c.drawLine(pad,y,xp,y,pr);}c.drawLine(xp,Math.max(1*d,cy-getHeight()*.34f),xp,cy+getHeight()*.34f,head);c.drawCircle(xp,y,(dragging?5.5f:4f)*d,thumb);
    }

    private float fraction(float x){float pad=9*d,w=Math.max(1,getWidth()-2*pad);return clamp((x-pad)/w);}private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
    private static int blend(int a,int b,float q){q=Math.max(0f,Math.min(1f,q));float p=1f-q;return Color.rgb((int)(Color.red(a)*p+Color.red(b)*q),(int)(Color.green(a)*p+Color.green(b)*q),(int)(Color.blue(a)*p+Color.blue(b)*q));}
    @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:dragging=true;progress=fraction(e.getX());invalidate();if(listener!=null)listener.onSeek(progress,false);return true;case MotionEvent.ACTION_MOVE:progress=fraction(e.getX());invalidate();if(listener!=null)listener.onSeek(progress,false);return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:progress=fraction(e.getX());dragging=false;invalidate();if(listener!=null)listener.onSeek(progress,true);performClick();return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
