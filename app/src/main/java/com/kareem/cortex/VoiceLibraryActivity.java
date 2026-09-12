package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public final class VoiceLibraryActivity extends Activity {
    VaultDb db;
    LinearLayout list;
    EditText search;
    TextView stats;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        db=new VaultDb(this);
        build();
        render("");
    }

    @Override protected void onDestroy(){
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        super.onDestroy();
    }

    void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);

        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(12),dp(18),dp(28));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView h=CortexUi.plain(this,"Voice Memory",28,CortexUi.TEXT);
        CortexUi.medium(h);
        titles.addView(h);
        TextView sub=CortexUi.text(this,
                "Recordings, transcripts, and what Cortex understood — kept together.",
                11,CortexUi.MUTED);
        sub.setPadding(0,dp(2),0,0);
        titles.addView(sub);
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(head);

        stats=CortexUi.plain(this,"",10,CortexUi.MUTED);
        stats.setPadding(dp(2),dp(12),0,dp(8));
        body.addView(stats);

        search=new EditText(this);
        search.setHint("Search recordings, transcript, or meaning");
        search.setTextColor(CortexUi.TEXT);
        search.setHintTextColor(CortexUi.FAINT);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setPadding(dp(14),0,dp(14),0);
        search.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,18));
        body.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));

        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0,dp(14),0,0);
        body.addView(list);

        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){render(s==null?"":s.toString());}
            public void afterTextChanged(Editable e){}
        });

        CortexUi.addBottomNav(this,root,"memory",null);
        setContentView(root);
        CortexUi.fitSystemBars(this,root);
    }

    void render(String q){
        if(list==null||db==null)return;
        list.removeAllViews();
        ArrayList<KnowledgeItem> items=db.voiceSearch(q,500);

        int ready=0,processing=0,failed=0;
        for(KnowledgeItem k:items){
            if("analyzed".equalsIgnoreCase(k.status))ready++;
            else if(k.status!=null&&k.status.toLowerCase(Locale.ROOT).contains("fail"))failed++;
            else processing++;
        }
        stats.setText(items.size()+" recordings  •  "+ready+" ready"+
                (processing>0?"  •  "+processing+" processing":"")+
                (failed>0?"  •  "+failed+" need attention":""));

        if(items.isEmpty()){
            TextView empty=CortexUi.text(this,
                    q==null||q.trim().isEmpty()?
                            "No voice recordings yet. Record a voice note from Capture and it will appear here with its transcript.":
                            "No voice memory matches this search.",
                    13,CortexUi.MUTED);
            empty.setPadding(dp(4),dp(22),dp(4),dp(22));
            list.addView(empty);
            return;
        }

        String lastDay="";
        for(KnowledgeItem k:items){
            String day=dayLabel(k.createdAt);
            if(!day.equals(lastDay)){
                list.addView(CortexUi.section(this,day));
                lastDay=day;
            }
            addCard(k);
        }
    }

    void addCard(KnowledgeItem k){
        LinearLayout card=CortexUi.card(this,20);
        card.setPadding(dp(14),dp(13),dp(14),dp(13));
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        CortexGlyphView icon=CortexUi.glyph(this,"voice",
                "analyzed".equalsIgnoreCase(k.status)?CortexUi.GREEN:CortexUi.ORANGE,true);
        top.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));

        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);
        xp.setMargins(dp(11),0,dp(8),0);
        top.addView(tx,xp);

        String title=displayTitle(k);
        TextView h=CortexUi.text(this,title,15,CortexUi.TEXT);
        h.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        CortexUi.medium(h);
        tx.addView(h);

        TextView meta=CortexUi.plain(this,meta(k),9,CortexUi.MUTED);
        meta.setPadding(0,dp(4),0,0);
        tx.addView(meta);

        card.addView(top);

        String transcript=transcript(k);
        if(!transcript.isEmpty()){
            TextView label=CortexUi.plain(this,"TRANSCRIPT",9,CortexUi.MUTED);
            label.setPadding(0,dp(12),0,dp(3));
            card.addView(label);

            TextView body=CortexUi.text(this,transcript,12,CortexUi.TEXT);
            body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            body.setMaxLines(3);
            card.addView(body);
        }

        String understood=understood(k);
        if(!understood.isEmpty()){
            TextView label=CortexUi.plain(this,"CORTEX UNDERSTOOD",9,CortexUi.MUTED);
            label.setPadding(0,dp(11),0,dp(3));
            card.addView(label);

            TextView body=CortexUi.text(this,understood,12,CortexUi.MUTED);
            body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            body.setMaxLines(3);
            card.addView(body);
        }

        CortexUi.pressable(this,card,CortexUi.velvet(this,20));
        card.setOnClickListener(v->{
            Intent i=new Intent(this,VoiceDetailActivity.class);
            i.putExtra("item_id",k.id);
            startActivity(i);
        });

        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,0,0,dp(10));
        list.addView(card,cp);
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
        String s=safe(k.summary);
        String t=transcript(k);
        if(!VoiceTextPresentation.materiallyDifferent(s,t))return "";
        return s;
    }

    String meta(KnowledgeItem k){
        String when=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(k.createdAt));
        StringBuilder b=new StringBuilder(when);
        long dur=durationMs(k);
        if(dur>0)b.append("  •  ").append(formatDuration(dur));
        String lang=language(k);
        if(!lang.isEmpty())b.append("  •  ").append(lang);
        b.append("  •  ").append(friendlyState(k.status));
        return b.toString();
    }

    long durationMs(KnowledgeItem k){
        try{
            JSONObject m=new JSONObject(safe(k.metadataJson));
            long x=m.optLong("duration_ms",0);
            if(x<=0)x=m.optLong("audio_duration_ms",0);
            return x;
        }catch(Throwable ignored){return 0;}
    }

    String language(KnowledgeItem k){
        try{
            JSONObject m=new JSONObject(safe(k.metadataJson));
            return safe(m.optString("language",""));
        }catch(Throwable ignored){return "";}
    }

    String friendlyState(String s){
        String x=safe(s).toLowerCase(Locale.ROOT);
        if("analyzed".equals(x))return "ready";
        if(x.contains("fail"))return "needs attention";
        return "processing";
    }

    String dayLabel(long ms){
        Calendar now=Calendar.getInstance(),d=Calendar.getInstance();
        d.setTimeInMillis(ms);
        if(sameDay(now,d))return "Today";
        now.add(Calendar.DAY_OF_YEAR,-1);
        if(sameDay(now,d))return "Yesterday";
        return new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(new Date(ms));
    }

    boolean sameDay(Calendar a,Calendar b){
        return a.get(Calendar.YEAR)==b.get(Calendar.YEAR)&&
                a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);
    }

    String formatDuration(long ms){
        long sec=Math.max(0,ms/1000);
        return String.format(Locale.US,"%d:%02d",sec/60,sec%60);
    }

    String safe(String s){return s==null?"":s.trim();}
}
