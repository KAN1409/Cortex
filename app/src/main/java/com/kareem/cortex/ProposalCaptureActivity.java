package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Compact capture sheet shared by every entry point into Cortex. */
public final class ProposalCaptureActivity extends SatinCaptureActivity {
    @Override void build(){
        Window w=getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.72f;w.setAttributes(lp);
        root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setOnClickListener(v->finish());
        sheet=CortexUi.card(this,20);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(16),dp(14),dp(16),dp(16));sheet.setOnClickListener(v->{});

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);TextView title=CortexUi.plain(this,"Add to Cortex",20,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);TextView sub=CortexUi.plain(this,"Choose how to capture it",10,CortexUi.MUTED);sub.setPadding(0,dp(2),0,0);tx.addView(sub);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView close=CortexUi.plain(this,"×",27,CortexUi.MUTED);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->finish());head.addView(close,new LinearLayout.LayoutParams(dp(40),dp(40)));sheet.addView(head);

        importState=CortexUi.plain(this,"Importing…",10,CortexUi.ORANGE);importState.setPadding(0,dp(8),0,0);importState.setVisibility(View.GONE);sheet.addView(importState);

        choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(0,dp(14),0,0);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);captureTile(top,"Voice","Speak","wave",CortexUi.RED,this::startVoice,0);captureTile(top,"Text","Type or paste","text",CortexUi.YELLOW,this::quickNote,8);choices.addView(top,new LinearLayout.LayoutParams(-1,dp(92)));
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);captureTile(bottom,"Photo","Camera or gallery","photo",CortexUi.GREEN,this::pickPhoto,0);captureTile(bottom,"File","Documents & media","file",CortexUi.ORANGE,this::pickFile,8);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(92));bp.setMargins(0,dp(8),0,0);choices.addView(bottom,bp);sheet.addView(choices);

        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sp.setMargins(dp(10),0,dp(10),dp(10));root.addView(sheet,sp);setContentView(root);applyInsets();
    }

    void captureTile(LinearLayout row,String title,String sub,String icon,int color,Runnable action,int left){
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(12),dp(9),dp(12),dp(9));CortexUi.pressable(this,tile,CortexUi.round(this,CortexUi.SURFACE_2,Color.TRANSPARENT,15));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView t=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(32),1);tp.setMargins(dp(8),0,0,0);top.addView(t,tp);tile.addView(top);TextView s=CortexUi.plain(this,sub,9,CortexUi.MUTED);s.setPadding(0,dp(5),0,0);tile.addView(s);tile.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);
    }

    @Override void showResult(long id){try{Intent i=new Intent(this,ProposalCaptureResultActivity.class);i.putExtra("item_id",id);startActivity(i);finish();}catch(Throwable e){Toast.makeText(this,"Captured successfully. Open Brief to see it.",Toast.LENGTH_LONG).show();try{startActivity(new Intent(this,PremiumHomeActivity.class));}catch(Throwable ignored){}finish();}}
}
