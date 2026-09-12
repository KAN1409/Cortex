package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Grounded lexical retrieval across active Work Vault file versions only. */
public final class WorkVaultSearch {
    public static final String VERSION="work_vault_search_008";
    private WorkVaultSearch(){}

    public static ArrayList<Hit> search(VaultDb vault,String query,int limit){
        SQLiteDatabase db=vault.getReadableDatabase();WorkVaultIndexSchema.ensure(db);
        String q=query==null?"":query.trim();ArrayList<Hit> out=new ArrayList<>();
        if(q.isEmpty())return out;
        String like="%"+q+"%";

        appendExactProcurementRefs(db,q,out,40);

        Cursor c=null;
        int fastText=WorkVaultFtsSearch.append(db,q,out,80);
        if(fastText<=0){
            c=db.rawQuery("SELECT c.file_id,f.display_name,f.document_uri,c.chunk_text,c.sheet_name,c.page_number,c.slide_number,c.row_number FROM work_chunks c JOIN work_files f ON f.id=c.file_id WHERE f.active_version_id>0 AND c.version_id=f.active_version_id AND (c.chunk_text LIKE ? OR f.display_name LIKE ?) LIMIT 80",new String[]{like,like});
            while(c.moveToNext()){Hit h=new Hit();h.kind="TEXT";h.fileId=c.getLong(0);h.fileName=s(c,1);h.documentUri=s(c,2);h.snippet=clip(s(c,3),900);h.sheet=s(c,4);h.page=c.getInt(5);h.slide=c.getInt(6);h.row=c.getInt(7);h.score=score(q,h.fileName+" "+h.snippet)+.15;out.add(h);}c.close();
        }

        c=db.rawQuery("SELECT p.file_id,f.display_name,f.document_uri,p.item_name,p.vendor_name,p.quantity,p.unit,p.unit_price,p.total_price,p.currency,p.sheet_name,p.page_number,p.row_number,p.reference_type,p.reference_value FROM work_price_records p JOIN work_files f ON f.id=p.file_id WHERE f.active_version_id>0 AND p.version_id=f.active_version_id AND (p.item_name LIKE ? OR p.vendor_name LIKE ? OR p.reference_value LIKE ? OR f.display_name LIKE ?) LIMIT 60",new String[]{like,like,like,like});
        while(c.moveToNext()){Hit h=new Hit();h.kind="PRICE";h.fileId=c.getLong(0);h.fileName=s(c,1);h.documentUri=s(c,2);StringBuilder b=new StringBuilder();b.append(s(c,3));if(!s(c,4).isEmpty())b.append(" • vendor ").append(s(c,4));if(!c.isNull(5))b.append(" • qty ").append(c.getDouble(5)).append(' ').append(s(c,6));if(!c.isNull(7))b.append(" • unit price ").append(c.getDouble(7)).append(' ').append(s(c,9));if(!c.isNull(8))b.append(" • total ").append(c.getDouble(8)).append(' ').append(s(c,9));if(!s(c,13).isEmpty())b.append(" • ").append(s(c,13)).append(' ').append(s(c,14));h.snippet=b.toString();h.sheet=s(c,10);h.page=c.getInt(11);h.row=c.getInt(12);h.score=score(q,h.fileName+" "+h.snippet)+.35;out.add(h);}c.close();

        c=db.rawQuery("SELECT u.file_id,f.display_name,f.document_uri,u.reference_type,u.reference_value,u.item_name,u.status,u.status_normalized,u.owner_name,u.due_text,u.remarks,u.vendor_name,u.sheet_name,u.page_number,u.row_number FROM work_followup_records u JOIN work_files f ON f.id=u.file_id WHERE f.active_version_id>0 AND u.version_id=f.active_version_id AND (u.reference_value LIKE ? OR u.item_name LIKE ? OR u.status LIKE ? OR u.status_normalized LIKE ? OR u.owner_name LIKE ? OR u.due_text LIKE ? OR u.remarks LIKE ? OR u.vendor_name LIKE ? OR f.display_name LIKE ?) LIMIT 80",new String[]{like,like,like,like,like,like,like,like,like});
        while(c.moveToNext()){Hit h=new Hit();h.kind="FOLLOW_UP";h.fileId=c.getLong(0);h.fileName=s(c,1);h.documentUri=s(c,2);StringBuilder b=new StringBuilder();if(!s(c,3).isEmpty()||!s(c,4).isEmpty())b.append(s(c,3)).append(' ').append(s(c,4));if(!s(c,5).isEmpty()){if(b.length()>0)b.append(" • ");b.append(s(c,5));}if(!s(c,6).isEmpty())b.append(" • status ").append(s(c,6));if(!s(c,8).isEmpty())b.append(" • owner ").append(s(c,8));if(!s(c,9).isEmpty())b.append(" • due ").append(s(c,9));if(!s(c,11).isEmpty())b.append(" • vendor ").append(s(c,11));if(!s(c,10).isEmpty())b.append(" • ").append(s(c,10));h.snippet=b.toString();h.sheet=s(c,12);h.page=c.getInt(13);h.row=c.getInt(14);h.score=score(q,h.fileName+" "+h.snippet)+.42;out.add(h);}c.close();

        c=db.rawQuery("SELECT x.file_id,f.display_name,f.document_uri,x.fact_type,x.fact_key,x.text_value,x.sheet_name,x.page_number,x.slide_number,x.row_number FROM work_facts x JOIN work_files f ON f.id=x.file_id WHERE f.active_version_id>0 AND x.version_id=f.active_version_id AND (x.fact_key LIKE ? OR x.text_value LIKE ? OR f.display_name LIKE ?) LIMIT 60",new String[]{like,like,like});
        while(c.moveToNext()){Hit h=new Hit();h.kind=s(c,3);h.fileId=c.getLong(0);h.fileName=s(c,1);h.documentUri=s(c,2);h.snippet=(s(c,4).isEmpty()?"":s(c,4)+": ")+s(c,5);h.sheet=s(c,6);h.page=c.getInt(7);h.slide=c.getInt(8);h.row=c.getInt(9);h.score=score(q,h.fileName+" "+h.snippet)+.30;out.add(h);}c.close();

        WorkProcurementLifecycleSearch.append(db,q,out,Math.max(80,limit*3));
        WorkProcurementCaseSearch.append(vault,q,out,Math.max(20,limit));
        WorkPriceComparisonSearch.append(vault,q,out,Math.max(20,limit));

        Collections.sort(out,(a,b)->Double.compare(b.score,a.score));LinkedHashMap<String,Hit> unique=new LinkedHashMap<>();for(Hit h:out){String key=h.fileId+"|"+h.location()+"|"+h.snippet;unique.putIfAbsent(key,h);if(unique.size()>=Math.max(1,limit))break;}return new ArrayList<>(unique.values());
    }

