package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import java.util.Locale;

/** Routes supported archive formats to bounded streaming parsers. */
public final class WorkDocumentParser {
    public static final String VERSION="work_document_parser_001";
    private WorkDocumentParser(){}

    public static WorkParsedDocument parse(Context context,Uri uri,String extension)throws Exception{
        String ext=extension==null?"":extension.toLowerCase(Locale.ROOT).replace(".","").trim();
        switch(ext){
            case "xlsx": case "docx": case "pptx":
                return WorkOoxmlParser.parse(context,uri,ext);
            case "pdf":
                return WorkPdfParser.parse(context,uri);
            default:
                throw new UnsupportedOperationException("Unsupported Work Vault format: "+ext);
        }
    }

    public static boolean supported(String extension){
        String e=extension==null?"":extension.toLowerCase(Locale.ROOT).replace(".","").trim();
        return "xlsx".equals(e)||"docx".equals(e)||"pptx".equals(e)||"pdf".equals(e);
    }
}
