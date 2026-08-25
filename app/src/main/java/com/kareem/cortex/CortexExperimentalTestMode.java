package com.kareem.cortex;

import android.content.Context;
import java.util.Locale;

/** Process-persistent guard used only while the explicit robot-user explorer is running. */
public final class CortexExperimentalTestMode {
    private CortexExperimentalTestMode(){}
    private static final String PREFS="cortex_experimental_test_mode",KEY="active";
    public static void set(Context c,boolean active){try{c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY,active).commit();}catch(Throwable ignored){}}
    public static boolean active(Context c){try{return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(KEY,false);}catch(Throwable ignored){return false;}}

    /** Conservative label classifier: exhaustive explorer still records the action but does not execute irreversible/external mutation. */
    public static boolean guardedLabel(String raw){
        String s=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);
        if(s.isEmpty())return false;
        String[] risky={"delete","remove","erase","clear data","reset","restore","uninstall","send","save to calendar","prepare draft","open dialer","message","email","call ","install","download model","remove local model","حذف","امسح","مسح","استرجاع","ريستور","إرسال","ابعت","اتصل","مكالمة","تثبيت"};
        for(String x:risky)if(s.contains(x))return true;
        return false;
    }
}
