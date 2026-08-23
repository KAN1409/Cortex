package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** v50 dedicated Ask surface: no modal prompt, immediate feedback, grounded answer + sources. */
public class AskCortexActivity extends Activity {
    VaultDb db;
    LinearLayout conversation,composer;
    ScrollView scroll;
    EditText input;
    TextView send,status;
    boolean busy=false;

    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);build();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(14),dp(18),dp(10));
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Ask Cortex",25,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);status=CortexUi.plain(this,LocalModelManager.installed(this)?"Grounded in your Vault  •  Local Qwen ready":"Grounded in your Vault  •  fallback mode",11,LocalModelManager.installed(this)?CortexUi.SAGE:CortexUi.MUTED);titles.addView(status);header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);

        scroll=new ScrollView(this);scroll.setFillViewport(true);conversation=new LinearLayout(this);conversation.setOrientation(LinearLayout.VERTICAL);conversation.setPadding(dp(18),dp(8),dp(18),dp(20));scroll.addView(conversation);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        welcome();

        composer=new LinearLayout(this);composer.setOrientation(LinearLayout.HORIZONTAL);composer.setGravity(Gravity.BOTTOM|Gravity.CENTER_VERTICAL);composer.setPadding(dp(12),dp(8),dp(12),dp(12));composer.setBackgroundColor(CortexUi.BG);
        input=new EditText(this);input.setHint("Ask what you know, decided, saved or still need to do…");input.setHintTextColor(CortexUi.FAINT);input.setTextColor(CortexUi.TEXT);input.setTextSize(14);input.setMinLines(1);input.setMaxLines(5);input.setSingleLine(false);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setPadding(dp(15),dp(11),dp(12),dp(11));input.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER,18));composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send=CortexUi.action(this,"Ask",CortexUi.COPPER,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(64),dp(48));sp.setMargins(dp(8),0,0,0);composer.addView(send,sp);send.setOnClickListener(v->submit());root.addView(composer);
        setContentView(root);
    }

    void welcome(){
        LinearLayout hero=CortexUi.card(this,24);hero.setPadding(dp(18),dp(18),dp(18),dp(18));hero.setBackground(CortexUi.gradient(this,Color.rgb(49,32,24),CortexUi.SURFACE,CortexUi.BORDER,24));
        TextView eyebrow=CortexUi.plain(this,"PRIVATE INTELLIGENCE",10,CortexUi.COPPER);CortexUi.medium(eyebrow);eyebrow.setLetterSpacing(.09f);hero.addView(eyebrow);
        TextView title=CortexUi.plain(this,"Ask your memory, not the internet.",22,CortexUi.TEXT);CortexUi.medium(title);title.setPadding(0,dp(7),0,0);hero.addView(title);
        TextView body=CortexUi.text(this,"Cortex retrieves the most relevant saved evidence first, then answers from that evidence only.",13,CortexUi.MUTED);body.setPadding(0,dp(7),0,dp(13));hero.addView(body);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,0,0,dp(14));conversation.addView(hero,hp);
        TextView sh=CortexUi.section(this,"Try asking");sh.setPadding(0,dp(8),0,dp(9));conversation.addView(sh);
        addSuggestion("What still needs my attention?");addSuggestion("What did I decide recently?");addSuggestion("What do I know about this project?");
    }

    void addSuggestion(String s){TextView c=CortexUi.chip(this,s,CortexUi.COPPER,false);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(14),0,dp(14),0);c.setOnClickListener(v->{input.setText(s);input.setSelection(input.length());submit();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.setMargins(0,0,0,dp(8));conversation.addView(c,p);}

    void submit(){
        if(busy)return;String q=input.getText().toString().trim();if(q.isEmpty())return;busy=true;send.setText("…");send.setEnabled(false);input.setEnabled(false);hideKeyboard();addUser(q);input.setText("");TextView thinking=addThinking();scrollEnd();long started=android.os.SystemClock.elapsedRealtime();
        new Thread(()->{LocalAskRouter.Result r=LocalAskRouter.ask(this,db,q);long wall=android.os.SystemClock.elapsedRealtime()-started;runOnUiThread(()->{conversation.removeView(thinking);addAnswer(r,wall);busy=false;send.setText("Ask");send.setEnabled(true);input.setEnabled(true);input.requestFocus();scrollEnd();});},"CortexAskUi").start();
    }

    void addUser(String q){LinearLayout wrap=new LinearLayout(this);wrap.setGravity(Gravity.RIGHT);TextView bubble=CortexUi.text(this,q,14,CortexUi.TEXT);bubble.setPadding(dp(14),dp(11),dp(14),dp(11));bubble.setBackground(CortexUi.round(this,Color.rgb(51,38,29),Color.rgb(76,52,38),18));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.setMargins(dp(46),0,0,dp(10));wrap.addView(bubble,bp);conversation.addView(wrap);}

    TextView addThinking(){TextView t=CortexUi.plain(this,"✦  Retrieving memory…",12,CortexUi.COPPER);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(52),dp(10));conversation.addView(t,p);return t;}

    void addAnswer(LocalAskRouter.Result r,long wall){
        LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(17),dp(15),dp(17),dp(15));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView mark=CortexUi.plain(this,"✦",18,CortexUi.COPPER);head.addView(mark,new LinearLayout.LayoutParams(dp(30),dp(30)));TextView label=CortexUi.plain(this,"Cortex",13,CortexUi.TEXT);CortexUi.medium(label);head.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView provider=CortexUi.chip(this,"local-qwen".equals(r.provider)?"LOCAL":"GROUNDED",r.provider.equals("local-qwen")?CortexUi.SAGE:CortexUi.GOLD,true);head.addView(provider,new LinearLayout.LayoutParams(-2,dp(27)));card.addView(head);
        TextView body=CortexUi.text(this,r.answer,15,CortexUi.TEXT);body.setPadding(0,dp(12),0,0);body.setTextIsSelectable(true);card.addView(body);
        String perf=""+r.grounded.sources.size()+" sources"+(r.tokensPerSecond>0?"  •  "+String.format(Locale.US,"%.1f tok/s",r.tokensPerSecond):"")+"  •  "+friendlyMs(wall);
        TextView meta=CortexUi.plain(this,perf,10,CortexUi.MUTED);meta.setPadding(0,dp(12),0,0);card.addView(meta);
        if(r.cacheHit){TextView warm=CortexUi.plain(this,"Warm model • no reload",10,CortexUi.SAGE);warm.setPadding(0,dp(3),0,0);card.addView(warm);}else if(r.modelLoadMs>0){TextView cold=CortexUi.plain(this,"Model loaded in "+friendlyMs(r.modelLoadMs)+" • later asks should be faster",10,CortexUi.GOLD);cold.setPadding(0,dp(3),0,0);card.addView(cold);}
        if(!r.grounded.sources.isEmpty()){
            TextView sec=CortexUi.section(this,"Sources");sec.setPadding(0,dp(16),0,dp(8));card.addView(sec);
            int n=Math.min(4,r.grounded.sources.size());for(int i=0;i<n;i++){KnowledgeItem k=r.grounded.sources.get(i).item;TextView source=CortexUi.text(this,"M"+(i+1)+"  •  "+clean(k.title),12,CortexUi.MUTED);source.setMaxLines(2);source.setPadding(0,dp(5),0,dp(5));card.addView(source);}
            TextView open=CortexUi.action(this,"Open Vault",CortexUi.COPPER,false);open.setOnClickListener(v->startActivity(new Intent(this,VaultActivity.class)));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(42));op.setMargins(0,dp(8),0,0);card.addView(open,op);
        }
        if(r.error!=null&&!r.error.trim().isEmpty()){TextView e=CortexUi.text(this,r.error,11,CortexUi.CORAL);e.setPadding(0,dp(9),0,0);card.addView(e);}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(14));conversation.addView(card,p);
    }

    String friendlyMs(long ms){return ms<1000?ms+" ms":String.format(Locale.US,"%.1f s",ms/1000f);}String clean(String s){if(s==null||s.trim().isEmpty())return"Memory";String x=s.trim();return x.length()>72?x.substring(0,72)+"…":x;}
    void scrollEnd(){scroll.postDelayed(()->scroll.fullScroll(View.FOCUS_DOWN),80);}void hideKeyboard(){try{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}catch(Exception ignored){}}
    @Override protected void onDestroy(){super.onDestroy();if(db!=null)db.close();}
}
