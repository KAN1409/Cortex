package com.kareem.cortex;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adds derived, explicitly-labelled procurement case projections for missing-step questions. */
public final class WorkProcurementCaseSearch {
    public static final String VERSION="work_procurement_case_search_002";
    private WorkProcurementCaseSearch(){}

    public static void append(VaultDb vault,String query,List<WorkVaultSearch.Hit> out,int max){
        Intent intent=intent(query);if(intent==Intent.NONE||vault==null||out==null)return;
        ArrayList<WorkProcurementCaseEngine.Case> cases=WorkProcurementCaseEngine.load(vault,Math.max(40,max));
        int added=0;
        for(WorkProcurementCaseEngine.Case x:cases){
            if(!matches(intent,x))continue;
            WorkVaultSearch.Hit h=new WorkVaultSearch.Hit();
            h.kind="CASE_GAP";h.fileId=0;h.fileName=(x.project.isEmpty()?"":x.project+" · ")+"PR "+x.pr;h.documentUri="";
            h.snippet="Derived procurement case projection • "+x.headline()+" • stages: quotation "+mark(x.quotation)+", comparison "+mark(x.comparison)+", approval "+mark(x.approval)+", PO "+mark(x.po)+status(x);
            h.score=1.45;out.add(h);if(++added>=max)break;
        }
    }

    static Intent intent(String query){
        String q=query==null?"":query.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
        if(q.isEmpty())return Intent.NONE;
        if(containsAny(q,
                "no po","without po","missing po",
                "مفيش po","مافيش po","بدون po","لسه مفيش po","من غير po","po مش موجود",
                "مفيش لها po","مافيش لها po","لسه مفيش لها po","من غير لها po"))return Intent.MISSING_PO;
        if(containsAny(q,
                "missing approval","no approval","without approval",
                "مفيش اعتماد","بدون اعتماد","اعتماد مش موجود",
                "مفيش لها اعتماد","مافيش لها اعتماد","لسه مفيش لها اعتماد"))return Intent.MISSING_APPROVAL;
        if(containsAny(q,
                "missing comparison","no comparison","without comparison",
                "مفيش مقارنة","بدون مقارنة","مقارنة مش موجودة",
                "مفيش لها مقارنة","مافيش لها مقارنة","لسه مفيش لها مقارنة"))return Intent.MISSING_COMPARISON;
        if(containsAny(q,
                "missing quotation","no quotation","without quotation",
                "مفيش عرض سعر","بدون عرض سعر","عرض سعر مش موجود",
                "مفيش لها عرض سعر","مافيش لها عرض سعر","لسه مفيش لها عرض سعر"))return Intent.MISSING_QUOTATION;
        return Intent.NONE;
    }

    private static boolean matches(Intent i,WorkProcurementCaseEngine.Case x){
        if(i==Intent.MISSING_PO)return !x.po;
        if(i==Intent.MISSING_APPROVAL)return !x.approval&&!x.po;
        if(i==Intent.MISSING_COMPARISON)return !x.comparison&&!x.po;
        if(i==Intent.MISSING_QUOTATION)return !x.quotation&&!x.po;
        return false;
    }
    private static String status(WorkProcurementCaseEngine.Case x){return x.statuses.isEmpty()?"":" • follow-up: "+x.statuses.get(0);}
    private static String mark(boolean x){return x?"found":"not found";}
    private static boolean containsAny(String q,String...xs){for(String x:xs)if(q.contains(x))return true;return false;}
    enum Intent{NONE,MISSING_PO,MISSING_APPROVAL,MISSING_COMPARISON,MISSING_QUOTATION}
}
