package com.kareem.cortex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Copies lightweight accessibility events and persists them off the service/UI thread. */
public final class PhoneContextCollector {
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-phone-context");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static volatile String lastPackage="";
    private static volatile long lastPackageAt=0;
    private PhoneContextCollector(){}

    public static void onAccessibilityEvent(Context context,AccessibilityEvent event){
        if(context==null||event==null||!PrivacyPolicy.canCollect(context,"phone_context"))return;
        final Context app=context.getApplicationContext();
        final String pkg=n(event.getPackageName()==null?null:event.getPackageName().toString());
        if(pkg.isEmpty()||pkg.equals(app.getPackageName()))return;
        final String cls=n(event.getClassName()==null?null:event.getClassName().toString());
        final int type=event.getEventType();
        final long when=event.getEventTime()>0?event.getEventTime():System.currentTimeMillis();
        boolean password=false;String preview="";AccessibilityNodeInfo src=null;
        try{src=event.getSource();password=src!=null&&src.isPassword();}catch(Throwable ignored){}
        if(password)preview="<protected field>";else preview=eventText(event);
        final boolean protectedField=password;final String text=preview;final String label=appLabel(app,pkg);final int windowId=event.getWindowId();
        try{EXEC.execute(()->persist(app,pkg,label,cls,type,text,protectedField,windowId,when));}catch(RejectedExecutionException ignored){}
    }

    private static void persist(Context app,String pkg,String label,String cls,int type,String text,boolean protectedField,int windowId,long when){
        VaultDb db=null;try{
            db=new VaultDb(app);PhoneContextStore.ensure(db);
            String eventType=eventName(type);String kind="window_context";
            if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||type==AccessibilityEvent.TYPE_WINDOWS_CHANGED)kind="app_transition";
            else if(type==AccessibilityEvent.TYPE_VIEW_CLICKED)kind="interaction";
            JSONObject m=new JSONObject();m.put("window_id",windowId);m.put("protected_field",protectedField);m.put("accessibility_event_type",type);m.put("background_monitoring",true);m.put("local_only",!PrivacyPolicy.canUseCloud(app,"phone_context"));
            PhoneContextStore.record(db,kind,"accessibility",pkg,label,cls,eventType,text,when,m);
            if("app_transition".equals(kind)&&(!pkg.equals(lastPackage)||when-lastPackageAt>15_000L)){
                lastPackage=pkg;lastPackageAt=when;
                app.getSharedPreferences("cortex_phone_context",Context.MODE_PRIVATE).edit().putString("current_package",pkg).putString("current_app",label).putLong("current_at",when).apply();
            }
        }catch(Throwable e){if(db!=null)try{DiagnosticsLog.error(db,"PhoneContextCollector","accessibility_event",e,"PHONE_CONTEXT_ACCESSIBILITY",0,0,0,0,0,null);}catch(Throwable ignored){}
        }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private static String eventText(AccessibilityEvent e){LinkedHashSet<String> xs=new LinkedHashSet<>();try{for(CharSequence c:e.getText())add(xs,c);}catch(Throwable ignored){}try{add(xs,e.getContentDescription());}catch(Throwable ignored){}StringBuilder b=new StringBuilder();for(String x:xs){if(b.length()>0)b.append(" · ");if(b.length()+x.length()>800)break;b.append(x);}return b.toString();}
    private static void add(Set<String> out,CharSequence s){if(s==null)return;String x=s.toString().replaceAll("\\s+"," ").trim();if(x.length()<2)return;if(x.length()>300)x=x.substring(0,300)+"…";out.add(x);}
    private static String eventName(int t){if(t==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return"window_state_changed";if(t==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)return"window_content_changed";if(t==AccessibilityEvent.TYPE_WINDOWS_CHANGED)return"windows_changed";if(t==AccessibilityEvent.TYPE_VIEW_CLICKED)return"view_clicked";return"event_"+t;}
    private static String appLabel(Context c,String pkg){try{ApplicationInfo ai=c.getPackageManager().getApplicationInfo(pkg,0);CharSequence x=c.getPackageManager().getApplicationLabel(ai);return x==null?pkg:x.toString();}catch(Throwable ignored){return pkg;}}
    private static String n(String s){return s==null?"":s.trim();}
}
