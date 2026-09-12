package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Read model for generated Work Vault outputs; these rows are derived artifacts, never source evidence. */
final class WorkGeneratedDocumentsReader {
    static final String VERSION="work_generated_documents_reader_001";
    private WorkGeneratedDocumentsReader(){}

    static List<Row> load(SQLiteDatabase db,int limit){
        ArrayList<Row> out=new ArrayList<>();if(db==null)return out;
        WorkGeneratedDocumentRegistry.ensure(db);WorkChatGptBuildRequestRegistry.ensure(db);
        Cursor c=db.rawQuery(
                "SELECT g.id,g.document_kind,g.output_format,COALESCE(g.project_filter,''),g.generator,g.origin,g.package_path,COALESCE(g.package_uri,''),g.created_at,"+
                "COALESCE((SELECT b.returned_file_name FROM work_chatgpt_build_requests b WHERE b.generated_document_id=g.id ORDER BY b.updated_at DESC LIMIT 1),'') "+
                "FROM work_generated_documents g ORDER BY g.created_at DESC,g.id DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1,Math.min(500,limit)))});
        try{while(c.moveToNext())out.add(new Row(c.getLong(0),safe(c.getString(1)),safe(c.getString(2)),safe(c.getString(3)),safe(c.getString(4)),safe(c.getString(5)),safe(c.getString(6)),safe(c.getString(7)),c.getLong(8),safe(c.getString(9))));}finally{c.close();}
        return out;
    }

    static boolean isSourceEvidence(Row row){return false;}
    static boolean isOpenableUri(String uri){return uri!=null&&uri.trim().toLowerCase(java.util.Locale.ROOT).startsWith("content://");}
    static String displayName(Row row){
        if(row==null)return "Generated document";
        if(!row.returnedFileName.isEmpty())return row.returnedFileName;
        String kind=row.documentKind.isEmpty()?"Generated document":row.documentKind.replace('_',' ');
        return row.outputFormat.isEmpty()?kind:kind+" · "+row.outputFormat.toUpperCase(java.util.Locale.ROOT);
    }
    private static String safe(String s){return s==null?"":s;}

    static final class Row{
        final long id,createdAt;final String documentKind,outputFormat,project,generator,origin,path,uri,returnedFileName;
        Row(long id,String kind,String format,String project,String generator,String origin,String path,String uri,long createdAt,String returnedFileName){this.id=id;this.documentKind=kind;this.outputFormat=format;this.project=project;this.generator=generator;this.origin=origin;this.path=path;this.uri=uri;this.createdAt=createdAt;this.returnedFileName=returnedFileName;}
    }
}
