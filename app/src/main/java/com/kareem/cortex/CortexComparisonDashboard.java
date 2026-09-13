package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Aggregates accepted ChatGPT benchmark verdicts into a user-visible scorecard. */
public final class CortexComparisonDashboard {
    private CortexComparisonDashboard() {}

    public static JSONObject build(Context context) {
        JSONObject out = new JSONObject();
        try {
            ChatGptBridgeStore store = new ChatGptBridgeStore(context.getApplicationContext());
            List<JSONObject> verdicts = store.listVerdicts();
            int batches=0,total=0,agree=0,falseInterruptions=0,missedUrgent=0,critical=0,grounding=0;
            int fileCases=0,fileFailures=0,continuityFailures=0;
            JSONArray lessons = new JSONArray();
            JSONArray disagreements = new JSONArray();

            for (JSONObject envelope : verdicts) {
                String testId = envelope.optString("testId", "");
                if (!testId.startsWith("true-comparison-batch-")) continue;
                JSONObject p = envelope.optJSONObject("payload");
                if (p == null) continue;
                batches++;
                JSONObject m = firstObject(p, "aggregateAgreementMetrics", "metrics", "aggregateMetrics");
                if (m != null) {
                    int batchTotal = firstInt(m,100,"totalCases","cases","caseCount");
                    int batchAgree = firstInt(m,-1,"agreementCount","agreements","matched");
                    total += batchTotal;
                    if (batchAgree >= 0) agree += batchAgree;
                    else {
                        double rate = firstDouble(m,-1,"agreementRate","accuracy","agreement");
                        if (rate >= 0) agree += (int)Math.round(rate <= 1.0 ? rate * batchTotal : (rate/100.0) * batchTotal);
                    }
                    falseInterruptions += firstInt(m,0,"falseInterruptions","falsePositiveInterruptions");
                    missedUrgent += firstInt(m,0,"missedUrgentCases","missedUrgent","falseNegatives");
                    critical += firstInt(m,0,"criticalDisagreements","criticalMismatchCount");
                    grounding += firstInt(m,0,"groundingViolations","unsupportedClaims");
                    fileCases += firstInt(m,0,"fileCases","fileCaseCount");
                    fileFailures += firstInt(m,0,"fileFlowFailures","fileFailures");
                    continuityFailures += firstInt(m,0,"continuityFailures","stateContinuityFailures");
                }
                copyStrings(firstArray(p,"topLessons","lessons","teachingLessons"),lessons,10);
                copyObjects(firstArray(p,"materialDisagreements","mismatches","criticalDisagreements"),disagreements,10);
            }

            double accuracy = total <= 0 ? -1 : (100.0 * agree / total);
            out.put("batches",batches).put("expectedBatches",10)
                    .put("totalCases",total).put("agreementCount",agree).put("accuracyPercent",accuracy)
                    .put("falseInterruptions",falseInterruptions).put("missedUrgentCases",missedUrgent)
                    .put("criticalDisagreements",critical).put("groundingViolations",grounding)
                    .put("fileCases",fileCases).put("fileFlowFailures",fileFailures)
                    .put("continuityFailures",continuityFailures).put("topLessons",lessons)
                    .put("topDisagreements",disagreements)
                    .put("complete",batches>=10 && total>=1000);
        } catch (Throwable t) {
            try { out.put("error",t.getClass().getSimpleName()+": "+safe(t.getMessage())); } catch (Throwable ignored) {}
        }
        return out;
    }

    public static String render(Context context) {
        JSONObject d = build(context);
        if (d.has("error")) return "Dashboard unavailable · "+d.optString("error");
        int batches=d.optInt("batches",0), total=d.optInt("totalCases",0);
        StringBuilder s=new StringBuilder();
        s.append("Comparison batches: ").append(batches).append(" / 10");
        s.append("\nCases judged: ").append(total).append(" / 1000");
        double accuracy=d.optDouble("accuracyPercent",-1);
        s.append("\nAccuracy / agreement: ").append(accuracy<0?"waiting":String.format(java.util.Locale.US,"%.1f%%",accuracy));
        s.append("\nFalse interruptions: ").append(d.optInt("falseInterruptions",0));
        s.append("\nMissed urgent cases: ").append(d.optInt("missedUrgentCases",0));
        s.append("\nCritical disagreements: ").append(d.optInt("criticalDisagreements",0));
        s.append("\nGrounding violations: ").append(d.optInt("groundingViolations",0));
        s.append("\nFile-flow failures: ").append(d.optInt("fileFlowFailures",0)).append(" / ").append(d.optInt("fileCases",0));
        s.append("\nContinuity failures: ").append(d.optInt("continuityFailures",0));
        JSONArray lessons=d.optJSONArray("topLessons");
        if(lessons!=null&&lessons.length()>0){
            s.append("\n\nTop lessons:");
            for(int i=0;i<Math.min(10,lessons.length());i++) s.append("\n• ").append(lessons.optString(i,""));
        }
        JSONArray disagreements=d.optJSONArray("topDisagreements");
        if(disagreements!=null&&disagreements.length()>0){
            s.append("\n\nMaterial disagreements:");
            for(int i=0;i<Math.min(5,disagreements.length());i++){
                JSONObject x=disagreements.optJSONObject(i);
                if(x!=null)s.append("\n• ").append(x.optString("caseId","case")).append(" · ")
                        .append(x.optString("reason",x.optString("detail",x.toString())));
                else s.append("\n• ").append(disagreements.optString(i,""));
            }
        }
        if(!d.optBoolean("complete",false)) s.append("\n\nStatus: waiting for remaining ChatGPT verdicts.");
        else s.append("\n\nStatus: benchmark complete.");
        return s.toString();
    }

    private static JSONObject firstObject(JSONObject p,String...keys){for(String k:keys){JSONObject x=p.optJSONObject(k);if(x!=null)return x;}return null;}
    private static JSONArray firstArray(JSONObject p,String...keys){for(String k:keys){JSONArray x=p.optJSONArray(k);if(x!=null)return x;}return null;}
    private static int firstInt(JSONObject p,int fallback,String...keys){for(String k:keys)if(p.has(k))return p.optInt(k,fallback);return fallback;}
    private static double firstDouble(JSONObject p,double fallback,String...keys){for(String k:keys)if(p.has(k))return p.optDouble(k,fallback);return fallback;}
    private static void copyStrings(JSONArray src,JSONArray dst,int max)throws Exception{if(src==null)return;for(int i=0;i<src.length()&&dst.length()<max;i++){Object v=src.opt(i);if(v!=null)dst.put(v instanceof JSONObject?((JSONObject)v).optString("lesson",v.toString()):String.valueOf(v));}}
    private static void copyObjects(JSONArray src,JSONArray dst,int max)throws Exception{if(src==null)return;for(int i=0;i<src.length()&&dst.length()<max;i++)dst.put(src.opt(i));}
    private static String safe(String s){return s==null?"":s;}
}
