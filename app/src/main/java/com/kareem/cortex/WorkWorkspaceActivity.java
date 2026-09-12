package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** High-level professional intelligence surface. Work Vault remains the grounded evidence store. */
public final class WorkWorkspaceActivity extends Activity {
    private VaultDb db;
    private LinearLayout content;
    private boolean destroyed;

    private int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());WorkVaultIndexSchema.ensure(db.getWritableDatabase());build();render();
    }

    @Override protected void onResume(){super.onResume();if(!destroyed)render();}
    @Override protected void onDestroy(){destroyed=true;if(db!=null)try{db.close();}catch(Throwable ignored){}db=null;super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(30));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"work",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void render(){
        if(destroyed||content==null||db==null)return;content.removeAllViews();
        WorkVaultScanner.Counts c=WorkVaultScanner.counts(db);ArrayList<ProjectRow> projects=loadProjects(4);ArrayList<WorkProcurementCaseEngine.Case> cases=WorkProcurementCaseEngine.load(db,8);
        int attention=0;for(WorkProcurementCaseEngine.Case x:cases)if(x.needsAttention())attention++;
        content.addView(topBar());content.addView(hero(c,attention),margins(0,5,0,0));content.addView(askBar(),margins(0,12,0,0));
        content.addView(CortexUi.section(this,"Workspace"));content.addView(workspaceGrid(c),new LinearLayout.LayoutParams(-1,-2));
        if(attention>0){
            content.addView(sectionRow("Needs attention",attention,CortexUi.YELLOW));int shown=0;for(WorkProcurementCaseEngine.Case x:cases){if(!x.needsAttention())continue;caseRow(x);if(++shown>=3)break;}
            TextView note=CortexUi.text(this,"Evidence gap means the indexed archive does not currently contain that stage. It is not a claim that the real-world step never happened.",10,CortexUi.FAINT);note.setPadding(dp(3),dp(2),dp(3),0);content.addView(note);
        }
        content.addView(sectionRow("Projects",projects.size(),CortexUi.LIME));
        if(projects.isEmpty())content.addView(emptyProjectState());else{
            for(ProjectRow p:projects)projectRow(p);
            TextView all=CortexUi.action(this,"Open all projects",CortexUi.MUTED,false);all.setOnClickListener(v->openBrowse(WorkVaultBrowseActivity.MODE_PROJECTS));content.addView(all,marginsHeight(0,8,0,0,44));
        }
        content.addView(CortexUi.section(this,"Create"));LinearLayout createRow=new LinearLayout(this);createRow.setOrientation(LinearLayout.HORIZONTAL);
        createRow.addView(command("Create from archive","Grounded office files","file",CortexUi.LIME,v->open(WorkVaultActivity.class)),new LinearLayout.LayoutParams(0,dp(112),1));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(112),1);cp.setMargins(dp(8),0,0,0);createRow.addView(command("Build with ChatGPT","Reference-led documents","nodes",CortexUi.OLIVE,v->open(WorkVaultActivity.class)),cp);content.addView(createRow);
    }

    private LinearLayout topBar(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(8),dp(1),dp(11));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);row.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(CortexUi.eyebrow(this,"CORTEX · PROFESSIONAL INTELLIGENCE",CortexUi.LIME));TextView h=CortexUi.plain(this,"Work",34,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(1),0,0);titles.addView(h);
        TextView archive=CortexUi.chip(this,"Archive",CortexUi.MUTED,false);archive.setOnClickListener(v->open(WorkVaultActivity.class));row.addView(archive,new LinearLayout.LayoutParams(-2,dp(36)));return row;
    }

    private LinearLayout hero(WorkVaultScanner.Counts c,int attention){
        LinearLayout card=CortexUi.card(this,28);card.setPadding(dp(19),dp(18),dp(19),dp(17));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView eye=CortexUi.eyebrow(this,"WORKING MEMORY",CortexUi.LIME);top.addView(eye,new LinearLayout.LayoutParams(0,-2,1));
        boolean indexing=c.newFiles+c.modifiedFiles>0;String state=indexing?"indexing":(attention>0?attention+" to review":"ready");int stateColor=indexing?CortexUi.ORANGE:(attention>0?CortexUi.YELLOW:CortexUi.GREEN);top.addView(CortexUi.chip(this,state,stateColor,true),new LinearLayout.LayoutParams(-2,dp(30)));card.addView(top);
        String title=c.projects>0?c.projects+" projects connected":"Connect your professional archive";TextView h=CortexUi.plain(this,title,25,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(13),0,0);card.addView(h);
        TextView b=CortexUi.text(this,"Prices, procurement, follow-up and source evidence stay connected to the files they came from.",12,CortexUi.MUTED);b.setPadding(0,dp(6),0,dp(15));card.addView(b);
        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.addView(CortexUi.metric(this,String.valueOf(c.projects),"PROJECTS",c.projects>0?CortexUi.LIME:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(68),1));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(68),1);mp.setMargins(dp(7),0,dp(7),0);stats.addView(CortexUi.metric(this,String.valueOf(c.openFollowUps),"OPEN",c.openFollowUps>0?CortexUi.YELLOW:CortexUi.FAINT),mp);stats.addView(CortexUi.metric(this,String.valueOf(c.prices),"PRICES",c.prices>0?CortexUi.GREEN:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(68),1));card.addView(stats);return card;
    }

    private LinearLayout askBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(14),dp(10),dp(10),dp(10));CortexUi.pressable(this,bar,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,19));bar.addView(CortexUi.glyph(this,"search",CortexUi.LIME,true),new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(8),0);bar.addView(copy,xp);TextView h=CortexUi.plain(this,"Ask your work archive",14,CortexUi.TEXT);CortexUi.medium(h);copy.addView(h);TextView s=CortexUi.plain(this,"Projects · vendors · PR / PO · prices · follow-up",10,CortexUi.MUTED);s.setPadding(0,dp(2),0,0);copy.addView(s);TextView arrow=CortexUi.plain(this,"›",25,CortexUi.LIME);arrow.setGravity(Gravity.CENTER);bar.addView(arrow,new LinearLayout.LayoutParams(dp(30),dp(40)));bar.setOnClickListener(v->open(WorkVaultAskActivity.class));return bar;
    }

    private LinearLayout workspaceGrid(WorkVaultScanner.Counts c){
        LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);LinearLayout a=new LinearLayout(this);a.setOrientation(LinearLayout.HORIZONTAL);
        a.addView(command("Projects",c.projects+" active","project",CortexUi.LIME,v->openBrowse(WorkVaultBrowseActivity.MODE_PROJECTS)),new LinearLayout.LayoutParams(0,dp(118),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(118),1);p.setMargins(dp(8),0,0,0);a.addView(command("Follow-up",c.openFollowUps+" open","bolt",CortexUi.YELLOW,v->open(WorkFollowUpActivity.class)),p);group.addView(a);
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.HORIZONTAL);b.setPadding(0,dp(8),0,0);
        b.addView(command("Price intelligence",c.prices+" records","trend",CortexUi.GREEN,v->openBrowse(WorkVaultBrowseActivity.MODE_PRICES)),new LinearLayout.LayoutParams(0,dp(118),1));
        LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(0,dp(118),1);q.setMargins(dp(8),0,0,0);b.addView(command("Files & evidence",c.files+" indexed","file",CortexUi.OLIVE,v->openBrowse(WorkVaultBrowseActivity.MODE_FILES)),q);group.addView(b);return group;
    }

    private LinearLayout command(String title,String subtitle,String icon,int color,View.OnClickListener click){
        LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(14),dp(13),dp(14),dp(12));CortexUi.pressable(this,card,CortexUi.velvet(this,22));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(40),dp(40)));TextView arrow=CortexUi.plain(this,"›",22,CortexUi.FAINT);arrow.setGravity(Gravity.CENTER);top.addView(arrow,new LinearLayout.LayoutParams(0,dp(40),1));card.addView(top);TextView h=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(7),0,0);card.addView(h);TextView s=CortexUi.plain(this,subtitle,10,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);card.addView(s);card.setOnClickListener(click);return card;
    }

    private LinearLayout sectionRow(String label,int count,int color){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(24),dp(1),dp(10));View dot=new View(this);dot.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView h=CortexUi.plain(this,label,12,CortexUi.TEXT);CortexUi.medium(h);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(dp(9),0,0,0);row.addView(h,hp);row.addView(CortexUi.plain(this,String.valueOf(count),10,CortexUi.MUTED));return row;}

    private void caseRow(WorkProcurementCaseEngine.Case x){LinearLayout row=CortexUi.card(this,21);row.setPadding(dp(15),dp(14),dp(15),dp(14));String name=(x.project.isEmpty()?"":x.project+" · ")+"PR "+x.pr;TextView h=CortexUi.plain(this,name,14,CortexUi.TEXT);CortexUi.medium(h);row.addView(h);TextView issue=CortexUi.text(this,x.headline(),11,CortexUi.YELLOW);issue.setPadding(0,dp(5),0,0);row.addView(issue);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));content.addView(row,p);}

    private void projectRow(ProjectRow p){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(3),dp(12),dp(3),dp(12));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView h=CortexUi.plain(this,p.name,14,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView s=CortexUi.plain(this,p.refs+" procurement refs · "+p.prices+" prices",10,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);tx.addView(s);TextView arrow=CortexUi.plain(this,"›",23,CortexUi.FAINT);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(38)));row.setOnClickListener(v->openBrowse(WorkVaultBrowseActivity.MODE_PROJECTS));content.addView(row);content.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}

    private LinearLayout emptyProjectState(){LinearLayout card=CortexUi.card(this,22);TextView h=CortexUi.plain(this,"No projects indexed yet",16,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);TextView b=CortexUi.text(this,"Open the archive to connect a work folder. Originals stay in place while Cortex builds grounded professional memory.",11,CortexUi.MUTED);b.setPadding(0,dp(5),0,dp(10));card.addView(b);TextView a=CortexUi.action(this,"Open archive",CortexUi.LIME,true);a.setOnClickListener(v->open(WorkVaultActivity.class));card.addView(a,new LinearLayout.LayoutParams(-1,dp(43)));return card;}

    private ArrayList<ProjectRow> loadProjects(int limit){ArrayList<ProjectRow> out=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT p.id,p.canonical_name,(SELECT COUNT(*) FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE r.project_id=p.id AND f.active_version_id>0 AND r.version_id=f.active_version_id),(SELECT COUNT(*) FROM work_price_records pr JOIN work_files f2 ON f2.id=pr.file_id WHERE pr.project_id=p.id AND f2.active_version_id>0 AND pr.version_id=f2.active_version_id) FROM work_projects p WHERE p.state='active' ORDER BY p.updated_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});try{while(c.moveToNext())out.add(new ProjectRow(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3)));}finally{c.close();}return out;}

    private void openBrowse(String mode){try{Intent i=new Intent(this,WorkVaultBrowseActivity.class);i.putExtra(WorkVaultBrowseActivity.EXTRA_MODE,mode);startActivity(i);}catch(Throwable ignored){}}
    private void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable ignored){}}
    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private LinearLayout.LayoutParams marginsHeight(int l,int t,int r,int b,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h));p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private static final class ProjectRow{final long id,refs,prices;final String name;ProjectRow(long id,String name,long refs,long prices){this.id=id;this.name=name==null?"Project":name;this.refs=refs;this.prices=prices;}}
}
