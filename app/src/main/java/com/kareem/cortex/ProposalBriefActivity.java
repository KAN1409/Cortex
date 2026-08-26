package com.kareem.cortex;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Product-reset NOW surface.
 *
 * This is intentionally consequence-first, not pipeline-first: no audio hero, no generic recent
 * capture stream, no per-card AI proposal generation, and no empty placeholder tiles. Something is
 * visible here only when it changes what the user should do, wait for, remember as a decision, or
 * meaningfully continue.
 */
public final class ProposalBriefActivity extends PremiumHomeActivity {

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));headerNow();
        TextView loading=CortexUi.plain(this,"UNDERSTANDING WHAT MATTERS NOW…",9,CortexUi.FAINT);loading.setPadding(dp(3),dp(22),0,dp(8));content.addView(loading);
        CortexUi.addBottomNav(this,root,"brief",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void headerNow(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(7),dp(2),dp(10));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.RED,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));
        TextView cortex=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(cortex);if(android.os.Build.VERSION.SDK_INT>=21)cortex.setLetterSpacing(.20f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(cortex,cp);
        View divider=CortexUi.divider(this);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(28));dpv.setMargins(dp(12),0,dp(12),0);row.addView(divider,dpv);
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);TextView now=CortexUi.plain(this,"NOW",12,CortexUi.TEXT);CortexUi.medium(now);tx.addView(now);TextView sub=CortexUi.plain(this,"Only what changes what you should know or do.",9,CortexUi.MUTED);sub.setPadding(0,dp(2),0,0);tx.addView(sub);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(CortexUi.glyph(this,"brain",CortexUi.RED,true),new LinearLayout.LayoutParams(dp(46),dp(46)));content.addView(row);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;while(content.getChildCount()>1)content.removeViewAt(1);boolean visible=false;
        ContextPacketBuilder.Packet p=null;try{p=ContextPacketBuilder.buildLocal(db,420);}catch(Throwable ignored){}
        if(p!=null&&p.available()&&!CortexTruthPolicy.ambientContext(p.title+" "+p.currentActivity,"")){content.addView(contextCard(p),space(dp(8)));visible=true;}
        if(!s.actions.isEmpty()){content.addView(CortexUi.section(this,"Needs your attention"));for(int i=0;i<Math.min(5,s.actions.size());i++)derivedRow(s.actions.get(i));visible=true;}
        if(!s.waiting.isEmpty()){content.addView(CortexUi.section(this,"Waiting for"));for(int i=0;i<Math.min(5,s.waiting.size());i++)derivedRow(s.waiting.get(i));visible=true;}
        if(!s.decisions.isEmpty()){content.addView(CortexUi.section(this,"Decisions you made"));for(int i=0;i<Math.min(5,s.decisions.size());i++)derivedRow(s.decisions.get(i));visible=true;}
        if(!s.reviews.isEmpty()){content.addView(CortexUi.section(this,"Needs your confirmation"));for(int i=0;i<Math.min(3,s.reviews.size());i++)reviewRow(s.reviews.get(i));visible=true;}
        if(!visible)content.addView(emptyCard(),space(dp(15)));
        content.addView(askDock(),space(dp(16)));
    }

    View contextCard(ContextPacketBuilder.Packet p){
        LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(14),dp(14),dp(14),dp(14));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        int color=p.confidence>=.82?CortexUi.GREEN:CortexUi.ORANGE;top.addView(CortexUi.glyph(this,"brain",color,true),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,dp(6),0);top.addView(tx,xp);
        TextView label=CortexUi.plain(this,"CURRENT CONTEXT",9,color);CortexUi.medium(label);tx.addView(label);TextView title=CortexUi.text(this,p.title,17,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(2);title.setPadding(0,dp(3),0,0);tx.addView(title);card.addView(top);
        addContextFact(card,"NOW",p.currentActivity,CortexUi.GREEN);addContextFact(card,"OPEN LOOP",p.openLoops,CortexUi.ORANGE);addContextFact(card,"NEXT",p.nextStep,CortexUi.RED);
        TextView open=CortexUi.action(this,"Open current context",CortexUi.MUTED,false);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(42));op.setMargins(0,dp(12),0,0);card.addView(open,op);open.setOnClickListener(v->{try{startActivity(new android.content.Intent(this,ContextNowActivity.class));}catch(Throwable ignored){}});return card;
    }

    void addContextFact(LinearLayout card,String label,String value,int color){if(value==null||value.trim().isEmpty())return;TextView l=CortexUi.plain(this,label,8,color);CortexUi.medium(l);l.setPadding(0,dp(11),0,0);card.addView(l);TextView v=CortexUi.text(this,value,11,CortexUi.TEXT);v.setMaxLines(3);v.setPadding(0,dp(3),0,0);card.addView(v);}

    View emptyCard(){LinearLayout c=CortexUi.card(this,22);c.setPadding(dp(16),dp(18),dp(16),dp(18));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(CortexUi.glyph(this,"check",CortexUi.GREEN,true),new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,0,0);row.addView(tx,xp);TextView h=CortexUi.text(this,"Nothing useful needs surfacing right now",16,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView b=CortexUi.text(this,"Cortex will stay quiet until it has a grounded action, waiting item, decision, or meaningful context.",11,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);tx.addView(b);c.addView(row);return c;}

    View askDock(){LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(dp(10),dp(7),dp(7),dp(7));dock.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER,18));dock.addView(CortexUi.glyph(this,"brain",CortexUi.YELLOW,false),new LinearLayout.LayoutParams(dp(40),dp(40)));TextView ask=CortexUi.plain(this,"Ask what needs me, what I'm waiting for, or what I decided",11,CortexUi.MUTED);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMargins(dp(9),0,dp(5),0);ask.setGravity(Gravity.CENTER_VERTICAL);ask.setMaxLines(2);dock.addView(ask,ap);TextView go=CortexUi.action(this,"Ask",CortexUi.YELLOW,false);dock.addView(go,new LinearLayout.LayoutParams(dp(72),dp(44)));View.OnClickListener l=v->CortexActionExecutor.openBrain(this,0,"What actually needs my attention now? Separate confirmed actions, things I am waiting for, and decisions I actually made. Ignore notification noise and unsupported guesses.");ask.setOnClickListener(l);go.setOnClickListener(l);return dock;}

    LinearLayout.LayoutParams space(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,top,0,0);return p;}
}
