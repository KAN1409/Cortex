package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Read-only price analytics over grounded Work Vault price records.
 * It never converts units or currencies and never writes canonical facts.
 */
public final class WorkPriceAnalyticsEngine {
    public static final String VERSION="work_price_analytics_engine_001";
    private WorkPriceAnalyticsEngine(){}

    public static ArrayList<VendorQuote> vendorComparison(VaultDb vault,String query,int limit){
        ArrayList<VendorQuote> out=new ArrayList<>();
        if(vault==null)return out;
        SQLiteDatabase db=vault.getReadableDatabase();WorkVaultIndexSchema.ensure(db);
        String needle=WorkPriceComparisonEngine.item(query);
        LinkedHashMap<String,VendorQuote> best=new LinkedHashMap<>();
        Cursor c=db.rawQuery(
                "SELECT p.id,p.file_id,p.project_id,p.item_name,p.vendor_name,p.unit,p.unit_price,p.currency,"+
                "p.reference_type,p.reference_value,f.display_name,f.document_uri,f.modified_at,p.created_at,"+
                "p.sheet_name,p.page_number,p.row_number,COALESCE(pr.canonical_name,'') "+
                "FROM work_price_records p JOIN work_files f ON f.id=p.file_id LEFT JOIN work_projects pr ON pr.id=p.project_id "+
                "WHERE p.unit_price IS NOT NULL AND p.unit_price>0 AND TRIM(p.item_name)<>'' AND TRIM(COALESCE(p.vendor_name,''))<>'' "+
                "ORDER BY CASE WHEN f.modified_at>0 THEN f.modified_at ELSE p.created_at END DESC,p.id DESC LIMIT 1800",null);
        while(c.moveToNext()){
            WorkPriceComparisonEngine.Price p=read(c);
            String itemKey=WorkPriceComparisonEngine.item(p.item);
            if(!needle.isEmpty()&&!itemKey.contains(needle)&&!needle.contains(itemKey))continue;
            String unit=WorkPriceComparisonEngine.unit(p.unit),currency=WorkPriceComparisonEngine.currency(p.currency);
            if(itemKey.isEmpty()||unit.isEmpty()||currency.isEmpty())continue;
            String vendor=norm(p.vendor);if(vendor.isEmpty())continue;
            String key=itemKey+"|"+unit+"|"+currency+"|"+vendor;
            if(!best.containsKey(key))best.put(key,new VendorQuote(p,itemKey,unit,currency));
        }c.close();
        out.addAll(best.values());
        out.sort(Comparator.comparingDouble(a->a.price.unitPrice));
        int cap=Math.max(1,limit);if(out.size()>cap)return new ArrayList<>(out.subList(0,cap));return out;
    }

    public static ArrayList<Trend> trends(VaultDb vault,int limit){
        ArrayList<Trend> out=new ArrayList<>();
        if(vault==null)return out;
        SQLiteDatabase db=vault.getReadableDatabase();WorkVaultIndexSchema.ensure(db);
        LinkedHashMap<String,ArrayList<WorkPriceComparisonEngine.Price>> groups=new LinkedHashMap<>();
        Cursor c=db.rawQuery(
                "SELECT p.id,p.file_id,p.project_id,p.item_name,p.vendor_name,p.unit,p.unit_price,p.currency,"+
                "p.reference_type,p.reference_value,f.display_name,f.document_uri,f.modified_at,p.created_at,"+
                "p.sheet_name,p.page_number,p.row_number,COALESCE(pr.canonical_name,'') "+
                "FROM work_price_records p JOIN work_files f ON f.id=p.file_id LEFT JOIN work_projects pr ON pr.id=p.project_id "+
                "WHERE p.unit_price IS NOT NULL AND p.unit_price>0 AND TRIM(p.item_name)<>'' "+
                "ORDER BY CASE WHEN f.modified_at>0 THEN f.modified_at ELSE p.created_at END ASC,p.id ASC LIMIT 2400",null);
        while(c.moveToNext()){
            WorkPriceComparisonEngine.Price p=read(c);String item=WorkPriceComparisonEngine.item(p.item),unit=WorkPriceComparisonEngine.unit(p.unit),currency=WorkPriceComparisonEngine.currency(p.currency);
            if(item.isEmpty()||unit.isEmpty()||currency.isEmpty())continue;
            String key=item+"|"+unit+"|"+currency;
            ArrayList<WorkPriceComparisonEngine.Price> list=groups.get(key);if(list==null){list=new ArrayList<>();groups.put(key,list);}list.add(p);
        }c.close();
        for(ArrayList<WorkPriceComparisonEngine.Price> list:groups.values()){
            ArrayList<WorkPriceComparisonEngine.Price> distinct=distinctFiles(list);if(distinct.size()<2)continue;
            WorkPriceComparisonEngine.Price first=distinct.get(0),last=distinct.get(distinct.size()-1);
            if(first.unitPrice<=0)continue;
            double pct=((last.unitPrice-first.unitPrice)/first.unitPrice)*100d;
            out.add(new Trend(first,last,distinct.size(),pct));
        }
        out.sort((a,b)->Long.compare(b.latest.sourceTime,a.latest.sourceTime));
        int cap=Math.max(1,limit);if(out.size()>cap)return new ArrayList<>(out.subList(0,cap));return out;
    }

