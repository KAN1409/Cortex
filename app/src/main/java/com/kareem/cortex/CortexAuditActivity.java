package com.kareem.cortex;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** One exhaustive Cortex diagnostic with live progress and evidence. */
public class CortexAuditActivity extends Activity {
    VaultDb db;LinearLayout root,testsBox;TextView headline,current,counts,recent;ProgressBar progress;TextView start,export,stop;Handler h=new Handler(Looper.getMainLooper());String lastTests="";boolean destroyed=false;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);CortexAuditStore.ensure(db);build();refresh();}
    @Override protected void onResume(){super.onResume();h.post(tick);}
    @Override protected void onPause(){h.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;h.removeCallbacksAndMessages(null);if(db!=null)try{db.close();}catch(Throwable ignored){}db=null;super.onDestroy();}
    final Runnable tick=new Runnable(){@Override public void run(){if(!destroyed&&!isFinishing()){refresh();h.postDelayed(this,1000);}}};

    void build(){ScrollView sv=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(14),dp(18),dp(28));root.setBackgroundColor(CortexUi.BG);sv.addView(root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView title=CortexUi.plain(this,"Full Cortex Diagnostic",27,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);TextView sub=CortexUi.text(this,"One exhaustive test. Every safe path is checked; protected actions are reported instead of faked.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);tx.addView(sub);root.addView(head);

        LinearLayout hero=CortexUi.card(this,22);hero.setPadding(dp(15),dp(15),dp(15),dp(15));headline=CortexUi.plain(this,"Ready to test Cortex",19,CortexUi.TEXT);CortexUi.medium(headline);hero.addView(headline);current=CortexUi.text(this,"No diagnostic has been run yet.",11,CortexUi.MUTED);current.setPadding(0,dp(6),0,dp(10));hero.addView(current);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);hero.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));counts=CortexUi.plain(this,"",10,CortexUi.MUTED);counts.setPadding(0,dp(9),0,0);hero.addView(counts);root.addView(hero,lp(0,dp(10),0,0));

        start=CortexUi.action(this,"RUN FULL CORTEX DIAGNOSTIC",CortexUi.LIME,true);start.setOnClickListener(v->startAudit());root.addView(start,lp(0,dp(12),0,0));
        stop=CortexUi.action(this,"STOP CURRENT DIAGNOSTIC",CortexUi.RED,false);stop.setOnClickListener(v->stopAudit());root.addView(stop,lp(0,dp(8),0,0));
        export=CortexUi.action(this,"EXPORT COMPLETE DIAGNOSTIC DATA",CortexUi.MUTED,false);export.setOnClickListener(v->{if(db==null)return;export.setEnabled(false);export.setText("BUILDING DIAGNOSTIC PACKAGE…");DebugExporter.exportAndShare(this,db);h.postDelayed(()->{if(!destroyed){export.setEnabled(true);export.setText("EXPORT COMPLETE DIAGNOSTIC DATA");}},2500);});root.addView(export,lp(0,dp(8),0,0));

        root.addView(CortexUi.section(this,"Live diagnostic activity"));recent=CortexUi.text(this,"Waiting for a run.",11,CortexUi.MUTED);recent.setTextIsSelectable(true);LinearLayout events=CortexUi.card(this,18);events.addView(recent);root.addView(events);
        root.addView(CortexUi.section(this,"Every check"));testsBox=new LinearLayout(this);testsBox.setOrientation(LinearLayout.VERTICAL);root.addView(testsBox);setContentView(sv);CortexUi.fitSystemBars(this,root);}

    void startAudit(){if(db==null)return;start.setEnabled(false);start.setText("STARTING…");new Thread(()->{try{long id=CortexAuditScheduler.start(this);post(()->{Toast.makeText(this,"Full diagnostic started • run #"+id,Toast.LENGTH_LONG).show();refresh();});}catch(Throwable e){post(()->{start.setEnabled(true);start.setText("RUN FULL CORTEX DIAGNOSTIC");headline.setText("Could not start diagnostic");current.setText(safe(e.getMessage()));});}},"CortexAuditStart").start();}
    void stopAudit(){if(db==null)return;CortexAuditStore.Run r=CortexAuditStore.active(db);if(r==null)return;new AlertDialog.Builder(this).setTitle("Stop full diagnostic?").setMessage("Everything already tested stays in the diagnostic record.").setPositiveButton("Stop",(d,w)->{CortexAuditScheduler.stop(this,r.id);refresh();}).setNegativeButton("Keep running",null).show();}

    void refresh(){if(db==null||destroyed)return;CortexAuditStore.Run r=CortexAuditStore.latest(db);if(r==null){headline.setText("Ready to test Cortex");current.setText("This run checks app identity, permissions, capture, Vault, AI, integrations, background work, reliability and diagnostics.");counts.setText(CortexAuditStore.defs().size()+" checks registered");progress.setProgress(0);start.setEnabled(true);start.setText("RUN FULL CORTEX DIAGNOSTIC");stop.setVisibility(View.GONE);export.setEnabled(true);recent.setText("No diagnostic events yet.");renderTests(0);return;}
        int pc=r.progress();progress.setProgress(pc*10);headline.setText(statusHuman(r.status)+"  •  "+pc+"%");current.setText(empty(r.phase,"Diagnostic")+"\n"+empty(r.currentTest,"Waiting for next check")+(r.error.isEmpty()?"":"\nLast error: "+r.error));JSONObject sc=CortexAuditStore.statusCounts(db,r.id);counts.setText("PASS "+sc.optInt("pass",0)+"   •   WARN "+sc.optInt("warn",0)+"   •   FAIL "+sc.optInt("fail",0)+"   •   RUNNING "+sc.optInt("running",0)+"   •   NOT RUN "+sc.optInt("not_run",0));boolean active=r.active();start.setEnabled(!active);start.setText(active?"FULL DIAGNOSTIC IS RUNNING":"RUN FULL CORTEX DIAGNOSTIC AGAIN");stop.setVisibility(active?View.VISIBLE:View.GONE);export.setEnabled(true);recent.setText(events(r.id));renderTests(r.id);}

    void renderTests(long runId){JSONArray a=runId>0?CortexAuditStore.testsJson(db,runId):CortexAuditStore.catalogJson();String sig=a.toString();if(sig.equals(lastTests))return;lastTests=sig;testsBox.removeAllViews();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String status=runId>0?o.optString("status","pending"):o.optString("mode","");int color=statusColor(status);LinearLayout c=CortexUi.card(this,17);c.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView t=CortexUi.text(this,(i+1)+". "+o.optString("title",o.optString("test_key","Test")),12,CortexUi.TEXT);CortexUi.medium(t);top.addView(t,new LinearLayout.LayoutParams(0,-2,1));TextView chip=CortexUi.chip(this,status.toUpperCase(Locale.ROOT),color,true);top.addView(chip,new LinearLayout.LayoutParams(-2,dp(29)));c.addView(top);String detail=runId>0?o.optString("detail",""):o.optString("description","");if(!detail.isEmpty()){TextView d=CortexUi.text(this,detail,10,CortexUi.MUTED);d.setPadding(0,dp(5),0,0);d.setMaxLines(6);c.addView(d);}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(7));testsBox.addView(c,p);}}

    String events(long id){JSONArray a=CortexAuditStore.eventsJson(db,id,16);if(a.length()==0)return"Waiting for first event…";StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;long at=o.optLong("created_at",0);String tm=at>0?new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(at)):"";b.append(tm).append("  •  ").append(o.optString("component","")).append("  •  ").append(o.optString("detail",o.optString("event",""))).append('\n');}return b.toString().trim();}
    void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
    int statusColor(String s){if("pass".equals(s))return CortexUi.GREEN;if("warn".equals(s)||"observing".equals(s))return CortexUi.YELLOW;if("fail".equals(s))return CortexUi.RED;if("running".equals(s))return CortexUi.LIME;return CortexUi.MUTED;}
    String statusHuman(String s){if("complete".equals(s))return"DIAGNOSTIC COMPLETE";if("canceled".equals(s))return"DIAGNOSTIC STOPPED";if("finalizing".equals(s))return"FINALIZING";if("running".equals(s)||"starting".equals(s)||"soaking".equals(s))return"DIAGNOSTIC RUNNING";return safe(s).toUpperCase(Locale.ROOT);}String empty(String s,String f){return s==null||s.trim().isEmpty()?f:s.trim();}String safe(String s){return s==null?"":s;}LinearLayout.LayoutParams lp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
