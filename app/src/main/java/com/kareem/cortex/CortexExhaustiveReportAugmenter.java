package com.kareem.cortex;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Adds the large strict verification matrix to the master simulation JSON after the 90-second run. */
public final class CortexExhaustiveReportAugmenter {
    private CortexExhaustiveReportAugmenter(){}

    public static JSONObject augment(Context context,File report)throws Exception{
        if(context==null||report==null||!report.exists())throw new FileNotFoundException("Master simulation JSON is unavailable");
        JSONObject root=new JSONObject(read(report));String runId=root.optString("run_id","augment_"+System.currentTimeMillis());VaultDb db=new VaultDb(context.getApplicationContext());JSONObject suite;
        try{suite=CortexExhaustiveVerificationSuite.run(context.getApplicationContext(),db,runId);}finally{db.close();}
        root.put("exhaustive_verification",suite);

        JSONObject summary=root.optJSONObject("summary");if(summary==null){summary=new JSONObject();root.put("summary",summary);}
        JSONObject counts=suite.optJSONObject("counts");if(counts==null)counts=new JSONObject();
        int extraFail=counts.optInt(CortexExhaustiveVerificationSuite.EXECUTED_FAIL,0);
        int blockedUser=counts.optInt(CortexExhaustiveVerificationSuite.BLOCKED_WAITING_USER,0);
        int blockedSetup=counts.optInt(CortexExhaustiveVerificationSuite.BLOCKED_SETUP,0);
        int protectedCount=counts.optInt(CortexExhaustiveVerificationSuite.PROTECTED_REQUIRES_CONFIRMATION,0);
        int baseFail=summary.optInt("failed_steps",0);int baseWarn=summary.optInt("warning_steps",0);
        String overall=baseFail>0||extraFail>0?"FAIL":(blockedUser+blockedSetup+protectedCount)>0?"INCOMPLETE_BLOCKED":baseWarn>0?"PASS_WITH_WARNINGS":"PASS";
        summary.put("overall",overall)
            .put("extra_test_count",suite.optInt("test_count",0))
            .put("extra_executed_count",suite.optInt("executed_count",0))
            .put("extra_execution_coverage",suite.optDouble("execution_coverage",0))
            .put("extra_counts",counts)
            .put("blocked_waiting_user",blockedUser)
            .put("blocked_setup",blockedSetup)
            .put("protected_confirmation_tests",protectedCount)
            .put("coverage_note","Blocked tests are not counted as PASS. Use the in-app Unblock Wizard and Protected Live Tests, then rerun.");
        JSONObject manifest=root.optJSONObject("max_data_manifest");if(manifest==null){manifest=new JSONObject();root.put("max_data_manifest",manifest);}manifest.put("exhaustive_verification_matrix",true).put("interactive_unblock_wizard",true).put("protected_live_test_layer",true);

        write(report,root.toString(2));summary.put("report_bytes",report.length()).put("report_file",report.getName());return summary;
    }

    private static String read(File f)throws Exception{StringBuilder b=new StringBuilder();try(Reader r=new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8)){char[] x=new char[32768];for(int n;(n=r.read(x))>0;)b.append(x,0,n);}return b.toString();}
    private static void write(File f,String s)throws Exception{try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8),131072)){w.write(s);}}
}
