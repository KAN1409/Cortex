package com.kareem.cortex;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class AttachmentAnalyzer {
    private AttachmentAnalyzer(){}
    public static AnalysisResult analyze(KnowledgeItem item) throws Exception {
        File f=new File(item.attachmentPath);if(!f.exists())throw new FileNotFoundException("Attachment missing");String name=item.rawText==null?f.getName():item.rawText;String low=name.toLowerCase();boolean text=low.endsWith(".txt")||low.endsWith(".csv")||low.endsWith(".tsv")||low.endsWith(".json")||low.endsWith(".xml")||low.endsWith(".md")||low.endsWith(".log");
        if(text&&f.length()<=2_000_000){byte[] b=new byte[(int)f.length()];try(FileInputStream in=new FileInputStream(f)){int p=0,n;while(p<b.length&&(n=in.read(b,p,b.length-p))>0)p+=n;}String s=new String(b,StandardCharsets.UTF_8);AnalysisResult r=TabularAnalyzer.looksTabular(s)?TabularAnalyzer.analyze(s):LocalAnalyzer.analyze(s,"text/plain");if(r==null)r=LocalAnalyzer.analyze(s,"text/plain");r.extractedText=s;r.engine=r.engine+"+file";r.version="1";if(r.title==null||r.title.isEmpty())r.title=name;return r;}
        AnalysisResult r=new AnalysisResult();r.title=name;r.summary="File saved locally in Cortex. Content extraction is not available for this file type yet.";r.category="Files";r.tags="file,attachment";r.engine="file_metadata";r.version="1";return r;
    }
}
