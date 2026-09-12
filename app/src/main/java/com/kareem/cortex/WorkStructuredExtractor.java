package com.kareem.cortex;

import java.util.*;
import java.util.regex.*;

/** Deterministic first-pass extraction for construction/procurement archives. */
public final class WorkStructuredExtractor {
    public static final String VERSION="work_structured_extractor_001";
    private static final Pattern PR=Pattern.compile("(?i)(?:\\bPR\\b|P\\.?R\\.?|طلب شراء|طلب الشراء)\\s*[-:#/]?\\s*([A-Z0-9][A-Z0-9._/-]{1,24})");
    private static final Pattern PO=Pattern.compile("(?i)(?:\\bPO\\b|P\\.?O\\.?|أمر إسناد|امر اسناد|أمر شراء|امر شراء)\\s*[-:#/]?\\s*([A-Z0-9][A-Z0-9._/-]{1,24})");
    private static final Pattern PROJECT=Pattern.compile("(?i)(?:project|المشروع)\\s*[:\\-]\\s*([^|\\n\\r]{2,80})");

    private WorkStructuredExtractor(){}

    public static Result extract(WorkParsedDocument doc){
        Result r=new Result();if(doc==null)return r;
        HashMap<String,Header> headers=new HashMap<>();
        for(WorkParsedDocument.Block b:doc.blocks){
            if(b==null)continue;String text=n(b.text);
            refs(text,b,r);project(text,b,r);
            if(!b.cells.isEmpty()){
                String key=n(b.sheetName).toLowerCase(Locale.ROOT);
                Header h=Header.detect(b.cells);
                if(h.score>=2){headers.put(key,h);continue;}
                Header active=headers.get(key);
                if(active!=null){Price p=active.price(b);if(p!=null)r.prices.add(p);}
            }
        }
        return r;
    }

    private static void refs(String text,WorkParsedDocument.Block b,Result r){
        Matcher m=PR.matcher(text);while(m.find())r.refs.add(new Ref("PR",cleanRef(m.group(1)),b));
        m=PO.matcher(text);while(m.find())r.refs.add(new Ref("PO",cleanRef(m.group(1)),b));
    }

    private static void project(String text,WorkParsedDocument.Block b,Result r){
        Matcher m=PROJECT.matcher(text);while(m.find()){
            String name=n(m.group(1)).replaceAll("\\s{2,}"," ");
            if(name.length()>1&&!looksHeader(name))r.projects.add(new Project(name,b));
        }
    }

    private static boolean looksHeader(String s){String x=s.toLowerCase(Locale.ROOT);return x.equals("name")||x.equals("اسم المشروع")||x.length()>80;}
    private static String cleanRef(String s){return n(s).replaceAll("^[#:/-]+|[#:/-]+$","").toUpperCase(Locale.ROOT);}

    public static final class Result{
        public final ArrayList<Ref> refs=new ArrayList<>();
        public final ArrayList<Project> projects=new ArrayList<>();
        public final ArrayList<Price> prices=new ArrayList<>();
    }
    public static final class Ref{public final String type,value;public final WorkParsedDocument.Block source;Ref(String t,String v,WorkParsedDocument.Block s){type=t;value=v;source=s;}}
    public static final class Project{public final String name;public final WorkParsedDocument.Block source;Project(String n,WorkParsedDocument.Block s){name=n;source=s;}}
    public static final class Price{
        public String item="",vendor="",unit="",currency="";public Double quantity,unitPrice,totalPrice;public WorkParsedDocument.Block source;
    }

    private static final class Header{
        String item,vendor,qty,unit,unitPrice,total,currency;int score;
        static Header detect(Map<String,String> cells){
            Header h=new Header();
            for(Map.Entry<String,String> e:cells.entrySet()){
                String x=normHeader(e.getValue()),col=e.getKey();
                if(any(x,"item","description","item description","scope","البند","الوصف","البيان")){h.item=col;h.score++;}
                else if(any(x,"vendor","supplier","contractor","المورد","المقاول")){h.vendor=col;h.score++;}
                else if(any(x,"qty","quantity","الكمية","كمية")){h.qty=col;h.score++;}
                else if(any(x,"unit","uom","الوحدة","وحدة")){h.unit=col;h.score++;}
                else if(any(x,"unit price","unit rate","rate","price/unit","سعر الوحدة","سعر وحده","السعر")){h.unitPrice=col;h.score++;}
                else if(any(x,"total","amount","total price","value","الإجمالي","الاجمالي","اجمالي","القيمة")){h.total=col;h.score++;}
                else if(any(x,"currency","curr","العملة")){h.currency=col;h.score++;}
            }
            return h;
        }
        Price price(WorkParsedDocument.Block b){
            String item=v(b.cells,this.item),vendor=v(b.cells,this.vendor),unit=v(b.cells,this.unit),currency=v(b.cells,this.currency);
            Double qty=num(v(b.cells,this.qty)),unitPrice=num(v(b.cells,this.unitPrice)),total=num(v(b.cells,this.total));
            if(item.isEmpty())return null;
            if(qty==null&&unitPrice==null&&total==null)return null;
            Price p=new Price();p.item=item;p.vendor=vendor;p.unit=unit;p.currency=currency;p.quantity=qty;p.unitPrice=unitPrice;p.totalPrice=total;p.source=b;
            if(p.currency.isEmpty()){String joined=(v(b.cells,this.unitPrice)+" "+v(b.cells,this.total)).toUpperCase(Locale.ROOT);if(joined.contains("EGP")||joined.contains("LE")||joined.contains("جنيه"))p.currency="EGP";else if(joined.contains("USD")||joined.contains("$"))p.currency="USD";}
            return p;
        }
    }

    private static String v(Map<String,String> m,String k){if(k==null)return "";String v=m.get(k);return n(v);}
    private static Double num(String s){if(s==null||s.trim().isEmpty())return null;String x=s.replaceAll("(?i)EGP|USD|AED|SAR|LE|جنيه|ر.س|دولار","").replace(",","").replace(" ","");Matcher m=Pattern.compile("[-+]?\\d+(?:\\.\\d+)?").matcher(x);if(!m.find())return null;try{return Double.parseDouble(m.group());}catch(Exception e){return null;}}
    private static boolean any(String x,String...vs){for(String v:vs)if(x.equals(v)||x.contains(v))return true;return false;}
    private static String normHeader(String s){return n(s).toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s+"," ");}
    private static String n(String s){return s==null?"":s.trim();}
}
