package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Read-only diagnostic surface for reconstructing how Context changed over time. */
public final class ContextDiagnosticsActivity extends Activity {
    VaultDb db;LinearLayout body;TextView summary,report;long windowMs=2L*60L*60L*1000L;ContextDiagnostics.Report latest;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){if(db!=null)try{db.close();}catch(Throwable ignored){}db=null;super.onDestroy();}

    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);sv.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(12),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);TextView title=CortexUi.plain(this,"Context replay",27,CortexUi.TEXT);CortexUi.medium(title);ht.addView(title);ht.addView(CortexUi.plain(this,"Read-only reconstruction from Context ledgers.",10,CortexUi.MUTED));head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        summary=CortexUi.text(this,"",11,CortexUi.MUTED);summary.setPadding(dp(2),dp(12),dp(2),dp(10));body.addView(summary);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);TextView two=CortexUi.action(this,"Last 2h",CortexUi.GREEN,false);TextView day=CortexUi.action(this,"Last 24h",CortexUi.ORANGE,false);controls.addView(two,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(42),1);dlp.setMargins(dp(8),0,0,0);controls.addView(day,dlp);body.addView(controls);two.setOnClickListener(v->{windowMs=2L*60L*60L*1000L;refresh();});day.setOnClickListener(v->{windowMs=24L*60L*60L*1000L;refresh();});

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(8),0,0);TextView current=CortexUi.action(this,"Current Context",CortexUi.YELLOW,false);TextView copy=CortexUi.action(this,"Copy replay",CortexUi.MUTED,false);actions.addView(current,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(42),1);clp.setMargins(dp(8),0,0,0);actions.addView(copy,clp);body.addView(actions);current.setOnClickListener(v->{try{startActivity(new Intent(this,ContextNowActivity.class));}catch(Throwable ignored){}});copy.setOnClickListener(v->copy());

        LinearLayout card=CortexUi.card(this,18);card.setPadding(dp(13),dp(13),dp(13),dp(13));report=CortexUi.text(this,"",11,CortexUi.TEXT);report.setTextIsSelectable(true);card.addView(report);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(12),0,0);body.addView(card,cp);setContentView(root);CortexUi.fitSystemBars(this,root);}

    void refresh(){if(db==null||report==null)return;latest=ContextDiagnostics.build(db,windowMs,70);summary.setText("READ ONLY · "+latest.stackCount+" stack · "+latest.episodeCount+" episodes · "+latest.snapshotCount+" snapshots · "+latest.feedbackCount+" corrections · "+latest.evidenceCount+" primary evidence links");report.setText(latest.text);}
    void copy(){if(latest==null)return;try{ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("Cortex Context replay",latest.text));Toast.makeText(this,"Context replay copied",Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}}
}
