package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Intentional capture stream: save first, understand and connect asynchronously. */
public final class InboxActivity extends Activity {
    private VaultDb db;private LinearLayout list;private EditText composer;

    @Override protected void onCreate(Bundle state){super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(this);build();render();focusComposerIfRequested(getIntent());}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);focusComposerIfRequested(intent);}
    @Override protected void onDestroy(){super.onDestroy();try{db.close();}catch(Throwable ignored){}}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(CortexUi.aurora(this));root.setPadding(CortexUi.dp(this,18),CortexUi.dp(this,10),CortexUi.dp(this,18),0);
        TextView eyebrow=CortexUi.plain(this,"PERSONAL INTAKE",10,CortexUi.BRAND);CortexUi.medium(eyebrow);if(android.os.Build.VERSION.SDK_INT>=21)eyebrow.setLetterSpacing(.13f);root.addView(eyebrow,new LinearLayout.LayoutParams(-1,CortexUi.dp(this,26)));
        TextView title=CortexUi.plain(this,"Inbox",34,CortexUi.TEXT);CortexUi.bold(title);root.addView(title,new LinearLayout.LayoutParams(-1,CortexUi.dp(this,48)));
        TextView sub=CortexUi.text(this,"Throw anything at Cortex. It keeps the original, understands it, and quietly connects it to the rest of your world.",12,CortexUi.MUTED);root.addView(sub,new LinearLayout.LayoutParams(-1,CortexUi.dp(this,54)));
        LinearLayout mode=CortexUi.card(this,22);mode.setOrientation(LinearLayout.HORIZONTAL);mode.setGravity(Gravity.CENTER_VERTICAL);mode.setPadding(CortexUi.dp(this,13),CortexUi.dp(this,11),CortexUi.dp(this,13),CortexUi.dp(this,11));TextView orb=CortexUi.plain(this,"●",21,CortexUi.BRAND);mode.addView(orb,new LinearLayout.LayoutParams(CortexUi.dp(this,36),CortexUi.dp(this,36)));LinearLayout mt=new LinearLayout(this);mt.setOrientation(LinearLayout.VERTICAL);TextView mh=CortexUi.plain(this,"Save first. Think second.",14,CortexUi.TEXT);CortexUi.medium(mh);mt.addView(mh);TextView mb=CortexUi.plain(this,"Nothing gets lost because AI or a link fetch failed.",10,CortexUi.MUTED);mb.setPadding(0,CortexUi.dp(this,3),0,0);mt.addView(mb);mode.addView(mt,new LinearLayout.LayoutParams(0,-2,1));TextView safe=CortexUi.chip(this,"SAFE",CortexUi.GREEN,true);mode.addView(safe,new LinearLayout.LayoutParams(-2,CortexUi.dp(this,29)));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.setMargins(0,CortexUi.dp(this,6),0,CortexUi.dp(this,12));root.addView(mode,mp);
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,CortexUi.dp(this,4),0,CortexUi.dp(this,8));scroll.addView(list,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout sendShell=CortexUi.card(this,24);sendShell.setPadding(CortexUi.dp(this,7),CortexUi.dp(this,6),CortexUi.dp(this,6),CortexUi.dp(this,6));sendShell.setOrientation(LinearLayout.HORIZONTAL);sendShell.setGravity(Gravity.CENTER_VERTICAL);composer=new EditText(this);composer.setHint("Drop a thought, link, reminder, idea…");composer.setHintTextColor(CortexUi.FAINT);composer.setTextColor(CortexUi.TEXT);composer.setTextSize(14);composer.setSingleLine(false);composer.setMaxLines(3);composer.setBackgroundColor(Color.TRANSPARENT);composer.setPadding(CortexUi.dp(this,10),CortexUi.dp(this,10),CortexUi.dp(this,10),CortexUi.dp(this,10));sendShell.addView(composer,new LinearLayout.LayoutParams(0,CortexUi.dp(this,54),1));TextView send=CortexUi.action(this,"Send",CortexUi.BRAND,true);sendShell.addView(send,new LinearLayout.LayoutParams(CortexUi.dp(this,74),CortexUi.dp(this,46)));send.setOnClickListener(v->saveComposer());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,CortexUi.dp(this,6),0,CortexUi.dp(this,5));root.addView(sendShell,cp);
        CortexUi.addBottomNav(this,root,"inbox",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void focusComposerIfRequested(Intent i){if(i==null||!i.getBooleanExtra("focus_composer",false)||composer==null)return;composer.postDelayed(()->{composer.requestFocus();try{InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(imm!=null)imm.showSoftInput(composer,InputMethodManager.SHOW_IMPLICIT);}catch(Throwable ignored){}},180);}
    private void saveComposer(){String text=composer.getText()==null?"":composer.getText().toString().trim();if(text.isEmpty())return;long id=InboxStore.addNote(db,text);if(id>0){composer.setText("");AnalysisQueue.kick(this,null,this::render);render();}}

    private void render(){if(list==null)return;runOnUiThread(()->{list.removeAllViews();ArrayList<KnowledgeItem> items=InboxStore.recent(db,120);if(items.isEmpty()){LinearLayout e=CortexUi.card(this,24);TextView h=CortexUi.plain(this,"Nothing waiting in the stream",19,CortexUi.TEXT);CortexUi.medium(h);e.addView(h);TextView b=CortexUi.text(this,"Share from any app or type below. Cortex will keep the capture intact and do the filing for you.",12,CortexUi.MUTED);b.setPadding(0,CortexUi.dp(this,7),0,0);e.addView(b);list.addView(e,params());return;}for(KnowledgeItem k:items)list.addView(card(k),params());});}

    private View card(KnowledgeItem k){LinearLayout c=CortexUi.card(this,22);String state=InboxStore.processingState(k);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(this);int sc=stateColor(state);dot.setBackground(CortexUi.round(this,sc,Color.TRANSPARENT,999));top.addView(dot,new LinearLayout.LayoutParams(CortexUi.dp(this,8),CortexUi.dp(this,8)));String heading=k.title==null||k.title.trim().isEmpty()?friendlyType(k.type):k.title;TextView h=CortexUi.plain(this,heading,15,CortexUi.TEXT);CortexUi.medium(h);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(CortexUi.dp(this,10),0,0,0);top.addView(h,hp);TextView stateChip=CortexUi.chip(this,shortState(state),sc,false);top.addView(stateChip,new LinearLayout.LayoutParams(-2,CortexUi.dp(this,28)));c.addView(top);String body=bestText(k);if(!body.isEmpty()){TextView b=CortexUi.text(this,trim(body,300),12,CortexUi.MUTED);b.setPadding(0,CortexUi.dp(this,9),0,0);c.addView(b);}LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);String time=DateFormat.format("h:mm a",k.createdAt).toString();TextView m=CortexUi.plain(this,time+"  ·  "+friendlyState(state),10,CortexUi.MUTED);meta.addView(m,new LinearLayout.LayoutParams(0,CortexUi.dp(this,30),1));if("fetch_failed".equals(state)){TextView retry=CortexUi.chip(this,"Retry",CortexUi.ORANGE,false);retry.setOnClickListener(v->{try{SharedLinkIntelligence.enrichAsync(this,db,k.id,k.rawText);render();}catch(Throwable ignored){}});meta.addView(retry,new LinearLayout.LayoutParams(-2,CortexUi.dp(this,28)));}c.addView(meta);return c;}

    private LinearLayout.LayoutParams params(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,CortexUi.dp(this,10));return p;}
    private String friendlyType(String s){if(s==null)return"Saved item";String x=s.toLowerCase().replace('_',' ');return x.isEmpty()?"Saved item":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private String friendlyState(String s){if("understood".equals(s)||"analyzed".equals(s))return"Understood and ready to connect";if("fetch_failed".equals(s))return"Content unavailable · original preserved";if("analysis_failed".equals(s))return"Understanding failed · original preserved";if("pending_content".equals(s)||"queued".equals(s)||"analyzing".equals(s))return"Cortex is understanding this";return"Captured safely";}
    private String shortState(String s){if("understood".equals(s)||"analyzed".equals(s))return"READY";if("fetch_failed".equals(s)||"analysis_failed".equals(s))return"PRESERVED";if("pending_content".equals(s)||"queued".equals(s)||"analyzing".equals(s))return"THINKING";return"SAVED";}
    private String bestText(KnowledgeItem k){if(k.summary!=null&&!k.summary.trim().isEmpty())return k.summary.trim();if(k.extractedText!=null&&!k.extractedText.trim().isEmpty())return k.extractedText.trim();return k.rawText==null?"":k.rawText.trim();}
    private int stateColor(String s){if("understood".equals(s)||"analyzed".equals(s))return CortexUi.GREEN;if("fetch_failed".equals(s)||"analysis_failed".equals(s))return CortexUi.ORANGE;if("pending_content".equals(s)||"queued".equals(s)||"analyzing".equals(s))return CortexUi.BRAND;return CortexUi.AURORA;}
    private String trim(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}
}
