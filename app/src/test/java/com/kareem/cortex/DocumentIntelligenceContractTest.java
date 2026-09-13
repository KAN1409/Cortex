package com.kareem.cortex;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class DocumentIntelligenceContractTest {
    private final Context ctx=ApplicationProvider.getApplicationContext();

    @Test public void xlsxIsActuallyExtracted() throws Exception {
        File f=new File(ctx.getCacheDir(),"orders_test.xlsx");
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
            put(z,"xl/workbook.xml","<workbook><sheets><sheet name=\"Orders\" sheetId=\"1\"/></sheets></workbook>");
            put(z,"xl/sharedStrings.xml","<sst><si><t>PO Number</t></si><si><t>Vendor</t></si><si><t>PO-0262</t></si><si><t>Life Style</t></si></sst>");
            put(z,"xl/worksheets/sheet1.xml","<worksheet><sheetData><row><c t=\"s\"><v>0</v></c><c t=\"s\"><v>1</v></c></row><row><c t=\"s\"><v>2</v></c><c t=\"s\"><v>3</v></c></row></sheetData></worksheet>");
        }
        AnalysisResult r=DocumentExtractorRegistry.extract(ctx,f,f.getName());
        assertTrue(r.extractedText.contains("PO Number"));assertTrue(r.extractedText.contains("PO-0262"));assertTrue(r.extractedText.contains("Life Style"));assertEquals("document_ooxml_xlsx",r.engine);
    }

    @Test public void docxIsActuallyExtracted() throws Exception {
        File f=new File(ctx.getCacheDir(),"note_test.docx");
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){put(z,"word/document.xml","<w:document xmlns:w=\"x\"><w:body><w:p><w:r><w:t>Negma follow up order review</w:t></w:r></w:p></w:body></w:document>");}
        AnalysisResult r=DocumentExtractorRegistry.extract(ctx,f,f.getName());assertTrue(r.extractedText.contains("Negma follow up order review"));assertEquals("document_ooxml_docx",r.engine);
    }

    @Test public void pptxIsActuallyExtracted() throws Exception {
        File f=new File(ctx.getCacheDir(),"offer_test.pptx");
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){put(z,"ppt/slides/slide1.xml","<p:sld xmlns:p=\"p\" xmlns:a=\"a\"><p:cSld><a:t>Installation Offer 24-5</a:t><a:t>Total 125000</a:t></p:cSld></p:sld>");}
        AnalysisResult r=DocumentExtractorRegistry.extract(ctx,f,f.getName());assertTrue(r.extractedText.contains("Installation Offer 24-5"));assertTrue(r.extractedText.contains("125000"));assertEquals("document_ooxml_pptx",r.engine);
    }

    @Test public void textPdfIsActuallyExtracted() throws Exception {
        PDFBoxResourceLoader.init(ctx);File f=new File(ctx.getCacheDir(),"quotation_test.pdf");
        try(PDDocument d=new PDDocument()){PDPage p=new PDPage();d.addPage(p);try(PDPageContentStream cs=new PDPageContentStream(d,p)){cs.beginText();cs.setFont(PDType1Font.HELVETICA,12);cs.newLineAtOffset(72,700);cs.showText("Jaz Elite Negma Installation Offer total 125000 EGP");cs.endText();}d.save(f);}
        AnalysisResult r=DocumentExtractorRegistry.extract(ctx,f,f.getName());assertTrue(r.extractedText.contains("Jaz Elite Negma Installation Offer"));assertEquals("document_pdfbox_ocr",r.engine);
    }

    @Test public void supportedProfessionalTypesAreExplicit(){assertTrue(DocumentExtractorRegistry.supports("x.xlsx"));assertTrue(DocumentExtractorRegistry.supports("x.pdf"));assertTrue(DocumentExtractorRegistry.supports("x.docx"));assertTrue(DocumentExtractorRegistry.supports("x.pptx"));assertFalse(DocumentExtractorRegistry.supports("x.exe"));}

    private static void put(ZipOutputStream z,String name,String text)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}
