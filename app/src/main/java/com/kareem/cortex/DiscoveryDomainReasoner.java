package com.kareem.cortex;

import java.util.*;

public final class DiscoveryDomainReasoner {
    private DiscoveryDomainReasoner(){}
    public static String domain(String history,String topic,String space){
        String x=DiscoveryPolicy.norm(history+" "+topic);
        if(any(x,"lab","medical","doctor","hospital","medicine","medication","symptom","cholesterol","ldl","hdl","تحليل","تحاليل","دكتور","دواء","مستشفى","أشعة","اعراض","أعراض"))return "HEALTH";
        if(any(x,"pr ","po ","quotation","vendor","supplier","contractor","invoice","boq","procurement","project","مقاول","مورد","عرض سعر","توريد","تنفيذ","مشروع"))return "WORK_PROCUREMENT";
        if(any(x,"price","cost","purchase","receipt","warranty","سعر","شراء","فاتورة","ضمان"))return "PURCHASE";
        if(any(x,"flight","hotel","booking","travel","trip","رحلة","فندق","حجز","طيران"))return "TRAVEL";
        if(any(x,"car","vehicle","service","maintenance","سيارة","عربية","صيانة"))return "VEHICLE";
        if(any(x,"person","contact","message","whatsapp","call","meeting","شخص","واتساب","مكالمة","رسالة"))return "PEOPLE";
        return "GENERAL";
    }
    public static List<String> questions(String domain,String title){
        ArrayList<String> q=new ArrayList<>();
        q.add("What changed in "+title+" that could invalidate an earlier assumption?");
        q.add("What expected next step is missing or unresolved in "+title+"?");
        q.add("Do any sources disagree about the current state of "+title+"?");
        q.add("Is there a cross-source connection that was not obvious when each item was viewed alone?");
        if("HEALTH".equals(domain)){q.add("Do available results form a clinically meaningful pattern or change that deserves professional follow-up?");q.add("Would current reputable medical guidance materially change how this evidence should be interpreted?");}
        if("WORK_PROCUREMENT".equals(domain)){q.add("Is there a procurement-chain gap between request, quotation, approval, PO, execution and payment?");q.add("Is any comparable price or scope inconsistent with prior evidence?");}
        if("PURCHASE".equals(domain)){q.add("Is the price, warranty, model or seller information inconsistent with other available evidence?");}
        if("PEOPLE".equals(domain)){q.add("Is there an unfulfilled commitment or unresolved conversation thread involving this person?");}
        return q;
    }
    public static boolean researchUseful(String domain,String question){
        if("HEALTH".equals(domain)||"PURCHASE".equals(domain)||"TRAVEL".equals(domain)||"VEHICLE".equals(domain))return true;
        String q=DiscoveryPolicy.norm(question);
        return any(q,"current","guidance","market","regulation","recall","price","latest","research");
    }
    private static boolean any(String s,String... xs){for(String x:xs)if(s.contains(DiscoveryPolicy.norm(x)))return true;return false;}
}
