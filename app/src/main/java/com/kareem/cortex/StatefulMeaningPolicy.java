package com.kareem.cortex;

import java.util.*;

/** Pure policy for v69 identity -> state -> meaning -> correlation -> projection. No Android/UI side effects. */
public final class StatefulMeaningPolicy {
    public static final String VERSION="stateful_meaning_policy_001";
    private StatefulMeaningPolicy(){}

    public static final class ProjectionDecision {
        public final boolean capture,now,brief,brain;
        public final String reason;
        ProjectionDecision(boolean c,boolean n,boolean b,boolean br,String r){capture=c;now=n;brief=b;brain=br;reason=n(r);}
    }

    public static String notificationInstanceKey(String pkg,String notificationKey,String shortcutId,String groupKey,int id,String tag,String title){
        String p=n(pkg),k=n(notificationKey),s=n(shortcutId),g=n(groupKey),t=n(tag);
        if(!k.isEmpty())return "sbn|"+p+"|"+k;
        if(!s.isEmpty())return "shortcut|"+p+"|"+s;
        if(!g.isEmpty()&&(id!=0||!t.isEmpty()))return "group|"+p+"|"+g+"|"+id+"|"+t;
        if(id!=0||!t.isEmpty())return "id|"+p+"|"+id+"|"+t;
        return "fallback|"+p+"|"+Fingerprint.text(LocalSemanticEmbedder.norm(n(title)));
    }

    public static String lifecycleState(String technical,String eventType,String title,String body,String removalReason){
        String tech=n(technical).toLowerCase(Locale.ROOT),event=n(eventType).toLowerCase(Locale.ROOT),text=(n(title)+" "+n(body)).toLowerCase(Locale.ROOT),reason=n(removalReason).toLowerCase(Locale.ROOT);
        if("removed".equals(event)||event.contains("remove"))return "removed:"+(reason.isEmpty()?"unknown":reason);
        if(tech.contains("call")){
            if(any(text,"missed call","مكالمة فائتة","لم يتم الرد"))return "missed";
            if(any(text,"incoming call","ringing","مكالمة واردة"))return "ringing";
            if(any(text,"ongoing call","call in progress","مكالمة جارية"))return "active";
            if(any(text,"call ended","ended call","انتهت المكالمة"))return "ended";
        }
        if(tech.contains("progress")||tech.contains("download")){
            if(any(text,"complete","completed","downloaded","اكتمل","تم التنزيل"))return "completed";
            if(any(text,"failed","error","فشل"))return "failed";
            return "progress";
        }
        return "posted";
    }

    /** A source transition is not automatically a semantic fact. */
    public static boolean shouldEmitSemanticFact(String technical,String fromState,String toState,String title,String body){
        String tech=n(technical).toLowerCase(Locale.ROOT),from=n(fromState),to=n(toState),text=(n(title)+" "+n(body)).trim();
        if(to.startsWith("removed:"))return false;
        if(tech.contains("progress")||tech.contains("download")||tech.contains("service_state"))return "completed".equals(to)||"failed".equals(to);
        if(tech.contains("call"))return "missed".equals(to)||"ended".equals(to);
        if(to.equals(from)&&!from.isEmpty())return false;
        return !text.isEmpty();
    }

    public static String correlationKey(String semanticType,String subject,String summary,String source,long occurredAt){
        String type=n(semanticType).toLowerCase(Locale.ROOT),sub=norm(subject),sum=norm(summary),all=(sub+" "+sum).trim();
        if(isSecurity(all))return "security|"+securitySubject(all)+"|"+securityAction(all);
        String phone=phone(all);if(!phone.isEmpty()&&(type.contains("call")||type.contains("phone")))return "call|"+phone+"|"+(occurredAt/(10L*60L*1000L));
        if(type.contains("conversation")||type.contains("request")||type.contains("commitment")||type.contains("decision"))return "conversation|"+Fingerprint.text(topic(sub.isEmpty()?sum:sub));
        if(isWeather(type,all))return "weather|"+weatherPlace(all);
        return "semantic|"+type+"|"+Fingerprint.text(topic(sub+" "+sum));
    }

