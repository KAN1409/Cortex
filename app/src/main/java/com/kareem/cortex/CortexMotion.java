package com.kareem.cortex;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Shared premium motion primitives. Motion is decorative only; state never depends on animation. */
public final class CortexMotion {
    private CortexMotion(){}

    private static final Interpolator EMPHASIZED = Build.VERSION.SDK_INT>=21
            ? new PathInterpolator(.16f,1f,.30f,1f) : null;
    private static final Interpolator SOFT = Build.VERSION.SDK_INT>=21
            ? new PathInterpolator(.20f,.80f,.20f,1f) : null;

    public static boolean enabled(){
        return Build.VERSION.SDK_INT<26 || ValueAnimator.areAnimatorsEnabled();
    }

    public static void enterSheet(Activity a, View v){
        if(v==null)return;
        if(!enabled()){v.setAlpha(1f);v.setTranslationY(0f);v.setScaleX(1f);v.setScaleY(1f);return;}
        v.setAlpha(0f);
        v.setTranslationY(CortexUi.dp(a,54));
        v.setScaleX(.975f);v.setScaleY(.975f);
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(520).setInterpolator(EMPHASIZED).start();
    }

    public static void enter(Activity a, View v, int index){
        if(v==null)return;
        if(!enabled()){v.setAlpha(1f);v.setTranslationY(0f);return;}
        v.setAlpha(0f);
        v.setTranslationY(CortexUi.dp(a,22));
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.max(0,index)*52L)
                .setDuration(420)
                .setInterpolator(EMPHASIZED).start();
    }

    public static void stagger(Activity a, ViewGroup group){
        if(group==null)return;
        for(int i=0;i<group.getChildCount();i++)enter(a,group.getChildAt(i),i);
    }

    public static void pulse(View v){
        if(v==null||!enabled())return;
        v.animate().cancel();
        v.animate().scaleX(.965f).scaleY(.965f).setDuration(80).setInterpolator(SOFT)
                .withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(EMPHASIZED).start())
                .start();
    }

    public static void reveal(View v){
        if(v==null)return;
        v.setVisibility(View.VISIBLE);
        if(!enabled()){v.setAlpha(1f);return;}
        v.setAlpha(0f);v.setScaleX(.985f);v.setScaleY(.985f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(EMPHASIZED).start();
    }

    public static void hide(View v, Runnable end){
        if(v==null){if(end!=null)end.run();return;}
        if(!enabled()){v.setVisibility(View.GONE);if(end!=null)end.run();return;}
        v.animate().alpha(0f).translationY(CortexUi.dp(v.getContext(),12)).setDuration(180)
                .setInterpolator(SOFT).withEndAction(()->{v.setVisibility(View.GONE);v.setAlpha(1f);v.setTranslationY(0f);if(end!=null)end.run();}).start();
    }
}
