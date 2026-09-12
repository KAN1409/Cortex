package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Locale;

/** Adds grounded price-comparison projections to Work Vault search for explicit price/comparison queries. */
public final class WorkPriceComparisonSearch {
    public static final String VERSION="work_price_comparison_search_001";
    private WorkPriceComparisonSearch(){}

    static boolean wantsComparison(String query){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        return q.contains("price")||q.contains("prices")||q.contains("compare")||q.contains("comparison")||
                q.contains("increase")||q.contains("decrease")||q.contains("higher")||q.contains("lower")||
                q.contains("سعر")||q.contains("أسعار")||q.contains("اسعار")||q.contains("قارن")||q.contains("مقارنة")||
                q.contains("زيادة")||q.contains("ارتفاع")||q.contains("انخفاض")||q.contains("أعلى")||q.contains("اعلى")||q.contains("أقل")||q.contains("اقل");
    }

    public static void append(VaultDb vault,String query,ArrayList<WorkVaultSearch.Hit> out,int limit){
        if(vault==null||out==null||!wantsComparison(query))return;
        String q=norm(query);int added=0;
        for(WorkPriceComparisonEngine.Comparison x:WorkPriceComparisonEngine.recent(vault,Math.max(20,limit))){
            if(!x.comparable||x.current==null||x.previous==null)continue;
            String hay=norm(x.current.item+" "+x.current.vendor+" "+x.previous.vendor+" "+x.current.project+" "+x.previous.project+" "+x.current.referenceValue+" "+x.previous.referenceValue);
            if(!matches(q,hay))continue;
            WorkVaultSearch.Hit h=new WorkVaultSearch.Hit();h.kind="PRICE_COMPARISON";h.fileId=x.current.fileId;h.fileName=x.current.fileName;h.documentUri=x.current.documentUri;h.sheet=x.current.sheet;h.page=x.current.page;h.row=x.current.row;
            String currency=WorkPriceComparisonEngine.currency(x.current.currency);String unit=WorkPriceComparisonEngine.unit(x.current.unit);
            StringBuilder b=new StringBuilder();b.append(x.current.item).append(" • ").append(x.previous.unitPrice).append(' ').append(currency).append('/').append(unit).append(" → ").append(x.current.unitPrice).append(' ').append(currency).append('/').append(unit);
            b.append(" • ").append(x.direction()).append(' ').append(String.format(Locale.ROOT,"%+.1f%%",x.percent));
            if(!x.previous.fileName.isEmpty())b.append(" • previous source: ").append(x.previous.fileName);
            if(x.crossProject())b.append(" • cross-project: ").append(x.previous.project).append(" → ").append(x.current.project);
            else if(!x.current.project.isEmpty())b.append(" • project: ").append(x.current.project);
            if(!x.current.vendor.isEmpty()||!x.previous.vendor.isEmpty())b.append(" • vendor: ").append(x.previous.vendor).append(" → ").append(x.current.vendor);
            h.snippet=b.toString();h.score=.98;out.add(h);if(++added>=Math.max(1,limit))break;
        }
    }

    private static boolean matches(String query,String hay){
        if(query.isEmpty())return true;String[] tokens=query.split("[^\\p{L}\\p{N}]+");int meaningful=0,hit=0;for(String t:tokens){if(t.length()<3||isIntentWord(t))continue;meaningful++;if(hay.contains(t))hit++;}return meaningful==0||hit>0;
    }
    private static boolean isIntentWord(String t){return t.equals("price")||t.equals("prices")||t.equals("compare")||t.equals("comparison")||t.equals("سعر")||t.equals("أسعار")||t.equals("اسعار")||t.equals("قارن")||t.equals("مقارنة");}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).trim();}
}
