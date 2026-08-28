package com.kareem.cortex;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;

/**
 * Temporary direct-capture compatibility listener. Production ownership moves to Cortex Relay after
 * the Relay->Cortex acceptance test is green; while this listener remains present it feeds the exact
 * same Tier-0 -> CognitiveAdjudicatorV2 pipeline and cannot create a parallel semantic authority.
 */
public class NotificationCaptureService extends NotificationListenerService {
    @Override public void onListenerConnected(){super.onListenerConnected();try{StatusBarNotification[] xs=getActiveNotifications();if(xs!=null)for(StatusBarNotification x:xs)ingest(x,"active_snapshot");}catch(Throwable ignored){}}
    @Override public void onNotificationPosted(StatusBarNotification sbn){ingest(sbn,"posted");}
    @Override public void onNotificationRemoved(StatusBarNotification sbn){super.onNotificationRemoved(sbn);if(sbn==null)return;VaultDb db=null;try{db=new VaultDb(this);PhoneContextStore.ensure(db);String pkg=sbn.getPackageName()==null?"":sbn.getPackageName();JSONObject m=new JSONObject().put("notification_id",sbn.getId()).put("notification_key",sbn.getKey()==null?"":sbn.getKey()).put("removal",true);PhoneContextStore.record(db,"notification_context","notification_listener",pkg,label(pkg),"","removed","",System.currentTimeMillis(),m);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}

    private void ingest(StatusBarNotification sbn,String captureMode){VaultDb db=null;try{if(sbn==null||sbn.getNotification()==null)return;if(getPackageName().equals(sbn.getPackageName()))return;if(!PrivacyPolicy.canCollect(this,"notifications"))return;Notification n=sbn.getNotification();Bundle e=n.extras;String pkg=sbn.getPackageName()==null?"":sbn.getPackageName();CommunicationEvidenceNormalizer.Result normalized=CommunicationEvidenceNormalizer.fromNotification(pkg,n,e);String title=normalized.title,text=normalized.body;String body=(title+(title.isEmpty()||text.isEmpty()?"":"\n")+text).trim();boolean ongoing=(n.flags&Notification.FLAG_ONGOING_EVENT)!=0;
            JSONObject meta=new JSONObject();meta.put("capture_kind","android_notification");meta.put("capture_mode",captureMode);meta.put("package",pkg);meta.put("posted_at",sbn.getPostTime());meta.put("notification_id",sbn.getId());meta.put("ongoing",ongoing);meta.put("has_visible_text",!body.isEmpty());meta.put("notification_kind",normalized.kind);meta.put("normalized_source",normalized.source);meta.put("communication",normalized.communication);if(!normalized.personHint.isEmpty())meta.put("person_hint",normalized.personHint);if(sbn.getKey()!=null)meta.put("notification_key",sbn.getKey());if(sbn.getGroupKey()!=null)meta.put("group_key",sbn.getGroupKey());if(n.category!=null)meta.put("category",n.category);if(Build.VERSION.SDK_INT>=26&&n.getChannelId()!=null)meta.put("channel_id",n.getChannelId());String template=str(e.getString(Notification.EXTRA_TEMPLATE));if(!template.isEmpty())meta.put("template",template);String sub=str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));if(!sub.isEmpty())meta.put("sub_text",sub);String summary=str(e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));if(!summary.isEmpty())meta.put("summary_text",summary);NotificationIdentityHintsV4.enrich(meta,n,e);
            db=new VaultDb(this);PhoneContextStore.ensure(db);PhoneContextStore.record(db,"notification_context","notification_listener",pkg,label(pkg),normalized.personHint,"notification_"+captureMode,body,sbn.getPostTime(),meta);
            if(body.isEmpty())return;
            MasterRelevanceFilter.Signal signal=new MasterRelevanceFilter.Signal("notification",pkg,title,body,meta.toString(),sbn.getPostTime(),ongoing);
            long signalId=NotificationSignalIngressV1.capture(db,signal),itemId=signalId>0?RawSignalStore.promotedItemId(db,signalId):0,threadId=signalId>0?RawSignalStore.threadId(db,signalId):0;
            if(signalId>0){
                NotificationEnrichmentEngine.enrich(db,signalId,itemId,threadId,signal);
                long personEntityId=CanonicalPersonResolver.resolveSignal(db,signalId,normalized,meta);
                CrossSourceSituationStitcher.stitchSignal(db,signalId,personEntityId);
                if(CognitiveSignalV2.CognitiveState.PENDING_ADJUDICATION.name().equals(RawSignalStore.cognitiveState(db,signalId)))
                    CognitiveAdjudicatorV2.enqueue(this,threadId,signalId);
            }
            // Compatibility projection only for pre-V2/already-promoted rows. New V2 notifications
            // project after validated adjudication inside CognitiveAdjudicatorV2.
            if(itemId>0){AnalysisQueue.kick(this,null,null);CognitiveRealtimeProjectionV4.schedule(this,signalId);}
        }catch(Throwable error){android.util.Log.e("CortexNotification","Notification ingestion failed",error);VaultDb d=null;try{d=new VaultDb(this);JSONObject meta=new JSONObject();meta.put("package",sbn==null?"":String.valueOf(sbn.getPackageName()));DiagnosticsLog.error(d,"NotificationCaptureService","notification_ingest",error,"NOTIFICATION_INGEST",0,0,0,0,0,meta);}catch(Throwable ignored){}finally{if(d!=null)try{d.close();}catch(Throwable ignored){}}}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
    private String label(String pkg){if(pkg==null||pkg.isEmpty())return"Notification";try{ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0);CharSequence x=getPackageManager().getApplicationLabel(ai);return x==null?pkg:x.toString();}catch(Throwable e){return pkg;}}
    private static String str(CharSequence s){return s==null?"":s.toString().trim();}
}
