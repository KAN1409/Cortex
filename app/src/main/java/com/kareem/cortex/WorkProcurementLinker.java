package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/**
 * Builds evidence links between already-grounded procurement records.
 * Never creates canonical procurement facts. Ambiguous joins are deliberately skipped.
 */
public final class WorkProcurementLinker {
    public static final String VERSION="work_procurement_linker_001";
    private WorkProcurementLinker(){}

    public static Result rebuildForFile(SQLiteDatabase db,long fileId,long projectId){
        Result out=new Result();if(db==null||fileId<=0)return out;WorkVaultIndexSchema.ensure(db);
        db.delete("work_procurement_links","source_file_id=?",new String[]{String.valueOf(fileId)});
        long now=System.currentTimeMillis();

        ArrayList<Ref> refs=refsForFile(db,fileId);
        ArrayList<Follow> follow=followForFile(db,fileId);
        ArrayList<Price> prices=pricesForFile(db,fileId);

        // Exact reference identity is safe even across different files.
        for(Ref r:refs){
            Cursor c=db.rawQuery("SELECT id,file_id,project_id FROM work_procurement_refs WHERE ref_type=? AND normalized_value=? AND id<>?",new String[]{r.type,r.value,String.valueOf(r.id)});
            while(c.moveToNext()){
                long other=c.getLong(0),otherFile=c.getLong(1),otherProject=c.getLong(2);
                double conf=(projectId>0&&otherProject>0&&projectId==otherProject)?.995:.985;
                link(db,"REF",r.id,"REF",other,"same_reference",conf,"exact_type_and_normalized_reference",fileId,projectId>0?projectId:otherProject,now);out.sameReference++;
            }c.close();
        }

        // A status row belongs to a procurement reference only on exact type/value match.
        for(Follow f:follow){
            if(f.refType.isEmpty()||f.refValue.isEmpty())continue;
            String normalized=normRef(f.refValue);
            Cursor c=db.rawQuery("SELECT id,file_id,project_id FROM work_procurement_refs WHERE ref_type=? AND normalized_value=?",new String[]{f.refType,normalized});
            while(c.moveToNext()){
                long refId=c.getLong(0),refFile=c.getLong(1),refProject=c.getLong(2);
                double conf=refFile==fileId?.995:.98;
                link(db,"FOLLOWUP",f.id,"REF",refId,"status_for_reference",conf,"exact_reference_identity",fileId,projectId>0?projectId:refProject,now);out.statusLinks++;
            }c.close();
        }

        Ref onlyPr=onlyOfType(refs,"PR"),onlyPo=onlyOfType(refs,"PO");
        if(onlyPr!=null&&onlyPo!=null){
            link(db,"REF",onlyPr.id,"REF",onlyPo.id,"co_document_pr_po",.82,"single_pr_and_single_po_in_same_source_file",fileId,projectId,now);out.lifecycleLinks++;
        }

        // Prices are linked to a reference only when that source file contains one unambiguous lifecycle ref.
        Ref sole=refs.size()==1?refs.get(0):null;
        if(sole!=null){
            for(Price p:prices){link(db,"PRICE",p.id,"REF",sole.id,"price_context_for_reference",.86,"single_procurement_reference_in_same_source_file",fileId,projectId,now);out.priceLinks++;}
        }else if(onlyPo!=null&&countOfType(refs,"PO")==1&&countOfType(refs,"PR")==0){
            for(Price p:prices){link(db,"PRICE",p.id,"REF",onlyPo.id,"price_context_for_po",.88,"single_po_and_no_pr_in_same_source_file",fileId,projectId,now);out.priceLinks++;}
        }else if(onlyPr!=null&&countOfType(refs,"PR")==1&&countOfType(refs,"PO")==0){
            for(Price p:prices){link(db,"PRICE",p.id,"REF",onlyPr.id,"price_context_for_pr",.84,"single_pr_and_no_po_in_same_source_file",fileId,projectId,now);out.priceLinks++;}
        }else if(!prices.isEmpty()&&!refs.isEmpty())out.ambiguousSkipped+=prices.size();

        return out;
    }

    private static ArrayList<Ref> refsForFile(SQLiteDatabase db,long fileId){
        ArrayList<Ref> out=new ArrayList<>();Cursor c=db.rawQuery("SELECT id,ref_type,normalized_value FROM work_procurement_refs WHERE file_id=? ORDER BY id",new String[]{String.valueOf(fileId)});while(c.moveToNext())out.add(new Ref(c.getLong(0),s(c,1),s(c,2)));c.close();return out;
    }
    private static ArrayList<Follow> followForFile(SQLiteDatabase db,long fileId){
        ArrayList<Follow> out=new ArrayList<>();Cursor c=db.rawQuery("SELECT id,reference_type,reference_value FROM work_followup_records WHERE file_id=? ORDER BY id",new String[]{String.valueOf(fileId)});while(c.moveToNext())out.add(new Follow(c.getLong(0),s(c,1),s(c,2)));c.close();return out;
    }
    private static ArrayList<Price> pricesForFile(SQLiteDatabase db,long fileId){ArrayList<Price> out=new ArrayList<>();Cursor c=db.rawQuery("SELECT id FROM work_price_records WHERE file_id=? ORDER BY id",new String[]{String.valueOf(fileId)});while(c.moveToNext())out.add(new Price(c.getLong(0)));c.close();return out;}
    private static Ref onlyOfType(List<Ref> refs,String type){Ref found=null;for(Ref r:refs)if(type.equals(r.type)){if(found!=null)return null;found=r;}return found;}
    private static int countOfType(List<Ref> refs,String type){int n=0;for(Ref r:refs)if(type.equals(r.type))n++;return n;}
    private static void link(SQLiteDatabase db,String fk,long fid,String tk,long tid,String relation,double confidence,String rule,long sourceFile,long project,long now){
        if(fid<=0||tid<=0)return;ContentValues v=new ContentValues();v.put("from_kind",fk);v.put("from_id",fid);v.put("to_kind",tk);v.put("to_id",tid);v.put("relation",relation);v.put("confidence",confidence);v.put("evidence_rule",rule);v.put("source_file_id",sourceFile);v.put("project_id",project);v.put("created_at",now);db.insertWithOnConflict("work_procurement_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private static String normRef(String x){return x==null?"":x.trim().replaceAll("^[#:/-]+|[#:/-]+$","").toUpperCase(Locale.ROOT);}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class Ref{final long id;final String type,value;Ref(long i,String t,String v){id=i;type=t;value=v;}}
    private static final class Follow{final long id;final String refType,refValue;Follow(long i,String t,String v){id=i;refType=t;refValue=v;}}
    private static final class Price{final long id;Price(long i){id=i;}}
    public static final class Result{public int sameReference,statusLinks,lifecycleLinks,priceLinks,ambiguousSkipped;public int total(){return sameReference+statusLinks+lifecycleLinks+priceLinks;}}
}
