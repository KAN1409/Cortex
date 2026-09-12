package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Grounded Work Vault search surface. Brain receives only retrieved archive evidence. */
public final class WorkVaultAskActivity extends Activity {
    private VaultDb db;private LinearLayout results;private EditText query;private TextView brain;private ArrayList<WorkVaultSearch.Hit> last=new ArrayList<>();private final ExecutorService io=Executors.newSingleThreadExecutor();private volatile boolean dead=false;
    int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());WorkVaultIndexSchema.ensure(db.getWritableDatabase());build();}
    @Override protected void onDestroy(){dead=true;io.shutdownNow();if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.setPadding(dp(20),dp(14),dp(20),dp(18));
        TextView title=CortexUi.plain(this,"Ask Work Archive",29,CortexUi.TEXT);CortexUi.medium(title);root.addView(title);
        TextView sub=CortexUi.text(this,"Grounded search across files, sheets, pages, slides, PRs, POs and indexed prices.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,dp(12));root.addView(sub);

        query=new EditText(this);query.setSingleLine(false);query.setMinLines(2);query.setMaxLines(4);query.setTextColor(CortexUi.TEXT);query.setHintTextColor(CortexUi.FAINT);query.setHint("e.g. Compare Galala marble prices in Negma");query.setTextSize(14);query.setPadding(dp(14),dp(12),dp(14),dp(12));query.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,16));root.addView(query,new LinearLayout.LayoutParams(-1,-2));

        String pre=getIntent().getStringExtra("query");if(pre!=null)query.setText(pre);
        TextView search=CortexUi.action(this,"SEARCH ARCHIVE",CortexUi.ACCENT,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(46));sp.setMargins(0,dp(10),0,0);root.addView(search,sp);search.setOnClickListener(v->search());

        brain=CortexUi.action(this,"ASK BRAIN WITH THESE SOURCES",CortexUi.MUTED,false);brain.setEnabled(false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(44));bp.setMargins(0,dp(8),0,0);root.addView(brain,bp);brain.setOnClickListener(v->askBrain());

        ScrollView sv=new ScrollView(this);results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);results.setPadding(0,dp(8),0,dp(24));sv.addView(results);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));CortexUi.fitSystemBars(this,root);setContentView(root);
        if(pre!=null&&!pre.trim().isEmpty())search();
    }

    private void search(){
        String q=query.getText()==null?"":query.getText().toString().trim();if(q.isEmpty())return;results.removeAllViews();TextView loading=CortexUi.text(this,"Searching grounded archive evidence…",12,CortexUi.MUTED);results.addView(loading);brain.setEnabled(false);
        io.execute(()->{ArrayList<WorkVaultSearch.Hit> hits=WorkVaultSearch.search(db,q,40);runOnUiThread(()->{if(dead)return;last=hits;render(hits);brain.setEnabled(!hits.isEmpty());});});
    }

    private void render(ArrayList<WorkVaultSearch.Hit> hits){
        results.removeAllViews();if(hits.isEmpty()){TextView e=CortexUi.text(this,"No grounded Work Vault result found yet.",12,CortexUi.MUTED);e.setPadding(0,dp(16),0,0);results.addView(e);return;}
        TextView count=CortexUi.plain(this,hits.size()+" grounded results",10,CortexUi.MUTED);count.setPadding(0,dp(6),0,dp(4));results.addView(count);
        for(WorkVaultSearch.Hit h:hits)row(h);
    }

    private void row(WorkVaultSearch.Hit h){
        LinearLayout card=CortexUi.card(this,18);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(8),0,0);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView name=CortexUi.text(this,h.fileName,14,CortexUi.TEXT);CortexUi.medium(name);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView badge=CortexUi.chip(this,h.kind.isEmpty()?"EVIDENCE":h.kind,CortexUi.semanticFor(h.kind),false);top.addView(badge,new LinearLayout.LayoutParams(-2,dp(28)));card.addView(top);
        if(!h.location().isEmpty()){TextView loc=CortexUi.plain(this,h.location(),10,CortexUi.ACCENT);loc.setPadding(0,dp(5),0,0);card.addView(loc);}
        TextView body=CortexUi.text(this,h.snippet,12,CortexUi.MUTED);body.setPadding(0,dp(6),0,0);body.setMaxLines(8);card.addView(body);
        TextView open=CortexUi.action(this,"OPEN SOURCE FILE",CortexUi.MUTED,false);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(40));op.setMargins(0,dp(9),0,0);card.addView(open,op);open.setOnClickListener(v->openSource(h));
        results.addView(card,cp);
    }

    private void openSource(WorkVaultSearch.Hit h){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setData(Uri.parse(h.documentUri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Throwable e){android.widget.Toast.makeText(this,"Could not open source file",android.widget.Toast.LENGTH_SHORT).show();}}
    private void askBrain(){String q=query.getText()==null?"":query.getText().toString().trim();if(last.isEmpty()||q.isEmpty())return;String context=WorkVaultSearch.groundedContext(q,last,12000);CortexActionExecutor.openBrain(this,0,"Answer my Work Vault question using only the grounded archive evidence below. Preserve source provenance and cite file/location for concrete claims.\n\n"+context);}
}
