package com.kareem.cortex.rebuild;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Fresh Cortex product shell. Evidence stays underneath until cognition gives it meaning. */
public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(8,10,8),SURFACE=Color.rgb(24,28,24),SURFACE2=Color.rgb(31,36,31),BORDER=Color.rgb(55,62,55);
    private static final int TEXT=Color.rgb(244,246,242),MUTED=Color.rgb(164,171,163),FAINT=Color.rgb(112,120,112),BRAND=Color.rgb(143,226,67),BLUE=Color.rgb(75,158,255),AMBER=Color.rgb(238,174,60);
    private CortexDb db; private LinearLayout page; private SwipeRefreshLayout swipe; private String currentTab="now";

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);db=new CortexDb(this);build();currentTab=tabFrom(getIntent(),"now");render(currentTab);BrainIntakeQueue.recoverPending(this,(id,result,error)->render(currentTab));}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String tab=tabFrom(intent,currentTab);render(tab);}
    @Override protected void onResume(){super.onResume();if(page!=null&&db!=null){BrainIntakeQueue.recoverPending(this,(id,result,error)->render(currentTab));render(currentTab);}}
    @Override protected void onDestroy(){try{if(db!=null)db.close();}catch(Throwable ignored){}super.onDestroy();}

    private String tabFrom(Intent intent,String fallback){String t=intent==null?null:intent.getStringExtra("tab");if("now".equals(t)||"memory".equals(t)||"world".equals(t)||"ask".equals(t))return t;return fallback;}

    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(18),dp(22),dp(32));scroll.addView(page,new ScrollView.LayoutParams(-1,-2));swipe=new SwipeRefreshLayout(this);swipe.setColorSchemeColors(BRAND,BLUE);swipe.setProgressBackgroundColorSchemeColor(SURFACE);swipe.addView(scroll,new ViewGroup.LayoutParams(-1,-1));swipe.setOnRefreshListener(()->{BrainIntakeQueue.recoverPending(this,(id,result,error)->render(currentTab));render(currentTab);swipe.setRefreshing(false);});root.addView(swipe,new LinearLayout.LayoutParams(-1,0,1));root.addView(bottomNav(),new LinearLayout.LayoutParams(-1,dp(88)));setContentView(root);}

    private void render(String tab){currentTab=tab;if(page==null)return;page.removeAllViews();header(tab);switch(tab){case"memory":renderMemory();break;case"world":renderWorld();break;case"ask":renderAsk();break;default:renderNow();}}

    private void header(String tab){LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView mark=text("C",18,BRAND,true);mark.setGravity(Gravity.CENTER);mark.setBackground(round(Color.TRANSPARENT,BRAND,14,2));top.addView(mark,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView brand=text("C  O  R  T  E  X",16,TEXT,true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.setMargins(dp(14),0,0,0);top.addView(brand,bp);page.addView(top);String label=tab.equals("now")?"NOW":tab.equals("memory")?"MEMORY":tab.equals("world")?"WORLD":"ASK";TextView context=text(label,11,FAINT,true);context.setLetterSpacing(.18f);context.setPadding(0,dp(22),0,dp(4));page.addView(context);}

    private void renderNow(){title("What matters now");paragraph("A current-state view. Relay evidence and deliberate captures stay underneath; only situations the Cortex brain creates can surface here.");relaySensorCard();List<CortexDb.Row> situations=db.activeSituations(12);if(situations.isEmpty()){LinearLayout clear=card();clear.addView(text("Clear horizon",20,TEXT,true));TextView b=paragraphView("Nothing currently requires your attention. Incoming evidence can accumulate safely without turning itself into tasks or priorities.");b.setPadding(0,dp(8),0,0);clear.addView(b);TextView rule=text("0 CURRENT SITUATIONS",10,BRAND,true);rule.setLetterSpacing(.12f);rule.setPadding(0,dp(14),0,0);clear.addView(rule);page.addView(clear,margins(0,dp(12),0,0));return;}section("CURRENT SITUATIONS",BRAND);for(CortexDb.Row row:situations){LinearLayout c=card();c.addView(text(row.title,17,TEXT,true));if(!row.body.isEmpty()){TextView body=paragraphView(row.body);body.setPadding(0,dp(6),0,0);c.addView(body);}TextView state=text(row.type.toUpperCase(Locale.ROOT),10,BRAND,true);state.setPadding(0,dp(10),0,0);c.addView(state);page.addView(c,margins(0,dp(10),0,0));}}

    private void relaySensorCard(){CortexDb.RelayStats stats=db.relayStats();LinearLayout c=card();LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.addView(text("●",14,stats.total>0?BRAND:AMBER,true));TextView heading=text(stats.total>0?"Relay sensor connected":"Relay sensor waiting",15,TEXT,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(dp(10),0,0,0);line.addView(heading,hp);c.addView(line);String body;if(stats.total<=0)body="No authenticated Relay evidence has reached this fresh Cortex yet. Notifications remain a Cortex Relay responsibility.";else body=stats.today+" Relay evidence event"+(stats.today==1?"":"s")+" received today"+(stats.lastReceivedAt>0?" · last "+format(stats.lastReceivedAt):"")+". Raw notifications are not Now cards.";TextView b=paragraphView(body);b.setPadding(0,dp(7),0,0);c.addView(b);long manual=db.manualEvidenceCount();TextView local=text(manual+" LOCAL CAPTURE"+(manual==1?"":"S")+" · VOICE / PHOTO / FILE / TEXT",9,manual>0?BRAND:FAINT,true);local.setPadding(0,dp(10),0,0);c.addView(local);if(stats.total>0){String detail=(stats.lastProtocol.isEmpty()?"RELAY":stats.lastProtocol)+(stats.lastSourcePackage.isEmpty()?"":" · "+stats.lastSourcePackage);TextView meta=text(detail,9,FAINT,true);meta.setPadding(0,dp(6),0,0);c.addView(meta);}page.addView(c,margins(0,dp(16),0,0));}

    private void renderMemory(){
        title("Memory");
        paragraph("Durable things Cortex explicitly chose to keep. Capture processing, retry state and evidence-only items stay outside Memory.");
        List<CortexDb.Row> memories=db.recentMemories(50);
        if(!memories.isEmpty()){
            section("DURABLE MEMORY",BRAND);
            for(CortexDb.Row row:memories){LinearLayout c=card();c.addView(text(row.title,16,TEXT,true));if(!row.body.equals(row.title)){TextView body=paragraphView(row.body);body.setPadding(0,dp(6),0,0);c.addView(body);}TextView meta=text(format(row.updatedAt),10,FAINT,false);meta.setPadding(0,dp(10),0,0);c.addView(meta);page.addView(c,margins(0,dp(10),0,0));}
        } else {
            emptyCard("No durable memories yet","Deliberate captures remain evidence until Cortex explicitly decides they are worth keeping as durable memory.");
        }
    }

    private void renderWorld(){title("World");paragraph("People, projects and other entities appear only after Cortex has enough grounded evidence to maintain a real model of them.");List<CortexDb.Row> entities=db.worldEntities(50);if(entities.isEmpty()){emptyCard("No world model yet","No phone numbers, contact dumps or accidental notification fragments are promoted into people or projects.");return;}section("KNOWN ENTITIES",BLUE);for(CortexDb.Row row:entities){LinearLayout c=card();c.addView(text(row.title,17,TEXT,true));TextView kind=text(row.type,10,BLUE,true);kind.setPadding(0,dp(5),0,0);c.addView(kind);if(!row.body.isEmpty()){TextView body=paragraphView(row.body);body.setPadding(0,dp(8),0,0);c.addView(body);}page.addView(c,margins(0,dp(10),0,0));}}

    private void renderAsk(){title("Ask Cortex");paragraph("Grounded recall across deliberate memory and underlying evidence. Voice transcripts are searchable after the ASR quality gate accepts them.");LinearLayout box=card();EditText input=new EditText(this);input.setHint("Ask about something Cortex has evidence for…");input.setHintTextColor(FAINT);input.setTextColor(TEXT);input.setTextSize(16);input.setSingleLine(false);input.setMinLines(2);input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setBackground(round(SURFACE2,BORDER,14,1));box.addView(input,new LinearLayout.LayoutParams(-1,-2));TextView ask=action("Search grounded context");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(48));ap.setMargins(0,dp(10),0,0);box.addView(ask,ap);page.addView(box,margins(0,dp(18),0,0));LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);page.addView(results,margins(0,dp(10),0,0));ask.setOnClickListener(v->{results.removeAllViews();String q=input.getText().toString().trim();if(q.isEmpty()){results.addView(messageCard("Ask a specific question","Cortex needs a query before it can look for grounded context."));return;}List<CortexDb.Row> matches=db.searchGrounded(q,5);if(matches.isEmpty()){results.addView(messageCard("I don't have grounded evidence for that yet","Nothing in fresh Cortex memory or evidence matches this query, so no answer was fabricated."));return;}results.addView(messageCard("Grounded context found",matches.size()+" supporting item"+(matches.size()==1?"":"s")+"."));for(CortexDb.Row row:matches){LinearLayout c=card();c.addView(text(row.title,15,TEXT,true));TextView b=paragraphView(row.body);b.setPadding(0,dp(5),0,0);c.addView(b);TextView source=text(row.type,9,row.type.equals("EVIDENCE")?BLUE:BRAND,true);source.setPadding(0,dp(8),0,0);c.addView(source);results.addView(c,margins(0,dp(8),0,0));}});}

    private View bottomNav(){LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(10),dp(8),dp(10),dp(10));bar.setBackgroundColor(BG);bar.addView(nav("Now","now"),new LinearLayout.LayoutParams(0,-1,1));bar.addView(nav("Memory","memory"),new LinearLayout.LayoutParams(0,-1,1));TextView plus=text("+",34,BG,false);plus.setGravity(Gravity.CENTER);plus.setBackground(round(BRAND,Color.TRANSPARENT,999,0));plus.setOnClickListener(v->startActivity(new Intent(this,CaptureActivity.class)));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(66),dp(66));pp.setMargins(dp(7),0,dp(7),0);bar.addView(plus,pp);bar.addView(nav("World","world"),new LinearLayout.LayoutParams(0,-1,1));bar.addView(nav("Ask","ask"),new LinearLayout.LayoutParams(0,-1,1));return bar;}
    private View nav(String label,String tab){TextView t=text(label,12,MUTED,false);t.setGravity(Gravity.CENTER);t.setOnClickListener(v->render(tab));return t;}

    private void title(String value){TextView t=text(value,30,TEXT,true);t.setPadding(0,dp(10),0,0);page.addView(t);}private void paragraph(String value){TextView t=paragraphView(value);t.setPadding(0,dp(8),0,0);page.addView(t);}private TextView paragraphView(String value){TextView t=text(value,14,MUTED,false);t.setLineSpacing(0,1.18f);return t;}private void section(String value,int color){TextView t=text(value,11,color,true);t.setLetterSpacing(.12f);t.setPadding(0,dp(24),0,dp(5));page.addView(t);}private void emptyCard(String heading,String body){LinearLayout c=card();c.addView(text(heading,19,TEXT,true));TextView b=paragraphView(body);b.setPadding(0,dp(8),0,0);c.addView(b);page.addView(c,margins(0,dp(18),0,0));}private View messageCard(String heading,String body){LinearLayout c=card();c.addView(text(heading,16,TEXT,true));TextView b=paragraphView(body);b.setPadding(0,dp(6),0,0);c.addView(b);return c;}private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(17),dp(18),dp(17));c.setBackground(round(SURFACE,BORDER,20,1));return c;}private TextView action(String label){TextView t=text(label,14,BG,true);t.setGravity(Gravity.CENTER);t.setBackground(round(BRAND,Color.TRANSPARENT,14,0));return t;}private TextView text(String value,int sp,int color,boolean medium){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(sp);if(medium)t.setTypeface(Typeface.create("sans",Typeface.BOLD));return t;}private GradientDrawable round(int fill,int stroke,int radius,int strokeWidth){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(strokeWidth>0)d.setStroke(dp(strokeWidth),stroke);return d;}private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(l,t,r,b);return p;}private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}private static String format(long time){return new SimpleDateFormat("dd MMM · h:mm a",Locale.getDefault()).format(new Date(time));}
}