    private static void appendExactProcurementRefs(SQLiteDatabase db,String query,List<Hit> out,int max){
        RefQuery rq=parseReferenceQuery(query);if(rq.value.isEmpty())return;
        String sql="SELECT r.file_id,f.display_name,f.document_uri,r.ref_type,r.ref_value,r.normalized_value,r.confidence "+
                "FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id "+
                "WHERE f.active_version_id>0 AND r.version_id=f.active_version_id AND "+
                (rq.type.isEmpty()?"r.normalized_value=?":"r.ref_type=? AND r.normalized_value=?")+
                " ORDER BY r.confidence DESC,r.id DESC LIMIT ?";
        String[] args=rq.type.isEmpty()?new String[]{rq.value,String.valueOf(max)}:new String[]{rq.type,rq.value,String.valueOf(max)};
        Cursor c=db.rawQuery(sql,args);int count=0;
        while(c.moveToNext()&&count<max){Hit h=new Hit();h.kind="PROCUREMENT_REF";h.fileId=c.getLong(0);h.fileName=s(c,1);h.documentUri=s(c,2);h.snippet=s(c,3)+" "+s(c,4)+" • exact reference match";h.score=2.0+Math.min(.2,c.getDouble(6)*.2);out.add(h);count++;}c.close();
        if(count>0)return;
        String fuzzy="%"+rq.value+"%";
        sql="SELECT r.file_id,f.display_name,f.document_uri,r.ref_type,r.ref_value,r.normalized_value,r.confidence "+
                "FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id "+
                "WHERE f.active_version_id>0 AND r.version_id=f.active_version_id AND "+
                (rq.type.isEmpty()?"r.normalized_value LIKE ?":"r.ref_type=? AND r.normalized_value LIKE ?")+
                " ORDER BY r.confidence DESC,r.id DESC LIMIT ?";
        args=rq.type.isEmpty()?new String[]{fuzzy,String.valueOf(max)}:new String[]{rq.type,fuzzy,String.valueOf(max)};
        c=db.rawQuery(sql,args);
        while(c.moveToNext()&&count<max){Hit h=new Hit();h.kind="PROCUREMENT_REF";h.fileId=c.getLong(0);h.fileName=s(c,1);h.documentUri=s(c,2);h.snippet=s(c,3)+" "+s(c,4)+" • normalized reference match";h.score=1.55+Math.min(.15,c.getDouble(6)*.15);out.add(h);count++;}c.close();
    }

