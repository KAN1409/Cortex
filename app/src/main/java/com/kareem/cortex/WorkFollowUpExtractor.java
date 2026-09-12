package com.kareem.cortex;

import java.util.*;

/** Deterministic extraction of project/procurement follow-up rows. It preserves source location and raw status. */
public final class WorkFollowUpExtractor {
    public static final String VERSION="work_followup_extractor_002";
    private WorkFollowUpExtractor(){}

    public static Result extract(WorkParsedDocument doc){
        Result out=new Result();if(doc==null)return out;
        HashMap<String,Header> headers=new HashMap<>();
        for(WorkParsedDocument.Block b:doc.blocks){
            if(b==null||b.cells.isEmpty())continue;
            String scope=safe(b.sheetName).toLowerCase(Locale.ROOT);
            Header candidate=Header.detect(b.cells);
            if(candidate.score>=2&&candidate.hasOperationalColumn()){
                headers.put(scope,candidate);continue;
            }
            Header active=headers.get(scope);if(active==null)continue;
            out.records.addAll(active.records(b));
        }
        return out;
    }

    public static final class Result{public final ArrayList<Record> records=new ArrayList<>();}
    public static final class Record{
        public String referenceType="",referenceValue="",item="",status="",normalizedStatus="",owner="",dueText="",remarks="",vendor="",project="";
        public WorkParsedDocument.Block source;
    }

    private static final class Header{
        String prReference,poReference,genericReference,item,status,owner,due,remarks,vendor,project;int score;

        boolean hasOperationalColumn(){
            return prReference!=null||poReference!=null||genericReference!=null||status!=null||item!=null;
        }

        static Header detect(Map<String,String> cells){
            Header h=new Header();
            for(Map.Entry<String,String> e:cells.entrySet()){
                String x=normHeader(e.getValue()),col=e.getKey();
                if(any(x,"pr no","pr number","pr #","purchase request no","purchase request number","رقم طلب الشراء","طلب شراء رقم")){h.prReference=col;h.score++;continue;}
                if(any(x,"po no","po number","po #","purchase order no","purchase order number","رقم أمر الشراء","رقم امر الشراء","رقم أمر الإسناد","رقم امر الاسناد")){h.poReference=col;h.score++;continue;}
                if(any(x,"reference","ref no","ref number","reference no","reference number","document no","document number","pr/po no","po/pr no","المرجع","الرقم المرجعي","رقم المستند")){h.genericReference=col;h.score++;continue;}
                if(any(x,"item description","description","item","scope","البند","الوصف","البيان")){h.item=col;h.score++;continue;}
                if(any(x,"status","state","الحالة","حالة")){h.status=col;h.score++;continue;}
                if(any(x,"responsible","owner","assigned to","assignee","pic","المسؤول","مسئول","مسؤول")){h.owner=col;h.score++;continue;}
                if(any(x,"due date","target date","deadline","required date","due","تاريخ الاستحقاق","التاريخ المطلوب","ميعاد","موعد")){h.due=col;h.score++;continue;}
                if(any(x,"remarks","remark","notes","note","comments","comment","ملاحظات","ملحوظات","تعليق")){h.remarks=col;h.score++;continue;}
                if(any(x,"vendor","supplier","contractor","المورد","المقاول")){h.vendor=col;h.score++;continue;}
                if(any(x,"project","project name","المشروع","اسم المشروع")){h.project=col;h.score++;}
            }
            return h;
        }

        ArrayList<Record> records(WorkParsedDocument.Block b){
            ArrayList<Record> out=new ArrayList<>();
            String pr=v(b.cells,prReference),po=v(b.cells,poReference),generic=v(b.cells,genericReference);
            String itemValue=v(b.cells,item),statusValue=v(b.cells,status),ownerValue=v(b.cells,owner),dueValue=v(b.cells,due),remarksValue=v(b.cells,remarks),vendorValue=v(b.cells,vendor),projectValue=v(b.cells,project);
            boolean hasPayload=!itemValue.isEmpty()||!statusValue.isEmpty()||!ownerValue.isEmpty()||!dueValue.isEmpty()||!remarksValue.isEmpty()||!vendorValue.isEmpty()||!projectValue.isEmpty();

            if(!pr.isEmpty())addRecord(out,"PR",pr,itemValue,statusValue,ownerValue,dueValue,remarksValue,vendorValue,projectValue,b);
            if(!po.isEmpty()&&!sameReference(pr,po))addRecord(out,"PO",po,itemValue,statusValue,ownerValue,dueValue,remarksValue,vendorValue,projectValue,b);
            if(!generic.isEmpty()&&pr.isEmpty()&&po.isEmpty())addRecord(out,inferReferenceType(generic),stripReferencePrefix(generic),itemValue,statusValue,ownerValue,dueValue,remarksValue,vendorValue,projectValue,b);
            if(out.isEmpty()&&hasPayload)addRecord(out,"","",itemValue,statusValue,ownerValue,dueValue,remarksValue,vendorValue,projectValue,b);
            return out;
        }
    }

    private static void addRecord(List<Record> out,String type,String value,String item,String status,String owner,String due,String remarks,String vendor,String project,WorkParsedDocument.Block source){
        Record r=new Record();r.referenceType=safe(type);r.referenceValue=safe(value);r.item=safe(item);r.status=safe(status);r.owner=safe(owner);r.dueText=safe(due);r.remarks=safe(remarks);r.vendor=safe(vendor);r.project=safe(project);r.source=source;
        if(r.referenceValue.isEmpty()&&r.item.isEmpty()&&r.status.isEmpty()&&r.owner.isEmpty()&&r.dueText.isEmpty()&&r.remarks.isEmpty()&&r.vendor.isEmpty()&&r.project.isEmpty())return;
        if(looksLikeHeader(r))return;
        r.normalizedStatus=normalizeStatus(r.status);out.add(r);
    }

    private static boolean sameReference(String a,String b){return !safe(a).isEmpty()&&safe(a).equalsIgnoreCase(safe(b));}

    static String inferReferenceType(String value){
        String x=safe(value).toUpperCase(Locale.ROOT).replace('–','-').replace('—','-');
        if(x.matches("^P\\.?R\\.?\\s*[-:#/]?.*"))return "PR";
        if(x.matches("^P\\.?O\\.?\\s*[-:#/]?.*"))return "PO";
        return "REF";
    }

    static String stripReferencePrefix(String value){
        String x=safe(value).replace('–','-').replace('—','-');
        return x.replaceFirst("(?i)^P\\.?[RO]\\.?\\s*[-:#/]*\\s*","").trim();
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
