package com.kareem.cortex;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.*;

/** Approved Cortex Atlas skin over the canonical living-world model. */
public final class ProposalPeopleProjectsActivity extends PeopleProjectsActivity {
    enum CardVariant { INSIGHT, ACTIVE, QUIET, PROJECT }
    private SwipeRefreshLayout proposalSwipe;

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(10),dp(20),0);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(header(),new LinearLayout.LayoutParams(-1,dp(60)));
        TextView sub=CortexUi.text(this,"People and projects as Cortex understands them — current state, open loops and recent change.",11,CortexUi.MUTED);LinearLayout.LayoutParams shp=new LinearLayout.LayoutParams(-1,-2);shp.setMargins(0,0,0,dp(8));body.addView(sub,shp);

        tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);tabs.setGravity(Gravity.CENTER_VERTICAL);addSelector("People","people","person");addSelector("Projects","projects","project");LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(58));tp.setMargins(0,dp(4),0,dp(9));body.addView(tabs,tp);

        LinearLayout sb=new LinearLayout(this);sb.setOrientation(LinearLayout.HORIZONTAL);sb.setGravity(Gravity.CENTER_VERTICAL);sb.setPadding(dp(12),0,dp(9),0);sb.setBackground(CortexUi.round(this,CortexUi.BG,CortexUi.BORDER,20));sb.addView(CortexUi.glyph(this,"search",CortexUi.TEXT,false),new LinearLayout.LayoutParams(dp(36),dp(36)));
        search=new EditText(this);search.setHint("Search Atlas");search.setHintTextColor(CortexUi.MUTED);search.setTextColor(CortexUi.TEXT);search.setTextSize(14);search.setSingleLine(true);search.setIncludeFontPadding(false);search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(8),0,0,0);sb.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));CortexGlyphView filter=CortexUi.glyph(this,"filter",CortexUi.TEXT,false);sb.addView(filter,new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.setMargins(0,0,0,dp(9));body.addView(sb,sp);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render(lastRows,lastMode);}public void afterTextChanged(Editable e){}});

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);feed.setPadding(0,dp(1),0,dp(22));sv.addView(feed);
        proposalSwipe=new SwipeRefreshLayout(this);proposalSwipe.setColorSchemeColors(CortexUi.BRAND,CortexUi.BLUE,CortexUi.ORANGE);proposalSwipe.setProgressBackgroundColorSchemeColor(CortexUi.SURFACE);proposalSwipe.addView(sv,new android.view.ViewGroup.LayoutParams(-1,-1));proposalSwipe.setOnRefreshListener(()->{maintained=false;refreshAsync();});body.addView(proposalSwipe,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"atlas",null);setContentView(root);CortexUi.fitSystemBars(this,root);styleTabs();
    }

    @Override void render(ArrayList<Row> rows,String wanted){super.render(rows,wanted);if(proposalSwipe!=null)proposalSwipe.setRefreshing(false);}

    private View header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);CortexGlyphView mark=CortexUi.glyph(this,"brand",CortexUi.BRAND,false);row.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView word=CortexUi.plain(this,"C O R T E X",15,CortexUi.TEXT);CortexUi.medium(word);if(android.os.Build.VERSION.SDK_INT>=21)word.setLetterSpacing(.20f);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,dp(40),1);wp.setMargins(dp(8),0,0,0);row.addView(word,wp);CortexGlyphView menu=CortexUi.glyph(this,"menu",CortexUi.TEXT,false);menu.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});row.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));return row;}

    private void addSelector(String label,String key,String icon){LinearLayout tile=new LinearLayout(this);tile.setTag(key);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(8),0,dp(8),0);CortexGlyphView g=CortexUi.glyph(this,icon,CortexUi.MUTED,false);tile.addView(g,new LinearLayout.LayoutParams(dp(28),dp(28)));TextView t=CortexUi.plain(this,label,14,CortexUi.MUTED);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(dp(8),0,0,0);tile.addView(t,lp);tile.setOnClickListener(v->{mode=String.valueOf(v.getTag());styleTabs();if(search!=null)search.setText("");refreshAsync();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);if(tabs.getChildCount()>0)p.setMargins(dp(9),0,0,0);tabs.addView(tile,p);}

    @Override void styleTabs(){if(tabs==null)return;for(int i=0;i<tabs.getChildCount();i++){View raw=tabs.getChildAt(i);if(!(raw instanceof LinearLayout))continue;LinearLayout tile=(LinearLayout)raw;boolean on=mode.equals(String.valueOf(tile.getTag()));tile.setBackground(on?CortexUi.round(this,Color.argb(38,137,217,74),Color.argb(105,137,217,74),20):CortexUi.round(this,CortexUi.BG,CortexUi.BORDER,20));for(int j=0;j<tile.getChildCount();j++){View v=tile.getChildAt(j);if(v instanceof CortexGlyphView)((CortexGlyphView)v).setAccent(on?CortexUi.BRAND:CortexUi.MUTED);if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor(on?CortexUi.TEXT:CortexUi.MUTED);if(on)CortexUi.medium(t);}}}}

    @Override void addRow(Row r){
        CardVariant variant=r.candidate?CardVariant.ACTIVE:(!r.liveKind.isEmpty()?CardVariant.INSIGHT:("projects".equals(mode)?CardVariant.PROJECT:CardVariant.QUIET));
        int indicator=indicatorColor(variant);String icon="people".equals(mode)?"person":"project";
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,16));card.setPadding(dp(16),dp(16),dp(16),dp(16));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.TOP);View stripe=new View(this);stripe.setBackground(CortexUi.round(this,indicator,Color.TRANSPARENT,2));LinearLayout.LayoutParams sr=new LinearLayout.LayoutParams(dp(4),dp(64));sr.setMargins(0,0,dp(12),0);row.addView(stripe,sr);
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.VERTICAL);row.addView(inner,new LinearLayout.LayoutParams(0,-2,1));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(avatarView(r,icon,indicator),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.setMargins(dp(12),0,dp(8),0);top.addView(names,np);TextView name=CortexUi.cardTitle(this,r.name);name.setMaxLines(2);names.addView(name);
        String subtitle;if(r.candidate)subtitle="Needs confirmation";else if(!r.liveKind.isEmpty())subtitle=r.liveKind+" · current situation";else if(!r.latestContext.isEmpty())subtitle="No open loop · recent grounded context";else subtitle="No active situation";TextView refs=CortexUi.caption(this,subtitle);refs.setPadding(0,dp(4),0,0);names.addView(refs);
        CortexGlyphView arrow=CortexUi.glyph(this,"arrow",variant==CardVariant.INSIGHT?CortexUi.TEXT:CortexUi.MUTED,false);arrow.setBackground(CortexUi.round(this,Color.argb(8,245,247,241),CortexUi.BORDER,999));top.addView(arrow,new LinearLayout.LayoutParams(dp(40),dp(40)));top.setOnClickListener(v->detail(r));arrow.setOnClickListener(v->{if(variant==CardVariant.INSIGHT)openEntityBrain(r);else detail(r);});inner.addView(top);
        if(variant==CardVariant.INSIGHT){LinearLayout badge=insightBadge();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(26));bp.setMargins(0,dp(10),0,0);inner.addView(badge,bp);String current=!r.liveTitle.isEmpty()?r.liveTitle:r.liveBody;TextView action=CortexUi.text(this,proposalClip(current,180),18,CortexUi.BRAND);CortexUi.medium(action);action.setPadding(0,dp(8),dp(8),0);action.setMaxLines(3);inner.addView(action);}else if(!r.latestContext.isEmpty()){TextView quiet=CortexUi.caption(this,proposalClip(r.latestContext,160));quiet.setPadding(0,dp(8),0,0);inner.addView(quiet);}
        card.addView(row,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);if(feed.getChildCount()>0)cp.setMargins(0,dp(6),0,0);feed.addView(card,cp);
    }

    private int indicatorColor(CardVariant variant){switch(variant){case INSIGHT:return CortexUi.BRAND;case ACTIVE:return CortexUi.YELLOW;case PROJECT:return CortexUi.PURPLE;default:return CortexUi.MUTED;}}
    private LinearLayout insightBadge(){LinearLayout badge=new LinearLayout(this);badge.setGravity(Gravity.CENTER_VERTICAL);badge.setPadding(dp(10),0,dp(10),0);badge.setBackground(CortexUi.round(this,Color.argb(38,137,217,74),Color.TRANSPARENT,20));View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.BRAND,Color.TRANSPARENT,999));badge.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView text=CortexUi.plain(this,"Current Cortex situation",10,CortexUi.BRAND);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMargins(dp(6),0,0,0);badge.addView(text,tp);return badge;}
    private View avatarView(Row r,String icon,int dot){LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER);box.setBackground(CortexUi.round(this,CortexUi.BG,CortexUi.BORDER,14));String initials=initials(r.name);if(!initials.isEmpty()){TextView t=CortexUi.plain(this,initials,14,CortexUi.TEXT);CortexUi.medium(t);t.setGravity(Gravity.CENTER);box.addView(t,new LinearLayout.LayoutParams(-1,-1));}else box.addView(CortexUi.glyph(this,icon,CortexUi.TEXT,false),new LinearLayout.LayoutParams(dp(32),dp(32)));return box;}
    private String initials(String name){if(name==null)return"";String[] p=name.trim().split("\\s+");StringBuilder s=new StringBuilder();for(String x:p){if(x.isEmpty())continue;char c=x.charAt(0);if(Character.isLetterOrDigit(c)){s.append(Character.toUpperCase(c));if(s.length()==2)break;}}return s.toString();}
    @Override void openEntityBrain(Row r){String subject=r.identified?"identified person":"confirmed project";CortexActionExecutor.openBrain(this,r.latestMemoryId,"Focus on the "+subject+" ‘"+r.name+"’. Start with what is CURRENTLY OPEN, what changed most recently, what is waiting on them versus waiting on me, related projects/situations, and the single best next action. Use grounded Cortex evidence connected to this exact "+(r.identified?"person":"project")+". Do not infer facts from the name alone and do not lead with reference counts.");}
    private static String proposalClip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
