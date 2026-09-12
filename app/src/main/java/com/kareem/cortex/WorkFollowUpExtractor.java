package com.kareem.cortex;

import java.util.*;

/** Deterministic extraction of project/procurement follow-up rows. It preserves source location and raw status. */
public final class WorkFollowUpExtractor {
    public static final String VERSION="work_followup_extractor_001";
    private WorkFollowUpExtractor(){}

    public static Result extract(WorkParsedDocument doc){
        Result out=new Result();if(doc==null)return out;
        HashMap<String,Header> headers=new HashMap<>();
        for(WorkParsedDocument.Block b:doc.blocks){
            if(b==null||b.cells.isEmpty())continue;
            String scope=safe(b.sheetName).toLowerCase(Locale.ROOT);
            Header candidate=Header.detect(b.cells);
            if(candidate.score>=2&&(candidate.status!=null||candidate.reference!=null||candidate.item!=null)){
                headers.put(scope,candidate);continue;
            }
            Header active=headers.get(scope);if(active==null)continue;
            Record r=active.record(b);if(r!=null)out.records.add(r);
        }
        return out;
    }

    public static final class Result{public final ArrayList<Record> records=new ArrayList<>();}
    public static final class Record{
        public String referenceType="",referenceValue="",item="",status="",normalizedStatus="",owner="",dueText="",remarks="",vendor="",project="";
        public WorkParsedDocument.Block source;
    }

    private static final class Header{
        String reference,referenceType,item,status,owner,due,remarks,vendor,project;int score;
        static Header detect(Map<String,String> cells){
            Header h=new Header();
            for(Map.Entry<String,String> e:cells.entrySet()){
                String x=normHeader(e.getValue()),col=e.getKey();
                if(any(x,"pr no","pr number","pr #","purchase request","طلب شراء","رقم طلب الشراء")){h.reference=col;h.referenceType="PR";h.score++;}
                else if(any(x,"po no","po number","po #","purchase order","أمر إسناد","امر اسناد","أمر شراء","امر شراء","رقم أمر الشراء","رقم امر الشراء")){h.reference=col;h.referenceType="PO";h.score++;}
                else if(any(x,"reference","ref no","ref number","reference no","reference number","المرجع","الرقم المرجعي")){h.reference=col;h.referenceType="REF";h.score++;}
                else if(any(x,"item description","description","item","scope","البند","الوصف","البيان")){h.item=col;h.score++;}
                else if(any(x,"status","state","الحالة","حالة")){h.status=col;h.score++;}
                else if(any(x,"responsible","owner","assigned to","assignee","pic","المسؤول","مسئول","مسؤول")){h.owner=col;h.score++;}
                else if(any(x,"due date","target date","deadline","required date","due","تاريخ الاستحقاق","التاريخ المطلوب","ميعاد","موعد")){h.due=col;h.score++;}
                else if(any(x,"remarks","remark","notes","note","comments","comment","ملاحظات","ملحوظات","تعليق")){h.remarks=col;h.score++;}
                else if(any(x,"vendor","supplier","contractor","المورد","المقاول")){h.vendor=col;h.score++;}
                else if(any(x,"project","project name","المشروع","اسم المشروع")){h.project=col;h.score++;}
            }
            return h;
        }

        Record record(WorkParsedDocument.Block b){
            Record r=new Record();r.referenceType=safe(referenceType);r.referenceValue=v(b.cells,reference);r.item=v(b.cells,item);r.status=v(b.cells,status);r.owner=v(b.cells,owner);r.dueText=v(b.cells,due);r.remarks=v(b.cells,remarks);r.vendor=v(b.cells,vendor);r.project=v(b.cells,project);r.source=b;
            if(r.referenceValue.isEmpty()&&r.item.isEmpty()&&r.status.isEmpty()&&r.owner.isEmpty()&&r.dueText.isEmpty()&&r.remarks.isEmpty())return null;
            if(looksLikeHeader(r))return null;
            r.normalizedStatus=normalizeStatus(r.status);
            return r;
        }
    }

    private static boolean looksLikeHeader(Record r){
        String a=normHeader(r.item),b=normHeader(r.status),c=normHeader(r.referenceValue);
        return "item".equals(a)||"description".equals(a)||"status".equals(b)||"reference".equals(c)||"pr no".equals(c)||"po no".equals(c);
    }

    static String normalizeStatus(String status){
        String x=normHeader(status);
        if(x.isEmpty())return "unknown";
        if(any(x,"approved","approve","accepted","معتمد","تم الاعتماد","موافق"))return "approved";
        if(any(x,"rejected","reject","مرفوض","رفض"))return "rejected";
        if(any(x,"completed","complete","done","closed","finished","تم","مكتمل","منتهي","مغلق"))return "completed";
        if(any(x,"issued","released","sent","صادر","تم الاصدار","تم الإصدار","مرسل"))return "issued";
        if(any(x,"cancelled","canceled","cancel","ملغي","ملغى","إلغاء","الغاء"))return "cancelled";
        if(any(x,"hold","on hold","paused","معلق","موقوف"))return "on_hold";
        if(any(x,"pending","open","waiting","in progress","under review","قيد","جاري","انتظار","معلق للمراجعة","تحت المراجعة"))return "open";
        return "other";
    }

    private static String v(Map<String,String> cells,String col){if(col==null)return "";return safe(cells.get(col));}
    private static boolean any(String x,String...values){for(String v:values)if(x.equals(v)||x.contains(v))return true;return false;}
    private static String normHeader(String s){return safe(s).toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s+"," ");}
    private static String safe(String s){return s==null?"":s.trim();}
}
