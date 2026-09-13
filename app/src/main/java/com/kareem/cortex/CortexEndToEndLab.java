package com.kareem.cortex;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import androidx.core.content.ContextCompat;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Production-embedded, user-triggered end-to-end verification.
 * Runs on the installed phone against real Cortex production code paths.
 * Synthetic fixtures are isolated under files/e2e-runs and never become canonical evidence.
 */
public final class CortexEndToEndLab {
    public interface Listener { void onStage(String id,String status,String detail); }
    private CortexEndToEndLab(){}

    public static final class RunResult {
        public final String runId;
        public final File dir;
        public final File reportFile;
        public final JSONObject report;
        public final int pass,warn,fail;
        RunResult(String id,File d,File f,JSONObject r,int p,int w,int x){runId=id;dir=d;reportFile=f;report=r;pass=p;warn=w;fail=x;}
        public boolean ok(){return fail==0;}
    }

    private static final class Acc {
        final Context ctx; final Listener listener; final JSONArray tests=new JSONArray(); int pass,warn,fail;
        Acc(Context c,Listener l){ctx=c;listener=l;}
        void emit(String id,String status,String detail){
            try{JSONObject o=new JSONObject();o.put("id",id);o.put("status",status);o.put("detail",detail==null?"":detail);tests.put(o);}catch(Throwable ignored){}
            if("PASS".equals(status))pass++;else if("WARN".equals(status))warn++;else fail++;
            if(listener!=null)try{listener.onStage(id,status,detail==null?"":detail);}catch(Throwable ignored){}
        }
        void pass(String id,String d){emit(id,"PASS",d);} void warn(String id,String d){emit(id,"WARN",d);} void fail(String id,String d){emit(id,"FAIL",d);}
    }

    public static RunResult run(Context context,Listener listener){
        Context ctx=context.getApplicationContext();
        String runId="e2e-"+System.currentTimeMillis();
        File root=new File(ctx.getFilesDir(),"e2e-runs"); File dir=new File(root,runId); dir.mkdirs();
        long started=System.currentTimeMillis(); Acc a=new Acc(ctx,listener);
        JSONObject report=new JSONObject();
        try{
            report.put("schemaVersion",1);report.put("runId",runId);report.put("startedAt",started);
            identity(ctx,a,report);
            storage(ctx,dir,a,report);
            database(ctx,a,report);
            functional(ctx,a,report);
            documentFixtures(ctx,dir,a,report);
            components(ctx,a,report);
            permissions(ctx,a,report);
            updateCheckpoint(ctx,a,report);
        }catch(Throwable e){a.fail("harness",e.getClass().getSimpleName()+": "+safe(e.getMessage()));}
        long ended=System.currentTimeMillis();
        try{
            JSONObject summary=new JSONObject();summary.put("pass",a.pass);summary.put("warn",a.warn);summary.put("fail",a.fail);summary.put("ok",a.fail==0);
            report.put("tests",a.tests);report.put("summary",summary);report.put("endedAt",ended);report.put("durationMs",ended-started);
            report.put("device",deviceJson());
        }catch(Throwable ignored){}
        File out=new File(dir,"report.json");
        try{write(out,report.toString(2));}catch(Throwable ignored){}
        try{write(new File(dir,"report.md"),markdown(report,a));}catch(Throwable ignored){}
        try{
            ctx.getSharedPreferences("cortex_e2e_lab",Context.MODE_PRIVATE).edit()
                    .putString("latest_run_id",runId).putString("latest_report",out.getAbsolutePath())
                    .putLong("latest_at",ended).putBoolean("latest_ok",a.fail==0).apply();
        }catch(Throwable ignored){}
        return new RunResult(runId,dir,out,report,a.pass,a.warn,a.fail);
    }

