package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Offline failure-evidence viewer/share surface. Reads only Cortex private diagnostic files. */
public final class CrashReportActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
    }

    void build(){
        ProcessExitRecorder.captureHistoricalExit(this);
        String crash=CrashRecorder.read(this,60000);
        String exit=ProcessExitRecorder.read(this,120000);
        String evidence=combine(crash,exit);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.setPadding(dp(18),dp(14),dp(18),dp(22));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=CortexUi.plain(this,"Process failure evidence",25,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);
        TextView sub=CortexUi.text(this,evidence.trim().isEmpty()?"No Java crash or Android process-exit diagnosis has been recorded.":"Stored locally on this device. Includes Java crash evidence plus Android process-exit reason when available.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);tx.addView(sub);root.addView(head);

        ScrollView sv=new ScrollView(this);TextView body=CortexUi.text(this,evidence.trim().isEmpty()?"Nothing to show yet.":evidence,11,CortexUi.TEXT);body.setTextIsSelectable(true);body.setPadding(dp(12),dp(12),dp(12),dp(12));sv.addView(body);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.setMargins(0,dp(12),0,dp(12));root.addView(sv,sp);

        TextView share=CortexUi.action(this,"SHARE FAILURE EVIDENCE",CortexUi.ORANGE,true);share.setEnabled(!evidence.trim().isEmpty());share.setOnClickListener(v->share(evidence));root.addView(share,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView copy=CortexUi.action(this,"COPY TO CLIPBOARD",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(8),0,0);root.addView(copy,cp);copy.setEnabled(!evidence.trim().isEmpty());copy.setOnClickListener(v->copy(evidence));
        TextView clear=CortexUi.action(this,"CLEAR STORED EVIDENCE",CortexUi.RED,false);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(-1,dp(44));xp.setMargins(0,dp(8),0,0);root.addView(clear,xp);clear.setEnabled(!evidence.trim().isEmpty());clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear failure evidence?").setMessage("This removes only the local crash/process-exit diagnostic files. Cortex data and Capture evidence are untouched.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{CrashRecorder.clear(this);ProcessExitRecorder.clear(this);build();}).show());
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    String combine(String crash,String exit){
        StringBuilder b=new StringBuilder();
        if(crash!=null&&!crash.trim().isEmpty())b.append("=== JAVA UNCAUGHT CRASH ===\n").append(crash.trim()).append("\n");
        if(exit!=null&&!exit.trim().isEmpty()){if(b.length()>0)b.append("\n");b.append("=== ANDROID PROCESS EXIT ===\n").append(exit.trim()).append("\n");}
        return b.toString();
    }

    void share(String evidence){
        if(evidence==null||evidence.trim().isEmpty())return;
        try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"Cortex process failure evidence");i.putExtra(Intent.EXTRA_TEXT,evidence);startActivity(Intent.createChooser(i,"Share Cortex failure evidence"));}
        catch(Throwable e){Toast.makeText(this,"Could not open share sheet",Toast.LENGTH_LONG).show();}
    }
    void copy(String evidence){
        if(evidence==null||evidence.trim().isEmpty())return;
        try{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("Cortex process failure evidence",evidence));Toast.makeText(this,"Failure evidence copied",Toast.LENGTH_SHORT).show();}
        catch(Throwable e){Toast.makeText(this,"Could not copy report",Toast.LENGTH_LONG).show();}
    }
}
