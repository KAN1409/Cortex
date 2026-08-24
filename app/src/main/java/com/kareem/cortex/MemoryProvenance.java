package com.kareem.cortex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Product guard: no orphan data. Every surfaced item should explain origin, capture path and why it was kept. */
public final class MemoryProvenance {
    private MemoryProvenance(){}

    public static final class Info {
        public final boolean notification;
        public final String appLabel,packageName,kind,channel,category,reason,disposition;
        public final long occurredAt;
        Info(boolean notification,String appLabel,String packageName,String kind,String channel,String category,String reason,String disposition,long occurredAt){
            this.notification=notification;this.appLabel=n(appLabel);this.packageName=n(packageName);this.kind=n(kind);this.channel=n(channel);this.category=n(category);this.reason=n(reason);this.disposition=n(disposition);this.occurredAt=occurredAt;
        }
        public String sourceLabel(){return !appLabel.isEmpty()?appLabel:(!packageName.isEmpty()?packageName:"Unknown source");}
        public String kindLabel(){return friendlyKind(kind,category);}
        public String captureLabel(){if(!notification)return"Saved memory";String k=kindLabel();return k.isEmpty()?"Android notification":"Android notification · "+k;}
        public String exactTime(){long t=occurredAt>0?occurredAt:System.currentTimeMillis();return new SimpleDateFormat("dd MMM yyyy · HH:mm:ss",Locale.getDefault()).format(new Date(t));}
        public String whyKept(){String d=friendlyDisposition(disposition);if(reason.isEmpty())return d.isEmpty()?"No retention reason recorded":d;return d.isEmpty()?reason:d+" · "+reason;}
    }

    public static Info from(Context ctx,KnowledgeItem k){
        if(k==null)return new Info(false,"","","","","","","",0);
        String pkg=n(k.source),kind="",channel="",category="",reason="",disp="";long occurred=k.createdAt;
        boolean notification="NOTIFICATION".equalsIgnoreCase(k.type)||"notification".equalsIgnoreCase(k.source);
        try{
            JSONObject root=new JSONObject(n(k.metadataJson));
            occurred=root.optLong("occurred_at",occurred);reason=root.optString("filter_reason","");disp=root.optString("relevance_disposition","");
            JSONObject src=root.optJSONObject("source_metadata");
            if(src!=null){pkg=src.optString("package",pkg);occurred=src.optLong("posted_at",occurred);kind=src.optString("notification_kind","");channel=src.optString("channel_id","");category=src.optString("category","");notification=true;}
        }catch(Exception ignored){}
        if(notification&&friendlyKind(kind,category).isEmpty())kind=inferKind(pkg,n(k.title)+" "+n(k.rawText));
        String label=appLabel(ctx,pkg);if(label.isEmpty())label=knownAppLabel(pkg);
        return new Info(notification,label,pkg,kind,channel,category,reason,disp,occurred);
    }

    public static String sourceForCard(Context ctx,KnowledgeItem k){Info i=from(ctx,k);if(i.notification)return i.sourceLabel();return n(k==null?"":k.source);}

    private static String appLabel(Context ctx,String pkg){
        if(ctx==null||pkg==null||pkg.trim().isEmpty())return"";
        try{PackageManager pm=ctx.getPackageManager();ApplicationInfo ai=pm.getApplicationInfo(pkg,0);CharSequence label=pm.getApplicationLabel(ai);return label==null?"":label.toString().trim();}catch(Throwable ignored){return"";}
    }
    private static String knownAppLabel(String pkg){String p=n(pkg);if("com.google.android.gm".equals(p))return"Gmail";if("com.google.android.apps.messaging".equals(p))return"Google Messages";if("com.truecaller".equals(p))return"Truecaller";if("com.whatsapp".equals(p))return"WhatsApp";if("com.facebook.orca".equals(p))return"Messenger";if("com.instagram.android".equals(p))return"Instagram";if(p.contains("dialer")||p.contains("incallui")||p.contains("phone"))return"Phone";return"";}
    private static String inferKind(String pkg,String text){String p=n(pkg),t=n(text).toLowerCase(Locale.ROOT);if("com.google.android.gm".equals(p))return"email";if("com.google.android.apps.messaging".equals(p)||"com.whatsapp".equals(p)||"com.facebook.orca".equals(p))return"message";if(p.contains("dialer")||p.contains("incallui")||p.contains("phone"))return"call";if("com.truecaller".equals(p)){if(t.contains("spam"))return"spam";if(t.contains("missed call")||t.contains("incoming call"))return"call";return"caller id";}return"";}
    private static String friendlyKind(String kind,String category){
        String x=n(kind).toLowerCase(Locale.ROOT),c=n(category).toLowerCase(Locale.ROOT);
        if("message".equals(x)||"msg".equals(c)||"message".equals(c))return"Message";
        if("email".equals(x)||"email".equals(c))return"Email";
        if("call".equals(x)||"call".equals(c))return"Call";
        if("spam".equals(x))return"Spam alert";
        if("caller id".equals(x))return"Caller ID";
        if("promotion".equals(x)||"promo".equals(c))return"Promotion";
        if("social".equals(x)||"social".equals(c))return"Social";
        if("recommendation".equals(x)||"recommendation".equals(c))return"Recommendation";
        if(!x.isEmpty()&&!"notification".equals(x))return Character.toUpperCase(x.charAt(0))+x.substring(1);
        return"";
    }
    private static String friendlyDisposition(String x){String d=n(x).toUpperCase(Locale.ROOT);if("ACTION".equals(d))return"Kept as an action signal";if("WAITING".equals(d))return"Kept as a waiting signal";if("DECISION".equals(d))return"Kept as a decision signal";if("REVIEW".equals(d))return"Kept for review";if("CONTEXT".equals(d))return"Kept as context";return"";}
    private static String n(String s){return s==null?"":s.trim();}
}
