package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Expands archive retrieval through provenance-bearing procurement links without inventing missing nodes. */
public final class WorkProcurementLifecycleSearch {
    private WorkProcurementLifecycleSearch(){}

    public static void append(SQLiteDatabase db,String query,List<WorkVaultSearch.Hit> out,int max){
        if(db==null||out==null||max<=0)return;String token=referenceToken(query);if(token.isEmpty())return;
        Cursor c=db.rawQuery("SELECT r.id,r.ref_type,r.ref_value,r.file_id,r.project_id FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE f.active_version_id>0 AND r.version_id=f.active_version_id AND r.normalized_value LIKE ? ORDER BY r.confidence DESC,r.id DESC LIMIT 20",new String[]{"%"+token+"%"});
        HashSet<Long> seenRefs=new HashSet<>();
        while(c.moveToNext()&&out.size()<max){long refId=c.getLong(0);if(!seenRefs.add(refId))continue;appendForRef(db,refId,out,max);}c.close();
    }

    private static void appendForRef(SQLiteDatabase db,long refId,List<WorkVaultSearch.Hit> out,int max){
        Cursor c=db.rawQuery("SELECT from_kind,from_id,to_kind,to_id,relation,confidence,evidence_rule,source_file_id FROM work_procurement_links WHERE (from_kind='REF' AND from_id=?) OR (to_kind='REF' AND to_id=?) ORDER BY confidence DESC,id DESC LIMIT 60",new String[]{String.valueOf(refId),String.valueOf(refId)});
        while(c.moveToNext()&&out.size()<max){String fk=s(c,0),tk=s(c,2),relation=s(c,4),rule=s(c,6);long fid=c.getLong(1),tid=c.getLong(3);double conf=c.getDouble(5);String kind;long id;if("REF".equals(fk)&&fid==refId){kind=tk;id=tid;}else{kind=fk;id=fid;}WorkVaultSearch.Hit h=resolve(db,kind,id,relation,rule,conf);if(h!=null)out.add(h);}c.close();
    }

    private static WorkVaultSearch.Hit resolve(SQLiteDatabase db,String kind,long id,String relation,String rule,double confidence){
        if("REF".equals(kind))return refHit(db,id,relation,rule,confidence);
        if("FOLLOWUP".equals(kind))return followHit(db,id,relation,rule,confidence);
        if("PRICE".equals(kind))return priceHit(db,id,relation,rule,confidence);
        if("FILE".equals(kind))return fileHit(db,id,relation,rule,confidence);
        return null;
    }

