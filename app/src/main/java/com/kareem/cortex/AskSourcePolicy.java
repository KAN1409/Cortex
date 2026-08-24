package com.kareem.cortex;

import java.util.Locale;

/** Prevents Cortex's own UI screenshots from echoing back as personal-memory answers. */
public final class AskSourcePolicy {
    private AskSourcePolicy(){}

    public static boolean allowSemantic(KnowledgeItem item,String query){
        if(item==null)return false;
        if(!isSelfUiScreenshot(item))return true;
        return queryExplicitlyAboutCortexUi(query);
    }

    public static boolean isSelfUiScreenshot(KnowledgeItem k){
        if(k==null||!("SCREENSHOT".equals(k.type)||"IMAGE".equals(k.type)))return false;
        String t=(n(k.title)+"\n"+n(k.extractedText)+"\n"+n(k.summary)).toLowerCase(Locale.ROOT);
        if(!t.contains("cortex"))return false;
        int markers=0;
        if(t.contains("ask cortex"))markers++;
        if(t.contains("try asking"))markers++;
        if(t.contains("home"))markers++;
        if(t.contains("focus"))markers++;
        if(t.contains("vault"))markers++;
        if(t.contains("settings"))markers++;
        if(t.contains("local qwen"))markers++;
        if(t.contains("needs you")||t.contains("needs attention"))markers++;
        if(t.contains("capture")&&t.contains("ask"))markers++;
        return markers>=2;
    }

    private static boolean queryExplicitlyAboutCortexUi(String query){
        String q=LocalSemanticEmbedder.norm(n(query));
        return has(q,"cortex","ask cortex","cortex ui","interface","screen","screenshot","vault","focus","واجهة","شاشة","سكرين شوت","كورتكس");
    }

    private static boolean has(String t,String... xs){for(String x:xs)if(t.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static String n(String s){return s==null?"":s;}
}
