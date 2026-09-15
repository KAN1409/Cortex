package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class CognitiveCouncilActivity extends Activity {
    VaultDb db;LinearLayout body;TextView status,models,runState,run;Handler handler=new Handler(Looper.getMainLooper());
    boolean destroyed=false;final Runnable poll=new Runnable(){public void run(){if(destroyed)return;refresh();handler.postDelayed(this,2500);}};
    com.google.common.util.concurrent.ListenableFuture<java.util.List<androidx.work.WorkInfo>> workSnapshot;
    String workMessage="";boolean workActive=false;

    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());build();refresh();handler.postDelayed(poll,2500);
    }
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,dp(8),0,dp(12));
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);header.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        titleBox.addView(CortexUi.eyebrow(this,"CORTEX · LOCAL COGNITIVE COUNCIL",CortexUi.LIME));TextView h=CortexUi.plain(this,"Brains",34,CortexUi.TEXT);CortexUi.medium(h);titleBox.addView(h);
        TextView close=CortexUi.chip(this,"Close",CortexUi.MUTED,false);close.setOnClickListener(v->finish());header.addView(close,new LinearLayout.LayoutParams(-2,dp(36)));body.addView(header);

        LinearLayout hero=CortexUi.card(this,28);hero.setPadding(dp(18),dp(17),dp(18),dp(17));hero.addView(CortexUi.eyebrow(this,"MAXIMUM LOCAL REASONING",CortexUi.LIME));
        TextView hh=CortexUi.plain(this,"See every brain think",22,CortexUi.TEXT);CortexUi.medium(hh);hh.setPadding(0,dp(8),0,0);hero.addView(hh);
        TextView hb=CortexUi.text(this,"Primary investigator → independent analyst → adversarial critic → final judge. Every pass stays tied to the same grounded evidence pack.",12,CortexUi.MUTED);hb.setPadding(0,dp(6),0,0);hero.addView(hb);body.addView(hero,lp(0,4,0,0));

        models=CortexUi.text(this,"Checking model state…",11,CortexUi.MUTED);models.setPadding(dp(4),dp(14),dp(4),dp(4));body.addView(models);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);TextView install=CortexUi.action(this,"Install / continue brains",CortexUi.OLIVE,false);run=CortexUi.action(this,"Run council now",CortexUi.LIME,false);
        actions.addView(install,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(46),1);rp.setMargins(dp(8),0,0,0);actions.addView(run,rp);body.addView(actions,lp(0,8,0,0));
        install.setOnClickListener(v->{LocalCouncilModelDownloadService.startAll(this);Toast.makeText(this,"Council model download started / resumed",Toast.LENGTH_SHORT).show();refresh();});
        run.setOnClickListener(v->{
            int ready=LocalCouncilModelRegistry.readyCount(this);
            if(ready<LocalCouncilModelRegistry.council().size()){
                Toast.makeText(this,"Required models are missing. Use Install / continue brains to download them.",Toast.LENGTH_LONG).show();
                refresh();return;
            }
            run.setEnabled(false);runState.setText("Council is starting on the strongest grounded situation…");
            DiscoveryV3DeepScheduler.kickFresh(getApplicationContext());
            Toast.makeText(this,"Analysis queued. Results will be saved when complete.",Toast.LENGTH_LONG).show();
        });

        status=CortexUi.text(this,"",11,CortexUi.MUTED);status.setPadding(dp(4),dp(14),dp(4),0);body.addView(status);
        runState=CortexUi.text(this,"",11,CortexUi.MUTED);runState.setPadding(dp(4),dp(6),dp(4),dp(8));body.addView(runState);
        body.addView(CortexUi.section(this,"Latest council runs"));
        CortexUi.fitSystemBars(this,root);setContentView(root);
    }

    void refresh(){
        if(destroyed)return;
        try{
            if(workSnapshot!=null&&workSnapshot.isDone()){
                java.util.List<androidx.work.WorkInfo> jobs=workSnapshot.get();workActive=false;workMessage="";
                for(androidx.work.WorkInfo job:jobs){
                    if(!job.getState().isFinished()){workActive=true;workMessage="Analysis "+job.getState().name().toLowerCase(java.util.Locale.ROOT);break;}
                    if(job.getState()==androidx.work.WorkInfo.State.FAILED)workMessage=job.getOutputData().getString("error");
                    else if(job.getState()==androidx.work.WorkInfo.State.SUCCEEDED)workMessage="Last requested analysis finished. Published findings: "+job.getOutputData().getInt("published",0);
                }
                workSnapshot=null;
            }
            if(workSnapshot==null)workSnapshot=androidx.work.WorkManager.getInstance(getApplicationContext()).getWorkInfosForUniqueWork("cortex-discovery-v3-deep-now");
            StringBuilder ms=new StringBuilder();int ready=0;
            for(LocalCouncilModelRegistry.Model m:LocalCouncilModelRegistry.council()){
                boolean r=LocalCouncilModelRegistry.ready(this,m);if(r)ready++;
                ms.append(r?"✓ ":"○ ").append(m.role).append(" · ").append(m.name);
                if(r){ms.append(" · READY");}
                else{
                    LocalCouncilModelDownloadService.Progress p=LocalCouncilModelDownloadService.progress(this,m);
                    if(p.done>0||!p.phase.isEmpty()){
                        ms.append(" · ").append(p.phase.isEmpty()?"PARTIAL":p.phase.toUpperCase(Locale.ROOT));
                        if(p.total>0)ms.append(" · ").append(LocalModelManager.human(p.done)).append(" / ").append(LocalModelManager.human(p.total)).append(" · ").append(p.percent()).append("%");
                        else if(p.done>0)ms.append(" · ").append(LocalModelManager.human(p.done));
                        if(p.speed>0){ms.append(" · ").append(LocalModelManager.human(p.speed)).append("/s");long eta=p.etaSeconds();if(eta>=0)ms.append(" · ETA ").append(etaText(eta));}
                        if("failed".equals(p.phase)&&!p.error.isEmpty())ms.append(" · ").append(clip(p.error,100));
                    }else ms.append(" · MISSING");
                }
                ms.append("\n");
            }
            models.setText(ms.toString().trim());
            status.setText("Council readiness: "+ready+"/"+LocalCouncilModelRegistry.council().size()+" heavy brains");
            renderRuns();
            if(workMessage!=null&&!workMessage.isEmpty())runState.setText(workMessage);
        }catch(Throwable e){runState.setText("Council view error: "+safe(e.getMessage()));}
    }

    void renderRuns(){
        while(body.getChildCount()>7)body.removeViewAt(7);
        SQLiteDatabase r=db.getReadableDatabase();DiscoveryV3Schema.ensure(r);int recovered=CognitiveCouncilRunRecovery.recoverStale(this,r,System.currentTimeMillis());
        Cursor c=r.rawQuery("SELECT r.id,r.situation_id,r.state,r.models_used,r.evidence_count,r.error,r.started_at,r.completed_at,s.label,s.domain "+
                "FROM discovery_v3_council_runs r LEFT JOIN discovery_v3_situations s ON s.id=r.situation_id ORDER BY r.id DESC LIMIT 6",null);
        int n=0;boolean running=false;
        while(c.moveToNext()){n++;String state=s(c,2);if("running".equals(state))running=true;body.addView(runCard(r,c),lp(0,0,0,10));}
        c.close();
        run.setEnabled(!running&&!workActive);
        runState.setText(running?"One council run is active. This screen will refresh automatically.":(recovered>0?recovered+" interrupted run(s) recovered. Deep analysis now runs only when you request it.":(n==0?(LocalCouncilModelRegistry.fullCouncilReady(this)?"Ready. Deep analysis runs only when you tap Run council now.":"Maximum council is waiting for all 3 local brains. Install / continue brains resumes the missing downloads."):"Deep analysis is manual · latest results below · tap a run to inspect every model pass.")));
    }

    View runCard(SQLiteDatabase r,Cursor c){
        long runId=c.getLong(0),sid=c.getLong(1),started=c.getLong(6),completed=c.getLong(7);String state=s(c,2),used=s(c,3),error=s(c,5),label=s(c,8),domain=s(c,9);
        LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(15),dp(14),dp(15),dp(14));CortexUi.pressable(this,card,CortexUi.velvet(this,22));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.eyebrow(this,(domain.isEmpty()?"GENERAL":domain)+" · "+state.toUpperCase(Locale.ROOT),state.contains("publish")?CortexUi.LIME:("failed".equals(state)?CortexUi.YELLOW:CortexUi.OLIVE)),new LinearLayout.LayoutParams(0,-2,1));
        top.addView(CortexUi.plain(this,c.getInt(4)+" evidence",9,CortexUi.FAINT));card.addView(top);
        TextView h=CortexUi.text(this,label.isEmpty()?"Situation #"+sid:label,15,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(6),0,0);card.addView(h);
        TextView meta=CortexUi.text(this,"Started "+stamp(started)+(completed>0?" · finished "+stamp(completed):" · still running"),10,CortexUi.MUTED);meta.setPadding(0,dp(4),0,0);card.addView(meta);
        if(!used.isEmpty()){TextView u=CortexUi.text(this,"Brains · "+used,10,CortexUi.MUTED);u.setPadding(0,dp(4),0,0);card.addView(u);}
        if(!error.isEmpty()){TextView e=CortexUi.text(this,"Blocked / error · "+clip(error,260),10,CortexUi.YELLOW);e.setPadding(0,dp(5),0,0);card.addView(e);}
        String decision=finalDecision(r,runId);if(!decision.isEmpty()){TextView d=CortexUi.text(this,clip(decision,420),11,CortexUi.TEXT);d.setPadding(0,dp(7),0,0);card.addView(d);}
        card.setOnClickListener(v->showRun(runId,label,domain));return card;
    }

    void showRun(long runId,String label,String domain){
        Dialog d=new Dialog(this);ScrollView sv=new ScrollView(this);LinearLayout box=CortexUi.card(this,24);box.setPadding(dp(17),dp(17),dp(17),dp(17));sv.addView(box);
        TextView title=CortexUi.text(this,label==null||label.isEmpty()?"Council run #"+runId:label,20,CortexUi.TEXT);CortexUi.medium(title);box.addView(title);
        box.addView(CortexUi.text(this,(domain==null?"":domain)+" · run #"+runId,10,CortexUi.MUTED));
        SQLiteDatabase r=db.getReadableDatabase();
        Cursor p=r.rawQuery("SELECT role,model_name,output_text,duration_ms,tokens,tokens_per_second FROM discovery_v3_council_passes WHERE run_id=? ORDER BY id ASC",new String[]{String.valueOf(runId)});
        int i=0;while(p.moveToNext()){
            String role=s(p,0),model=s(p,1),output=s(p,2);long ms=p.getLong(3);int tokens=p.getInt(4);double tps=p.getDouble(5);
            box.addView(CortexUi.section(this,role.replace('_',' ').toUpperCase(Locale.ROOT)));
            TextView m=CortexUi.plain(this,model+" · "+(ms/1000f)+"s · "+tokens+" tokens · "+String.format(Locale.US,"%.2f",tps)+" tok/s",9,CortexUi.FAINT);box.addView(m);
            TextView o=CortexUi.text(this,output,12,CortexUi.TEXT);o.setTextIsSelectable(true);o.setPadding(0,dp(5),0,dp(8));box.addView(o);i++;
        }p.close();
        if(i==0)box.addView(CortexUi.text(this,"No model pass has completed yet.",12,CortexUi.MUTED));
        TextView close=CortexUi.action(this,"Close",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(10),0,0);box.addView(close,cp);close.setOnClickListener(v->d.dismiss());
        d.setContentView(sv);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.96f),(int)(getResources().getDisplayMetrics().heightPixels*.88f));
    }

    String finalDecision(SQLiteDatabase r,long id){
        Cursor c=r.rawQuery("SELECT final_output FROM discovery_v3_council_runs WHERE id=?",new String[]{String.valueOf(id)});String x=c.moveToFirst()?s(c,0):"";c.close();return x;
    }
    LinearLayout.LayoutParams lp(int l,int t,int rr,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(rr),dp(b));return p;}
    String stamp(long t){return t<=0?"—":new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(t));}
    String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n)+"…";}
    String safe(String s){return s==null?"":s;}
    String etaText(long sec){long h=sec/3600,m=(sec%3600)/60,s=sec%60;if(h>0)return h+"h "+m+"m";if(m>0)return m+"m "+s+"s";return s+"s";}
}
