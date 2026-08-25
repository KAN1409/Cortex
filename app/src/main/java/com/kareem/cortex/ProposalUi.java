package com.kareem.cortex;

import android.app.*;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Compact reusable model-proposal strip mounted directly under one specific result. */
public final class ProposalUi {
    private ProposalUi(){}

    public static void attach(Activity activity,VaultDb db,LinearLayout parent,ResultProposalEngine.Target target){
        if(activity==null||db==null||parent==null||target==null||target.text.trim().isEmpty())return;
        final int dp=CortexUi.dp(activity,1);
        LinearLayout holder=new LinearLayout(activity);holder.setOrientation(LinearLayout.VERTICAL);holder.setPadding(0,8*dp,0,2*dp);
        TextView thinking=CortexUi.plain(activity,"✦ Thinking of useful next moves…",9,CortexUi.MUTED);holder.addView(thinking);parent.addView(holder);
        ResultProposalEngine.request(activity,target,(proposals,provider,error)->{
            if(activity.isFinishing()||activity.isDestroyed()||holder.getParent()==null)return;holder.removeAllViews();
            if(proposals.isEmpty()){
                if(!error.isEmpty()){TextView unavailable=CortexUi.plain(activity,"✦ Suggestions waiting for an available model",8,CortexUi.FAINT);holder.addView(unavailable);}else holder.setVisibility(View.GONE);
                return;
            }
            holder.setVisibility(View.VISIBLE);TextView head=CortexUi.plain(activity,"✦ Cortex suggests",9,CortexUi.ACCENT);CortexUi.medium(head);holder.addView(head);
            HorizontalScrollView scroller=new HorizontalScrollView(activity);scroller.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,6*dp,0,2*dp);scroller.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
            for(ResultProposalEngine.Proposal p:proposals){
                int color=proposalColor(p);TextView chip=CortexUi.chip(activity,p.title,color,false);chip.setSingleLine(true);chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setMaxWidth(290*dp);CortexUi.pressable(activity,chip,CortexUi.round(activity,CortexUi.SURFACE_2,CortexUi.BORDER,999));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,38*dp);if(row.getChildCount()>0)cp.setMargins(7*dp,0,0,0);row.addView(chip,cp);chip.setOnClickListener(v->open(activity,db,target,p));
            }
            holder.addView(scroller,new LinearLayout.LayoutParams(-1,44*dp));
            if(provider!=null&&!provider.trim().isEmpty()){TextView model=CortexUi.plain(activity,"Suggested by "+provider,7,CortexUi.FAINT);model.setPadding(0,1*dp,0,0);holder.addView(model);}
        });
    }

    private static int proposalColor(ResultProposalEngine.Proposal p){
        if(p==null)return CortexUi.ACCENT;if("BRAIN_PROMPT".equals(p.execution))return CortexUi.VIOLET;String t=p.actionType==null?"":p.actionType;
        if("WAIT_FOR".equals(t)||"REMINDER".equals(t)||"FOLLOW_UP".equals(t))return CortexUi.AMBER;
        if("CALENDAR_EVENT".equals(t)||"CALENDAR_RESCHEDULE".equals(t))return CortexUi.ACCENT;
        if("TASK".equals(t)||"PROJECT_LINK".equals(t)||"KNOWLEDGE_NOTE".equals(t))return CortexUi.SAGE;
        if("CALL".equals(t)||"MESSAGE_DRAFT".equals(t)||"EMAIL_DRAFT".equals(t))return CortexUi.ACCENT;
        return CortexUi.VIOLET;
    }

    private static void open(Activity a,VaultDb db,ResultProposalEngine.Target target,ResultProposalEngine.Proposal p){
        if("BRAIN_PROMPT".equals(p.execution)){
            StringBuilder q=new StringBuilder();if(p.prompt!=null&&!p.prompt.trim().isEmpty())q.append(p.prompt.trim());
            q.append("\n\nCORTEX RESULT CONTEXT\nSurface: ").append(target.surface).append("\nTitle: ").append(target.title).append("\nResult:\n").append(clip(target.text,1800));
            CortexActionExecutor.openBrain(a,target.sourceItemId,q.toString());return;
        }
        if(!"ACTION".equals(p.execution))return;
        String key=safeKey(target.resultKey)+"_"+safeKey(p.id);long syntheticJob=target.sourceItemId>0?target.sourceItemId:Math.max(1,Math.abs((long)target.fingerprint().hashCode()));
        String status=p.missing.length()==0?"READY":"NEEDS_DETAILS";
        BrainActionStore.Action action=new BrainActionStore.Action(0,syntheticJob,key,p.actionType,p.title,status,p.confidence,p.payload,p.missing,target.sourceItemId,target.sourceType,clip(target.text,420));
        CortexActionDispatcher.preview(a,db,action);
    }

    public static boolean cloudAllowedForMemory(Activity a,KnowledgeItem k){
        if(a==null||k==null)return false;try{return CloudEvidencePolicy.canSend(a,k);}catch(Throwable ignored){return false;}
    }

    private static String safeKey(String s){String x=s==null?"":s.replaceAll("[^A-Za-z0-9_-]","");return x.isEmpty()?"result":x;}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
