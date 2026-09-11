package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

public final class KnowledgeExplorerActivity extends Activity {
    VaultDb db;
    LinearLayout body;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);KnowledgeV2Schema.ensure(db.getWritableDatabase());build();
    }
    @Override protected void onDestroy(){try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"brain",null);setContentView(root);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);TextView t=CortexUi.plain(this,"Knowledge",28,CortexUi.TEXT);CortexUi.medium(t);ht.addView(t);
        ht.addView(CortexUi.text(this,"What Cortex knows, where it came from, and how it is connected.",11,CortexUi.MUTED));head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        SQLiteDatabase s=db.getReadableDatabase();
        int evidence=count(s,"kv2_evidence","knowledge_eligible=1"),facts=count(s,"kv2_facts","state='active'"),events=count(s,"kv2_events","status<>'dismissed'"),entities=count(s,"entity_nodes","status='active'"),categories=count(s,"kv2_categories","state='active'");
        LinearLayout overview=CortexUi.card(this,20);TextView nums=CortexUi.text(this,
                evidence+" evidence  •  "+facts+" facts  •  "+events+" events\n"+entities+" entities  •  "+categories+" dynamic categories",13,CortexUi.TEXT);overview.addView(nums);
        body.addView(overview,margin(8));

        body.addView(CortexUi.section(this,"Dynamic categories"));
        Cursor cats=s.rawQuery(
                "SELECT c.id,c.canonical_name,COUNT(m.knowledge_id),MAX(c.last_active_at) FROM kv2_categories c LEFT JOIN kv2_category_memberships m ON m.category_id=c.id WHERE c.state='active' GROUP BY c.id,c.canonical_name ORDER BY COUNT(m.knowledge_id) DESC,c.last_active_at DESC LIMIT 40",null);
        while(cats.moveToNext())categoryCard(cats.getLong(0),cats.getString(1),cats.getInt(2));cats.close();

        body.addView(CortexUi.section(this,"People, projects and things"));
        Cursor es=s.rawQuery("SELECT id,kind,canonical_name FROM entity_nodes WHERE status='active' ORDER BY updated_at DESC LIMIT 50",null);
        while(es.moveToNext())entityCard(es.getLong(0),es.getString(1),es.getString(2));es.close();

        body.addView(CortexUi.section(this,"Recent structured facts"));
        Cursor fs=s.rawQuery("SELECT f.id,f.predicate,f.object_type,f.object_value,f.confidence,e.id,e.source_uri FROM kv2_facts f LEFT JOIN kv2_fact_evidence l ON l.fact_id=f.id LEFT JOIN kv2_evidence e ON e.id=l.evidence_id WHERE f.state='active' ORDER BY f.updated_at DESC LIMIT 60",null);
        while(fs.moveToNext())factCard(fs.getLong(0),fs.getString(1),fs.getString(2),fs.getString(3),fs.getDouble(4),fs.getLong(5),fs.getString(6));fs.close();
    }

    void categoryCard(long id,String name,int count){
        LinearLayout c=CortexUi.card(this,17);TextView t=CortexUi.text(this,name,14,CortexUi.TEXT);CortexUi.medium(t);c.addView(t);c.addView(CortexUi.plain(this,count+" linked knowledge items",10,CortexUi.MUTED));
        c.setOnClickListener(v->categoryDialog(id,name));body.addView(c,margin(7));
    }

    void categoryDialog(long id,String name){
        ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(10),dp(14),dp(14));sv.addView(box);
        SQLiteDatabase s=db.getReadableDatabase();
        Cursor c=s.rawQuery("SELECT m.knowledge_type,m.knowledge_id,m.score,m.reason FROM kv2_category_memberships m WHERE m.category_id=? ORDER BY m.score DESC LIMIT 120",new String[]{String.valueOf(id)});
        while(c.moveToNext()){
            String type=c.getString(0);long kid=c.getLong(1);double score=c.getDouble(2);String reason=c.getString(3);
            TextView x=CortexUi.text(this,type+" #"+kid+"  •  "+Math.round(score*100)+"%\n"+(reason==null?"":reason),12,CortexUi.TEXT);x.setPadding(0,dp(8),0,dp(8));box.addView(x);
        }c.close();
        new android.app.AlertDialog.Builder(this).setTitle(name).setView(sv).setNegativeButton("Close",null).show();
    }

    void entityCard(long id,String kind,String name){
        LinearLayout c=CortexUi.card(this,17);TextView t=CortexUi.text(this,name,14,CortexUi.TEXT);CortexUi.medium(t);c.addView(t);c.addView(CortexUi.plain(this,kind,10,CortexUi.LIME));c.setOnClickListener(v->entityDialog(id,name));body.addView(c,margin(7));
    }

    void entityDialog(long id,String name){
        ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(10),dp(14),dp(14));sv.addView(box);
        SQLiteDatabase s=db.getReadableDatabase();
        Cursor c=s.rawQuery("SELECT from_type,from_id,to_type,to_id,relation,confidence FROM kv2_edges WHERE (from_type='entity' AND from_id=?) OR (to_type='entity' AND to_id=?) ORDER BY confidence DESC LIMIT 120",new String[]{String.valueOf(id),String.valueOf(id)});
        while(c.moveToNext()){box.addView(CortexUi.text(this,c.getString(4)+"  •  "+Math.round(c.getDouble(5)*100)+"%\n"+c.getString(0)+" #"+c.getLong(1)+" → "+c.getString(2)+" #"+c.getLong(3),12,CortexUi.TEXT));}
        c.close();new android.app.AlertDialog.Builder(this).setTitle(name).setView(sv).setNegativeButton("Close",null).show();
    }

    void factCard(long factId,String predicate,String type,String value,double conf,long evidenceId,String uri){
        LinearLayout c=CortexUi.card(this,17);TextView h=CortexUi.plain(this,predicate.replace('_',' '),10,CortexUi.LIME);CortexUi.medium(h);c.addView(h);
        c.addView(CortexUi.text(this,value,13,CortexUi.TEXT));c.addView(CortexUi.plain(this,type+"  •  confidence "+Math.round(conf*100)+"%  •  evidence #"+evidenceId,9,CortexUi.MUTED));
        if(uri!=null&&!uri.isEmpty())c.setOnClickListener(v->{try{Intent i=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Throwable ignored){}});
        body.addView(c,margin(7));
    }

    int count(SQLiteDatabase s,String table,String where){Cursor c=s.rawQuery("SELECT COUNT(*) FROM "+table+(where==null||where.isEmpty()?"":" WHERE "+where),null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,0);return p;}
}
