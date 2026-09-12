package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Builds a bounded, source-grounded package for local generation or ChatGPT document creation. */
public final class WorkDocumentBuildPackage {
    public static final String VERSION="work_document_build_package_002";
    private WorkDocumentBuildPackage(){}

    public static JSONObject build(VaultDb vault,WorkDocumentRecipe.Kind kind,String projectFilter){
        WorkDocumentRecipe.Recipe recipe=WorkDocumentRecipe.forKind(kind);
        JSONObject root=new JSONObject();
        SQLiteDatabase db=vault.getReadableDatabase();
        String project=projectFilter==null?"":projectFilter.trim();
        try{
            root.put("schemaVersion",VERSION);
            root.put("requestType","CREATE_DOCUMENT");
            root.put("documentKind",recipe.kind.name());
            root.put("outputFormat",recipe.outputFormat);
            root.put("orderFamily",recipe.family);
            root.put("orderScope",recipe.scope);
            root.put("projectFilter",project);
            root.put("generationPolicy",generationPolicy(recipe));
            root.put("requiredFields",new JSONArray(recipe.requiredFields));
            root.put("preferredSections",new JSONArray(recipe.preferredSections));
            root.put("projects",projects(db,project));
            root.put("procurementReferences",refs(db,project));
            root.put("facts",facts(db,project));
            root.put("priceRecords",prices(db,project));
            root.put("sourceEvidence",sources(db,project));
            root.put("instructions",instructions(recipe));
            root.put("createdAt",System.currentTimeMillis());
        }catch(Throwable ignored){}
        return root;
    }

    private static JSONObject generationPolicy(WorkDocumentRecipe.Recipe r){
        JSONObject o=new JSONObject();try{
            o.put("factsMustBeGrounded",true);
            o.put("doNotInferMissingCommercialFacts",true);
            o.put("missingRequiredFields","LEAVE_BLANK_OR_MARK_FOR_REVIEW");
            o.put("preserveSourceProvenance",true);
            o.put("generatedDocumentIsNotOriginalEvidence",true);
            o.put("language","Arabic or source language as appropriate");
            o.put("rtlWhenArabic",true);
            o.put("localGenerationCandidate",r.localCandidate);
        }catch(Throwable ignored){}return o;
    }

    private static JSONArray projects(SQLiteDatabase db,String filter){
        JSONArray a=new JSONArray();String sql="SELECT id,canonical_name FROM work_projects";ArrayList<String> args=new ArrayList<>();
        if(!filter.isEmpty()){sql+=" WHERE lower(canonical_name) LIKE ?";args.add("%"+filter.toLowerCase(Locale.ROOT)+"%");}sql+=" ORDER BY updated_at DESC LIMIT 20";
        Cursor c=db.rawQuery(sql,args.toArray(new String[0]));while(c.moveToNext()){JSONObject o=new JSONObject();try{o.put("id",c.getLong(0));o.put("name",s(c,1));a.put(o);}catch(Throwable ignored){}}c.close();return a;
    }

    private static JSONArray refs(SQLiteDatabase db,String filter){
        JSONArray a=new JSONArray();String sql="SELECT r.ref_type,r.ref_value,r.confidence,f.display_name,f.document_uri,p.canonical_name FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id LEFT JOIN work_projects p ON p.id=r.project_id WHERE f.active_version_id>0 AND r.version_id=f.active_version_id";
        ArrayList<String> args=new ArrayList<>();if(!filter.isEmpty()){sql+=" AND lower(COALESCE(p.canonical_name,'')) LIKE ?";args.add("%"+filter.toLowerCase(Locale.ROOT)+"%");}sql+=" ORDER BY r.created_at DESC LIMIT 120";
        Cursor c=db.rawQuery(sql,args.toArray(new String[0]));while(c.moveToNext()){JSONObject o=new JSONObject();try{o.put("type",s(c,0));o.put("value",s(c,1));o.put("confidence",c.getDouble(2));o.put("file",s(c,3));o.put("uri",s(c,4));o.put("project",s(c,5));a.put(o);}catch(Throwable ignored){}}c.close();return a;
    }

