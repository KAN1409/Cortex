package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Manual test surface for the bounded GPT-5.6 Sol teacher route.
 * No endpoint, relay token, or per-notification dependency is required.
 */
public final class ChatGptTeacherActivity extends Activity {
    private TextView status;
    private TextView sync;

    private int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
    }

    @Override protected void onResume(){
        super.onResume();
        if(status!=null)status.setText(statusText());
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);

        ScrollView sv=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(14),dp(20),dp(28));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView h=CortexUi.plain(this,"Cortex Teacher",28,CortexUi.TEXT);
        CortexUi.medium(h);
        titles.addView(h);
        TextView sub=CortexUi.text(this,
                "Free teacher route · bounded FINAL JUDGMENT policy only.",
                11,CortexUi.MUTED);
        sub.setPadding(0,dp(2),0,0);
        titles.addView(sub);
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(head);

        body.addView(CortexUi.section(this,"Teacher route"));
        TextView route=CortexUi.text(this,
                "Provider: OpenRouter\nRoute: "+CortexOpenAiTeacher.MODEL+
                "\nCost: free router\nCredential: existing encrypted OpenRouter key\nRelay endpoint: not required",
                12,CortexUi.TEXT);
        route.setPadding(0,dp(4),0,dp(8));
        body.addView(route);

        sync=CortexUi.action(this,"SYNC TEACHER NOW",CortexUi.LIME,true);
        LinearLayout.LayoutParams yp=new LinearLayout.LayoutParams(-1,dp(46));
        yp.setMargins(0,dp(10),0,0);
        body.addView(sync,yp);
        sync.setOnClickListener(v->syncNow());

        body.addView(CortexUi.section(this,"Status"));
        status=CortexUi.text(this,statusText(),12,CortexUi.TEXT);
        status.setPadding(0,dp(4),0,dp(8));
        status.setTextIsSelectable(true);
        body.addView(status);

        body.addView(CortexUi.section(this,"Safety boundary"));
        TextView rules=CortexUi.text(this,
                "Cortex sends only the normalized compact Context Pack. The free teacher model may return a temporary bounded Policy Pack for CortexAttentionJudge. "+
                "It cannot rewrite evidence or canonical knowledge, cannot directly surface or suppress items, cannot execute actions, and Cortex keeps working if this route is unavailable.",
                12,CortexUi.MUTED);
        rules.setPadding(0,dp(4),0,0);
        body.addView(rules);

        setContentView(root);
        CortexUi.fitSystemBars(this,root);
    }

    private void syncNow(){
        if(!CortexOpenAiTeacher.configured(this)){
            Toast.makeText(this,"OpenRouter is not configured in Cortex",Toast.LENGTH_LONG).show();
            return;
        }
        sync.setEnabled(false);
        sync.setText("TEACHING…");
        status.setText("Building normalized Context Pack and requesting bounded policy…");

        new Thread(()->{
            CortexOpenAiTeacher.Result result;
            try{
                result=CortexOpenAiTeacher.sync(getApplicationContext());
            }catch(Throwable e){
                result=new CortexOpenAiTeacher.Result(false,"",
                        e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage()),0);
            }
            final CortexOpenAiTeacher.Result r=result;
            runOnUiThread(()->{
                sync.setEnabled(true);
                sync.setText("SYNC TEACHER NOW");
                status.setText(statusText()+"\nLast request: "+(r.ok?"SUCCESS · "+r.durationMs+" ms":"FAILED · "+r.error));
                String toast;
                if(r.ok)toast="Teacher policy updated";
                else if(r.error.contains("429"))toast="Free teacher is busy · try again later";
                else if(r.error.contains("402"))toast="Free route unavailable for this request";
                else toast="Teacher request failed";
                Toast.makeText(this,toast,Toast.LENGTH_LONG).show();
            });
        },"cortex-gpt56-teacher").start();
    }

    private String statusText(){
        return "Teacher: "+(CortexOpenAiTeacher.configured(this)?"ready":"OpenRouter key missing")+
                "\nModel: "+CortexOpenAiTeacher.MODEL+
                "\nPolicy: "+CortexPersonalPolicy.version(this)+
                "\nCortex autonomy: local-first / teacher optional";
    }
}