    public static ProjectionDecision projection(String semanticType,String intent,double confidence,String transitionKind,int repeatedCount,String subject,String summary){
        String type=n(semanticType).toLowerCase(Locale.ROOT),in=n(intent).toLowerCase(Locale.ROOT),transition=n(transitionKind).toUpperCase(Locale.ROOT),all=norm(subject+" "+summary);
        boolean capture=true,now=false,brief=false,brain=false;
        if(confidence<0.70)return new ProjectionDecision(true,false,false,false,"low confidence stays in Capture");
        if(type.contains("action_request")||"request".equals(in)||isSecurity(all)){now=true;brief="OPENED".equals(transition)||"ESCALATED".equals(transition);return new ProjectionDecision(capture,now,brief,false,"actionable situation");}
        if(type.contains("decision")||type.contains("commitment")){now=type.contains("commitment");brief=materialTransition(transition);brain=confidence>=0.82;return new ProjectionDecision(capture,now,brief,brain,"durable decision/commitment");}
        if(type.contains("call")){
            boolean missed=all.contains("missed")||all.contains("فائت")||all.contains("لم يتم الرد");now=missed&&repeatedCount>=2;brief=missed&&repeatedCount>=2&&materialTransition(transition);return new ProjectionDecision(capture,now,brief,false,"call lifecycle is capture-only unless repeated missed calls matter");}
        if(isWeather(type,all)){boolean severe=isSevereWeather(all);brief=severe&&materialTransition(transition);return new ProjectionDecision(capture,false,brief,false,severe?"material weather change":"routine weather");}
        if(isSocial(type,all))return new ProjectionDecision(capture,false,false,false,"routine social update defaults to low salience");
        if(type.contains("conversation_message")||"message".equals(in))return new ProjectionDecision(capture,false,false,false,"ordinary message is understood but not promoted without obligation or material context");
        brief=materialTransition(transition)&&!type.contains("technical")&&!type.contains("notification_event");
        return new ProjectionDecision(capture,false,brief,false,brief?"material situation delta":"capture-only semantic fact");
    }

    public static boolean sameExactDelivery(String instanceKey,String oldHash,String newHash){return !n(instanceKey).isEmpty()&&!n(oldHash).isEmpty()&&n(oldHash).equals(n(newHash));}
    public static boolean materialChange(String before,String after){String a=norm(before),b=norm(after);if(a.equals(b))return false;if(a.isEmpty()!=b.isEmpty())return true;Set<String>x=tokens(a),y=tokens(b);if(x.isEmpty()||y.isEmpty())return true;int shared=0;for(String t:x)if(y.contains(t))shared++;double overlap=(double)shared/(double)Math.max(1,Math.min(x.size(),y.size()));return overlap<0.75;}
    public static boolean materialTransition(String transition){String x=n(transition).toUpperCase(Locale.ROOT);return "OPENED".equals(x)||"MATERIAL_UPDATE".equals(x)||"ESCALATED".equals(x)||"DEESCALATED".equals(x)||"DEADLINE_APPROACHING".equals(x)||"RESOLVED".equals(x)||"REOPENED".equals(x);}
    public static boolean isSocial(String type,String text){String s=(n(type)+" "+n(text)).toLowerCase(Locale.ROOT);return any(s,"story","liked","reaction","reacted","followed","follows you","added you","added back","typing","snapchat","instagram story","new story");}
    public static boolean isWeather(String type,String text){String s=(n(type)+" "+n(text)).toLowerCase(Locale.ROOT);return s.contains("weather")||s.contains("temperature")||s.matches(".*\\b-?\\d{1,2}°.*");}
    public static boolean isSevereWeather(String text){String s=n(text).toLowerCase(Locale.ROOT);return any(s,"warning","alert","storm","thunder","heavy rain","flood","hail","dust storm","extreme heat","severe","تحذير","عاصفة","أمطار غزيرة","سيول","حر شديد");}
    public static boolean isSecurity(String text){String s=n(text).toLowerCase(Locale.ROOT);return any(s,"security alert","critical security","password","compromised","found online","breach","saved passwords","تسريب","كلمة مرور","كلمات المرور","تنبيه أمني");}

    private static String securitySubject(String s){if(s.contains("google"))return "google_account";if(s.contains("microsoft"))return "microsoft_account";if(s.contains("facebook")||s.contains("meta"))return "meta_account";return "account_security";}
    private static String securityAction(String s){if(any(s,"password","كلمة مرور","كلمات المرور"))return "password_remediation";return "security_review";}
    private static String weatherPlace(String s){String x=s.replaceAll(".*\\bin\\s+","").replaceAll("[^\\p{L}\\p{N} ]"," ").trim();return x.isEmpty()?"local":Fingerprint.text(topic(x));}
    private static String phone(String s){String digits=s.replaceAll("[^0-9+]","");String only=digits.replaceAll("\\D","");return only.length()>=7&&only.length()<=15?only:"";}
    private static String topic(String s){ArrayList<String> out=new ArrayList<>();for(String x:n(s).split("[^\\p{L}\\p{N}]+")){String w=x.toLowerCase(Locale.ROOT);if(w.length()<3||STOP.contains(w))continue;out.add(w);}Collections.sort(out);return String.join(" ",out);}
    private static Set<String> tokens(String s){return new HashSet<>(Arrays.asList(topic(s).split("\\s+")));}
    private static String norm(String s){return LocalSemanticEmbedder.norm(n(s)).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static boolean any(String s,String... xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("the","and","for","from","with","your","this","that","was","were","has","have","into","about","after","before","user","notification","received","على","الى","إلى","هذا","هذه","الذي","التي","كان","كانت","تم","من","في"));
}
