package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;

/** Explicit opt-in configuration for a self-hosted Qwen3.5-4B vLLM server. */
public final class DeepQwenSettingsActivity extends Activity {
    private EditText baseUrl,token;private CheckBox enabled;private TextView status;
    private int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}

    private void build(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(20),dp(22),dp(30));body.setBackground(CortexUi.aurora(this));scroll.addView(body);
        TextView title=CortexUi.plain(this,"Optional Deep Qwen",26,CortexUi.TEXT);CortexUi.bold(title);body.addView(title);
        TextView sub=CortexUi.text(this,"Self-hosted Qwen3.5-4B via vLLM. Local Qwen stays the default brain; only validated mid-confidence, non-sensitive cognition may escalate here.",12,CortexUi.MUTED);sub.setPadding(0,dp(6),0,dp(16));body.addView(sub);

        baseUrl=new EditText(this);baseUrl.setHint("https://your-server.example");baseUrl.setText(DeepQwenConfig.baseUrl(this));baseUrl.setSingleLine(true);baseUrl.setTextColor(CortexUi.TEXT);baseUrl.setHintTextColor(CortexUi.MUTED);body.addView(baseUrl,new LinearLayout.LayoutParams(-1,dp(56)));
        token=new EditText(this);token.setHint(DeepQwenConfig.tokenConfigured(this)?"Bearer token saved · leave blank to keep":"Optional bearer token");token.setSingleLine(true);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setTextColor(CortexUi.TEXT);token.setHintTextColor(CortexUi.MUTED);body.addView(token,new LinearLayout.LayoutParams(-1,dp(56)));
        enabled=new CheckBox(this);enabled.setText("Allow Deep Qwen escalation");enabled.setTextColor(CortexUi.TEXT);enabled.setChecked(DeepQwenConfig.enabled(this));body.addView(enabled);

        status=CortexUi.text(this,currentStatus(),12,CortexUi.MUTED);status.setPadding(0,dp(10),0,dp(12));body.addView(status);
        TextView save=CortexUi.action(this,"SAVE SETTINGS",CortexUi.BRAND,false);save.setGravity(Gravity.CENTER);save.setOnClickListener(v->save());body.addView(save,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView test=CortexUi.action(this,"RUN DEEP QWEN HEALTH TEST",CortexUi.AURORA,false);test.setGravity(Gravity.CENTER);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(52));tp.topMargin=dp(10);body.addView(test,tp);test.setOnClickListener(v->test());
        TextView close=CortexUi.action(this,"CLOSE",CortexUi.MUTED,false);close.setGravity(Gravity.CENTER);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(52));cp.topMargin=dp(10);body.addView(close,cp);close.setOnClickListener(v->finish());
        setContentView(scroll);CortexUi.fitSystemBars(this,scroll);
    }

    private String currentStatus(){return"Model: "+DeepQwenConfig.MODEL+"\nEnabled: "+DeepQwenConfig.enabled(this)+"\nToken: "+(DeepQwenConfig.tokenConfigured(this)?"encrypted in Android Keystore":"none")+"\nSensitive SECURITY / TRANSACTION / credentials never auto-escalate.";}

    private void save(){
        try{String oldToken=DeepQwenConfig.bearerToken(this);String entered=token.getText().toString().trim();DeepQwenConfig.save(this,enabled.isChecked(),baseUrl.getText().toString(),entered.isEmpty()?oldToken:entered);token.setText("");token.setHint(DeepQwenConfig.tokenConfigured(this)?"Bearer token saved · leave blank to keep":"Optional bearer token");status.setText("Saved.\n"+currentStatus());}
        catch(Throwable e){status.setText("SAVE FAILED · "+e.getClass().getSimpleName()+": "+safe(e.getMessage()));}
    }

    private void test(){
        try{save();}catch(Throwable ignored){}status.setText("Testing self-hosted Deep Qwen…");new Thread(()->{
            String result;long started=System.currentTimeMillis();try{DeepQwenBrain brain=new DeepQwenBrain(getApplicationContext());BrainCompletion c=brain.classify(new BrainRequest("Return JSON only.","Return exactly {\"status\":\"CORTEX_DEEP_OK\"}",48));result="PASS · "+c.model+" · "+c.latencyMs+" ms\n"+clip(c.text,240);}catch(Throwable e){result="FAIL · "+e.getClass().getSimpleName()+" · "+safe(e.getMessage())+" · "+(System.currentTimeMillis()-started)+" ms";}final String out=result;runOnUiThread(()->status.setText(out));
        },"deep-qwen-health").start();
    }

    private static String safe(String s){String x=s==null?"":s.trim();return x.length()<=220?x:x.substring(0,220);}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n);}
}
