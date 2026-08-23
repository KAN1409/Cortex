package com.kareem.cortex;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.List;

/**
 * Production environment audit used by Cortex itself. It intentionally does not
 * depend on Termux: Termux remains a development/build laboratory only.
 */
public final class EnvironmentPreflight {
    private static final String PREF="cortex_environment_preflight",K_REPORT="report",K_AT="at";
    private EnvironmentPreflight(){}

    public static final class Report {
        public final long at,totalRam,availableRam,internalFree,modelStorageFree;
        public final int cpuCores,batteryPercent,thermalStatus,readyAssets,plannedAssets;
        public final String manufacturer,model,device,hardware,androidVersion,abis,llmRuntime,llmRuntimeInfo;
        public final boolean notificationAccess,microphone,calendarRead,calendarWrite,contacts,camera,coarseLocation,fineLocation,postNotifications,primaryModelReady,ocrReady,cloudAsrAvailable;
        public final String json;
        Report(long at,long totalRam,long availableRam,long internalFree,long modelStorageFree,int cpuCores,int batteryPercent,int thermalStatus,int readyAssets,int plannedAssets,String manufacturer,String model,String device,String hardware,String androidVersion,String abis,String llmRuntime,String llmRuntimeInfo,boolean notificationAccess,boolean microphone,boolean calendarRead,boolean calendarWrite,boolean contacts,boolean camera,boolean coarseLocation,boolean fineLocation,boolean postNotifications,boolean primaryModelReady,boolean ocrReady,boolean cloudAsrAvailable,String json){
            this.at=at;this.totalRam=totalRam;this.availableRam=availableRam;this.internalFree=internalFree;this.modelStorageFree=modelStorageFree;this.cpuCores=cpuCores;this.batteryPercent=batteryPercent;this.thermalStatus=thermalStatus;this.readyAssets=readyAssets;this.plannedAssets=plannedAssets;this.manufacturer=manufacturer;this.model=model;this.device=device;this.hardware=hardware;this.androidVersion=androidVersion;this.abis=abis;this.llmRuntime=llmRuntime;this.llmRuntimeInfo=llmRuntimeInfo;this.notificationAccess=notificationAccess;this.microphone=microphone;this.calendarRead=calendarRead;this.calendarWrite=calendarWrite;this.contacts=contacts;this.camera=camera;this.coarseLocation=coarseLocation;this.fineLocation=fineLocation;this.postNotifications=postNotifications;this.primaryModelReady=primaryModelReady;this.ocrReady=ocrReady;this.cloudAsrAvailable=cloudAsrAvailable;this.json=json;
        }
        public String summary(){
            StringBuilder s=new StringBuilder();s.append("AI assets ").append(readyAssets).append(" ready");if(plannedAssets>0)s.append(" • ").append(plannedAssets).append(" planned");s.append("\nRAM ").append(human(availableRam)).append(" free / ").append(human(totalRam));s.append(" • storage ").append(human(modelStorageFree)).append(" free");s.append("\nLocal LLM: ").append(primaryModelReady?"ready":"not ready").append(" • OCR: ").append(ocrReady?"ready":"not ready").append(" • notifications: ").append(notificationAccess?"connected":"not connected");return s.toString();
        }
    }

