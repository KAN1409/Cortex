package com.kareem.cortex;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Maps non-green health rows to safe user-driven remediation. */
public final class CortexRemediation {
    private CortexRemediation(){}

    public static boolean canHandle(String title){String x=n(title);return x.contains("permission")||x.contains("notification")||x.contains("screen")||x.contains("accessibility")||x.contains("screenshot folder")||x.contains("archived attachment")||x.contains("local qwen")||x.contains("external reasoning")||x.contains("openrouter")||x.contains("gemini")||x.contains("shizuku");}

    public static void open(Activity a,String title){String x=n(title);try{
        if(x.contains("permission")){requestRuntime(a);return;}
        if(x.contains("notification")){a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));return;}
        if(x.contains("screen")||x.contains("accessibility")){a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
        if(x.contains("screenshot folder")){a.startActivity(new Intent(a,VisualMemoryActivity.class));Toast.makeText(a,"Open Sync to reconnect Visual Memory to MediaStore. Advanced folder diagnostics remain available from Settings.",Toast.LENGTH_LONG).show();return;}
        if(x.contains("archived attachment")){a.startActivity(new Intent(a,VaultActivity.class));Toast.makeText(a,"These are legacy attachment paths, not a runtime permission. Open the source item to relink/re-capture missing originals; Cortex will not invent them.",Toast.LENGTH_LONG).show();return;}
        if(x.contains("local qwen")){a.startActivity(new Intent(a,SettingsActivity.class));Toast.makeText(a,"Local Qwen is optional while another reasoning route is healthy.",Toast.LENGTH_LONG).show();return;}
        if(x.contains("openrouter")){a.startActivity(new Intent(a,OpenRouterSettingsActivity.class));return;}
        if(x.contains("gemini")||x.contains("external reasoning")){a.startActivity(new Intent(a,GeminiSettingsActivity.class));return;}
        if(x.contains("shizuku")){Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+a.getPackageName()));a.startActivity(i);return;}
    }catch(Throwable e){Toast.makeText(a,"Could not open setup: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),Toast.LENGTH_LONG).show();}}

    private static void requestRuntime(Activity a){java.util.ArrayList<String> p=new java.util.ArrayList<>();if(Build.VERSION.SDK_INT>=33){if(a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);if(a.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!=android.content.pm.PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.READ_MEDIA_IMAGES);}if(a.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);if(a.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=android.content.pm.PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.READ_CALENDAR);if(a.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.READ_CONTACTS);if(!p.isEmpty())a.requestPermissions(p.toArray(new String[0]),8707);else Toast.makeText(a,"Standard runtime permissions are already granted. Tap specific amber rows for special-access setup.",Toast.LENGTH_LONG).show();}
    private static String n(String s){return s==null?"":s.toLowerCase(java.util.Locale.ROOT);}
}
