package com.kareem.cortex;

import android.content.Intent;
import android.view.Gravity;
import android.widget.*;
import java.util.List;

/** Top-level Now destination: v70 cognitive selections first, legacy PRIME fallback only. */
public final class NowActivity extends PremiumHomeActivity {
    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(2),dp(8),dp(2),dp(8));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=CortexUi.plain(this,"Now",30,CortexUi.TEXT);CortexUi.medium(title);titles.addView(title);
        TextView sub=CortexUi.text(this,"What deserves your attention right now.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);titles.addView(sub);
        TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});head.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));
        content.addView(head);

        TextView loading=CortexUi.plain(this,"CHECKING CURRENT PRIORITIES…",9,CortexUi.FAINT);loading.setPadding(dp(2),dp(22),0,dp(10));content.addView(loading);
        CortexUi.addBottomNav(this,root,"now",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;while(content.getChildCount()>1)content.removeViewAt(1);

        // v70 is now the authoritative READ path for Now. It remains read-only to projection
        // tables, so a cognitive failure falls back safely to the established PRIME snapshot.
        List<PrimeBriefStore.Item> cognitive;
        try{cognitive=CognitiveNowReadModel.load(db.getReadableDatabase(),8);}catch(Throwable ignored){cognitive=java.util.Collections.emptyList();}
        if(!cognitive.isEmpty()){
            int shown=0;
            for(String kind:new String[]{"ACTION","WAITING","DECISION","INSIGHT"}){
                int count=0;
                for(PrimeBriefStore.Item x:cognitive)if(kind.equalsIgnoreCase(x.kind))count++;
                if(count==0)continue;
                String heading="ACTION".equals(kind)?"Needs you":"WAITING".equals(kind)?"Waiting":"DECISION".equals(kind)?"Decisions":"Worth knowing now";
                content.addView(CortexUi.section(this,heading));
                for(PrimeBriefStore.Item x:cognitive){if(!kind.equalsIgnoreCase(x.kind))continue;derivedRow(x);shown++;}
            }
            if(shown>0)return;
        }

        int shown=0;
        if(!s.actions.isEmpty()){content.addView(CortexUi.section(this,"Needs you"));for(int i=0;i<Math.min(5,s.actions.size());i++){derivedRow(s.actions.get(i));shown++;}}
        if(!s.waiting.isEmpty()){content.addView(CortexUi.section(this,"Waiting"));for(int i=0;i<Math.min(3,s.waiting.size());i++){derivedRow(s.waiting.get(i));shown++;}}
        if(!s.decisions.isEmpty()){content.addView(CortexUi.section(this,"Decisions"));for(int i=0;i<Math.min(3,s.decisions.size());i++){derivedRow(s.decisions.get(i));shown++;}}
        if(!s.worthKnowing.isEmpty()){content.addView(CortexUi.section(this,"Worth knowing now"));for(int i=0;i<Math.min(4,s.worthKnowing.size());i++){derivedRow(s.worthKnowing.get(i));shown++;}}
        if(shown==0){
            LinearLayout card=CortexUi.card(this,24);card.setPadding(dp(18),dp(24),dp(18),dp(24));
            TextView h=CortexUi.plain(this,"Nothing needs you right now",19,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);
            TextView b=CortexUi.text(this,"Cortex is capturing and rebuilding its live world model. Important evidence will surface here after semantic evaluation.",12,CortexUi.MUTED);b.setPadding(0,dp(7),0,0);card.addView(b);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(14),0,0);content.addView(card,p);
        }
    }
}
