package com.kareem.cortex;

import android.content.Context;
import android.content.pm.PackageInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Fast product-regression gate. No 90-second soak and no protected/live side effects. */
public final class CortexRepairRegressionRunner {
    private CortexRepairRegressionRunner(){}
    public static final class Result{public final File file;public final JSONObject summary;Result(File f,JSONObject s){file=f;summary=s;}}

    public static Result run(Context context)throws Exception{
        Context c=context.getApplicationContext();long started=System.currentTimeMillis();String runId="repair_"+started+"_"+Long.toHexString(System.nanoTime());VaultDb db=new VaultDb(c);JSONArray tests=new JSONArray();
        try{
            JSONArray a=CortexProductRegressionV61.run(c,db,runId);for(int i=0;i<a.length();i++)tests.put(a.get(i));
            JSONArray b=CortexProductRegressionV62.run(c,db,runId);for(int i=0;i<b.length();i++)tests.put(b.get(i));
        }finally{db.close();}
        int pass=0,fail=0;for(int i=0;i<tests.length();i++){JSONObject t=tests.optJSONObject(i);if(t==null)continue;String s=t.optString("status","");if(CortexExhaustiveVerificationSuite.EXECUTED_PASS.equals(s))pass++;else if(CortexExhaustiveVerificationSuite.EXECUTED_FAIL.equals(s))fail++;}
        JSONObject summary=new JSONObject().put("overall",fail==0?"PASS":"FAIL").put("test_count",tests.length()).put("pass",pass).put("fail",fail).put("duration_ms",System.currentTimeMillis()-started);
        JSONObject root=new JSONObject().put("schema","CORTEX_REPAIR_REGRESSION_V1").put("run_id",runId).put("version_code",versionCode(c)).put("started_at_ms",started).put("finished_at_ms",System.currentTimeMillis()).put("safety","Synthetic DB changes are rolled back; no protected/live external actions are executed.").put("tests",tests).put("summary",summary);
        File dir=new File(c.getFilesDir(),"debug_exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create debug_exports");File out=new File(dir,"CortexRepairRegression_v"+versionCode(c)+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".json");try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out),StandardCharsets.UTF_8))){w.write(root.toString(2));}summary.put("report_file",out.getName()).put("report_bytes",out.length());return new Result(out,summary);
    }

    private static long versionCode(Context c){try{PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}catch(Exception e){return 0;}}
}
