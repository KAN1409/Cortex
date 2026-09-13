package com.kareem.cortex;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Localized reference-inspired lime energy ribbons for hero surfaces only. Never tints the full app canvas. */
public final class CortexEnergyRibbonView extends View {
    private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG),core=new Paint(Paint.ANTI_ALIAS_FLAG),gold=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p1=new Path(),p2=new Path(),p3=new Path();private ValueAnimator animator;private float phase;
    public CortexEnergyRibbonView(Context c){super(c);setLayerType(LAYER_TYPE_SOFTWARE,null);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);glow.setStyle(Paint.Style.STROKE);core.setStyle(Paint.Style.STROKE);gold.setStyle(Paint.Style.STROKE);glow.setStrokeCap(Paint.Cap.ROUND);core.setStrokeCap(Paint.Cap.ROUND);gold.setStrokeCap(Paint.Cap.ROUND);start();}
    private boolean motionAllowed(){try{return Settings.Global.getFloat(getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;}catch(Throwable ignored){return true;}}
    private void start(){if(!motionAllowed())return;animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(9000);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());animator.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();invalidate();});animator.start();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();animator=null;super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;float d=getResources().getDisplayMetrics().density;float drift=(float)Math.sin(phase*Math.PI*2.0)*h*.025f;
        glow.setStrokeWidth(15*d);glow.setColor(Color.argb(54,190,221,82));glow.setShadowLayer(18*d,0,0,Color.argb(105,178,233,58));
        core.setStrokeWidth(2.2f*d);core.setColor(Color.argb(214,193,232,82));core.setShadowLayer(5*d,0,0,Color.argb(150,190,221,82));
        gold.setStrokeWidth(1.1f*d);gold.setColor(Color.argb(125,240,184,56));gold.setShadowLayer(3*d,0,0,Color.argb(70,240,184,56));
        p1.reset();p1.moveTo(w*.55f,h*.92f);p1.cubicTo(w*.73f,h*.80f+drift,w*.98f,h*.67f-drift,w*.81f,h*.14f);p1.cubicTo(w*.73f,-h*.02f,w*.86f,h*.01f,w*.97f,h*.02f);
        p2.reset();p2.moveTo(w*.66f,h*.96f);p2.cubicTo(w*.47f,h*.69f-drift,w*.70f,h*.53f+drift,w*.89f,h*.40f);p2.cubicTo(w*1.03f,h*.31f,w*.94f,h*.18f,w*.85f,h*.11f);
        p3.reset();p3.moveTo(w*.54f,h*.76f);p3.cubicTo(w*.77f,h*.73f,w*.85f,h*.57f,w*.77f,h*.38f);p3.cubicTo(w*.69f,h*.22f,w*.77f,h*.13f,w*.93f,h*.08f);
        c.drawPath(p1,glow);c.drawPath(p2,glow);c.drawPath(p3,glow);c.drawPath(p1,core);c.drawPath(p2,core);c.drawPath(p3,core);c.drawPath(p2,gold);
        Paint haze=new Paint(Paint.ANTI_ALIAS_FLAG);haze.setShader(new RadialGradient(w*.82f,h*.35f,Math.max(w,h)*.28f,new int[]{Color.argb(30,190,221,82),Color.argb(8,240,184,56),Color.TRANSPARENT},new float[]{0,.46f,1f},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,haze);
    }
}
