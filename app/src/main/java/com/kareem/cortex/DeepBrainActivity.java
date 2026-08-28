package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-triggered Cortex -> ChatGPT Plus Deep Brain transport. */
public final class DeepBrainActivity extends Activity {
    static final int REQ_EXPORT_JSON=884;
    EditText question; TextView status,copy,share,export,paste; volatile boolean destroyed=false,busy=false;
    String pendingExportJson="",pendingExportName="";
    final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-deep-brain");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refresh();}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();super.onDestroy();}

    void build(){
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(14),dp(20),dp(28));root.setBackgroundColor(CortexUi.BG);scroll.addView(root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));TextView h=CortexUi.plain(this,"ChatGPT Deep Brain",27,CortexUi.TEXT);CortexUi.medium(h);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));root.addView(head);
        TextView intro=CortexUi.text(this,"Cortex prepares grounded context. Copy the compact packet, paste it into whichever ChatGPT conversation you want, then bring the structured response back to Cortex.",12,CortexUi.MUTED);intro.setPadding(0,dp(8),0,dp(14));root.addView(intro);
        TextView label=CortexUi.plain(this,"Question",11,CortexUi.MUTED);CortexUi.medium(label);root.addView(label);
        question=new EditText(this);question.setText("What needs my attention now, why, and what should I do next?");question.setTextColor(CortexUi.TEXT);question.setHintTextColor(CortexUi.FAINT);question.setTextSize(14);question.setMinLines(3);question.setMaxLines(7);question.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);question.setPadding(dp(14),dp(12),dp(14),dp(12));question.setBackground(CortexUi.round(this,CortexUi.SURFACE,android.graphics.Color.TRANSPARENT,18));root.addView(question,new LinearLayout.LayoutParams(-1,-2));

        copy=CortexUi.action(this,"Copy compact context",CortexUi.ACCENT,true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(50));cp.setMargins(0,dp(14),0,0);root.addView(copy,cp);copy.setOnClickListener(v->copyContext());
        share=CortexUi.action(this,"Open / share to ChatGPT",CortexUi.MUTED,false);LinearLayout.LayoutParams shp=new LinearLayout.LayoutParams(-1,dp(46));shp.setMargins(0,dp(8),0,0);root.addView(share,shp);share.setOnClickListener(v->send());
        export=CortexUi.action(this,"Export compact JSON file",CortexUi.MUTED,false);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(46));ep.setMargins(0,dp(8),0,0);root.addView(export,ep);export.setOnClickListener(v->exportJson());
        paste=CortexUi.action(this,"Paste / apply ChatGPT response",CortexUi.MUTED,false);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(48));pp.setMargins(0,dp(8),0,0);root.addView(paste,pp);paste.setOnClickListener(v->pasteDialog());
        TextView how=CortexUi.text(this,"Fast path: Copy compact context → open your existing ChatGPT conversation → Paste. File path: Export compact JSON → attach that file in the conversation. Return the CORTEX_RESPONSE_V1 result here by Share or Paste / apply. Cloud export respects Cortex Privacy controls.",11,CortexUi.MUTED);how.setPadding(0,dp(14),0,dp(12));root.addView(how);
        status=CortexUi.text(this,"",11,CortexUi.TEXT);status.setPadding(dp(12),dp(12),dp(12),dp(12));status.setBackground(CortexUi.round(this,CortexUi.SURFACE,android.graphics.Color.TRANSPARENT,16));root.addView(status);
        setContentView(scroll);CortexUi.fitSystemBars(this,scroll);
    }

    void copyContext(){
        if(busy||destroyed)return;String q=question.getText().toString().trim();if(q.isEmpty()){question.setError("Ask something first");return;}setBusy(true,"Building compact grounded context…");
        worker.execute(()->{VaultDb db=null;try{db=new VaultDb(getApplicationContext());CognitiveDeepBrainPacketBuilderV4.Packet p=CognitiveDeepBrainPacketBuilderV4.build(getApplicationContext(),db,q);CognitiveDeepBrainStoreV4.markExported(db,p.requestId);post(()->{try{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Cortex Deep Brain",p.compactText));setBusy(false,"Copied compact context · "+kb(p.compactText)+" · "+p.requestId);Toast.makeText(this,"Copied. Paste it into the ChatGPT conversation you want.",Toast.LENGTH_LONG).show();}catch(Throwable e){setBusy(false,"Could not copy context: "+safe(e.getMessage()));}});}catch(Throwable e){post(()->setBusy(false,"Could not build Deep Brain packet: "+safe(e.getMessage())));}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}});
    }

    void send(){
        if(busy||destroyed)return;String q=question.getText().toString().trim();if(q.isEmpty()){question.setError("Ask something first");return;}setBusy(true,"Building compact grounded context…");
        worker.execute(()->{VaultDb db=null;try{db=new VaultDb(getApplicationContext());CognitiveDeepBrainPacketBuilderV4.Packet p=CognitiveDeepBrainPacketBuilderV4.build(getApplicationContext(),db,q);CognitiveDeepBrainStoreV4.markExported(db,p.requestId);post(()->{setBusy(false,"Compact context ready · "+kb(p.compactText)+" · "+p.requestId);shareText(p.compactText);});}catch(Throwable e){post(()->setBusy(false,"Could not build Deep Brain packet: "+safe(e.getMessage())));}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}});
    }
    void shareText(String text){Intent base=new Intent(Intent.ACTION_SEND);base.setType("text/plain");base.putExtra(Intent.EXTRA_TEXT,text);base.putExtra(Intent.EXTRA_SUBJECT,"Cortex Deep Brain");try{Intent direct=new Intent(base);direct.setPackage("com.openai.chatgpt");startActivity(direct);}catch(Throwable noChatGpt){try{startActivity(Intent.createChooser(base,"Send Cortex context"));}catch(Throwable e){Toast.makeText(this,"No compatible share app found",Toast.LENGTH_LONG).show();}}}

    void exportJson(){
        if(busy||destroyed)return;String q=question.getText().toString().trim();if(q.isEmpty()){question.setError("Ask something first");return;}setBusy(true,"Building compact JSON…");
        worker.execute(()->{VaultDb db=null;try{db=new VaultDb(getApplicationContext());CognitiveDeepBrainPacketBuilderV4.Packet p=CognitiveDeepBrainPacketBuilderV4.build(getApplicationContext(),db,q);CognitiveDeepBrainStoreV4.markExported(db,p.requestId);post(()->{pendingExportJson=p.exportJson;String shortId=p.requestId.length()>12?p.requestId.substring(p.requestId.length()-12):p.requestId;pendingExportName="Cortex-DeepBrain-"+shortId+".json";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,pendingExportName);try{startActivityForResult(i,REQ_EXPORT_JSON);}catch(Throwable e){pendingExportJson="";setBusy(false,"Could not open file picker");}});}catch(Throwable e){post(()->setBusy(false,"Could not build Deep Brain JSON: "+safe(e.getMessage())));}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}});
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=REQ_EXPORT_JSON)return;if(result!=RESULT_OK||data==null||data.getData()==null){pendingExportJson="";setBusy(false,"JSON export cancelled");return;}Uri uri=data.getData();try(OutputStream os=getContentResolver().openOutputStream(uri)){if(os==null)throw new IllegalStateException("Could not open destination");os.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));os.flush();String name=pendingExportName;pendingExportJson="";pendingExportName="";setBusy(false,"JSON exported · "+name);Toast.makeText(this,"JSON saved. Attach it to the ChatGPT conversation you want.",Toast.LENGTH_LONG).show();}catch(Throwable e){pendingExportJson="";pendingExportName="";setBusy(false,"JSON export failed: "+safe(e.getMessage()));}}

    void pasteDialog(){if(busy)return;final EditText e=new EditText(this);e.setHint("Paste the full CORTEX_RESPONSE_V1 response");e.setMinLines(8);e.setMaxLines(16);e.setGravity(Gravity.TOP);e.setTextColor(CortexUi.TEXT);e.setHintTextColor(CortexUi.FAINT);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);new AlertDialog.Builder(this).setTitle("Apply ChatGPT response").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Validate & apply",(d,w)->apply(e.getText().toString())).show();}
    void apply(String raw){if(busy||raw==null||raw.trim().isEmpty())return;setBusy(true,"Validating ChatGPT response…");worker.execute(()->{VaultDb db=null;try{db=new VaultDb(getApplicationContext());CognitiveDeepBrainApplyV4.Result r=CognitiveDeepBrainApplyV4.apply(db,raw);post(()->{setBusy(false,r.human());if(!r.answer.isEmpty())new AlertDialog.Builder(this).setTitle("Deep Brain applied").setMessage(r.answer+"\n\n"+r.human()).setPositiveButton("Done",null).show();refresh();});}catch(Throwable e){post(()->setBusy(false,"Response rejected safely: "+safe(e.getMessage())));}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}});}
    void refresh(){worker.execute(()->{VaultDb db=null;try{db=new VaultDb(getApplicationContext());CognitiveDeepBrainStoreV4.Status s=CognitiveDeepBrainStoreV4.latest(db);String priorities=CognitiveDeepBrainStoreV4.latestPrioritySummary(db,8);post(()->{if(s==null){status.setText("No Deep Brain request yet.");return;}String when=new SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(new Date(s.createdAt));StringBuilder b=new StringBuilder();b.append("Latest · ").append(s.state).append(" · ").append(when).append("\n").append(s.question);if(s.answer!=null&&!s.answer.trim().isEmpty())b.append("\n\nLast answer:\n").append(s.answer);if(priorities!=null&&!priorities.trim().isEmpty())b.append("\n\nCurrent suggested priorities:\n").append(priorities);status.setText(b.toString());});}catch(Throwable e){post(()->status.setText("Deep Brain status unavailable."));}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}});}
    void setBusy(boolean on,String message){busy=on;if(copy!=null)copy.setEnabled(!on);if(share!=null)share.setEnabled(!on);if(export!=null)export.setEnabled(!on);if(paste!=null)paste.setEnabled(!on);if(status!=null&&message!=null&&!message.isEmpty())status.setText(message);}
    String kb(String s){double n=(s==null?0:s.getBytes(StandardCharsets.UTF_8).length)/1024.0;return String.format(Locale.US,"%.1f KB",n);}
    void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())try{r.run();}catch(Throwable ignored){}});}String safe(String s){return s==null||s.trim().isEmpty()?"unknown error":s.trim();}
}
