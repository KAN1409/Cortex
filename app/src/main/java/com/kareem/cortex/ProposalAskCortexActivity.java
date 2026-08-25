package com.kareem.cortex;

import android.view.View;
import android.widget.LinearLayout;

/** Brain surface where every completed answer receives its own model-generated proposal pass. */
public final class ProposalAskCortexActivity extends AskCortexActivity {
    @Override void addAnswer(LocalAskRouter.Result r,String q,boolean refined){
        int before=conversation==null?0:conversation.getChildCount();
        super.addAnswer(r,q,refined);
        if(conversation==null||db==null||r==null||conversation.getChildCount()<=before)return;
        LinearLayout card=null;
        for(int i=conversation.getChildCount()-1;i>=before;i--){View v=conversation.getChildAt(i);if(v instanceof LinearLayout){card=(LinearLayout)v;break;}}
        if(card==null)return;
        boolean cloudAllowed=!"your_data".equals(r.sourceMode);
        long sourceId="combined".equals(r.sourceMode)?Math.max(0,focalItemId):0;
        String title=q==null||q.trim().isEmpty()?"Brain answer":"Brain · "+clip(q,72);
        ResultProposalEngine.Target target=new ResultProposalEngine.Target(
                "Brain answer","brain_"+(r.jobId>0?r.jobId:System.nanoTime()),title,r.answer,sourceId,"BRAIN_RESULT",cloudAllowed);
        ProposalUi.attach(this,db,card,target);
    }

    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
