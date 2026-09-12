package com.kareem.cortex;

import android.animation.*;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Calm Cortex motion language. Every animation respects reduced motion and battery saver. */
public final class CortexMotion {
    private static final long FAST=120, STATE=200, ENTER=300, HERO=460;
    private static final WeakHashMap<View,Animator> LOOPS=new WeakHashMap<>();
    private CortexMotion(){}

    public static boolean allowed(Context c){
        try{
            PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
            if(pm!=null&&pm.isPowerSaveMode())return false;
            return Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;
        }catch(Throwable ignored){return true;}
    }

    public static void enter(View v,int index){
        if(v==null||!allowed(v.getContext()))return;
        float d=v.getResources().getDisplayMetrics().density;
        v.animate().cancel();v.setAlpha(0f);v.setTranslationY(10f*d);v.setScaleX(.992f);v.setScaleY(.992f);
        v.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                .setStartDelay(Math.min(150,Math.max(0,index)*28L)).setDuration(ENTER)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    public static void hero(View v){
        if(v==null||!allowed(v.getContext()))return;
        float d=v.getResources().getDisplayMetrics().density;
        v.animate().cancel();v.setAlpha(0f);v.setTranslationY(14f*d);v.setScaleX(.985f);v.setScaleY(.985f);
        v.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(HERO)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    public static void revealRail(View v){
        if(v==null||!allowed(v.getContext()))return;
        v.animate().cancel();v.setScaleY(0f);v.setPivotY(0f);v.setAlpha(.2f);
        v.animate().scaleY(1f).alpha(1f).setDuration(360).setInterpolator(new DecelerateInterpolator(1.7f)).start();
    }

    public static void count(TextView v,long from,long to,String suffix){
        if(v==null)return;String tail=suffix==null?"":suffix;
        if(!allowed(v.getContext())||from==to){v.setText(to+tail);return;}
        ValueAnimator a=ValueAnimator.ofFloat(0f,1f);a.setDuration(Math.min(650,320+Math.abs(to-from)*8));
        a.setInterpolator(new DecelerateInterpolator(1.8f));a.addUpdateListener(x->{float q=(Float)x.getAnimatedValue();long n=Math.round(from+(to-from)*q);v.setText(n+tail);});a.start();
    }

    public static void softSwap(TextView v,CharSequence text){
        if(v==null)return;if(!allowed(v.getContext())){v.setText(text);return;}
        v.animate().cancel();v.animate().alpha(.30f).setDuration(FAST).withEndAction(()->{v.setText(text);v.animate().alpha(1f).setDuration(STATE).start();}).start();
    }

    public static void pulseOnce(View v){
        if(v==null||!allowed(v.getContext()))return;
        v.animate().cancel();v.setScaleX(.97f);v.setScaleY(.97f);v.setAlpha(.82f);
        v.animate().scaleX(1.025f).scaleY(1.025f).alpha(1f).setDuration(170).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(170).start()).start();
    }

    /** Only for genuinely live/processing indicators. */
    public static void breathe(View v){
        if(v==null||!allowed(v.getContext()))return;
        synchronized(LOOPS){if(LOOPS.containsKey(v))return;}
        ObjectAnimator alpha=ObjectAnimator.ofFloat(v,View.ALPHA,1f,.62f,1f);alpha.setDuration(2300);alpha.setRepeatCount(ValueAnimator.INFINITE);alpha.setInterpolator(new PathInterpolator(.4f,0f,.2f,1f));
        synchronized(LOOPS){LOOPS.put(v,alpha);}v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View x){if(allowed(x.getContext())&&!alpha.isStarted())alpha.start();}public void onViewDetachedFromWindow(View x){alpha.cancel();synchronized(LOOPS){LOOPS.remove(x);}}});
        if(v.isAttachedToWindow())alpha.start();
    }

    public static void focus(View v,boolean focused){
        if(v==null||!allowed(v.getContext()))return;
        v.animate().cancel();v.animate().scaleX(focused?1.012f:1f).scaleY(focused?1.012f:1f).setDuration(STATE).start();
    }

    public static void stagger(ViewGroup group){if(group==null)return;for(int i=0;i<group.getChildCount();i++)enter(group.getChildAt(i),i);}
    public static void haptic(View v,boolean strong){if(v==null)return;try{v.performHapticFeedback(strong?HapticFeedbackConstants.CONFIRM:HapticFeedbackConstants.CLOCK_TICK);}catch(Throwable ignored){}}
}
