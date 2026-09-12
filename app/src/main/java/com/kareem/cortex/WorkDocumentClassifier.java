package com.kareem.cortex;

import java.util.*;

/** Deterministic procurement-document classification. Classification is descriptive metadata, not canonical fact. */
public final class WorkDocumentClassifier {
    public static final String VERSION="work_document_classifier_002";
    private WorkDocumentClassifier(){}

    public static Result classify(String fileName,WorkParsedDocument doc){
        StringBuilder b=new StringBuilder();
        if(doc!=null){int blocks=0;for(WorkParsedDocument.Block x:doc.blocks){if(x==null)continue;if(blocks++>=80)break;b.append(' ').append(n(x.sheetName)).append(' ').append(n(x.text));for(String v:x.cells.values())b.append(' ').append(n(v));}}
        return classifyText(fileName,b.toString());
    }

    public static Result classifyText(String fileName,String body){
        String text=norm(n(fileName)+" "+n(body));
        LinkedHashMap<String,Double> scores=new LinkedHashMap<>();
        score(scores,"FOLLOW_UP",text,1.2,"follow up","followup","متابعة","status","pending action","responsible","expected date");
        score(scores,"COMPARISON",text,1.15,"comparison","comparison sheet","commercial comparison","technical comparison","مقارنة","مقارنة اسعار","مقارنة أسعار","vendor 1","vendor 2","supplier 1","supplier 2");
        score(scores,"QUOTATION",text,1.0,"quotation","quote","offer","commercial offer","عرض سعر","عرض اسعار","عرض أسعار","validity","payment terms");
        score(scores,"PURCHASE_ORDER",text,1.2,"purchase order","po no","p o no","امر شراء","أمر شراء","امر اسناد","أمر إسناد");
        score(scores,"PURCHASE_REQUEST",text,1.15,"purchase request","pr no","p r no","طلب شراء","طلب الشراء");
        score(scores,"APPROVAL",text,1.0,"approval","approved","approval sheet","اعتماد","موافقة","معتمد","owner approval","consultant approval");
        score(scores,"INVOICE",text,.95,"invoice","tax invoice","فاتورة","vat","tax registration");
        score(scores,"DELIVERY",text,.95,"delivery note","delivery receipt","goods received","استلام","اذن استلام","إذن استلام");

        String fn=norm(fileName);
        for(String type:new ArrayList<>(scores.keySet())){
            double extra=filenameBoost(type,fn);
            if(extra>0)scores.put(type,scores.get(type)+extra);
        }

        String best="OTHER";double top=0,second=0;
        for(Map.Entry<String,Double> e:scores.entrySet()){
            double v=e.getValue();if(v>top){second=top;top=v;best=e.getKey();}else if(v>second)second=v;
        }
        double confidence=top<=0?0:Math.min(.99,.45+Math.min(.42,top*.08)+Math.min(.12,Math.max(0,top-second)*.08));
        if(top<1.0){best="OTHER";confidence=.35;}
        return new Result(best,confidence,scores);
    }

    private static void score(Map<String,Double> out,String type,String text,double weight,String...terms){double s=0;for(String t:terms)if(text.contains(norm(t)))s+=weight;out.put(type,s);}
    private static double filenameBoost(String type,String fn){
        if(type.equals("FOLLOW_UP")&&(fn.contains("follow")||fn.contains("متابعة")))return 2.4;
        if(type.equals("COMPARISON")&&(fn.contains("comparison")||fn.contains("مقارنة")))return 2.4;
        if(type.equals("QUOTATION")&&(fn.contains("quotation")||fn.contains("quote")||fn.contains("عرض سعر")))return 2.2;
        if(type.equals("PURCHASE_ORDER")&&(hasToken(fn,"po")||fn.contains("purchase order")||fn.contains("امر شراء")||fn.contains("أمر شراء")||fn.contains("امر اسناد")||fn.contains("أمر إسناد")))return 2.6;
        if(type.equals("PURCHASE_REQUEST")&&(hasToken(fn,"pr")||fn.contains("purchase request")||fn.contains("طلب شراء")))return 2.5;
        if(type.equals("APPROVAL")&&(fn.contains("approval")||fn.contains("اعتماد")||fn.contains("موافقة")))return 2.2;
        if(type.equals("INVOICE")&&(fn.contains("invoice")||fn.contains("فاتورة")))return 2.2;
        if(type.equals("DELIVERY")&&(fn.contains("delivery")||fn.contains("استلام")))return 2.0;
        return 0;
    }
    private static boolean hasToken(String text,String token){for(String p:text.split("[^\\p{L}\\p{N}]+"))if(p.equals(token))return true;return false;}
    private static String norm(String s){return n(s).toLowerCase(Locale.ROOT).replaceAll("[_\\-]+"," ").replaceAll("\\s+"," ").trim();}
    private static String n(String s){return s==null?"":s.trim();}

    public static final class Result{
        public final String type;public final double confidence;public final Map<String,Double> scores;
        Result(String type,double confidence,Map<String,Double> scores){this.type=type;this.confidence=confidence;this.scores=Collections.unmodifiableMap(new LinkedHashMap<>(scores));}
    }
}
