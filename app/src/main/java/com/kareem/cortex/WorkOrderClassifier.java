package com.kareem.cortex;

import java.util.Locale;

/** Conservative order taxonomy for the three real procurement order types. */
public final class WorkOrderClassifier {
    public static final String VERSION="work_order_classifier_001";
    private WorkOrderClassifier(){}

    public static final class Result {
        public final String family;
        public final String scope;
        public final double confidence;
        public final String reason;
        Result(String family,String scope,double confidence,String reason){this.family=family;this.scope=scope;this.confidence=confidence;this.reason=reason;}
        public boolean known(){return !"UNKNOWN".equals(family)&&!"UNKNOWN".equals(scope);}
    }

    public static Result classify(String fileName,String body){
        String text=norm((fileName==null?"":fileName)+" "+(body==null?"":body));
        boolean assignment=containsAny(text,"أمر إسناد","امر اسناد","اسناد","إسناد","assignment order","work order");
        boolean supplyOrder=containsAny(text,"أمر توريد","امر توريد","supply order");
        boolean manufacturing=containsAny(text,"مصنعات","تصنيع","manufacturing","fabrication","manufacture");
        boolean supply=containsAny(text,"توريد","supply","supply of","supplying");

        if(supplyOrder){
            if(assignment)return unknown("conflicting order-family evidence");
            return new Result("SUPPLY_ORDER","SUPPLY_ONLY",supply?.98:.91,"explicit supply order");
        }
        if(assignment){
            if(manufacturing&&supply)return new Result("ASSIGNMENT_ORDER","MANUFACTURING_AND_SUPPLY",.98,"assignment order with manufacturing and supply evidence");
            if(manufacturing&&!supply)return new Result("ASSIGNMENT_ORDER","MANUFACTURING_ONLY",.95,"assignment order with manufacturing-only evidence");
            return new Result("ASSIGNMENT_ORDER","UNKNOWN",.72,"assignment order found but scope is not grounded");
        }
        return unknown("no explicit grounded order type");
    }

    private static Result unknown(String reason){return new Result("UNKNOWN","UNKNOWN",.0,reason);}
    private static boolean containsAny(String s,String...terms){for(String t:terms)if(s.contains(norm(t)))return true;return false;}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replaceAll("[_\\-]+"," ").replaceAll("\\s+"," ").trim();}
}