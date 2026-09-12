package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;

/** Digital-PDF text extraction with exact page provenance. Empty pages are marked for OCR fallback. */
public final class WorkPdfParser {
    public static final String VERSION="work_pdf_parser_001";
    private static volatile boolean initialized=false;
    private WorkPdfParser(){}

    public static WorkParsedDocument parse(Context context,Uri uri)throws Exception{
        init(context);
        WorkParsedDocument out=new WorkParsedDocument();out.parserVersion=VERSION;
        try(InputStream in=context.getContentResolver().openInputStream(uri);PDDocument doc=PDDocument.load(in)){
            PDFTextStripper stripper=new PDFTextStripper();
            int empty=0;
            for(int page=1;page<=doc.getNumberOfPages();page++){
                stripper.setStartPage(page);stripper.setEndPage(page);
                String text=clean(stripper.getText(doc));
                if(text.isEmpty()){empty++;continue;}
                WorkParsedDocument.Block b=new WorkParsedDocument.Block("PAGE",text);b.pageNumber=page;out.blocks.add(b);
            }
            out.needsOcr=doc.getNumberOfPages()>0&&empty>0;
        }
        return out;
    }

    private static void init(Context context){
        if(initialized)return;
        synchronized(WorkPdfParser.class){if(!initialized){PDFBoxResourceLoader.init(context.getApplicationContext());initialized=true;}}
    }
    private static String clean(String s){return s==null?"":s.replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\\n\\n").trim();}
}
