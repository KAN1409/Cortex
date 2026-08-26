package com.kareem.cortex;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.util.*;

/** Compact reusable model-proposal strip mounted directly under one specific result. */
public final class ProposalUi {
    /** Absolute UI safety cap; provider/fallback budgets inside ResultProposalEngine are much shorter. */
    private static final long UI_TIMEOUT_MS=45_000L;
    private static final WeakHashMap<LinearLayout,Long> REQUEST_TOKENS=new WeakHashMap<>();
    private static final WeakHashMap<LinearLayout,Long> SEMANTIC_TOKENS=new WeakHashMap<>();
    private static long tokenSeq=0;
    private ProposalUi(){}

    public static void attach(Activity activity,VaultDb db,LinearLayout parent,ResultProposalEngine.Target target){
        if(activity==null||db==null||parent==null||target==null||target.text.trim().isEmpty())return;
        final int dp=CortexUi.dp(activity,1);LinearLayout holder=new LinearLayout(activity);holder.setOrientation(LinearLayout.VERTICAL);holder.setPadding(0,8*dp,0,2*dp);holder.setVisibility(View.GONE);parent.addView(holder);requestInto(activity,db,holder,target);
    }

    private static long beginRequest(LinearLayout holder){synchronized(REQUEST_TOKENS){long t=++tokenSeq;REQUEST_TOKENS.put(holder,t);return t;}}
    private static boolean isCurrent(LinearLayout holder,long token){synchronized(REQUEST_TOKENS){Long x=REQUEST_TOKENS.get(holder);return x!=null&&x==token;}}
    private static long beginSemantic(LinearLayout holder,ResultProposalEngine.Target target){synchronized(SEMANTIC_TOKENS){Long old=SEMANTIC_TOKENS.get(holder);if(old!=null){CortexSemanticOperation.Snapshot s=CortexSemanticOperation.get(old);if(s!=null&&!s.terminal())CortexSemanticOperation.cancel(old,"Superseded by a fresh proposal generation");}long op=CortexSemanticOperation.begin("PROPOSALS",target==null?"":target.resultKey);SEMANTIC_TOKENS.put(holder,op);return op;}}
    private static boolean currentSemantic(LinearLayout holder,long op){synchronized(SEMANTIC_TOKENS){Long x=SEMANTIC_TOKENS.get(holder);return x!=null&&x==op;}}

    private static void requestInto(Activity activity,VaultDb db,LinearLayout holder,ResultProposalEngine.Target target){
        if(activity==null||db==null||holder==null||target==null)return;final long requestToken=beginRequest(holder);final long semanticToken=beginSemantic(holder,target);CortexSemanticOperation.progress(semanticToken,"Generating useful next moves",3,"Proposal generation is independent from the already-visible result");
        // Fast Answer First invariant: once the owning result is visible/terminal, deferred proposal
        // enrichment must never make that surface look busy again. Keep the strip absent while work is
        // in flight; reveal it only at a proposal terminal state (suggestions, no-op, or recoverable error).
        holder.removeAllViews();holder.setVisibility(View.GONE);
        Handler main=new Handler(Looper.getMainLooper());
        Runnable timeout=()->{if(!isCurrent(holder,requestToken)||!currentSemantic(holder,semanticToken)||activity.isFinishing()||activity.isDestroyed()||holder.getParent()==null)return;CortexSemanticOperation.timeout(semanticToken,"Proposal UI timeout after "+UI_TIMEOUT_MS+" ms · stale callbacks will be ignored");holder.removeAllViews();renderRecoverableState(activity,db,holder,target,"Suggestions are taking too long","Cortex exhausted the remote and fallback budgets. Retry starts a fresh generation; stale responses are ignored.",ExternalBrainProvider.configurationHint(activity),true);};
        main.postDelayed(timeout,UI_TIMEOUT_MS);
        ResultProposalEngine.request(activity,target,(proposals,provider,error)->{
            if(!isCurrent(holder,requestToken)||!currentSemantic(holder,semanticToken))return;main.removeCallbacks(timeout);if(activity.isFinishing()||activity.isDestroyed()||holder.getParent()==null){CortexSemanticOperation.cancel(semanticToken,"Proposal result arrived after its UI surface was gone");return;}holder.removeAllViews();
            if(proposals.isEmpty()){boolean failed=error!=null&&!error.trim().isEmpty();String title=failed?"Couldn’t generate suggestions":"No useful next move found";String detail=failed?clip(error,240):"Cortex checked this result but did not find a meaningful next move yet.";if(failed)CortexSemanticOperation.fail(semanticToken,"PROPOSALS_FAILED · "+clip(error,280));else CortexSemanticOperation.complete(semanticToken,"PROPOSALS_READY · 0 useful proposals · "+safeProvider(provider));renderRecoverableState(activity,db,holder,target,title,detail,provider,failed);return;}
            CortexSemanticOperation.complete(semanticToken,"PROPOSALS_READY · "+proposals.size()+" proposal(s) · "+safeProvider(provider));renderProposals(activity,db,holder,target,proposals,provider);
        });
    }

