package com.kareem.cortex;

import java.util.ArrayList;

/** Small deterministic regression corpus. Real-data evaluation remains the source of product truth. */
public final class RelevanceSmokeTest {
    private RelevanceSmokeTest(){}

    public static final class Result {
        public final int total,passed;public final ArrayList<String> failures;
        Result(int total,int passed,ArrayList<String> failures){this.total=total;this.passed=passed;this.failures=failures;}
        public boolean ok(){return passed==total;}
    }

    private static final class C {
        final String mode,text,source,expected,candidate;final boolean ongoing;
        C(String mode,String text,String source,boolean ongoing,String expected,String candidate){this.mode=mode;this.text=text;this.source=source;this.ongoing=ongoing;this.expected=expected;this.candidate=candidate==null?"":candidate;}
    }

    public static Result run(){
        ArrayList<C> xs=new ArrayList<>();
        // 15 hard-noise cases.
        fast(xs,"Charging 53%","com.android.systemui",false,"IGNORE");
        fast(xs,"Battery level 20%","com.android.systemui",false,"IGNORE");
        fast(xs,"Fully charged","com.android.systemui",false,"IGNORE");
        fast(xs,"1 h 20 m until full","com.android.systemui",false,"IGNORE");
        fast(xs,"USB debugging connected","com.android.systemui",false,"IGNORE");
        fast(xs,"VPN is active","com.android.systemui",false,"IGNORE");
        fast(xs,"Connected to WiFi","com.android.systemui",false,"IGNORE");
        fast(xs,"Bluetooth connected","com.android.systemui",false,"IGNORE");
        fast(xs,"Syncing","com.android.systemui",true,"IGNORE");
        fast(xs,"App is running in the background","com.android.systemui",true,"IGNORE");
        fast(xs,"Now playing","com.spotify.music",true,"IGNORE");
        fast(xs,"Playing","com.google.android.apps.youtube.music",true,"IGNORE");
        fast(xs,"Media output","com.android.systemui",true,"IGNORE");
        fast(xs,"البطارية ٨٠٪","com.android.systemui",false,"IGNORE");
        fast(xs,"الشحن السريع يعمل","com.android.systemui",false,"IGNORE");

        // 15 clear communication cases, including Arabic spelling variants.
        thread(xs,"إبعتلي الملف لو سمحت","ACTION","");
        thread(xs,"ممكن تبعتلي الرسمة؟","ACTION","");
        thread(xs,"محتاجك تبعت النسخة النهائية","ACTION","");
        thread(xs,"please send the revised drawing","ACTION","");
        thread(xs,"can you confirm the quantity","ACTION","");
        thread(xs,"could you review this","ACTION","");
        thread(xs,"هبعتلك الملف النهارده","WAITING","");
        thread(xs,"هراجع وارجعلك","WAITING","");
        thread(xs,"هرد عليك بكره","WAITING","");
        thread(xs,"I'll send you the quotation","WAITING","");
        thread(xs,"I will get back to you","WAITING","");
        thread(xs,"تمت الموافقة","DECISION","");
        thread(xs,"تم الرفض","DECISION","");
        thread(xs,"approved","DECISION","");
        thread(xs,"has been rejected","DECISION","");

        // 10 explicit ambiguity cases that should remain in Review.
        thread(xs,"لازم يتبعت الملف","REVIEW","ACTION");
        thread(xs,"المفروض تبعت النسخة","REVIEW","ACTION");
        thread(xs,"محتاج موافقتك","REVIEW","ACTION");
        thread(xs,"we need the drawing","REVIEW","ACTION");
        thread(xs,"هحاول أبعتلك","REVIEW","WAITING");
        thread(xs,"المفروض يرد","REVIEW","WAITING");
        thread(xs,"expect a reply","REVIEW","WAITING");
        thread(xs,"مبدئيًا موافق","REVIEW","DECISION");
        thread(xs,"probably approved","REVIEW","DECISION");
        thread(xs,"tentatively approved","REVIEW","DECISION");

        // 10 ordinary/negative communication cases: no task should be fabricated.
        // Two slots deliberately exercise additional tentative-decision regressions.
        thread(xs,"تمام شكرا","CONTEXT","");
        thread(xs,"صباح الخير","CONTEXT","");
        thread(xs,"وصلت","CONTEXT","");
        thread(xs,"هشوف","CONTEXT","");
        thread(xs,"maybe later","CONTEXT","");
        thread(xs,"likely rejected","REVIEW","DECISION");
        thread(xs,"the file is attached","CONTEXT","");
        thread(xs,"what do you think?","CONTEXT","");
        thread(xs,"غالبًا مرفوض","REVIEW","DECISION");
        thread(xs,"بكره نتكلم","CONTEXT","");

        int passed=0;ArrayList<String> failures=new ArrayList<>();int i=0;
        for(C x:xs){i++;MasterRelevanceFilter.Decision d;if("FAST".equals(x.mode)){MasterRelevanceFilter.Signal s=new MasterRelevanceFilter.Signal("notification",x.source,"",x.text,"",System.currentTimeMillis(),x.ongoing);d=MasterRelevanceFilter.evaluateFast(s);}else d=MasterRelevanceFilter.evaluateThread(x.text,x.text);
            boolean ok=x.expected.equals(d.disposition.name());if(ok&&!x.candidate.isEmpty())ok=x.candidate.equals(d.candidateKind);if(ok)passed++;else failures.add("#"+i+" expected "+x.expected+(x.candidate.isEmpty()?"":"("+x.candidate+")")+" but got "+d.disposition.name()+(d.candidateKind.isEmpty()?"":"("+d.candidateKind+")")+" • "+x.text);
        }
        return new Result(xs.size(),passed,failures);
    }

    private static void fast(ArrayList<C> xs,String text,String source,boolean ongoing,String expected){xs.add(new C("FAST",text,source,ongoing,expected,""));}
    private static void thread(ArrayList<C> xs,String text,String expected,String candidate){xs.add(new C("THREAD",text,"communication",false,expected,candidate));}
}
