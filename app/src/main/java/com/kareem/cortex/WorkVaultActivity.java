package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.documentfile.provider.DocumentFile;
import java.text.DecimalFormat;
import java.util.ArrayList;

/** Work Vault home: persistent archive sources, inventory state and grounded work-memory entry point. */
public final class WorkVaultActivity extends Activity {
    private static final int REQ_TREE=8601;
    private VaultDb db;
    private LinearLayout content;
    private volatile boolean destroyed=false;

    int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        CortexUi.applyWindow(this);
        db=new VaultDb(getApplicationContext());
        WorkVaultIndexSchema.ensure(db.getWritableDatabase());
        build();refresh();
    }

    @Override protected void onResume(){super.onResume();if(!destroyed)refresh();}
    @Override protected void onDestroy(){destroyed=true;if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(14),dp(20),dp(26));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"work",null);setContentView(root);
    }

    private void refresh(){
        if(destroyed||db==null)return;
        WorkVaultScanner.Counts counts=WorkVaultScanner.counts(db);
        ArrayList<WorkVaultSourceStore.Source> sources=WorkVaultSourceStore.active(db);
        ArrayList<WorkProcurementCaseEngine.Case> cases=WorkProcurementCaseEngine.load(db,6);
        render(counts,sources,cases);
    }

    private void render(WorkVaultScanner.Counts counts,ArrayList<WorkVaultSourceStore.Source> sources,ArrayList<WorkProcurementCaseEngine.Case> cases){
        if(destroyed||content==null)return;content.removeAllViews();
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);
        TextView title=CortexUi.plain(this,"Work Vault",31,CortexUi.TEXT);CortexUi.medium(title);head.addView(title);
        TextView sub=CortexUi.text(this,"Your professional archive, indexed with source-level provenance.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);head.addView(sub);content.addView(head);

        LinearLayout stats=CortexUi.card(this,22);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(16),0,0);
        TextView stat=CortexUi.plain(this,counts.files+" files  •  "+counts.projects+" projects  •  "+counts.prices+" prices",16,CortexUi.TEXT);CortexUi.medium(stat);stats.addView(stat);
        TextView follow=CortexUi.text(this,counts.followUps+" follow-up rows  •  "+counts.openFollowUps+" potentially open",11,CortexUi.MUTED);follow.setPadding(0,dp(5),0,0);stats.addView(follow);
        TextView size=CortexUi.text(this,humanBytes(counts.bytes)+" referenced  •  originals stay in place",11,CortexUi.MUTED);size.setPadding(0,dp(4),0,0);stats.addView(size);
        long waiting=counts.newFiles+counts.modifiedFiles;
        if(waiting>0||counts.needsOcrFiles>0){StringBuilder p=new StringBuilder();if(waiting>0)p.append(waiting).append(" files waiting for parsing");if(counts.needsOcrFiles>0){if(p.length()>0)p.append("  •  ");p.append(counts.needsOcrFiles).append(" OCR unresolved");}TextView pending=CortexUi.plain(this,p.toString(),10,CortexUi.ACCENT);pending.setPadding(0,dp(7),0,0);stats.addView(pending);}content.addView(stats,sp);

        TextView add=CortexUi.action(this,"ADD ARCHIVE SOURCE",CortexUi.ACCENT,true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(48));ap.setMargins(0,dp(14),0,0);content.addView(add,ap);add.setOnClickListener(v->chooseTree());
        TextView ask=CortexUi.action(this,"ASK WORK ARCHIVE",CortexUi.MUTED,false);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(46));qp.setMargins(0,dp(8),0,0);content.addView(ask,qp);ask.setOnClickListener(v->startActivity(new Intent(this,WorkVaultAskActivity.class)));
        TextView followUp=CortexUi.action(this,"OPEN WORK FOLLOW-UP",CortexUi.MUTED,false);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(46));fp.setMargins(0,dp(8),0,0);content.addView(followUp,fp);followUp.setOnClickListener(v->startActivity(new Intent(this,WorkFollowUpActivity.class)));

        boolean hasAttention=false;for(WorkProcurementCaseEngine.Case x:cases)if(x.needsAttention()){hasAttention=true;break;}
        if(hasAttention){
            content.addView(CortexUi.section(this,"Needs Attention"));
            int shown=0;for(WorkProcurementCaseEngine.Case x:cases){if(!x.needsAttention())continue;procurementCaseRow(x);if(++shown>=5)break;}
            TextView note=CortexUi.text(this,"Missing-step notices mean Cortex could not find grounded evidence in the indexed archive. They do not prove the business step never happened.",10,CortexUi.MUTED);note.setPadding(dp(4),0,dp(4),dp(12));content.addView(note);
        }

        content.addView(CortexUi.section(this,"Archive sources"));
        if(sources.isEmpty()){
            LinearLayout empty=CortexUi.card(this,20);empty.addView(CortexUi.text(this,"No archive source yet. Add a project/archive folder; Cortex will keep a persistent SAF reference and inventory it without copying the originals.",12,CortexUi.MUTED));content.addView(empty);return;
        }
        for(WorkVaultSourceStore.Source s:sources)sourceRow(s);
    }

    private void procurementCaseRow(WorkProcurementCaseEngine.Case x){
        LinearLayout card=CortexUi.card(this,20);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));
        String title=(x.project.isEmpty()?"":x.project+"  •  ")+"PR "+x.pr;
        TextView name=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(name);card.addView(name);
        TextView issue=CortexUi.text(this,x.headline(),12,CortexUi.ACCENT);issue.setPadding(0,dp(5),0,0);card.addView(issue);
        String stages="Quotation "+mark(x.quotation)+"  •  Comparison "+mark(x.comparison)+"  •  Approval "+mark(x.approval)+"  •  PO "+mark(x.po);
        TextView stage=CortexUi.text(this,stages,10,CortexUi.MUTED);stage.setPadding(0,dp(6),0,0);card.addView(stage);
        if(!x.statuses.isEmpty()){TextView status=CortexUi.text(this,"Follow-up: "+x.statuses.get(0),10,CortexUi.MUTED);status.setPadding(0,dp(4),0,0);card.addView(status);}
        content.addView(card,p);
    }

    private static String mark(boolean present){return present?"✓":"—";}

    private void sourceRow(WorkVaultSourceStore.Source s){
        LinearLayout card=CortexUi.card(this,20);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));
        TextView name=CortexUi.text(this,s.displayName,15,CortexUi.TEXT);CortexUi.medium(name);card.addView(name);
        String status=s.lastError.isEmpty()?(s.lastScanAt>0?"Archive source ready":"Not scanned yet"):("Scan error: "+s.lastError);
        TextView meta=CortexUi.text(this,status,11,s.lastError.isEmpty()?CortexUi.MUTED:CortexUi.RED);meta.setPadding(0,dp(5),0,0);card.addView(meta);
        TextView scan=CortexUi.action(this,"SCAN + INDEX CHANGES",CortexUi.ACCENT,false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(42));bp.setMargins(0,dp(10),0,0);card.addView(scan,bp);
        scan.setOnClickListener(v->startBackgroundIndex(s,scan));content.addView(card,p);
    }

    private void chooseTree(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,REQ_TREE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_TREE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{getContentResolver().takePersistableUriPermission(uri,flags);}catch(Throwable ignored){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignoredAgain){}}
        String name="Work archive";try{DocumentFile root=DocumentFile.fromTreeUri(this,uri);if(root!=null&&root.getName()!=null&&!root.getName().trim().isEmpty())name=root.getName().trim();}catch(Throwable ignored){}
        long sourceId=WorkVaultSourceStore.addOrTouch(db,uri,name);refresh();
        if(sourceId>0)startBackgroundIndex(new WorkVaultSourceStore.Source(sourceId,uri.toString(),name,"active",0,""),null);
    }

    private void startBackgroundIndex(WorkVaultSourceStore.Source source,TextView button){
        if(destroyed||source==null||source.id<=0)return;
        Intent i=new Intent(this,WorkVaultIndexService.class);i.setAction(WorkVaultIndexService.ACTION_START);i.putExtra(WorkVaultIndexService.EXTRA_SOURCE_ID,source.id);i.putExtra(WorkVaultIndexService.EXTRA_TREE_URI,source.treeUri);i.putExtra(WorkVaultIndexService.EXTRA_SOURCE_NAME,source.displayName);
        try{
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            if(button!=null){button.setText("INDEXING IN BACKGROUND");button.setEnabled(false);button.postDelayed(()->{if(!destroyed){button.setText("SCAN + INDEX CHANGES");button.setEnabled(true);}},2500);}
            android.widget.Toast.makeText(this,"Work Vault indexing started in background",android.widget.Toast.LENGTH_SHORT).show();
        }catch(Throwable e){if(button!=null){button.setText("SCAN + INDEX CHANGES");button.setEnabled(true);}android.widget.Toast.makeText(this,"Could not start archive indexing",android.widget.Toast.LENGTH_LONG).show();}
    }

    private static String humanBytes(long bytes){if(bytes<1024)return bytes+" B";double v=bytes;String[] u={"KB","MB","GB","TB"};int i=-1;do{v/=1024;i++;}while(v>=1024&&i<u.length-1);return new DecimalFormat(v>=100?"0":(v>=10?"0.0":"0.00")).format(v)+" "+u[i];}
}
