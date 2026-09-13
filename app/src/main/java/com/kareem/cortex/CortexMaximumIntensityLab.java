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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maximum-intensity, production-embedded verification profile.
 * It runs only against isolated synthetic data plus read-only production state checks.
 * It never mutates canonical knowledge, user evidence, or attention state.
 */
public final class CortexMaximumIntensityLab {
    private static final int DB_QUICK_CHECK_LOOPS=50;
    private static final int SELF_TEST_LOOPS=5;
    private static final int DOC_LOOPS_PER_TYPE=20;
    private static final int STORAGE_FILES=128;
    private static final int STORAGE_BYTES_PER_FILE=128*1024;
    private static final int CONCURRENCY_THREADS=4;
    private static final int CONCURRENCY_LOOPS=25;

    private CortexMaximumIntensityLab(){}

    private static final class Extra {
        int pass,warn,fail;
        final JSONArray tests=new JSONArray();
        final CortexEndToEndLab.Listener listener;
        Extra(CortexEndToEndLab.Listener l){listener=l;}
        void emit(String id,String status,String detail,JSONObject evidence){
            try{
                JSONObject o=new JSONObject();o.put("id",id);o.put("status",status);o.put("detail",detail==null?"":detail);
                if(evidence!=null)o.put("evidence",evidence);tests.put(o);
            }catch(Throwable ignored){}
            if("PASS".equals(status))pass++;else if("WARN".equals(status))warn++;else fail++;
            if(listener!=null)try{listener.onStage(id,status,detail==null?"":detail);}catch(Throwable ignored){}
        }
        void pass(String id,String d,JSONObject e){emit(id,"PASS",d,e);}void warn(String id,String d,JSONObject e){emit(id,"WARN",d,e);}void fail(String id,String d,JSONObject e){emit(id,"FAIL",d,e);}
    }

    public static CortexEndToEndLab.RunResult run(Context context,CortexEndToEndLab.Listener listener){
        Context ctx=context.getApplicationContext();
        CortexEndToEndLab.RunResult base=CortexEndToEndLab.run(ctx,listener);
        Extra x=new Extra(listener);
        long started=System.currentTimeMillis();
        JSONObject report=base.report;
        try{report.put("schemaVersion",3);report.put("mode","MAXIMUM_INTENSITY");report.put("maximumIntensityStartedAt",started);}catch(Throwable ignored){}

        runtimeEnvelope(ctx,x,report);
        capabilityMatrix(ctx,x,report);
        repeatedDatabaseIntegrity(ctx,x,report);
        repeatedFunctionalSelfTest(ctx,x,report);
        extractorStress(ctx,base.dir,x,report);
        storageStress(ctx,base.dir,x,report);
        concurrentReadStress(ctx,x,report);
        fileProviderProbe(ctx,base.reportFile,x,report);
        reportRoundTrip(base.reportFile,x,report);
        runtimeEnvelopeAfter(ctx,x,report);

        int pass=base.pass+x.pass,warn=base.warn+x.warn,fail=base.fail+x.fail;
        long ended=System.currentTimeMillis();
        try{
            report.put("maximumIntensityTests",x.tests);
            JSONObject profile=new JSONObject();
            profile.put("dbQuickCheckLoops",DB_QUICK_CHECK_LOOPS);profile.put("selfTestLoops",SELF_TEST_LOOPS);
            profile.put("documentExtractionLoopsPerType",DOC_LOOPS_PER_TYPE);profile.put("storageFiles",STORAGE_FILES);
            profile.put("storageBytesPerFile",STORAGE_BYTES_PER_FILE);profile.put("concurrencyThreads",CONCURRENCY_THREADS);profile.put("concurrencyLoopsPerThread",CONCURRENCY_LOOPS);
            report.put("maximumIntensityProfile",profile);
            JSONObject summary=new JSONObject();summary.put("pass",pass);summary.put("warn",warn);summary.put("fail",fail);summary.put("ok",fail==0);
            report.put("summary",summary);report.put("maximumIntensityEndedAt",ended);report.put("maximumIntensityDurationMs",ended-started);
            write(base.reportFile,report.toString(2));
            write(new File(base.dir,"maximum-intensity-summary.md"),markdown(base.runId,pass,warn,fail,ended-started));
            ctx.getSharedPreferences("cortex_e2e_lab",Context.MODE_PRIVATE).edit()
                    .putString("latest_run_id",base.runId).putString("latest_report",base.reportFile.getAbsolutePath())
                    .putLong("latest_at",ended).putBoolean("latest_ok",fail==0).putString("latest_mode","MAXIMUM_INTENSITY").commit();
        }catch(Throwable e){
            x.fail("maximum_report_finalize",e.getClass().getSimpleName()+": "+safe(e.getMessage()),null);
            fail++;
        }
        return new CortexEndToEndLab.RunResult(base.runId,base.dir,base.reportFile,report,pass,warn,fail);
    }

