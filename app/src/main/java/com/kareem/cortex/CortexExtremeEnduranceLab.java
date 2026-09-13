package com.kareem.cortex;

import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Debug;
import android.os.StatFs;
import android.os.SystemClock;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deliberately expensive, user-triggered physical-device endurance profile.
 * Synthetic fixtures remain isolated from canonical Cortex evidence.
 * This class never repairs/deletes canonical records; attachment reconciliation is diagnostic only.
 */
public final class CortexExtremeEnduranceLab {
    private static final int DB_QUICK_CHECK_LOOPS=1000;
    private static final int SELF_TEST_LOOPS=100;
    private static final int DOC_LOOPS_PER_TYPE=250;
    private static final int STORAGE_FILES=2048;
    private static final int STORAGE_BYTES_PER_FILE=1024*1024;
    private static final int CONCURRENCY_THREADS=16;
    private static final int CONCURRENCY_LOOPS=250;
    private static final int FILE_PROVIDER_LOOPS=250;
    private static final int REPORT_ROUND_TRIP_LOOPS=250;
    private static final int GC_PRESSURE_CYCLES=100;
    private static final int MEMORY_SAMPLE_INTERVAL_MS=1000;
    private static final int SOAK_DURATION_MINUTES=120;
    private static final long MIN_FREE_SPACE_BYTES=3L*1024L*1024L*1024L;

    private CortexExtremeEnduranceLab(){}

    private static final class Extra {
        int pass,warn,fail;
        final JSONArray tests=new JSONArray();
        final CortexEndToEndLab.Listener listener;
        Extra(CortexEndToEndLab.Listener l){listener=l;}
        void emit(String id,String status,String detail,JSONObject evidence){
            try{JSONObject o=new JSONObject().put("id",id).put("status",status).put("detail",safe(detail));if(evidence!=null)o.put("evidence",evidence);tests.put(o);}catch(Throwable ignored){}
            if("PASS".equals(status))pass++;else if("WARN".equals(status))warn++;else fail++;
            if(listener!=null)try{listener.onStage(id,status,safe(detail));}catch(Throwable ignored){}
        }
        void pass(String id,String d,JSONObject e){emit(id,"PASS",d,e);} void warn(String id,String d,JSONObject e){emit(id,"WARN",d,e);} void fail(String id,String d,JSONObject e){emit(id,"FAIL",d,e);}
    }

    public static CortexEndToEndLab.RunResult run(Context context,CortexEndToEndLab.Listener listener){
        Context ctx=context.getApplicationContext();
        CortexEndToEndLab.RunResult base=CortexMaximumIntensityLab.run(ctx,listener);
        Extra x=new Extra(listener);
        long started=System.currentTimeMillis();
        JSONObject report=base.report;
        try{report.put("schemaVersion",4);report.put("mode","EXTREME_ENDURANCE");report.put("extremeStartedAt",started);}catch(Throwable ignored){}

        attachmentForensics(ctx,x,report);
        repeatedFunctionalSelfTest(ctx,x,report);
        repeatedDatabaseIntegrity(ctx,x,report);
        extractorStress(ctx,base.dir,x,report);
        storageStress(ctx,base.dir,x,report);
        concurrentReadStress(ctx,x,report);
        fileProviderStress(ctx,base.reportFile,x,report);
        reportRoundTripStress(base.reportFile,x,report);
        gcPressure(ctx,x,report);
        orchestrationCoverage(x,report);
        soak(ctx,x,report);

        int pass=base.pass+x.pass,warn=base.warn+x.warn,fail=base.fail+x.fail;
        long ended=System.currentTimeMillis();
        try{
            report.put("extremeEnduranceTests",x.tests);
            JSONObject p=new JSONObject();
            p.put("dbQuickCheckLoops",DB_QUICK_CHECK_LOOPS).put("selfTestLoops",SELF_TEST_LOOPS)
                    .put("documentExtractionLoopsPerType",DOC_LOOPS_PER_TYPE).put("storageFiles",STORAGE_FILES)
                    .put("storageBytesPerFile",STORAGE_BYTES_PER_FILE).put("concurrencyThreads",CONCURRENCY_THREADS)
                    .put("concurrencyLoopsPerThread",CONCURRENCY_LOOPS).put("fileProviderShareLoops",FILE_PROVIDER_LOOPS)
                    .put("reportRoundTripLoops",REPORT_ROUND_TRIP_LOOPS).put("gcPressureCycles",GC_PRESSURE_CYCLES)
                    .put("memorySamplingIntervalMs",MEMORY_SAMPLE_INTERVAL_MS).put("soakDurationMinutes",SOAK_DURATION_MINUTES)
                    .put("requestedColdStartCycles",50).put("requestedWarmStartCycles",100)
                    .put("requestedBackgroundForegroundCycles",100).put("requestedProcessRecreationCycles",25);
            report.put("extremeEnduranceProfile",p);
            JSONObject s=new JSONObject().put("pass",pass).put("warn",warn).put("fail",fail).put("ok",fail==0);
            report.put("summary",s).put("extremeEndedAt",ended).put("extremeDurationMs",ended-started);
            write(base.reportFile,report.toString(2));
            ctx.getSharedPreferences("cortex_e2e_lab",Context.MODE_PRIVATE).edit()
                    .putString("latest_run_id",base.runId).putString("latest_report",base.reportFile.getAbsolutePath())
                    .putLong("latest_at",ended).putBoolean("latest_ok",fail==0).putString("latest_mode","EXTREME_ENDURANCE").commit();
        }catch(Throwable e){x.fail("extreme_report_finalize",e.getClass().getSimpleName()+": "+safe(e.getMessage()),null);fail++;}
        return new CortexEndToEndLab.RunResult(base.runId,base.dir,base.reportFile,report,pass,warn,fail);
    }

