package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Dedicated grounded browse surfaces for projects, indexed files, and price evidence. */
public final class WorkVaultBrowseActivity extends Activity {
    public static final String EXTRA_MODE="work_vault_browse_mode";
    public static final String EXTRA_PROJECT_ID="work_vault_project_id";
    public static final String MODE_PROJECTS="projects";
    public static final String MODE_FILES="files";
    public static final String MODE_PRICES="prices";
    public static final String VERSION="work_vault_browse_activity_002";

    private VaultDb db;
    private LinearLayout content;
    private String mode=MODE_PROJECTS;
    private long projectId;
    private boolean destroyed;
    private final DecimalFormat number=new DecimalFormat("#,##0.##");

    private int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());WorkVaultIndexSchema.ensure(db.getWritableDatabase());
        Intent intent=getIntent();mode=normalizeMode(intent==null?null:intent.getStringExtra(EXTRA_MODE));projectId=normalizeProjectId(intent==null?0:intent.getLongExtra(EXTRA_PROJECT_ID,0));build();render();
    }

    @Override protected void onResume(){super.onResume();if(!destroyed)render();}
    @Override protected void onDestroy(){destroyed=true;if(db!=null)try{db.close();}catch(Throwable ignored){}db=null;super.onDestroy();}

    static String normalizeMode(String raw){
        String x=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);
        if(MODE_FILES.equals(x)||MODE_PRICES.equals(x)||MODE_PROJECTS.equals(x))return x;
        return MODE_PROJECTS;
    }

    static long normalizeProjectId(long raw){return Math.max(0L,raw);}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(30));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"work",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void render(){
        if(destroyed||content==null||db==null)return;content.removeAllViews();content.addView(topBar());
        if(MODE_FILES.equals(mode))renderFiles();else if(MODE_PRICES.equals(mode))renderPrices();else if(projectId>0)renderProjectDetail(projectId);else renderProjects();
    }

    private LinearLayout topBar(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(8),dp(1),dp(12));
        TextView back=CortexUi.chip(this,projectId>0?"‹ Projects":"‹ Work",CortexUi.MUTED,false);back.setOnClickListener(v->finish());row.addView(back,new LinearLayout.LayoutParams(-2,dp(36)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),0,0,0);row.addView(titles,tp);
        titles.addView(CortexUi.eyebrow(this,"GROUNDED WORK VAULT",CortexUi.LIME));
        String title=MODE_FILES.equals(mode)?"Files & evidence":MODE_PRICES.equals(mode)?"Price intelligence":projectId>0?"Project detail":"Projects";
        TextView h=CortexUi.plain(this,title,28,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);return row;
    }

    private void renderProjects(){
        ArrayList<ProjectRow> rows=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT p.id,p.canonical_name,"+
                "(SELECT COUNT(DISTINCT r.file_id) FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE r.project_id=p.id AND f.active_version_id>0 AND r.version_id=f.active_version_id),"+
                "(SELECT COUNT(*) FROM work_price_records pr JOIN work_files f2 ON f2.id=pr.file_id WHERE pr.project_id=p.id AND f2.active_version_id>0 AND pr.version_id=f2.active_version_id),"+
                "(SELECT COUNT(*) FROM work_followup_records u JOIN work_files f3 ON f3.id=u.file_id WHERE u.project_id=p.id AND f3.active_version_id>0 AND u.version_id=f3.active_version_id AND u.status_normalized NOT IN ('closed','done','complete','completed','cancelled')) "+
                "FROM work_projects p WHERE p.state='active' ORDER BY p.updated_at DESC,p.canonical_name COLLATE NOCASE",null);
        try{while(c.moveToNext())rows.add(new ProjectRow(c.getLong(0),safe(c.getString(1)),c.getLong(2),c.getLong(3),c.getLong(4)));}finally{c.close();}
        content.addView(summary(rows.size()+" active project"+(rows.size()==1?"":"s"),"Counts use only each file's active indexed version. Tap a project to inspect its grounded evidence."));
        if(rows.isEmpty()){empty("No projects indexed yet","Connect and index an archive source first.");return;}
        for(ProjectRow r:rows){
            LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(15),dp(14),dp(15),dp(14));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView h=CortexUi.plain(this,r.name.isEmpty()?"Unnamed project":r.name,15,CortexUi.TEXT);CortexUi.medium(h);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=CortexUi.plain(this,"›",22,CortexUi.FAINT);arrow.setGravity(Gravity.CENTER);top.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(34)));card.addView(top);
            TextView s=CortexUi.text(this,r.refs+" procurement refs · "+r.prices+" prices · "+r.open+" open follow-up",11,r.open>0?CortexUi.YELLOW:CortexUi.MUTED);s.setPadding(0,dp(5),0,0);card.addView(s);
            card.setOnClickListener(v->openProject(r.id));CortexUi.pressable(this,card,CortexUi.velvet(this,22));content.addView(card,margins(0,0,0,8));
        }
    }

    private void renderProjectDetail(long id){
        ProjectRow project=loadProject(id);
        if(project==null){projectId=0;empty("Project not available","This project is missing or no longer active.");return;}
        content.addView(summary(project.name.isEmpty()?"Unnamed project":project.name,project.refs+" procurement refs · "+project.prices+" prices · "+project.open+" open follow-up"));

        ArrayList<RefRow> refs=new ArrayList<>();Cursor rc=db.getReadableDatabase().rawQuery(
                "SELECT r.ref_type,r.ref_value,f.display_name,f.document_uri,r.confidence FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE r.project_id=? AND f.active_version_id>0 AND r.version_id=f.active_version_id ORDER BY r.ref_type,r.normalized_value,r.id DESC LIMIT 120",new String[]{String.valueOf(id)});
        try{while(rc.moveToNext())refs.add(new RefRow(safe(rc.getString(0)),safe(rc.getString(1)),safe(rc.getString(2)),safe(rc.getString(3)),rc.getDouble(4)));}finally{rc.close();}
        content.addView(section("Procurement",refs.size(),CortexUi.LIME));
        if(refs.isEmpty())empty("No active procurement references","No PR/PO reference is currently grounded to this project.");else for(RefRow r:refs){
            LinearLayout card=CortexUi.card(this,19);card.setPadding(dp(14),dp(12),dp(14),dp(12));TextView h=CortexUi.plain(this,(r.type.isEmpty()?"REF":r.type)+" "+r.value,13,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);TextView s=CortexUi.text(this,r.file,10,CortexUi.MUTED);s.setPadding(0,dp(4),0,0);card.addView(s);makeOpenable(card,r.uri,19);content.addView(card,margins(0,0,0,7));
        }

        ArrayList<FollowRow> follow=new ArrayList<>();Cursor fc=db.getReadableDatabase().rawQuery(
                "SELECT COALESCE(u.reference_type,''),COALESCE(u.reference_value,''),COALESCE(u.item_name,''),COALESCE(u.status,''),u.status_normalized,COALESCE(u.owner_name,''),COALESCE(u.due_text,''),f.display_name,f.document_uri,COALESCE(u.sheet_name,''),u.page_number,u.row_number FROM work_followup_records u JOIN work_files f ON f.id=u.file_id WHERE u.project_id=? AND f.active_version_id>0 AND u.version_id=f.active_version_id ORDER BY CASE WHEN u.status_normalized IN ('closed','done','complete','completed','cancelled') THEN 1 ELSE 0 END,u.created_at DESC LIMIT 120",new String[]{String.valueOf(id)});
        try{while(fc.moveToNext())follow.add(new FollowRow(safe(fc.getString(0)),safe(fc.getString(1)),safe(fc.getString(2)),safe(fc.getString(3)),safe(fc.getString(4)),safe(fc.getString(5)),safe(fc.getString(6)),safe(fc.getString(7)),safe(fc.getString(8)),safe(fc.getString(9)),fc.getInt(10),fc.getInt(11)));}finally{fc.close();}
        content.addView(section("Follow-up",follow.size(),CortexUi.YELLOW));
        if(follow.isEmpty())empty("No follow-up rows","No active follow-up evidence is currently linked to this project.");else for(FollowRow r:follow){
            boolean closed=isClosed(r.normalized);LinearLayout card=CortexUi.card(this,19);card.setPadding(dp(14),dp(12),dp(14),dp(12));String label=!r.item.isEmpty()?r.item:(!r.ref.isEmpty()?(r.type.isEmpty()?"REF":r.type)+" "+r.ref:"Follow-up item");TextView h=CortexUi.plain(this,label,13,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);String status=r.status.isEmpty()?r.normalized:r.status;TextView d=CortexUi.text(this,(status.isEmpty()?"unknown":status)+(r.owner.isEmpty()?"":" · "+r.owner)+(r.due.isEmpty()?"":" · "+r.due),11,closed?CortexUi.MUTED:CortexUi.YELLOW);d.setPadding(0,dp(4),0,0);card.addView(d);TextView s=CortexUi.text(this,r.file+location(r.sheet,r.page,r.row),10,CortexUi.FAINT);s.setPadding(0,dp(4),0,0);card.addView(s);makeOpenable(card,r.uri,19);content.addView(card,margins(0,0,0,7));
        }

        ArrayList<PriceRow> prices=loadPricesForProject(id,80);content.addView(section("Prices",prices.size(),CortexUi.GREEN));
        if(prices.isEmpty())empty("No active prices","No current price evidence is linked to this project.");else for(PriceRow r:prices)priceCard(r);

        ArrayList<FileRow> files=loadProjectFiles(id,120);content.addView(section("Files & evidence",files.size(),CortexUi.OLIVE));
        if(files.isEmpty())empty("No active evidence files","No active-version file is currently linked to this project.");else for(FileRow r:files)fileCard(r);
    }

    private ProjectRow loadProject(long id){
        Cursor c=db.getReadableDatabase().rawQuery("SELECT p.id,p.canonical_name,(SELECT COUNT(*) FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE r.project_id=p.id AND f.active_version_id>0 AND r.version_id=f.active_version_id),(SELECT COUNT(*) FROM work_price_records pr JOIN work_files f2 ON f2.id=pr.file_id WHERE pr.project_id=p.id AND f2.active_version_id>0 AND pr.version_id=f2.active_version_id),(SELECT COUNT(*) FROM work_followup_records u JOIN work_files f3 ON f3.id=u.file_id WHERE u.project_id=p.id AND f3.active_version_id>0 AND u.version_id=f3.active_version_id AND u.status_normalized NOT IN ('closed','done','complete','completed','cancelled')) FROM work_projects p WHERE p.id=? AND p.state='active' LIMIT 1",new String[]{String.valueOf(id)});
        try{return c.moveToFirst()?new ProjectRow(c.getLong(0),safe(c.getString(1)),c.getLong(2),c.getLong(3),c.getLong(4)):null;}finally{c.close();}
    }

    private ArrayList<FileRow> loadProjectFiles(long id,int limit){
        ArrayList<FileRow> rows=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT f.display_name,COALESCE(s.display_name,''),f.document_uri,COALESCE(f.extension,''),f.size_bytes,f.state,f.active_version_id,f.indexed_at FROM work_files f LEFT JOIN work_sources s ON s.id=f.source_id WHERE f.state<>'missing' AND f.active_version_id>0 AND (EXISTS(SELECT 1 FROM work_procurement_refs r WHERE r.file_id=f.id AND r.project_id=? AND r.version_id=f.active_version_id) OR EXISTS(SELECT 1 FROM work_price_records pr WHERE pr.file_id=f.id AND pr.project_id=? AND pr.version_id=f.active_version_id) OR EXISTS(SELECT 1 FROM work_followup_records u WHERE u.file_id=f.id AND u.project_id=? AND u.version_id=f.active_version_id) OR EXISTS(SELECT 1 FROM work_facts w WHERE w.file_id=f.id AND w.project_id=? AND w.version_id=f.active_version_id)) ORDER BY f.updated_at DESC LIMIT ?",new String[]{String.valueOf(id),String.valueOf(id),String.valueOf(id),String.valueOf(id),String.valueOf(Math.max(1,limit))});
        try{while(c.moveToNext())rows.add(new FileRow(safe(c.getString(0)),safe(c.getString(1)),safe(c.getString(2)),safe(c.getString(3)),c.getLong(4),safe(c.getString(5)),c.getLong(6),c.getLong(7)));}finally{c.close();}return rows;
    }

    private ArrayList<PriceRow> loadPricesForProject(long id,int limit){
        ArrayList<PriceRow> rows=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT pr.item_name,COALESCE(pr.vendor_name,''),pr.quantity,COALESCE(pr.unit,''),pr.unit_price,pr.total_price,COALESCE(pr.currency,''),COALESCE(p.canonical_name,''),f.display_name,f.document_uri,COALESCE(pr.sheet_name,''),pr.page_number,pr.row_number,pr.confidence FROM work_price_records pr JOIN work_files f ON f.id=pr.file_id LEFT JOIN work_projects p ON p.id=pr.project_id WHERE pr.project_id=? AND f.active_version_id>0 AND pr.version_id=f.active_version_id ORDER BY pr.created_at DESC,pr.id DESC LIMIT ?",new String[]{String.valueOf(id),String.valueOf(Math.max(1,limit))});
        try{while(c.moveToNext())rows.add(new PriceRow(safe(c.getString(0)),safe(c.getString(1)),nullableDouble(c,2),safe(c.getString(3)),nullableDouble(c,4),nullableDouble(c,5),safe(c.getString(6)),safe(c.getString(7)),safe(c.getString(8)),safe(c.getString(9)),safe(c.getString(10)),c.getInt(11),c.getInt(12),c.getDouble(13)));}finally{c.close();}return rows;
    }

    private void renderFiles(){
        ArrayList<FileRow> rows=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT f.display_name,COALESCE(s.display_name,''),f.document_uri,COALESCE(f.extension,''),f.size_bytes,f.state,f.active_version_id,f.indexed_at FROM work_files f LEFT JOIN work_sources s ON s.id=f.source_id WHERE f.state<>'missing' ORDER BY f.updated_at DESC LIMIT 300",null);
        try{while(c.moveToNext())rows.add(new FileRow(safe(c.getString(0)),safe(c.getString(1)),safe(c.getString(2)),safe(c.getString(3)),c.getLong(4),safe(c.getString(5)),c.getLong(6),c.getLong(7)));}finally{c.close();}
        content.addView(summary(rows.size()+" visible file"+(rows.size()==1?"":"s"),"Tap a row to open the original document. Originals remain outside Cortex."));if(rows.isEmpty()){empty("No files discovered yet","Scan an archive source to populate this view.");return;}for(FileRow r:rows)fileCard(r);
    }

    private void fileCard(FileRow r){
        LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(14),dp(13),dp(14),dp(13));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView h=CortexUi.plain(this,r.name,14,CortexUi.TEXT);CortexUi.medium(h);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));int stateColor=r.activeVersion>0?CortexUi.GREEN:("failed".equals(r.state)?CortexUi.ORANGE:CortexUi.YELLOW);top.addView(CortexUi.chip(this,r.activeVersion>0?"indexed":r.state,stateColor,true),new LinearLayout.LayoutParams(-2,dp(28)));card.addView(top);String meta=(r.ext.isEmpty()?"FILE":r.ext.toUpperCase(Locale.ROOT))+" · "+humanBytes(r.bytes)+(r.source.isEmpty()?"":" · "+r.source);TextView s=CortexUi.text(this,meta,10,CortexUi.MUTED);s.setPadding(0,dp(5),0,0);card.addView(s);makeOpenable(card,r.uri,20);content.addView(card,margins(0,0,0,8));
    }

    private void renderPrices(){
        ArrayList<PriceRow> rows=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT pr.item_name,COALESCE(pr.vendor_name,''),pr.quantity,COALESCE(pr.unit,''),pr.unit_price,pr.total_price,COALESCE(pr.currency,''),COALESCE(p.canonical_name,''),f.display_name,f.document_uri,COALESCE(pr.sheet_name,''),pr.page_number,pr.row_number,pr.confidence FROM work_price_records pr JOIN work_files f ON f.id=pr.file_id LEFT JOIN work_projects p ON p.id=pr.project_id WHERE f.active_version_id>0 AND pr.version_id=f.active_version_id ORDER BY pr.created_at DESC,pr.id DESC LIMIT 300",null);
        try{while(c.moveToNext())rows.add(new PriceRow(safe(c.getString(0)),safe(c.getString(1)),nullableDouble(c,2),safe(c.getString(3)),nullableDouble(c,4),nullableDouble(c,5),safe(c.getString(6)),safe(c.getString(7)),safe(c.getString(8)),safe(c.getString(9)),safe(c.getString(10)),c.getInt(11),c.getInt(12),c.getDouble(13)));}finally{c.close();}
        content.addView(summary(rows.size()+" recent active price record"+(rows.size()==1?"":"s"),"Every price stays linked to its source file and exact sheet/page/row provenance when available."));if(rows.isEmpty()){empty("No price records yet","Index quotations, comparisons or commercial sheets to build price intelligence.");return;}for(PriceRow r:rows)priceCard(r);
    }

    private void priceCard(PriceRow r){
        LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(15),dp(14),dp(15),dp(14));TextView h=CortexUi.plain(this,r.item.isEmpty()?"Unnamed item":r.item,14,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);String price=money(r.unitPrice,r.currency);if(price.isEmpty())price=money(r.total,r.currency);String detail=(r.vendor.isEmpty()?"Unknown vendor":r.vendor)+(price.isEmpty()?"":" · "+price)+(r.unit.isEmpty()?"":" / "+r.unit);TextView d=CortexUi.text(this,detail,12,CortexUi.GREEN);d.setPadding(0,dp(5),0,0);card.addView(d);String source=(r.project.isEmpty()?"":r.project+" · ")+r.file+location(r.sheet,r.page,r.row);TextView s=CortexUi.text(this,source,10,CortexUi.MUTED);s.setPadding(0,dp(5),0,0);card.addView(s);makeOpenable(card,r.uri,22);content.addView(card,margins(0,0,0,8));
    }

    private LinearLayout section(String label,int count,int color){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(22),dp(1),dp(9));View dot=new View(this);dot.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView h=CortexUi.plain(this,label,12,CortexUi.TEXT);CortexUi.medium(h);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(dp(9),0,0,0);row.addView(h,hp);row.addView(CortexUi.plain(this,String.valueOf(count),10,CortexUi.MUTED));return row;}
    private LinearLayout summary(String headline,String body){LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(15),dp(13),dp(15),dp(13));TextView h=CortexUi.plain(this,headline,15,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);TextView b=CortexUi.text(this,body,10,CortexUi.MUTED);b.setPadding(0,dp(4),0,0);card.addView(b);LinearLayout.LayoutParams p=margins(0,0,0,12);card.setLayoutParams(p);return card;}
    private void empty(String title,String body){LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(16),dp(17),dp(16),dp(17));TextView h=CortexUi.plain(this,title,16,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);TextView b=CortexUi.text(this,body,11,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);card.addView(b);content.addView(card,margins(0,0,0,8));}
    private void makeOpenable(LinearLayout card,String raw,int radius){if(raw==null||raw.isEmpty())return;card.setOnClickListener(v->openUri(raw));CortexUi.pressable(this,card,CortexUi.velvet(this,radius));}
    private void openProject(long id){if(id<=0)return;try{Intent i=new Intent(this,WorkVaultBrowseActivity.class);i.putExtra(EXTRA_MODE,MODE_PROJECTS);i.putExtra(EXTRA_PROJECT_ID,id);startActivity(i);}catch(Throwable ignored){}}
    private void openUri(String raw){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setData(Uri.parse(raw));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Throwable e){android.widget.Toast.makeText(this,"Could not open source file",android.widget.Toast.LENGTH_LONG).show();}}
    private String money(Double value,String currency){if(value==null)return "";String x=number.format(value);return currency==null||currency.trim().isEmpty()?x:x+" "+currency.trim();}
    private static String location(String sheet,int page,int row){if(sheet!=null&&!sheet.isEmpty())return " · "+sheet+(row>0?" row "+row:"");if(page>0)return " · page "+page;if(row>0)return " · row "+row;return "";}
    private static boolean isClosed(String status){String x=status==null?"":status.trim().toLowerCase(Locale.ROOT);return "closed".equals(x)||"done".equals(x)||"complete".equals(x)||"completed".equals(x)||"cancelled".equals(x);}
    private static Double nullableDouble(Cursor c,int index){return c.isNull(index)?null:c.getDouble(index);}
    private static String safe(String s){return s==null?"":s;}
    private static String humanBytes(long b){if(b<1024)return b+" B";double k=b/1024.0;if(k<1024)return String.format(Locale.ROOT,"%.1f KB",k);double m=k/1024.0;if(m<1024)return String.format(Locale.ROOT,"%.1f MB",m);return String.format(Locale.ROOT,"%.1f GB",m/1024.0);}
    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}

    private static final class ProjectRow{final long id,refs,prices,open;final String name;ProjectRow(long id,String name,long refs,long prices,long open){this.id=id;this.name=name;this.refs=refs;this.prices=prices;this.open=open;}}
    private static final class RefRow{final String type,value,file,uri;final double confidence;RefRow(String t,String v,String f,String u,double c){type=t;value=v;file=f;uri=u;confidence=c;}}
    private static final class FollowRow{final String type,ref,item,status,normalized,owner,due,file,uri,sheet;final int page,row;FollowRow(String t,String r,String i,String s,String n,String o,String d,String f,String u,String sh,int p,int rw){type=t;ref=r;item=i;status=s;normalized=n;owner=o;due=d;file=f;uri=u;sheet=sh;page=p;row=rw;}}
    private static final class FileRow{final String name,source,uri,ext,state;final long bytes,activeVersion,indexedAt;FileRow(String n,String s,String u,String e,long b,String st,long av,long ia){name=n;source=s;uri=u;ext=e;bytes=b;state=st;activeVersion=av;indexedAt=ia;}}
    private static final class PriceRow{final String item,vendor,unit,currency,project,file,uri,sheet;final Double quantity,unitPrice,total;final int page,row;final double confidence;PriceRow(String i,String v,Double q,String u,Double up,Double t,String c,String p,String f,String uri,String sh,int pg,int rw,double cf){item=i;vendor=v;quantity=q;unit=u;unitPrice=up;total=t;currency=c;project=p;file=f;this.uri=uri;sheet=sh;page=pg;row=rw;confidence=cf;}}
}
