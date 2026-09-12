package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Read-only price comparison over grounded active Work Vault price records.
 * Comparisons are allowed only for the same normalized item, compatible unit and currency.
 * Source file modified time is used for chronology so re-indexing never makes an old price look new.
 */
public final class WorkPriceComparisonEngine {
    public static final String VERSION="work_price_comparison_engine_002";
    private WorkPriceComparisonEngine(){}

    public static ArrayList<Comparison> recent(VaultDb vault,int limit){
        ArrayList<Comparison> out=new ArrayList<>();
        if(vault==null)return out;
        SQLiteDatabase db=vault.getReadableDatabase();WorkVaultIndexSchema.ensure(db);
        LinkedHashMap<String,Price> latest=new LinkedHashMap<>();
        LinkedHashMap<String,Price> previous=new LinkedHashMap<>();
        Cursor c=db.rawQuery(
                "SELECT p.id,p.file_id,p.project_id,p.item_name,p.vendor_name,p.unit,p.unit_price,p.currency,p.reference_type,p.reference_value,"+
                "f.display_name,f.document_uri,f.modified_at,p.created_at,p.sheet_name,p.page_number,p.row_number,COALESCE(pr.canonical_name,'') "+
                "FROM work_price_records p JOIN work_files f ON f.id=p.file_id LEFT JOIN work_projects pr ON pr.id=p.project_id "+
                "WHERE f.active_version_id>0 AND p.version_id=f.active_version_id AND p.unit_price IS NOT NULL AND p.unit_price>0 AND TRIM(p.item_name)<>'' "+
                "ORDER BY CASE WHEN f.modified_at>0 THEN f.modified_at ELSE p.created_at END DESC,p.id DESC LIMIT 1200",null);
        while(c.moveToNext()){
            Price p=read(c);String key=key(p);
            if(key.isEmpty())continue;
            Price first=latest.get(key);
            if(first==null){latest.put(key,p);continue;}
            if(previous.containsKey(key)||first.fileId==p.fileId)continue;
            previous.put(key,p);
        }c.close();
        for(String key:latest.keySet()){
            Price a=latest.get(key),b=previous.get(key);if(a==null||b==null)continue;
            Comparison x=compare(a,b);if(x.comparable)out.add(x);
        }
        out.sort((a,b)->Long.compare(b.current.sourceTime,a.current.sourceTime));
        int cap=Math.max(1,limit);if(out.size()>cap)return new ArrayList<>(out.subList(0,cap));return out;
    }

    static Comparison compare(Price current,Price previous){
        if(current==null||previous==null)return Comparison.notComparable(current,previous,"MISSING_PRICE_RECORD");
        if(!item(current.item).equals(item(previous.item)))return Comparison.notComparable(current,previous,"ITEM_MISMATCH");
        String cu=unit(current.unit),pu=unit(previous.unit);if(cu.isEmpty()||pu.isEmpty()||!cu.equals(pu))return Comparison.notComparable(current,previous,"UNIT_MISMATCH");
        String cc=currency(current.currency),pc=currency(previous.currency);if(cc.isEmpty()||pc.isEmpty()||!cc.equals(pc))return Comparison.notComparable(current,previous,"CURRENCY_MISMATCH");
        if(current.unitPrice<=0||previous.unitPrice<=0)return Comparison.notComparable(current,previous,"INVALID_PRICE");
        double delta=current.unitPrice-previous.unitPrice;double pct=(delta/previous.unitPrice)*100d;
        return new Comparison(current,previous,true,"COMPARABLE",delta,pct);
    }

    static String item(String s){return norm(s).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    static String unit(String s){String x=norm(s).replace("²","2").replace("³","3").replaceAll("[ ._-]+","");if(x.equals("sqm")||x.equals("sqmeter")||x.equals("squaremeter")||x.equals("m2"))return "m2";if(x.equals("lm")||x.equals("linm")||x.equals("linearmeter")||x.equals("m"))return x.equals("m")?"m":"lm";if(x.equals("no")||x.equals("nos")||x.equals("nr")||x.equals("number")||x.equals("pcs")||x.equals("pc")||x.equals("piece")||x.equals("pieces"))return "pcs";return x;}
    static String currency(String s){String x=norm(s).replaceAll("[ ._-]+","");if(x.equals("egp")||x.equals("le")||x.equals("جنيه")||x.equals("جنيهمصري"))return "EGP";if(x.equals("usd")||x.equals("$")||x.equals("dollar")||x.equals("dollars"))return "USD";if(x.equals("eur")||x.equals("€")||x.equals("euro"))return "EUR";return x.toUpperCase(Locale.ROOT);}

    private static String key(Price p){String i=item(p.item),u=unit(p.unit),c=currency(p.currency);if(i.isEmpty()||u.isEmpty()||c.isEmpty())return "";return i+"|"+u+"|"+c;}
    private static String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static Price read(Cursor c){Price p=new Price();p.id=c.getLong(0);p.fileId=c.getLong(1);p.projectId=c.getLong(2);p.item=s(c,3);p.vendor=s(c,4);p.unit=s(c,5);p.unitPrice=c.getDouble(6);p.currency=s(c,7);p.referenceType=s(c,8);p.referenceValue=s(c,9);p.fileName=s(c,10);p.documentUri=s(c,11);long modified=c.getLong(12),created=c.getLong(13);p.sourceTime=modified>0?modified:created;p.sheet=s(c,14);p.page=c.getInt(15);p.row=c.getInt(16);p.project=s(c,17);return p;}

    public static final class Price{
        public long id,fileId,projectId,sourceTime;public String item="",vendor="",unit="",currency="",referenceType="",referenceValue="",fileName="",documentUri="",sheet="",project="";public double unitPrice;public int page,row;
        static Price of(String item,String unit,double value,String currency){Price p=new Price();p.item=item;p.unit=unit;p.unitPrice=value;p.currency=currency;return p;}
    }
    public static final class Comparison{
        public final Price current,previous;public final boolean comparable;public final String reason;public final double delta,percent;
        Comparison(Price current,Price previous,boolean comparable,String reason,double delta,double percent){this.current=current;this.previous=previous;this.comparable=comparable;this.reason=reason;this.delta=delta;this.percent=percent;}
        static Comparison notComparable(Price a,Price b,String reason){return new Comparison(a,b,false,reason,0,0);}
        public String direction(){if(!comparable)return "NOT_COMPARABLE";if(Math.abs(percent)<.01)return "UNCHANGED";return percent>0?"INCREASE":"DECREASE";}
        public boolean crossProject(){return current!=null&&previous!=null&&!current.project.isEmpty()&&!previous.project.isEmpty()&&!current.project.equalsIgnoreCase(previous.project);}
    }
}
