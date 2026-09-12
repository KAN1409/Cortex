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
    public static final String VERSION="work_procurement_linker_003";
    private WorkProcurementLinker(){}

    /**
     * Removes every procurement link that can reference records owned by a file before those
     * records are deleted/recreated during re-indexing. This includes inbound links created by
     * other files, not only rows whose source_file_id is the file being rebuilt.
     */
    public static int cleanupForReindex(SQLiteDatabase db,long fileId){
        if(db==null||fileId<=0)return 0;WorkVaultIndexSchema.ensure(db);
        String id=String.valueOf(fileId);
        String where=
                "source_file_id=?"+
                " OR (from_kind='REF' AND from_id IN (SELECT id FROM work_procurement_refs WHERE file_id=?))"+
                " OR (to_kind='REF' AND to_id IN (SELECT id FROM work_procurement_refs WHERE file_id=?))"+
                " OR (from_kind='FOLLOWUP' AND from_id IN (SELECT id FROM work_followup_records WHERE file_id=?))"+
                " OR (to_kind='FOLLOWUP' AND to_id IN (SELECT id FROM work_followup_records WHERE file_id=?))"+
                " OR (from_kind='PRICE' AND from_id IN (SELECT id FROM work_price_records WHERE file_id=?))"+
                " OR (to_kind='PRICE' AND to_id IN (SELECT id FROM work_price_records WHERE file_id=?))";
        return db.delete("work_procurement_links",where,new String[]{id,id,id,id,id,id,id});
    }

    public static Result rebuildForFile(SQLiteDatabase db,long fileId,long projectId){
        Result out=new Result();if(db==null||fileId<=0)return out;WorkVaultIndexSchema.ensure(db);
        db.delete("work_procurement_links","source_file_id=?",new String[]{String.valueOf(fileId)});
        long now=System.currentTimeMillis();

        ArrayList<Ref> refs=refsForFile(db,fileId);
        ArrayList<Follow> follow=followForFile(db,fileId);
        ArrayList<Price> prices=pricesForFile(db,fileId);

        // Exact reference identity is strong only inside the same known project. If one side has
        // no project yet we allow a weaker provisional link; known cross-project collisions are skipped.
        for(Ref r:refs){
            Cursor c=db.rawQuery(
                    "SELECT r.id,r.file_id,r.project_id FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id "+
                    "WHERE r.ref_type=? AND r.normalized_value=? AND r.id<>? AND f.active_version_id>0 AND r.version_id=f.active_version_id",
                    new String[]{r.type,r.value,String.valueOf(r.id)});
            while(c.moveToNext()){
                long other=c.getLong(0),otherProject=c.getLong(2);
                if(!projectsCompatible(projectId,otherProject)){out.crossProjectSkipped++;continue;}
                long from=Math.min(r.id,other),to=Math.max(r.id,other);
                double conf=sameReferenceConfidence(projectId,otherProject);
                link(db,"REF",from,"REF",to,"same_reference",conf,
                        projectId>0&&otherProject>0?"exact_reference_same_project":"exact_reference_project_unknown",
                        fileId,resolvedProject(projectId,otherProject),now);out.sameReference++;
            }c.close();
        }

        // A status row belongs to a procurement reference only on exact type/value match, with
        // known cross-project collisions rejected before an edge is created.
        for(Follow f:follow){
            if(f.refType.isEmpty()||f.refValue.isEmpty())continue;
            String normalized=normRef(f.refValue);
            Cursor c=db.rawQuery(
                    "SELECT r.id,r.file_id,r.project_id FROM work_procurement_refs r JOIN work_files wf ON wf.id=r.file_id "+
                    "WHERE r.ref_type=? AND r.normalized_value=? AND (r.file_id=? OR (wf.active_version_id>0 AND r.version_id=wf.active_version_id))",
                    new String[]{f.refType,normalized,String.valueOf(fileId)});
            while(c.moveToNext()){
                long refId=c.getLong(0),refFile=c.getLong(1),refProject=c.getLong(2);
                if(refFile!=fileId&&!projectsCompatible(f.projectId,refProject)){out.crossProjectSkipped++;continue;}
                double conf=refFile==fileId?.995:(f.projectId>0&&refProject>0?.985:.93);
                link(db,"FOLLOWUP",f.id,"REF",refId,"status_for_reference",conf,
                        refFile==fileId?"exact_reference_same_file":(f.projectId>0&&refProject>0?"exact_reference_same_project":"exact_reference_project_unknown"),
                        fileId,resolvedProject(f.projectId,refProject),now);out.statusLinks++;
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

    static boolean projectsCompatible(long a,long b){return a<=0||b<=0||a==b;}
    static double sameReferenceConfidence(long a,long b){return a>0&&b>0&&a==b?.995:.93;}
    static long resolvedProject(long a,long b){return a>0?a:Math.max(0,b);}

    private static ArrayList<Ref> refsForFile(SQLiteDatabase db,long fileId){
        ArrayList<Ref> out=new ArrayList<>();Cursor c=db.rawQuery("SELECT id,ref_type,normalized_value FROM work_procurement_refs WHERE file_id=? ORDER BY id",new String[]{String.valueOf(fileId)});while(c.moveToNext())out.add(new Ref(c.getLong(0),s(c,1),s(c,2)));c.close();return out;
    }
    private static ArrayList<Follow> followForFile(SQLiteDatabase db,long fileId){
        ArrayList<Follow> out=new ArrayList<>();Cursor c=db.rawQuery("SELECT id,reference_type,reference_value,project_id FROM work_followup_records WHERE file_id=? ORDER BY id",new String[]{String.valueOf(fileId)});while(c.moveToNext())out.add(new Follow(c.getLong(0),s(c,1),s(c,2),c.getLong(3)));c.close();return out;
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
    private static final class Follow{final long id,projectId;final String refType,refValue;Follow(long i,String t,String v,long p){id=i;refType=t;refValue=v;projectId=p;}}
    private static final class Price{final long id;Price(long i){id=i;}}
    public static final class Result{public int sameReference,statusLinks,lifecycleLinks,priceLinks,ambiguousSkipped,crossProjectSkipped;public int total(){return sameReference+statusLinks+lifecycleLinks+priceLinks;}}
}
