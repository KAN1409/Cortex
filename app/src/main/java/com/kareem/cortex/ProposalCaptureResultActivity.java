package com.kareem.cortex;

import android.widget.LinearLayout;

/** Capture result with a model-generated micro proposal pass for the exact result being shown. */
public final class ProposalCaptureResultActivity extends CaptureResultActivity {
    @Override void render(KnowledgeItem k){
        super.render(k);
        if(stopped||db==null||content==null||k==null||notReady(k.status))return;
        VisualInsightStore.Insight vi=null;
        if(isImageType(k.type))try{vi=VisualInsightStore.get(db,k.id);}catch(Throwable ignored){}
        String understood=understanding(k,vi);
        StringBuilder result=new StringBuilder();
        if(understood!=null&&!understood.trim().isEmpty())result.append(understood.trim());
        if(k.summary!=null&&!k.summary.trim().isEmpty()&&!contains(result,k.summary)){if(result.length()>0)result.append("\n\n");result.append("Summary: ").append(k.summary.trim());}
        String raw=k.extractedText!=null&&!k.extractedText.trim().isEmpty()?k.extractedText:k.rawText;
        if(raw!=null&&!raw.trim().isEmpty()&&!contains(result,raw)){if(result.length()>0)result.append("\n\n");result.append("Evidence: ").append(clip(raw,1200));}
        if(vi!=null&&vi.ready()&&vi.usefulnessReason!=null&&!vi.usefulnessReason.trim().isEmpty()){result.append("\n\nWhy it may matter: ").append(clip(vi.usefulnessReason,600));}
        if(result.length()==0)return;
        LinearLayout host=CortexUi.card(this,18);host.setPadding(dp(14),dp(9),dp(14),dp(10));
        ResultProposalEngine.Target target=new ResultProposalEngine.Target(
                "Input / Capture result","capture_"+k.id,displayTitle(k,vi),result.toString(),k.id,k.type,
                ProposalUi.cloudAllowedForMemory(this,k));
        ProposalUi.attach(this,db,host,target);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);content.addView(host,p);
    }

    private static boolean notReady(String s){String x=s==null?"":s.trim().toLowerCase(java.util.Locale.ROOT);return x.isEmpty()||"pending".equals(x)||"queued".equals(x)||"analyzing".equals(x)||"processing".equals(x)||x.contains("failed");}
    private static boolean isImageType(String t){return "IMAGE".equals(t)||"SCREENSHOT".equals(t);}
    private static boolean contains(StringBuilder b,String s){if(s==null)return false;String x=s.replaceAll("\\s+"," ").trim();if(x.length()<24)return false;return b.toString().replaceAll("\\s+"," ").contains(x.substring(0,Math.min(120,x.length())));}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
