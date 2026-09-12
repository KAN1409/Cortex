package com.kareem.cortex;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Calm user-facing pipeline status. Technical telemetry remains one tap away. */
public final class CortexPipelineStatusBar {
    private CortexPipelineStatusBar(){}

    public static LinearLayout build(Activity a,VaultDb vault){
        Snapshot s=read(a,vault);LinearLayout card=CortexUi.card(a,18);card.setPadding(CortexUi.dp(a,13),CortexUi.dp(a,11),CortexUi.dp(a,13),CortexUi.dp(a,11));CortexUi.pressable(a,card,CortexUi.velvet(a,18));
        LinearLayout top=new LinearLayout(a);top.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(a);int color=statusColor(s);dot.setBackground(CortexUi.round(a,color,android.graphics.Color.TRANSPARENT,999));top.addView(dot,new LinearLayout.LayoutParams(CortexUi.dp(a,7),CortexUi.dp(a,7)));if(s.processing>0)CortexMotion.breathe(dot);
        TextView label=CortexUi.plain(a,statusLabel(s),10,color);CortexUi.medium(label);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(CortexUi.dp(a,8),0,0,0);top.addView(label,lp);
        String right=s.processing>0?s.processing+" processing":(s.visibleNow>0?s.visibleNow+" judged now":"all caught up");TextView numbers=CortexUi.plain(a,right,9,CortexUi.MUTED);top.addView(numbers);card.addView(top);
        long denom=Math.max(1,s.complete+s.processing);ProgressBar p=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);p.setMax((int)Math.min(Integer.MAX_VALUE,denom));p.setProgress((int)Math.min(p.getMax(),s.complete));p.setProgressTintList(android.content.res.ColorStateList.valueOf(color));p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(CortexUi.BORDER_SOFT));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,CortexUi.dp(a,3));pp.setMargins(0,CortexUi.dp(a,8),0,0);card.addView(p,pp);
        TextView summary=CortexUi.plain(a,s.processing>0?"Cortex is still understanding recent evidence. You can keep using the app.":"Current evidence is processed. Cortex will surface only what earns attention.",9,CortexUi.MUTED);summary.setPadding(0,CortexUi.dp(a,7),0,0);card.addView(summary);
        TextView detail=CortexUi.plain(a,"Observed "+s.raw+"  •  semantic waiting "+s.semanticWaiting+"  •  media queued "+s.mediaQueued+"\nJudged Now "+s.visibleNow+" / "+s.maxNow+"  •  evaluated "+s.judgedEvaluated+"  •  deferred "+s.judgedDeferred+"\nPolicy "+s.policyVersion+"  •  Brain memory "+s.brainMemory,9,CortexUi.FAINT);detail.setPadding(0,CortexUi.dp(a,9),0,0);detail.setVisibility(View.GONE);card.addView(detail);
        TextView hint=CortexUi.plain(a,"Pipeline details",9,CortexUi.FAINT);hint.setPadding(0,CortexUi.dp(a,6),0,0);card.addView(hint);
        card.setOnClickListener(v->{boolean open=detail.getVisibility()!=View.VISIBLE;detail.setVisibility(open?View.VISIBLE:View.GONE);hint.setText(open?"Hide details":"Pipeline details");CortexMotion.haptic(v,false);CortexMotion.enter(detail,0);});
        return card;
    }

    public static Snapshot read(VaultDb vault){Snapshot s=new Snapshot();if(vault==null)return s;SQLiteDatabase db=vault.getReadableDatabase();s.raw=scalar(db,"SELECT COUNT(*) FROM ue_raw_observations");s.complete=scalar(db,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='complete' AND superseded_by=0");s.semanticWaiting=scalar(db,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state IN ('waiting','blocked') AND superseded_by=0");s.mediaQueued=scalar(db,"SELECT COUNT(*) FROM knowledge_items WHERE status IN ('queued','analyzing')");s.nowSelected=scalar(db,"SELECT COUNT(*) FROM ue_cognitive_shadow_decisions WHERE cognitive_surface=1 AND run_id=(SELECT id FROM ue_cognitive_shadow_runs WHERE completed_at>0 ORDER BY id DESC LIMIT 1)");s.brainMemory=scalar(db,"SELECT COUNT(*) FROM knowledge_items WHERE source='semantic_bridge'");try{PrimeBriefStore.Snapshot p=PrimeBriefStore.load(vault);s.visibleNow=p.actions.size()+p.waiting.size()+p.decisions.size()+p.worthKnowing.size();}catch(Throwable ignored){}s.processing=s.semanticWaiting+s.mediaQueued;return s;}

    public static Snapshot read(Activity a,VaultDb vault){Snapshot s=new Snapshot();if(vault==null)return s;SQLiteDatabase db=vault.getReadableDatabase();s.raw=scalar(db,"SELECT COUNT(*) FROM ue_raw_observations");s.complete=scalar(db,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='complete' AND superseded_by=0");s.semanticWaiting=scalar(db,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state IN ('waiting','blocked') AND superseded_by=0");s.mediaQueued=scalar(db,"SELECT COUNT(*) FROM knowledge_items WHERE status IN ('queued','analyzing')");s.nowSelected=scalar(db,"SELECT COUNT(*) FROM ue_cognitive_shadow_decisions WHERE cognitive_surface=1 AND run_id=(SELECT id FROM ue_cognitive_shadow_runs WHERE completed_at>0 ORDER BY id DESC LIMIT 1)");s.brainMemory=scalar(db,"SELECT COUNT(*) FROM knowledge_items WHERE source='semantic_bridge'");s.maxNow=CortexPersonalPolicy.maxNowItems(a);s.policyVersion=CortexPersonalPolicy.version(a);try{PrimeBriefStore.Snapshot p=CortexJudgedBriefProjection.load(a.getApplicationContext(),vault);s.visibleNow=Math.min(s.maxNow,p.actions.size()+p.waiting.size()+p.decisions.size());CortexJudgmentTraceStore.Stats js=CortexJudgmentTraceStore.latestPolicyStats(db,s.policyVersion);s.judgedEvaluated=js.evaluated;s.judgedDeferred=js.deferred;}catch(Throwable ignored){}s.processing=s.semanticWaiting+s.mediaQueued;return s;}
    private static long scalar(SQLiteDatabase db,String sql){Cursor c=null;try{c=db.rawQuery(sql,null);return c.moveToFirst()?c.getLong(0):0;}catch(Throwable ignored){return 0;}finally{if(c!=null)c.close();}}
    private static String statusLabel(Snapshot s){if(s.processing>0)return"CORTEX IS PROCESSING";if(s.raw>0&&s.complete==0)return"PIPELINE NEEDS ATTENTION";return"CORTEX READY";}
    private static int statusColor(Snapshot s){if(s.processing>0)return CortexUi.YELLOW;if(s.raw>0&&s.complete==0)return CortexUi.RED;return CortexUi.GREEN;}
    public static final class Snapshot{public long raw,complete,semanticWaiting,mediaQueued,processing,nowSelected,visibleNow,brainMemory,maxNow,judgedEvaluated,judgedDeferred;public String policyVersion="local";}
}
