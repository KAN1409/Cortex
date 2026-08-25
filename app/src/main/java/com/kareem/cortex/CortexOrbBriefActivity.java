package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Satin Brief variant where capture is primary and playback is secondary. */
public class CortexOrbBriefActivity extends SatinBriefActivity {
    CortexRingButton recordOrb;TextView recordStatus;
    final Runnable recordPulse=new Runnable(){@Override public void run(){syncRecordOrb();if(!destroyed&&recordOrb!=null)ui.postDelayed(this,140);}};

    @Override View audioCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=satinCard(24);card.setPadding(dp(15),dp(15),dp(15),dp(14));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);BriefGlyphView source=new BriefGlyphView(this,"waveRing",RED);source.setBackground(CortexUi.round(this,INSET,Color.argb(20,255,255,255),18));top.addView(source,new LinearLayout.LayoutParams(dp(62),dp(62)));
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tbp=new LinearLayout.LayoutParams(0,-2,1);tbp.setMargins(dp(13),0,dp(8),0);top.addView(titleBox,tbp);
        audioTitle=CortexUi.plain(this,"Voice recording",19,PRIMARY);CortexUi.medium(audioTitle);audioTitle.setMaxLines(1);titleBox.addView(audioTitle);audioSub=CortexUi.plain(this,"Voice note",11,SECONDARY);audioSub.setPadding(0,dp(5),0,0);titleBox.addView(audioSub);top.addView(statusChip("VOICE",CortexUi.ACCENT),new LinearLayout.LayoutParams(-2,dp(32)));card.addView(top);

        LinearLayout seekRow=new LinearLayout(this);seekRow.setGravity(Gravity.CENTER_VERTICAL);seekRow.setPadding(0,dp(13),0,0);timeNow=label("00:00",10,SECONDARY);seekRow.addView(timeNow,new LinearLayout.LayoutParams(dp(46),dp(38)));scrub=new CortexScrubberView(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(38),1);sp.setMargins(dp(4),0,dp(4),0);seekRow.addView(scrub,sp);timeEnd=label("00:00",10,SECONDARY);timeEnd.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);seekRow.addView(timeEnd,new LinearLayout.LayoutParams(dp(46),dp(38)));card.addView(seekRow);

        LinearLayout transport=new LinearLayout(this);transport.setGravity(Gravity.CENTER);transport.setPadding(0,0,0,dp(4));CortexRingButton prev=new CortexRingButton(this);prev.setGlyph(CortexRingButton.Glyph.PREVIOUS);prev.setAccent(INFO);prev.setOnClickListener(v->selectAudio(audioIndex-1));transport.addView(prev,new LinearLayout.LayoutParams(dp(58),dp(58)));playRing=new CortexRingButton(this);playRing.setGlyph(CortexRingButton.Glyph.PLAY);playRing.setProgress(0);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(76),dp(76));pp.setMargins(dp(18),0,dp(18),0);transport.addView(playRing,pp);playRing.setOnClickListener(v->togglePlayback());CortexRingButton next=new CortexRingButton(this);next.setGlyph(CortexRingButton.Glyph.NEXT);next.setAccent(INFO);next.setOnClickListener(v->selectAudio(audioIndex+1));transport.addView(next,new LinearLayout.LayoutParams(dp(58),dp(58)));card.addView(transport);
        scrub.setListener((fraction,finished)->{if(player==null||!prepared)return;int d=player.getDuration(),pos=Math.max(0,Math.min(d,(int)(d*fraction)));timeNow.setText(fmt(pos));playRing.setProgress(fraction);if(finished)try{player.seekTo(pos);}catch(Throwable ignored){}});

        View line=new View(this);line.setBackgroundColor(Color.argb(24,255,255,255));card.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(0,dp(12),0,dp(7));recordOrb=new CortexRingButton(this);recordOrb.setGlyph(CortexRingButton.Glyph.RECORD);recordOrb.setAccent(CortexUi.SIGNAL);hero.addView(recordOrb,new LinearLayout.LayoutParams(dp(112),dp(112)));recordStatus=CortexUi.plain(this,"Record new memory",11,SECONDARY);CortexUi.medium(recordStatus);recordStatus.setGravity(Gravity.CENTER);recordStatus.setPadding(0,dp(3),0,0);hero.addView(recordStatus,new LinearLayout.LayoutParams(-1,dp(28)));TextView hint=label("CAPTURE IS PRIMARY  ·  PLAYBACK STAYS ABOVE",8,MUTED2);hint.setGravity(Gravity.CENTER);hero.addView(hint,new LinearLayout.LayoutParams(-1,dp(22)));card.addView(hero);recordOrb.setOnClickListener(v->toggleRecordOrb());syncRecordOrb();ui.removeCallbacks(recordPulse);ui.post(recordPulse);

        LinearLayout insights=new LinearLayout(this);insights.setGravity(Gravity.CENTER_VERTICAL);insights.addView(insight("⌁",Math.max(1,s.worthKnowing.size())+" key points",CortexUi.ACCENT),new LinearLayout.LayoutParams(0,dp(28),1));insights.addView(insight("◇",s.decisions.size()+" decisions",CortexUi.VIOLET),new LinearLayout.LayoutParams(0,dp(28),1));insights.addView(insight("↗",s.actions.size()+" actions",CortexUi.ACCENT),new LinearLayout.LayoutParams(0,dp(28),1));card.addView(insights);
        ui.post(()->selectAudio(Math.min(audioIndex,audioItems.size()-1)));return card;
    }

    void toggleRecordOrb(){
        if(CortexRecordService.isRecording(this)){try{startService(new Intent(this,CortexRecordService.class).setAction(CortexRecordService.ACTION_STOP));}catch(Throwable ignored){}ui.postDelayed(this::syncRecordOrb,90);return;}
        boolean permission=new AudioCapture().hasPermission(this),asr=GroqKeyStore.has(this)||GeminiKeyStore.has(this);if(!permission||!asr){openCaptureSetup();return;}
        try{Intent i=new Intent(this,CortexRecordService.class).setAction(CortexRecordService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);ui.postDelayed(this::syncRecordOrb,120);}catch(Throwable e){openCaptureSetup();}
    }
    void openCaptureSetup(){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode","voice");startActivity(i);}catch(Throwable ignored){}}
    void syncRecordOrb(){if(recordOrb==null)return;boolean running=CortexRecordService.isRecording(this);recordOrb.setGlyph(running?CortexRingButton.Glyph.STOP:CortexRingButton.Glyph.RECORD);recordOrb.setAccent(running?CortexUi.SIGNAL:CortexUi.SIGNAL);if(running){long elapsed=Math.max(0,System.currentTimeMillis()-CortexRecordService.startedAt(this));recordOrb.setProgress((elapsed%60000L)/60000f);if(recordStatus!=null)recordStatus.setText("Recording  ·  tap to stop");}else{recordOrb.setProgress(0f);if(recordStatus!=null)recordStatus.setText("Record new memory");}}

    @Override protected void onResume(){super.onResume();ui.removeCallbacks(recordPulse);if(recordOrb!=null)ui.post(recordPulse);}
    @Override protected void onPause(){ui.removeCallbacks(recordPulse);super.onPause();}
}
