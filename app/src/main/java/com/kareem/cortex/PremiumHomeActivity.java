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

/** v50 Warm Intelligence home: bento overview, one primary Ask action, real status and calmer hierarchy. */
public class PremiumHomeActivity extends Activity {
    VaultDb db;
    LinearLayout content,metrics,recent;
    TextView hello,heroStatus,needsMetric,memoryMetric,visualMetric,waitingMetric,recentCount;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);FeatureStore.ensure(db);VisualInsightStore.ensure(db);try{TemporalResolver.backfill(db,1000);}catch(Exception ignored){}V41Maintenance.run(this,db);build();refresh();}
    @Override protected void onResume(){super.onResume();if(db!=null)refresh();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(15),dp(18),dp(24));sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);hello=CortexUi.plain(this,greeting(),12,CortexUi.MUTED);brand.addView(hello);TextView title=CortexUi.plain(this,"Cortex",30,CortexUi.TEXT);CortexUi.medium(title);title.setLetterSpacing(-.02f);brand.addView(title);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=CortexUi.plain(this,"•••",18,CortexUi.TEXT);more.setGravity(Gravity.CENTER);CortexUi.pressable(this,more,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER,17));more.setOnClickListener(this::showMore);top.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(top);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(19),dp(18),dp(19),dp(18));hero.setBackground(CortexUi.gradient(this,Color.rgb(58,36,25),Color.rgb(25,23,20),CortexUi.BORDER,26));hero.setElevation(dp(2));
        LinearLayout heroHead=new LinearLayout(this);heroHead.setOrientation(LinearLayout.HORIZONTAL);heroHead.setGravity(Gravity.CENTER_VERTICAL);TextView eyebrow=CortexUi.plain(this,"YOUR INTELLIGENCE",10,CortexUi.COPPER);CortexUi.medium(eyebrow);eyebrow.setLetterSpacing(.10f);heroHead.addView(eyebrow,new LinearLayout.LayoutParams(0,-2,1));heroStatus=CortexUi.chip(this,"SYNCED",CortexUi.SAGE,true);heroHead.addView(heroStatus,new LinearLayout.LayoutParams(-2,dp(28)));hero.addView(heroHead);
        TextView heroTitle=CortexUi.plain(this,"What do you need to know?",24,CortexUi.TEXT);CortexUi.medium(heroTitle);heroTitle.setPadding(0,dp(11),0,0);hero.addView(heroTitle);
        TextView heroBody=CortexUi.text(this,"Ask across memories, screenshots, voice notes, decisions and anything still waiting for you.",13,CortexUi.MUTED);heroBody.setPadding(0,dp(6),0,dp(15));hero.addView(heroBody);
        TextView ask=CortexUi.action(this,"✦   Ask Cortex",CortexUi.COPPER,true);ask.setGravity(Gravity.CENTER_VERTICAL);ask.setPadding(dp(16),0,dp(16),0);ask.setOnClickListener(v->startActivity(new Intent(this,AskCortexActivity.class)));hero.addView(ask,new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(18),0,0);content.addView(hero,hp);

        content.addView(CortexUi.section(this,"Today"));
        metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.VERTICAL);content.addView(metrics);
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);needsMetric=metric(row1,"Needs you","0",CortexUi.CORAL,0,()->openSmart("needs"));visualMetric=metric(row1,"Visual understood","0",CortexUi.COPPER,10,()->startActivity(new Intent(this,VisualIntelligenceActivity.class)));metrics.addView(row1,new LinearLayout.LayoutParams(-1,dp(132)));
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);row2.setPadding(0,dp(10),0,0);memoryMetric=metric(row2,"Memories","0",CortexUi.TEXT,0,()->startActivity(new Intent(this,VaultActivity.class)));waitingMetric=metric(row2,"Waiting","0",CortexUi.GOLD,10,()->openSmart("waiting"));metrics.addView(row2,new LinearLayout.LayoutParams(-1,dp(142)));

        LinearLayout capture=CortexUi.card(this,22);capture.setOrientation(LinearLayout.HORIZONTAL);capture.setGravity(Gravity.CENTER_VERTICAL);capture.setPadding(dp(16),dp(13),dp(13),dp(13));TextView capIcon=CortexUi.plain(this,"＋",25,CortexUi.COPPER);capIcon.setGravity(Gravity.CENTER);capIcon.setBackground(CortexUi.round(this,Color.rgb(52,39,30),Color.TRANSPARENT,16));capture.addView(capIcon,new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout capText=new LinearLayout(this);capText.setOrientation(LinearLayout.VERTICAL);capText.setPadding(dp(12),0,dp(8),0);TextView ct=CortexUi.plain(this,"Capture something",15,CortexUi.TEXT);CortexUi.medium(ct);capText.addView(ct);capText.addView(CortexUi.plain(this,"Voice, text, file or shared content",11,CortexUi.MUTED));capture.addView(capText,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=CortexUi.plain(this,"›",26,CortexUi.MUTED);arrow.setGravity(Gravity.CENTER);capture.addView(arrow,new LinearLayout.LayoutParams(dp(34),dp(46)));CortexUi.pressable(this,capture,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,22));capture.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(12),0,0);content.addView(capture,cp);

        LinearLayout recentHead=new LinearLayout(this);recentHead.setOrientation(LinearLayout.HORIZONTAL);recentHead.setGravity(Gravity.CENTER_VERTICAL);recentHead.setPadding(0,dp(22),0,dp(10));TextView rh=CortexUi.plain(this,"RECENT MEMORY",11,CortexUi.MUTED);CortexUi.medium(rh);rh.setLetterSpacing(.08f);recentHead.addView(rh,new LinearLayout.LayoutParams(0,-2,1));recentCount=CortexUi.plain(this,"",11,CortexUi.FAINT);recentHead.addView(recentCount);content.addView(recentHead);
        recent=new LinearLayout(this);recent.setOrientation(LinearLayout.VERTICAL);content.addView(recent);

        CortexUi.addBottomNav(this,root,"home",()->showMore(more));
        setContentView(root);
    }

    TextView metric(LinearLayout row,String label,String value,int color,int left,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(13));CortexUi.pressable(this,c,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,22));TextView l=CortexUi.plain(this,label,11,CortexUi.MUTED);c.addView(l);TextView n=CortexUi.plain(this,value,30,color);CortexUi.medium(n);n.setPadding(0,dp(4),0,0);c.addView(n);TextView hint=CortexUi.plain(this,"Tap to open  ›",9,CortexUi.FAINT);hint.setPadding(0,dp(5),0,0);c.addView(hint);c.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(c,p);return n;}

    void refresh(){
        hello.setText(greeting());int needs=FeatureStore.needsCount(db),mem=count("knowledge_items","1=1"),visual=VisualInsightStore.countDone(db),waiting=waitingCount();needsMetric.setText(String.valueOf(needs));memoryMetric.setText(String.valueOf(mem));visualMetric.setText(String.valueOf(visual));waitingMetric.setText(String.valueOf(waiting));heroStatus.setText(needs>0?needs+" NEEDS":"ALL CLEAR");heroStatus.setTextColor(needs>0?CortexUi.CORAL:CortexUi.SAGE);
        recent.removeAllViews();ArrayList<KnowledgeItem> xs=db.lexicalSearch("",7);recentCount.setText(mem+" total");if(xs.isEmpty()){LinearLayout e=CortexUi.card(this,22);e.setGravity(Gravity.CENTER);TextView t=CortexUi.text(this,"Capture your first memory and Cortex will start building context here.",13,CortexUi.MUTED);t.setGravity(Gravity.CENTER);t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);e.addView(t);recent.addView(e);return;}for(KnowledgeItem k:xs)addMemory(k);
    }

    void addMemory(KnowledgeItem k){LinearLayout c=CortexUi.card(this,20);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(12),dp(12),dp(14),dp(12));CortexUi.pressable(this,c,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,20));
        View visual=memoryVisual(k);c.addView(visual,new LinearLayout.LayoutParams(dp(62),dp(62)));LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,0,0);TextView t=CortexUi.text(this,cleanTitle(k.title),14,CortexUi.TEXT);CortexUi.medium(t);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(t);TextView meta=CortexUi.plain(this,friendly(k.type)+"  •  "+age(k.createdAt),10,CortexUi.MUTED);meta.setPadding(0,dp(5),0,0);words.addView(meta);String p=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);if(!empty(p)){TextView b=CortexUi.text(this,clip(p,105),11,CortexUi.MUTED);b.setMaxLines(2);b.setPadding(0,dp(4),0,0);words.addView(b);}c.addView(words,new LinearLayout.LayoutParams(0,-2,1));c.setOnClickListener(v->{Intent i=new Intent(this,VaultActivity.class);i.putExtra("item_id",k.id);startActivity(i);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(9));recent.addView(c,p);}

    View memoryVisual(KnowledgeItem k){if(isShot(k)&&k.attachmentPath!=null&&!k.attachmentPath.isEmpty()){try{File f=new File(k.attachmentPath);if(f.exists()){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=8;Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),o);if(b!=null){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setImageBitmap(b);im.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,16));im.setClipToOutline(true);return im;}}}catch(Exception ignored){}}
        TextView icon=CortexUi.plain(this,"AUDIO".equals(k.type)?"◉":isShot(k)?"▧":"◆",22,"AUDIO".equals(k.type)?CortexUi.GOLD:CortexUi.COPPER);icon.setGravity(Gravity.CENTER);icon.setBackground(CortexUi.round(this,Color.rgb(43,36,30),CortexUi.BORDER_SOFT,16));return icon;}

    int waitingCount(){LinkedHashSet<Long> ids=new LinkedHashSet<>();for(FeatureStore.InboxEntry e:FeatureStore.needs(db,400))if("Waiting".equals(e.bucket))ids.add(e.item.id);for(FeatureStore.InboxEntry e:FeatureStore.inbox(db,400))if("Waiting".equals(e.bucket))ids.add(e.item.id);return ids.size();}
    int count(String table,String where){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table+" WHERE "+where,null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    void openSmart(String mode){Intent i=new Intent(this,SmartInboxActivity.class);i.putExtra("mode",mode);startActivity(i);}void showMore(){showMore(null);}void showMore(View anchor){if(anchor!=null){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("Brain & context");p.getMenu().add("Environment & Local AI");p.getMenu().add("Full app status / audit");p.getMenu().add("All Cortex features");p.getMenu().add("ASR settings");p.setOnMenuItemClickListener(m->{String s=m.getTitle().toString();if(s.startsWith("Brain"))startActivity(new Intent(this,BrainActivity.class));else if(s.startsWith("Environment"))startActivity(new Intent(this,EnvironmentActivity.class));else if(s.startsWith("Full"))startActivity(new Intent(this,CortexAuditActivity.class));else if(s.startsWith("All"))startActivity(new Intent(this,FeatureHubActivity.class));else startActivity(new Intent(this,AsrSettingsActivity.class));return true;});p.show();}else{String[] xs={"Brain & context","Environment & Local AI","Full app status / audit","All Cortex features","ASR settings"};new AlertDialog.Builder(this).setItems(xs,(d,w)->{if(w==0)startActivity(new Intent(this,BrainActivity.class));else if(w==1)startActivity(new Intent(this,EnvironmentActivity.class));else if(w==2)startActivity(new Intent(this,CortexAuditActivity.class));else if(w==3)startActivity(new Intent(this,FeatureHubActivity.class));else startActivity(new Intent(this,AsrSettingsActivity.class));}).show();}}

    String greeting(){int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return h<12?"GOOD MORNING":h<18?"GOOD AFTERNOON":"GOOD EVENING";}boolean isShot(KnowledgeItem k){return"SCREENSHOT".equals(k.type)||"IMAGE".equals(k.type);}String cleanTitle(String s){if(s==null||s.trim().isEmpty())return"Memory";String x=s.trim();if(x.regionMatches(true,0,"Voice:",0,6))x=x.substring(6).trim();return x;}String friendly(String s){if("AUDIO".equals(s))return"Voice";if("IMAGE".equals(s)||"SCREENSHOT".equals(s))return"Image";return s==null?"Memory":s;}String clip(String s,int n){return s==null?"":s.length()<=n?s:s.substring(0,n)+"…";}String age(long ms){long m=Math.max(0,System.currentTimeMillis()-ms)/60000;if(m<1)return"now";if(m<60)return m+"m";long h=m/60;if(h<24)return h+"h";long d=h/24;return d<7?d+"d":new SimpleDateFormat("dd MMM",Locale.getDefault()).format(new Date(ms));}boolean empty(String s){return s==null||s.trim().isEmpty();}
}
