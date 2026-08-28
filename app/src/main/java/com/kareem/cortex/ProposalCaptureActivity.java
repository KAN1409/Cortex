package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Final PRIME capture entry locked to the approved matte warm premium visual language. */
public final class ProposalCaptureActivity extends SatinCaptureActivity {
    @Override void build(){
        Window w=getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.68f;w.setAttributes(lp);
        root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setOnClickListener(v->finish());sheet=CortexUi.card(this,28);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(16),dp(14),dp(16),dp(18));sheet.setOnClickListener(v->{});CortexUi.raised(this,sheet,9);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.RED,Color.TRANSPARENT,999));head.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));TextView c=CortexUi.plain(this,"C O R T E X",13,CortexUi.TEXT);CortexUi.bold(c);if(android.os.Build.VERSION.SDK_INT>=21)c.setLetterSpacing(.18f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.setMargins(dp(11),0,0,0);head.addView(c,cp);View d=CortexUi.divider(this);LinearLayout.LayoutParams dv=new LinearLayout.LayoutParams(dp(1),dp(26));dv.setMargins(dp(11),0,dp(11),0);head.addView(d,dv);TextView mode=CortexUi.plain(this,"CAPTURE",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)mode.setLetterSpacing(.09f);head.addView(mode,new LinearLayout.LayoutParams(0,dp(38),1));TextView close=CortexUi.chip(this,"CLOSE",CortexUi.MUTED,false);close.setOnClickListener(v->finish());head.addView(close,new LinearLayout.LayoutParams(-2,dp(34)));sheet.addView(head);
        importState=CortexUi.plain(this,"Importing safely…",10,CortexUi.ORANGE);CortexUi.medium(importState);importState.setPadding(dp(2),dp(7),0,dp(5));importState.setVisibility(View.GONE);sheet.addView(importState);
        choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(0,dp(12),0,0);LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);captureTile(top,"Voice","Speak naturally","wave",CortexUi.RED,this::startVoice,0);captureTile(top,"Text","Type or paste","text",CortexUi.YELLOW,this::quickNote,8);choices.addView(top,new LinearLayout.LayoutParams(-1,dp(116)));LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);captureTile(bottom,"File","Import evidence","file",CortexUi.ORANGE,this::pickFile,0);captureTile(bottom,"Photo","Visual evidence","photo",CortexUi.GREEN,this::pickPhoto,8);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(116));bp.setMargins(0,dp(8),0,0);choices.addView(bottom,bp);sheet.addView(choices);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sp.setMargins(dp(12),0,dp(12),dp(12));root.addView(sheet,sp);setContentView(root);applyInsets();
    }

    /**
     * Keep one Android ACTION_SEND owner. Ordinary shares stay on the canonical capture path;
     * a validated Deep Brain marker is routed explicitly to the internal response importer.
     */
    @Override void handleIncoming(Intent incoming){
        if(incoming!=null&&Intent.ACTION_SEND.equals(incoming.getAction())){
            CharSequence text=incoming.getCharSequenceExtra(Intent.EXTRA_TEXT);
            String raw=text==null?"":text.toString();
            if(raw.contains(CognitiveDeepBrainProtocolV4.RESPONSE_MARKER)){
                Intent route=new Intent(this,DeepBrainImportActivity.class);
                route.setAction(Intent.ACTION_SEND);
                route.setType(incoming.getType()==null?"text/plain":incoming.getType());
                route.putExtra(Intent.EXTRA_TEXT,raw);
                startActivity(route);finish();return;
            }
        }
        super.handleIncoming(incoming);
    }

    void captureTile(LinearLayout row,String title,String sub,String icon,int color,Runnable action,int left){LinearLayout tile=CortexUi.card(this,20);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(12),dp(10),dp(12),dp(10));CortexUi.pressable(this,tile,CortexUi.velvet(this,20));tile.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(42),dp(42)));TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);t.setPadding(0,dp(7),0,0);tile.addView(t);TextView s=CortexUi.plain(this,sub,10,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);tile.addView(s);tile.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);}

    @Override void showResult(long id){try{Intent i=new Intent(this,ProposalCaptureResultActivity.class);i.putExtra("item_id",id);startActivity(i);finish();}catch(Throwable e){Toast.makeText(this,"Captured successfully. Open Brief to see it.",Toast.LENGTH_LONG).show();try{startActivity(new Intent(this,PremiumHomeActivity.class));}catch(Throwable ignored){}finish();}}
}