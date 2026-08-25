package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** In-app Health Connect privacy/rationale surface required by the Health permissions flow. */
public final class HealthPermissionsRationaleActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(16),dp(20),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->{CortexHaptics.press(v);finish();});head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));TextView h=CortexUi.plain(this,"Health data privacy",26,CortexUi.TEXT);CortexUi.medium(h);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);
        body.addView(CortexUi.section(this,"WHY CORTEX ASKS"));body.addView(CortexUi.text(this,"Cortex uses the health data types you explicitly allow to build your private health timeline, connect measurements with your own scans/notes, and surface grounded follow-ups or trends.",13,CortexUi.TEXT));
        body.addView(CortexUi.section(this,"READ ONLY"));body.addView(CortexUi.text(this,"The current Health Connect integration requests read access only for steps, heart rate, resting heart rate, sleep, oxygen saturation and weight. Cortex does not write or delete Health Connect records.",12,CortexUi.MUTED));
        body.addView(CortexUi.section(this,"PROVENANCE"));body.addView(CortexUi.text(this,"Each imported measurement keeps its Health Connect data-origin package, so Cortex can distinguish Samsung Health and other contributing sources instead of presenting mixed data as one anonymous stream.",12,CortexUi.MUTED));
        body.addView(CortexUi.section(this,"CONTROL"));body.addView(CortexUi.text(this,"You can grant or revoke any Health Connect permission from Android settings at any time. The current sync is user-triggered and limited to recent data; Cortex does not request background or older-history health access in this build.",12,CortexUi.MUTED));
        body.addView(CortexUi.section(this,"MEDICAL BOUNDARY"));body.addView(CortexUi.text(this,"Cortex may organize evidence and point out a pattern or pending follow-up, but it must keep that statement traceable to the underlying data and must not present an inferred pattern as a confirmed diagnosis.",12,CortexUi.MUTED));
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }
}