    private static JSONArray facts(SQLiteDatabase db,String filter){
        JSONArray a=new JSONArray();String sql="SELECT x.fact_type,x.fact_key,x.text_value,x.numeric_value,x.unit,x.currency,x.sheet_name,x.page_number,x.slide_number,x.row_number,x.column_name,x.confidence,f.display_name,f.document_uri,p.canonical_name FROM work_facts x JOIN work_files f ON f.id=x.file_id LEFT JOIN work_projects p ON p.id=x.project_id WHERE f.active_version_id>0 AND x.version_id=f.active_version_id";
        ArrayList<String> args=new ArrayList<>();if(!filter.isEmpty()){sql+=" AND lower(COALESCE(p.canonical_name,'')) LIKE ?";args.add("%"+filter.toLowerCase(Locale.ROOT)+"%");}sql+=" ORDER BY x.created_at DESC LIMIT 250";
        Cursor c=db.rawQuery(sql,args.toArray(new String[0]));while(c.moveToNext()){JSONObject o=new JSONObject();try{o.put("type",s(c,0));o.put("key",s(c,1));o.put("text",s(c,2));if(!c.isNull(3))o.put("number",c.getDouble(3));o.put("unit",s(c,4));o.put("currency",s(c,5));o.put("sheet",s(c,6));o.put("page",c.getInt(7));o.put("slide",c.getInt(8));o.put("row",c.getInt(9));o.put("column",s(c,10));o.put("confidence",c.getDouble(11));o.put("file",s(c,12));o.put("uri",s(c,13));o.put("project",s(c,14));a.put(o);}catch(Throwable ignored){}}c.close();return a;
    }

    private static JSONArray prices(SQLiteDatabase db,String filter){
        JSONArray a=new JSONArray();String sql="SELECT x.item_name,x.vendor_name,x.quantity,x.unit,x.unit_price,x.total_price,x.currency,x.reference_type,x.reference_value,x.sheet_name,x.page_number,x.row_number,x.confidence,f.display_name,f.document_uri,p.canonical_name FROM work_price_records x JOIN work_files f ON f.id=x.file_id LEFT JOIN work_projects p ON p.id=x.project_id WHERE f.active_version_id>0 AND x.version_id=f.active_version_id";
        ArrayList<String> args=new ArrayList<>();if(!filter.isEmpty()){sql+=" AND lower(COALESCE(p.canonical_name,'')) LIKE ?";args.add("%"+filter.toLowerCase(Locale.ROOT)+"%");}sql+=" ORDER BY f.modified_at DESC,x.created_at DESC LIMIT 250";
        Cursor c=db.rawQuery(sql,args.toArray(new String[0]));while(c.moveToNext()){JSONObject o=new JSONObject();try{o.put("item",s(c,0));o.put("vendor",s(c,1));if(!c.isNull(2))o.put("quantity",c.getDouble(2));o.put("unit",s(c,3));if(!c.isNull(4))o.put("unitPrice",c.getDouble(4));if(!c.isNull(5))o.put("totalPrice",c.getDouble(5));o.put("currency",s(c,6));o.put("referenceType",s(c,7));o.put("referenceValue",s(c,8));o.put("sheet",s(c,9));o.put("page",c.getInt(10));o.put("row",c.getInt(11));o.put("confidence",c.getDouble(12));o.put("file",s(c,13));o.put("uri",s(c,14));o.put("project",s(c,15));a.put(o);}catch(Throwable ignored){}}c.close();return a;
    }

    private static JSONArray sources(SQLiteDatabase db,String filter){
        JSONArray a=new JSONArray();String sql="SELECT DISTINCT f.display_name,f.document_uri,f.mime_type,f.modified_at,p.canonical_name FROM work_files f LEFT JOIN work_facts x ON x.file_id=f.id AND x.version_id=f.active_version_id LEFT JOIN work_projects p ON p.id=x.project_id WHERE f.active_version_id>0 AND f.state IN ('indexed','needs_ocr')";
        ArrayList<String> args=new ArrayList<>();if(!filter.isEmpty()){sql+=" AND lower(COALESCE(p.canonical_name,'')) LIKE ?";args.add("%"+filter.toLowerCase(Locale.ROOT)+"%");}sql+=" ORDER BY f.modified_at DESC LIMIT 120";
        Cursor c=db.rawQuery(sql,args.toArray(new String[0]));while(c.moveToNext()){JSONObject o=new JSONObject();try{o.put("file",s(c,0));o.put("uri",s(c,1));o.put("mimeType",s(c,2));o.put("modifiedAt",c.getLong(3));o.put("project",s(c,4));a.put(o);}catch(Throwable ignored){}}c.close();return a;
    }

    public static String instructions(WorkDocumentRecipe.Recipe r){
        return "Create the final "+r.outputFormat+" document from the attached JSON package. " +
                "Use only supplied grounded facts and source evidence. Do not invent project, vendor, commercial, quantity, price, tax, date, approval, PR or PO facts. " +
                "If required information is missing, leave it blank or mark it clearly for review. Preserve Arabic RTL when Arabic is used. " +
                "Follow the requested document kind, family and scope exactly. Build a professional production-ready document with the requested sections. " +
                "Where source references exist, retain them in notes/source register. Return the finished file, not merely draft text.";
    }

    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
}
