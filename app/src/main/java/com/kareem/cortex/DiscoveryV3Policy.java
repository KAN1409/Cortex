package com.kareem.cortex;

import java.util.*;
import java.util.regex.*;

public final class DiscoveryV3Policy {
    private static final Pattern REF=Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(?:P\\.?R\\.?|P\\.?O\\.?)(?![\\p{L}\\p{N}])\\s*[#:/._-]?\\s*([A-Z0-9][A-Z0-9._/-]{1,31})(?![\\p{L}\\p{N}])");
    private static final Pattern REF_PREFIX=Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(P\\.?R\\.?|P\\.?O\\.?)(?![\\p{L}\\p{N}])");
    private DiscoveryV3Policy(){}

    public static String norm(String s){return LocalSemanticEmbedder.norm(s==null?"":s).toLowerCase(Locale.ROOT).trim();}

    public static String exactRef(String text){
        String raw=text==null?"":text;Matcher m=REF.matcher(raw);
        while(m.find()){
            String token=m.group(1).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._/-]","");
            if(!validRefBody(token))continue;
            Matcher p=REF_PREFIX.matcher(m.group());if(!p.find())continue;
            String kind=p.group(1).toUpperCase(Locale.ROOT).replace(".","");
            return kind+"-"+token;
        }
        return "";
    }

    public static String space(KnowledgeItem k){
        if(k==null)return "UNKNOWN";
        String explicit=explicitSpace(k.metadataJson);
        if(!explicit.isEmpty())return explicit;
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

    private static boolean validRefBody(String body){
        if(body==null)return false;String x=body.trim().toUpperCase(Locale.ROOT);
        if(x.length()<2||x.length()>32)return false;
        boolean digit=x.matches(".*\\d.*");
        boolean structured=x.matches(".*[-_/].*");
        if(!digit)return false;
        if(x.matches("(?i)(IVACY|OJECT|IMARY|OCESSING|ICE|OGRESS|OVIDER|EVIEW|ODUCT|OBLEM|OMPT|OFILE|OJECTS)"))return false;
        return true;
    }

    private static String explicitSpace(String metadata){
        String raw=metadata==null?"":metadata;
        Matcher m=Pattern.compile("(?i)[\\\"']?space[\\\"']?\\s*[:=]\\s*[\\\"']?(WORK|LIFE)[\\\"']?").matcher(raw);
        return m.find()?m.group(1).toUpperCase(Locale.ROOT):"";
    }

    private static int hits(String s,String... xs){int n=0;for(String x:xs)if(s.contains(norm(x)))n++;return n;}
    private static boolean any(String s,String... xs){return hits(s,xs)>0;}
}
