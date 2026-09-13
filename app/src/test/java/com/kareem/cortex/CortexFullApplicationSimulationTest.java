package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * High-level simulation gate for Cortex.
 *
 * This is intentionally broader than a normal unit test: it exercises the real
 * launcher contract, database, production self-test, capability registry,
 * capture surfaces, voice recovery, document extraction and key intelligence
 * boundaries in one JVM simulation before an APK is considered for delivery.
 */
@RunWith(RobolectricTestRunner.class)
public class CortexFullApplicationSimulationTest {
    private int pass=0;
    private final Context context=ApplicationProvider.getApplicationContext();

    @After public void cleanup(){
        SafeCoreRuntime.resetForTests();
    }

    @Test public void fullApplicationSimulation() throws Exception {
        stage("APP_IDENTITY", () -> {
            assertEquals("com.kareem.cortex", context.getPackageName());
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            assertNotNull(launch);
            ComponentName component=launch.getComponent();
            assertNotNull(component);
            assertEquals(NowActivity.class.getName(), component.getClassName());
        });

        stage("LAUNCHER_SAFE_CORE", () -> {
            SafeCoreRuntime.resetForTests();
            NowActivity activity=Robolectric.buildActivity(NowActivity.class).get();
            new SafeCoreLifecycle().onActivityResumed(activity);
            assertTrue(SafeCoreRuntime.armedForTests());
            assertEquals("v102_reindexed_after_entry_surface_fix", DocumentIntelligenceMigration.preferenceKeyForTests());
        });

        stage("DATABASE_HEALTH", () -> {
            VaultDb db=new VaultDb(context);
            try {
                Cursor c=db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)", null);
                assertTrue(c.moveToFirst());
                assertEquals("ok", c.getString(0).toLowerCase());
                c.close();
            } finally { db.close(); }
        });

        stage("PRODUCTION_SELF_TEST", () -> {
            CortexFunctionalSelfTest.Report report=CortexFunctionalSelfTest.run(context);
            assertEquals("Self-test failures: "+report.text(), 0, report.fail);
            assertTrue("Self-test did not exercise enough paths: "+report.text(), report.pass>=12);
        });

        stage("CAPABILITY_REGISTRY", () -> {
            VaultDb db=new VaultDb(context);
            try {
                assertEquals(43, CortexCapabilityRegistry.all().size());
                Set<String> keys=new HashSet<>();
                for(CortexCapabilityRegistry.Capability capability:CortexCapabilityRegistry.all()){
                    assertTrue("duplicate capability: "+capability.key, keys.add(capability.key));
                    CortexCapabilityRegistry.State state=CortexCapabilityRegistry.evaluate(context,db,capability);
                    assertNotNull(state);
                    assertNotNull(state.status);
                    assertFalse("blank status: "+capability.key,state.status.trim().isEmpty());
                    assertNotEquals("not verified: "+capability.key,CortexCapabilityRegistry.NOT_VERIFIED,state.status);
                }
            } finally { db.close(); }
        });

        stage("PHOTO_CAPTURE_CHOOSER", () -> {
            ProposalCaptureActivity a=Robolectric.buildActivity(ProposalCaptureActivity.class).setup().get();
            a.pickPhoto();
            assertTrue("camera choice missing", hasText(a.getWindow().getDecorView(),"Open camera"));
            assertTrue("gallery choice missing", hasText(a.getWindow().getDecorView(),"Choose from gallery"));
            a.finish();
        });

        stage("VOICE_RECOVERY", () -> {
            VaultDb db=new VaultDb(context); long id=0;
            try {
                String token="simulation-voice-"+System.nanoTime();
                id=db.insert("AUDIO","manual_recording","Voice recording","","Voice & Audio","voice,audio","/tmp/simulation.wav",token,"{}");
                assertTrue(id>0);
                db.getWritableDatabase().execSQL("UPDATE knowledge_items SET status='analyzing', updated_at=? WHERE id=?",new Object[]{System.currentTimeMillis()-180000L,id});
                int recovered=VoiceCapturePipeline.recoverStale(db);
                KnowledgeItem item=db.getById(id);
                assertTrue(recovered>=1);
                assertNotNull(item);
                assertEquals("queued",item.status);
            } finally {
                if(id>0) db.getWritableDatabase().delete("knowledge_items","id=?",new String[]{String.valueOf(id)});
                db.close();
            }
        });

        stage("CAPTURE_HYGIENE", () -> {
            KnowledgeItem manual=new KnowledgeItem(-1,"TEXT","manual","x","x","","","","","","analyzed","","","{}",0,0);
            KnowledgeItem passive=new KnowledgeItem(-2,"NOTIFICATION","notification_listener","weather","weather","","","","","","analyzed","","","{}",0,0);
            assertTrue(IntentionalCapturePolicy.visibleInCapturedLibrary(manual));
            assertFalse(IntentionalCapturePolicy.visibleInCapturedLibrary(passive));
        });

