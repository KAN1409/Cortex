package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class WorkOoxmlIntegrityTest {
    private final Context context=ApplicationProvider.getApplicationContext();

    @Test public void corruptAndMismatchedPackagesNeverReturnSuccessfulEmptyParse() throws Exception {
        for(String ext:new String[]{"docx","xlsx","pptx"}){
            File corrupt=File.createTempFile("corrupt-","."+ext,context.getCacheDir());
            Files.write(corrupt.toPath(),"not a package".getBytes(StandardCharsets.UTF_8));
            reject(corrupt,ext);
            File wrong=packageFile("unrelated.xml","<root/>");
            reject(wrong,ext);
        }
    }

    @Test public void realPackagesPreserveOriginalBytesAcrossRepeatedReads() throws Exception {
        String[] extensions={"docx","xlsx","pptx"};
        String[] entries={"word/document.xml","xl/workbook.xml","ppt/presentation.xml"};
        String[] contents={"<document><body><p><r><t>Approved quote</t></r></p></body></document>",
                "<workbook><sheets/></workbook>","<presentation/>"};
        for(int i=0;i<extensions.length;i++){
            File file=packageFile(entries[i],contents[i]);
            byte[] before=Files.readAllBytes(file.toPath());
            for(int repeat=0;repeat<20;repeat++){
                WorkParsedDocument result=WorkDocumentParser.parse(context,Uri.fromFile(file),extensions[i]);
                assertEquals(WorkOoxmlParser.VERSION,result.parserVersion);
                if(i==0)assertFalse(result.blocks.isEmpty());
            }
            assertArrayEquals(before,Files.readAllBytes(file.toPath()));
            reject(file,extensions[(i+1)%extensions.length]);
        }
    }

    @Test public void missingFilesWrongRootsAndMalformedXmlFailClosed() throws Exception {
        reject(new File(context.getCacheDir(),"absent-document.docx"),"docx");
        String[] extensions={"docx","xlsx","pptx"};
        String[] entries={"word/document.xml","xl/workbook.xml","ppt/presentation.xml"};
        String[] malformed={"<document><body>","<workbook><sheets>","<presentation><sldIdLst>"};
        for(int i=0;i<extensions.length;i++){
            reject(packageFile(entries[i],malformed[i]),extensions[i]);
            reject(packageFile(entries[i],"<wrongRoot/>"),extensions[i]);
        }
    }

    @Test public void decompressionBombAndEntryFloodFailClosedWithoutMutatingOriginal() throws Exception {
        File expansion=packageWithInflatedFiller(WorkOoxmlParser.MAX_ENTRY_UNCOMPRESSED_BYTES+8192L);
        reject(expansion,"docx");
        File entryFlood=packageWithEmptyEntries(WorkOoxmlParser.MAX_ZIP_ENTRIES+1);
        reject(entryFlood,"docx");
    }

    @Test public void namespacedWordPreservesParagraphsAndTableCells() throws Exception {
        File file=packageFile("word/document.xml",
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                +"<w:p><w:r><w:t>Approved Negma quote</w:t></w:r></w:p>"
                +"<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Galala</w:t></w:r></w:p></w:tc>"
                +"<w:tc><w:p><w:r><w:t>1250</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                +"</w:body></w:document>");
        byte[] before=Files.readAllBytes(file.toPath());
        WorkParsedDocument result=WorkDocumentParser.parse(context,Uri.fromFile(file),"docx");
        assertEquals(2,result.blocks.size());
        assertEquals("Approved Negma quote",result.blocks.get(0).text);
        assertEquals("Galala",result.blocks.get(1).cells.get("C1"));
        assertEquals("1250",result.blocks.get(1).cells.get("C2"));
        assertArrayEquals(before,Files.readAllBytes(file.toPath()));
    }

    @Test public void namespacedPowerPointPreservesSlideTextAndLocation() throws Exception {
        File file=packageFile("ppt/presentation.xml",
                "<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"/>",
                "ppt/slides/slide7.xml",
                "<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" "
                +"xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                +"<p:cSld><p:spTree><p:sp><p:txBody><a:bodyPr/><a:lstStyle/>"
                +"<a:p><a:r><a:t>Ceiling option approved</a:t></a:r></a:p>"
                +"</p:txBody></p:sp></p:spTree></p:cSld></p:sld>");
        byte[] before=Files.readAllBytes(file.toPath());
        WorkParsedDocument result=WorkDocumentParser.parse(context,Uri.fromFile(file),"pptx");
        assertEquals(1,result.blocks.size());
        assertEquals("Ceiling option approved",result.blocks.get(0).text);
        assertEquals(7,result.blocks.get(0).slideNumber);
        assertArrayEquals(before,Files.readAllBytes(file.toPath()));
    }

    @Test public void namespacedExcelPreservesRelationshipSheetNamesCellsAndFormulas() throws Exception {
        String ns="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        File file=packageFile("xl/workbook.xml",
                "<s:workbook xmlns:s=\""+ns+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                +"<s:sheets><s:sheet name=\"Negma Prices\" sheetId=\"4\" r:id=\"rId9\"/></s:sheets></s:workbook>",
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                +"<Relationship Id=\"rId9\" Target=\"worksheets/sheet2.xml\" "
                +"Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"/></Relationships>",
                "xl/sharedStrings.xml","<s:sst xmlns:s=\""+ns+"\"><s:si><s:t>Galala</s:t></s:si></s:sst>",
                "xl/worksheets/sheet2.xml","<s:worksheet xmlns:s=\""+ns+"\"><s:sheetData><s:row r=\"3\">"
                +"<s:c r=\"A3\" t=\"s\"><s:v>0</s:v></s:c><s:c r=\"B3\"><s:f>625*2</s:f><s:v>1250</s:v></s:c>"
                +"</s:row></s:sheetData></s:worksheet>");
        byte[] before=Files.readAllBytes(file.toPath());
        WorkParsedDocument result=WorkDocumentParser.parse(context,Uri.fromFile(file),"xlsx");
        assertEquals(1,result.blocks.size());
        WorkParsedDocument.Block row=result.blocks.get(0);
        assertEquals("Negma Prices",row.sheetName);
        assertEquals(3,row.rowNumber);
        assertEquals("Galala",row.cells.get("A"));
        assertEquals("1250",row.cells.get("B"));
        assertEquals("625*2",row.formulas.get("B"));
        assertArrayEquals(before,Files.readAllBytes(file.toPath()));
    }

    @Test public void undeclaredNamespacePrefixesFailClosed() throws Exception {
        reject(packageFile("word/document.xml","<w:document><w:body/></w:document>"),"docx");
        reject(packageFile("xl/workbook.xml","<s:workbook><s:sheets/></s:workbook>"),"xlsx");
        reject(packageFile("ppt/presentation.xml","<p:presentation/>"),"pptx");
    }

    private void reject(File file,String ext)throws Exception{
        byte[] before=file.exists()?Files.readAllBytes(file.toPath()):null;
        try {WorkDocumentParser.parse(context,Uri.fromFile(file),ext);fail("Unexpected parse success: "+ext);}
        catch(IOException | org.xmlpull.v1.XmlPullParserException expected) { }
        if(before!=null)assertArrayEquals(before,Files.readAllBytes(file.toPath()));
    }

    private File packageFile(String... parts)throws Exception{
        File f=File.createTempFile("package-",".zip",context.getCacheDir());
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
            for(int i=0;i<parts.length;i+=2){
                z.putNextEntry(new ZipEntry(parts[i]));z.write(parts[i+1].getBytes(StandardCharsets.UTF_8));z.closeEntry();
            }
        }
        return f;
    }

    private File packageWithInflatedFiller(long uncompressedBytes)throws Exception{
        File f=File.createTempFile("expansion-",".zip",context.getCacheDir());
        byte[] chunk=new byte[64*1024];Arrays.fill(chunk,(byte)'A');
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
            z.putNextEntry(new ZipEntry("custom/expansion.bin"));
            long remaining=uncompressedBytes;
            while(remaining>0){int n=(int)Math.min((long)chunk.length,remaining);z.write(chunk,0,n);remaining-=n;}
            z.closeEntry();
            z.putNextEntry(new ZipEntry("word/document.xml"));
            z.write("<document><body><p><r><t>safe</t></r></p></body></document>".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return f;
    }

    private File packageWithEmptyEntries(int count)throws Exception{
        File f=File.createTempFile("entry-flood-",".zip",context.getCacheDir());
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
            for(int i=0;i<count;i++){
                z.putNextEntry(new ZipEntry("custom/entry-"+i+".xml"));z.closeEntry();
            }
            z.putNextEntry(new ZipEntry("word/document.xml"));
            z.write("<document><body><p><r><t>safe</t></r></p></body></document>".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return f;
    }
}
