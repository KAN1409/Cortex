package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Production Brain route using the v51 app-wide conversation-first Cortex UI. */
public final class ProposalAskCortexActivity extends AskCortexActivity {

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(9),dp(14),dp(7));
        TextView h=CortexUi.plain(this,"Cortex",22,CortexUi.TEXT);CortexUi.medium(h);header.addView(h,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView modeMenu=CortexUi.chip(this,"Combined ▾",CortexUi.MUTED,false);modeMenu.setTag("modeMenu");modeMenu.setGravity(Gravity.CENTER);modeMenu.setOnClickListener(v->toggleModeChooser());header.addView(modeMenu,new LinearLayout.LayoutParams(dp(102),dp(36)));
        TextView settings=CortexUi.plain(this,"⋮",24,CortexUi.MUTED);settings.setGravity(Gravity.CENTER);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(40),dp(40));hp.setMargins(dp(4),0,0,0);header.addView(settings,hp);root.addView(header);

        modeBar=new LinearLayout(this);modeBar.setOrientation(LinearLayout.HORIZONTAL);modeBar.setPadding(dp(18),dp(2),dp(18),dp(5));modeBar.setVisibility(View.GONE);addMode("Your data","your_data");addMode("External","external");addMode("Combined","combined");root.addView(modeBar,new LinearLayout.LayoutParams(-1,dp(43)));
        modeNote=CortexUi.plain(this,"",10,CortexUi.MUTED);modeNote.setPadding(dp(20),0,dp(20),dp(5));modeNote.setVisibility(View.GONE);root.addView(modeNote);
        contextNote=CortexUi.plain(this,"",11,CortexUi.MUTED);contextNote.setPadding(dp(20),dp(4),dp(20),dp(7));contextNote.setVisibility(View.GONE);root.addView(contextNote);styleModes();

        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);conversation=new LinearLayout(this);conversation.setOrientation(LinearLayout.VERTICAL);conversation.setPadding(dp(18),dp(12),dp(18),dp(14));scroll.addView(conversation);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));suggestions();

        LinearLayout contextStrip=new LinearLayout(this);contextStrip.setGravity(Gravity.CENTER_VERTICAL);contextStrip.setPadding(dp(18),0,dp(18),dp(5));TextView context=CortexUi.plain(this,"Chat · Cortex memory",11,CortexUi.MUTED);contextStrip.addView(context,new LinearLayout.LayoutParams(0,dp(28),1));TextView sources=CortexUi.plain(this,"Sources ▾",11,CortexUi.MUTED);sources.setGravity(Gravity.CENTER);sources.setOnClickListener(v->{boolean show=modeNote.getVisibility()!=View.VISIBLE;modeNote.setVisibility(show?View.VISIBLE:View.GONE);sources.setText(show?"Sources ▴":"Sources ▾");});contextStrip.addView(sources,new LinearLayout.LayoutParams(-2,dp(28)));root.addView(contextStrip);

        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(12),dp(7),dp(12),dp(10));input=new EditText(this);input.setHint("Ask Cortex anything…");input.setHintTextColor(CortexUi.FAINT);input.setTextColor(CortexUi.TEXT);input.setTextSize(15);input.setMinLines(1);input.setMaxLines(4);input.setSingleLine(false);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setPadding(dp(15),dp(10),dp(10),dp(10));input.setBackground(CortexUi.round(this,CortexUi.SURFACE,Color.TRANSPARENT,16));composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));send=CortexUi.action(this,"↑",CortexUi.ACCENT,true);send.setTextSize(22);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(48),dp(48));sp.setMargins(dp(8),0,0,0);composer.addView(send,sp);send.setOnClickListener(v->submit());root.addView(composer);

        CortexUi.addBottomNav(this,root,"ask",null);setContentView(root);
    }

    void toggleModeChooser(){modeBar.setVisibility(modeBar.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}

    @Override void styleModes(){
        if(modeBar!=null)for(int i=0;i<modeBar.getChildCount();i++){TextView t=(TextView)modeBar.getChildAt(i);boolean on=sourceMode.equals(t.getTag());t.setTextColor(on?CortexUi.TEXT:CortexUi.MUTED);t.setBackground(CortexUi.round(this,on?CortexUi.SURFACE_2:CortexUi.SURFACE,Color.TRANSPARENT,999));if(on)CortexUi.medium(t);}
        if(modeNote!=null){if("combined".equals(sourceMode))modeNote.setText("Cortex memory + configured external AI · local fallback available");else if("external".equals(sourceMode))modeNote.setText("External AI only · Cortex memory stays private");else modeNote.setText("Cortex memory only · no cloud memory upload");}
        View root=modeBar==null?null:modeBar.getRootView();if(root!=null){View menu=root.findViewWithTag("modeMenu");if(menu instanceof TextView)((TextView)menu).setText(modeLabel(sourceMode)+" ▾");}
        if(modeBar!=null)for(int i=0;i<modeBar.getChildCount();i++){View v=modeBar.getChildAt(i);v.setOnClickListener(x->{if(busy)return;sourceMode=String.valueOf(x.getTag());styleModes();modeBar.setVisibility(View.GONE);});}
    }

    @Override void finishBusy(){if(destroyed)return;busy=false;if(send!=null){send.setText("↑");send.setEnabled(true);}if(input!=null)input.requestFocus();scrollEnd();}

    @Override void addStructuredActions(LinearLayout card, LocalAskRouter.Result r){
        if(db==null||r.jobId<=0||"your_data".equals(r.sourceMode))return;
        ArrayList<BrainActionStore.Action> xs;try{xs=BrainActionStore.list(db,r.jobId);}catch(Throwable e){return;}if(xs.isEmpty())return;
        TextView head=CortexUi.plain(this,"Next actions",11,CortexUi.MUTED);CortexUi.medium(head);head.setPadding(0,dp(14),0,dp(6));card.addView(head);
        for(BrainActionStore.Action x:xs){TextView action=CortexUi.action(this,(x.ready()?"":"Needs details · ")+x.title,CortexUi.MUTED,false);action.setGravity(Gravity.CENTER_VERTICAL);action.setOnClickListener(v->CortexActionDispatcher.preview(this,db,x));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.setMargins(0,dp(5),0,0);card.addView(action,p);}
        TextView trust=CortexUi.plain(this,"External changes always open as a preview before confirmation.",9,CortexUi.FAINT);trust.setPadding(0,dp(7),0,0);card.addView(trust);
    }

    @Override void addAnswer(LocalAskRouter.Result r,String q,boolean refined){
        int before=conversation==null?0:conversation.getChildCount();super.addAnswer(r,q,refined);if(conversation==null||db==null||r==null||conversation.getChildCount()<=before)return;
        LinearLayout answer=null;for(int i=conversation.getChildCount()-1;i>=before;i--){View v=conversation.getChildAt(i);if(v instanceof LinearLayout){answer=(LinearLayout)v;break;}}if(answer==null)return;
        boolean cloudAllowed=!"your_data".equals(r.sourceMode);long sourceId="combined".equals(r.sourceMode)?Math.max(0,focalItemId):0;String title=q==null||q.trim().isEmpty()?"Cortex answer":"Cortex · "+clip(q,72);
        ResultProposalEngine.Target target=new ResultProposalEngine.Target("Cortex answer","brain_"+(r.jobId>0?r.jobId:System.nanoTime()),title,r.answer,sourceId,"BRAIN_RESULT",cloudAllowed);ProposalUi.attach(this,db,answer,target);
    }

    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
