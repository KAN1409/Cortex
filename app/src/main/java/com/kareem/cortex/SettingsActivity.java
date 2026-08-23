package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** One place for configuration and advanced tools. Main navigation stays Home / Focus / Vault / Ask. */
public class SettingsActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(26));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Settings",29,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);titles.addView(CortexUi.plain(this,"Configuration and advanced tools",11,CortexUi.MUTED));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);
        body.addView(CortexUi.section(this,"Intelligence"));row(body,"Local AI","Model status, runtime and local Qwen",EnvironmentActivity.class);row(body,"Brain & context","Context graph, decisions and open loops",BrainActivity.class);row(body,"Visual intelligence","Image understanding and visual diagnostics",VisualIntelligenceActivity.class);
        body.addView(CortexUi.section(this,"Capture & processing"));row(body,"Transcription","ASR providers, keys and audio settings",AsrSettingsActivity.class);
        body.addView(CortexUi.section(this,"System"));row(body,"Diagnostics","Run the full Cortex audit",CortexAuditActivity.class);row(body,"Advanced tools","Backup, integrations, privacy and experimental features",FeatureHubActivity.class);
        CortexUi.addBottomNav(this,root,"",null);setContentView(root);}
    void row(LinearLayout parent,String title,String sub,Class<?> cls){LinearLayout c=CortexUi.card(this,21);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(16),dp(14),dp(13),dp(14));CortexUi.pressable(this,c,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,21));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);text.addView(t);TextView s=CortexUi.text(this,sub,11,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);text.addView(s);c.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView go=CortexUi.plain(this,"›",27,CortexUi.MUTED);go.setGravity(Gravity.CENTER);c.addView(go,new LinearLayout.LayoutParams(dp(32),dp(44)));c.setOnClickListener(v->startActivity(new Intent(this,cls)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(9));parent.addView(c,p);}
}
