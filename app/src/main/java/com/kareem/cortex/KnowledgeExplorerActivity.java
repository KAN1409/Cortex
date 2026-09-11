package com.kareem.cortex;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.format.DateFormat;
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
        ht.addView(CortexUi.text(this,"Structured memory with source, confidence and evidence-grounded relationships.",11,CortexUi.MUTED));head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        SQLiteDatabase s=db.getReadableDatabase();
        int registered=count(s,"kv2_evidence","source_type='PICBRAIN_SCREENSHOT'"),eligible=count(s,"kv2_evidence","source_type='PICBRAIN_SCREENSHOT' AND knowledge_eligible=1"),facts=count(s,"kv2_facts","state='active'"),events=count(s,"kv2_events","status<>'dismissed'"),entities=countSemanticEntities(s),categories=count(s,"kv2_categories","state='active'");
        int[] p=processing(s);int accounted=p[0]+p[1]+p[2]+p[3]+p[4]+p[5];int unaccounted=Math.max(0,registered-accounted);
        LinearLayout overview=CortexUi.card(this,20);TextView nums=CortexUi.text(this,
                registered+" visual evidence  •  "+eligible+" eligible\n"+
                facts+" facts  •  "+events+" events  •  "+entities+" identities  •  "+categories+" categories\n"+
                p[2]+" understood  •  "+p[0]+" pending  •  "+p[1]+" running  •  "+p[3]+" blocked  •  "+p[5]+" no-text  •  "+p[4]+" failed"+
                (unaccounted>0?"\n"+unaccounted+" evidence item(s) awaiting accounting":""),12,CortexUi.TEXT);overview.addView(nums);
        body.addView(overview,margin(8));

        body.addView(CortexUi.section(this,"Dynamic categories"));
        Cursor cats=s.rawQuery(
                "SELECT c.id,c.canonical_name,COUNT(m.knowledge_id),MAX(c.last_active_at) FROM kv2_categories c LEFT JOIN kv2_category_memberships m ON m.category_id=c.id WHERE c.state='active' GROUP BY c.id,c.canonical_name HAVING COUNT(m.knowledge_id)>0 ORDER BY COUNT(m.knowledge_id) DESC,c.last_active_at DESC LIMIT 30",null);
        while(cats.moveToNext())categoryCard(cats.getLong(0),cats.getString(1),cats.getInt(2));cats.close();

        body.addView(CortexUi.section(this,"People, projects and things"));
        Cursor es=s.rawQuery("SELECT id,kind,canonical_name FROM entity_nodes WHERE status='active' AND upper(kind) IN ('PERSON','PROJECT','ORGANIZATION','ORG','PRODUCT','PLACE') ORDER BY updated_at DESC LIMIT 160",null);
        int shown=0;while(es.moveToNext()&&shown<50){String kind=es.getString(1),name=es.getString(2);if(!EntityQualityPolicy.plausibleEntity(kind,name))continue;entityCard(es.getLong(0),kind,name);shown++;}es.close();
        if(shown==0)body.addView(CortexUi.text(this,"No high-confidence identities yet. Cortex will add them as evidence is processed.",12,CortexUi.MUTED),margin(6));

        body.addView(CortexUi.section(this,"Recent structured facts"));
        Cursor fs=s.rawQuery("SELECT f.id,f.predicate,f.object_type,f.object_value,f.confidence,e.id,e.source_uri,e.observed_at FROM kv2_facts f LEFT JOIN kv2_fact_evidence l ON l.fact_id=f.id LEFT JOIN kv2_evidence e ON e.id=l.evidence_id WHERE f.state='active' ORDER BY f.updated_at DESC LIMIT 80",null);
        int factCount=0;while(fs.moveToNext()){factCard(fs.getLong(0),fs.getString(1),fs.getString(2),fs.getString(3),fs.getDouble(4),fs.getLong(5),fs.getString(6),fs.getLong(7));factCount++;}fs.close();
        if(factCount==0)body.addView(CortexUi.text(this,"Structured fact extraction has not produced facts yet. Pending evidence will populate this section automatically.",12,CortexUi.MUTED),margin(6));
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
            String desc=describeNode(s,type,kid);
            TextView x=CortexUi.text(this,desc+"\n"+Math.round(score*100)+"%"+(reason==null||reason.trim().isEmpty()?"":"  •  "+reason),12,CortexUi.TEXT);x.setPadding(0,dp(8),0,dp(8));box.addView(x);
        }c.close();
        showDialog(name,sv);
    }

    void entityCard(long id,String kind,String name){
        LinearLayout c=CortexUi.card(this,17);TextView t=CortexUi.text(this,name,14,CortexUi.TEXT);CortexUi.medium(t);c.addView(t);c.addView(CortexUi.plain(this,kind+"  •  tap for evidence & relationships",10,CortexUi.LIME));c.setOnClickListener(v->entityDialog(id,name));body.addView(c,margin(7));
    }

    void entityDialog(long id,String name){
        ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(10),dp(14),dp(14));sv.addView(box);
        SQLiteDatabase s=db.getReadableDatabase();
        TextView sources=CortexUi.plain(this,"EVIDENCE",10,CortexUi.LIME);CortexUi.medium(sources);box.addView(sources);
        Cursor m=s.rawQuery("SELECT em.mention_text,em.resolution_confidence,e.id,e.observed_at,e.source_type,e.source_uri,u.summary FROM kv2_entity_mentions em JOIN kv2_evidence e ON e.id=em.evidence_id LEFT JOIN kv2_understanding u ON u.evidence_id=e.id WHERE em.resolved_entity_id=? ORDER BY e.observed_at DESC LIMIT 20",new String[]{String.valueOf(id)});
        int n=0;while(m.moveToNext()){n++;String summary=m.getString(6),uri=m.getString(5);String line="Evidence #"+m.getLong(2)+"  •  "+Math.round(m.getDouble(1)*100)+"%  •  "+friendlyTime(m.getLong(3))+"\n"+safe(summary);TextView x=CortexUi.text(this,line,11,CortexUi.TEXT);x.setPadding(0,dp(7),0,dp(7));if(uri!=null&&!uri.isEmpty())x.setOnClickListener(v->openUri(uri));box.addView(x);}m.close();
        if(n==0)box.addView(CortexUi.text(this,"No Knowledge V2 source mention is linked yet.",11,CortexUi.MUTED));
        TextView rel=CortexUi.plain(this,"RELATIONSHIPS",10,CortexUi.LIME);CortexUi.medium(rel);rel.setPadding(0,dp(12),0,0);box.addView(rel);
        Cursor c=s.rawQuery("SELECT from_type,from_id,to_type,to_id,relation,confidence FROM kv2_edges WHERE (from_type='entity' AND from_id=?) OR (to_type='entity' AND to_id=?) ORDER BY confidence DESC LIMIT 80",new String[]{String.valueOf(id),String.valueOf(id)});
        int edges=0;while(c.moveToNext()){
            String ft=c.getString(0),tt=c.getString(2),relation=c.getString(4);long fid=c.getLong(1),tid=c.getLong(3);double confidence=c.getDouble(5);
            boolean entityIsFrom="entity".equals(ft)&&fid==id;String otherType=entityIsFrom?tt:ft;long otherId=entityIsFrom?tid:fid;
            String line=friendlyRelation(relation)+"  •  "+Math.round(confidence*100)+"%\n"+describeNode(s,otherType,otherId);
            TextView x=CortexUi.text(this,line,11,CortexUi.TEXT);x.setPadding(0,dp(6),0,dp(6));box.addView(x);edges++;
        }c.close();
        if(edges==0)box.addView(CortexUi.text(this,"No evidence-grounded relationships inferred yet.",11,CortexUi.MUTED));
        showDialog(name,sv);
    }

    void factCard(long factId,String predicate,String type,String value,double conf,long evidenceId,String uri,long observedAt){
        LinearLayout c=CortexUi.card(this,17);TextView h=CortexUi.plain(this,friendlyPredicate(predicate),10,CortexUi.LIME);CortexUi.medium(h);c.addView(h);
        c.addView(CortexUi.text(this,value,13,CortexUi.TEXT));c.addView(CortexUi.plain(this,type+"  •  "+Math.round(conf*100)+"%  •  evidence #"+evidenceId+"  •  "+friendlyTime(observedAt),9,CortexUi.MUTED));
        if(uri!=null&&!uri.isEmpty())c.setOnClickListener(v->openUri(uri));
        body.addView(c,margin(7));
    }

    String describeNode(SQLiteDatabase s,String type,long id){
        String t=type==null?"":type.toLowerCase(Locale.ROOT);
        try{
            if("fact".equals(t)||"fact".equalsIgnoreCase(type)){
                Cursor c=s.rawQuery("SELECT object_value,object_type FROM kv2_facts WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});String out=c.moveToFirst()?c.getString(0)+"  •  "+c.getString(1):"Fact #"+id;c.close();return out;
            }
            if("event".equals(t)||"event".equalsIgnoreCase(type)){
                Cursor c=s.rawQuery("SELECT title FROM kv2_events WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});String out=c.moveToFirst()?c.getString(0):"Event #"+id;c.close();return safe(out);
            }
            if("entity".equals(t)||"entity".equalsIgnoreCase(type)){
                Cursor c=s.rawQuery("SELECT canonical_name,kind FROM entity_nodes WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});String out=c.moveToFirst()?c.getString(0)+"  •  "+c.getString(1):"Entity #"+id;c.close();return safe(out);
            }
            if("evidence".equals(t)||"evidence".equalsIgnoreCase(type)){
                Cursor c=s.rawQuery("SELECT COALESCE(u.summary,e.raw_text) FROM kv2_evidence e LEFT JOIN kv2_understanding u ON u.evidence_id=e.id WHERE e.id=? LIMIT 1",new String[]{String.valueOf(id)});String out=c.moveToFirst()?safe(c.getString(0)):"Evidence #"+id;c.close();return out;
            }
        }catch(Throwable ignored){}
        return (type==null?"Knowledge":type)+" #"+id;
    }

    String friendlyRelation(String raw){
        String r=raw==null?"":raw;
        if("MENTIONS".equals(r))return"Mentioned in evidence";
        if("IDENTITY_MENTION_SUPPORTED_BY".equals(r))return"Identity supported by";
        if("CO_MENTIONED_WITH_AMOUNT".equals(r))return"Associated amount";
        if("CO_MENTIONED_WITH_DATE".equals(r))return"Associated date";
        if("CO_MENTIONED_WITH_REFERENCE".equals(r))return"Related reference";
        if("CO_MENTIONED_WITH_CONTACT".equals(r))return"Related contact";
        if("ACTION_INVOLVES".equals(r))return"Involved in action";
        return r.replace('_',' ').toLowerCase(Locale.ROOT);
    }
    String friendlyPredicate(String raw){String x=raw==null?"":raw.replace('_',' ').toLowerCase(Locale.ROOT);if(x.isEmpty())return"FACT";return Character.toUpperCase(x.charAt(0))+x.substring(1);}

    void showDialog(String title,ScrollView view){
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(view).setNegativeButton("Close",null).create();
        d.setOnShowListener(x->{try{d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(CortexUi.LIME);if(d.getWindow()!=null)d.getWindow().setBackgroundDrawable(CortexUi.round(this,CortexUi.SURFACE_3,CortexUi.BORDER,22));}catch(Throwable ignored){}});d.show();
    }

    void openUri(String uri){try{Intent i=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Throwable ignored){}}
    String friendlyTime(long when){if(when<=0)return"time unknown";return DateFormat.format("dd MMM yyyy · HH:mm",when).toString();}
    String safe(String s){String x=s==null?"":s.trim();return x.isEmpty()?"Source evidence":(x.length()>220?x.substring(0,220)+"…":x);}
    int countSemanticEntities(SQLiteDatabase s){Cursor c=s.rawQuery("SELECT COUNT(*) FROM entity_nodes WHERE status='active' AND upper(kind) IN ('PERSON','PROJECT','ORGANIZATION','ORG','PRODUCT','PLACE')",null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    int[] processing(SQLiteDatabase s){int[] x=new int[6];Cursor c=s.rawQuery("SELECT state,COUNT(*) FROM kv2_processing WHERE stage=? AND pipeline_version=? GROUP BY state",new String[]{KnowledgeV2Store.STAGE_EXTRACTION,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});while(c.moveToNext()){String st=c.getString(0);int n=c.getInt(1);if("PENDING".equals(st))x[0]=n;else if("RUNNING".equals(st))x[1]=n;else if("DONE".equals(st))x[2]=n;else if("BLOCKED".equals(st))x[3]=n;else if("FAILED".equals(st))x[4]=n;else if("SKIPPED".equals(st))x[5]=n;}c.close();return x;}
    int count(SQLiteDatabase s,String table,String where){Cursor c=s.rawQuery("SELECT COUNT(*) FROM "+table+(where==null||where.isEmpty()?"":" WHERE "+where),null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,0);return p;}
}
