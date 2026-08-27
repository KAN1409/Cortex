package com.kareem.cortex;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * Attention-first Today surface. The screen budget belongs to what needs the user now;
 * recent voice/context stays compact and expands only on demand.
 */
public final class CompactTodayActivity extends CortexOrbBriefActivity {
    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(22));sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));systemHeader();
        TextView loading=CortexUi.plain(this,"BUILDING TODAY…",9,CortexUi.FAINT);loading.setPadding(dp(2),dp(14),0,dp(8));content.addView(loading);
        addTodayNav(root);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    @Override void systemHeader(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(5),dp(2),dp(7));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.RED,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(8),dp(8)));
        TextView cortex=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(cortex);if(android.os.Build.VERSION.SDK_INT>=21)cortex.setLetterSpacing(.20f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(36));cp.setMargins(dp(12),0,0,0);row.addView(cortex,cp);
        View divider=CortexUi.divider(this);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(24));dpv.setMargins(dp(12),0,dp(12),0);row.addView(divider,dpv);
        TextView sys=CortexUi.plain(this,"TODAY",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)sys.setLetterSpacing(.10f);row.addView(sys,new LinearLayout.LayoutParams(0,dp(36),1));
        CortexGlyphView settings=CortexUi.glyph(this,"settings",CortexUi.RED,false);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});row.addView(settings,new LinearLayout.LayoutParams(dp(42),dp(42)));
        content.addView(row);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;while(content.getChildCount()>1)content.removeViewAt(1);collectAudio(s);

        if(!s.actions.isEmpty())derivedSection("NEEDS YOU",CortexUi.RED,s.actions,"action",4);
        if(!s.waiting.isEmpty())derivedSection("WAITING",CortexUi.ORANGE,s.waiting,"waiting",3);
        if(!s.decisions.isEmpty())derivedSection("DECISIONS",CortexUi.YELLOW,s.decisions,"decision",3);
        if(!s.changes.isEmpty())derivedSection("CHANGED & EVOLVING",CortexUi.ORANGE,s.changes,"change",3);
        if(!s.worthKnowing.isEmpty())derivedSection("WORTH KNOWING",CortexUi.GREEN,s.worthKnowing,"info",3);
        if(!s.reviews.isEmpty())reviewRow(s.reviews.size());
        if(!audioItems.isEmpty()){
            sectionTitle("RECENT CONTEXT",CortexUi.MUTED);
            content.addView(audioCard(s),margins(0,0,0,0));
        }
        if(s.empty()){
            LinearLayout e=CortexUi.card(this,20);e.setPadding(dp(16),dp(18),dp(16),dp(18));
            TextView h=CortexUi.plain(this,"Nothing needs you right now",18,CortexUi.TEXT);CortexUi.medium(h);e.addView(h);
            TextView b=CortexUi.text(this,"Cortex is listening. New evidence will surface here only when it deserves attention.",12,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);e.addView(b);content.addView(e,margins(0,dp(8),0,0));
        }
        content.addView(promptDock(),margins(0,dp(14),0,dp(7)));
    }

    @Override View signalCard(PrimeBriefStore.Snapshot s){
        View v=new View(this);v.setVisibility(View.GONE);return v;
    }

    @Override View audioCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=CortexUi.card(this,18);card.setGravity(Gravity.CENTER_VERTICAL);card.setOrientation(LinearLayout.HORIZONTAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));
        CortexGlyphView wave=CortexUi.glyph(this,"wave",CortexUi.RED,true);card.addView(wave,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(10),0,dp(8),0);card.addView(text,tp);
        KnowledgeItem k=audioItems.get(Math.max(0,Math.min(audioIndex,audioItems.size()-1)));
        TextView title=CortexUi.text(this,k.title==null||k.title.trim().isEmpty()?"Latest voice note":clipLocal(k.title,56),13,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(1);text.addView(title);
        TextView meta=CortexUi.plain(this,"Voice context  •  "+ageLocal(k.createdAt)+"  •  tap to play",9,CortexUi.MUTED);meta.setPadding(0,dp(3),0,0);text.addView(meta);
        TextView open=CortexUi.plain(this,"OPEN",8,CortexUi.RED);open.setGravity(Gravity.CENTER);card.addView(open,new LinearLayout.LayoutParams(dp(48),dp(32)));
        card.setOnClickListener(v->showVoicePlayer(s));return card;
    }

    void showVoicePlayer(PrimeBriefStore.Snapshot s){
        Dialog d=new Dialog(this);ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(12),dp(12),dp(12));sv.addView(box);
        View player=CortexOrbBriefActivity.super.audioCard(s);box.addView(player);
        TextView close=CortexUi.action(this,"Close",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(8),0,0);box.addView(close,cp);close.setOnClickListener(v->d.dismiss());d.setContentView(sv);try{d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94f),(int)(getResources().getDisplayMetrics().heightPixels*.80f));}catch(Throwable ignored){}
    }

    @Override void derivedSection(String title,int color,List<PrimeBriefStore.Item> xs,String glyph,int limit){
        sectionTitle(title,color);int n=Math.min(limit,xs.size());
        for(int i=0;i<n;i++){
            PrimeBriefStore.Item x=xs.get(i);String tt=x.title==null||x.title.trim().isEmpty()?friendlyFallback(x.kind):x.title.trim();String body=x.body==null?"":x.body.trim();
            LinearLayout card=CortexUi.card(this,17);card.setPadding(0,0,0,0);
            LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(dp(12),dp(11),dp(12),dp(8));
            View rail=new View(this);rail.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(3),dp(58));rp.setMargins(0,0,dp(11),0);main.addView(rail,rp);
            LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);main.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
            TextView h=CortexUi.text(this,clipLocal(tt,110),15,CortexUi.TEXT);CortexUi.medium(h);h.setMaxLines(3);txt.addView(h);
            if(!body.isEmpty()){TextView m=CortexUi.text(this,clipLocal(body,145),11,CortexUi.MUTED);m.setMaxLines(2);m.setPadding(0,dp(4),0,0);txt.addView(m);}
            TextView why=CortexUi.plain(this,x.attentionBand+"  •  "+x.attentionScore+"  •  "+clipLocal(x.whyNow,120),9,CortexUi.FAINT);why.setMaxLines(2);why.setPadding(0,dp(5),0,0);txt.addView(why);card.addView(main);main.setOnClickListener(v->{AttentionLearning.record(db,x.id,"opened");derivedDetail(x);});
            card.addView(compactActions(x),new LinearLayout.LayoutParams(-1,dp(38)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);if(i>0)cp.setMargins(0,dp(7),0,0);content.addView(card,cp);
        }
        if(xs.size()>n){TextView more=CortexUi.plain(this,"+ "+(xs.size()-n)+" MORE",8,CortexUi.FAINT);more.setGravity(Gravity.RIGHT);more.setPadding(0,dp(6),dp(3),0);content.addView(more);}
    }

    View compactActions(PrimeBriefStore.Item x){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),0,dp(8),dp(6));
        TextView why=small("WHY",CortexUi.MUTED),done=small("DONE",CortexUi.GREEN),snooze=small("SNOOZE",CortexUi.ORANGE),dismiss=small("HIDE",CortexUi.FAINT);
        why.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Why now").setMessage(x.attentionBand+" · "+x.attentionScore+"/100\n\n"+x.whyNow).setPositiveButton("OK",null).show());
        done.setOnClickListener(v->{if(resolveDerived(x.id)){AttentionLearning.record(db,x.id,"acted");refreshAsync();}});
        snooze.setOnClickListener(v->{AttentionLearning.snooze(db,x.id,System.currentTimeMillis()+3L*60L*60L*1000L);refreshAsync();});
        dismiss.setOnClickListener(v->{AttentionLearning.record(db,x.id,"dismissed");refreshAsync();});
        row.addView(why,new LinearLayout.LayoutParams(0,dp(32),1));row.addView(done,new LinearLayout.LayoutParams(0,dp(32),1));row.addView(snooze,new LinearLayout.LayoutParams(0,dp(32),1));row.addView(dismiss,new LinearLayout.LayoutParams(0,dp(32),1));return row;
    }

    TextView small(String s,int color){TextView t=CortexUi.plain(this,s,8,color);t.setGravity(Gravity.CENTER);return t;}

    void reviewRow(int count){
        sectionTitle("NEEDS REVIEW",CortexUi.YELLOW);TextView row=CortexUi.text(this,count+" item"+(count==1?"":"s")+" need your judgement",12,CortexUi.TEXT);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(CortexUi.round(this,CortexUi.SURFACE,Color.argb(24,255,255,255),16));row.setOnClickListener(v->{try{startActivity(new Intent(this,ReviewQueueActivity.class));}catch(Throwable ignored){}});content.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));
    }

    @Override void sectionTitle(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.09f);h.setPadding(dp(1),dp(13),0,dp(6));content.addView(h);}

    void addTodayNav(LinearLayout root){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(8),dp(4),dp(8),dp(7));bar.setBackground(CortexUi.round(this,Color.rgb(8,9,12),Color.argb(30,255,255,255),20));
        addTodayNavItem(bar,"Today",CortexUi.RED,null);addTodayNavItem(bar,"Memory",CortexUi.MUTED,ProposalPeopleProjectsActivity.class);addTodayNavItem(bar,"Capture",CortexUi.ORANGE,ProposalCaptureActivity.class);addTodayNavItem(bar,"Cortex",CortexUi.MUTED,ProposalAskCortexActivity.class);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(64));p.setMargins(dp(12),dp(4),dp(12),dp(8));root.addView(bar,p);
    }

    void addTodayNavItem(LinearLayout bar,String label,int color,Class<?> target){TextView v=CortexUi.plain(this,label,9,color);v.setGravity(Gravity.CENTER);if(target==null)v.setBackground(CortexUi.round(this,Color.argb(22,255,42,36),Color.argb(80,255,42,36),14));else v.setOnClickListener(x->{try{Intent i=new Intent(this,target);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}catch(Throwable ignored){}});bar.addView(v,new LinearLayout.LayoutParams(0,-1,1));}
}
