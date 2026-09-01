package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** CORTEX UI DESIGN LOCK V1 compact capture/attachment sheet. */
public final class ProposalCaptureActivity extends SatinCaptureActivity {
    @Override void build(){
        Window w=getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.72f;w.setAttributes(lp);
        root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setOnClickListener(v->finish());
        sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(16),dp(13),dp(16),dp(17));sheet.setBackground(CortexUi.round(this,Color.rgb(12,14,12),Color.rgb(55,59,53),24));sheet.setOnClickListener(v->{});

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(new CortexLineIconView(this,"logo",CortexUi.LIME),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView title=CortexUi.plain(this,"Add to Cortex",16,CortexUi.TEXT);CortexUi.medium(title);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(9),0,0,0);head.addView(title,tp);FrameLayout close=new FrameLayout(this);close.setBackground(CortexUi.round(this,Color.rgb(23,25,23),Color.rgb(55,59,53),999));close.addView(new CortexLineIconView(this,"plus",CortexUi.MUTED),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));close.setRotation(45f);close.setOnClickListener(v->finish());head.addView(close,new LinearLayout.LayoutParams(dp(38),dp(38)));sheet.addView(head,new LinearLayout.LayoutParams(-1,dp(44)));

        importState=CortexUi.plain(this,"Importing safely…",9,CortexUi.LIME);importState.setPadding(dp(4),dp(6),0,dp(5));importState.setVisibility(View.GONE);sheet.addView(importState);
        choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(0,dp(7),0,0);
        choices.addView(actionRow("voice","Voice","Speak naturally",this::startVoice));
        choices.addView(actionRow("photo","Photo","Take or import visual evidence",this::pickPhoto));
        choices.addView(actionRow("file","File","Documents, audio and attachments",this::pickFile));
        choices.addView(actionRow("evidence","Text / Paste","Write or paste text into Cortex",this::quickNote));
        sheet.addView(choices);

        TextView trust=CortexUi.plain(this,"Every capture keeps its source and provenance.",9,CortexUi.FAINT);trust.setPadding(dp(4),dp(10),0,0);sheet.addView(trust);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sp.setMargins(dp(10),0,dp(10),dp(10));root.addView(sheet,sp);setContentView(root);applyInsets();
    }

    View actionRow(String icon,String title,String subtitle,Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(7),dp(6),dp(5),dp(6));row.addView(iconDisc(icon),new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,0,0);row.addView(tx,xp);TextView h=CortexUi.plain(this,title,13,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView s=CortexUi.plain(this,subtitle,9,CortexUi.MUTED);s.setPadding(0,dp(2),0,0);tx.addView(s);row.addView(new CortexLineIconView(this,"chevron",CortexUi.FAINT),new LinearLayout.LayoutParams(dp(20),dp(20)));row.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(60));p.setMargins(0,dp(1),0,0);row.setLayoutParams(p);return row;}
    View iconDisc(String kind){FrameLayout f=new FrameLayout(this);f.setBackground(CortexUi.round(this,Color.rgb(22,24,22),Color.rgb(58,62,56),999));f.addView(new CortexLineIconView(this,kind,CortexUi.LIME),new FrameLayout.LayoutParams(dp(27),dp(27),Gravity.CENTER));return f;}

    /** Keep one Android Share owner. Structured ChatGPT returns are routed to Deep Review, not ingested as ordinary evidence. */
    @Override void handleIncoming(Intent i){
        if(i!=null&&Intent.ACTION_SEND.equals(i.getAction())){
            CharSequence extra=i.getCharSequenceExtra(Intent.EXTRA_TEXT);String text=extra==null?"":extra.toString();
            if(text.contains(DeepReviewContractV1.RESPONSE_MARKER)){
                try{Intent review=new Intent(this,DeepReviewActivity.class);review.putExtra("deep_review_response",text);startActivity(review);}catch(Throwable e){Toast.makeText(this,"Could not open Deep Review",Toast.LENGTH_LONG).show();}
                finish();return;
            }
        }
        super.handleIncoming(i);
    }

    @Override void showResult(long id){try{Intent i=new Intent(this,ProposalCaptureResultActivity.class);i.putExtra("item_id",id);startActivity(i);finish();}catch(Throwable e){Toast.makeText(this,"Captured successfully. Open Brief to see it.",Toast.LENGTH_LONG).show();try{startActivity(new Intent(this,InputActivity.class));}catch(Throwable ignored){}finish();}}
}