    public static ArrayList<Anomaly> anomalies(VaultDb vault,int limit){
        ArrayList<Anomaly> out=new ArrayList<>();
        if(vault==null)return out;
        SQLiteDatabase db=vault.getReadableDatabase();WorkVaultIndexSchema.ensure(db);
        LinkedHashMap<String,ArrayList<WorkPriceComparisonEngine.Price>> groups=new LinkedHashMap<>();
        Cursor c=db.rawQuery(
                "SELECT p.id,p.file_id,p.project_id,p.item_name,p.vendor_name,p.unit,p.unit_price,p.currency,"+
                "p.reference_type,p.reference_value,f.display_name,f.document_uri,f.modified_at,p.created_at,"+
                "p.sheet_name,p.page_number,p.row_number,COALESCE(pr.canonical_name,'') "+
                "FROM work_price_records p JOIN work_files f ON f.id=p.file_id LEFT JOIN work_projects pr ON pr.id=p.project_id "+
                "WHERE p.unit_price IS NOT NULL AND p.unit_price>0 AND TRIM(p.item_name)<>'' "+
                "ORDER BY CASE WHEN f.modified_at>0 THEN f.modified_at ELSE p.created_at END DESC,p.id DESC LIMIT 2400",null);
        while(c.moveToNext()){
            WorkPriceComparisonEngine.Price p=read(c);String item=WorkPriceComparisonEngine.item(p.item),unit=WorkPriceComparisonEngine.unit(p.unit),currency=WorkPriceComparisonEngine.currency(p.currency);
            if(item.isEmpty()||unit.isEmpty()||currency.isEmpty())continue;
            String key=item+"|"+unit+"|"+currency;
            ArrayList<WorkPriceComparisonEngine.Price> list=groups.get(key);if(list==null){list=new ArrayList<>();groups.put(key,list);}list.add(p);
        }c.close();
        for(ArrayList<WorkPriceComparisonEngine.Price> list:groups.values()){
            ArrayList<WorkPriceComparisonEngine.Price> distinct=distinctFiles(list);if(distinct.size()<4)continue;
            WorkPriceComparisonEngine.Price current=distinct.get(0);
            ArrayList<Double> history=new ArrayList<>();for(int i=1;i<distinct.size();i++)history.add(distinct.get(i).unitPrice);
            double med=median(history);if(med<=0)continue;double pct=((current.unitPrice-med)/med)*100d;
            if(Math.abs(pct)>=20d)out.add(new Anomaly(current,med,pct,distinct.size()-1));
        }
        out.sort((a,b)->Double.compare(Math.abs(b.percentFromMedian),Math.abs(a.percentFromMedian)));
        int cap=Math.max(1,limit);if(out.size()>cap)return new ArrayList<>(out.subList(0,cap));return out;
    }

    static double median(List<Double> values){if(values==null||values.isEmpty())return 0;ArrayList<Double> x=new ArrayList<>(values);Collections.sort(x);int n=x.size();return n%2==1?x.get(n/2):(x.get(n/2-1)+x.get(n/2))/2d;}
    private static ArrayList<WorkPriceComparisonEngine.Price> distinctFiles(List<WorkPriceComparisonEngine.Price> in){ArrayList<WorkPriceComparisonEngine.Price> out=new ArrayList<>();long last=-1;for(WorkPriceComparisonEngine.Price p:in){boolean seen=false;for(WorkPriceComparisonEngine.Price x:out)if(x.fileId==p.fileId){seen=true;break;}if(!seen)out.add(p);}return out;}
    private static String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static WorkPriceComparisonEngine.Price read(Cursor c){WorkPriceComparisonEngine.Price p=new WorkPriceComparisonEngine.Price();p.id=c.getLong(0);p.fileId=c.getLong(1);p.projectId=c.getLong(2);p.item=s(c,3);p.vendor=s(c,4);p.unit=s(c,5);p.unitPrice=c.getDouble(6);p.currency=s(c,7);p.referenceType=s(c,8);p.referenceValue=s(c,9);p.fileName=s(c,10);p.documentUri=s(c,11);long modified=c.getLong(12),created=c.getLong(13);p.sourceTime=modified>0?modified:created;p.sheet=s(c,14);p.page=c.getInt(15);p.row=c.getInt(16);p.project=s(c,17);return p;}

    public static final class VendorQuote{public final WorkPriceComparisonEngine.Price price;public final String itemKey,unit,currency;VendorQuote(WorkPriceComparisonEngine.Price p,String i,String u,String c){price=p;itemKey=i;unit=u;currency=c;}}
    public static final class Trend{public final WorkPriceComparisonEngine.Price earliest,latest;public final int samples;public final double percent;Trend(WorkPriceComparisonEngine.Price e,WorkPriceComparisonEngine.Price l,int s,double p){earliest=e;latest=l;samples=s;percent=p;}public String direction(){if(Math.abs(percent)<.01)return "UNCHANGED";return percent>0?"INCREASE":"DECREASE";}}
    public static final class Anomaly{public final WorkPriceComparisonEngine.Price current;public final double historicalMedian,percentFromMedian;public final int historicalSamples;Anomaly(WorkPriceComparisonEngine.Price c,double m,double p,int n){current=c;historicalMedian=m;percentFromMedian=p;historicalSamples=n;}public String direction(){return percentFromMedian>=0?"ABOVE_HISTORY":"BELOW_HISTORY";}}
}
