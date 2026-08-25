package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Minimal recessed progress track with tap/drag scrubbing. */
public final class CortexScrubberView extends View {
    public interface Listener { void onSeek(float fraction,boolean finished); }
    private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG),played=new Paint(Paint.ANTI_ALIAS_FLAG),thumb=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;private float progress=0f;private boolean dragging=false;private Listener listener;
    public CortexScrubberView(Context c){this(c,null);}public CortexScrubberView(Context c,AttributeSet a){super(c,a);d=getResources().getDisplayMetrics().density;setLayerType(LAYER_TYPE_SOFTWARE,null);track.setColor(Color.rgb(22,25,31));track.setStrokeWidth(6*d);track.setStrokeCap(Paint.Cap.ROUND);played.setColor(Color.rgb(255,42,36));played.setStrokeWidth(6*d);played.setStrokeCap(Paint.Cap.ROUND);glow.setColor(Color.rgb(255,42,36));glow.setStrokeWidth(7*d);glow.setStrokeCap(Paint.Cap.ROUND);glow.setShadowLayer(7*d,0,0,Color.argb(145,255,42,36));thumb.setColor(Color.rgb(255,55,47));thumb.setStyle(Paint.Style.FILL);}
    public void setListener(Listener l){listener=l;}public void setProgress(float p){if(dragging)return;progress=clamp(p);invalidate();}public float getProgress(){return progress;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float pad=9*d,y=getHeight()/2f,x0=pad,x1=Math.max(x0,getWidth()-pad),xp=x0+(x1-x0)*progress;c.drawLine(x0,y,x1,y,track);if(progress>0){c.drawLine(x0,y,xp,y,glow);c.drawLine(x0,y,xp,y,played);}if(dragging||progress>0)c.drawCircle(xp,y,(dragging?7:5)*d,thumb);}
    private float fraction(float x){float pad=9*d,w=Math.max(1,getWidth()-2*pad);return clamp((x-pad)/w);}private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
    @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:dragging=true;progress=fraction(e.getX());invalidate();if(listener!=null)listener.onSeek(progress,false);return true;case MotionEvent.ACTION_MOVE:progress=fraction(e.getX());invalidate();if(listener!=null)listener.onSeek(progress,false);return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:progress=fraction(e.getX());dragging=false;invalidate();if(listener!=null)listener.onSeek(progress,true);performClick();return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
