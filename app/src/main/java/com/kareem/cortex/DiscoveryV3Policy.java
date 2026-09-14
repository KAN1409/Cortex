package com.kareem.cortex;

import org.json.JSONObject;
import java.util.*;
import java.util.regex.*;

public final class DiscoveryV3Policy {
    private static final Pattern REF=Pattern.compile("(?i)\\b(PR|PO)[\\s#:/._-]*([A-Z0-9][A-Z0-9._/-]{1,})\\b");
    private DiscoveryV3Policy(){}

    public static String norm(String s){return LocalSemanticEmbedder.norm(s==null?"":s).toLowerCase(Locale.ROOT).trim();}

    public static String exactRef(String text){
        Matcher m=REF.matcher(text==null?"":text);
        if(!m.find())return "";
        return m.group(1).toUpperCase(Locale.ROOT)+"-"+m.group(2).toUpperCase(Locale.ROOT);
    }

    public static String space(KnowledgeItem k){
        if(k==null)return "UNKNOWN";
        try{
            JSONObject o=new JSONObject(k.metadataJson==null?"{}":k.metadataJson);
            String x=o.optString("space","").trim().toUpperCase(Locale.ROOT);
            if("WORK".equals(x)||"LIFE".equals(x))return x;
        }catch(Throwable ignored){}
        String x=norm(k.category+" "+k.tags+" "+k.source+" "+k.title);
        int work=hits(x,"project","procurement","quotation","vendor","supplier","contractor","boq","work","مشروع","مقاول","مورد","توريد","تنفيذ","عرض سعر");
        int life=hits(x,"health","medical","doctor","hospital","medicine","personal","family","تحليل","تحاليل","دكتور","مستشفى","دواء","شخصي","عائلة");
        if(work>0&&life==0)return "WORK";
        if(life>0&&work==0)return "LIFE";
        return "UNKNOWN";
    }

    public static String domain(KnowledgeItem k,String text){
        String x=norm((k==null?"":k.category+" "+k.tags)+" "+text);
        if(hits(x,"health","medical","doctor","hospital","medicine","lab","cholesterol","ldl","hdl","تحليل","تحاليل","دكتور","مستشفى","دواء","أشعة")>0)return "HEALTH";
        if(hits(x,"pr","po","quotation","vendor","supplier","contractor","boq","procurement","project","مقاول","مورد","توريد","تنفيذ","عرض سعر","مشروع")>0)return "WORK_PROCUREMENT";
        if(hits(x,"price","cost","receipt","purchase","warranty","سعر","فاتورة","شراء","ضمان")>0)return "PURCHASE";
        if(hits(x,"call","message","whatsapp","contact","person","meeting","مكالمة","رسالة","واتساب","شخص","اجتماع")>0)return "PEOPLE";
        return "GENERAL";
    }

    public static String stateClaim(String text){
        String x=norm(text);
        if(any(x,"needs revision","revision required","revise","requires revision","يحتاج تعديل","تعديل مطلوب","يرجى التعديل"))return "revision";
        if(any(x,"rejected","declined","cancelled","canceled","مرفوض","تم الرفض","ملغي","إلغاء"))return "rejected";
        if(any(x,"approved","accepted","confirmed","تمت الموافقة","معتمد","تم الاعتماد"))return "approved";
        if(any(x,"completed","finished","closed","done","تم التنفيذ","اكتمل","تم الانتهاء"))return "completed";
        if(any(x,"pending","waiting","awaiting","منتظر","بانتظار","قيد الانتظار"))return "pending";
        return "";
    }

    public static boolean incompatible(String a,String b){
        if(a==null||b==null||a.equals(b))return false;
        Set<String> approval=new HashSet<>(Arrays.asList("approved","revision","rejected"));
        if(approval.contains(a)&&approval.contains(b))return true;
        return ("completed".equals(a)&&"pending".equals(b))||("pending".equals(a)&&"completed".equals(b));
    }

    public static double score(double consequence,double novelty,double evidence,double timeliness,double confidence,double uncertainty){
        double v=.25*consequence+.22*novelty+.20*evidence+.13*timeliness+.20*confidence-.22*uncertainty;
        return Math.max(0,Math.min(1,v));
    }

    public static boolean publishable(String family,String whatFound,String suggestedAction,int evidenceCount,double confidence,double score){
        String x=norm(whatFound);
        if(x.isEmpty()||suggestedAction==null||suggestedAction.trim().isEmpty())return false;
        if(x.matches(".*\\b\\d+\\s+(records?|items?|observations?|notifications?)\\b.*"))return false;
        if(confidence<.68||score<.66)return false;
        if(("CONTRADICTION".equals(family)||"CROSS_SOURCE_CONNECTION".equals(family))&&evidenceCount<2)return false;
        return evidenceCount>=1;
    }

    private static int hits(String s,String... xs){int n=0;for(String x:xs)if(s.contains(norm(x)))n++;return n;}
    private static boolean any(String s,String... xs){return hits(s,xs)>0;}
}
