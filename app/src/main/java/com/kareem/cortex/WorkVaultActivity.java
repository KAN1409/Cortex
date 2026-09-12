package com.kareem.cortex;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.documentfile.provider.DocumentFile;
import java.text.DecimalFormat;
import java.util.ArrayList;

/** Work intelligence home: grounded professional memory, archive evidence and document creation. */
public final class WorkVaultActivity extends Activity {
    private static final int REQ_TREE=8601;
    private VaultDb db;
    private LinearLayout content;
    private volatile boolean destroyed=false;

    int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());WorkVaultIndexSchema.ensure(db.getWritableDatabase());build();refresh();
    }

    @Override protected void onResume(){super.onResume();if(!destroyed)refresh();}
    @Override protected void onDestroy(){destroyed=true;if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(28));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"work",null);setContentView(root);CortexUi.fitSystemBars(this,root);
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
        content.addView(topBar());

        long waiting=counts.newFiles+counts.modifiedFiles;
        int attention=0;for(WorkProcurementCaseEngine.Case x:cases)if(x.needsAttention())attention++;
        content.addView(workHero(counts,waiting,attention),margins(0,5,0,0));
        content.addView(primaryActions(),margins(0,11,0,0));

        TextView add=CortexUi.action(this,"Add archive source",CortexUi.MUTED,false);add.setOnClickListener(v->chooseTree());content.addView(add,marginsHeight(0,8,0,0,44));

        if(attention>0){
            content.addView(sectionHeader("Needs attention",attention,CortexUi.YELLOW));
            int shown=0;for(WorkProcurementCaseEngine.Case x:cases){if(!x.needsAttention())continue;procurementCaseRow(x);if(++shown>=5)break;}
            TextView note=CortexUi.text(this,"Evidence gap means Cortex could not find that step in the indexed archive. It does not claim the real-world step never happened.",10,CortexUi.FAINT);note.setPadding(dp(4),dp(2),dp(4),dp(6));content.addView(note);
        }

        content.addView(sectionHeader("Archive sources",sources.size(),CortexUi.OLIVE));
        if(sources.isEmpty()){
            LinearLayout empty=CortexUi.card(this,22);empty.setPadding(dp(18),dp(20),dp(18),dp(20));
            TextView eye=CortexUi.eyebrow(this,"Start here",CortexUi.LIME);empty.addView(eye);
            TextView h=CortexUi.plain(this,"Connect your work archive",19,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(8),0,0);empty.addView(h);
            TextView body=CortexUi.text(this,"Choose a project or archive folder. Cortex keeps a persistent reference, indexes it in place and preserves exact provenance without copying the originals.",12,CortexUi.MUTED);body.setPadding(0,dp(6),0,dp(12));empty.addView(body);
            TextView action=CortexUi.action(this,"Choose archive folder",CortexUi.LIME,true);action.setOnClickListener(v->chooseTree());empty.addView(action,new LinearLayout.LayoutParams(-1,dp(44)));content.addView(empty);return;
        }
        for(WorkVaultSourceStore.Source s:sources)sourceRow(s);
    }

    private LinearLayout topBar(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(8),dp(1),dp(10));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);row.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(CortexUi.eyebrow(this,"Cortex · Professional memory",CortexUi.LIME));
        TextView title=CortexUi.plain(this,"Work",32,CortexUi.TEXT);CortexUi.medium(title);title.setPadding(0,dp(1),0,0);titles.addView(title);
        TextView ask=CortexUi.chip(this,"Ask archive",CortexUi.LIME,true);ask.setOnClickListener(v->startActivity(new Intent(this,WorkVaultAskActivity.class)));row.addView(ask,new LinearLayout.LayoutParams(-2,dp(36)));
        return row;
    }

    private LinearLayout workHero(WorkVaultScanner.Counts counts,long waiting,int attention){
        LinearLayout card=CortexUi.card(this,26);card.setPadding(dp(18),dp(17),dp(18),dp(16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView eye=CortexUi.eyebrow(this,"Work intelligence",CortexUi.LIME);top.addView(eye,new LinearLayout.LayoutParams(0,-2,1));
        String state=waiting>0?waiting+" indexing":(attention>0?attention+" review":"ready");int stateColor=waiting>0?CortexUi.ORANGE:(attention>0?CortexUi.YELLOW:CortexUi.GREEN);TextView chip=CortexUi.chip(this,state,stateColor,true);top.addView(chip,new LinearLayout.LayoutParams(-2,dp(30)));card.addView(top);
        TextView h=CortexUi.plain(this,counts.projects>0?counts.projects+" projects in working memory":"Your professional memory",23,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(12),0,0);card.addView(h);
        TextView body=CortexUi.text(this,"Projects, procurement, prices, follow-up and source evidence — connected without losing where each fact came from.",12,CortexUi.MUTED);body.setPadding(0,dp(6),0,dp(14));card.addView(body);

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(CortexUi.metric(this,String.valueOf(counts.files),"FILES",counts.files>0?CortexUi.LIME:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(68),1));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(68),1);mp.setMargins(dp(7),0,dp(7),0);metrics.addView(CortexUi.metric(this,String.valueOf(counts.prices),"PRICES",counts.prices>0?CortexUi.GREEN:CortexUi.FAINT),mp);
        metrics.addView(CortexUi.metric(this,String.valueOf(counts.openFollowUps),"OPEN",counts.openFollowUps>0?CortexUi.YELLOW:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(68),1));card.addView(metrics);

        TextView meta=CortexUi.plain(this,humanBytes(counts.bytes)+" referenced · originals stay in place"+(counts.needsOcrFiles>0?" · "+counts.needsOcrFiles+" OCR unresolved":""),9,CortexUi.FAINT);meta.setPadding(0,dp(11),0,0);card.addView(meta);return card;
    }

    private LinearLayout primaryActions(){
        LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        TextView ask=CortexUi.action(this,"Ask archive",CortexUi.LIME,true);ask.setOnClickListener(v->startActivity(new Intent(this,WorkVaultAskActivity.class)));row1.addView(ask,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView follow=CortexUi.action(this,"Follow-up",CortexUi.YELLOW,false);follow.setOnClickListener(v->startActivity(new Intent(this,WorkFollowUpActivity.class)));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(46),1);fp.setMargins(dp(8),0,0,0);row1.addView(follow,fp);group.addView(row1);
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);row2.setPadding(0,dp(8),0,0);
        TextView create=CortexUi.action(this,"Create from archive",CortexUi.LIME,false);create.setOnClickListener(v->chooseDocumentToCreate());row2.addView(create,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView chat=CortexUi.action(this,"Build with ChatGPT",CortexUi.MUTED,false);chat.setOnClickListener(v->chooseDirectChatGptDocument());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1);cp.setMargins(dp(8),0,0,0);row2.addView(chat,cp);group.addView(row2);return group;
    }

    private LinearLayout sectionHeader(String label,int count,int color){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(24),dp(1),dp(10));View dot=new View(this);dot.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView h=CortexUi.plain(this,label,12,CortexUi.TEXT);CortexUi.medium(h);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(dp(9),0,0,0);row.addView(h,hp);TextView n=CortexUi.plain(this,String.valueOf(count),10,CortexUi.MUTED);row.addView(n);return row;
    }

    private WorkDocumentRecipe.Kind[] documentKinds(){return new WorkDocumentRecipe.Kind[]{
            WorkDocumentRecipe.Kind.ASSIGNMENT_ORDER_MANUFACTURING_ONLY,
            WorkDocumentRecipe.Kind.ASSIGNMENT_ORDER_MANUFACTURING_AND_SUPPLY,
            WorkDocumentRecipe.Kind.SUPPLY_ORDER_SUPPLY_ONLY,
            WorkDocumentRecipe.Kind.COMMERCIAL_COMPARISON,
            WorkDocumentRecipe.Kind.FOLLOW_UP_REPORT,
            WorkDocumentRecipe.Kind.PRICE_COMPARISON,
            WorkDocumentRecipe.Kind.PROJECT_STATUS_REPORT,
            WorkDocumentRecipe.Kind.OWNER_PRESENTATION};}

    private String[] labels(WorkDocumentRecipe.Kind[] kinds){String[] labels=new String[kinds.length];for(int i=0;i<kinds.length;i++)labels[i]=WorkDocumentRecipe.displayName(kinds[i]);return labels;}

    private void chooseDocumentToCreate(){WorkDocumentRecipe.Kind[] kinds=documentKinds();new AlertDialog.Builder(this).setTitle("Create from Archive").setItems(labels(kinds),(d,which)->askProjectAndBuild(kinds[which])).setNegativeButton("Cancel",null).show();}

    private void chooseDirectChatGptDocument(){
        WorkDocumentRecipe.Kind[] kinds=documentKinds();
        new AlertDialog.Builder(this).setTitle("Build with ChatGPT").setMessage("Choose the new document type. On the next screen you can attach reference Word, PDF, Excel or PowerPoint files and describe exactly what you need.")
                .setItems(labels(kinds),(d,which)->{Intent i=new Intent(this,WorkChatGptBuildActivity.class);i.putExtra(WorkChatGptBuildActivity.EXTRA_KIND,kinds[which].name());startActivity(i);}).setNegativeButton("Cancel",null).show();
    }

    private void askProjectAndBuild(WorkDocumentRecipe.Kind kind){
        EditText input=new EditText(this);input.setHint("Project name — optional");input.setSingleLine(true);input.setPadding(dp(18),dp(10),dp(18),dp(10));
        new AlertDialog.Builder(this).setTitle(WorkDocumentRecipe.displayName(kind)).setMessage("Optional project filter. Leave blank to build from all matching grounded archive evidence.").setView(input)
                .setPositiveButton("Create",(d,w)->buildAndShare(kind,input.getText()==null?"":input.getText().toString().trim())).setNegativeButton("Cancel",null).show();
    }

    private void buildAndShare(WorkDocumentRecipe.Kind kind,String project){
        android.widget.Toast.makeText(this,"Preparing grounded document…",android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                org.json.JSONObject payload=WorkDocumentBuildPackage.build(db,kind,project);WorkDocumentGenerationDecision.Decision decision=WorkDocumentGenerationDecision.decide(kind,payload);
                if(decision.route==WorkDocumentGenerationDecision.Route.LOCAL_GENERATION){
                    WorkLocalXlsxGenerator.Generated generated=WorkLocalXlsxGenerator.generate(this,kind,payload);WorkGeneratedDocumentRegistry.register(db.getWritableDatabase(),kind,"XLSX",project,"LOCAL_XLSX_GENERATOR",generated.file.getAbsolutePath(),generated.contentUri.toString());runOnUiThread(()->shareLocalDocument(generated,decision));return;
                }
                WorkDocumentBuilderBridge.Prepared prepared=WorkDocumentBuilderBridge.prepare(this,db,kind,project);
                runOnUiThread(()->{boolean opened=WorkDocumentBuilderBridge.openChatGpt(this,prepared);String msg=opened?"Sent to ChatGPT document builder":"Could not open ChatGPT/share target";android.widget.Toast.makeText(this,msg,opened?android.widget.Toast.LENGTH_SHORT:android.widget.Toast.LENGTH_LONG).show();});
            }catch(Throwable e){runOnUiThread(()->android.widget.Toast.makeText(this,"Could not create document",android.widget.Toast.LENGTH_LONG).show());}
        },"work-document-builder").start();
    }

    private void shareLocalDocument(WorkLocalXlsxGenerator.Generated generated,WorkDocumentGenerationDecision.Decision decision){
        try{Intent share=new Intent(Intent.ACTION_SEND);share.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");share.putExtra(Intent.EXTRA_STREAM,generated.contentUri);share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"Share generated Work Vault file"));android.widget.Toast.makeText(this,"Created locally • "+generated.rows+" grounded rows",android.widget.Toast.LENGTH_SHORT).show();}
        catch(Throwable e){android.widget.Toast.makeText(this,"File created, but share sheet could not open",android.widget.Toast.LENGTH_LONG).show();}
    }

    private void procurementCaseRow(WorkProcurementCaseEngine.Case x){
        LinearLayout card=CortexUi.card(this,22);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);String title=(x.project.isEmpty()?"":x.project+" · ")+"PR "+x.pr;TextView name=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(name);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView evidence=CortexUi.chip(this,"Evidence gap",CortexUi.YELLOW,true);top.addView(evidence,new LinearLayout.LayoutParams(-2,dp(29)));card.addView(top);
        TextView issue=CortexUi.text(this,x.headline(),12,CortexUi.YELLOW);issue.setPadding(0,dp(7),0,0);card.addView(issue);
        String stages="Quotation "+mark(x.quotation)+"   Comparison "+mark(x.comparison)+"   Approval "+mark(x.approval)+"   PO "+mark(x.po);TextView stage=CortexUi.text(this,stages,10,CortexUi.MUTED);stage.setPadding(0,dp(7),0,0);card.addView(stage);
        if(!x.statuses.isEmpty()){TextView status=CortexUi.text(this,"Follow-up · "+x.statuses.get(0),10,CortexUi.MUTED);status.setPadding(0,dp(5),0,0);card.addView(status);}content.addView(card,p);
    }

    private static String mark(boolean present){return present?"✓":"—";}

    private void sourceRow(WorkVaultSourceStore.Source s){
        LinearLayout card=CortexUi.card(this,22);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView name=CortexUi.text(this,s.displayName,15,CortexUi.TEXT);CortexUi.medium(name);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));String status=s.lastError.isEmpty()?(s.lastScanAt>0?"Ready":"Not scanned"):("Error");TextView badge=CortexUi.chip(this,status,s.lastError.isEmpty()?(s.lastScanAt>0?CortexUi.GREEN:CortexUi.MUTED):CortexUi.RED,false);top.addView(badge,new LinearLayout.LayoutParams(-2,dp(28)));card.addView(top);
        String detail=s.lastError.isEmpty()?(s.lastScanAt>0?"Indexed archive source · originals remain in place":"Ready to scan and index changes"):("Scan error · "+s.lastError);TextView meta=CortexUi.text(this,detail,11,s.lastError.isEmpty()?CortexUi.MUTED:CortexUi.RED);meta.setPadding(0,dp(6),0,0);card.addView(meta);
        TextView scan=CortexUi.action(this,"Scan + index changes",CortexUi.LIME,false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(42));bp.setMargins(0,dp(11),0,0);card.addView(scan,bp);scan.setOnClickListener(v->startBackgroundIndex(s,scan));content.addView(card,p);
    }

    private void chooseTree(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,REQ_TREE);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_TREE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{getContentResolver().takePersistableUriPermission(uri,flags);}catch(Throwable ignored){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignoredAgain){}}
        String name="Work archive";try{DocumentFile root=DocumentFile.fromTreeUri(this,uri);if(root!=null&&root.getName()!=null&&!root.getName().trim().isEmpty())name=root.getName().trim();}catch(Throwable ignored){}
        long sourceId=WorkVaultSourceStore.addOrTouch(db,uri,name);refresh();if(sourceId>0)startBackgroundIndex(new WorkVaultSourceStore.Source(sourceId,uri.toString(),name,"active",0,""),null);
    }

    private void startBackgroundIndex(WorkVaultSourceStore.Source source,TextView button){
        if(destroyed||source==null||source.id<=0)return;Intent i=new Intent(this,WorkVaultIndexService.class);i.setAction(WorkVaultIndexService.ACTION_START);i.putExtra(WorkVaultIndexService.EXTRA_SOURCE_ID,source.id);i.putExtra(WorkVaultIndexService.EXTRA_TREE_URI,source.treeUri);i.putExtra(WorkVaultIndexService.EXTRA_SOURCE_NAME,source.displayName);
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);if(button!=null){button.setText("Indexing in background");button.setEnabled(false);button.postDelayed(()->{if(!destroyed){button.setText("Scan + index changes");button.setEnabled(true);}},2500);}android.widget.Toast.makeText(this,"Work Vault indexing started in background",android.widget.Toast.LENGTH_SHORT).show();}
        catch(Throwable e){if(button!=null){button.setText("Scan + index changes");button.setEnabled(true);}android.widget.Toast.makeText(this,"Could not start archive indexing",android.widget.Toast.LENGTH_LONG).show();}
    }

    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private LinearLayout.LayoutParams marginsHeight(int l,int t,int r,int b,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h));p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private static String humanBytes(long bytes){if(bytes<1024)return bytes+" B";double v=bytes;String[] u={"KB","MB","GB","TB"};int i=-1;do{v/=1024;i++;}while(v>=1024&&i<u.length-1);return new DecimalFormat(v>=100?"0":(v>=10?"0.0":"0.00")).format(v)+" "+u[i];}
}
