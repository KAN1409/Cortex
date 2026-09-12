package com.kareem.cortex;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public final class VoiceDetailActivity extends Activity {
    VaultDb db;
    long itemId;
    MediaPlayer player;
    TextView play;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        db=new VaultDb(this);
        itemId=getIntent().getLongExtra("item_id",0);
        build();
    }

    @Override protected void onDestroy(){
        stopPlayer();
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        super.onDestroy();
    }

    void build(){
        KnowledgeItem k=db.getById(itemId);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);

        ScrollView sv=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(12),dp(18),dp(30));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"memory",null);
        setContentView(root);
        CortexUi.fitSystemBars(this,root);

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        TextView h=CortexUi.plain(this,"Voice Memory",25,CortexUi.TEXT);
        CortexUi.medium(h);
        head.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(head);

        if(k==null){
            TextView missing=CortexUi.text(this,"This recording could not be found.",13,CortexUi.MUTED);
            missing.setPadding(0,dp(20),0,0);
            body.addView(missing);
            return;
        }

        TextView title=CortexUi.text(this,displayTitle(k),22,CortexUi.TEXT);
        title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        CortexUi.medium(title);
        title.setPadding(0,dp(12),0,0);
        body.addView(title);

        TextView meta=CortexUi.plain(this,meta(k),10,CortexUi.MUTED);
        meta.setPadding(0,dp(6),0,dp(12));
        body.addView(meta);

        if(!safe(k.attachmentPath).isEmpty()&&new File(k.attachmentPath).exists()){
            play=CortexUi.action(this,"PLAY RECORDING",CortexUi.GREEN,true);
            body.addView(play,new LinearLayout.LayoutParams(-1,dp(48)));
            play.setOnClickListener(v->toggle(k.attachmentPath));
        }

        section(body,"Transcript",transcript(k),CortexUi.TEXT);

        String understood=understood(k);
        section(body,"What Cortex understood",
                understood.isEmpty()?"No distinct semantic summary yet. The transcript is preserved as evidence.":understood,
                understood.isEmpty()?CortexUi.MUTED:CortexUi.TEXT);

        ArrayList<String> actions=db.actions(k.id);
        if(!actions.isEmpty()){
            body.addView(CortexUi.section(this,"Useful next moves"));
            for(String a:actions){
                TextView row=CortexUi.text(this,"• "+a,12,CortexUi.TEXT);
                row.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                row.setPadding(dp(2),dp(5),0,dp(5));
                body.addView(row);
            }
        }

        ArrayList<String> entities=db.entities(k.id);
        if(!entities.isEmpty()){
            body.addView(CortexUi.section(this,"Detected context"));
            for(String e:entities){
                TextView row=CortexUi.text(this,e,11,CortexUi.MUTED);
                row.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                row.setPadding(dp(2),dp(4),0,dp(4));
                body.addView(row);
            }
        }

        body.addView(CortexUi.section(this,"Storage"));
        TextView storage=CortexUi.text(this,
                "Recording file: "+(safe(k.attachmentPath).isEmpty()?"not available":"kept locally")+
                "\nTranscript: kept with this Voice Memory"+
                "\nCreated: "+new SimpleDateFormat("d MMM yyyy · HH:mm",Locale.getDefault()).format(new Date(k.createdAt)),
                11,CortexUi.MUTED);
        storage.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        body.addView(storage);
    }

    void section(LinearLayout body,String heading,String text,int color){
        body.addView(CortexUi.section(this,heading));
        TextView t=CortexUi.text(this,safe(text).isEmpty()?"Not available yet.":text,13,color);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        t.setTextIsSelectable(true);
        t.setPadding(0,dp(4),0,dp(6));
        body.addView(t);
    }

    void toggle(String path){
        try{
            if(player!=null&&player.isPlaying()){
                player.pause();
                play.setText("PLAY RECORDING");
                return;
            }
            if(player==null){
                player=new MediaPlayer();
                player.setDataSource(path);
                player.prepare();
                player.setOnCompletionListener(mp->{if(play!=null)play.setText("PLAY RECORDING");});
            }
            player.start();
            play.setText("PAUSE");
        }catch(Throwable e){
            if(play!=null)play.setText("PLAYBACK UNAVAILABLE");
        }
    }

    void stopPlayer(){
        if(player!=null){
            try{player.stop();}catch(Throwable ignored){}
            try{player.release();}catch(Throwable ignored){}
            player=null;
        }
    }

    String displayTitle(KnowledgeItem k){
        String t=safe(k.title);
        if(t.isEmpty()||"Voice recording".equalsIgnoreCase(t)||"Voice note".equalsIgnoreCase(t)){
            String tr=transcript(k);
            if(!tr.isEmpty())return VoiceTextPresentation.compactTitle(tr);
            return "Voice note";
        }
        return t;
    }

    String transcript(KnowledgeItem k){
        String t=safe(k.extractedText);
        if(t.isEmpty())t=safe(k.rawText);
        return t;
    }

    String understood(KnowledgeItem k){
        String s=safe(k.summary),t=transcript(k);
        return VoiceTextPresentation.materiallyDifferent(s,t)?s:"";
    }

    String meta(KnowledgeItem k){
        StringBuilder b=new StringBuilder();
        b.append(new SimpleDateFormat("d MMM · HH:mm",Locale.getDefault()).format(new Date(k.createdAt)));
        try{
            JSONObject m=new JSONObject(safe(k.metadataJson));
            long dur=m.optLong("duration_ms",m.optLong("audio_duration_ms",0));
            if(dur>0){
                long sec=dur/1000;
                b.append("  •  ").append(String.format(Locale.US,"%d:%02d",sec/60,sec%60));
            }
            String lang=safe(m.optString("language",""));
            if(!lang.isEmpty())b.append("  •  ").append(lang);
        }catch(Throwable ignored){}
        b.append("  •  ").append("analyzed".equalsIgnoreCase(k.status)?"ready":"processing");
        return b.toString();
    }

    String safe(String s){return s==null?"":s.trim();}
}
