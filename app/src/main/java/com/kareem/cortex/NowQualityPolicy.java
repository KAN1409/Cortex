package com.kareem.cortex;

import java.util.Locale;

/** Legacy/pre-judge user-facing quality guard. Final-Judge rows are presentation-only here. */
public final class NowQualityPolicy {
    private NowQualityPolicy(){}

    public static boolean suppress(String kind,String source,String title,String body){
        String k=n(kind).toUpperCase(Locale.ROOT);
        String src=n(source).toLowerCase(Locale.ROOT);
        if(src.startsWith("final_judge|"))return false;
        String t=n(title);
        String b=n(body);
        String x=(t+" "+b).replaceAll("\\s+"," ").trim();
        String l=x.toLowerCase(Locale.ROOT);

        if(x.isEmpty())return true;
        if(metaUi(l))return true;
        if("WAITING".equals(k)&&systemNotice(l))return true;

        // PicBrain is evidence first. This guard remains for legacy/non-canonical surfaces only.
        if("picbrain".equals(src)||src.contains("knowledge_v2")){
            if(x.length()>180)return true;
            if(genericReference(l))return true;
            if(!explicitActionCue(l))return true;
        }
        return false;
    }

    public static boolean explicitActionCue(String l){
        if(l==null||l.trim().isEmpty())return false;
        String x=" "+l.toLowerCase(Locale.ROOT)+" ";
        String[] en={" i need "," i have to "," need to "," must "," remember to "," remind me "," follow up "," follow-up "," call "," send "," reply "," respond "," buy "," pay "," book "," schedule "," submit "," renew "," cancel "," check "," fix "," install "," update "," contact "," order "," return "," finish "," complete "," deadline "," due "," tomorrow "," today "};
        for(String q:en)if(x.contains(q))return true;
        String[] ar={"عايز","عاوز","محتاج","لازم","افتكر","فكرني","ابعت","ابعث","كلم","اتصل","اشتري","ادفع","احجز","راجع","صلح","ثبت","حدّث","حدث","رد","تابع","ميعاد","موعد","بكره","بكرة","النهارده","اليوم"};
        for(String q:ar)if(l.contains(q))return true;
        return false;
    }

    private static boolean metaUi(String l){
        return has(l,
                "ask brain to interpret the intent",
                "connect it to your current context",
                "turn it into a decision, follow-up, reminder, or next action",
                "cortex is processing",
                "recent structured facts",
                "start semantic index",
                "open knowledge",
                "re-analyze (offline)",
                "offline ai preview",
                "voice notes 1 memos transcribed",
                "view details n make sure you add your low resolution photo");
    }

    private static boolean genericReference(String l){
        return has(l,
                "its significance should be clinically correlated",
                "clinical correlation is recommended",
                "for informational purposes only",
                "not medical advice");
    }

    private static boolean systemNotice(String l){
        return has(l,
                "scheduled maintenance","system maintenance","systems maintenance","service maintenance",
                "سيتم تحديث أنظمة","تحديث أنظمة","أعمال صيانة","صيانة الأنظمة","صيانة مجدولة");
    }

    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
}
