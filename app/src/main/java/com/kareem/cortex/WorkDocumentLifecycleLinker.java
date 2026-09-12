package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/**
 * Adds provenance-bearing document-role edges after document classification.
 * This layer never creates procurement facts; it only links an already-classified FILE
 * to exact PR/PO references extracted from that same active file version. Ambiguous reference sets are skipped.
 */
public final class WorkDocumentLifecycleLinker {
    public static final String VERSION="work_document_lifecycle_linker_002";
    private static final String RULE_PREFIX="document_profile_";
    private WorkDocumentLifecycleLinker(){}

    public static Result rebuildForSource(SQLiteDatabase db,long sourceId){
        Result out=new Result();
        if(db==null||sourceId<=0)return out;
        WorkVaultIndexSchema.ensure(db);WorkDocumentProfileStore.ensure(db);
        db.delete("work_procurement_links","evidence_rule LIKE ? AND source_file_id IN (SELECT id FROM work_files WHERE source_id=?)",new String[]{RULE_PREFIX+"%",String.valueOf(sourceId)});

        Cursor files=db.rawQuery(
                "SELECT f.id,p.document_type,p.confidence FROM work_files f JOIN work_document_profiles p ON p.file_id=f.id AND p.version_id=f.active_version_id WHERE f.source_id=? AND f.active_version_id>0 AND f.state IN ('indexed','needs_ocr') ORDER BY f.id",
                new String[]{String.valueOf(sourceId)});
        while(files.moveToNext()){
            long fileId=files.getLong(0);String type=s(files,1);double confidence=files.getDouble(2);
            if(confidence<.70){out.lowConfidenceSkipped++;continue;}
            ArrayList<Ref> refs=refs(db,fileId);
            Ref pr=only(refs,"PR"),po=only(refs,"PO");
            int prCount=count(refs,"PR"),poCount=count(refs,"PO");
            String relation=relationForType(type);
            if(relation.isEmpty())continue;

            if("PURCHASE_ORDER".equals(type)){
                if(poCount==1&&po!=null){add(db,fileId,po.id,"purchase_order_document_for_po",Math.min(.99,.88+.10*confidence),RULE_PREFIX+"purchase_order_plus_exact_po",fileId,po.projectId);out.links++;}
                else if(poCount>1)out.ambiguousSkipped++;
                if(prCount==1&&pr!=null){add(db,fileId,pr.id,"purchase_order_document_for_pr",Math.min(.97,.84+.10*confidence),RULE_PREFIX+"purchase_order_plus_exact_pr",fileId,pr.projectId);out.links++;}
                else if(prCount>1)out.ambiguousSkipped++;
                continue;
            }
            if("PURCHASE_REQUEST".equals(type)){
                if(prCount==1&&pr!=null){add(db,fileId,pr.id,"purchase_request_document_for_pr",Math.min(.99,.89+.09*confidence),RULE_PREFIX+"purchase_request_plus_exact_pr",fileId,pr.projectId);out.links++;}
                else if(prCount>1)out.ambiguousSkipped++;
                continue;
            }

            if(prCount==1&&pr!=null){
                add(db,fileId,pr.id,relation+"_for_pr",Math.min(.97,.82+.12*confidence),RULE_PREFIX+type.toLowerCase(Locale.ROOT)+"_plus_exact_pr",fileId,pr.projectId);out.links++;
            }else if(prCount>1){
                out.ambiguousSkipped++;
            }else if(poCount==1&&po!=null){
                add(db,fileId,po.id,relation+"_for_po",Math.min(.94,.78+.12*confidence),RULE_PREFIX+type.toLowerCase(Locale.ROOT)+"_plus_exact_po",fileId,po.projectId);out.links++;
            }else if(poCount>1){
                out.ambiguousSkipped++;
            }
        }
        files.close();return out;
    }

    static String relationForType(String type){
        if("QUOTATION".equals(type))return "quotation_document";
        if("COMPARISON".equals(type))return "comparison_document";
        if("APPROVAL".equals(type))return "approval_document";
        if("FOLLOW_UP".equals(type))return "followup_document";
        if("PURCHASE_ORDER".equals(type))return "purchase_order_document";
        if("PURCHASE_REQUEST".equals(type))return "purchase_request_document";
        if("INVOICE".equals(type))return "invoice_document";
        if("DELIVERY".equals(type))return "delivery_document";
        return "";
    }

    private static ArrayList<Ref> refs(SQLiteDatabase db,long fileId){
        ArrayList<Ref> out=new ArrayList<>();Cursor c=db.rawQuery("SELECT r.id,r.ref_type,r.normalized_value,r.project_id FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE r.file_id=? AND f.active_version_id>0 AND r.version_id=f.active_version_id ORDER BY r.id",new String[]{String.valueOf(fileId)});
        while(c.moveToNext())out.add(new Ref(c.getLong(0),s(c,1),s(c,2),c.getLong(3)));c.close();return out;
    }
    private static Ref only(List<Ref> refs,String type){Ref found=null;for(Ref r:refs)if(type.equals(r.type)){if(found!=null)return null;found=r;}return found;}
    private static int count(List<Ref> refs,String type){int n=0;for(Ref r:refs)if(type.equals(r.type))n++;return n;}
    private static void add(SQLiteDatabase db,long fileId,long refId,String relation,double confidence,String rule,long sourceFile,long projectId){
        if(fileId<=0||refId<=0)return;ContentValues v=new ContentValues();v.put("from_kind","FILE");v.put("from_id",fileId);v.put("to_kind","REF");v.put("to_id",refId);v.put("relation",relation);v.put("confidence",confidence);v.put("evidence_rule",rule);v.put("source_file_id",sourceFile);v.put("project_id",projectId);v.put("created_at",System.currentTimeMillis());db.insertWithOnConflict("work_procurement_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class Ref{final long id,projectId;final String type,value;Ref(long id,String type,String value,long projectId){this.id=id;this.type=type;this.value=value;this.projectId=projectId;}}
    public static final class Result{public int links,ambiguousSkipped,lowConfidenceSkipped;}
}
