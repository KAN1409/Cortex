package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative secondary identity for durable thread intelligence.
 * Different obligations in the same conversation must never collapse merely
 * because they share thread + semantic kind. Exact-ish lexical identity is
 * intentionally preferred over aggressive fuzzy merging; duplicate candidates
 * are safer than overwriting two genuinely different tasks.
 */
public final class DerivedSemanticIdentity {
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(
            "please","pls","can","could","would","you","we","i","me","my","our","the","a","an","to","for","of","and","or","on","in","at","this","that","it","is","are","be","need","needs","needed","required","kindly",
            "لو","ممكن","من","فضلك","بعد","اذنك","إذنك","انا","أنا","احنا","إحنا","انت","أنت","هو","هي","ده","دي","دا","ال","في","على","عن","من","الى","إلى","و","او","أو","محتاج","محتاجين","لازم","مطلوب"
    ));
    private DerivedSemanticIdentity(){}

    public static String key(String kind,String evidence){
        String canonical=canonical(evidence);
        if(canonical.isEmpty())return"";
        return Fingerprint.text(n(kind).toUpperCase(Locale.ROOT)+"|"+canonical);
    }

    public static String canonical(String evidence){
        String x=LocalSemanticEmbedder.norm(n(evidence));
        if(x.isEmpty())return"";
        x=x.replaceAll("https?://\\S+"," ").replaceAll("www\\.\\S+"," ").replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
        if(x.isEmpty())return"";
        ArrayList<String> kept=new ArrayList<>();
        for(String token:x.split("\\s+")){
            String t=n(token).toLowerCase(Locale.ROOT);
            if(t.isEmpty()||STOP.contains(t))continue;
            kept.add(t);
            if(kept.size()>=16)break;
        }
        if(kept.isEmpty())return x.length()<=180?x:x.substring(0,180);
        StringBuilder out=new StringBuilder();for(String t:kept){if(out.length()>0)out.append(' ');out.append(t);}return out.toString();
    }

    private static String n(String s){return s==null?"":s.trim();}
}
