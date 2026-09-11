package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

public final class ChatGptTeacherActivity extends Activity {
    private TextView status;
    private TextView launch;

    private int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
    }

    @Override protected void onResume(){
        super.onResume();
        if(status!=null){
            CortexChatGptAppTeacher.ImportResult r=CortexChatGptAppTeacher.importClipboardIfReady(this);
            if(r.ok)Toast.makeText(this,"ChatGPT policy imported",Toast.LENGTH_LONG).show();
            status.setText(statusText(r));
        }
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
        TextView h=CortexUi.plain(this,"ChatGPT Teacher",28,CortexUi.TEXT);
        CortexUi.medium(h);
        titles.addView(h);
        TextView sub=CortexUi.text(this,
                "Uses your ChatGPT app subscription · no API credits required.",
                11,CortexUi.MUTED);
        sub.setPadding(0,dp(2),0,0);
        titles.addView(sub);
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(head);

        body.addView(CortexUi.section(this,"How it works"));
        TextView route=CortexUi.text(this,
                "1. Cortex builds a compact normalized Context Pack.\n"+
                "2. ChatGPT opens with the teacher request.\n"+
                "3. Copy the JSON Policy Pack in ChatGPT, then return here.\n"+
                "4. Cortex validates every bound locally before applying it.\n\n"+
                "You can also use Share in ChatGPT and choose Cortex.",
                12,CortexUi.TEXT);
        route.setPadding(0,dp(4),0,dp(8));
        body.addView(route);

        launch=CortexUi.action(this,"TEACH WITH CHATGPT",CortexUi.LIME,true);
        LinearLayout.LayoutParams yp=new LinearLayout.LayoutParams(-1,dp(46));
        yp.setMargins(0,dp(10),0,0);
        body.addView(launch,yp);
        launch.setOnClickListener(v->launchChatGpt());

        TextView paste=CortexUi.action(this,"PASTE POLICY FROM CLIPBOARD",CortexUi.TEXT,false);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(46));
        pp.setMargins(0,dp(10),0,0);
        body.addView(paste,pp);
        paste.setOnClickListener(v->{
            CortexChatGptAppTeacher.ImportResult r=CortexChatGptAppTeacher.importClipboardIfReady(this);
            status.setText(statusText(r));
            Toast.makeText(this,r.ok?"ChatGPT policy imported":"Import failed: "+r.error,Toast.LENGTH_LONG).show();
        });

        body.addView(CortexUi.section(this,"Status"));
        status=CortexUi.text(this,statusText(null),12,CortexUi.TEXT);
        status.setPadding(0,dp(4),0,dp(8));
        status.setTextIsSelectable(true);
        body.addView(status);

        body.addView(CortexUi.section(this,"Teacher impact"));
        TextView impact=CortexUi.text(this,CortexTeacherImpact.summary(this),12,CortexUi.MUTED);
        impact.setPadding(0,dp(4),0,dp(8));
        body.addView(impact);

        body.addView(CortexUi.section(this,"Policy promotion"));
        TextView promotion=CortexUi.text(this,CortexPolicyPromotion.status(this),12,CortexUi.MUTED);
        promotion.setPadding(0,dp(4),0,dp(8));
        body.addView(promotion);

        TextView rollback=CortexUi.action(this,"ROLL BACK TO PREVIOUS POLICY",CortexUi.MUTED,false);
        LinearLayout.LayoutParams rbp=new LinearLayout.LayoutParams(-1,dp(44));
        rbp.setMargins(0,dp(4),0,0);
        body.addView(rollback,rbp);
        rollback.setEnabled(CortexPolicyPromotion.canRollback(this));
        rollback.setOnClickListener(v->{
            boolean ok=CortexPolicyPromotion.rollback(this,"manual rollback from teacher screen");
            Toast.makeText(this,ok?"Previous policy restored":"No previous policy available",Toast.LENGTH_LONG).show();
            status.setText(statusText(null));
        });

        body.addView(CortexUi.section(this,"Safety boundary"));
        TextView rules=CortexUi.text(this,
                "ChatGPT never writes directly into Cortex. It returns a proposed Policy Pack only. "+
                "Cortex validates the threshold, TTL, interruption penalty, feature weights and boosts locally before saving. "+
                "Evidence, canonical knowledge, world state and actions remain owned by Cortex.",
                12,CortexUi.MUTED);
        rules.setPadding(0,dp(4),0,0);
        body.addView(rules);

        setContentView(root);
        CortexUi.fitSystemBars(this,root);
    }

    private void launchChatGpt(){
        launch.setEnabled(false);
        launch.setText("PREPARING…");
        new Thread(()->{
            try{
                CortexChatGptAppTeacher.launch(this);
            }catch(Throwable e){
                runOnUiThread(()->Toast.makeText(this,
                        "Could not open ChatGPT: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }finally{
                runOnUiThread(()->{
                    launch.setEnabled(true);
                    launch.setText("TEACH WITH CHATGPT");
                    status.setText(statusText(null));
                });
            }
        },"cortex-chatgpt-app-teacher").start();
    }

    private String statusText(CortexChatGptAppTeacher.ImportResult latest){
        StringBuilder b=new StringBuilder();
        b.append("Policy: ").append(CortexPersonalPolicy.version(this));
        b.append("\nPending ChatGPT request: ").append(CortexChatGptAppTeacher.pending(this)?"YES":"NO");
        b.append("\nLifecycle: ").append(CortexPolicyLifecycle.state(this));
        String lifecycleReason=CortexPolicyLifecycle.reason(this);
        if(!lifecycleReason.isEmpty())b.append(" · ").append(lifecycleReason);
        if(CortexChatGptAppTeacher.launchedAt(this)>0)
            b.append("\nLast launch: ").append(new java.text.SimpleDateFormat("dd MMM · HH:mm:ss",
                    java.util.Locale.getDefault()).format(new java.util.Date(CortexChatGptAppTeacher.launchedAt(this))));
        if(latest!=null){
            if(latest.ok)b.append("\nLast import: SUCCESS · ").append(latest.version);
            else if(!latest.error.isEmpty()&&!"Waiting for ChatGPT policy".equals(latest.error))
                b.append("\nClipboard status: ").append(latest.error);
        }
        JSONObject impact=CortexTeacherImpact.latest(this);
        if(impact.length()>0){
            b.append("\nShadow: Now ")
                    .append(impact.optInt("beforeNow",0)).append(" → ")
                    .append(impact.optInt("afterNow",0))
                    .append(" · +").append(impact.optInt("promoted",0))
                    .append(" / -").append(impact.optInt("deferred",0));
        }
        b.append("\nPromotion: ").append(CortexPolicyPromotion.status(this).replace("\n"," · "));
        return b.toString();
    }
}