    private static void renderRecoverableState(Activity activity,VaultDb db,LinearLayout holder,ResultProposalEngine.Target target,String title,String detail,String provider,boolean failed){
        final int dp=CortexUi.dp(activity,1);holder.setVisibility(View.VISIBLE);LinearLayout headRow=new LinearLayout(activity);headRow.setGravity(Gravity.CENTER_VERTICAL);headRow.addView(CortexUi.glyph(activity,"brain",failed?CortexUi.RED:CortexUi.YELLOW,true),new LinearLayout.LayoutParams(30*dp,30*dp));LinearLayout copy=new LinearLayout(activity);copy.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(activity,title,9,CortexUi.TEXT);CortexUi.medium(h);copy.addView(h);TextView d=CortexUi.plain(activity,detail,8,CortexUi.MUTED);d.setPadding(0,2*dp,0,0);copy.addView(d);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(7*dp,0,0,0);headRow.addView(copy,cp);holder.addView(headRow);
        TextView retry=CortexUi.action(activity,"Retry suggestions",failed?CortexUi.RED:CortexUi.YELLOW,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,40*dp);rp.setMargins(0,8*dp,0,0);holder.addView(retry,rp);retry.setOnClickListener(v->{try{ResultProposalEngine.invalidate(db,target);}catch(Throwable ignored){}requestInto(activity,db,holder,target);});
        if(provider!=null&&!provider.trim().isEmpty()){TextView model=CortexUi.plain(activity,"Last provider: "+provider,7,CortexUi.FAINT);model.setPadding(0,4*dp,0,0);holder.addView(model);}
    }