    public static Report run(Context context){
        Context c=context.getApplicationContext();long now=System.currentTimeMillis();
        ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();if(am!=null)am.getMemoryInfo(mi);
        long total=mi.totalMem,avail=mi.availMem,internal=free(c.getFilesDir());File modelDir=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);long modelFree=free(modelDir==null?c.getFilesDir():modelDir);
        int cores=Math.max(1,Runtime.getRuntime().availableProcessors());int battery=battery(c);int thermal=thermal(c);
        String abis=join(Build.SUPPORTED_ABIS);String runtime=LocalLlmRuntime.runtimeVersion();String runtimeInfo="";try{runtimeInfo=LocalLlmBridge.runtimeInfo();}catch(Throwable t){runtimeInfo=t.getClass().getSimpleName();}
        boolean notificationAccess=notificationAccess(c),mic=granted(c,Manifest.permission.RECORD_AUDIO),calR=granted(c,Manifest.permission.READ_CALENDAR),calW=granted(c,Manifest.permission.WRITE_CALENDAR),contacts=granted(c,Manifest.permission.READ_CONTACTS),camera=granted(c,Manifest.permission.CAMERA),coarse=granted(c,Manifest.permission.ACCESS_COARSE_LOCATION),fine=granted(c,Manifest.permission.ACCESS_FINE_LOCATION),post=Build.VERSION.SDK_INT<33||granted(c,Manifest.permission.POST_NOTIFICATIONS);
        boolean primary=LocalModelManager.installed(c),ocr=ArabicOcr.modelReady(c),cloudAsr=GroqKeyStore.has(c)||GeminiKeyStore.has(c)||CohereKeyStore.has(c);
        List<ModelAssetRegistry.Asset> assets=ModelAssetRegistry.inventory(c);int ready=0,planned=0;JSONArray aa=new JSONArray();for(ModelAssetRegistry.Asset a:assets){if(a.state==ModelAssetRegistry.State.READY)ready++;if(a.state==ModelAssetRegistry.State.PLANNED)planned++;try{aa.put(new JSONObject().put("id",a.id).put("role",a.role).put("name",a.name).put("state",a.state.name()).put("bytes",a.bytes).put("local",a.local).put("offline_required",a.requiredForOfflineBaseline).put("detail",a.detail));}catch(Exception ignored){}}
        JSONObject j=new JSONObject();try{
            j.put("version","preflight_001").put("at",now);
            j.put("device",new JSONObject().put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("device",Build.DEVICE).put("hardware",Build.HARDWARE).put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("abis",abis).put("cpu_cores",cores));
            j.put("resources",new JSONObject().put("ram_total",total).put("ram_available",avail).put("internal_free",internal).put("model_storage_free",modelFree).put("battery_percent",battery).put("thermal_status",thermal));
            j.put("runtime",new JSONObject().put("llm",runtime).put("system_info",runtimeInfo));
            j.put("permissions",new JSONObject().put("notification_access",notificationAccess).put("microphone",mic).put("calendar_read",calR).put("calendar_write",calW).put("contacts",contacts).put("camera",camera).put("coarse_location",coarse).put("fine_location",fine).put("post_notifications",post));
            j.put("capabilities",new JSONObject().put("primary_local_llm",primary).put("arabic_ocr",ocr).put("cloud_asr",cloudAsr).put("groq",GroqKeyStore.has(c)).put("gemini",GeminiKeyStore.has(c)).put("cohere",CohereKeyStore.has(c)).put("semantic_baseline",true));
            j.put("assets",aa);
        }catch(Exception ignored){}
        String json=j.toString();c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(K_REPORT,json).putLong(K_AT,now).apply();
        return new Report(now,total,avail,internal,modelFree,cores,battery,thermal,ready,planned,Build.MANUFACTURER,Build.MODEL,Build.DEVICE,Build.HARDWARE,Build.VERSION.RELEASE,abis,runtime,runtimeInfo,notificationAccess,mic,calR,calW,contacts,camera,coarse,fine,post,primary,ocr,cloudAsr,json);
    }

    public static String lastJson(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(K_REPORT,"");}
    public static long lastAt(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(K_AT,0);}

    private static boolean granted(Context c,String permission){return c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private static boolean notificationAccess(Context c){try{String x=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");return x!=null&&x.contains(c.getPackageName());}catch(Exception e){return false;}}
    private static int battery(Context c){try{BatteryManager b=(BatteryManager)c.getSystemService(Context.BATTERY_SERVICE);return b==null?-1:b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);}catch(Exception e){return -1;}}
    private static int thermal(Context c){if(Build.VERSION.SDK_INT<29)return -1;try{PowerManager p=(PowerManager)c.getSystemService(Context.POWER_SERVICE);return p==null?-1:p.getCurrentThermalStatus();}catch(Exception e){return -1;}}
    private static long free(File f){try{StatFs s=new StatFs(f.getAbsolutePath());return s.getAvailableBytes();}catch(Exception e){return 0;}}
    private static String join(String[] xs){if(xs==null||xs.length==0)return"";StringBuilder s=new StringBuilder();for(String x:xs){if(s.length()>0)s.append(',');s.append(x);}return s.toString();}
    private static String human(long b){if(b<=0)return"0 B";double g=b/1073741824.0;if(g>=1)return String.format(java.util.Locale.US,"%.1f GB",g);return String.format(java.util.Locale.US,"%.0f MB",b/1048576.0);}
}
