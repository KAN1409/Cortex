package com.kareem.cortex;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/**
 * Safety net for the whole application. Every Activity, including advanced and legacy
 * utility surfaces, receives the same system bars, typography controls and low-chrome
 * interaction treatment. Screen-specific layouts can then focus on information hierarchy.
 */
public final class CortexGlobalSkin {
    private CortexGlobalSkin(){}

    public static void apply(Activity a){
        if(a==null||a.isFinishing())return;
        CortexUi.applyWindow(a);
        View content=a.findViewById(android.R.id.content);
        if(content!=null)walk(a,content,0);
    }

    private static void walk(Activity a,View v,int depth){
        if(v==null||depth>40)return;
        if(Build.VERSION.SDK_INT>=21&&v.getElevation()>CortexUi.dp(a,1))v.setElevation(CortexUi.dp(a,1));

        if(v instanceof ScrollView){((ScrollView)v).setVerticalScrollBarEnabled(false);}
        if(v instanceof EditText){
            EditText e=(EditText)v;e.setTextColor(CortexUi.TEXT);e.setHintTextColor(CortexUi.FAINT);e.setTextSize(Math.max(14,e.getTextSize()/a.getResources().getDisplayMetrics().scaledDensity));
            if(e.getBackground()==null)e.setBackground(CortexUi.round(a,CortexUi.SURFACE,Color.TRANSPARENT,14));
        }else if(v instanceof Button){
            Button b=(Button)v;b.setAllCaps(false);b.setTextColor(CortexUi.TEXT);b.setBackground(CortexUi.round(a,CortexUi.SURFACE_2,Color.TRANSPARENT,13));
            if(Build.VERSION.SDK_INT>=21)b.setBackgroundTintList(null);
        }else if(v instanceof ProgressBar){
            ProgressBar p=(ProgressBar)v;
            if(Build.VERSION.SDK_INT>=21){p.setProgressTintList(ColorStateList.valueOf(CortexUi.LIME));p.setIndeterminateTintList(ColorStateList.valueOf(CortexUi.LIME));}
        }

        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++)walk(a,g.getChildAt(i),depth+1);
        }
    }
}
