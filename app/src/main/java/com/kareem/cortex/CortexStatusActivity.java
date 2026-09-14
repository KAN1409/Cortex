package com.kareem.cortex;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** User-facing health summary. Engineering diagnostics remain in dedicated internal Activities. */
public class CortexStatusActivity extends Activity {
    private static final Set<String> CORE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "app_identity","components","db_integrity","db_schema","vault_readability",
            "grounded_ask","semantic_retrieval","smart_inbox","interaction_telemetry"
    )));

    private LinearLayout body,issuesBox;
    private TextView stateText,summaryText,setupText,refresh;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"CortexSystemHealth");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private volatile boolean destroyed=false,running=false;
    private boolean resumedOnce=false;

    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
        refreshHealth();
    }

    @Override protected void onResume(){
        super.onResume();
        if(resumedOnce)refreshHealth(); else resumedOnce=true;
    }

    @Override protected void onDestroy(){
        destroyed=true;
        worker.shutdownNow();
        super.onDestroy();
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setContentDescription("Back");back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=CortexUi.plain(this,"System Health",29,CortexUi.TEXT);CortexUi.medium(title);titles.addView(title);
        TextView sub=CortexUi.text(this,"A simple live check of the Cortex paths that protect your data and results.",11,CortexUi.MUTED);sub.setPadding(0,dp(2),0,0);titles.addView(sub);body.addView(head);

        LinearLayout hero=CortexUi.card(this,24);hero.setPadding(dp(16),dp(18),dp(16),dp(18));
        TextView eyebrow=CortexUi.plain(this,"CORTEX STATUS",11,CortexUi.MUTED);CortexUi.medium(eyebrow);hero.addView(eyebrow);
        stateText=CortexUi.plain(this,"CHECKING…",28,CortexUi.TEXT);CortexUi.medium(stateText);stateText.setPadding(0,dp(8),0,0);hero.addView(stateText);
        summaryText=CortexUi.text(this,"Reading core data, memory and intelligence paths.",13,CortexUi.MUTED);summaryText.setPadding(0,dp(8),0,0);hero.addView(summaryText);
        setupText=CortexUi.text(this,"",11,CortexUi.MUTED);setupText.setPadding(0,dp(8),0,0);hero.addView(setupText);
        body.addView(hero,lp(0,16,0,0));

        refresh=CortexUi.action(this,"REFRESH HEALTH",CortexUi.LIME,true);refresh.setContentDescription("Refresh System Health");refresh.setOnClickListener(v->refreshHealth());body.addView(refresh,lp(0,12,0,0));

        body.addView(CortexUi.section(this,"Needs attention"));
        issuesBox=new LinearLayout(this);issuesBox.setOrientation(LinearLayout.VERTICAL);body.addView(issuesBox);
        TextView note=CortexUi.text(this,"Optional permissions and providers do not count as failures by themselves. Cortex will ask for them when a feature actually needs them.",11,CortexUi.MUTED);note.setPadding(dp(2),dp(14),dp(2),0);body.addView(note);

        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void refreshHealth(){
        if(destroyed||worker.isShutdown()||running)return;
        running=true;refresh.setEnabled(false);refresh.setText("CHECKING…");stateText.setText("CHECKING…");stateText.setTextColor(CortexUi.TEXT);summaryText.setText("Reading core data, memory and intelligence paths.");
        try{worker.execute(()->{
            Health health=evaluate();
            post(()->render(health));
        });}catch(RejectedExecutionException ignored){running=false;}
    }

    private Health evaluate(){
        VaultDb db=null;
        ArrayList<Issue> critical=new ArrayList<>(),degraded=new ArrayList<>();
        int setup=0;
        try{
            db=new VaultDb(getApplicationContext());
            for(CortexCapabilityRegistry.Capability c:CortexCapabilityRegistry.all()){
                CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(getApplicationContext(),db,c);
                if(CortexCapabilityRegistry.FAILED.equals(s.status)){
                    Issue issue=new Issue(c.title,s.detail,CortexRemediation.canHandle(c.title));
                    if(CORE.contains(c.key))critical.add(issue);else degraded.add(issue);
                }else if(CortexCapabilityRegistry.NOT_VERIFIED.equals(s.status)){
                    degraded.add(new Issue(c.title,s.detail,CortexRemediation.canHandle(c.title)));
                }else if(CortexCapabilityRegistry.NEEDS_ACCESS.equals(s.status)||CortexCapabilityRegistry.NEEDS_SETUP.equals(s.status)){
                    setup++;
                }
            }
        }catch(Throwable t){
            critical.add(new Issue("System health check","Cortex could not complete the live health evaluation: "+safe(t.getMessage()),false));
        }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
        String level=critical.isEmpty()?(degraded.isEmpty()?"HEALTHY":"DEGRADED"):"NEEDS ATTENTION";
        ArrayList<Issue> shown=new ArrayList<>();shown.addAll(critical);shown.addAll(degraded);
        return new Health(level,shown,critical.size(),degraded.size(),setup);
    }

    private void render(Health h){
        if(destroyed)return;
        running=false;refresh.setEnabled(true);refresh.setText("REFRESH HEALTH");
        int color="HEALTHY".equals(h.level)?CortexUi.GREEN:("DEGRADED".equals(h.level)?CortexUi.YELLOW:CortexUi.RED);
        stateText.setText(h.level);stateText.setTextColor(color);
        if("HEALTHY".equals(h.level))summaryText.setText("Core Cortex data, memory and intelligence paths are operating normally.");
        else if("DEGRADED".equals(h.level))summaryText.setText("Core Cortex remains available, but "+h.degraded+" non-core subsystem"+(h.degraded==1?" is":"s are")+" reporting a failure.");
        else summaryText.setText(h.critical+" core problem"+(h.critical==1?" needs":"s need")+" attention before Cortex can claim full health.");
        setupText.setText(h.setup>0?h.setup+" optional connection"+(h.setup==1?" is":"s are")+" not configured. This is not counted as a failure.":"No optional setup is blocking this health result.");
        issuesBox.removeAllViews();
        if(h.issues.isEmpty()){
            TextView ok=CortexUi.text(this,"No active failures need your attention.",13,CortexUi.MUTED);ok.setPadding(dp(2),dp(8),dp(2),dp(10));issuesBox.addView(ok);return;
        }
        for(Issue issue:h.issues)addIssue(issue);
    }

    private void addIssue(Issue issue){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(14),dp(2),dp(14));
        TextView dot=CortexUi.plain(this,"•",20,CortexUi.RED);dot.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);row.addView(dot,new LinearLayout.LayoutParams(dp(24),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=CortexUi.plain(this,issue.title,15,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);
        TextView detail=CortexUi.text(this,issue.detail,11,CortexUi.MUTED);detail.setPadding(0,dp(3),0,0);tx.addView(detail);
        if(issue.fixable){TextView fix=CortexUi.plain(this,"Tap to fix",10,CortexUi.LIME);fix.setPadding(0,dp(5),0,0);tx.addView(fix);row.setContentDescription(issue.title+". Tap to fix.");CortexUi.pressable(this,row,CortexUi.round(this,android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT,12));row.setOnClickListener(v->CortexRemediation.open(this,issue.title));}
        issuesBox.addView(row);issuesBox.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
    private LinearLayout.LayoutParams lp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private static String safe(String s){return s==null?"":s.trim();}
    private static final class Issue{final String title,detail;final boolean fixable;Issue(String t,String d,boolean f){title=t;detail=d;fixable=f;}}
    private static final class Health{final String level;final ArrayList<Issue> issues;final int critical,degraded,setup;Health(String l,ArrayList<Issue> i,int c,int d,int s){level=l;issues=i;critical=c;degraded=d;setup=s;}}
}