    public static File latestReport(Context ctx){
        try{String p=ctx.getSharedPreferences("cortex_e2e_lab",Context.MODE_PRIVATE).getString("latest_report","");if(!p.isEmpty()){File f=new File(p);if(f.isFile())return f;}}catch(Throwable ignored){}
        return null;
    }

    /** Phase 1 for a real APK update-survival test. */
    public static String armUpdateCheckpoint(Context context){
        String token="update-"+System.currentTimeMillis()+"-"+UUID.randomUUID();
        context.getSharedPreferences("cortex_e2e_update",Context.MODE_PRIVATE).edit()
                .putString("token",token).putLong("armed_at",System.currentTimeMillis())
                .putInt("from_version_code",BuildConfig.VERSION_CODE).putString("from_version_name",BuildConfig.VERSION_NAME).apply();
        return token;
    }

    private static void identity(Context ctx,Acc a,JSONObject r){
        try{
            PackageInfo pi=ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0);
            boolean pkg="com.kareem.cortex".equals(ctx.getPackageName());
            if(pkg)a.pass("app_identity","package="+ctx.getPackageName()+" · version="+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")");else a.fail("app_identity","Unexpected package "+ctx.getPackageName());
            JSONObject x=new JSONObject();x.put("package",ctx.getPackageName());x.put("versionName",BuildConfig.VERSION_NAME);x.put("versionCode",BuildConfig.VERSION_CODE);x.put("firstInstallTime",pi.firstInstallTime);x.put("lastUpdateTime",pi.lastUpdateTime);r.put("app",x);
        }catch(Throwable e){a.fail("app_identity",e.toString());}
    }

    private static void storage(Context ctx,File dir,Acc a,JSONObject r){
        try{
            File f=new File(dir,"storage-probe.txt");String token="CORTEX_E2E_STORAGE_"+UUID.randomUUID();write(f,token);String got=read(f);boolean ok=token.equals(got);if(ok)a.pass("storage_round_trip","Private app storage write/read passed");else a.fail("storage_round_trip","Private app storage content mismatch");
            r.put("storageProbeSha256",sha256(f));
        }catch(Throwable e){a.fail("storage_round_trip",e.toString());}
    }

    private static void database(Context ctx,Acc a,JSONObject r){
        VaultDb db=null;Cursor c=null;try{
            db=new VaultDb(ctx);c=db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)",null);String x=c.moveToFirst()?safe(c.getString(0)):"";if("ok".equalsIgnoreCase(x))a.pass("database_quick_check","SQLite quick_check=ok");else a.fail("database_quick_check","SQLite quick_check="+x);r.put("databaseQuickCheck",x);
        }catch(Throwable e){a.fail("database_quick_check",e.toString());}finally{if(c!=null)try{c.close();}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private static void functional(Context ctx,Acc a,JSONObject r){
        try{
            CortexFunctionalSelfTest.Report f=CortexFunctionalSelfTest.run(ctx);
            if(f.fail==0)a.pass("production_functional_self_test",f.pass+" pass · "+f.warn+" warning · 0 failure");else a.fail("production_functional_self_test",f.pass+" pass · "+f.warn+" warning · "+f.fail+" failure");
            JSONObject j=new JSONObject();j.put("pass",f.pass);j.put("warn",f.warn);j.put("fail",f.fail);j.put("metrics",f.metrics);JSONArray lines=new JSONArray();for(String line:f.lines)lines.put(line);j.put("lines",lines);r.put("functionalSelfTest",j);
        }catch(Throwable e){a.fail("production_functional_self_test",e.toString());}
    }

    private static void documentFixtures(Context ctx,File dir,Acc a,JSONObject r){
        JSONArray docs=new JSONArray();
        try{File f=new File(dir,"fixture.xlsx");makeXlsx(f);checkExtract(ctx,f,"fixture.xlsx","CORTEX_XLSX_2026",a,docs,"xlsx_extraction");}catch(Throwable e){a.fail("xlsx_extraction",e.toString());}
        try{File f=new File(dir,"fixture.docx");makeDocx(f);checkExtract(ctx,f,"fixture.docx","CORTEX_DOCX_2026",a,docs,"docx_extraction");}catch(Throwable e){a.fail("docx_extraction",e.toString());}
        try{File f=new File(dir,"fixture.pptx");makePptx(f);checkExtract(ctx,f,"fixture.pptx","CORTEX_PPTX_2026",a,docs,"pptx_extraction");}catch(Throwable e){a.fail("pptx_extraction",e.toString());}
        try{File f=new File(dir,"fixture.pdf");makePdf(ctx,f);checkExtract(ctx,f,"fixture.pdf","CORTEX_PDF_2026",a,docs,"pdf_extraction");}catch(Throwable e){a.fail("pdf_extraction",e.toString());}
        try{r.put("documentFixtures",docs);}catch(Throwable ignored){}
    }

    private static void checkExtract(Context ctx,File f,String name,String token,Acc a,JSONArray docs,String id)throws Exception{
        AnalysisResult x=DocumentExtractorRegistry.extract(ctx,f,name);String text=x==null?"":safe(x.extractedText);boolean ok=text.contains(token);if(ok)a.pass(id,"Real DocumentExtractorRegistry extracted "+name+" via "+safe(x.engine));else a.fail(id,"Expected fixture token was not extracted from "+name);
        JSONObject d=new JSONObject();d.put("name",name);d.put("bytes",f.length());d.put("sha256",sha256(f));d.put("engine",x==null?"":safe(x.engine));d.put("extractedChars",text.length());d.put("tokenFound",ok);docs.put(d);
    }

    private static void components(Context ctx,Acc a,JSONObject r){
        try{
            PackageManager pm=ctx.getPackageManager();String[] names={"NowActivity","ProposalCaptureActivity","WorkVaultActivity","WorkVaultAskActivity","CortexAuditActivity","SettingsActivity"};int ok=0;JSONArray arr=new JSONArray();
            for(String n:names){boolean exists;try{pm.getActivityInfo(new ComponentName(ctx,Class.forName("com.kareem.cortex."+n)),0);exists=true;}catch(Throwable e){exists=false;}if(exists)ok++;arr.put(new JSONObject().put("name",n).put("registered",exists));}
            if(ok==names.length)a.pass("production_components","Critical production activities registered="+ok);else a.fail("production_components","Registered "+ok+"/"+names.length);r.put("components",arr);
        }catch(Throwable e){a.fail("production_components",e.toString());}
    }

    private static void permissions(Context ctx,Acc a,JSONObject r){
        try{
            JSONObject p=new JSONObject();boolean mic=ContextCompat.checkSelfPermission(ctx,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;p.put("recordAudio",mic);
            if(Build.VERSION.SDK_INT>=33)p.put("notifications",ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
            p.put("accessibilityConnected",CortexScreenAccessibilityService.connected());p.put("usageAccess",PhoneUsageAccess.has(ctx));p.put("shizuku",ShizukuContextBridge.granted());r.put("runtimeAccess",p);
            if(mic)a.pass("runtime_permissions","Microphone permission granted; special-access snapshot recorded");else a.warn("runtime_permissions","Microphone permission not granted; hardware voice test requires user permission");
        }catch(Throwable e){a.warn("runtime_permissions",e.toString());}
    }

    private static void updateCheckpoint(Context ctx,Acc a,JSONObject r){
        try{
            android.content.SharedPreferences p=ctx.getSharedPreferences("cortex_e2e_update",Context.MODE_PRIVATE);String token=p.getString("token","");if(token.isEmpty()){a.warn("update_survival","No pre-update checkpoint armed. Use 'Arm update test' before installing the next APK.");return;}
            int from=p.getInt("from_version_code",-1);long armed=p.getLong("armed_at",0);boolean survived=from>0&&BuildConfig.VERSION_CODE>=from;
            JSONObject u=new JSONObject();u.put("token",token);u.put("armedAt",armed);u.put("fromVersionCode",from);u.put("currentVersionCode",BuildConfig.VERSION_CODE);u.put("survived",survived);r.put("updateCheckpoint",u);
            if(survived)a.pass("update_survival","Checkpoint survived install/update · from "+from+" to "+BuildConfig.VERSION_CODE);else a.fail("update_survival","Checkpoint present but version state is inconsistent");
        }catch(Throwable e){a.fail("update_survival",e.toString());}
    }

    private static JSONObject deviceJson(){JSONObject d=new JSONObject();try{d.put("manufacturer",Build.MANUFACTURER);d.put("model",Build.MODEL);d.put("device",Build.DEVICE);d.put("sdk",Build.VERSION.SDK_INT);d.put("release",Build.VERSION.RELEASE);d.put("fingerprint",Build.FINGERPRINT);}catch(Throwable ignored){}return d;}

    private static void makeDocx(File f)throws Exception{
        Map<String,String> m=new LinkedHashMap<>();m.put("[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>");m.put("word/document.xml","<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>CORTEX_DOCX_2026 embedded end to end</w:t></w:r></w:p></w:body></w:document>");zip(f,m);
    }
    private static void makePptx(File f)throws Exception{
        Map<String,String> m=new LinkedHashMap<>();m.put("[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>");m.put("ppt/slides/slide1.xml","<?xml version=\"1.0\"?><p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><p:cSld><a:t>CORTEX_PPTX_2026 embedded end to end</a:t></p:cSld></p:sld>");zip(f,m);
    }
    private static void makeXlsx(File f)throws Exception{
        Map<String,String> m=new LinkedHashMap<>();m.put("[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>");m.put("xl/workbook.xml","<?xml version=\"1.0\"?><workbook><sheets><sheet name=\"E2E\" sheetId=\"1\"/></sheets></workbook>");m.put("xl/sharedStrings.xml","<?xml version=\"1.0\"?><sst><si><t>CORTEX_XLSX_2026</t></si><si><t>embedded end to end</t></si></sst>");m.put("xl/worksheets/sheet1.xml","<?xml version=\"1.0\"?><worksheet><sheetData><row r=\"1\"><c t=\"s\"><v>0</v></c><c t=\"s\"><v>1</v></c></row></sheetData></worksheet>");zip(f,m);
    }
    private static void makePdf(Context ctx,File f)throws Exception{
        PDFBoxResourceLoader.init(ctx);try(PDDocument doc=new PDDocument()){PDPage page=new PDPage();doc.addPage(page);try(PDPageContentStream s=new PDPageContentStream(doc,page)){s.beginText();s.setFont(PDType1Font.HELVETICA,12);s.newLineAtOffset(72,720);s.showText("CORTEX_PDF_2026 embedded end to end");s.endText();}doc.save(f);}
    }
    private static void zip(File f,Map<String,String> entries)throws Exception{try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){for(Map.Entry<String,String> e:entries.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();}}}
    private static String markdown(JSONObject r,Acc a){return "# Cortex Embedded End-to-End Report\n\nRun: `"+r.optString("runId")+"`\n\nResult: **"+(a.fail==0?"PASS":"FAIL")+"**\n\n- Pass: "+a.pass+"\n- Warning: "+a.warn+"\n- Fail: "+a.fail+"\n\nAttach `report.json` to ChatGPT for structured diagnosis.\n";}
    private static void write(File f,String s)throws Exception{File p=f.getParentFile();if(p!=null)p.mkdirs();try(FileOutputStream o=new FileOutputStream(f)){o.write((s==null?"":s).getBytes(StandardCharsets.UTF_8));}}
    private static String read(File f)throws Exception{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toString("UTF-8");}}
    private static String sha256(File f)throws Exception{MessageDigest m=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)m.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:m.digest())s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();}
    private static String safe(String s){return s==null?"":s;}
}