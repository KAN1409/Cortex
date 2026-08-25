package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Final PRIME Brief: unified Satin shell plus one model proposal pass per visible intelligence result. */
public final class ProposalBriefActivity extends CortexOrbBriefActivity {
    LinearLayout audioProposalHost;

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));systemHeader();
        TextView loading=CortexUi.plain(this,"BUILDING CURRENT BRIEF…",9,CortexUi.FAINT);loading.setPadding(dp(4),dp(24),0,dp(10));content.addView(loading);
        addSatinNav(root);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    @Override void systemHeader(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(7),dp(8),dp(4),dp(10));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.ACCENT,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(8),dp(8)));
        TextView cortex=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(cortex);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(cortex,cp);
        View divider=CortexUi.divider(this);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(28));dpv.setMargins(dp(12),0,dp(12),0);row.addView(divider,dpv);
        TextView sys=CortexUi.plain(this,"SYSTEM",10,CortexUi.MUTED);row.addView(sys,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView settings=CortexUi.chip(this,"SETTINGS",CortexUi.MUTED,false);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});row.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));
        content.addView(row);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;while(content.getChildCount()>1)content.removeViewAt(1);collectAudio(s);
        content.addView(signalCard(s),margins(0,dp(8),0,0));if(!audioItems.isEmpty())content.addView(audioCard(s),margins(0,dp(12),0,0));
        if(!s.actions.isEmpty())derivedSection("NEEDS YOU",CortexUi.ACCENT,s.actions,"action",4);
        if(!s.waiting.isEmpty())derivedSection("WAITING",CortexUi.AMBER,s.waiting,"waiting",3);
        if(!s.decisions.isEmpty())derivedSection("DECISIONS",CortexUi.VIOLET,s.decisions,"decision",3);
        if(!s.worthKnowing.isEmpty())derivedSection("WORTH KNOWING",CortexUi.VIOLET,s.worthKnowing,"info",3);
        if(!s.changes.isEmpty())derivedSection("CHANGED & EVOLVING",CortexUi.ACCENT,s.changes,"change",3);
        if(!s.reviews.isEmpty()){
            sectionTitle("NEEDS REVIEW",CortexUi.AMBER);LinearLayout r=satinCard(18);r.setPadding(dp(14),dp(12),dp(14),dp(12));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);BriefGlyphView g=new BriefGlyphView(this,"review",CortexUi.AMBER);top.addView(g,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView t=CortexUi.plain(this,s.reviews.size()+" item"+(s.reviews.size()==1?"":"s")+" need your review",13,CortexUi.TEXT);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(10),0,0,0);top.addView(t,tp);top.addView(statusChip("REVIEW",CortexUi.AMBER),new LinearLayout.LayoutParams(-2,dp(32)));r.addView(top);r.setOnClickListener(v->{try{startActivity(new Intent(this,ReviewQueueActivity.class));}catch(Throwable ignored){}});content.addView(r);
        }
        if(s.empty()){LinearLayout e=satinCard(22);e.setPadding(dp(17),dp(20),dp(17),dp(20));TextView h=CortexUi.plain(this,"Nothing needs you right now",18,CortexUi.TEXT);CortexUi.medium(h);e.addView(h);TextView b=CortexUi.text(this,"Cortex is still listening. New captures and derived intelligence will surface here when they need attention.",12,CortexUi.MUTED);b.setPadding(0,dp(6),0,0);e.addView(b);content.addView(e,margins(0,dp(14),0,0));}
        content.addView(promptDock(),margins(0,dp(16),0,dp(8)));
    }

    @Override View signalCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=satinCard(22);card.setPadding(dp(15),dp(15),dp(13),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);BriefGlyphView wave=new BriefGlyphView(this,"wave",CortexUi.ACCENT);top.addView(wave,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams txp=new LinearLayout.LayoutParams(0,-2,1);txp.setMargins(dp(12),0,dp(8),0);top.addView(text,txp);TextView h=CortexUi.plain(this,"CURRENT CORTEX SIGNAL",10,CortexUi.ACCENT);CortexUi.medium(h);text.addView(h);
        int attention=s.actions.size()+s.waiting.size()+s.decisions.size();TextView b=CortexUi.text(this,attention>0?"Cortex has "+attention+" current signal"+(attention==1?"":"s")+" that may need you.":"Context stream active. Nothing urgent is being forced into your attention.",13,CortexUi.TEXT);b.setPadding(0,dp(5),0,0);text.addView(b);TextView meta=CortexUi.plain(this,"TAP DAILY  ·  HOLD WEEKLY",8,CortexUi.FAINT);meta.setPadding(0,dp(7),0,0);text.addView(meta);top.addView(statusChip("● LIVE",CortexUi.ACCENT),new LinearLayout.LayoutParams(-2,dp(34)));card.addView(top);card.setOnClickListener(v->showComposed(false));card.setOnLongClickListener(v->{showComposed(true);return true;});
        return card;
    }

    @Override View audioCard(PrimeBriefStore.Snapshot s){
        View v=super.audioCard(s);if(v instanceof LinearLayout){LinearLayout card=(LinearLayout)v;audioProposalHost=new LinearLayout(this);audioProposalHost.setOrientation(LinearLayout.VERTICAL);audioProposalHost.setPadding(dp(4),dp(6),dp(4),0);card.addView(audioProposalHost);refreshAudioProposal();}return v;
    }

    @Override void selectAudio(int index){super.selectAudio(index);refreshAudioProposal();}
    void refreshAudioProposal(){
        if(audioProposalHost==null||db==null||audioItems.isEmpty())return;audioProposalHost.removeAllViews();int i=Math.max(0,Math.min(audioIndex,audioItems.size()-1));KnowledgeItem k=audioItems.get(i);String text=!blank(k.summary)?k.summary:(!blank(k.extractedText)?k.extractedText:k.rawText);if(blank(text))text="Voice recording captured in Cortex; analysis status: "+(k.status==null?"":k.status);
        ResultProposalEngine.Target target=new ResultProposalEngine.Target("Brief / Voice result","brief_audio_"+k.id,k.title==null||k.title.trim().isEmpty()?"Voice recording":k.title,text,k.id,k.type,ProposalUi.cloudAllowedForMemory(this,k));ProposalUi.attach(this,db,audioProposalHost,target);
    }

    @Override void derivedSection(String title,int color,List<PrimeBriefStore.Item> xs,String glyph,int limit){
        sectionTitle(title,color);LinearLayout box=satinCard(19);box.setPadding(0,0,0,0);int n=Math.min(limit,xs.size());
        for(int i=0;i<n;i++){
            PrimeBriefStore.Item x=xs.get(i);LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setPadding(0,0,0,0);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(13),dp(11),dp(11),dp(7));BriefGlyphView icon=new BriefGlyphView(this,glyph,color);row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
            LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(10),0,dp(8),0);row.addView(txt,tp);String tt=x.title==null||x.title.trim().isEmpty()?friendlyFallback(x.kind):x.title.trim();TextView h=CortexUi.text(this,clipLocal(tt,80),13,CortexUi.TEXT);CortexUi.medium(h);h.setMaxLines(2);txt.addView(h);String body=x.body==null?"":x.body.trim();TextView m=CortexUi.plain(this,(body.isEmpty()?ageLocal(x.updatedAt):clipLocal(body,90)+"  ·  "+ageLocal(x.updatedAt)),10,CortexUi.MUTED);m.setMaxLines(1);m.setPadding(0,dp(4),0,0);txt.addView(m);row.addView(statusChip(chipLabel(x.kind),color),new LinearLayout.LayoutParams(-2,dp(32)));row.setOnClickListener(v->derivedDetail(x));wrap.addView(row);
            long evidenceId=sourceEvidenceId(x);boolean cloud=false;String sourceType="BRIEF_"+(x.kind==null?"RESULT":x.kind);if(evidenceId>0)try{KnowledgeItem k=db.getById(evidenceId);cloud=ProposalUi.cloudAllowedForMemory(this,k);if(k!=null&&!blank(k.type))sourceType=k.type;}catch(Throwable ignored){}
            String result=tt+(body.isEmpty()?"":"\n"+body)+"\nState: "+x.state+" · confidence "+Math.round(x.confidence*100)+"%";
            LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);host.setPadding(dp(13),0,dp(11),dp(7));wrap.addView(host);ProposalUi.attach(this,db,host,new ResultProposalEngine.Target("Brief / "+title,"brief_"+x.kind+"_"+x.id,tt,result,evidenceId,sourceType,cloud));
            box.addView(wrap);if(i<n-1){View d=CortexUi.divider(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(13),0,dp(13),0);box.addView(d,lp);}
        }
        content.addView(box);if(xs.size()>n){TextView more=CortexUi.plain(this,"+ "+(xs.size()-n)+" MORE",8,CortexUi.FAINT);more.setGravity(Gravity.RIGHT);more.setPadding(0,dp(6),dp(4),0);content.addView(more);}
    }

    @Override View promptDock(){
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(dp(10),dp(6),dp(6),dp(6));dock.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,22));BriefGlyphView nodes=new BriefGlyphView(this,"nodes",CortexUi.ACCENT);dock.addView(nodes,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView ask=CortexUi.plain(this,"Ask Cortex about this briefing…",12,CortexUi.MUTED);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMargins(dp(8),0,dp(4),0);ask.setGravity(Gravity.CENTER_VERTICAL);dock.addView(ask,ap);CortexRingButton record=new CortexRingButton(this);record.setGlyph(CortexRingButton.Glyph.RECORD);record.setAccent(CortexUi.SIGNAL);record.setProgress(0f);dock.addView(record,new LinearLayout.LayoutParams(dp(58),dp(58)));
        View.OnClickListener brain=v->{long id=audioItems.isEmpty()?0:audioItems.get(Math.max(0,Math.min(audioIndex,audioItems.size()-1))).id;CortexActionExecutor.openBrain(this,id,"Analyze my current Cortex Brief. Connect the audio/context with what needs me, what is waiting, decisions, and useful next actions. Be concise and executable.");};ask.setOnClickListener(brain);nodes.setOnClickListener(brain);record.setOnClickListener(v->{try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode","voice");startActivity(i);}catch(Throwable ignored){}});return dock;
    }

    @Override void addSatinNav(LinearLayout root){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(8),dp(4),dp(8),dp(7));bar.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,20));
        nav(bar,"INPUT",false,InputActivity.class);nav(bar,"BRIEF",true,null);nav(bar,"PEOPLE",false,ProposalPeopleProjectsActivity.class);nav(bar,"BRAIN",false,ProposalAskCortexActivity.class);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.setMargins(dp(12),dp(4),dp(12),dp(8));root.addView(bar,p);
    }
    void nav(LinearLayout bar,String text,boolean selected,Class<?> target){TextView v=CortexUi.plain(this,text,8,selected?CortexUi.TEXT:CortexUi.MUTED);CortexUi.medium(v);v.setGravity(Gravity.CENTER);if(selected)v.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.ACCENT,13));else if(target!=null)v.setOnClickListener(x->{try{Intent i=new Intent(this,target);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}catch(Throwable ignored){}});bar.addView(v,new LinearLayout.LayoutParams(0,-1,1));}

    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
}
