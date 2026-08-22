package com.kareem.cortex;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import java.util.Locale;

public class NotificationCaptureService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        try{
            if(sbn==null||sbn.getNotification()==null)return;
            if(getPackageName().equals(sbn.getPackageName()))return;
            if(!PrivacyPolicy.canCollect(this,"notifications"))return;
            Bundle e=sbn.getNotification().extras;String title=str(e.getCharSequence(Notification.EXTRA_TITLE));String text=str(e.getCharSequence(Notification.EXTRA_TEXT));String big=str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));if(!big.isEmpty())text=big;
            if(title.isEmpty()&&text.isEmpty())return;
            String body=(title+(title.isEmpty()||text.isEmpty()?"":"\n")+text).trim();if(body.isEmpty())return;
            String pkg=sbn.getPackageName()==null?"":sbn.getPackageName();String tags="notification,"+safe(pkg);if(sensitive(body))tags+=" ,sensitive,local_only";
            VaultDb db=new VaultDb(this);String fp=Fingerprint.text(pkg+"|"+body+"|"+(sbn.getPostTime()/60000));long id=db.insert("NOTIFICATION",pkg,title.isEmpty()?"Notification":title,body,"Notifications",tags,"",fp,"{\"package\":\""+json(pkg)+"\",\"posted_at\":"+sbn.getPostTime()+"}");if(id>0)AnalysisQueue.kick(this,db,null);else db.close();
        }catch(Exception ignored){}
    }
    private static boolean sensitive(String s){String x=s.toLowerCase(Locale.US);return x.matches(".*\\b(otp|one[- ]?time password|verification code|cvv|pin code)\\b.*")||x.contains("كود التحقق")||x.contains("رمز التحقق")||x.contains("كلمة السر");}
    private static String str(CharSequence s){return s==null?"":s.toString().trim();}
    private static String safe(String s){return s==null?"":s.replaceAll("[^A-Za-z0-9_.-]","_");}
    private static String json(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
}
