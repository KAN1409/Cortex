package com.kareem.cortex;

import org.json.JSONObject;
import java.util.*;
import java.util.regex.*;

/** Pure deterministic policy used before any model-based discovery reasoning. */
public final class DiscoveryPolicy {
    private static final Pattern EXACT_REF=Pattern.compile("(?i)\\b(PR|PO)[\\s#:/._-]*([A-Z0-9][A-Z0-9._/-]{1,})\\b");
    private static final Set<String> WORK_WORDS=new HashSet<>(Arrays.asList(
            "work","project","projects","procurement","quotation","quote","vendor","supplier","contractor","site","invoice","boq","pr","po",
            "شغل","مشروع","مشاريع","مقاول","مورد","عرض","سعر","موقع","توريد","تنفيذ"));
    private static final Set<String> LIFE_WORDS=new HashSet<>(Arrays.asList(
            "health","medical","doctor","clinic","hospital","medicine","medication","lab","analysis","family","home","personal",
            "صحة","طبيب","دكتور","مستشفى","دواء","تحاليل","تحليل","عائلة","بيت","شخصي"));
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(
            "the","and","for","from","with","this","that","your","you","have","has","was","were","into","about","after","before",
            "في","من","على","الى","إلى","عن","مع","هذا","هذه","تم","كان","كانت"));

    private DiscoveryPolicy(){}

    public static String inferSpace(KnowledgeItem item){
        if(item==null)return "UNKNOWN";
        try{
            JSONObject o=new JSONObject(n(item.metadataJson));
            String explicit=n(o.optString("space","")).toUpperCase(Locale.ROOT);
            if("WORK".equals(explicit)||"LIFE".equals(explicit))return explicit;
        }catch(Exception ignored){}
        String text=norm(n(item.category)+" "+n(item.tags)+" "+n(item.source)+" "+n(item.title));
        int work=hits(text,WORK_WORDS),life=hits(text,LIFE_WORDS);
        if(work>0&&life==0)return "WORK";
        if(life>0&&work==0)return "LIFE";
        return "UNKNOWN";
    }

    public static String exactReference(String text){
        Matcher m=EXACT_REF.matcher(n(text));
        if(!m.find())return "";
        return (m.group(1).toUpperCase(Locale.ROOT)+"-"+m.group(2).toUpperCase(Locale.ROOT)).replaceAll("[^A-Z0-9._/-]","");
    }

    public static String topicKey(KnowledgeItem item,List<String> entities){
        String all=n(item.title)+" "+n(item.summary)+" "+n(item.extractedText)+" "+n(item.rawText)+" "+n(item.tags);
        String ref=exactReference(all);
        if(!ref.isEmpty())return "ref|"+ref.toLowerCase(Locale.ROOT);
        if(entities!=null){
            for(String raw:entities){
                String x=n(raw);
                int p=x.indexOf(':');
                String kind=p>0?x.substring(0,p).trim().toLowerCase(Locale.ROOT):"";
                String value=p>0?x.substring(p+1).trim():x;
                if(value.length()<3)continue;
                if(kind.contains("project")||kind.contains("organization")||kind.contains("company")||kind.contains("person")||kind.contains("contact"))
                    return "entity|"+kind+"|"+Fingerprint.text(norm(value));
            }
        }
        String seed=topicWords(n(item.category)+" "+n(item.title)+" "+n(item.tags));
        if(seed.isEmpty())seed=topicWords(n(item.summary));
        return seed.isEmpty()?"item|"+item.id:"topic|"+Fingerprint.text(seed);
    }

    public static String topicLabel(KnowledgeItem item){
        String ref=exactReference(n(item.title)+" "+n(item.summary)+" "+n(item.extractedText));
        if(!ref.isEmpty())return ref;
        String title=n(item.title);
        if(!title.isEmpty())return title.length()<=120?title:title.substring(0,120);
        String c=n(item.category);
        return c.isEmpty()?"Cortex situation":c;
    }

    public static String claimState(KnowledgeItem item){
        String x=norm(n(item.title)+" "+n(item.summary)+" "+n(item.extractedText));
        if(any(x,"rejected","declined","cancelled","canceled","رفض","مرفوض","ملغي","أُلغي","الغاء"))return "rejected";
        if(any(x,"needs revision","revise","revision required","تعديل","مراجعة مطلوبة","يحتاج تعديل"))return "revision";
        if(any(x,"approved","accepted","confirmed","اعتماد","معتمد","تمت الموافقة","موافقه"))return "approved";
        if(any(x,"completed","complete","done","closed","finished","تم التنفيذ","اكتمل","انتهى","مغلق"))return "completed";
        if(any(x,"pending","waiting","awaiting","قيد الانتظار","منتظر","بانتظار"))return "pending";
        return "";
    }

    public static boolean contradictory(String a,String b){
        String x=n(a),y=n(b);if(x.isEmpty()||y.isEmpty()||x.equals(y))return false;
        if(("approved".equals(x)&&("rejected".equals(y)||"revision".equals(y)))||("approved".equals(y)&&("rejected".equals(x)||"revision".equals(x))))return true;
        if(("completed".equals(x)&&"pending".equals(y))||("completed".equals(y)&&"pending".equals(x)))return true;
        return false;
    }

    public static boolean isTrivial(String title,String body){
        String x=norm(n(title)+" "+n(body));
        if(x.isEmpty())return true;
        if(x.matches(".*\\b\\d+\\s+(records?|items?|observations?|notifications?)\\b.*")&&
                !any(x,"missing","failed","overdue","conflict","contradict","risk"))return true;
        return any(x,"there is no comparable prior week","observed count, not a trend");
    }

    public static double score(double novelty,double relevance,double consequence,double evidence,double timeliness,double uncertainty){
        double raw=novelty*0.24+relevance*0.24+consequence*0.20+evidence*0.18+timeliness*0.14-uncertainty*0.18;
        return Math.max(0,Math.min(1,raw));
    }

    public static String norm(String s){return LocalSemanticEmbedder.norm(n(s)).toLowerCase(Locale.ROOT);}
    private static String topicWords(String s){
        ArrayList<String> words=new ArrayList<>();
        for(String p:norm(s).split("[^\\p{L}\\p{N}]+")){
            if(p.length()<3||STOP.contains(p))continue;
            words.add(p);
            if(words.size()>=8)break;
        }
        Collections.sort(words);
        return String.join(" ",words);
    }
    private static int hits(String text,Set<String> words){int n=0;for(String w:words)if(text.contains(w))n++;return n;}
    private static boolean any(String s,String... xs){for(String x:xs)if(s.contains(norm(x)))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
}
