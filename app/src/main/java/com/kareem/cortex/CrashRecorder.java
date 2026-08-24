package com.kareem.cortex;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Minimal process crash marker. No DB access, network, model loading or Android UI work. */
public final class CrashRecorder {
    private static volatile boolean installed=false;
    private static final String FILE="last_crash.txt";
    private CrashRecorder(){}

    public static synchronized void install(Context context){
        if(installed||context==null)return;
        installed=true;
        final Context app=context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{write(app,thread,error);}catch(Throwable ignored){}
            if(previous!=null){previous.uncaughtException(thread,error);return;}
            try{android.os.Process.killProcess(android.os.Process.myPid());}catch(Throwable ignored){}
            System.exit(10);
        });
    }

    public static File file(Context context){return new File(context.getFilesDir(),FILE);}
    public static String read(Context context,int maxChars){
        File f=file(context);if(!f.exists())return"";
        try(BufferedReader r=new BufferedReader(new FileReader(f))){StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null&&b.length()<Math.max(1000,maxChars)){b.append(line).append('\n');}String s=b.toString();return s.length()<=maxChars?s:s.substring(0,maxChars)+"\n…";}catch(Throwable ignored){return"";}
    }
    public static void clear(Context context){try{File f=file(context);if(f.exists())f.delete();}catch(Throwable ignored){}}

    private static void write(Context context,Thread thread,Throwable error)throws Exception{
        File target=file(context),tmp=new File(context.getFilesDir(),FILE+".part");
        try(FileOutputStream fos=new FileOutputStream(tmp,false);PrintWriter p=new PrintWriter(new OutputStreamWriter(fos,"UTF-8"))){
            p.println("CORTEX_LAST_CRASH_V1");
            p.println("time="+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date()));
            p.println("thread="+(thread==null?"unknown":thread.getName()));
            p.println("sdk="+Build.VERSION.SDK_INT);
            p.println("device="+safe(Build.MANUFACTURER)+" "+safe(Build.MODEL));
            p.println("process="+android.os.Process.myPid());
            p.println();
            if(error!=null)error.printStackTrace(p);else p.println("Unknown uncaught error");
            p.flush();fos.getFD().sync();
        }
        if(target.exists())target.delete();
        if(!tmp.renameTo(target)){copy(tmp,target);tmp.delete();}
    }
    private static void copy(File a,File b)throws IOException{try(InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b)){byte[] buf=new byte[8192];for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);}}
    private static String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
}
