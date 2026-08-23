package com.kareem.cortex;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;

/** All allowed notifications enter the raw-signal layer first; only selected signals reach durable Cortex intelligence. */
public class NotificationCaptureService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        try{
            if(sbn==null||sbn.getNotification()==null)return;if(getPackageName().equals(sbn.getPackageName()))return;if(!PrivacyPolicy.canCollect(this,"notifications"))return;
            Notification n=sbn.getNotification();Bundle e=n.extras;String title=str(e.getCharSequence(Notification.EXTRA_TITLE)),text=str(e.getCharSequence(Notification.EXTRA_TEXT)),big=str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));if(!big.isEmpty())text=big;if(title.isEmpty()&&text.isEmpty())return;String body=(title+(title.isEmpty()||text.isEmpty()?"":"\n")+text).trim();if(body.isEmpty())return;String pkg=sbn.getPackageName()==null?"":sbn.getPackageName();boolean ongoing=(n.flags&Notification.FLAG_ONGOING_EVENT)!=0;
            JSONObject meta=new JSONObject();meta.put("package",pkg);meta.put("posted_at",sbn.getPostTime());meta.put("notification_id",sbn.getId());meta.put("ongoing",ongoing);if(sbn.getKey()!=null)meta.put("notification_key",sbn.getKey());if(sbn.getGroupKey()!=null)meta.put("group_key",sbn.getGroupKey());if(n.category!=null)meta.put("category",n.category);
            MasterRelevanceFilter.Signal signal=new MasterRelevanceFilter.Signal("notification",pkg,title,body,meta.toString(),sbn.getPostTime(),ongoing);VaultDb db=new VaultDb(this);long signalId=RawSignalStore.capture(db,signal),itemId=signalId>0?RawSignalStore.promotedItemId(db,signalId):0,threadId=signalId>0?RawSignalStore.threadId(db,signalId):0;if(threadId>0)ThreadModelAdjudicator.enqueue(this,threadId,signalId);if(itemId>0)AnalysisQueue.kick(this,db,null);else db.close();
        }catch(Throwable error){
            android.util.Log.e("CortexNotification","Notification ingestion failed",error);VaultDb d=null;try{d=new VaultDb(this);JSONObject meta=new JSONObject();meta.put("package",sbn==null?"":String.valueOf(sbn.getPackageName()));DiagnosticsLog.error(d,"NotificationCaptureService","on_notification_posted",error,"NOTIFICATION_INGEST",0,0,0,0,0,meta);}catch(Throwable ignored){}finally{if(d!=null)try{d.close();}catch(Throwable ignored){}}
        }
    }
    private static String str(CharSequence s){return s==null?"":s.toString().trim();}
}