    private static void renderProposals(Activity activity,VaultDb db,LinearLayout holder,ResultProposalEngine.Target target,ArrayList<ResultProposalEngine.Proposal> proposals,String provider){
        final int dp=CortexUi.dp(activity,1);holder.setVisibility(View.VISIBLE);LinearLayout headRow=new LinearLayout(activity);headRow.setGravity(Gravity.CENTER_VERTICAL);headRow.addView(CortexUi.glyph(activity,"brain",CortexUi.RED,true),new LinearLayout.LayoutParams(30*dp,30*dp));TextView head=CortexUi.plain(activity,"Cortex suggests",9,CortexUi.TEXT);CortexUi.medium(head);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(7*dp,0,0,0);headRow.addView(head,hp);holder.addView(headRow);
        HorizontalScrollView scroller=new HorizontalScrollView(activity);scroller.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,7*dp,0,2*dp);scroller.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        for(ResultProposalEngine.Proposal p:proposals){int color=proposalColor(p);TextView chip=CortexUi.chip(activity,p.title,color,false);chip.setSingleLine(true);chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setMaxWidth(290*dp);int wash=Color.argb(9,Color.red(color),Color.green(color),Color.blue(color));CortexUi.pressable(activity,chip,CortexUi.round(activity,wash,Color.argb(68,Color.red(color),Color.green(color),Color.blue(color)),999));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,38*dp);if(row.getChildCount()>0)cp.setMargins(7*dp,0,0,0);row.addView(chip,cp);chip.setOnClickListener(v->open(activity,db,target,p));}
        holder.addView(scroller,new LinearLayout.LayoutParams(-1,45*dp));if(provider!=null&&!provider.trim().isEmpty()){TextView model=CortexUi.plain(activity,"Suggested by "+provider,7,CortexUi.FAINT);model.setPadding(0,1*dp,0,0);holder.addView(model);}
    }

    private static int proposalColor(ResultProposalEngine.Proposal p){if(p==null)return CortexUi.RED;if("BRAIN_PROMPT".equals(p.execution))return CortexUi.RED;String t=p.actionType==null?"":p.actionType;if("WAIT_FOR".equals(t)||"REMINDER".equals(t)||"FOLLOW_UP".equals(t))return CortexUi.ORANGE;if("CALENDAR_EVENT".equals(t)||"CALENDAR_RESCHEDULE".equals(t))return CortexUi.YELLOW;if("TASK".equals(t)||"PROJECT_LINK".equals(t)||"KNOWLEDGE_NOTE".equals(t))return CortexUi.GREEN;if("CALL".equals(t)||"MESSAGE_DRAFT".equals(t)||"EMAIL_DRAFT".equals(t))return CortexUi.RED;return CortexUi.YELLOW;}

    private static void open(Activity a,VaultDb db,ResultProposalEngine.Target target,ResultProposalEngine.Proposal p){
        if("BRAIN_PROMPT".equals(p.execution)){String prompt=p.prompt==null?"":p.prompt.trim();if(target.sourceItemId>0){/* Do not duplicate its OCR/transcript: item_id is authoritative. */CortexActionExecutor.openBrain(a,target.sourceItemId,prompt);return;}StringBuilder q=new StringBuilder(prompt);q.append("\n\nCORTEX RESULT CONTEXT\nSurface: ").append(target.surface).append("\nTitle: ").append(target.title).append("\nResult:\n").append(clip(target.text,900));CortexActionExecutor.openBrain(a,0,q.toString());return;}
        if(!"ACTION".equals(p.execution))return;String key=safeKey(target.resultKey)+"_"+safeKey(p.id);long syntheticJob=target.sourceItemId>0?target.sourceItemId:Math.max(1,Math.abs((long)target.fingerprint().hashCode()));JSONArray missing=normalizedMissing(p,target);String status=missing.length()==0?"READY":"NEEDS_DETAILS";BrainActionStore.Action action=new BrainActionStore.Action(0,syntheticJob,key,p.actionType,p.title,status,p.confidence,p.payload,missing,target.sourceItemId,target.sourceType,clip(target.text,420));CortexActionDispatcher.preview(a,db,action);
    }

    private static JSONArray normalizedMissing(ResultProposalEngine.Proposal p,ResultProposalEngine.Target target){LinkedHashSet<String> xs=new LinkedHashSet<>();for(int i=0;i<p.missing.length();i++){String x=p.missing.optString(i,"").trim();if(!x.isEmpty())xs.add(x);}if("PROJECT_LINK".equals(p.actionType)){xs.remove("confirmed project selection");if(target.sourceItemId<=0)xs.add("source capture");}if("OPEN_APP".equals(p.actionType)){String pkg=p.payload.optString("package","").trim();if(pkg.isEmpty())xs.add("exact app package");}JSONArray out=new JSONArray();for(String x:xs)out.put(x);return out;}
    public static boolean cloudAllowedForMemory(Activity a,KnowledgeItem k){if(a==null||k==null)return false;try{return CloudEvidencePolicy.canSend(a,k);}catch(Throwable ignored){return false;}}
    private static String safeKey(String s){String x=s==null?"":s.replaceAll("[^A-Za-z0-9_-]","");return x.isEmpty()?"result":x;}
    private static String safeProvider(String s){String x=s==null?"":s.trim();return x.isEmpty()?"provider unknown":x;}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