    static RefQuery parseReferenceQuery(String query){
        String q=n(query).toUpperCase(Locale.ROOT);String type="",value="";
        String[] parts=q.split("[^\\p{L}\\p{N}._/-]+");
        for(int i=0;i<parts.length;i++){
            String x=normRef(parts[i]);if(x.isEmpty())continue;
            if(("PR".equals(x)||"PO".equals(x))&&i+1<parts.length){String next=normRef(parts[i+1]);if(hasDigit(next)){type=x;value=next;break;}}
            if(hasDigit(x)&&x.length()>value.length())value=x;
        }
        return new RefQuery(type,value);
    }
    private static String normRef(String x){return n(x).replaceAll("^[#:/-]+|[#:/-]+$","").toUpperCase(Locale.ROOT);}
    private static boolean hasDigit(String x){for(int i=0;i<x.length();i++)if(Character.isDigit(x.charAt(i)))return true;return false;}

    public static String groundedContext(String query,List<Hit> hits,int maxChars){StringBuilder b=new StringBuilder();b.append("WORK VAULT EVIDENCE\nQuery: ").append(query==null?"":query).append("\n\n");int i=1;for(Hit h:hits){String block="["+i+"] "+h.fileName+(h.location().isEmpty()?"":" — "+h.location())+"\n"+h.snippet+"\n\n";if(b.length()+block.length()>maxChars)break;b.append(block);i++;}b.append("Use only the grounded evidence above. CASE_GAP items are derived projections over grounded archive evidence and mean evidence was not found in the indexed archive, not proof the real-world step never happened. PRICE_COMPARISON items compare only normalized matching items with compatible unit and currency and preserve both source files in the text; cross-project comparisons must be described as cross-project. Treat lifecycle links as evidence-backed associations, not stronger facts than their source rule allows. Distinguish facts from inference. Cite evidence numbers and file/location for every concrete source claim. If evidence is insufficient, say so.");return b.toString();}
    private static double score(String query,String text){String q=n(query).toLowerCase(Locale.ROOT),t=n(text).toLowerCase(Locale.ROOT);if(q.isEmpty())return 0;double s=t.contains(q)?.7:0;String[] tokens=q.split("[^\\p{L}\\p{N}]+");int hit=0,total=0;for(String token:tokens){if(token.length()<2)continue;total++;if(t.contains(token))hit++;}if(total>0)s+=.3*((double)hit/total);return Math.min(1,s);}
    private static String clip(String s,int n){String x=n(s);return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static String n(String s){return s==null?"":s.trim();}
    static final class RefQuery{final String type,value;RefQuery(String t,String v){type=t;value=v;}}
    public static final class Hit{public String kind="",fileName="",documentUri="",snippet="",sheet="";public long fileId;public int page,slide,row;public double score;public String location(){ArrayList<String> p=new ArrayList<>();if(!sheet.isEmpty())p.add("Sheet "+sheet);if(page>0)p.add("Page "+page);if(slide>0)p.add("Slide "+slide);if(row>0)p.add("Row "+row);return android.text.TextUtils.join(" • ",p);}}
}
