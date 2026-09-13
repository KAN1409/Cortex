package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    @Test public void missingFileAndMalformedXmlFailClosed() throws Exception {
        reject(new File(context.getCacheDir(),"absent-document.docx"),"docx");
        reject(packageFile("word/document.xml","<document><body>"),"docx");
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
}
