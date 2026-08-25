package com.kareem.cortex;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Central tactile language for Cortex. Respects the user's system haptic setting. */
public final class CortexHaptics {
    private CortexHaptics(){}

    public static void press(View v){perform(v,HapticFeedbackConstants.VIRTUAL_KEY);}
    public static void tick(View v){perform(v,HapticFeedbackConstants.CLOCK_TICK);}
    public static void confirm(View v){
        if(Build.VERSION.SDK_INT>=30)perform(v,HapticFeedbackConstants.CONFIRM);
        else perform(v,HapticFeedbackConstants.VIRTUAL_KEY);
    }
    public static void reject(View v){
        if(Build.VERSION.SDK_INT>=30)perform(v,HapticFeedbackConstants.REJECT);
        else perform(v,HapticFeedbackConstants.LONG_PRESS);
    }
    public static void longPress(View v){perform(v,HapticFeedbackConstants.LONG_PRESS);}

    private static void perform(View v,int constant){
        if(v==null||!v.isEnabled())return;
        try{v.performHapticFeedback(constant);}catch(Throwable ignored){}
    }
}
