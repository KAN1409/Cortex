package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Cortex home: one primary Ask action, one capture action and a very small number of real signals. */
public class PremiumHomeActivity extends Activity {
    VaultDb db;LinearLayout recent;TextView needsMetric,actionMetric,memoryCount,heroStatus;volatile int refreshGeneration=0;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);FeatureStore.ensure(db);VisualInsightStore.ensure(db);build();refreshAsync();new Thread(()->{try{TemporalResolver.backfill(db,250);V41Maintenance.run(this,db);}catch(Throwable ignored){}},"CortexMaintenance").start();}
    @Override protected void onResume(){super.onResume();if(db!=null)refreshAsync();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(14),dp(20),dp(24));sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView brand=CortexUi.plain(this,"Cortex",31,CortexUi.TEXT);CortexUi.medium(brand);titles.addView(brand);titles.addView(CortexUi.plain(this,"Private memory, organized around what matters",11,CortexUi.MUTED));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView settings=CortexUi.chip(this,"SETTINGS",CortexUi.MUTED,false);settings.setOnClickListener(v->open(SettingsActivity.class));head.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));content.addView(head);

        LinearLayout hero=CortexUi.card(this,28);hero.setPadding(dp(20),dp(20),dp(20),dp(20));hero.setBackground(CortexUi.gradient(this,Color.rgb(22,26,36),Color.rgb(15,17,22),CortexUi.BORDER,28));
        LinearLayout heroTop=new LinearLayout(this);heroTop.setOrientation(LinearLayout.HORIZONTAL);heroTop.setGravity(Gravity.CENTER_VERTICAL);TextView eyebrow=CortexUi.plain(this,"PRIVATE SECOND BRAIN",10,CortexUi.ACCENT);CortexUi.medium(eyebrow);eyebrow.setLetterSpacing(.11f);heroTop.addView(eyebrow,new LinearLayout.LayoutParams(0,-2,1));heroStatus=CortexUi.chip(this,"READY",CortexUi.SAGE,true);heroTop.addView(heroStatus,new LinearLayout.LayoutParams(-2,dp(28)));hero.addView(heroTop);
        TextView ht=CortexUi.plain(this,"Ask anything you've saved.",27,CortexUi.TEXT);CortexUi.medium(ht);ht.setPadding(0,dp(12),0,0);hero.addView(ht);
        TextView hb=CortexUi.text(this,"Cortex searches your own notes, screenshots, voice transcripts and decisions first.",13,CortexUi.MUTED);hb.setPadding(0,dp(7),0,dp(17));hero.addView(hb);
        TextView ask=CortexUi.action(this,"Ask Cortex",CortexUi.ACCENT,true);ask.setGravity(Gravity.CENTER);ask.setOnClickListener(v->open(AskCortexActivity.class));hero.addView(ask,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(20),0,0);content.addView(hero,hp);

        content.addView(CortexUi.section(this,"At a glance"));LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);needsMetric=metric(stats,"Needs attention","—",0,()->openFocus("needs"));actionMetric=metric(stats,"Open actions","—",10,()->openFocus("action"));content.addView(stats,new LinearLayout.LayoutParams(-1,dp(108)));

        LinearLayout capture=CortexUi.card(this,23);capture.setOrientation(LinearLayout.HORIZONTAL);capture.setGravity(Gravity.CENTER_VERTICAL);capture.setPadding(dp(16),dp(14),dp(14),dp(14));CortexUi.pressable(this,capture,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,23));
        TextView plus=CortexUi.plain(this,"+",28,CortexUi.ACCENT);plus.setGravity(Gravity.CENTER);plus.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,16));capture.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout cc=new LinearLayout(this);cc.setOrientation(LinearLayout.VERTICAL);cc.setPadding(dp(13),0,0,0);TextView ct=CortexUi.plain(this,"Capture",16,CortexUi.TEXT);CortexUi.medium(ct);cc.addView(ct);cc.addView(CortexUi.plain(this,"Voice note, quick note or recording",11,CortexUi.MUTED));capture.addView(cc,new LinearLayout.LayoutParams(0,-2,1));TextView go=CortexUi.plain(this,"›",27,CortexUi.MUTED);go.setGravity(Gravity.CENTER);capture.addView(go,new LinearLayout.LayoutParams(dp(34),dp(48)));capture.setOnClickListener(v->open(CaptureActivity.class));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(14),0,0);content.addView(capture,cp);

        LinearLayout rh=new LinearLayout(this);rh.setOrientation(LinearLayout.HORIZONTAL);rh.setGravity(Gravity.CENTER_VERTICAL);rh.setPadding(0,dp(23),0,dp(10));TextView rl=CortexUi.plain(this,"RECENT MEMORY",11,CortexUi.MUTED);CortexUi.medium(rl);rl.setLetterSpacing(.09f);rh.addView(rl,new LinearLayout.LayoutParams(0,-2,1));memoryCount=CortexUi.plain(this,"",11,CortexUi.FAINT);rh.addView(memoryCount);content.addView(rh);recent=new LinearLayout(this);recent.setOrientation(LinearLayout.VERTICAL);content.addView(recent);

        CortexUi.addBottomNav(this,root,"home",null);setContentView(root);
    }

    TextView metric(LinearLayout row,String label,String value,int left,Runnable open){LinearLayout c=CortexUi.card(this,21);c.setPadding(dp(15),dp(14),dp(15),dp(12));CortexUi.pressable(this,c,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,21));TextView n=CortexUi.plain(this,value,28,CortexUi.TEXT);CortexUi.medium(n);c.addView(n);TextView l=CortexUi.plain(this,label,11,CortexUi.MUTED);l.setPadding(0,dp(4),0,0);c.addView(l);c.setOnClickListener(v->open.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(c,p);return n;}

    void refreshAsync(){final int generation=++refreshGeneration;new Thread(()->{try{ArrayList<FeatureStore.InboxEntry> needs=FeatureStore.needs(db,80);int actions=visibleActionCount(),mem=count("knowledge_items","1=1");ArrayList<KnowledgeItem> xs=db.lexicalSearch("",6);runOnUiThread(()->{if(generation!=refreshGeneration||isFinishing())return;needsMetric.setText(String.valueOf(needs.size()));actionMetric.setText(String.valueOf(actions));memoryCount.setText(mem+" memories");heroStatus.setText(needs.isEmpty()?"CLEAR":needs.size()+" NEED");heroStatus.setTextColor(needs.isEmpty()?CortexUi.SAGE:CortexUi.ACCENT);renderRecent(xs);});}catch(Throwable ignored){}},"CortexHomeRefresh").start();}

    void renderRecent(ArrayList<KnowledgeItem> xs){recent.removeAllViews();if(xs.isEmpty()){LinearLayout e=CortexUi.card(this,21);TextView t=CortexUi.text(this,"Nothing saved yet. Capture something and it will appear here.",13,CortexUi.MUTED);t.setGravity(Gravity.CENTER);e.addView(t);recent.addView(e);return;}for(KnowledgeItem k:xs)addMemory(k);}
    void addMemory(KnowledgeItem k){LinearLayout c=CortexUi.card(this,20);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(12),dp(12),dp(13),dp(12));CortexUi.pressable(this,c,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,20));View media=memoryVisual(k);c.addView(media,new LinearLayout.LayoutParams(dp(56),dp(56)));LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,0,0);TextView t=CortexUi.text(this,cleanTitle(k.title),14,CortexUi.TEXT);CortexUi.medium(t);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(t);TextView meta=CortexUi.plain(this,friendly(k.type)+"  •  "+age(k.createdAt),10,CortexUi.MUTED);meta.setPadding(0,dp(4),0,0);words.addView(meta);String preview=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);if(!empty(preview)){TextView b=CortexUi.text(this,clip(preview,95),11,CortexUi.MUTED);b.setMaxLines(2);b.setPadding(0,dp(4),0,0);words.addView(b);}c.addView(words,new LinearLayout.LayoutParams(0,-2,1));c.setOnClickListener(v->{Intent i=new Intent(this,VaultActivity.class);i.putExtra("item_id",k.id);startActivity(i);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(9));recent.addView(c,p);}
    View memoryVisual(KnowledgeItem k){if(isShot(k)&&!empty(k.attachmentPath)){try{File f=new File(k.attachmentPath);if(f.exists()){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=8;Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),o);if(b!=null){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setImageBitmap(b);im.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,15));im.setClipToOutline(true);return im;}}}catch(Throwable ignored){}}TextView icon=CortexUi.plain(this,"AUDIO".equals(k.type)?"V":isShot(k)?"I":"M",13,CortexUi.ACCENT);CortexUi.medium(icon);icon.setGravity(Gravity.CENTER);icon.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,15));return icon;}

    int visibleActionCount(){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM actions a JOIN knowledge_items k ON k.id=a.item_id LEFT JOIN smart_inbox si ON si.item_id=k.id WHERE a.status='open' AND (k.type NOT IN ('SCREENSHOT','IMAGE') OR COALESCE(si.manual_bucket,0)=1)",null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    int count(String table,String where){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table+" WHERE "+where,null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    void openFocus(String mode){Intent i=new Intent(this,SmartInboxActivity.class);i.putExtra("mode",mode);startActivity(i);}void open(Class<?> c){Intent i=new Intent(this,c);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}
    boolean isShot(KnowledgeItem k){return"SCREENSHOT".equals(k.type)||"IMAGE".equals(k.type);}String cleanTitle(String s){if(empty(s))return"Memory";String x=s.trim();if(x.regionMatches(true,0,"Voice:",0,6))x=x.substring(6).trim();return x;}String friendly(String s){if("AUDIO".equals(s))return"Voice";if("IMAGE".equals(s)||"SCREENSHOT".equals(s))return"Image";if("FILE".equals(s))return"File";return"Memory";}String clip(String s,int n){return s==null?"":s.length()<=n?s:s.substring(0,n)+"…";}String age(long ms){long m=Math.max(0,System.currentTimeMillis()-ms)/60000;if(m<1)return"now";if(m<60)return m+"m";long h=m/60;if(h<24)return h+"h";long d=h/24;return d<7?d+"d":new SimpleDateFormat("dd MMM",Locale.getDefault()).format(new Date(ms));}boolean empty(String s){return s==null||s.trim().isEmpty();}
}
