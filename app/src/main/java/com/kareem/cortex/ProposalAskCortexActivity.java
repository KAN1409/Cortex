package com.kareem.cortex;

import android.widget.*;
import java.util.ArrayList;

/** Production Brain route. Inherits the conversation-first Cortex UI and only layers proposal behavior on answers. */
public final class ProposalAskCortexActivity extends AskCortexActivity {

    @Override void addStructuredActions(LinearLayout card, LocalAskRouter.Result r){
        if(db==null||r.jobId<=0||"your_data".equals(r.sourceMode))return;
        ArrayList<BrainActionStore.Action> xs;
        try{xs=BrainActionStore.list(db,r.jobId);}catch(Throwable e){return;}
        if(xs.isEmpty())return;

        TextView head=CortexUi.plain(this,"Next actions",11,CortexUi.MUTED);
        CortexUi.medium(head);
        head.setPadding(0,dp(14),0,dp(6));
        card.addView(head);

        for(BrainActionStore.Action x:xs){
            TextView action=CortexUi.action(this,(x.ready()?"":"Needs details · ")+x.title,CortexUi.MUTED,false);
            action.setGravity(android.view.Gravity.CENTER_VERTICAL);
            action.setOnClickListener(v->CortexActionDispatcher.preview(this,db,x));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));
            p.setMargins(0,dp(5),0,0);
            card.addView(action,p);
        }

        TextView trust=CortexUi.plain(this,"External changes always open as a preview before confirmation.",9,CortexUi.FAINT);
        trust.setPadding(0,dp(7),0,0);
        card.addView(trust);
    }

    @Override void addAnswer(LocalAskRouter.Result r,String q,boolean refined){
        int before=conversation==null?0:conversation.getChildCount();
        super.addAnswer(r,q,refined);
        if(conversation==null||db==null||r==null||conversation.getChildCount()<=before)return;

        LinearLayout answer=null;
        for(int i=conversation.getChildCount()-1;i>=before;i--){
            android.view.View v=conversation.getChildAt(i);
            if(v instanceof LinearLayout){answer=(LinearLayout)v;break;}
        }
        if(answer==null)return;

        boolean cloudAllowed=!"your_data".equals(r.sourceMode);
        long sourceId="combined".equals(r.sourceMode)?Math.max(0,focalItemId):0;
        String title=q==null||q.trim().isEmpty()?"Cortex answer":"Cortex · "+clip(q,72);
        ResultProposalEngine.Target target=new ResultProposalEngine.Target(
                "Cortex answer",
                "brain_"+(r.jobId>0?r.jobId:System.nanoTime()),
                title,
                r.answer,
                sourceId,
                "BRAIN_RESULT",
                cloudAllowed);
        ProposalUi.attach(this,db,answer,target);
    }

    private static String clip(String s,int n){
        String x=s==null?"":s.replaceAll("\\s+"," ").trim();
        return x.length()>n?x.substring(0,n)+"…":x;
    }
}
