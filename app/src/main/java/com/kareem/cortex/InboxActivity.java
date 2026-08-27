package com.kareem.cortex;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** MeMessage-style intentional capture surface backed by Cortex canonical evidence. */
public final class InboxActivity extends Activity {
    private VaultDb db;private LinearLayout list;private EditText composer;

    @Override protected void onCreate(Bundle state){super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(this);build();render();}
    @Override protected void onDestroy(){super.onDestroy();try{db.close();}catch(Throwable ignored){}}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.setPadding(CortexUi.dp(this,16),CortexUi.dp(this,10),CortexUi.dp(this,16),0);
        TextView title=CortexUi.plain(this,"INBOX",22,CortexUi.TEXT);CortexUi.bold(title);root.addView(title,new LinearLayout.LayoutParams(-1,CortexUi.dp(this,42)));
        TextView sub=CortexUi.plain(this,"Send anything here. Cortex saves first, understands second.",11,CortexUi.MUTED);root.addView(sub,new LinearLayout.LayoutParams(-1,CortexUi.dp(this,38)));
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout sendRow=new LinearLayout(this);sendRow.setGravity(Gravity.CENTER_VERTICAL);sendRow.setPadding(0,CortexUi.dp(this,8),0,CortexUi.dp(this,8));composer=new EditText(this);composer.setHint("Message yourself…");composer.setHintTextColor(CortexUi.FAINT);composer.setTextColor(CortexUi.TEXT);composer.setTextSize(14);composer.setSingleLine(false);composer.setMaxLines(3);composer.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,20));composer.setPadding(CortexUi.dp(this,14),CortexUi.dp(this,10),CortexUi.dp(this,14),CortexUi.dp(this,10));sendRow.addView(composer,new LinearLayout.LayoutParams(0,CortexUi.dp(this,54),1));TextView send=CortexUi.action(this,"SEND",CortexUi.RED,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(CortexUi.dp(this,72),CortexUi.dp(this,46));sp.setMargins(CortexUi.dp(this,8),0,0,0);sendRow.addView(send,sp);send.setOnClickListener(v->saveComposer());root.addView(sendRow);
        CortexUi.addBottomNav(this,root,"inbox",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void saveComposer(){String text=composer.getText()==null?"":composer.getText().toString().trim();if(text.isEmpty())return;long id=InboxStore.addNote(db,text);if(id>0){composer.setText("");AnalysisQueue.kick(this,null,this::render);render();}}

    private void render(){if(list==null)return;runOnUiThread(()->{list.removeAllViews();ArrayList<KnowledgeItem> items=InboxStore.recent(db,120);if(items.isEmpty()){TextView e=CortexUi.plain(this,"Nothing here yet. Share a link, image, file, voice note, or type a message below.",13,CortexUi.MUTED);e.setPadding(0,CortexUi.dp(this,30),0,0);list.addView(e);return;}for(KnowledgeItem k:items)list.addView(card(k),params());});}

    private View card(KnowledgeItem k){LinearLayout c=CortexUi.card(this,22);String state=InboxStore.processingState(k);String heading=k.title==null||k.title.trim().isEmpty()?k.type:k.title;TextView h=CortexUi.plain(this,heading,15,CortexUi.TEXT);CortexUi.medium(h);c.addView(h);String body=bestText(k);if(!body.isEmpty()){TextView b=CortexUi.text(this,trim(body,360),12,CortexUi.MUTED);b.setPadding(0,CortexUi.dp(this,7),0,0);c.addView(b);}LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);String time=DateFormat.format("h:mm a",k.createdAt).toString();TextView m=CortexUi.plain(this,time+"  ·  "+state,10,stateColor(state));meta.addView(m,new LinearLayout.LayoutParams(0,CortexUi.dp(this,28),1));if("fetch_failed".equals(state)){TextView retry=CortexUi.chip(this,"RETRY",CortexUi.ORANGE,false);retry.setOnClickListener(v->{try{SharedLinkIntelligence.enrichAsync(this,db,k.id,k.rawText);render();}catch(Throwable ignored){}});meta.addView(retry,new LinearLayout.LayoutParams(-2,CortexUi.dp(this,28)));}c.addView(meta);return c;}

    private LinearLayout.LayoutParams params(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,CortexUi.dp(this,10));return p;}
    private String bestText(KnowledgeItem k){if(k.summary!=null&&!k.summary.trim().isEmpty())return k.summary.trim();if(k.extractedText!=null&&!k.extractedText.trim().isEmpty())return k.extractedText.trim();return k.rawText==null?"":k.rawText.trim();}
    private int stateColor(String s){if("understood".equals(s)||"analyzed".equals(s))return CortexUi.GREEN;if("fetch_failed".equals(s)||"analysis_failed".equals(s))return CortexUi.RED;if("pending_content".equals(s)||"queued".equals(s)||"analyzing".equals(s))return CortexUi.ORANGE;return CortexUi.MUTED;}
    private String trim(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}
}
