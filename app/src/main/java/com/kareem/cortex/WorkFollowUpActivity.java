package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Read-only grounded view of follow-up rows extracted from archive tables. */
public final class WorkFollowUpActivity extends Activity {
    private VaultDb db;private LinearLayout content;private volatile boolean dead=false;
    int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle state){super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());WorkVaultIndexSchema.ensure(db.getWritableDatabase());build();render(load());}
    @Override protected void onResume(){super.onResume();if(!dead&&db!=null)render(load());}
    @Override protected void onDestroy(){dead=true;if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(14),dp(20),dp(28));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"work",null);setContentView(root);
    }

    private ArrayList<Row> load(){
        ArrayList<Row> out=new ArrayList<>();SQLiteDatabase sql=db.getReadableDatabase();WorkVaultIndexSchema.ensure(sql);
        Cursor c=sql.rawQuery("SELECT u.id,u.reference_type,u.reference_value,u.item_name,u.status,u.status_normalized,u.owner_name,u.due_text,u.remarks,u.vendor_name,u.sheet_name,u.page_number,u.row_number,f.display_name,p.canonical_name "+
                "FROM work_followup_records u JOIN work_files f ON f.id=u.file_id LEFT JOIN work_projects p ON p.id=u.project_id "+
                "ORDER BY CASE u.status_normalized WHEN 'open' THEN 0 WHEN 'on_hold' THEN 1 WHEN 'other' THEN 2 WHEN 'unknown' THEN 3 ELSE 4 END,u.created_at DESC,u.id DESC LIMIT 500",null);
        while(c.moveToNext()){Row r=new Row();r.id=c.getLong(0);r.refType=s(c,1);r.ref=s(c,2);r.item=s(c,3);r.status=s(c,4);r.normalized=s(c,5);r.owner=s(c,6);r.due=s(c,7);r.remarks=s(c,8);r.vendor=s(c,9);r.sheet=s(c,10);r.page=c.getInt(11);r.row=c.getInt(12);r.file=s(c,13);r.project=s(c,14);out.add(r);}c.close();return out;
    }

    private void render(ArrayList<Row> rows){
        if(dead||content==null)return;content.removeAllViews();
        TextView title=CortexUi.plain(this,"Work Follow-up",30,CortexUi.TEXT);CortexUi.medium(title);content.addView(title);
        TextView sub=CortexUi.text(this,"Extracted from your archive tables. Nothing here is invented or promoted beyond source evidence.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,dp(14));content.addView(sub);
        int open=0,closed=0;for(Row r:rows){if("open".equals(r.normalized)||"on_hold".equals(r.normalized)||"other".equals(r.normalized)||"unknown".equals(r.normalized))open++;else closed++;}
        LinearLayout stats=CortexUi.card(this,18);TextView st=CortexUi.plain(this,open+" potentially open  •  "+closed+" resolved/issued",14,CortexUi.TEXT);CortexUi.medium(st);stats.addView(st);TextView note=CortexUi.text(this,rows.size()+" grounded follow-up rows",10,CortexUi.MUTED);note.setPadding(0,dp(4),0,0);stats.addView(note);content.addView(stats);
        if(rows.isEmpty()){TextView e=CortexUi.text(this,"No follow-up table rows have been extracted yet. Index an Excel/Word/PDF/PowerPoint archive that contains status or follow-up tables.",12,CortexUi.MUTED);e.setPadding(0,dp(18),0,0);content.addView(e);return;}
        content.addView(CortexUi.section(this,"Follow-up records"));for(Row r:rows)row(r);
    }

    private void row(Row r){
        LinearLayout card=CortexUi.card(this,18);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);String label=!r.item.isEmpty()?r.item:(!r.ref.isEmpty()?r.ref:r.file);TextView name=CortexUi.text(this,label,14,CortexUi.TEXT);CortexUi.medium(name);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView chip=CortexUi.chip(this,statusLabel(r),CortexUi.semanticFor(r.normalized),false);top.addView(chip,new LinearLayout.LayoutParams(-2,dp(28)));card.addView(top);
        StringBuilder meta=new StringBuilder();if(!r.project.isEmpty())append(meta,r.project);if(!r.ref.isEmpty())append(meta,(r.refType.isEmpty()?"REF":r.refType)+" "+r.ref);if(!r.owner.isEmpty())append(meta,"Owner "+r.owner);if(!r.due.isEmpty())append(meta,"Due "+r.due);if(!r.vendor.isEmpty())append(meta,"Vendor "+r.vendor);if(meta.length()>0){TextView m=CortexUi.text(this,meta.toString(),11,CortexUi.MUTED);m.setPadding(0,dp(6),0,0);card.addView(m);}
        if(!r.remarks.isEmpty()){TextView rm=CortexUi.text(this,r.remarks,11,CortexUi.MUTED);rm.setPadding(0,dp(5),0,0);card.addView(rm);}
        String loc=r.file+location(r);TextView src=CortexUi.plain(this,loc,10,CortexUi.ACCENT);src.setPadding(0,dp(7),0,0);card.addView(src);
        TextView ask=CortexUi.action(this,"ASK ARCHIVE ABOUT THIS",CortexUi.MUTED,false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(40));ap.setMargins(0,dp(8),0,0);card.addView(ask,ap);ask.setOnClickListener(v->{Intent i=new Intent(this,WorkVaultAskActivity.class);String q=!r.ref.isEmpty()?"Show me the latest grounded status and related evidence for "+r.refType+" "+r.ref:"Show me the grounded follow-up evidence for "+label;i.putExtra("query",q);startActivity(i);});content.addView(card,cp);
    }

    private static String statusLabel(Row r){if(!r.status.isEmpty())return r.status;return r.normalized.isEmpty()?"UNKNOWN":r.normalized.toUpperCase().replace('_',' ');}
    private static String location(Row r){StringBuilder b=new StringBuilder();if(!r.sheet.isEmpty())b.append(" • Sheet ").append(r.sheet);if(r.page>0)b.append(" • Page ").append(r.page);if(r.row>0)b.append(" • Row ").append(r.row);return b.toString();}
    private static void append(StringBuilder b,String x){if(x==null||x.trim().isEmpty())return;if(b.length()>0)b.append(" • ");b.append(x.trim());}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class Row{long id;String refType="",ref="",item="",status="",normalized="",owner="",due="",remarks="",vendor="",sheet="",file="",project="";int page,row;}
}