    private static WorkVaultSearch.Hit refHit(SQLiteDatabase db,long id,String relation,String rule,double conf){
        Cursor c=db.rawQuery("SELECT r.file_id,f.display_name,f.document_uri,r.ref_type,r.ref_value FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE r.id=? AND f.active_version_id>0 AND r.version_id=f.active_version_id LIMIT 1",new String[]{String.valueOf(id)});if(!c.moveToFirst()){c.close();return null;}WorkVaultSearch.Hit h=base(c,0,1,2);h.kind="LIFECYCLE_REF";h.snippet=s(c,3)+" "+s(c,4)+linkSuffix(relation,rule,conf);h.score=Math.min(1.5,.55+conf);c.close();return h;
    }
    private static WorkVaultSearch.Hit followHit(SQLiteDatabase db,long id,String relation,String rule,double conf){
        Cursor c=db.rawQuery("SELECT u.file_id,f.display_name,f.document_uri,u.reference_type,u.reference_value,u.item_name,u.status,u.owner_name,u.due_text,u.remarks,u.sheet_name,u.page_number,u.row_number FROM work_followup_records u JOIN work_files f ON f.id=u.file_id WHERE u.id=? AND f.active_version_id>0 AND u.version_id=f.active_version_id LIMIT 1",new String[]{String.valueOf(id)});if(!c.moveToFirst()){c.close();return null;}WorkVaultSearch.Hit h=base(c,0,1,2);h.kind="LIFECYCLE_STATUS";StringBuilder b=new StringBuilder();if(!s(c,3).isEmpty()||!s(c,4).isEmpty())b.append(s(c,3)).append(' ').append(s(c,4));if(!s(c,5).isEmpty())b.append(" • ").append(s(c,5));if(!s(c,6).isEmpty())b.append(" • status ").append(s(c,6));if(!s(c,7).isEmpty())b.append(" • owner ").append(s(c,7));if(!s(c,8).isEmpty())b.append(" • due ").append(s(c,8));if(!s(c,9).isEmpty())b.append(" • ").append(s(c,9));b.append(linkSuffix(relation,rule,conf));h.snippet=b.toString();h.sheet=s(c,10);h.page=c.getInt(11);h.row=c.getInt(12);h.score=Math.min(1.5,.65+conf);c.close();return h;
    }
    private static WorkVaultSearch.Hit priceHit(SQLiteDatabase db,long id,String relation,String rule,double conf){
        Cursor c=db.rawQuery("SELECT p.file_id,f.display_name,f.document_uri,p.item_name,p.vendor_name,p.quantity,p.unit,p.unit_price,p.total_price,p.currency,p.sheet_name,p.page_number,p.row_number FROM work_price_records p JOIN work_files f ON f.id=p.file_id WHERE p.id=? AND f.active_version_id>0 AND p.version_id=f.active_version_id LIMIT 1",new String[]{String.valueOf(id)});if(!c.moveToFirst()){c.close();return null;}WorkVaultSearch.Hit h=base(c,0,1,2);h.kind="LIFECYCLE_PRICE";StringBuilder b=new StringBuilder(s(c,3));if(!s(c,4).isEmpty())b.append(" • vendor ").append(s(c,4));if(!c.isNull(5))b.append(" • qty ").append(c.getDouble(5)).append(' ').append(s(c,6));if(!c.isNull(7))b.append(" • unit price ").append(c.getDouble(7)).append(' ').append(s(c,9));if(!c.isNull(8))b.append(" • total ").append(c.getDouble(8)).append(' ').append(s(c,9));b.append(linkSuffix(relation,rule,conf));h.snippet=b.toString();h.sheet=s(c,10);h.page=c.getInt(11);h.row=c.getInt(12);h.score=Math.min(1.5,.60+conf);c.close();return h;
    }
    private static WorkVaultSearch.Hit fileHit(SQLiteDatabase db,long fileId,String relation,String rule,double conf){
        WorkDocumentProfileStore.ensure(db);
        Cursor c=db.rawQuery("SELECT f.id,f.display_name,f.document_uri,p.document_type,p.confidence FROM work_files f LEFT JOIN work_document_profiles p ON p.file_id=f.id AND p.version_id=f.active_version_id WHERE f.id=? AND f.active_version_id>0 LIMIT 1",new String[]{String.valueOf(fileId)});
        if(!c.moveToFirst()){c.close();return null;}WorkVaultSearch.Hit h=base(c,0,1,2);String type=s(c,3);double classConf=c.isNull(4)?0:c.getDouble(4);h.kind="LIFECYCLE_DOCUMENT";String label=type.isEmpty()?"WORK_DOCUMENT":type;h.snippet="Document type "+label+" • classifier confidence "+Math.round(classConf*100)+"%"+linkSuffix(relation,rule,conf);h.score=Math.min(1.5,.62+conf);c.close();return h;
    }

    private static WorkVaultSearch.Hit base(Cursor c,int fileId,int name,int uri){WorkVaultSearch.Hit h=new WorkVaultSearch.Hit();h.fileId=c.getLong(fileId);h.fileName=s(c,name);h.documentUri=s(c,uri);return h;}
    private static String linkSuffix(String relation,String rule,double conf){return " • linked: "+relation+" • confidence "+Math.round(conf*100)+"% • rule "+rule;}
    private static String referenceToken(String query){if(query==null)return "";String[] parts=query.toUpperCase(Locale.ROOT).split("[^A-Z0-9._/-]+");String best="";for(String p:parts){String x=p.replaceAll("^[#:/-]+|[#:/-]+$","");if(x.length()<2||"PR".equals(x)||"PO".equals(x)||"REF".equals(x))continue;boolean hasDigit=false;for(int i=0;i<x.length();i++)if(Character.isDigit(x.charAt(i))){hasDigit=true;break;}if(hasDigit&&x.length()>best.length())best=x;}return best;}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
}