    private static void attachmentForensics(Context ctx,Extra x,JSONObject report){
        VaultDb db=null;Cursor c=null;
        try{
            db=new VaultDb(ctx);c=db.getReadableDatabase().rawQuery("SELECT id,attachment_path FROM knowledge_items WHERE COALESCE(attachment_path,'')<>''",null);
            int total=0,valid=0,missing=0,unreadable=0,relocated=0;JSONArray sample=new JSONArray();
            ArrayList<File> roots=new ArrayList<>();roots.add(ctx.getFilesDir());roots.add(ctx.getNoBackupFilesDir());File ext=ctx.getExternalFilesDir(null);if(ext!=null)roots.add(ext);
            Map<String,File> byName=indexFiles(roots,6,25000);
            while(c.moveToNext()){
                total++;long id=c.getLong(0);String path=safe(c.getString(1));File f=new File(path);String state;
                if(f.isFile()&&f.canRead()){valid++;state="FOUND_CANONICAL";}
                else if(f.exists()){unreadable++;state="UNREADABLE";}
                else {File alt=byName.get(f.getName());if(alt!=null&&alt.isFile()&&alt.canRead()){relocated++;state="FOUND_RELOCATED";}else{missing++;state="TRULY_MISSING";}}
                if(!"FOUND_CANONICAL".equals(state)&&sample.length()<200)sample.put(new JSONObject().put("id",id).put("path",path).put("state",state));
            }
            JSONObject ev=new JSONObject().put("total",total).put("valid",valid).put("missing",missing).put("unreadable",unreadable).put("relocated",relocated).put("nonCanonicalSample",sample);
            report.put("attachmentIntegrityForensics",ev);
            if(missing==0&&unreadable==0&&relocated==0)x.pass("attachment_integrity_forensics",total+" attachment reference(s) resolve canonically",ev);
            else x.fail("attachment_integrity_forensics","valid="+valid+" · relocated="+relocated+" · unreadable="+unreadable+" · truly missing="+missing,ev);
        }catch(Throwable e){x.fail("attachment_integrity_forensics",e.toString(),null);}finally{if(c!=null)try{c.close();}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private static Map<String,File> indexFiles(ArrayList<File> roots,int depth,int limit){
        HashMap<String,File> out=new HashMap<>();ArrayList<File> q=new ArrayList<>(roots);ArrayList<Integer> d=new ArrayList<>();for(int i=0;i<q.size();i++)d.add(0);
        for(int i=0;i<q.size()&&out.size()<limit;i++){File f=q.get(i);int level=d.get(i);File[] children;try{children=f==null?null:f.listFiles();}catch(Throwable e){children=null;}if(children==null)continue;for(File ch:children){if(ch.isFile())out.putIfAbsent(ch.getName(),ch);else if(level<depth&&ch.isDirectory()){q.add(ch);d.add(level+1);}if(out.size()>=limit)break;}}
        return out;
    }

    private static void repeatedFunctionalSelfTest(Context ctx,Extra x,JSONObject report){
        long start=SystemClock.elapsedRealtime();int good=0,totalPass=0,totalWarn=0,totalFail=0;JSONArray loops=new JSONArray();
        for(int i=0;i<SELF_TEST_LOOPS;i++){
            long before=SystemClock.elapsedRealtime();JSONObject it=new JSONObject();
            try{
                CortexFunctionalSelfTest.Report r=CortexFunctionalSelfTest.run(ctx);if(r.fail==0)good++;totalPass+=r.pass;totalWarn+=r.warn;totalFail+=r.fail;
                JSONArray tests=new JSONArray();for(String line:r.lines){String status=line.startsWith("PASS")?"PASS":line.startsWith("WARN")?"WARN":line.startsWith("FAIL")?"FAIL":"UNKNOWN";String[] p=line.split("\\n",2);String head=p.length>0?p[0]:line;String detail=p.length>1?p[1]:"";String id=head.replaceFirst("^(PASS|WARN|FAIL) · ","");tests.put(new JSONObject().put("id",id).put("status",status).put("detail",detail));}
                it.put("iteration",i+1).put("coldStart",false).put("pass",r.pass).put("warn",r.warn).put("fail",r.fail).put("tests",tests).put("metrics",r.metrics).put("durationMs",SystemClock.elapsedRealtime()-before).put("processUptimeMs",SystemClock.uptimeMillis()).put("pssKb",Debug.getPss()).put("threadCount",Thread.getAllStackTraces().size());
            }catch(Throwable e){
                totalFail++;
                try{it.put("iteration",i+1).put("fail",1).put("exception",e.getClass().getName()).put("message",safe(e.getMessage())).put("durationMs",SystemClock.elapsedRealtime()-before);}catch(Throwable ignored){}
            }
            loops.put(it);
        }
        long ms=SystemClock.elapsedRealtime()-start;try{JSONObject ev=new JSONObject().put("iterations",SELF_TEST_LOOPS).put("successfulIterations",good).put("aggregatePass",totalPass).put("aggregateWarn",totalWarn).put("aggregateFail",totalFail).put("durationMs",ms);report.put("extremeFunctionalSelfTestStress",loops);if(good==SELF_TEST_LOOPS&&totalFail==0)x.pass("extreme_functional_self_test",SELF_TEST_LOOPS+"/"+SELF_TEST_LOOPS+" warm-process production self-tests had zero failures",ev);else x.fail("extreme_functional_self_test",good+"/"+SELF_TEST_LOOPS+" warm-process self-tests had zero failures",ev);}catch(Throwable e){x.fail("extreme_functional_self_test",e.toString(),null);}
    }

    private static void repeatedDatabaseIntegrity(Context ctx,Extra x,JSONObject report){
        long start=SystemClock.elapsedRealtime();int good=0;JSONArray failures=new JSONArray();
        for(int i=0;i<DB_QUICK_CHECK_LOOPS;i++){VaultDb db=null;Cursor c=null;try{db=new VaultDb(ctx);c=db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)",null);String v=c.moveToFirst()?safe(c.getString(0)):"";if("ok".equalsIgnoreCase(v))good++;else if(failures.length()<50)failures.put(new JSONObject().put("iteration",i+1).put("result",v));}catch(Throwable e){if(failures.length()<50)try{failures.put(new JSONObject().put("iteration",i+1).put("exception",e.toString()));}catch(Throwable ignored){}}finally{if(c!=null)try{c.close();}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}}
        try{JSONObject ev=new JSONObject().put("iterations",DB_QUICK_CHECK_LOOPS).put("passed",good).put("failures",failures).put("durationMs",SystemClock.elapsedRealtime()-start);report.put("extremeDatabaseStress",ev);if(good==DB_QUICK_CHECK_LOOPS)x.pass("extreme_database_integrity",good+"/"+DB_QUICK_CHECK_LOOPS+" SQLite quick_check iterations passed",ev);else x.fail("extreme_database_integrity",good+"/"+DB_QUICK_CHECK_LOOPS+" passed",ev);}catch(Throwable e){x.fail("extreme_database_integrity",e.toString(),null);}
    }

    private static void extractorStress(Context ctx,File dir,Extra x,JSONObject report){
        String[][] specs={{"fixture.xlsx","CORTEX_XLSX_2026"},{"fixture.docx","CORTEX_DOCX_2026"},{"fixture.pptx","CORTEX_PPTX_2026"},{"fixture.pdf","CORTEX_PDF_2026"}};JSONArray all=new JSONArray();int expected=specs.length*DOC_LOOPS_PER_TYPE,good=0;long start=SystemClock.elapsedRealtime();
        for(String[] s:specs){File f=new File(dir,s[0]);int typeGood=0;long ts=SystemClock.elapsedRealtime();JSONArray failures=new JSONArray();for(int i=0;i<DOC_LOOPS_PER_TYPE;i++)try{AnalysisResult r=DocumentExtractorRegistry.extract(ctx,f,s[0]);if(r!=null&&safe(r.extractedText).contains(s[1])){typeGood++;good++;}else if(failures.length()<25)failures.put(i+1);}catch(Throwable e){if(failures.length()<25)try{failures.put(new JSONObject().put("iteration",i+1).put("exception",e.toString()));}catch(Throwable ignored){}}try{all.put(new JSONObject().put("name",s[0]).put("loops",DOC_LOOPS_PER_TYPE).put("passed",typeGood).put("failures",failures).put("durationMs",SystemClock.elapsedRealtime()-ts));}catch(Throwable ignored){}}
        try{JSONObject ev=new JSONObject().put("expectedExtractions",expected).put("passedExtractions",good).put("durationMs",SystemClock.elapsedRealtime()-start);report.put("extremeDocumentExtractionStress",all);if(good==expected)x.pass("extreme_document_extraction",good+"/"+expected+" repeated extractor calls returned expected tokens",ev);else x.fail("extreme_document_extraction",good+"/"+expected+" repeated extractions passed",ev);}catch(Throwable e){x.fail("extreme_document_extraction",e.toString(),null);}
    }

    private static void storageStress(Context ctx,File dir,Extra x,JSONObject report){
        StatFs fs=new StatFs(ctx.getFilesDir().getAbsolutePath());long free=fs.getAvailableBytes();long planned=(long)STORAGE_FILES*STORAGE_BYTES_PER_FILE;
        if(free<planned+MIN_FREE_SPACE_BYTES){try{JSONObject ev=new JSONObject().put("freeBytes",free).put("plannedBytes",planned).put("requiredReserveBytes",MIN_FREE_SPACE_BYTES);report.put("extremeStorageStress",ev);x.warn("extreme_storage_stress","Skipped 2 GiB storage torture test to preserve a 3 GiB free-space reserve",ev);}catch(Throwable ignored){}return;}
        File root=new File(dir,"extreme-storage-stress");root.mkdirs();Random rnd=new Random(0xC0A7E5L);byte[] data=new byte[STORAGE_BYTES_PER_FILE];int good=0;long bytes=0,start=SystemClock.elapsedRealtime();
        try{for(int i=0;i<STORAGE_FILES;i++){rnd.nextBytes(data);File f=new File(root,String.format(Locale.ROOT,"probe-%04d.bin",i));String expected=sha256(data);try(FileOutputStream out=new FileOutputStream(f)){out.write(data);out.getFD().sync();}bytes+=f.length();if(expected.equals(sha256(f)))good++;}JSONObject ev=new JSONObject().put("files",STORAGE_FILES).put("verified",good).put("bytes",bytes).put("durationMs",SystemClock.elapsedRealtime()-start);report.put("extremeStorageStress",ev);if(good==STORAGE_FILES)x.pass("extreme_storage_stress",good+"/"+STORAGE_FILES+" 1 MiB files survived write+fsync+read+SHA-256",ev);else x.fail("extreme_storage_stress",good+"/"+STORAGE_FILES+" files verified",ev);}catch(Throwable e){x.fail("extreme_storage_stress",e.toString(),null);}finally{deleteTree(root);}
    }

    private static void concurrentReadStress(Context ctx,Extra x,JSONObject report){
        AtomicInteger ok=new AtomicInteger(),bad=new AtomicInteger();CountDownLatch latch=new CountDownLatch(CONCURRENCY_THREADS);long start=SystemClock.elapsedRealtime();
        for(int t=0;t<CONCURRENCY_THREADS;t++)new Thread(()->{try{for(int i=0;i<CONCURRENCY_LOOPS;i++){VaultDb db=null;Cursor c=null;try{db=new VaultDb(ctx);c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM knowledge_items",null);if(c.moveToFirst()){c.getLong(0);ok.incrementAndGet();}else bad.incrementAndGet();}catch(Throwable e){bad.incrementAndGet();}finally{if(c!=null)try{c.close();}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}}}finally{latch.countDown();}},"cortex-extreme-db-"+t).start();
        boolean completed=false;try{completed=latch.await(10,TimeUnit.MINUTES);}catch(InterruptedException e){Thread.currentThread().interrupt();}int expected=CONCURRENCY_THREADS*CONCURRENCY_LOOPS;
        try{JSONObject ev=new JSONObject().put("threads",CONCURRENCY_THREADS).put("loopsPerThread",CONCURRENCY_LOOPS).put("expectedReads",expected).put("successfulReads",ok.get()).put("failedReads",bad.get()).put("completed",completed).put("durationMs",SystemClock.elapsedRealtime()-start);report.put("extremeConcurrentReadStress",ev);if(completed&&ok.get()==expected&&bad.get()==0)x.pass("extreme_concurrent_database_reads",expected+"/"+expected+" concurrent reads completed",ev);else x.fail("extreme_concurrent_database_reads","completed="+completed+" · ok="+ok.get()+"/"+expected+" · failed="+bad.get(),ev);}catch(Throwable e){x.fail("extreme_concurrent_database_reads",e.toString(),null);}
    }

    private static void fileProviderStress(Context ctx,File reportFile,Extra x,JSONObject report){int good=0;JSONArray failures=new JSONArray();for(int i=0;i<FILE_PROVIDER_LOOPS;i++)try{android.net.Uri u=FileProvider.getUriForFile(ctx,ctx.getPackageName()+".feedback.files",reportFile);if("content".equals(u.getScheme()))good++;else if(failures.length()<25)failures.put(i+1);}catch(Throwable e){if(failures.length()<25)try{failures.put(new JSONObject().put("iteration",i+1).put("exception",e.toString()));}catch(Throwable ignored){}}try{JSONObject ev=new JSONObject().put("iterations",FILE_PROVIDER_LOOPS).put("passed",good).put("failures",failures);report.put("extremeFileProviderStress",ev);if(good==FILE_PROVIDER_LOOPS)x.pass("extreme_file_provider_share",good+"/"+FILE_PROVIDER_LOOPS+" content URI resolutions passed",ev);else x.fail("extreme_file_provider_share",good+"/"+FILE_PROVIDER_LOOPS+" passed",ev);}catch(Throwable e){x.fail("extreme_file_provider_share",e.toString(),null);}}

    private static void reportRoundTripStress(File reportFile,Extra x,JSONObject report){int good=0;JSONArray failures=new JSONArray();for(int i=0;i<REPORT_ROUND_TRIP_LOOPS;i++)try{JSONObject p=new JSONObject(read(reportFile));if(!p.optString("runId","").isEmpty()&&p.optJSONObject("summary")!=null)good++;else if(failures.length()<25)failures.put(i+1);}catch(Throwable e){if(failures.length()<25)try{failures.put(new JSONObject().put("iteration",i+1).put("exception",e.toString()));}catch(Throwable ignored){}}try{JSONObject ev=new JSONObject().put("iterations",REPORT_ROUND_TRIP_LOOPS).put("passed",good).put("failures",failures);report.put("extremeReportRoundTripStress",ev);if(good==REPORT_ROUND_TRIP_LOOPS)x.pass("extreme_report_round_trip",good+"/"+REPORT_ROUND_TRIP_LOOPS+" JSON round-trips passed",ev);else x.fail("extreme_report_round_trip",good+"/"+REPORT_ROUND_TRIP_LOOPS+" passed",ev);}catch(Throwable e){x.fail("extreme_report_round_trip",e.toString(),null);}}

    private static void gcPressure(Context ctx,Extra x,JSONObject report){JSONArray samples=new JSONArray();long start=SystemClock.elapsedRealtime();try{for(int i=0;i<GC_PRESSURE_CYCLES;i++){byte[][] blocks=new byte[8][];for(int j=0;j<blocks.length;j++)blocks[j]=new byte[512*1024];blocks=null;if((i%5)==0){System.gc();SystemClock.sleep(20);}samples.put(memorySample(ctx,i+1));}JSONObject ev=new JSONObject().put("cycles",GC_PRESSURE_CYCLES).put("samples",samples).put("durationMs",SystemClock.elapsedRealtime()-start);report.put("gcPressure",ev);x.pass("gc_pressure","Completed "+GC_PRESSURE_CYCLES+" bounded allocation/GC pressure cycles without uncaught failure",ev);}catch(Throwable e){x.fail("gc_pressure",e.toString(),null);}}

    private static void orchestrationCoverage(Extra x,JSONObject report){try{JSONObject ev=new JSONObject().put("coldStartCyclesExecuted",0).put("backgroundForegroundCyclesExecuted",0).put("processRecreationCyclesExecuted",0).put("reason","A single in-process test runner cannot truthfully claim OS process death/relaunch or task background/foreground transitions. These require an external or durable relaunch orchestrator.");report.put("extremeLifecycleOrchestration",ev);x.warn("lifecycle_orchestration_gap","Cold-start/process-recreation/background↔foreground cycles are not faked; this build records them as unexecuted until a durable relaunch orchestrator is added",ev);}catch(Throwable ignored){}}

    private static void soak(Context ctx,Extra x,JSONObject report){
        long duration=SOAK_DURATION_MINUTES*60_000L,start=SystemClock.elapsedRealtime(),end=start+duration;JSONArray samples=new JSONArray();int count=0;boolean low=false;
        try{while(SystemClock.elapsedRealtime()<end){JSONObject s=memorySample(ctx,++count);samples.put(s);low|=s.optBoolean("systemLowMemory",false);SystemClock.sleep(MEMORY_SAMPLE_INTERVAL_MS);}JSONObject ev=new JSONObject().put("durationMinutes",SOAK_DURATION_MINUTES).put("sampleCount",count).put("systemLowMemoryObserved",low).put("samples",samples);report.put("extremeSoak",ev);if(low)x.fail("extreme_120_minute_soak","System low-memory state was observed during soak",ev);else x.pass("extreme_120_minute_soak","120-minute in-process soak completed without systemLowMemory",ev);}catch(Throwable e){x.fail("extreme_120_minute_soak",e.toString(),null);}
    }

    private static JSONObject memorySample(Context ctx,int index)throws Exception{ActivityManager am=(ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);Runtime rt=Runtime.getRuntime();StatFs fs=new StatFs(ctx.getFilesDir().getAbsolutePath());return new JSONObject().put("index",index).put("elapsedRealtimeMs",SystemClock.elapsedRealtime()).put("pssKb",Debug.getPss()).put("javaUsedBytes",rt.totalMemory()-rt.freeMemory()).put("javaMaxBytes",rt.maxMemory()).put("systemAvailableBytes",mi.availMem).put("systemLowMemory",mi.lowMemory).put("threadCount",Thread.getAllStackTraces().size()).put("privateStorageFreeBytes",fs.getAvailableBytes());}

    private static String sha256(byte[] b)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");md.update(b);return hex(md.digest());}
    private static String sha256(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[256*1024];try(FileInputStream in=new FileInputStream(f)){int n;while((n=in.read(b))>0)md.update(b,0,n);}return hex(md.digest());}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();}
    private static String read(File f)throws Exception{byte[] b=new byte[(int)Math.min(Integer.MAX_VALUE,f.length())];int off=0;try(FileInputStream in=new FileInputStream(f)){int n;while(off<b.length&&(n=in.read(b,off,b.length-off))>0)off+=n;}return new String(b,0,off,StandardCharsets.UTF_8);}
    private static void write(File f,String s)throws Exception{try(FileOutputStream out=new FileOutputStream(f,false)){out.write(s.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
    private static void deleteTree(File f){if(f==null||!f.exists())return;File[] a=f.listFiles();if(a!=null)for(File x:a)deleteTree(x);try{f.delete();}catch(Throwable ignored){}}
    private static String safe(String s){return s==null?"":s;}
}
