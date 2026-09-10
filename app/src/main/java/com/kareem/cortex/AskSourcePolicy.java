package com.kareem.cortex;

import java.util.Locale;

/** Brain retrieval policy: captured artifacts are evidence, not durable knowledge, unless the question explicitly asks for captured evidence. */
public final class AskSourcePolicy {
    private AskSourcePolicy(){}

    public static boolean allowSemantic(KnowledgeItem item,String query){
        if(item==null)return false;
        if(isRawCaptureArtifact(item))return queryExplicitlyAboutCapturedEvidence(query);
        if(isSelfUiScreenshot(item))return queryExplicitlyAboutCortexUi(query);
        return true;
    }

    public static boolean isRawCaptureArtifact(KnowledgeItem k){
        if(k==null)return false;String t=n(k.type).toUpperCase(Locale.ROOT),s=n(k.source).toLowerCase(Locale.ROOT);
        if("NOTIFICATION".equals(t)||"SCREENSHOT".equals(t)||"IMAGE".equals(t)||"AUDIO".equals(t)||"FILE".equals(t)||"DOCUMENT".equals(t)||"PDF".equals(t))return !"universal_memory".equals(s);
        return s.equals("screenshot-folder")||s.equals("screen_understanding")||s.equals("screen_understand")||s.equals("audio_import")||s.equals("manual_recording");
    }

    public static boolean isSelfUiScreenshot(KnowledgeItem k){if(k==null||!("SCREENSHOT".equals(k.type)||"IMAGE".equals(k.type)))return false;String t=(n(k.title)+"\n"+n(k.extractedText)+"\n"+n(k.summary)).toLowerCase(Locale.ROOT);if(!t.contains("cortex"))return false;int markers=0;if(t.contains("ask cortex"))markers++;if(t.contains("try asking"))markers++;if(t.contains("home"))markers++;if(t.contains("focus"))markers++;if(t.contains("vault"))markers++;if(t.contains("settings"))markers++;if(t.contains("local qwen"))markers++;if(t.contains("needs you")||t.contains("needs attention"))markers++;if(t.contains("capture")&&t.contains("ask"))markers++;return markers>=2;}
    private static boolean queryExplicitlyAboutCapturedEvidence(String query){String q=LocalSemanticEmbedder.norm(n(query));return has(q,"screenshot","image","photo","voice note","recording","file","document","notification","captured","capture","screen","سكرين","صورة","صور","تسجيل","فويس","ملف","اشعار","إشعار","التقط","كابتشر");}
    private static boolean queryExplicitlyAboutCortexUi(String query){String q=LocalSemanticEmbedder.norm(n(query));return has(q,"cortex","ask cortex","cortex ui","interface","screen","screenshot","vault","focus","واجهة","شاشة","سكرين شوت","كورتكس");}
    private static boolean has(String t,String... xs){for(String x:xs)if(t.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}private static String n(String s){return s==null?"":s;}
}
