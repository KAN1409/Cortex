package com.kareem.cortex;

import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;

/** Standard Android Usage Access: foreground/background app timeline and recent app history. */
public final class PhoneUsageAccess {
    private PhoneUsageAccess(){}

    public static boolean has(Context c){
        try{AppOpsManager a=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);if(a==null)return false;int mode=a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName());return mode==AppOpsManager.MODE_ALLOWED;}catch(Throwable e){return false;}
    }

    public static void openSettings(Activity a){
        try{Intent i=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+a.getPackageName()));a.startActivity(i);}catch(Throwable e){try{a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Throwable ignored){}}
    }

    /** Returns number of UsageEvents copied into the local phone-context timeline, or -1 when access is missing. */
    public static int syncRecent(Context c,VaultDb db,long since){
        if(c==null||db==null||!has(c))return -1;PhoneContextStore.ensure(db);UsageStatsManager u=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);if(u==null)return 0;
        long end=System.currentTimeMillis(),start=Math.max(end-24L*60L*60L*1000L,Math.max(0,since));UsageEvents events=u.queryEvents(start,end);if(events==null)return 0;UsageEvents.Event e=new UsageEvents.Event();int seen=0,stored=0;
        while(events.hasNextEvent()&&seen<6000){events.getNextEvent(e);seen++;int type=e.getEventType();boolean fg=type==UsageEvents.Event.MOVE_TO_FOREGROUND,bg=type==UsageEvents.Event.MOVE_TO_BACKGROUND;
            if(Build.VERSION.SDK_INT>=29){fg=fg||type==UsageEvents.Event.ACTIVITY_RESUMED;bg=bg||type==UsageEvents.Event.ACTIVITY_PAUSED;}
            if(!fg&&!bg)continue;String pkg=n(e.getPackageName());if(pkg.isEmpty()||pkg.equals(c.getPackageName()))continue;String label=label(c,pkg);String cls=n(e.getClassName());JSONObject m=new JSONObject();try{m.put("usage_event_type",type);m.put("local_only",!PrivacyPolicy.canUseCloud(c,"phone_context"));}catch(Exception ignored){}
            long id=PhoneContextStore.record(db,"app_usage","usage_stats",pkg,label,cls,fg?"foreground":"background","",e.getTimeStamp(),m);if(id>0)stored++;
        }
        return stored;
    }

    public static String currentPackage(Context c){
        if(!has(c))return"";UsageStatsManager u=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);if(u==null)return"";long end=System.currentTimeMillis(),start=end-30L*60L*1000L;UsageEvents x=u.queryEvents(start,end);if(x==null)return"";UsageEvents.Event e=new UsageEvents.Event();String current="";long at=0;while(x.hasNextEvent()){x.getNextEvent(e);int type=e.getEventType();boolean fg=type==UsageEvents.Event.MOVE_TO_FOREGROUND;if(Build.VERSION.SDK_INT>=29)fg=fg||type==UsageEvents.Event.ACTIVITY_RESUMED;if(fg&&e.getTimeStamp()>=at){at=e.getTimeStamp();current=n(e.getPackageName());}}return current;
    }

    private static String label(Context c,String pkg){try{ApplicationInfo ai=c.getPackageManager().getApplicationInfo(pkg,0);CharSequence x=c.getPackageManager().getApplicationLabel(ai);return x==null?pkg:x.toString();}catch(Throwable ignored){return pkg;}}
    private static String n(String s){return s==null?"":s.trim();}
}
