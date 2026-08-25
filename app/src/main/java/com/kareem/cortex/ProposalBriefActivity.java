package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Final PRIME Brief locked to the approved matte warm premium preview. */
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
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(5),dp(8),dp(2),dp(11));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.RED,Color.TRANSPARENT,999));CortexUi.raised(this,dot,3);row.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));
        TextView cortex=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(cortex);if(android.os.Build.VERSION.SDK_INT>=21)cortex.setLetterSpacing(.20f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(cortex,cp);
        View divider=CortexUi.divider(this);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(28));dpv.setMargins(dp(12),0,dp(12),0);row.addView(divider,dpv);
        TextView sys=CortexUi.plain(this,"SYSTEM",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)sys.setLetterSpacing(.10f);row.addView(sys,new LinearLayout.LayoutParams(0,dp(40),1));
        CortexGlyphView settings=CortexUi.glyph(this,"settings",CortexUi.RED,false);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});row.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46)));
        content.addView(row);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;while(content.getChildCount()>1)content.removeViewAt(1);collectAudio(s);
        content.addView(signalCard(s),margins(0,dp(8),0,0));if(!audioItems.isEmpty())content.addView(audioCard(s),margins(0,dp(12),0,0));
        if(!s.actions.isEmpty())derivedSection("NEEDS YOU",CortexUi.RED,s.actions,"action",4);
        if(!s.waiting.isEmpty())derivedSection("WAITING",CortexUi.ORANGE,s.waiting,"waiting",3);
        if(!s.decisions.isEmpty())derivedSection("DECISIONS",CortexUi.YELLOW,s.decisions,"decision",3);
        if(!s.worthKnowing.isEmpty())derivedSection("WORTH KNOWING",CortexUi.GREEN,s.worthKnowing,"info",3);
        if(!s.changes.isEmpty())derivedSection("CHANGED & EVOLVING",CortexUi.ORANGE,s.changes,"change",3);
        if(!s.reviews.isEmpty())derivedSection("NEEDS REVIEW",CortexUi.YELLOW,s.reviews,"review",3);
        if(s.empty()){
            LinearLayout e=CortexUi.card(this,22);e.setPadding(dp(17),dp(20),dp(17),dp(20));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,"check",CortexUi.GREEN,true),new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(12),0,0,0);top.addView(tx,xp);TextView h=CortexUi.plain(this,"Nothing needs you right now",18,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView b=CortexUi.text(this,"Cortex is listening. Useful changes will surface here when they deserve attention.",12,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);tx.addView(b);e.addView(top);content.addView(e,margins(0,dp(14),0,0));
        }
        content.addView(promptDock(),margins(0,dp(17),0,dp(8)));
    }

    @Override View signalCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=CortexUi.card(this,22);card.setPadding(0,0,0,0);LinearLayout core=new LinearLayout(this);core.setGravity(Gravity.CENTER_VERTICAL);core.setPadding(dp(14),dp(15),dp(13),dp(15));
        View rail=new View(this);rail.setBackground(CortexUi.round(this,CortexUi.RED,Color.TRANSPARENT,999));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(2),dp(58));rp.setMargins(0,0,dp(12),0);core.addView(rail,rp);
        core.addView(CortexUi.glyph(this,"wave",CortexUi.RED,true),new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams txp=new LinearLayout.LayoutParams(0,-2,1);txp.setMargins(dp(13),0,dp(8),0);core.addView(text,txp);
        TextView h=CortexUi.plain(this,"AUDIO BRIEFING SIGNAL",10,CortexUi.RED);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.09f);text.addView(h);
        int attention=s.actions.size()+s.waiting.size()+s.decisions.size();TextView b=CortexUi.text(this,attention>0?"Cortex has "+attention+" current signal"+(attention==1?"":"s")+" that may need you.":"Voice transcript & context stream active.",13,CortexUi.TEXT);b.setPadding(0,dp(5),0,0);text.addView(b);
        TextView meta=CortexUi.plain(this,"LIVE  •  CONTEXT  •  CURRENT",8,CortexUi.MUTED);meta.setPadding(0,dp(7),0,0);text.addView(meta);core.addView(statusChip("● LIVE",CortexUi.RED),new LinearLayout.LayoutParams(-2,dp(34)));card.addView(core);card.setOnClickListener(v->showComposed(false));card.setOnLongClickListener(v->{showComposed(true);return true;});return card;
    }

    /** Playback is the hero inside the audio card; recording remains a separate tactile control in the dock. */
    @Override View audioCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=CortexUi.card(this,24);card.setPadding(dp(15),dp(15),dp(15),dp(14));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,"wave",CortexUi.RED,true),new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tbp=new LinearLayout.LayoutParams(0,-2,1);tbp.setMargins(dp(14),0,dp(8),0);top.addView(titleBox,tbp);
        audioTitle=CortexUi.plain(this,"Voice recording",20,CortexUi.TEXT);CortexUi.medium(audioTitle);audioTitle.setMaxLines(1);titleBox.addView(audioTitle);audioSub=CortexUi.plain(this,"Voice note",11,CortexUi.MUTED);audioSub.setPadding(0,dp(5),0,0);titleBox.addView(audioSub);top.addView(statusChip("VOICE",CortexUi.RED),new LinearLayout.LayoutParams(-2,dp(34)));card.addView(top);

        scrub=new CortexScrubberView(this);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(92));wp.setMargins(0,dp(11),0,0);card.addView(scrub,wp);
        LinearLayout times=new LinearLayout(this);times.setGravity(Gravity.CENTER_VERTICAL);timeNow=CortexUi.plain(this,"00:00",10,CortexUi.MUTED);timeEnd=CortexUi.plain(this,"00:00",10,CortexUi.MUTED);timeEnd.setGravity(Gravity.RIGHT);times.addView(timeNow,new LinearLayout.LayoutParams(0,dp(24),1));times.addView(timeEnd,new LinearLayout.LayoutParams(0,dp(24),1));card.addView(times);

        LinearLayout transport=new LinearLayout(this);transport.setGravity(Gravity.CENTER);transport.setPadding(0,dp(2),0,dp(8));
        CortexRingButton prev=new CortexRingButton(this);prev.setGlyph(CortexRingButton.Glyph.PREVIOUS);prev.setAccent(CortexUi.MUTED);prev.setOnClickListener(v->selectAudio(audioIndex-1));transport.addView(prev,new LinearLayout.LayoutParams(dp(58),dp(58)));
        playRing=new CortexRingButton(this);playRing.setGlyph(CortexRingButton.Glyph.PLAY);playRing.setAccent(CortexUi.RED);playRing.setProgress(0);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(82),dp(82));pp.setMargins(dp(22),0,dp(22),0);transport.addView(playRing,pp);playRing.setOnClickListener(v->togglePlayback());
        CortexRingButton next=new CortexRingButton(this);next.setGlyph(CortexRingButton.Glyph.NEXT);next.setAccent(CortexUi.MUTED);next.setOnClickListener(v->selectAudio(audioIndex+1));transport.addView(next,new LinearLayout.LayoutParams(dp(58),dp(58)));card.addView(transport);
        scrub.setListener((fraction,finished)->{if(player==null||!prepared)return;int d=player.getDuration(),pos=Math.max(0,Math.min(d,(int)(d*fraction)));timeNow.setText(fmt(pos));playRing.setProgress(fraction);if(finished)try{player.seekTo(pos);}catch(Throwable ignored){}});

        View line=CortexUi.divider(this);card.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout metrics=new LinearLayout(this);metrics.setGravity(Gravity.CENTER_VERTICAL);metrics.setPadding(0,dp(9),0,0);metrics.addView(metric("wave",Math.max(1,s.worthKnowing.size())+" key points",CortexUi.RED),new LinearLayout.LayoutParams(0,dp(44),1));metrics.addView(metric("decision",s.decisions.size()+" decisions",CortexUi.YELLOW),new LinearLayout.LayoutParams(0,dp(44),1));metrics.addView(metric("bolt",s.actions.size()+" actions",CortexUi.ORANGE),new LinearLayout.LayoutParams(0,dp(44),1));card.addView(metrics);
        audioProposalHost=new LinearLayout(this);audioProposalHost.setOrientation(LinearLayout.VERTICAL);audioProposalHost.setPadding(dp(3),dp(8),dp(3),0);card.addView(audioProposalHost);ui.post(()->{selectAudio(Math.min(audioIndex,audioItems.size()-1));refreshAudioProposal();});return card;
    }

    View metric(String kind,String text,int color){LinearLayout x=new LinearLayout(this);x.setGravity(Gravity.CENTER_VERTICAL);x.setPadding(dp(3),0,dp(3),0);x.addView(CortexUi.glyph(this,kind,color,false),new LinearLayout.LayoutParams(dp(34),dp(34)));TextView t=CortexUi.plain(this,text,9,CortexUi.MUTED);t.setMaxLines(2);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(6),0,0,0);x.addView(t,tp);return x;}

    @Override void selectAudio(int index){super.selectAudio(index);refreshAudioProposal();}
    void refreshAudioProposal(){
        if(audioProposalHost==null||db==null||audioItems.isEmpty())return;audioProposalHost.removeAllViews();int i=Math.max(0,Math.min(audioIndex,audioItems.size()-1));KnowledgeItem k=audioItems.get(i);String text=!blank(k.summary)?k.summary:(!blank(k.extractedText)?k.extractedText:k.rawText);if(blank(text))text="Voice recording captured in Cortex; analysis status: "+(k.status==null?"":k.status);
        ResultProposalEngine.Target target=new ResultProposalEngine.Target("Brief / Voice result","brief_audio_"+k.id,k.title==null||k.title.trim().isEmpty()?"Voice recording":k.title,text,k.id,k.type,ProposalUi.cloudAllowedForMemory(this,k));ProposalUi.attach(this,db,audioProposalHost,target);
    }

    @Override void derivedSection(String title,int color,List<PrimeBriefStore.Item> xs,String glyph,int limit){
        sectionTitle(title,color);int n=Math.min(limit,xs.size());
        for(int i=0;i<n;i++){
            PrimeBriefStore.Item x=xs.get(i);String tt=x.title==null||x.title.trim().isEmpty()?friendlyFallback(x.kind):x.title.trim();String body=x.body==null?"":x.body.trim();
            LinearLayout card=CortexUi.card(this,18);card.setPadding(0,0,0,0);LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(dp(11),dp(11),dp(10),dp(10));
            View rail=new View(this);rail.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(2),dp(46));rp.setMargins(0,0,dp(10),0);main.addView(rail,rp);
            String iconKind="waiting".equals(glyph)?"clock":"decision".equals(glyph)?"decision":"action".equals(glyph)?"open":"review".equals(glyph)?"note":"info";main.addView(CortexUi.glyph(this,iconKind,color,true),new LinearLayout.LayoutParams(dp(46),dp(46)));
            LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(11),0,dp(8),0);main.addView(txt,tp);TextView h=CortexUi.text(this,clipLocal(tt,84),14,CortexUi.TEXT);CortexUi.medium(h);h.setMaxLines(2);txt.addView(h);TextView m=CortexUi.plain(this,(body.isEmpty()?ageLocal(x.updatedAt):clipLocal(body,94)+"  •  "+ageLocal(x.updatedAt)),10,CortexUi.MUTED);m.setMaxLines(2);m.setPadding(0,dp(4),0,0);txt.addView(m);main.addView(statusChip(chipLabel(x.kind),color),new LinearLayout.LayoutParams(-2,dp(32)));card.addView(main);main.setOnClickListener(v->derivedDetail(x));
            long evidenceId=sourceEvidenceId(x);boolean cloud=false;String sourceType="BRIEF_"+(x.kind==null?"RESULT":x.kind);if(evidenceId>0)try{KnowledgeItem k=db.getById(evidenceId);cloud=ProposalUi.cloudAllowedForMemory(this,k);if(k!=null&&!blank(k.type))sourceType=k.type;}catch(Throwable ignored){}
            String result=tt+(body.isEmpty()?"":"\n"+body)+"\nState: "+x.state+" · confidence "+Math.round(x.confidence*100)+"%";LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);host.setPadding(dp(13),0,dp(11),dp(8));card.addView(host);ProposalUi.attach(this,db,host,new ResultProposalEngine.Target("Brief / "+title,"brief_"+x.kind+"_"+x.id,tt,result,evidenceId,sourceType,cloud));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);if(i>0)cp.setMargins(0,dp(8),0,0);content.addView(card,cp);
        }
        if(xs.size()>n){TextView more=CortexUi.plain(this,"+ "+(xs.size()-n)+" MORE",8,CortexUi.FAINT);more.setGravity(Gravity.RIGHT);more.setPadding(0,dp(7),dp(4),0);content.addView(more);}
    }

    @Override void sectionTitle(String title,int color){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(18),dp(1),dp(8));TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.09f);row.addView(h,new LinearLayout.LayoutParams(0,-2,1));content.addView(row);}

    @Override View promptDock(){
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(dp(9),dp(7),dp(7),dp(7));dock.setBackground(CortexUi.matte(this,22));CortexUi.raised(this,dock,5);
        CortexGlyphView nodes=CortexUi.glyph(this,"brain",CortexUi.RED,true);dock.addView(nodes,new LinearLayout.LayoutParams(dp(42),dp(42)));TextView ask=CortexUi.plain(this,"Ask Cortex about this briefing…",12,CortexUi.MUTED);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMargins(dp(9),0,dp(6),0);ask.setGravity(Gravity.CENTER_VERTICAL);dock.addView(ask,ap);
        recordOrb=new CortexRingButton(this);recordOrb.setGlyph(CortexRingButton.Glyph.RECORD);recordOrb.setAccent(CortexUi.RED);recordOrb.setProgress(0f);dock.addView(recordOrb,new LinearLayout.LayoutParams(dp(64),dp(64)));recordStatus=null;recordOrb.setOnClickListener(v->toggleRecordOrb());syncRecordOrb();ui.removeCallbacks(recordPulse);ui.post(recordPulse);
        View.OnClickListener brain=v->{long id=audioItems.isEmpty()?0:audioItems.get(Math.max(0,Math.min(audioIndex,audioItems.size()-1))).id;CortexActionExecutor.openBrain(this,id,"Analyze my current Cortex Brief. Connect the audio/context with what needs me, what is waiting, decisions, and useful next actions. Be concise and executable.");};ask.setOnClickListener(brain);nodes.setOnClickListener(brain);return dock;
    }

    @Override LinearLayout satinCard(int radius){return CortexUi.card(this,radius);}
    @Override TextView statusChip(String text,int color){return CortexUi.chip(this,text,color,true);}
    @Override void addSatinNav(LinearLayout root){CortexUi.addBottomNav(this,root,"brief",null);}

    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
}
