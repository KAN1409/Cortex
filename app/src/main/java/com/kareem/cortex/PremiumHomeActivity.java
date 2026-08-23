package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Home is a concise briefing, not a second Vault. */
public class PremiumHomeActivity extends Activity {
    VaultDb db;LinearLayout recent,signals;TextView needsValue,actionsValue;volatile int refreshGeneration=0;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);FeatureStore.ensure(db);build();refreshAsync();new Thread(()->{try{TemporalResolver.backfill(db,250);V41Maintenance.run(this,db);}catch(Throwable ignored){}},"CortexMaintenance").start();}
    @Override protected void onResume(){super.onResume();if(db!=null)refreshAsync();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(14),dp(20),dp(24));sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView brand=CortexUi.plain(this,"Cortex",31,CortexUi.TEXT);CortexUi.medium(brand);head.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->open(SettingsActivity.class));head.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));content.addView(head);

        LinearLayout ask=CortexUi.card(this,26);ask.setPadding(dp(20),dp(20),dp(20),dp(18));TextView title=CortexUi.plain(this,"What do you need to know?",25,CortexUi.TEXT);CortexUi.medium(title);ask.addView(title);TextView button=CortexUi.action(this,"Ask Cortex",CortexUi.ACCENT,true);button.setOnClickListener(v->open(AskCortexActivity.class));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.setMargins(0,dp(16),0,0);ask.addView(button,bp);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.setMargins(0,dp(20),0,0);content.addView(ask,ap);

        signals=new LinearLayout(this);signals.setOrientation(LinearLayout.VERTICAL);signals.addView(CortexUi.section(this,"Needs attention"));needsValue=signalRow(signals,"Needs attention",()->openFocus("needs"));actionsValue=signalRow(signals,"Open actions",()->openFocus("action"));content.addView(signals);

        content.addView(CortexUi.section(this,"Recent"));recent=new LinearLayout(this);recent.setOrientation(LinearLayout.VERTICAL);content.addView(recent);

        CortexUi.addBottomNav(this,root,"home",null);setContentView(root);
    }

    TextView signalRow(LinearLayout parent,String label,Runnable action){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(12),dp(2),dp(12));CortexUi.pressable(this,row,CortexUi.round(this,Color.TRANSPARENT,Color.TRANSPARENT,12));TextView labelView=CortexUi.plain(this,label,14,CortexUi.TEXT);CortexUi.medium(labelView);row.addView(labelView,new LinearLayout.LayoutParams(0,-2,1));TextView value=CortexUi.plain(this,"0",14,CortexUi.ACCENT);CortexUi.medium(value);row.addView(value);TextView arrow=CortexUi.plain(this,"›",22,CortexUi.MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(34)));row.setOnClickListener(v->action.run());parent.addView(row);parent.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));return value;}

    void refreshAsync(){final int generation=++refreshGeneration;new Thread(()->{try{ArrayList<FeatureStore.InboxEntry> needs=FeatureStore.needs(db,80);int actions=visibleActionCount();ArrayList<KnowledgeItem> xs=db.lexicalSearch("",3);runOnUiThread(()->{if(generation!=refreshGeneration||isFinishing())return;needsValue.setText(String.valueOf(needs.size()));actionsValue.setText(String.valueOf(actions));signals.setVisibility(needs.isEmpty()&&actions==0?View.GONE:View.VISIBLE);renderRecent(xs);});}catch(Throwable ignored){}},"CortexHomeRefresh").start();}

    void renderRecent(ArrayList<KnowledgeItem> xs){recent.removeAllViews();if(xs.isEmpty()){TextView t=CortexUi.text(this,"Nothing saved yet.",13,CortexUi.MUTED);t.setPadding(dp(2),dp(10),dp(2),dp(10));recent.addView(t);return;}for(KnowledgeItem k:xs)addRecent(k);}
    void addRecent(KnowledgeItem k){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(12),dp(2),dp(12));CortexUi.pressable(this,row,CortexUi.round(this,Color.TRANSPARENT,Color.TRANSPARENT,12));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView title=CortexUi.text(this,cleanTitle(k.title),14,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title);TextView meta=CortexUi.plain(this,friendly(k.type)+"  •  "+age(k.createdAt),10,CortexUi.MUTED);meta.setPadding(0,dp(3),0,0);text.addView(meta);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=CortexUi.plain(this,"›",22,CortexUi.MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(38)));row.setOnClickListener(v->{Intent i=new Intent(this,VaultActivity.class);i.putExtra("item_id",k.id);startActivity(i);});recent.addView(row);recent.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}

    int visibleActionCount(){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM actions a JOIN knowledge_items k ON k.id=a.item_id LEFT JOIN smart_inbox si ON si.item_id=k.id WHERE a.status='open' AND (k.type NOT IN ('SCREENSHOT','IMAGE') OR COALESCE(si.manual_bucket,0)=1)",null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    void openFocus(String mode){Intent i=new Intent(this,SmartInboxActivity.class);i.putExtra("mode",mode);startActivity(i);}void open(Class<?> c){Intent i=new Intent(this,c);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}
    String cleanTitle(String s){if(empty(s))return"Memory";String x=s.trim();if(x.regionMatches(true,0,"Voice:",0,6))x=x.substring(6).trim();return x;}String friendly(String s){if("AUDIO".equals(s))return"Voice";if("IMAGE".equals(s)||"SCREENSHOT".equals(s))return"Image";if("FILE".equals(s))return"File";return"Memory";}String age(long ms){long m=Math.max(0,System.currentTimeMillis()-ms)/60000;if(m<1)return"now";if(m<60)return m+"m";long h=m/60;if(h<24)return h+"h";long d=h/24;return d<7?d+"d":new SimpleDateFormat("dd MMM",Locale.getDefault()).format(new Date(ms));}boolean empty(String s){return s==null||s.trim().isEmpty();}
}
