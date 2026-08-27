package com.kareem.cortex;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Cortex Library: one object map, no duplicate navigation layers. */
public final class ProposalPeopleProjectsActivity extends PeopleProjectsActivity {
    private LinearLayout objectRow;

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(CortexUi.aurora(this));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),0);body.setClipChildren(false);body.setClipToPadding(false);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(header());
        TextView expl=CortexUi.text(this,"People, projects, situations and memory — one map, organized by meaning.",12,CortexUi.MUTED);expl.setPadding(dp(2),0,dp(2),dp(12));body.addView(expl);
        objectRow=entryRow();LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(88));op.setMargins(0,0,0,dp(12));body.addView(objectRow,op);

        LinearLayout sb=CortexUi.card(this,22);sb.setOrientation(LinearLayout.HORIZONTAL);sb.setGravity(Gravity.CENTER_VERTICAL);sb.setPadding(dp(10),dp(5),dp(11),dp(5));sb.addView(CortexUi.glyph(this,"search",CortexUi.BRAND,false),new LinearLayout.LayoutParams(dp(40),dp(40)));
        search=new EditText(this);search.setHint("Search Library");search.setHintTextColor(CortexUi.FAINT);search.setTextColor(CortexUi.TEXT);search.setTextSize(13);search.setSingleLine(true);search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(9),0,0,0);sb.addView(search,new LinearLayout.LayoutParams(0,dp(46),1));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(58));sp.setMargins(0,0,0,dp(10));body.addView(sb,sp);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render(lastRows,lastMode);}public void afterTextChanged(Editable e){}});

        TextView scope=CortexUi.plain(this,"people".equals(mode)?"PEOPLE":"PROJECTS",10,CortexUi.BRAND);scope.setTag("scope_label");CortexUi.medium(scope);if(android.os.Build.VERSION.SDK_INT>=21)scope.setLetterSpacing(.12f);scope.setPadding(dp(2),dp(2),0,dp(8));body.addView(scope);

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);feed.setPadding(0,dp(2),0,dp(26));sv.addView(feed);body.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"library",null);setContentView(root);styleTabs();
    }

    View header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(7),dp(2),dp(4));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);TextView eye=CortexUi.plain(this,"KNOWLEDGE ATLAS",10,CortexUi.AURORA);CortexUi.medium(eye);if(android.os.Build.VERSION.SDK_INT>=21)eye.setLetterSpacing(.14f);tx.addView(eye);TextView c=CortexUi.plain(this,"Library",34,CortexUi.TEXT);CortexUi.bold(c);tx.addView(c);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));CortexGlyphView archive=CortexUi.glyph(this,"file",CortexUi.BRAND,false);archive.setOnClickListener(v->startActivity(new Intent(this,VaultActivity.class)));row.addView(archive,new LinearLayout.LayoutParams(dp(48),dp(48)));return row;}

    private LinearLayout entryRow(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);row.setClipChildren(false);row.setClipToPadding(false);
        String[] labels={"People","Projects","Situations","Memory"};String[] keys={"people","projects","situations","memory"};String[] icons={"person","project","clock","note"};
        for(int i=0;i<labels.length;i++){
            final String key=keys[i],label=labels[i];LinearLayout tile=new LinearLayout(this);tile.setTag(key);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(3),dp(5),dp(3),dp(4));
            tile.addView(CortexUi.glyph(this,icons[i],i<2?CortexUi.BRAND:CortexUi.AURORA,false),new LinearLayout.LayoutParams(dp(34),dp(34)));
            TextView v=CortexUi.plain(this,label,10,CortexUi.TEXT);CortexUi.medium(v);v.setGravity(Gravity.CENTER);v.setIncludeFontPadding(false);tile.addView(v,new LinearLayout.LayoutParams(-1,dp(24)));
            if("people".equals(key)||"projects".equals(key))tile.setOnClickListener(x->{mode=key;styleTabs();render(lastRows,lastMode);});
            else if("situations".equals(key))tile.setOnClickListener(x->{try{startActivity(new Intent(this,CompactTodayActivity.class));}catch(Throwable ignored){}});
            else tile.setOnClickListener(x->{try{startActivity(new Intent(this,VaultActivity.class));}catch(Throwable ignored){}});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(76),1);if(row.getChildCount()>0)p.setMargins(dp(7),0,0,0);row.addView(tile,p);
        }
        return row;
    }

    /** The four object entrances are the selector. There is deliberately no second People/Projects tab row. */
    @Override void styleTabs(){
        if(objectRow==null)return;
        for(int i=0;i<objectRow.getChildCount();i++){
            View raw=objectRow.getChildAt(i);if(!(raw instanceof LinearLayout))continue;LinearLayout tile=(LinearLayout)raw;String key=String.valueOf(tile.getTag());boolean selectable="people".equals(key)||"projects".equals(key);boolean on=selectable&&mode.equals(key);
            tile.setBackground(on?CortexUi.gradient(this,Color.argb(58,Color.red(CortexUi.BRAND),Color.green(CortexUi.BRAND),Color.blue(CortexUi.BRAND)),Color.argb(25,Color.red(CortexUi.AURORA),Color.green(CortexUi.AURORA),Color.blue(CortexUi.AURORA)),Color.argb(104,Color.red(CortexUi.BRAND),Color.green(CortexUi.BRAND),Color.blue(CortexUi.BRAND)),20):CortexUi.velvet(this,20));
        }
        try{ViewGroup parent=(ViewGroup)objectRow.getParent();for(int i=0;i<parent.getChildCount();i++){View v=parent.getChildAt(i);if(v instanceof TextView&&"scope_label".equals(v.getTag()))((TextView)v).setText("people".equals(mode)?"PEOPLE":"PROJECTS");}}catch(Throwable ignored){}
    }

    @Override void addRow(Row r){
        Insight in=relationshipInsight(r);int color=r.candidate?CortexUi.ORANGE:(in.openCount>0?CortexUi.BRAND:CortexUi.AURORA);String icon="people".equals(mode)?"person":"project";
        LinearLayout card=CortexUi.card(this,22);card.setPadding(0,0,0,0);LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(dp(13),dp(13),dp(11),dp(10));
        main.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(12),0,dp(8),0);main.addView(tx,xp);TextView n=CortexUi.text(this,r.name,16,CortexUi.TEXT);CortexUi.medium(n);n.setMaxLines(2);tx.addView(n);
        String meta=r.candidate?"Needs confirmation":in.statusLine;TextView m=CortexUi.plain(this,meta,10,r.candidate?CortexUi.ORANGE:(in.openCount>0?CortexUi.BRAND:CortexUi.MUTED));m.setPadding(0,dp(5),0,0);tx.addView(m);main.addView(CortexUi.chip(this,r.candidate?"Review":(in.openCount>0?"Live":"Context"),color,true),new LinearLayout.LayoutParams(-2,dp(32)));card.addView(main);main.setOnClickListener(v->detail(r));
        if(!r.candidate){LinearLayout intel=new LinearLayout(this);intel.setOrientation(LinearLayout.VERTICAL);intel.setPadding(dp(14),0,dp(13),dp(11));if(!in.latest.isEmpty()){TextView last=CortexUi.text(this,"Latest · "+proposalClip(in.latest,190),11,CortexUi.TEXT);last.setPadding(0,0,0,dp(5));intel.addView(last);}TextView next=CortexUi.text(this,"Next · "+in.next,11,in.openCount>0?CortexUi.TEXT:CortexUi.MUTED);intel.addView(next);card.addView(intel);}
        ArrayList<ContextRef> recent=r.candidate?new ArrayList<>():safeRecent(r);StringBuilder result=new StringBuilder();result.append(r.candidate?"Project candidate":"people".equals(mode)?"Relationship":"Project").append(": ").append(r.name);result.append("\nStatus: ").append(in.statusLine);if(!in.latest.isEmpty())result.append("\nLatest useful context: ").append(in.latest);result.append("\nBest next move: ").append(in.next);if(r.mentions>0)result.append("\nGrounded references: ").append(r.mentions);long sourceId=recent.isEmpty()?0:recent.get(0).itemId;
        LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);host.setPadding(dp(12),0,dp(10),dp(8));card.addView(host);ProposalUi.attach(this,db,host,new ResultProposalEngine.Target("Library","library_"+r.kind+"_"+r.id,r.name,result.toString(),sourceId,r.kind,false));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);if(feed.getChildCount()>0)cp.setMargins(0,dp(9),0,0);feed.addView(card,cp);
    }

    private Insight relationshipInsight(Row r){if(r.candidate)return new Insight(0,"Project candidate","Confirm whether this is a real project","");int open=0;String latest="";String q="%"+r.name+"%";Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT kind,title,body,updated_at FROM derived_items WHERE state IN ('open','pending') AND (title LIKE ? OR body LIKE ?) ORDER BY importance DESC,updated_at DESC LIMIT 5",new String[]{q,q});while(c.moveToNext()){open++;if(latest.isEmpty())latest=ppNz(c.getString(1))+(ppNz(c.getString(2)).isEmpty()?"":" — "+ppNz(c.getString(2)));}}catch(Throwable ignored){}finally{if(c!=null)c.close();}ArrayList<ContextRef> recent=safeRecent(r);if(latest.isEmpty()&&!recent.isEmpty()){ContextRef x=recent.get(0);latest=ppNz(x.title)+(ppNz(x.preview).isEmpty()?"":" — "+ppNz(x.preview));}String status=open>0?open+" live situation"+(open==1?"":"s"):(recent.isEmpty()?"No active grounded context":"Recent context available");String next=open>0?"Open the live situation and choose the next step":(recent.isEmpty()?"No action needed":"Review latest context only when it matters");return new Insight(open,status,next,latest);}
    static final class Insight{final int openCount;final String statusLine,next,latest;Insight(int o,String s,String n,String l){openCount=o;statusLine=s;next=n;latest=l;}}

    @Override void openEntityBrain(Row r,long focal){String subject=r.identified?"identified person":"confirmed project";CortexActionExecutor.openBrain(this,focal,"Focus on the "+subject+" ‘"+r.name+"’. Start with what is CURRENTLY OPEN, what changed most recently, what is waiting on them versus waiting on me, related projects/situations, and the single best next action. Use grounded Cortex evidence connected to this exact "+(r.identified?"person":"project")+". Do not infer facts from the name alone and do not lead with reference counts.");}
    private ArrayList<ContextRef> safeRecent(Row r){try{return recentContext(r);}catch(Throwable e){return new ArrayList<>();}}
    private static String proposalClip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
    private static String ppNz(String s){return s==null?"":s.trim();}
}
