package com.kareem.cortex;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Authoritative inventory of real user-access gates Cortex can benefit from on this device. */
public final class AccessGateRegistry {
    public enum Kind { RUNTIME, SPECIAL_ACCESS, HEALTH, OPTIONAL, BUILT_IN }
    public static final class Gate {
        public final String key,title,why,status;public final Kind kind;public final boolean active,recommended;
        Gate(String key,String title,String why,Kind kind,boolean active,boolean recommended,String status){this.key=key;this.title=title;this.why=why;this.kind=kind;this.active=active;this.recommended=recommended;this.status=status;}
    }
    private AccessGateRegistry(){}

    public static List<Gate> snapshot(Context c){
        ArrayList<Gate> out=new ArrayList<>();
        boolean mic=granted(c,Manifest.permission.RECORD_AUDIO);
        out.add(new Gate("microphone","Microphone","Voice capture, Quick Voice and the recording widget.",Kind.RUNTIME,mic,true,mic?"GRANTED":"NEEDS ACCESS"));

        boolean appNotifications=notificationsEnabled(c);
        out.add(new Gate("app_notifications","Cortex notifications","Foreground recording status and useful Cortex alerts.",Kind.RUNTIME,appNotifications,true,appNotifications?"GRANTED":"NEEDS ACCESS"));

        boolean listener=notificationListener(c);
        out.add(new Gate("notification_listener","All-notification awareness","Lets Cortex observe incoming app notifications locally, then decide relevance after seeing the signal.",Kind.SPECIAL_ACCESS,listener,true,listener?"ACTIVE":"NEEDS ACCESS"));

        boolean accessibility=CortexScreenAccessibilityService.connected()||accessibilityEnabled(c);
        out.add(new Gate("accessibility","Current app / screen context","Bounded Accessibility events provide app/window context and explicit screen-understanding support; password fields remain excluded.",Kind.SPECIAL_ACCESS,accessibility,true,accessibility?"ACTIVE":"NEEDS ACCESS"));

        boolean usage=PhoneUsageAccess.has(c);
        out.add(new Gate("usage","Usage Access","Grounds recent/foreground app context so Cortex can answer what you were doing on the phone.",Kind.SPECIAL_ACCESS,usage,true,usage?"ACTIVE":"NEEDS ACCESS"));

        boolean contacts=granted(c,Manifest.permission.READ_CONTACTS);
        out.add(new Gate("contacts","Contacts read","Grounds person identity for People memory and action drafts.",Kind.RUNTIME,contacts,false,contacts?"GRANTED":"OPTIONAL · NOT GRANTED"));

        boolean calendar=granted(c,Manifest.permission.READ_CALENDAR);
        out.add(new Gate("calendar","Calendar read","Imports your schedule for grounded follow-ups and planning. External writes remain approval-first drafts owned by the calendar app.",Kind.RUNTIME,calendar,false,calendar?"GRANTED":"OPTIONAL · NOT GRANTED"));

        boolean battery=batteryUnrestricted(c);
        out.add(new Gate("battery","Background reliability","Removing battery optimization can make scheduled local context work more reliably on aggressive Android firmware.",Kind.SPECIAL_ACCESS,battery,false,battery?"UNRESTRICTED":"OPTIONAL · OPTIMIZED"));

        int hc=HealthConnectBridge.sdkStatus(c);boolean healthAvailable=hc==3;
        out.add(new Gate("health","Health Connect","Read-only Samsung Health / compatible health metrics after separate Health Connect consent.",Kind.HEALTH,healthAvailable,false,healthAvailable?"AVAILABLE · CHECK HEALTH SCOPES":"UNAVAILABLE / PROVIDER UPDATE NEEDED"));

        boolean shizuku=ShizukuContextBridge.granted();boolean shizukuAvailable=ShizukuContextBridge.available();
        out.add(new Gate("shizuku","Shizuku","Optional read-only process snapshot for extra local system context. Standard Cortex awareness does not depend on it.",Kind.OPTIONAL,shizuku,false,shizuku?"ACTIVE":shizukuAvailable?"OPTIONAL · NEEDS ACCESS":"OPTIONAL · SERVER NOT RUNNING"));

        out.add(new Gate("saf","Files / photos through Android picker","Cortex imports chosen files, scans and photos through Storage Access Framework URI grants; broad storage access is intentionally unnecessary.",Kind.BUILT_IN,true,false,"READY · NO EXTRA PERMISSION"));
        return out;
    }

    public static int activeRecommended(Context c){int n=0;for(Gate g:snapshot(c))if(g.recommended&&g.active)n++;return n;}
    public static int recommendedCount(Context c){int n=0;for(Gate g:snapshot(c))if(g.recommended)n++;return n;}

    public static boolean granted(Context c,String permission){return Build.VERSION.SDK_INT<23||c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    public static boolean notificationsEnabled(Context c){
        boolean runtime=Build.VERSION.SDK_INT<33||granted(c,Manifest.permission.POST_NOTIFICATIONS);if(!runtime)return false;
        try{NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);return n==null||Build.VERSION.SDK_INT<24||n.areNotificationsEnabled();}catch(Throwable e){return runtime;}
    }
    public static boolean notificationListener(Context c){try{String x=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");return x!=null&&x.contains(c.getPackageName());}catch(Throwable e){return false;}}
    public static boolean accessibilityEnabled(Context c){try{String x=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return x!=null&&x.contains(c.getPackageName());}catch(Throwable e){return false;}}
    public static boolean batteryUnrestricted(Context c){try{PowerManager p=(PowerManager)c.getSystemService(Context.POWER_SERVICE);return p!=null&&p.isIgnoringBatteryOptimizations(c.getPackageName());}catch(Throwable e){return false;}}
}
