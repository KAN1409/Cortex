package com.kareem.cortex;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Presentation-time consolidation of durable derived candidates into canonical events.
 * Stored relevance remains untouched; this layer only prevents duplicate evidence from
 * consuming multiple attention slots and assigns a more useful cognitive presentation kind.
 */
public final class CandidateConsolidator {
    private static final Pattern URL=Pattern.compile("https?://\\S+",Pattern.CASE_INSENSITIVE);
    private static final Pattern REF=Pattern.compile("\\[(?:\\d+|[^\\]]{0,12})\\]");
    private CandidateConsolidator(){}

    public static String effectiveKind(PrimeBriefStore.Item x){
        if(x==null)return "CONTEXT";
        String stored=n(x.kind).toUpperCase(Locale.ROOT),t=norm(x.title+" "+x.body),src=norm(x.source);
        if(alert(t,src))return "ALERT";
        if(reminder(t))return "REMINDER";
        if(change(t,src,stored))return "CHANGE";
        return stored.isEmpty()?"CONTEXT":stored;
    }

    /** Conservative event fingerprint. Keeps meaningful numbers/amounts, removes transport noise. */
    public static String eventKey(PrimeBriefStore.Item x){
        if(x==null)return "";
        String t=canonicalText(x.title+" "+x.body);
        String source=sourceFamily(x.source);
        return effectiveKind(x)+"|"+source+"|"+t;
    }

    public static boolean sameEvent(PrimeBriefStore.Item a,PrimeBriefStore.Item b){
        if(a==null||b==null)return false;
        if(!effectiveKind(a).equals(effectiveKind(b)))return false;
        String sa=sourceFamily(a.source),sb=sourceFamily(b.source);
        if(!sa.isEmpty()&&!sb.isEmpty()&&!sa.equals(sb))return false;
        String x=canonicalText(a.title+" "+a.body),y=canonicalText(b.title+" "+b.body);
        if(x.equals(y))return true;
        if(x.length()<24||y.length()<24)return false;
        // Conservative near-duplicate test: one canonical rendering largely contains the other.
        int min=Math.min(x.length(),y.length()),max=Math.max(x.length(),y.length());
        if(min*100/max<88)return false;
        if(x.contains(y)||y.contains(x))return true;
        return tokenOverlap(x,y)>=0.92;
    }

    public static <T> ArrayList<T> consolidate(List<T> rows,ItemAccessor<T> access){
        ArrayList<T> out=new ArrayList<>();
        for(T row:rows){
            PrimeBriefStore.Item item=access.item(row);boolean merged=false;
            for(int i=0;i<out.size();i++){
                T old=out.get(i);PrimeBriefStore.Item oi=access.item(old);
                if(!sameEvent(item,oi))continue;
                if(access.priority(row)>access.priority(old)||(access.priority(row)==access.priority(old)&&item.updatedAt>oi.updatedAt))out.set(i,row);
                merged=true;break;
            }
            if(!merged)out.add(row);
        }
        return out;
    }

    public interface ItemAccessor<T>{PrimeBriefStore.Item item(T x);int priority(T x);}

    private static boolean alert(String t,String src){
        boolean financial=has(t,"transaction","payment","purchase","card","بطاق","معامله","المعامله","عمليه","عملية","خصم","رصيد","credit limit","الحد الائتماني","egp");
        boolean failure=has(t,"declined","rejected","refused","failed","رفض","مرفوض","لم تتم","غير كافي","عدم كفايه","عدم كفاية","تجاوزتم");
        boolean security=has(t,"security alert","unusual activity","new login","تسجيل دخول جديد","تنبيه امان");
        return security||(financial&&failure)||(src.contains("bank")&&failure);
    }
    private static boolean reminder(String t){return has(t,"reminder","remind me","remember to","فكرني","ذكرني","اعمل تذكير","اعمل reminder");}
    private static boolean change(String t,String src,String stored){
        if(!("DECISION".equals(stored)||"MEMORY".equals(stored)))return false;
        if(has(t,"status changed","changed to","became","تم التغيير","اتغير","تغيرت الحاله","تغيرت الحالة"))return true;
        // System/provider approvals are changes; conversational approvals remain decisions.
        return !src.isEmpty()&&!src.contains("whatsapp")&&!src.contains("telegram")&&!src.contains("messenger")&&has(t,"approved","rejected","تمت الموافقه","تمت الموافقة","تم الرفض");
    }
    private static String canonicalText(String s){
        String x=s==null?"":s; x=URL.matcher(x).replaceAll(" ");x=REF.matcher(x).replaceAll(" ");x=norm(x);
        // Remove repeated provider prefix/suffix punctuation without deleting amounts or dates.
        x=x.replaceAll("(?i)^(cib|nbe|qnb|banque misr|bank)\\s*[:·-]\\s*"," ").replaceAll("(?i)\\s+(cib|nbe|qnb)\\s*[:·-]?\\s*$"," ");
        return x.replaceAll("\\s+"," ").trim();
    }
    private static String sourceFamily(String s){String x=norm(s);if(x.contains("cib"))return "cib";if(x.contains("whatsapp"))return "whatsapp";if(x.contains("gmail")||x.contains("mail"))return "mail";if(x.contains("sms")||x.contains("message"))return "message";return x.length()>48?x.substring(0,48):x;}
    private static double tokenOverlap(String a,String b){Set<String>x=tokens(a),y=tokens(b);if(x.isEmpty()||y.isEmpty())return 0;Set<String>i=new HashSet<>(x);i.retainAll(y);Set<String>u=new HashSet<>(x);u.addAll(y);return u.isEmpty()?0:(double)i.size()/u.size();}
    private static Set<String> tokens(String s){HashSet<String>r=new HashSet<>();for(String x:s.split("\\s+"))if(x.length()>1)r.add(x);return r;}
    private static boolean has(String s,String... xs){for(String x:xs)if(s.contains(norm(x)))return true;return false;}
    private static String norm(String s){return MasterRelevanceFilter.ruleNorm(s==null?"":s);}
    private static String n(String s){return s==null?"":s.trim();}
}
