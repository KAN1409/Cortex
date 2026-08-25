package com.kareem.cortex;

import android.content.Context;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/** Optional Shizuku system-context bridge for a bounded, read-only process snapshot. */
public final class ShizukuContextBridge {
    public static final int REQUEST_CODE=9117;
    private static final int MAX_OUTPUT_BYTES=512*1024,MAX_ROWS=220;
    private ShizukuContextBridge(){}

    public static boolean available(){try{return Shizuku.pingBinder()&&!Shizuku.isPreV11();}catch(Throwable e){return false;}}
    public static boolean granted(){try{return available()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Throwable e){return false;}}
    public static boolean needsRationale(){try{return available()&&!granted()&&Shizuku.shouldShowRequestPermissionRationale();}catch(Throwable e){return false;}}
    public static void requestPermission(){if(!available())return;try{if(!granted()&&!needsRationale())Shizuku.requestPermission(REQUEST_CODE);}catch(Throwable ignored){}}
    public static String status(){try{if(!Shizuku.pingBinder())return"Shizuku service not running";if(Shizuku.isPreV11())return"Shizuku version is too old";if(granted())return"Granted · service v"+Shizuku.getVersion()+" · uid "+Shizuku.getUid();if(needsRationale())return"Permission denied · allow Cortex from Shizuku";return"Shizuku running · permission required";}catch(Throwable e){return"Shizuku unavailable: "+e.getClass().getSimpleName();}}

    public static final class Snapshot {public final boolean ok;public final int processCount,storedCount;public final String detail;Snapshot(boolean o,int p,int s,String d){ok=o;processCount=p;storedCount=s;detail=d;}}

    /** Fixed internal shell command only; callers cannot supply arbitrary commands. */
    public static Snapshot captureProcessSnapshot(Context context,VaultDb db){
        if(context==null||db==null)return new Snapshot(false,0,0,"Missing Cortex context/database");
        if(!available())return new Snapshot(false,0,0,"Shizuku service is not running");
        if(!granted())return new Snapshot(false,0,0,"Shizuku permission is not granted");
        Object process=null;try{
            String[] cmd={"/system/bin/sh","-c","ps -A -o USER,PID,NAME 2>/dev/null || ps -A"};
            Method start=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);start.setAccessible(true);
            process=start.invoke(null,new Object[]{cmd,null,null});if(process==null)return new Snapshot(false,0,0,"Shizuku did not create the read-only process snapshot");
            InputStream in=(InputStream)invoke(process,"getInputStream");String output=readBounded(in,MAX_OUTPUT_BYTES);int exit=waitFor(process);
            ArrayList<Proc> rows=parse(output);ArrayList<PhoneContextStore.ProcessInfo> current=new ArrayList<>();LinkedHashSet<String> names=new LinkedHashSet<>();
            for(Proc p:rows){if(names.size()>=MAX_ROWS)break;if(p.name.isEmpty()||!names.add(p.name))continue;current.add(new PhoneContextStore.ProcessInfo(p.name,labelForProcess(context,p.name),p.user,p.pid));}
            long now=System.currentTimeMillis();int active=PhoneContextStore.updateProcessSnapshot(db,current,"shizuku",now);JSONObject summary=new JSONObject();summary.put("exit_code",exit);summary.put("parsed_processes",rows.size());summary.put("active_unique",active);summary.put("read_only",true);summary.put("shizuku_uid",safeUid());PhoneContextStore.record(db,"process_snapshot","shizuku","","System process snapshot","","snapshot",active+" visible running process name(s)",now,summary);
            return new Snapshot(exit==0||!rows.isEmpty(),rows.size(),active,(exit==0?"Read-only Shizuku process snapshot complete":"Process command returned "+exit)+" · "+rows.size()+" parsed · "+active+" active unique process name(s)");
        }catch(Throwable e){return new Snapshot(false,0,0,e.getClass().getSimpleName()+": "+safe(rootMessage(e)));}
        finally{destroy(process);}
    }

    private static ArrayList<Proc> parse(String output){ArrayList<Proc> out=new ArrayList<>();if(output==null)return out;String[] lines=output.split("\\r?\\n");for(String line:lines){String x=line.trim();if(x.isEmpty()||x.toUpperCase(Locale.ROOT).contains(" PID ")||x.startsWith("USER "))continue;String[] p=x.split("\\s+");if(p.length<2)continue;String user="",pid="",name="";if(p.length>=3&&digits(p[1])){user=p[0];pid=p[1];name=p[p.length-1];}else{for(int i=0;i<p.length;i++)if(digits(p[i])){pid=p[i];if(i>0)user=p[0];break;}name=p[p.length-1];}name=cleanName(name);if(name.isEmpty())continue;out.add(new Proc(user,pid,name));if(out.size()>=MAX_ROWS*2)break;}return out;}
    private static String cleanName(String s){String x=safe(s).trim();if(x.startsWith("[")&&x.endsWith("]"))return"";if(x.equals("ps")||x.equals("sh"))return"";return x.length()>180?x.substring(0,180):x;}
    private static String labelForProcess(Context c,String name){String pkg=name;int colon=pkg.indexOf(':');if(colon>0)pkg=pkg.substring(0,colon);try{CharSequence l=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0));return l==null?name:l.toString();}catch(Throwable ignored){return name;}}
    private static String readBounded(InputStream in,int max)throws IOException{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int total=0;for(int n;(n=x.read(buf))!=-1;){int take=Math.min(n,max-total);if(take>0)b.write(buf,0,take);total+=take;if(total>=max)break;}return b.toString("UTF-8");}}
    private static Object invoke(Object target,String name)throws Exception{Method m;try{m=target.getClass().getMethod(name);}catch(NoSuchMethodException e){m=target.getClass().getDeclaredMethod(name);}m.setAccessible(true);return m.invoke(target);}
    private static int waitFor(Object p){if(p==null)return-1;try{Object r=invoke(p,"waitFor");return r instanceof Integer?(Integer)r:-1;}catch(Throwable e){return-1;}}
    private static void destroy(Object p){if(p==null)return;try{invoke(p,"destroy");}catch(Throwable ignored){}}
    private static int safeUid(){try{return Shizuku.getUid();}catch(Throwable e){return-1;}}
    private static String rootMessage(Throwable e){Throwable x=e;for(int i=0;i<5&&x.getCause()!=null;i++)x=x.getCause();String m=x.getMessage();return m==null?x.getClass().getSimpleName():m;}
    private static boolean digits(String s){if(s==null||s.isEmpty())return false;for(int i=0;i<s.length();i++)if(!Character.isDigit(s.charAt(i)))return false;return true;}
    private static String safe(String s){return s==null?"":s;}
    private static final class Proc{final String user,pid,name;Proc(String u,String p,String n){user=safe(u);pid=safe(p);name=safe(n);}}
}
