package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Offline crash report viewer/share surface. Reads only Cortex's private last_crash.txt. */
public final class CrashReportActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
    }

    void build(){
        String crash=CrashRecorder.read(this,60000);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.setPadding(dp(18),dp(14),dp(18),dp(22));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=CortexUi.plain(this,"Last crash report",25,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);
        TextView sub=CortexUi.text(this,crash.trim().isEmpty()?"No uncaught Java crash has been recorded.":"Stored locally on this device. No Wi‑Fi, ADB or Shizuku required.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);tx.addView(sub);root.addView(head);

        ScrollView sv=new ScrollView(this);TextView body=CortexUi.text(this,crash.trim().isEmpty()?"Nothing to show yet.":crash,11,CortexUi.TEXT);body.setTextIsSelectable(true);body.setPadding(dp(12),dp(12),dp(12),dp(12));sv.addView(body);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.setMargins(0,dp(12),0,dp(12));root.addView(sv,sp);

        TextView share=CortexUi.action(this,"SHARE CRASH REPORT",CortexUi.ORANGE,true);share.setEnabled(!crash.trim().isEmpty());share.setOnClickListener(v->share(crash));root.addView(share,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView copy=CortexUi.action(this,"COPY TO CLIPBOARD",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(8),0,0);root.addView(copy,cp);copy.setEnabled(!crash.trim().isEmpty());copy.setOnClickListener(v->copy(crash));
        TextView clear=CortexUi.action(this,"CLEAR STORED REPORT",CortexUi.RED,false);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(-1,dp(44));xp.setMargins(0,dp(8),0,0);root.addView(clear,xp);clear.setEnabled(!crash.trim().isEmpty());clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear crash report?").setMessage("This removes only last_crash.txt. Cortex data and Capture evidence are untouched.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{CrashRecorder.clear(this);build();}).show());
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void share(String crash){
        if(crash==null||crash.trim().isEmpty())return;
        try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"Cortex crash report");i.putExtra(Intent.EXTRA_TEXT,crash);startActivity(Intent.createChooser(i,"Share Cortex crash report"));}
        catch(Throwable e){Toast.makeText(this,"Could not open share sheet",Toast.LENGTH_LONG).show();}
    }
    void copy(String crash){
        if(crash==null||crash.trim().isEmpty())return;
        try{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("Cortex crash report",crash));Toast.makeText(this,"Crash report copied",Toast.LENGTH_SHORT).show();}
        catch(Throwable e){Toast.makeText(this,"Could not copy report",Toast.LENGTH_LONG).show();}
    }
}
