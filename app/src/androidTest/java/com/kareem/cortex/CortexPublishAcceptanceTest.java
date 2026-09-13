package com.kareem.cortex;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Final publish acceptance suite. This runs on a real Android runtime/emulator,
 * not Robolectric. It deliberately exercises installed UI surfaces, production
 * document extractors, local storage, synthetic media, OCR, and persistence markers.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class CortexPublishAcceptanceTest {
    private static final String PKG = "com.kareem.cortex";
    private Instrumentation inst;
    private Context app;
    private UiDevice device;
    private File reportDir;
    private File fixturesDir;

    @Before public void setup() throws Exception {
        inst = InstrumentationRegistry.getInstrumentation();
        app = inst.getTargetContext();
        device = UiDevice.getInstance(inst);
        File external = app.getExternalFilesDir("publish-simulation");
        assertNotNull("External files directory unavailable", external);
        reportDir = new File(external, "screens");
        fixturesDir = new File(external, "fixtures");
        assertTrue(reportDir.exists() || reportDir.mkdirs());
        assertTrue(fixturesDir.exists() || fixturesDir.mkdirs());
        PDFBoxResourceLoader.init(app);
    }

    @Test public void test01_freshRuntimeIdentityAndLauncher() throws Exception {
        assertEquals(PKG, app.getPackageName());
        Intent launcher = app.getPackageManager().getLaunchIntentForPackage(PKG);
        assertNotNull("No launcher intent", launcher);
        app.startActivity(launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue("Launcher never became visible", device.wait(Until.hasObject(By.pkg(PKG)), 10000));
        device.waitForIdle();
        assertEquals(PKG, device.getCurrentPackageName());
        assertTrue("Launcher screenshot failed", shot("00_launcher_now"));
        assertFalse("Crash dialog detected", crashDialog());
        app.getSharedPreferences("cortex_publish_acceptance", Context.MODE_PRIVATE).edit()
                .putString("marker", "PERSIST_ME_ACROSS_REINSTALL")
                .putLong("created_at", System.currentTimeMillis()).commit();
        System.out.println("PUBLISH_SIM|PASS|fresh_runtime_launcher");
    }

    @Test public void test02_everyDeclaredUserSurfaceRenders() throws Exception {
        // Every activity/alias declared by the current production manifest. Detail
        // screens are intentionally included: they must degrade gracefully when opened
        // without a selected record rather than crashing the app process.
        String[] surfaces = {
                "NowActivity","NexusActivity","CaptureHubActivity","VoiceLibraryActivity","VoiceDetailActivity",
                "CaptureOverviewActivity","WorkWorkspaceActivity","WorkVaultActivity","WorkVaultBrowseActivity",
                "WorkVaultAskActivity","WorkFollowUpActivity","WorkChatGptBuildActivity","CognitiveShadowActivity",
                "CrashReportActivity","SatinBriefActivity","CortexOrbBriefActivity","ProposalBriefActivity",
                "PremiumHomeActivity","SatinCaptureActivity","CaptureActivity","ProposalCaptureActivity",
                "CaptureResultActivity","ProposalCaptureResultActivity","PinRecordWidgetActivity","PeopleProjectsActivity",
                "ProposalPeopleProjectsActivity","SmartInboxActivity","VaultActivity","PromptLibraryActivity",
                "AskCortexActivity","ProposalAskCortexActivity","SettingsActivity","ReviewQueueActivity",
                "RelevanceEvaluationActivity","CorrectionLearningActivity","FeatureHubActivity","PhoneContextAccessActivity",
                "CapabilityMatrixActivity","EnvironmentActivity","CortexStatusActivity","CortexAuditActivity",
                "ExternalModelCheckActivity","VisualIntelligenceActivity","VisualMemoryActivity","VisualMemoryDetailActivity",
                "KnowledgeExplorerActivity","OcrTestActivity","AsrSettingsActivity","CortexAsrLabActivity","BrainActivity",
                "OpenRouterSettingsActivity","GeminiSettingsActivity","ChatGptTeacherActivity","CortexTeacherImportActivity",
                "MainActivity","InputActivity"
        };
        int passed = 0;
        for (String simple : surfaces) {
            launchComponent(simple);
            if (!device.wait(Until.hasObject(By.pkg(PKG)), 8000)) {
                fail("Surface did not render in Cortex package: " + simple + " current=" + device.getCurrentPackageName());
            }
            device.waitForIdle();
            if (crashDialog()) fail("Crash dialog while rendering " + simple);
            assertTrue("Screenshot failed for " + simple, shot(String.format(Locale.US,"surface_%02d_%s",passed,simple)));
            passed++;
            device.pressBack();
            SystemClock.sleep(120);
        }
        assertEquals("Not all manifest UI surfaces were exercised", surfaces.length, passed);
        System.out.println("PUBLISH_SIM|PASS|ui_surface_matrix|count=" + passed);
    }

    @Test public void test03_syntheticOfficeFilesUseProductionExtractors() throws Exception {
        File pdf = makePdf();
        File xlsx = makeXlsx();
        File docx = makeDocx();
        File pptx = makePptx();

        AnalysisResult pr = DocumentExtractorRegistry.extract(app, pdf, pdf.getName());
        assertContains(pr.extractedText, "CORTEX FINAL PUBLISH PDF");
        assertContains(pr.extractedText, "PO-0262");
        assertEquals("document_pdfbox_ocr", pr.engine);

        AnalysisResult xr = DocumentExtractorRegistry.extract(app, xlsx, xlsx.getName());
        assertContains(xr.extractedText, "PO-0262");
        assertContains(xr.extractedText, "Life Style");
        assertContains(xr.extractedText, "Galala Marble");
        assertEquals("document_ooxml_xlsx", xr.engine);

        AnalysisResult dr = DocumentExtractorRegistry.extract(app, docx, docx.getName());
        assertContains(dr.extractedText, "Cortex final Word acceptance");
        assertContains(dr.extractedText, "مرحلة التسليم النهائي");
        assertEquals("document_ooxml_docx", dr.engine);

        AnalysisResult sr = DocumentExtractorRegistry.extract(app, pptx, pptx.getName());
        assertContains(sr.extractedText, "Cortex final presentation acceptance");
        assertContains(sr.extractedText, "Final Publish");
        assertEquals("document_ooxml_pptx", sr.engine);

        writeUtf8(new File(fixturesDir,"sample_notes.txt"), "Cortex sample note\nملاحظة عربية للاختبار\nOwner: Karim");
        writeUtf8(new File(fixturesDir,"sample_prices.csv"), "item,vendor,price\nGalala,Life Style,1250\nPiatra,Vendor B,1675\n");
        System.out.println("PUBLISH_SIM|PASS|office_document_extractors|pdf+xlsx+docx+pptx+txt+csv");
    }

    @Test public void test04_syntheticImageAndOcrRuntime() throws Exception {
        File image = makeTextImage();
        Bitmap decoded = BitmapFactory.decodeFile(image.getAbsolutePath());
        assertNotNull("Generated PNG cannot be decoded", decoded);
        assertTrue(decoded.getWidth() >= 1000 && decoded.getHeight() >= 500);
        decoded.recycle();

        final CountDownLatch latch = new CountDownLatch(1);
        final String[] recognized = {""};
        final boolean[] accepted = {false};
        ArabicOcr.recognizeDetailed(app, image, "CORTEX OCR SAMPLE 2026", r -> {
            if (r != null) {
                recognized[0] = r.text == null ? "" : r.text;
                accepted[0] = r.accepted;
            }
            latch.countDown();
        });
        assertTrue("OCR callback timed out", latch.await(45, TimeUnit.SECONDS));
        assertTrue("OCR returned no usable text: " + recognized[0], accepted[0] || recognized[0].trim().length() >= 5);
        System.out.println("PUBLISH_SIM|PASS|image_decode_and_ocr|chars=" + recognized[0].length());
    }

    @Test public void test05_syntheticWavIsValidAndVoiceUiSurvives() throws Exception {
        File wav = makeWav();
        assertTrue(wav.isFile());
        assertTrue(wav.length() > 32000);
        byte[] h = new byte[12];
        try (java.io.FileInputStream in = new java.io.FileInputStream(wav)) { assertEquals(12,in.read(h)); }
        assertEquals("RIFF", new String(Arrays.copyOfRange(h,0,4), StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(Arrays.copyOfRange(h,8,12), StandardCharsets.US_ASCII));
        launchComponent("VoiceLibraryActivity");
        assertTrue(device.wait(Until.hasObject(By.pkg(PKG)), 8000));
        assertFalse(crashDialog());
        assertTrue(shot("voice_library_runtime"));
        device.pressBack();
        System.out.println("PUBLISH_SIM|PASS|synthetic_audio_and_voice_surface");
    }

    @Test public void test06_databaseAndCoreSelfTestOnDevice() throws Exception {
        VaultDb db = new VaultDb(app);
        android.database.Cursor c = db.getReadableDatabase().rawQuery("PRAGMA quick_check", null);
        assertTrue(c.moveToFirst());
        assertEquals("ok", c.getString(0));
        c.close();
        db.close();

        CortexFunctionalSelfTest.Report r = CortexFunctionalSelfTest.run(app);
        assertNotNull(r);
        assertTrue("Production self-test failed: " + r.toString(), r.ok());
        writeUtf8(new File(reportDir.getParentFile(),"self_test.txt"), r.toString());
        System.out.println("PUBLISH_SIM|PASS|database_quick_check_and_production_self_test");
    }

    @Test public void test07_shareAndDeepEntryRoutesDoNotCrash() throws Exception {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "CORTEX FINAL PUBLISH SHARE SAMPLE — متابعة أمر الإسناد PO-0262");
        share.setComponent(new ComponentName(PKG, PKG + ".InputActivity"));
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        app.startActivity(share);
        assertTrue("Share entry did not render", device.wait(Until.hasObject(By.pkg(PKG)), 8000));
        assertFalse(crashDialog());
        assertTrue(shot("share_text_entry"));
        device.pressBack();
        System.out.println("PUBLISH_SIM|PASS|share_and_input_entry");
    }

    @Test public void test08_permissionsAndSettingsSurfacesAreSafe() throws Exception {
        for (String simple : new String[]{"SettingsActivity","PhoneContextAccessActivity","AsrSettingsActivity","OpenRouterSettingsActivity","GeminiSettingsActivity"}) {
            launchComponent(simple);
            assertTrue("Settings surface absent: " + simple, device.wait(Until.hasObject(By.pkg(PKG)), 8000));
            assertFalse("Crash on settings surface " + simple, crashDialog());
            assertTrue(shot("settings_" + simple));
            device.pressBack();
        }
        System.out.println("PUBLISH_SIM|PASS|settings_permission_surfaces");
    }

    @Test public void test09_persistenceMarkerReadableBeforeUpgradeProbe() throws Exception {
        String marker = app.getSharedPreferences("cortex_publish_acceptance", Context.MODE_PRIVATE).getString("marker", "");
        assertEquals("PERSIST_ME_ACROSS_REINSTALL", marker);
        writeUtf8(new File(reportDir.getParentFile(),"persistence_marker.txt"), marker);
        System.out.println("PUBLISH_SIM|PASS|persistence_seed_ready_for_adb_reinstall_probe");
    }

    private void launchComponent(String simple) throws Exception {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, PKG + "." + simple));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        app.startActivity(i);
        SystemClock.sleep(350);
    }

    private boolean crashDialog() {
        return device.hasObject(By.textContains("keeps stopping")) ||
                device.hasObject(By.textContains("isn't responding")) ||
                device.hasObject(By.textContains("has stopped"));
    }

    private boolean shot(String name) {
        File f = new File(reportDir, name.replaceAll("[^A-Za-z0-9_.-]","_") + ".png");
        return device.takeScreenshot(f);
    }

    private File makePdf() throws Exception {
        File f = new File(fixturesDir,"sample_final_publish.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(); doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc,page)) {
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD,16); cs.newLineAtOffset(60,720);
                cs.showText("CORTEX FINAL PUBLISH PDF");
                cs.setFont(PDType1Font.HELVETICA,12); cs.newLineAtOffset(0,-30);
                cs.showText("Purchase order PO-0262 - Vendor Life Style - Galala Marble acceptance evidence.");
                cs.newLineAtOffset(0,-22); cs.showText("This page exists only for the automated final publish simulation.");
                cs.endText();
            }
            doc.save(f);
        }
        return f;
    }

    private File makeXlsx() throws Exception {
        File f = new File(fixturesDir,"sample_procurement.xlsx");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            zip(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            zip(z,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheets><sheet name=\"Procurement\" sheetId=\"1\"/></sheets></workbook>");
            zip(z,"xl/worksheets/sheet1.xml","<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"+
                    row("PO Number","Vendor","Item","Price")+row("PO-0262","Life Style","Galala Marble","1250")+row("PO-0310","Vendor B","Piatra","1675")+
                    "</sheetData></worksheet>");
        }
        return f;
    }

    private String row(String... values) {
        StringBuilder b=new StringBuilder("<row>");
        for(String v:values)b.append("<c t=\"inlineStr\"><is><t>").append(xml(v)).append("</t></is></c>");
        return b.append("</row>").toString();
    }

    private File makeDocx() throws Exception {
        File f = new File(fixturesDir,"sample_final_brief.docx");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            zip(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            zip(z,"word/document.xml","<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Cortex final Word acceptance</w:t></w:r></w:p><w:p><w:r><w:t>مرحلة التسليم النهائي</w:t></w:r></w:p><w:p><w:r><w:t>Project Negma PO-0262 evidence sample</w:t></w:r></w:p></w:body></w:document>");
        }
        return f;
    }

    private File makePptx() throws Exception {
        File f = new File(fixturesDir,"sample_final_deck.pptx");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            zip(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/ppt/slides/slide1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/></Types>");
            zip(z,"ppt/slides/slide1.xml","<?xml version=\"1.0\"?><p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>Cortex final presentation acceptance</a:t></a:r></a:p><a:p><a:r><a:t>Final Publish - Work Vault sample</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>");
        }
        return f;
    }

    private File makeTextImage() throws Exception {
        File f = new File(fixturesDir,"sample_ocr.png");
        Bitmap b=Bitmap.createBitmap(1200,650,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(64);
        c.drawText("CORTEX OCR SAMPLE 2026",70,170,p);p.setTextSize(54);c.drawText("PO-0262  LIFE STYLE  1250",70,290,p);
        p.setTextSize(58);p.setTypeface(Typeface.DEFAULT);c.drawText("اختبار نهائي للقراءة",70,420,p);
        try(FileOutputStream out=new FileOutputStream(f)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
        b.recycle();return f;
    }

    private File makeWav() throws Exception {
        File f=new File(fixturesDir,"sample_voice.wav");int rate=16000,seconds=2,samples=rate*seconds,data=samples*2;
        try(FileOutputStream out=new FileOutputStream(f)){
            ByteArrayOutputStream h=new ByteArrayOutputStream();
            writeAscii(h,"RIFF");writeLe32(h,36+data);writeAscii(h,"WAVEfmt ");writeLe32(h,16);writeLe16(h,1);writeLe16(h,1);writeLe32(h,rate);writeLe32(h,rate*2);writeLe16(h,2);writeLe16(h,16);writeAscii(h,"data");writeLe32(h,data);out.write(h.toByteArray());
            for(int i=0;i<samples;i++){double envelope=Math.sin(Math.PI*i/samples);short s=(short)(Math.sin(2*Math.PI*440*i/rate)*9000*envelope);out.write(s&255);out.write((s>>8)&255);}
        }return f;
    }

    private static void zip(ZipOutputStream z,String name,String text)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static void writeUtf8(File f,String text)throws Exception{try(OutputStreamWriter w=new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8)){w.write(text);}}
    private static void writeAscii(ByteArrayOutputStream o,String s)throws Exception{o.write(s.getBytes(StandardCharsets.US_ASCII));}
    private static void writeLe16(ByteArrayOutputStream o,int v){o.write(v&255);o.write((v>>8)&255);}
    private static void writeLe32(ByteArrayOutputStream o,int v){o.write(v&255);o.write((v>>8)&255);o.write((v>>16)&255);o.write((v>>24)&255);}
    private static void assertContains(String actual,String expected){assertNotNull(actual);assertTrue("Expected ["+expected+"] in ["+actual+"]",actual.contains(expected));}
}