    private static void capabilityMatrix(Context ctx,Extra x,JSONObject report){
        VaultDb db=null;try{
            db=new VaultDb(ctx);JSONArray arr=new JSONArray();int active=0,ready=0,access=0,setup=0,failed=0,unverified=0;
            for(CortexCapabilityRegistry.Capability c:CortexCapabilityRegistry.all()){
                CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(ctx,db,c);
                JSONObject o=new JSONObject();o.put("number",c.number);o.put("key",c.key);o.put("title",c.title);o.put("status",s.status);o.put("detail",s.detail);arr.put(o);
                if(CortexCapabilityRegistry.ACTIVE.equals(s.status))active++;else if(CortexCapabilityRegistry.READY.equals(s.status))ready++;else if(CortexCapabilityRegistry.NEEDS_ACCESS.equals(s.status))access++;else if(CortexCapabilityRegistry.NEEDS_SETUP.equals(s.status))setup++;else if(CortexCapabilityRegistry.FAILED.equals(s.status))failed++;else if(CortexCapabilityRegistry.NOT_VERIFIED.equals(s.status))unverified++;
            }
            JSONObject e=new JSONObject();e.put("count",arr.length());e.put("active",active);e.put("ready",ready);e.put("needsAccess",access);e.put("needsSetup",setup);e.put("failed",failed);e.put("notVerified",unverified);report.put("capabilityMatrix",arr);
            if(arr.length()!=43||unverified>0)x.fail("capability_matrix_43","Expected 43 fully evaluated capabilities; count="+arr.length()+" · NOT_VERIFIED="+unverified,e);
            else if(failed>0)x.fail("capability_matrix_43","All 43 evaluated, but "+failed+" capability state(s) are FAILED",e);
            else if(access>0||setup>0)x.warn("capability_matrix_43","All 43 evaluators executed; some features need access/setup on this phone",e);
            else x.pass("capability_matrix_43","All 43 capability evaluators completed without FAILED/NOT_VERIFIED",e);
        }catch(Throwable e){x.fail("capability_matrix_43",e.toString(),null);}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private static void repeatedDatabaseIntegrity(Context ctx,Extra x,JSONObject report){
        long start=SystemClock.elapsedRealtime();int good=0;String last="";
        for(int i=0;i<DB_QUICK_CHECK_LOOPS;i++){
            VaultDb db=null;Cursor c=null;try{db=new VaultDb(ctx);c=db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)",null);last=c.moveToFirst()?safe(c.getString(0)):"";if("ok".equalsIgnoreCase(last))good++;}catch(Throwable ignored){}finally{if(c!=null)try{c.close();}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}
        }
        long ms=SystemClock.elapsedRealtime()-start;try{JSONObject e=new JSONObject().put("iterations",DB_QUICK_CHECK_LOOPS).put("passed",good).put("durationMs",ms).put("lastResult",last);report.put("databaseStress",e);if(good==DB_QUICK_CHECK_LOOPS)x.pass("database_integrity_stress",good+"/"+DB_QUICK_CHECK_LOOPS+" SQLite quick_check iterations passed",e);else x.fail("database_integrity_stress",good+"/"+DB_QUICK_CHECK_LOOPS+" passed",e);}catch(Throwable e){x.fail("database_integrity_stress",e.toString(),null);}
    }

    private static void repeatedFunctionalSelfTest(Context ctx,Extra x,JSONObject report){
        long start=SystemClock.elapsedRealtime();int good=0,totalPass=0,totalWarn=0,totalFail=0;JSONArray loops=new JSONArray();
        for(int i=0;i<SELF_TEST_LOOPS;i++)try{CortexFunctionalSelfTest.Report r=CortexFunctionalSelfTest.run(ctx);if(r.fail==0)good++;totalPass+=r.pass;totalWarn+=r.warn;totalFail+=r.fail;loops.put(new JSONObject().put("iteration",i+1).put("pass",r.pass).put("warn",r.warn).put("fail",r.fail));}catch(Throwable e){totalFail++;}
        long ms=SystemClock.elapsedRealtime()-start;try{JSONObject ev=new JSONObject().put("iterations",SELF_TEST_LOOPS).put("successfulIterations",good).put("aggregatePass",totalPass).put("aggregateWarn",totalWarn).put("aggregateFail",totalFail).put("durationMs",ms);report.put("functionalSelfTestStress",loops);if(good==SELF_TEST_LOOPS&&totalFail==0)x.pass("functional_self_test_stress",SELF_TEST_LOOPS+" consecutive production self-tests passed",ev);else x.fail("functional_self_test_stress",good+"/"+SELF_TEST_LOOPS+" consecutive self-tests had zero failures",ev);}catch(Throwable e){x.fail("functional_self_test_stress",e.toString(),null);}
    }

    private static void extractorStress(Context ctx,File dir,Extra x,JSONObject report){
        String[][] specs={{"fixture.xlsx","CORTEX_XLSX_2026"},{"fixture.docx","CORTEX_DOCX_2026"},{"fixture.pptx","CORTEX_PPTX_2026"},{"fixture.pdf","CORTEX_PDF_2026"}};
        JSONArray all=new JSONArray();int expected=specs.length*DOC_LOOPS_PER_TYPE,good=0;long start=SystemClock.elapsedRealtime();
        for(String[] s:specs){File f=new File(dir,s[0]);long typeStart=SystemClock.elapsedRealtime();int typeGood=0;String engine="";for(int i=0;i<DOC_LOOPS_PER_TYPE;i++)try{AnalysisResult r=DocumentExtractorRegistry.extract(ctx,f,s[0]);engine=r==null?"":safe(r.engine);if(r!=null&&safe(r.extractedText).contains(s[1])){typeGood++;good++;}}catch(Throwable ignored){}try{all.put(new JSONObject().put("name",s[0]).put("loops",DOC_LOOPS_PER_TYPE).put("passed",typeGood).put("durationMs",SystemClock.elapsedRealtime()-typeStart).put("engine",engine));}catch(Throwable ignored){}}
        long ms=SystemClock.elapsedRealtime()-start;try{JSONObject ev=new JSONObject().put("expectedExtractions",expected).put("passedExtractions",good).put("durationMs",ms);report.put("documentExtractionStress",all);if(good==expected)x.pass("document_extraction_stress",good+"/"+expected+" repeated real extractor calls returned the expected tokens",ev);else x.fail("document_extraction_stress",good+"/"+expected+" repeated extractions passed",ev);}catch(Throwable e){x.fail("document_extraction_stress",e.toString(),null);}
    }

    private static void storageStress(Context ctx,File dir,Extra x,JSONObject report){
        File root=new File(dir,"storage-stress");root.mkdirs();Random rnd=new Random(0xC0A7E5L);int good=0;long bytes=0,start=SystemClock.elapsedRealtime();byte[] data=new byte[STORAGE_BYTES_PER_FILE];
        try{
            for(int i=0;i<STORAGE_FILES;i++){rnd.nextBytes(data);File f=new File(root,String.format(Locale.ROOT,"probe-%03d.bin",i));String expected=sha256(data);try(FileOutputStream out=new FileOutputStream(f)){out.write(data);out.getFD().sync();}bytes+=f.length();if(expected.equals(sha256(f)))good++;}
            long ms=SystemClock.elapsedRealtime()-start;JSONObject ev=new JSONObject().put("files",STORAGE_FILES).put("verified",good).put("bytes",bytes).put("durationMs",ms);report.put("storageStress",ev);if(good==STORAGE_FILES)x.pass("storage_stress",good+" files / "+bytes+" bytes survived write+fsync+read+SHA-256 verification",ev);else x.fail("storage_stress",good+"/"+STORAGE_FILES+" files verified",ev);
        }catch(Throwable e){x.fail("storage_stress",e.toString(),null);}finally{deleteTree(root);}
    }

    private static void concurrentReadStress(Context ctx,Extra x,JSONObject report){
        AtomicInteger ok=new AtomicInteger(),fail=new AtomicInteger();CountDownLatch latch=new CountDownLatch(CONCURRENCY_THREADS);long start=SystemClock.elapsedRealtime();
        for(int t=0;t<CONCURRENCY_THREADS;t++)new Thread(()->{try{for(int i=0;i<CONCURRENCY_LOOPS;i++){VaultDb db=null;Cursor c=null;try{db=new VaultDb(ctx);c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM knowledge_items",null);if(c.moveToFirst()){c.getLong(0);ok.incrementAndGet();}else fail.incrementAndGet();}catch(Throwable e){fail.incrementAndGet();}finally{if(c!=null)try{c.close();}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}}}finally{latch.countDown();}},"cortex-e2e-db-"+t).start();
        boolean completed=false;try{completed=latch.await(90,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        int expected=CONCURRENCY_THREADS*CONCURRENCY_LOOPS;long ms=SystemClock.elapsedRealtime()-start;try{JSONObject ev=new JSONObject().put("threads",CONCURRENCY_THREADS).put("loopsPerThread",CONCURRENCY_LOOPS).put("expectedReads",expected).put("successfulReads",ok.get()).put("failedReads",fail.get()).put("completed",completed).put("durationMs",ms);report.put("concurrentReadStress",ev);if(completed&&ok.get()==expected&&fail.get()==0)x.pass("concurrent_database_read_stress",expected+" concurrent production DB reads completed without error",ev);else x.fail("concurrent_database_read_stress","completed="+completed+" · ok="+ok.get()+"/"+expected+" · failed="+fail.get(),ev);}catch(Throwable e){x.fail("concurrent_database_read_stress",e.toString(),null);}
    }

    private static void fileProviderProbe(Context ctx,File reportFile,Extra x,JSONObject report){
        try{android.net.Uri u=FileProvider.getUriForFile(ctx,ctx.getPackageName()+".feedback.files",reportFile);JSONObject ev=new JSONObject().put("uriScheme",u.getScheme()).put("authority",u.getAuthority());report.put("fileProviderProbe",ev);if("content".equals(u.getScheme()))x.pass("file_provider_share_probe","Report resolves to a grantable content:// URI",ev);else x.fail("file_provider_share_probe","Unexpected URI scheme "+u.getScheme(),ev);}catch(Throwable e){x.fail("file_provider_share_probe",e.toString(),null);}
    }

    private static void reportRoundTrip(File reportFile,Extra x,JSONObject report){
        try{String raw=read(reportFile);JSONObject parsed=new JSONObject(raw);boolean ok=!parsed.optString("runId","").isEmpty()&&parsed.optJSONObject("summary")!=null;JSONObject ev=new JSONObject().put("bytes",reportFile.length()).put("roundTripParse",ok);report.put("reportRoundTrip",ev);if(ok)x.pass("report_json_round_trip","Generated JSON report parses back successfully",ev);else x.fail("report_json_round_trip","Generated report did not retain required fields",ev);}catch(Throwable e){x.fail("report_json_round_trip",e.toString(),null);}
    }

    private static void runtimeEnvelope(Context ctx,Extra x,JSONObject report){snapshot(ctx,x,report,"runtime_before","runtimeBefore");}
    private static void runtimeEnvelopeAfter(Context ctx,Extra x,JSONObject report){snapshot(ctx,x,report,"runtime_after","runtimeAfter");}
    private static void snapshot(Context ctx,Extra x,JSONObject report,String id,String key){
        try{ActivityManager am=(ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);StatFs fs=new StatFs(ctx.getFilesDir().getAbsolutePath());JSONObject ev=new JSONObject().put("pssKb",Debug.getPss()).put("javaUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("javaMaxBytes",Runtime.getRuntime().maxMemory()).put("systemAvailBytes",mi.availMem).put("systemLowMemory",mi.lowMemory).put("diskAvailBytes",fs.getAvailableBytes());report.put(key,ev);if(mi.lowMemory)x.warn(id,"Android reports low-memory state during the intensive run",ev);else x.pass(id,"Runtime memory/storage envelope captured",ev);}catch(Throwable e){x.warn(id,e.toString(),null);}
    }

    private static void write(File f,String s)throws Exception{try(FileOutputStream out=new FileOutputStream(f,false)){out.write((s==null?"":s).getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
    private static String read(File f)throws Exception{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private static String sha256(File f)throws Exception{MessageDigest m=MessageDigest.getInstance("SHA-256");try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)m.update(b,0,n);}return hex(m.digest());}
    private static String sha256(byte[] b)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(b));}
    private static String hex(byte[] d){StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    private static String markdown(String runId,int pass,int warn,int fail,long ms){return "# Cortex Maximum-Intensity Embedded E2E\n\nRun: `"+runId+"`\n\nResult: **"+(fail>0?"FAIL":(warn>0?"PASS WITH WARNINGS":"PASS"))+"**\n\n- Pass: "+pass+"\n- Warning: "+warn+"\n- Fail: "+fail+"\n- Intensive duration: "+ms+" ms\n\nAttach `report.json` to ChatGPT for evidence-based diagnosis.\n";}
    private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)deleteTree(c);}try{f.delete();}catch(Throwable ignored){}}
    private static String safe(String s){return s==null?"":s;}
}
