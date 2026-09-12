package com.kareem.cortex;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Very subtle non-interactive ambient glow. Respects battery saver and animator scale. */
public final class CortexAmbientOverlayView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private ValueAnimator animator;private float phase;
    public CortexAmbientOverlayView(Context c){super(c);setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);paint.setStyle(Paint.Style.FILL);}
    public void start(){if(!motionAllowed())return;animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(14000);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setRepeatMode(ValueAnimator.REVERSE);animator.setInterpolator(new LinearInterpolator());animator.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();invalidate();});animator.start();}
    public void stop(){if(animator!=null){animator.cancel();animator=null;}}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;float x=w*(.18f+.62f*phase),y=h*(.12f+.18f*(1f-phase));paint.setShader(new RadialGradient(x,y,Math.max(w,h)*.62f,new int[]{Color.argb(18,190,221,82),Color.argb(6,111,202,86),Color.TRANSPARENT},new float[]{0,.42f,1f},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);paint.setShader(null);}
    private boolean motionAllowed(){try{PowerManager pm=(PowerManager)getContext().getSystemService(Context.POWER_SERVICE);if(pm!=null&&pm.isPowerSaveMode())return false;float scale=Settings.Global.getFloat(getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f);return scale>0f;}catch(Throwable ignored){return true;}}
    public static void attach(Activity a){try{android.view.ViewGroup decor=(android.view.ViewGroup)a.getWindow().getDecorView();if(decor.findViewWithTag("cortex_ambient")!=null)return;CortexAmbientOverlayView v=new CortexAmbientOverlayView(a);v.setTag("cortex_ambient");decor.addView(v,new android.view.ViewGroup.LayoutParams(-1,-1));v.start();}catch(Throwable ignored){}}
}
