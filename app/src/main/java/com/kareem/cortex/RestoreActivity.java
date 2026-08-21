package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public class RestoreActivity extends Activity {
    static final int REQ_RESTORE=930;
    int bg=Color.rgb(16,17,20),text=Color.rgb(243,244,246),muted=Color.rgb(165,168,176),accent=Color.rgb(143,169,255);
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);return v;}
    @Override public void onCreate(Bundle b){super.onCreate(b);build();}
    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(24),dp(40),dp(24),dp(24));root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title=tv("CORTEX RESTORE",26,text);title.setTypeface(null,1);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView body=tv("One-time signing migration. Restore a portable Cortex backup after installing the permanently signed app.\n\nThis restores cortex.db plus the original local attachments. Your backup ZIP stays untouched.",15,muted);body.setPadding(0,dp(18),0,dp(24));root.addView(body,new LinearLayout.LayoutParams(-1,-2));
        Button restore=new Button(this);restore.setText("SELECT CORTEX BACKUP ZIP");restore.setTextColor(Color.BLACK);restore.setBackgroundColor(accent);restore.setOnClickListener(v->pick());root.addView(restore,new LinearLayout.LayoutParams(-1,dp(56)));
        Button open=new Button(this);open.setText("OPEN CORTEX");open.setOnClickListener(v->openCortex());LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(52));op.setMargins(0,dp(12),0,0);root.addView(open,op);setContentView(root);
    }
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,REQ_RESTORE);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=REQ_RESTORE||result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();Toast.makeText(this,"Restoring Cortex backup…",Toast.LENGTH_LONG).show();new Thread(()->{try{BackupRestorer.Result r=BackupRestorer.restore(this,u);runOnUiThread(()->{Toast.makeText(this,"Restore complete • "+r.memories+" memories • "+r.attachments+" attachments",Toast.LENGTH_LONG).show();openCortex();});}catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Restore failed").setMessage(e.getClass().getSimpleName()+": "+e.getMessage()).setPositiveButton("Close",null).show());}}).start();}
    void openCortex(){Intent i=new Intent(this,BrainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);startActivity(i);finish();}
}
