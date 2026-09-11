package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Manual test surface for the bounded Cortex <-> ChatGPT teacher bridge.
 * No automatic per-notification calls are made from this screen.
 */
public final class ChatGptTeacherActivity extends Activity {
    private EditText endpoint;
    private EditText token;
    private TextView enabledButton;
    private TextView status;
    private boolean enabled;

    private int dp(int x){ return CortexUi.dp(this,x); }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        enabled=CortexChatGptBridgeConfig.enabled(this);
        build();
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
                "Bounded judgment teacher · Cortex remains local-first and autonomous.",
                11,CortexUi.MUTED);
        sub.setPadding(0,dp(2),0,0);
        titles.addView(sub);
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(head);

        body.addView(CortexUi.section(this,"Connection"));
        endpoint=field("Relay endpoint",CortexChatGptBridgeConfig.endpoint(this),false);
        token=field("Device token",CortexChatGptBridgeConfig.token(this),true);
        body.addView(endpoint);
        body.addView(token);

        enabledButton=CortexUi.action(this,enabled?"Teacher enabled":"Teacher disabled",
                enabled?CortexUi.LIME:CortexUi.MUTED,true);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(46));
        ep.setMargins(0,dp(12),0,0);
        body.addView(enabledButton,ep);
        enabledButton.setOnClickListener(v->{
            enabled=!enabled;
            enabledButton.setText(enabled?"Teacher enabled":"Teacher disabled");
        });

        TextView save=CortexUi.action(this,"SAVE CONNECTION",CortexUi.ACCENT,true);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(46));
        sp.setMargins(0,dp(8),0,0);
        body.addView(save,sp);
        save.setOnClickListener(v->save());

        TextView sync=CortexUi.action(this,"SYNC NOW",CortexUi.LIME,true);
        LinearLayout.LayoutParams yp=new LinearLayout.LayoutParams(-1,dp(46));
        yp.setMargins(0,dp(8),0,0);
        body.addView(sync,yp);
        sync.setOnClickListener(v->syncNow());

        body.addView(CortexUi.section(this,"Status"));
        status=CortexUi.text(this,statusText(),12,CortexUi.TEXT);
        status.setPadding(0,dp(4),0,dp(8));
        status.setTextIsSelectable(true);
        body.addView(status);

        body.addView(CortexUi.section(this,"Safety boundary"));
        TextView rules=CortexUi.text(this,
                "ChatGPT receives a compact Context Pack, not the whole database. " +
                "It can teach bounded FINAL JUDGMENT policy only. It cannot rewrite evidence, " +
                "create canonical facts, execute actions, or become required for Cortex to work.",
                12,CortexUi.MUTED);
        rules.setPadding(0,dp(4),0,0);
        body.addView(rules);

        setContentView(root);
        CortexUi.fitSystemBars(this,root);
    }

    private EditText field(String hint,String value,boolean secret){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setText(value==null?"":value);
        e.setTextColor(CortexUi.TEXT);
        e.setHintTextColor(CortexUi.MUTED);
        e.setSingleLine(true);
        e.setTextSize(13);
        e.setPadding(dp(12),dp(10),dp(12),dp(10));
        if(secret) e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));
        p.setMargins(0,dp(8),0,0);
        e.setLayoutParams(p);
        return e;
    }

    private void save(){
        CortexChatGptBridgeConfig.save(this,enabled,
                endpoint.getText().toString(),token.getText().toString());
        status.setText(statusText());
        Toast.makeText(this,"Teacher connection saved",Toast.LENGTH_SHORT).show();
    }

    private void syncNow(){
        save();
        if(!CortexChatGptBridgeConfig.enabled(this)){
            Toast.makeText(this,"Enable the teacher and set an endpoint first",Toast.LENGTH_LONG).show();
            return;
        }
        status.setText("Syncing bounded Context Pack…");
        new Thread(()->{
            boolean ok=false;
            String error="";
            try{ ok=CortexChatGptBridge.sync(getApplicationContext()); }
            catch(Throwable e){ error=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage()); }
            final boolean result=ok;
            final String fallbackError=error;
            runOnUiThread(()->{
                status.setText(statusText());
                Toast.makeText(this,result?"Teacher sync complete":
                        "Teacher sync failed"+(fallbackError.isEmpty()?"":" · "+fallbackError),
                        Toast.LENGTH_LONG).show();
            });
        },"cortex-chatgpt-teacher-sync").start();
    }

    private String statusText(){
        long at=CortexChatGptBridgeConfig.lastSyncAt(this);
        String when=at<=0?"Never synced":
                new SimpleDateFormat("dd MMM · HH:mm:ss",Locale.getDefault()).format(new Date(at));
        String error=CortexChatGptBridgeConfig.lastError(this);
        return "Bridge: "+(CortexChatGptBridgeConfig.enabled(this)?"enabled":"disabled")+
                "\nPolicy: "+CortexPersonalPolicy.version(this)+
                "\nLast sync: "+when+
                (error==null||error.trim().isEmpty()?"":"\nLast error: "+error);
    }
}
