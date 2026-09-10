package com.kareem.cortex;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** One authoritative real-device Cortex status test. */
public class CortexAuditActivity extends Activity {
    private LinearLayout body,statusBox,resultBox;
    private TextView headline,summary,run;
    private volatile boolean destroyed=false;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"CortexExtensiveStatus");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refreshStatus();}
    @Override protected void onResume(){super.onResume();refreshStatus();}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(14),dp(18),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=CortexUi.plain(this,"Cortex System Status",27,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);
        TextView sub=CortexUi.text(this,"One extensive real-device test. Green means the path is actually working; red means broken; amber means user setup/access is still required; quarantined native engines are shown explicitly.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);tx.addView(sub);body.addView(head);

        LinearLayout hero=CortexUi.card(this,22);hero.setPadding(dp(15),dp(15),dp(15),dp(15));
        headline=CortexUi.plain(this,"Checking Cortex…",19,CortexUi.TEXT);CortexUi.medium(headline);hero.addView(headline);
        summary=CortexUi.text(this,"Reading the live capability state.",11,CortexUi.MUTED);summary.setPadding(0,dp(6),0,0);hero.addView(summary);body.addView(hero,lp(0,10,0,0));

        run=CortexUi.action(this,"RUN ONE EXTENSIVE SYSTEM TEST",CortexUi.LIME,true);run.setOnClickListener(v->runExtensive());body.addView(run,lp(0,12,0,0));
        TextView matrix=CortexUi.action(this,"REFRESH LIVE STATUS",CortexUi.MUTED,false);matrix.setOnClickListener(v->refreshStatus());body.addView(matrix,lp(0,8,0,0));

        body.addView(CortexUi.section(this,"Models, brain and runtime"));
        statusBox=new LinearLayout(this);statusBox.setOrientation(LinearLayout.VERTICAL);body.addView(statusBox);
        body.addView(CortexUi.section(this,"Extensive test result"));
        resultBox=new LinearLayout(this);resultBox.setOrientation(LinearLayout.VERTICAL);body.addView(resultBox);
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void refreshStatus(){
        if(destroyed||worker.isShutdown())return;
        try{worker.execute(()->{
            ArrayList<Row> rows=new ArrayList<>();int green=0,amber=0,red=0,quarantine=0;
            VaultDb db=null;
            try{
                db=new VaultDb(getApplicationContext());
                for(CortexCapabilityRegistry.Capability c:CortexCapabilityRegistry.all()){
                    CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(getApplicationContext(),db,c);
                    String visual=visualStatus(c,s);
                    rows.add(new Row(c.title,visual,s.detail));
                    if("GREEN".equals(visual))green++;else if("RED".equals(visual))red++;else if("QUARANTINED".equals(visual))quarantine++;else amber++;
                }
                addRuntimeRows(rows);
            }catch(Throwable e){rows.add(new Row("System health evaluator","RED",e.getClass().getSimpleName()+": "+safe(e.getMessage())));red++;}
            finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
            final int g=green,a=amber,r=red,q=quarantine;post(()->renderStatus(rows,g,a,r,q));
        });}catch(RejectedExecutionException ignored){}
    }

    private void addRuntimeRows(ArrayList<Row> rows){
        boolean recovery=StartupSafetyGate.active();
        rows.add(new Row("Recovery safety gate",recovery?"GREEN":"RED",recovery?"Recovery protection is active.":"Recovery protection is unexpectedly disabled."));
        rows.add(nativeRow("OCR native runtime",CapabilitySupervisor.Capability.OCR_NATIVE));
        rows.add(nativeRow("ASR / Whisper native runtime",CapabilitySupervisor.Capability.ASR_NATIVE));
        rows.add(nativeRow("Local LLM native runtime",CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE));
        boolean ext=ExternalBrainProvider.configured(this);
        rows.add(new Row("External reasoning brain",ext?"GREEN":"AMBER",ext?ExternalBrainProvider.activeProviderId(this)+" · "+ExternalBrainProvider.activeModel(this):"No external reasoning provider is configured."));
        rows.add(new Row("OpenRouter",OpenRouterKeyStore.has(this)?"GREEN":"AMBER",OpenRouterKeyStore.has(this)?OpenRouterModelConfig.generationModel(this):"Not configured."));
        rows.add(new Row("Gemini",GeminiKeyStore.has(this)?"GREEN":"AMBER",GeminiKeyStore.has(this)?"Configured":"Not configured."));
        boolean local=LocalModelManager.installed(this)&&LocalModelManager.verified(this);
        LocalLlmRuntime.State st=LocalLlmRuntime.state(this);
        rows.add(new Row("Local Qwen model",local?(recovery?"QUARANTINED":"GREEN"):"AMBER",local?(recovery?"Model is installed/verified but native inference is intentionally quarantined.":"Installed and verified."):"Model is not installed/verified."+(safe(st.error).isEmpty()?"":" · "+safe(st.error))));
    }

    private Row nativeRow(String name,CapabilitySupervisor.Capability c){boolean allowed=CapabilitySupervisor.allowed(this,c);return new Row(name,allowed?"GREEN":"QUARANTINED",allowed?"Capability supervisor allows this runtime.":"Intentionally isolated by the recovery capability supervisor.");}

    private String visualStatus(CortexCapabilityRegistry.Capability c,CortexCapabilityRegistry.State s){
        if(("ocr".equals(c.key)||"audio_asr".equals(c.key)||"local_qwen".equals(c.key))&&StartupSafetyGate.active())return "QUARANTINED";
        if(CortexCapabilityRegistry.ACTIVE.equals(s.status)||CortexCapabilityRegistry.READY.equals(s.status))return "GREEN";
        if(CortexCapabilityRegistry.FAILED.equals(s.status)||CortexCapabilityRegistry.NOT_VERIFIED.equals(s.status))return "RED";
        return "AMBER";
    }

    private void runExtensive(){
        if(destroyed||worker.isShutdown())return;run.setEnabled(false);run.setText("RUNNING EXTENSIVE TEST…");headline.setText("Testing every safe Cortex path…");resultBox.removeAllViews();
        try{worker.execute(()->{
            CortexFunctionalSelfTest.Report report=CortexFunctionalSelfTest.run(getApplicationContext());
            post(()->{renderReport(report);run.setEnabled(true);run.setText("RUN ONE EXTENSIVE SYSTEM TEST AGAIN");refreshStatus();});
        });}catch(RejectedExecutionException ignored){}
    }

    private void renderStatus(ArrayList<Row> rows,int green,int amber,int red,int quarantine){
        if(destroyed)return;
        headline.setText(red==0?"SYSTEM HEALTHY":"SYSTEM NEEDS ATTENTION");headline.setTextColor(red==0?CortexUi.GREEN:CortexUi.RED);
        summary.setText("GREEN "+green+"   •   AMBER "+amber+"   •   RED "+red+"   •   QUARANTINED "+quarantine);
        statusBox.removeAllViews();for(Row row:rows)addRow(statusBox,row.title,row.state,row.detail);
    }

    private void renderReport(CortexFunctionalSelfTest.Report report){
        resultBox.removeAllViews();String state=report.fail==0?"GREEN":"RED";
        addRow(resultBox,"ONE EXTENSIVE SYSTEM TEST",state,report.pass+" passed · "+report.warn+" warning · "+report.fail+" failed");
        for(String line:report.lines){String upper=line.toUpperCase(Locale.ROOT);String s=upper.startsWith("FAIL")?"RED":upper.startsWith("WARN")?"AMBER":"GREEN";addRow(resultBox,line,s,"");}
    }

    private void addRow(LinearLayout parent,String title,String state,String detail){
        LinearLayout card=CortexUi.card(this,17);card.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t=CortexUi.text(this,title,12,CortexUi.TEXT);CortexUi.medium(t);top.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        TextView chip=CortexUi.chip(this,state,statusColor(state),true);top.addView(chip,new LinearLayout.LayoutParams(-2,dp(29)));card.addView(top);
        if(!safe(detail).isEmpty()){TextView d=CortexUi.text(this,detail,10,CortexUi.MUTED);d.setPadding(0,dp(5),0,0);d.setMaxLines(8);card.addView(d);}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(7));parent.addView(card,p);
    }

    private int statusColor(String s){if("GREEN".equals(s))return CortexUi.GREEN;if("RED".equals(s))return CortexUi.RED;if("QUARANTINED".equals(s))return CortexUi.COPPER;return CortexUi.YELLOW;}
    private void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
    private LinearLayout.LayoutParams lp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private static String safe(String s){return s==null?"":s.trim();}
    private static final class Row{final String title,state,detail;Row(String t,String s,String d){title=t;state=s;detail=d;}}
}
