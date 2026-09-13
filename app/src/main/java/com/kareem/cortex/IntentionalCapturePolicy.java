package com.kareem.cortex;

import java.util.Locale;

/**
 * Presentation boundary for user-created capture libraries.
 *
 * This does not delete, reclassify, or weaken evidence. Passive observations remain in their
 * canonical/raw stores; they simply do not masquerade as things the user intentionally captured.
 */
public final class IntentionalCapturePolicy {
    private IntentionalCapturePolicy(){}

    public static boolean visibleInCapturedLibrary(KnowledgeItem k){
        if(k==null)return false;
        String source=n(k.source),type=n(k.type).toUpperCase(Locale.ROOT);
        if("manual".equals(source)||"manual_recording".equals(source)||"quick_capture".equals(source)||"android_share".equals(source)||"audio_import".equals(source))return true;
        // Explicit screen-understand is user initiated, unlike ambient screenshot/notification ingestion.
        if("screen_understand".equals(source))return true;
        // Never let passive/system sources leak into the personal captured library.
        if("NOTIFICATION".equals(type)||"CALENDAR_EVENT".equals(type)||"CONTACT".equals(type))return false;
        return false;
    }

    public static boolean highPriorityForAnalysis(KnowledgeItem k){
        if(k==null)return false;
        String source=n(k.source),type=n(k.type).toUpperCase(Locale.ROOT);
        if("AUDIO".equals(type)&&("manual_recording".equals(source)||"audio_import".equals(source)))return true;
        return "manual".equals(source)||"quick_capture".equals(source)||"android_share".equals(source)||"screen_understand".equals(source);
    }

    public static String sourceLabel(String source){
        String s=n(source);
        if("manual_recording".equals(s))return"Recorded in Cortex";
        if("audio_import".equals(s))return"Imported audio";
        if("android_share".equals(s))return"Shared to Cortex";
        if("screen_understand".equals(s))return"Captured from screen";
        if("manual".equals(s)||"quick_capture".equals(s))return"Captured in Cortex";
        return s.isEmpty()?"Cortex":s;
    }

    private static String n(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
}