        stage("XLSX_EXTRACTION", () -> {
            File f=new File(context.getCacheDir(),"simulation_orders.xlsx");
            try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
                put(z,"xl/workbook.xml","<workbook><sheets><sheet name=\"Orders\" sheetId=\"1\"/></sheets></workbook>");
                put(z,"xl/sharedStrings.xml","<sst><si><t>PO Number</t></si><si><t>Vendor</t></si><si><t>PO-0262</t></si><si><t>Life Style</t></si></sst>");
                put(z,"xl/worksheets/sheet1.xml","<worksheet><sheetData><row><c t=\"s\"><v>0</v></c><c t=\"s\"><v>1</v></c></row><row><c t=\"s\"><v>2</v></c><c t=\"s\"><v>3</v></c></row></sheetData></worksheet>");
            }
            AnalysisResult r=DocumentExtractorRegistry.extract(context,f,f.getName());
            assertTrue(r.extractedText.contains("PO Number"));
            assertTrue(r.extractedText.contains("PO-0262"));
            assertTrue(r.extractedText.contains("Life Style"));
            assertEquals("document_ooxml_xlsx",r.engine);
        });

        stage("PDF_EXTRACTION", () -> {
            PDFBoxResourceLoader.init(context);
            File f=new File(context.getCacheDir(),"simulation_quote.pdf");
            try(PDDocument d=new PDDocument()){
                PDPage p=new PDPage(); d.addPage(p);
                try(PDPageContentStream cs=new PDPageContentStream(d,p)){
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA,12); cs.newLineAtOffset(72,700);
                    cs.showText("Jaz Elite Negma Installation Offer total 125000 EGP"); cs.endText();
                }
                d.save(f);
            }
            AnalysisResult r=DocumentExtractorRegistry.extract(context,f,f.getName());
            assertTrue(r.extractedText.contains("Jaz Elite Negma Installation Offer"));
            assertEquals("document_pdfbox_ocr",r.engine);
        });

        stage("DOCX_EXTRACTION", () -> {
            File f=new File(context.getCacheDir(),"simulation_note.docx");
            try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
                put(z,"word/document.xml","<w:document xmlns:w=\"x\"><w:body><w:p><w:r><w:t>Negma follow up order review</w:t></w:r></w:p></w:body></w:document>");
            }
            AnalysisResult r=DocumentExtractorRegistry.extract(context,f,f.getName());
            assertTrue(r.extractedText.contains("Negma follow up order review"));
            assertEquals("document_ooxml_docx",r.engine);
        });

        stage("PPTX_EXTRACTION", () -> {
            File f=new File(context.getCacheDir(),"simulation_offer.pptx");
            try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
                put(z,"ppt/slides/slide1.xml","<p:sld xmlns:p=\"p\" xmlns:a=\"a\"><p:cSld><a:t>Installation Offer 24-5</a:t><a:t>Total 125000</a:t></p:cSld></p:sld>");
            }
            AnalysisResult r=DocumentExtractorRegistry.extract(context,f,f.getName());
            assertTrue(r.extractedText.contains("Installation Offer 24-5"));
            assertTrue(r.extractedText.contains("125000"));
            assertEquals("document_ooxml_pptx",r.engine);
        });

        stage("ATTENTION_BOUNDARY", () -> {
            DeterministicSemanticRecovery.Classification request=DeterministicSemanticRecovery.classify("conversation_notification","Ahmed","Please send the quotation today");
            assertEquals("ACTION",request.attentionKind);
            DeterministicSemanticRecovery.Classification ordinary=DeterministicSemanticRecovery.classify("conversation_notification","Nasser","وصلت البيت");
            assertNull(ordinary.attentionKind);
            DeterministicSemanticRecovery.Classification announcement=DeterministicSemanticRecovery.classify("notification","CIB","يرجى العلم أنه سوف يتم تحديث أنظمة CIB يوم الجمعة");
            assertNull(announcement.attentionKind);
        });

        stage("DIAGNOSTICS", () -> {
            VaultDb db=new VaultDb(context);
            try {
                String trace=AttentionTraceExporter.export(db.getReadableDatabase());
                assertTrue(trace.contains("CORTEX_ATTENTION_TRACE_V4"));
                assertEquals("application/json",DiagnosticFileShare.mimeType("cortex-attention-trace.json"));
            } finally { db.close(); }
        });

        System.out.println("CORTEX_SIMULATION_RESULT|PASS|stages="+pass);
        assertTrue("simulation must exercise at least 13 stages",pass>=13);
    }

    private void stage(String name, ThrowingRunnable body) throws Exception {
        System.out.println("CORTEX_SIMULATION|START|"+name);
        try {
            body.run();
            pass++;
            System.out.println("CORTEX_SIMULATION|PASS|"+name);
        } catch(Throwable t){
            System.out.println("CORTEX_SIMULATION|FAIL|"+name+"|"+t.getClass().getSimpleName()+"|"+String.valueOf(t.getMessage()));
            if(t instanceof Exception) throw (Exception)t;
            if(t instanceof Error) throw (Error)t;
            throw new RuntimeException(t);
        }
    }

    private static boolean hasText(View v,String exact){
        if(v instanceof TextView && exact.contentEquals(((TextView)v).getText())) return true;
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) if(hasText(g.getChildAt(i),exact)) return true;
        }
        return false;
    }

    private static void put(ZipOutputStream z,String name,String text)throws Exception{
        z.putNextEntry(new ZipEntry(name));
        z.write(text.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
