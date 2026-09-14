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

    private void reject(File file,String ext)throws Exception{
        byte[] before=file.exists()?Files.readAllBytes(file.toPath()):null;
        try {WorkDocumentParser.parse(context,Uri.fromFile(file),ext);fail("Unexpected parse success: "+ext);}
        catch(IOException | org.xmlpull.v1.XmlPullParserException expected) { }
        if(before!=null)assertArrayEquals(before,Files.readAllBytes(file.toPath()));
    }

    private File packageFile(String entry,String xml)throws Exception{
        File f=File.createTempFile("package-",".zip",context.getCacheDir());
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
            z.putNextEntry(new ZipEntry(entry));z.write(xml.getBytes(StandardCharsets.UTF_8));z.closeEntry();
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
