package com.kareem.cortex;

import android.view.View;
import android.widget.LinearLayout;
import java.util.*;

/** PRIME People / Projects where every visible entity result gets its own grounded proposal pass. */
public final class ProposalPeopleProjectsActivity extends PeopleProjectsActivity {
    @Override void addRow(Row r){
        int before=feed==null?0:feed.getChildCount();
        super.addRow(r);
        if(feed==null||db==null||feed.getChildCount()<before+2)return;
        View divider=feed.getChildAt(feed.getChildCount()-1);View base=feed.getChildAt(feed.getChildCount()-2);
        if(!(base instanceof LinearLayout))return;
        feed.removeView(divider);feed.removeView(base);
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.addView(base,new LinearLayout.LayoutParams(-1,-2));

        ArrayList<ContextRef> recent=r.candidate?new ArrayList<>():safeRecent(r);
        StringBuilder result=new StringBuilder();
        result.append(r.candidate?"Project candidate":"people".equals(mode)?"Identified person":"Confirmed project").append(": ").append(r.name);
        if(r.mentions>0)result.append("\nGrounded references: ").append(r.mentions);
        for(int i=0;i<Math.min(2,recent.size());i++){
            ContextRef x=recent.get(i);result.append("\nContext: ").append(x.title==null?"":x.title);
            if(x.preview!=null&&!x.preview.trim().isEmpty())result.append(" — ").append(proposalClip(x.preview,260));
        }
        long sourceId=recent.isEmpty()?0:recent.get(0).itemId;
        ResultProposalEngine.Target target=new ResultProposalEngine.Target(
                "People / Projects","entity_"+r.kind+"_"+r.id,r.name,result.toString(),sourceId,r.kind,false);
        ProposalUi.attach(this,db,wrap,target);
        feed.addView(wrap,new LinearLayout.LayoutParams(-1,-2));feed.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    @Override void openEntityBrain(Row r,long focal){
        String subject=r.identified?"identified person":"confirmed project";
        CortexActionExecutor.openBrain(this,focal,"Focus on the "+subject+" ‘"+r.name+"’. Use grounded Cortex evidence connected to this exact "+(r.identified?"person":"project")+", including the attached latest evidence when present. Tell me the useful current context, open loops, recent changes, decisions, and the best next actions. Do not infer facts from the name alone.");
    }

    private ArrayList<ContextRef> safeRecent(Row r){try{return recentContext(r);}catch(Throwable e){return new ArrayList<>();}}
    private static String proposalClip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
