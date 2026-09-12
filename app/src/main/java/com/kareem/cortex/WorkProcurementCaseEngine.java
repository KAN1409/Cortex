package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Read-only procurement case projection built from already-grounded Work Vault evidence.
 * It never creates or strengthens facts. Missing stages mean "no grounded evidence found",
 * not proof that the business step never happened.
 */
public final class WorkProcurementCaseEngine {
    public static final String VERSION="work_procurement_case_engine_001";
    private WorkProcurementCaseEngine(){}

    public static ArrayList<Case> load(VaultDb vault,int limit){
        if(vault==null)return new ArrayList<>();
        SQLiteDatabase db=vault.getReadableDatabase();
        WorkVaultIndexSchema.ensure(db);WorkDocumentProfileStore.ensure(db);
        ArrayList<Case> out=new ArrayList<>();
        Cursor c=db.rawQuery(
                "SELECT normalized_value,COALESCE(NULLIF(project_id,0),0),MAX(confidence) "+
                "FROM work_procurement_refs WHERE ref_type='PR' AND normalized_value<>'' "+
                "GROUP BY normalized_value,COALESCE(NULLIF(project_id,0),0) ORDER BY MAX(created_at) DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1,limit*4))});
        while(c.moveToNext()){
            String pr=s(c,0);long projectId=c.getLong(1);double refConfidence=c.getDouble(2);
            Case x=build(db,pr,projectId,refConfidence);
            if(x!=null)out.add(x);
        }c.close();
        out.sort((a,b)->Integer.compare(b.priority,a.priority));
        if(out.size()>Math.max(1,limit))return new ArrayList<>(out.subList(0,Math.max(1,limit)));
        return out;
    }

    private static Case build(SQLiteDatabase db,String pr,long projectId,double refConfidence){
        ArrayList<Long> refIds=new ArrayList<>();
        Cursor r=db.rawQuery("SELECT id FROM work_procurement_refs WHERE ref_type='PR' AND normalized_value=? AND (?=0 OR project_id=? OR project_id=0)",new String[]{pr,String.valueOf(projectId),String.valueOf(projectId)});
        while(r.moveToNext())refIds.add(r.getLong(0));r.close();
        if(refIds.isEmpty())return null;

        Stage stage=new Stage();
        LinkedHashSet<String> docs=new LinkedHashSet<>();
        LinkedHashSet<String> statuses=new LinkedHashSet<>();
        for(long refId:refIds){
            Cursor l=db.rawQuery("SELECT from_kind,from_id,to_kind,to_id,relation,confidence FROM work_procurement_links WHERE (from_kind='REF' AND from_id=?) OR (to_kind='REF' AND to_id=?) ORDER BY confidence DESC",new String[]{String.valueOf(refId),String.valueOf(refId)});
            while(l.moveToNext()){
                String fk=s(l,0),tk=s(l,2),rel=s(l,4);long fid=l.getLong(1),tid=l.getLong(3);double conf=l.getDouble(5);
                String kind;long id;if("REF".equals(fk)&&fid==refId){kind=tk;id=tid;}else{kind=fk;id=fid;}
                if("FILE".equals(kind)){
                    String type=WorkDocumentProfileStore.typeForFile(db,id);
                    markDocument(stage,type,conf);String name=fileName(db,id);if(!name.isEmpty())docs.add(type+" · "+name);
                }else if("FOLLOWUP".equals(kind)){
                    String status=followStatus(db,id);if(!status.isEmpty())statuses.add(status);
                }else if("REF".equals(kind)&&rel.contains("pr_po"))stage.po=true;
            }l.close();
        }

        String project=projectName(db,projectId);
        String issue=issue(stage.quotation,stage.comparison,stage.approval,stage.po);
        int priority=priority(issue,statuses);
        return new Case(pr,project,stage.quotation,stage.comparison,stage.approval,stage.po,stage.invoice,stage.delivery,
                new ArrayList<>(docs),new ArrayList<>(statuses),issue,priority,refConfidence);
    }

    static String issue(boolean quotation,boolean comparison,boolean approval,boolean po){
        if(po)return "PO_EVIDENCE_FOUND";
        if(approval&&comparison)return "PO_EVIDENCE_NOT_FOUND";
        if(comparison)return "APPROVAL_EVIDENCE_NOT_FOUND";
        if(quotation)return "COMPARISON_EVIDENCE_NOT_FOUND";
        return "QUOTATION_EVIDENCE_NOT_FOUND";
    }

    private static int priority(String issue,List<String> statuses){
        String joined=statuses.toString().toLowerCase(Locale.ROOT);
        if(joined.contains("overdue")||joined.contains("urgent"))return 100;
        if("PO_EVIDENCE_NOT_FOUND".equals(issue))return 90;
        if("APPROVAL_EVIDENCE_NOT_FOUND".equals(issue))return 75;
        if("COMPARISON_EVIDENCE_NOT_FOUND".equals(issue))return 60;
        if("QUOTATION_EVIDENCE_NOT_FOUND".equals(issue))return 45;
        return 10;
    }

    private static void markDocument(Stage s,String type,double confidence){
        if(confidence<.70)return;
        if("QUOTATION".equals(type))s.quotation=true;
        else if("COMPARISON".equals(type))s.comparison=true;
        else if("APPROVAL".equals(type))s.approval=true;
        else if("PURCHASE_ORDER".equals(type))s.po=true;
        else if("INVOICE".equals(type))s.invoice=true;
        else if("DELIVERY".equals(type))s.delivery=true;
    }

    private static String fileName(SQLiteDatabase db,long id){Cursor c=db.rawQuery("SELECT display_name FROM work_files WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});String x=c.moveToFirst()?s(c,0):"";c.close();return x;}
    private static String followStatus(SQLiteDatabase db,long id){Cursor c=db.rawQuery("SELECT COALESCE(NULLIF(status,''),status_normalized) FROM work_followup_records WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});String x=c.moveToFirst()?s(c,0):"";c.close();return x;}
    private static String projectName(SQLiteDatabase db,long id){if(id<=0)return "";Cursor c=db.rawQuery("SELECT canonical_name FROM work_projects WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});String x=c.moveToFirst()?s(c,0):"";c.close();return x;}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class Stage{boolean quotation,comparison,approval,po,invoice,delivery;}

    public static final class Case{
        public final String pr,project,issue;public final boolean quotation,comparison,approval,po,invoice,delivery;public final ArrayList<String> documents,statuses;public final int priority;public final double referenceConfidence;
        Case(String pr,String project,boolean quotation,boolean comparison,boolean approval,boolean po,boolean invoice,boolean delivery,ArrayList<String> documents,ArrayList<String> statuses,String issue,int priority,double confidence){
            this.pr=pr;this.project=project;this.quotation=quotation;this.comparison=comparison;this.approval=approval;this.po=po;this.invoice=invoice;this.delivery=delivery;this.documents=documents;this.statuses=statuses;this.issue=issue;this.priority=priority;this.referenceConfidence=confidence;
        }
        public boolean needsAttention(){return !"PO_EVIDENCE_FOUND".equals(issue);}
        public String headline(){
            if("PO_EVIDENCE_NOT_FOUND".equals(issue))return "Comparison / approval found · PO evidence not found";
            if("APPROVAL_EVIDENCE_NOT_FOUND".equals(issue))return "Comparison found · approval evidence not found";
            if("COMPARISON_EVIDENCE_NOT_FOUND".equals(issue))return "Quotation found · comparison evidence not found";
            if("QUOTATION_EVIDENCE_NOT_FOUND".equals(issue))return "PR found · quotation evidence not found";
            return "PO evidence found";
        }
    }
}
