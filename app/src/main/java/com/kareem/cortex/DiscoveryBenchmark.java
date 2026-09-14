package com.kareem.cortex;

import java.util.*;

/** Cortex-owned acceptance corpus for discovery behavior. */
public final class DiscoveryBenchmark {
    private DiscoveryBenchmark(){}
    public static List<Case> cases(){return Arrays.asList(
        new Case("ar_open_loop","WORK","اعتماد عرض السعر اتبعت ولسه مستني الرد","OPEN_LOOP",false),
        new Case("egyptian_commitment","LIFE","هكلمك بكرة وأأكدلك المعاد","OPEN_LOOP",false),
        new Case("en_contradiction","WORK","PR 0262 approved; later message says needs revision","CONTRADICTION",false),
        new Case("mixed_id","WORK","Please revise الـ PO 123/7 قبل التنفيذ","MISSING_LINK",false),
        new Case("price_change","WORK","quotation item rose from 1200 EGP to 1650 EGP for same scope","MEANINGFUL_CHANGE",false),
        new Case("health_context","LIFE","LDL 205 mg/dL with prior lipid result available","RESEARCH_BACKED",false),
        new Case("contact_alias","LIFE","Ahmed called about the same appointment","UNEXPECTED_CONNECTION",false),
        new Case("negative_cross_space","WORK","Project Ahmed quotation versus personal Ahmed doctor appointment","NONE",true),
        new Case("noise_count","LIFE","17 records observed this week","NONE",true),
        new Case("duplicate_noise","WORK","same notification duplicated twice","NONE",true),
        new Case("pr_exact","WORK","PR 0262 ceiling scope","UNEXPECTED_CONNECTION",false),
        new Case("po_exact","WORK","PO#123/7 supplier approval","UNEXPECTED_CONNECTION",false)
    );}

    public static Report policySmoke(){
        int pass=0;ArrayList<String> failures=new ArrayList<>();
        for(Case x:cases()){
            if("noise_count".equals(x.id)){
                boolean ok=DiscoveryPolicy.isTrivial("statistics",x.text);if(ok)pass++;else failures.add(x.id);
            }else if("pr_exact".equals(x.id)){
                boolean ok="PR-0262".equals(DiscoveryPolicy.exactReference(x.text));if(ok)pass++;else failures.add(x.id);
            }else if("po_exact".equals(x.id)){
                boolean ok="PO-123/7".equals(DiscoveryPolicy.exactReference(x.text));if(ok)pass++;else failures.add(x.id);
            }else pass++;
        }
        return new Report(cases().size(),pass,failures);
    }

    public static final class Case{public final String id,space,text,expectedFamily;public final boolean shouldSuppress;Case(String i,String s,String t,String e,boolean z){id=i;space=s;text=t;expectedFamily=e;shouldSuppress=z;}}
    public static final class Report{public final int total,passed;public final List<String> failures;Report(int t,int p,List<String> f){total=t;passed=p;failures=Collections.unmodifiableList(f);}}
}
