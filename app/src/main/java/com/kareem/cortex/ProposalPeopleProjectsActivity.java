package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** People and projects in the same quiet, information-first language as the rest of Cortex. */
public final class ProposalPeopleProjectsActivity extends PeopleProjectsActivity {
    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),0);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(CortexUi.simpleHeader(this,"People & projects","Grounded context",v->startActivity(new Intent(this,VaultActivity.class))));

        tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);tabs.setPadding(0,dp(10),0,dp(8));addTab("People","people");addTab("Projects","projects");body.addView(tabs,new LinearLayout.LayoutParams(-1,dp(54)));

        LinearLayout sb=new LinearLayout(this);sb.setGravity(Gravity.CENTER_VERTICAL);sb.setPadding(dp(11),dp(3),dp(10),dp(3));sb.setBackground(CortexUi.round(this,CortexUi.SURFACE,Color.TRANSPARENT,15));
        sb.addView(CortexUi.glyph(this,"search",CortexUi.MUTED,false),new LinearLayout.LayoutParams(dp(30),dp(30)));
        search=new EditText(this);search.setHint("Search people or projects");search.setHintTextColor(CortexUi.FAINT);search.setTextColor(CortexUi.TEXT);search.setTextSize(14);search.setSingleLine(true);search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(8),0,0,0);sb.addView(search,new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));sp.setMargins(0,0,0,dp(8));body.addView(sb,sp);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){render(lastRows,lastMode);}public void afterTextChanged(Editable e){}});

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);feed.setPadding(0,dp(5),0,dp(24));sv.addView(feed);body.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"people",null);setContentView(root);styleTabs();
    }

    @Override void styleTabs(){
        if(tabs==null)return;
        for(int i=0;i<tabs.getChildCount();i++){
            TextView t=(TextView)tabs.getChildAt(i);boolean on=mode.equals(t.getTag());t.setTextSize(13);t.setTextColor(on?CortexUi.TEXT:CortexUi.MUTED);
            t.setBackground(CortexUi.round(this,on?CortexUi.SURFACE_2:Color.TRANSPARENT,Color.TRANSPARENT,12));if(on)CortexUi.medium(t);
        }
    }

    @Override void addRow(Row r){
        boolean focus=!r.candidate&&r.mentions>=4;int color=r.candidate?CortexUi.YELLOW:CortexUi.LIME;String icon="people".equals(mode)?"person":"project";
        LinearLayout card=CortexUi.card(this,16);card.setPadding(0,0,0,0);
        LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(dp(11),dp(focus?12:9),dp(10),dp(focus?11:9));
        main.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(focus?38:34),dp(focus?38:34)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(7),0);main.addView(tx,xp);
        TextView n=CortexUi.text(this,r.name,focus?16:15,CortexUi.TEXT);CortexUi.medium(n);n.setMaxLines(2);tx.addView(n);
        String meta=r.candidate?"Project candidate · confirm first":(r.identified?"Identified person":"Confirmed project")+(r.mentions>0?" · "+r.mentions+" grounded reference"+(r.mentions==1?"":"s"):"");
        TextView m=CortexUi.plain(this,meta,10,r.candidate?CortexUi.YELLOW:CortexUi.MUTED);m.setPadding(0,dp(3),0,0);tx.addView(m);
        main.addView(CortexUi.chip(this,r.candidate?"Review":"Context",color,false),new LinearLayout.LayoutParams(-2,dp(30)));card.addView(main);main.setOnClickListener(v->detail(r));

        ArrayList<ContextRef> recent=r.candidate?new ArrayList<>():safeRecent(r);StringBuilder result=new StringBuilder();result.append(r.candidate?"Project candidate":"people".equals(mode)?"Identified person":"Confirmed project").append(": ").append(r.name);if(r.mentions>0)result.append("\nGrounded references: ").append(r.mentions);
        for(int i=0;i<Math.min(2,recent.size());i++){ContextRef x=recent.get(i);result.append("\nContext: ").append(x.title==null?"":x.title);if(x.preview!=null&&!x.preview.trim().isEmpty())result.append(" — ").append(proposalClip(x.preview,260));}
        long sourceId=recent.isEmpty()?0:recent.get(0).itemId;LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);host.setPadding(dp(12),0,dp(10),dp(focus?10:7));card.addView(host);
        ProposalUi.attach(this,db,host,new ResultProposalEngine.Target("People / Projects","entity_"+r.kind+"_"+r.id,r.name,result.toString(),sourceId,r.kind,false));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);if(feed.getChildCount()>0)cp.setMargins(0,dp(7),0,0);feed.addView(card,cp);
    }

    @Override void openEntityBrain(Row r,long focal){String subject=r.identified?"identified person":"confirmed project";CortexActionExecutor.openBrain(this,focal,"Focus on the "+subject+" ‘"+r.name+"’. Use grounded Cortex evidence connected to this exact "+(r.identified?"person":"project")+", including the attached latest evidence when present. Tell me the useful current context, open loops, recent changes, decisions, and the best next actions. Do not infer facts from the name alone.");}
    private ArrayList<ContextRef> safeRecent(Row r){try{return recentContext(r);}catch(Throwable e){return new ArrayList<>();}}
    private static String proposalClip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
