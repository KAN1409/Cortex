package com.kareem.cortex;

import java.util.Locale;

/** Final user-facing guard against automated replies, promotions and spam becoming Now actions. */
public final class AttentionNoisePolicy {
    private AttentionNoisePolicy(){}

    public static boolean suppress(String source,String title,String body,String semanticType,String intent){
        String x=(n(source)+" "+n(title)+" "+n(body)+" "+n(semanticType)+" "+n(intent)).toLowerCase(Locale.ROOT);
        if(has(x,"spam","promoted","promotion","promotional","offer valid","special offer","exclusive offer","0%","without interest","بدون فوائد","عرض","استمتع بالعرض","فرصة تقسيط","تقسيط")) return true;
        if(has(x,"thank you for contacting","thanks for contacting","please let us know how we can help","how can we help you","we received your message","شكرا لتواصلك","شكرًا لتواصلك")) return true;
        if(has(x,"screenshot saved","download complete","downloaded","tap here to see your screenshot","most viewed prompts","top prompts from this week")) return true;
        return false;
    }

    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
}
