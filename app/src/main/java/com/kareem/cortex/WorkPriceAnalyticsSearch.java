package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Locale;

/** Adds grounded vendor/trend/anomaly projections to Work Vault search. */
public final class WorkPriceAnalyticsSearch {
    public static final String VERSION="work_price_analytics_search_001";
    private WorkPriceAnalyticsSearch(){}

    public static void append(VaultDb vault,String query,ArrayList<WorkVaultSearch.Hit> out,int limit){
        if(vault==null||out==null)return;String q=norm(query);int cap=Math.max(1,limit);
        if(wantsVendor(q))appendVendors(vault,q,out,cap);
        if(wantsTrend(q))appendTrends(vault,q,out,cap);
        if(wantsAnomaly(q))appendAnomalies(vault,q,out,cap);
    }

    static boolean wantsVendor(String q){return q.contains("vendor")||q.contains("supplier")||q.contains("cheapest")||q.contains("lowest")||q.contains("مورد")||q.contains("موردين")||q.contains("أرخص")||q.contains("ارخص")||q.contains("أقل سعر")||q.contains("اقل سعر");}
    static boolean wantsTrend(String q){return q.contains("trend")||q.contains("history")||q.contains("historical")||q.contains("over time")||q.contains("اتجاه")||q.contains("تاريخ السعر")||q.contains("مع الوقت")||q.contains("تطور السعر");}
    static boolean wantsAnomaly(String q){return q.contains("anomaly")||q.contains("unusual")||q.contains("abnormal")||q.contains("outlier")||q.contains("شاذ")||q.contains("غريب")||q.contains("غير طبيعي")||q.contains("مرتفع بشكل")||q.contains("منخفض بشكل");}

    private static void appendVendors(VaultDb vault,String q,ArrayList<WorkVaultSearch.Hit> out,int limit){
        int n=0;for(WorkPriceAnalyticsEngine.VendorQuote x:WorkPriceAnalyticsEngine.vendorComparison(vault,extractSubject(q),Math.max(20,limit))){
            WorkPriceComparisonEngine.Price p=x.price;WorkVaultSearch.Hit h=new WorkVaultSearch.Hit();h.kind="VENDOR_PRICE";h.fileId=p.fileId;h.fileName=p.fileName;h.documentUri=p.documentUri;h.sheet=p.sheet;h.page=p.page;h.row=p.row;
            h.snippet=p.item+" • vendor "+p.vendor+" • "+p.unitPrice+" "+x.currency+"/"+x.unit+(p.project.isEmpty()?"":" • project: "+p.project);h.score=.97;out.add(h);if(++n>=limit)break;
        }
    }

    private static void appendTrends(VaultDb vault,String q,ArrayList<WorkVaultSearch.Hit> out,int limit){
        int n=0;String subject=extractSubject(q);for(WorkPriceAnalyticsEngine.Trend x:WorkPriceAnalyticsEngine.trends(vault,Math.max(20,limit))){
            if(!subject.isEmpty()&&!WorkPriceComparisonEngine.item(x.latest.item).contains(subject))continue;WorkVaultSearch.Hit h=new WorkVaultSearch.Hit();h.kind="PRICE_TREND";h.fileId=x.latest.fileId;h.fileName=x.latest.fileName;h.documentUri=x.latest.documentUri;h.sheet=x.latest.sheet;h.page=x.latest.page;h.row=x.latest.row;
            h.snippet=x.latest.item+" • "+x.earliest.unitPrice+" → "+x.latest.unitPrice+" "+WorkPriceComparisonEngine.currency(x.latest.currency)+"/"+WorkPriceComparisonEngine.unit(x.latest.unit)+" • "+x.direction()+" "+String.format(Locale.ROOT,"%+.1f%%",x.percent)+" • "+x.samples+" source samples";h.score=.965;out.add(h);if(++n>=limit)break;
        }
    }

    private static void appendAnomalies(VaultDb vault,String q,ArrayList<WorkVaultSearch.Hit> out,int limit){
        int n=0;String subject=extractSubject(q);for(WorkPriceAnalyticsEngine.Anomaly x:WorkPriceAnalyticsEngine.anomalies(vault,Math.max(20,limit))){
            if(!subject.isEmpty()&&!WorkPriceComparisonEngine.item(x.current.item).contains(subject))continue;WorkVaultSearch.Hit h=new WorkVaultSearch.Hit();h.kind="PRICE_ANOMALY";h.fileId=x.current.fileId;h.fileName=x.current.fileName;h.documentUri=x.current.documentUri;h.sheet=x.current.sheet;h.page=x.current.page;h.row=x.current.row;
            h.snippet=x.current.item+" • current "+x.current.unitPrice+" vs historical median "+String.format(Locale.ROOT,"%.2f",x.historicalMedian)+" "+WorkPriceComparisonEngine.currency(x.current.currency)+"/"+WorkPriceComparisonEngine.unit(x.current.unit)+" • "+x.direction()+" "+String.format(Locale.ROOT,"%+.1f%%",x.percentFromMedian)+" • based on "+x.historicalSamples+" prior sources";h.score=.99;out.add(h);if(++n>=limit)break;
        }
    }

    private static String extractSubject(String q){String x=q.replaceAll("(?i)vendor|supplier|cheapest|lowest|trend|history|historical|over time|anomaly|unusual|abnormal|outlier|compare|comparison|price|prices", " ");x=x.replace("موردين"," ").replace("مورد"," ").replace("أرخص"," ").replace("ارخص"," ").replace("أقل سعر"," ").replace("اقل سعر"," ").replace("اتجاه"," ").replace("تاريخ السعر"," ").replace("مع الوقت"," ").replace("تطور السعر"," ").replace("شاذ"," ").replace("غريب"," ").replace("غير طبيعي"," ").replace("سعر"," ").replace("أسعار"," ").replace("اسعار"," ").trim();return WorkPriceComparisonEngine.item(x);}
    private static String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
}
