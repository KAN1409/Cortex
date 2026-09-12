package com.kareem.cortex;

import java.util.Locale;

/**
 * Cheap deterministic action-quality gate.
 * Imperative grammar alone is not enough to make an item actionable.
 */
public final class ActionSpecificityGate {
    public static final String VERSION="action_specificity_gate_002";

    public static final class Result {
        public final double specificity;
        public final boolean eligible;
        public final String reason;
        Result(double specificity,boolean eligible,String reason){this.specificity=Math.max(0,Math.min(1,specificity));this.eligible=eligible;this.reason=reason==null?"":reason;}
    }
    private ActionSpecificityGate(){}
    public static Result evaluate(String semanticType,String intent,String subject,String summary,boolean explicitRequest,boolean linkedCommitment){String type=norm(semanticType),in=norm(intent),s=norm(subject),body=norm(summary);String all=(type+" "+in+" "+s+" "+body).trim();if(all.isEmpty())return new Result(0,false,"empty candidate");if(isExplicitNoAction(all))return new Result(0,false,"explicitly states that no action or follow-up is required");if(isUiChromeLike(all)&&!explicitRequest&&!linkedCommitment)return new Result(0,false,"UI/OCR chrome is evidence, not an action");if(isPhoneLabelOnly(all)&&!explicitRequest&&!linkedCommitment)return new Result(0,false,"contact label is not an obligation");double score=0;if(explicitRequest)score+=.28;if(linkedCommitment)score+=.22;if(s.length()>=4)score+=.18;if(body.length()>=24)score+=.12;if(hasConcreteObject(all))score+=.10;if(hasTimeAnchor(all))score+=.10;boolean generic=genericImperative(all);if(generic)score-=.35;boolean eligible=score>=.28||explicitRequest||linkedCommitment;if(generic&&!explicitRequest&&!linkedCommitment)eligible=false;String reason=generic?"generic action without grounded object":(eligible?"specific enough for attention triage":"insufficient action specificity");return new Result(score,eligible,reason);}
    public static boolean allowLegacyExtraction(String actionText){String x=norm(actionText);if(x.isEmpty()||x.length()<4)return false;if(isExplicitNoAction(x)||isUiChromeLike(x)||isPhoneLabelOnly(x))return false;return !genericImperative(x);}
    public static boolean isExplicitNoAction(String text){String x=norm(text).replace('‑','-').replace('–','-').replace('—','-');return containsAny(x,"no explicit follow-up","no explicit follow up","no follow-up or action","no follow up or action","no action required","no further action","nothing required","nothing to do","none detected","no follow-up required","no follow up required","no action was detected","no follow-up was detected","لا يوجد إجراء","لا يوجد اجراء","لا توجد متابعة","لا يحتاج إجراء","لا يحتاج اجراء","لا إجراء مطلوب","لا اجراء مطلوب","لا توجد إجراءات","لا توجد اجراءات","لا يلزم إجراء","لا يلزم اجراء");}
    public static boolean isUiChromeLike(String text){String x=norm(text);if(x.isEmpty())return false;int hits=0;String[] chrome={"open","close","cancel","save","settings","download","upload","refresh","sync","back","next","menu","search","share","install","uninstall","permissions","allow all","learn more","view plugin detail","فتح","إغلاق","اغلاق","إلغاء","الغاء","حفظ","الإعدادات","الاعدادات","تنزيل","تحميل","بحث","مشاركة","تثبيت"};for(String token:chrome)if(hasWordOrPhrase(x,token))hits++;boolean actionAddressed=containsAny(x,"please ","can you ","could you ","need you to ","لو سمحت","محتاج منك","محتاجك","ابعتلي","ابعثلي");return hits>=3&&!actionAddressed;}
    private static boolean isPhoneLabelOnly(String x){String compact=x.replaceAll("[+()0-9\\s:._/-]","").trim();boolean phoneish=containsAny(x,"phone","mobile","tel","telephone","رقم","تليفون","هاتف");return phoneish&&compact.length()<24&&!containsAny(x,"call ","اتصل","كلم","أكلم","اكلم");}
    private static boolean genericImperative(String x){return x.equals("check important info")||x.equals("check important info.")||x.equals("review important information")||x.equals("take action")||x.equals("do this")||x.equals("follow up")||x.matches("^(check|review|see|open|look at)\\s+(important\\s+)?(info|information|details)\\.?$");}
    private static boolean hasConcreteObject(String x){String[] tokens=x.split("[^\\p{L}\\p{N}@._-]+");int meaningful=0;for(String t:tokens){if(t.length()<3)continue;if(STOP.contains(t))continue;meaningful++;}return meaningful>=3;}
    private static boolean hasTimeAnchor(String x){return x.matches(".*\\b(today|tomorrow|tonight|due|deadline|am|pm|\\d{1,2}:\\d{2})\\b.*")||x.contains("النهارده")||x.contains("بكره")||x.contains("بكرة")||x.contains("موعد")||x.contains("ميعاد")||x.contains("قبل ");}
    private static boolean hasWordOrPhrase(String x,String token){if(token.indexOf(' ')>=0)return x.contains(token);return (" "+x+" ").contains(" "+token+" ");}
    private static boolean containsAny(String s,String... xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static final java.util.Set<String> STOP=new java.util.HashSet<>(java.util.Arrays.asList("check","important","info","information","details","please","this","that","with","from","action","review","open","look","see","the","and","for","your","you","need","راجع","مهم","مهمة","معلومات","تفاصيل","شوف","افتح","اعمل","لازم"));
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
}
