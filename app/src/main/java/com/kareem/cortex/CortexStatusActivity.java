package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Human-readable live dashboard. It intentionally explains work in plain language. */
public class CortexStatusActivity extends Activity {
    VaultDb db; LinearLayout box; Handler h=new Handler(Looper.getMainLooper());
    TextView headline,current,fast,deep,model,storage; ProgressBar fastBar,deepBar;
    int bg=Color.rgb(11,12,14),surface=Color.rgb(24,26,30),text=Color.rgb(245,244,240),muted=Color.rgb(156,159,168),accent=Color.rgb(232,177,72),ok=Color.rgb(120,205,150),warn=Color.rgb(238,184,94),border=Color.rgb(47,50,57);
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);} GradientDrawable round(int fill,int stroke,int r){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(r));g.setStroke(dp(1),stroke);return g;}
    TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setTextSize(sp);v.setTextColor(c);CortexTextUi.setPlain(v,s);return v;}
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new VaultDb(this);build();refresh();}
    @Override protected void onResume(){super.onResume();h.post(tick);}
    @Override protected void onPause(){super.onPause();h.removeCallbacks(tick);}
    Runnable tick=new Runnable(){public void run(){if(!isFinishing()){refresh();h.postDelayed(this,1200);}}};

    void build(){ScrollView sv=new ScrollView(this);box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(30));box.setBackgroundColor(bg);sv.addView(box);
        TextView title=tv("CORTEX STATUS",27,text);title.setTypeface(null,1);box.addView(title);TextView sub=tv("A simple view of what Cortex is doing right now.",13,muted);sub.setPadding(0,dp(4),0,dp(16));box.addView(sub);
        headline=card("RIGHT NOW");current=card("CURRENT ITEM");
        fast=card("QUICK SCREENSHOT READING");fastBar=bar();box.addView(fastBar,barLp());
        deep=card("DEEP OCR • 3 PASSES");deepBar=bar();box.addView(deepBar,barLp());
        model=card("LOCAL BRAIN");storage=card("LIBRARY");
        TextView note=tv("Quick reading makes a screenshot searchable first. Deep OCR later tries multiple image versions and keeps the best result. Heavy deep OCR waits for charging so it does not unnecessarily drain your phone.",12,muted);note.setPadding(dp(4),dp(8),dp(4),0);box.addView(note);setContentView(sv);}
    TextView card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(surface,border,20));TextView t=tv(title,11,accent);t.setTypeface(null,1);c.addView(t);TextView state=tv("",14,text);state.setPadding(0,dp(7),0,0);c.addView(state);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));box.addView(c,p);return state;}
    ProgressBar bar(){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(1000);return p;}LinearLayout.LayoutParams barLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(7));p.setMargins(dp(4),-dp(5),dp(4),dp(12));return p;}

    void refresh(){SQLiteDatabase s=db.getReadableDatabase();int total=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE')");int analyzed=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE') AND status='analyzed'");int queued=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE') AND status='queued'");int analyzing=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE') AND status='analyzing'");int failed=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE') AND status IN ('analysis_failed','failed_retryable')");boolean charging=charging();
        int deepDone=0,passRows=0;long latestDeepItem=0,latestDeepAt=0;if(table(s,"screenshot_ocr_passes")){passRows=count(s,"SELECT COUNT(*) FROM screenshot_ocr_passes");deepDone=count(s,"SELECT COUNT(DISTINCT item_id) FROM screenshot_ocr_passes");try(Cursor c=s.rawQuery("SELECT item_id,MAX(created_at) FROM screenshot_ocr_passes",null)){if(c.moveToFirst()){latestDeepItem=c.getLong(0);latestDeepAt=c.getLong(1);}}catch(Exception ignored){}}
        int deepLeft=Math.max(0,total-deepDone);String now;if(analyzing>0)now="Reading a new screenshot now.";else if(queued>0)now="New screenshots are waiting for quick reading.";else if(deepLeft>0&&!charging)now="Quick reading is caught up. Deep OCR is waiting for the charger.";else if(deepLeft>0)now="Deep OCR is improving older screenshots in the background.";else now="All screenshot reading is caught up.";headline.setText(now+"\n"+(charging?"Phone is charging.":"Phone is not charging."));headline.setTextColor((queued==0&&analyzing==0&&deepLeft==0)?ok:text);
        String cur="Nothing is being quick-read at this moment.";if(analyzing>0){String x=scalar(s,"SELECT title FROM knowledge_items WHERE source='screenshot-folder' AND status='analyzing' ORDER BY updated_at DESC LIMIT 1");cur="Quick OCR: "+clean(x);}else if(charging&&deepLeft>0&&latestDeepItem>0){String x=scalar(s,"SELECT title FROM knowledge_items WHERE id="+latestDeepItem);cur="Deep OCR is working through the library.\nMost recently improved: "+clean(x)+(latestDeepAt>0?"\nUpdated "+age(latestDeepAt):"");}else if(latestDeepItem>0){String x=scalar(s,"SELECT title FROM knowledge_items WHERE id="+latestDeepItem);cur="Most recent deep OCR: "+clean(x)+(latestDeepAt>0?"\nFinished "+age(latestDeepAt):"");}current.setText(cur);
        fast.setText(analyzed+" of "+total+" screenshots quick-read"+(queued>0?" • "+queued+" waiting":"")+(analyzing>0?" • "+analyzing+" being read":"")+(failed>0?" • "+failed+" need retry":"")+"\nThis is the first fast pass that makes screenshots searchable.");fastBar.setProgress(total==0?0:(int)Math.min(1000,(analyzed*1000L)/total));
        deep.setText(deepDone+" of "+total+" screenshots have deep OCR evidence\n"+deepLeft+" still to improve • "+passRows+" pass results saved\n"+(deepLeft==0?"Deep OCR complete.":(charging?"Allowed to run now because the phone is charging.":"Paused automatically until charging.")));deep.setTextColor(deepLeft==0?ok:text);deepBar.setProgress(total==0?0:(int)Math.min(1000,(deepDone*1000L)/total));
        LocalLlmRuntime.State rt=LocalLlmRuntime.state(this);model.setText(LocalModelManager.installed(this)?"Local Qwen is ready for reasoning.\nLast self-test: "+String.format(Locale.US,"%.2f tokens/sec",rt.tokensPerSecond):"Local Qwen is not ready yet." );model.setTextColor(LocalModelManager.installed(this)?ok:warn);
        int memories=count(s,"SELECT COUNT(*) FROM knowledge_items");int txt=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE COALESCE(extracted_text,'')<>''");storage.setText(memories+" memories saved • "+txt+" have extracted text\nNothing here means a file is being deleted; this page only reports processing progress.");
    }
    boolean charging(){Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i==null)return false;int st=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);return st==BatteryManager.BATTERY_STATUS_CHARGING||st==BatteryManager.BATTERY_STATUS_FULL;}
    boolean table(SQLiteDatabase s,String n){try(Cursor c=s.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",new String[]{n})){return c.moveToFirst();}catch(Exception e){return false;}}
    int count(SQLiteDatabase s,String q){try(Cursor c=s.rawQuery(q,null)){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return 0;}}
    String scalar(SQLiteDatabase s,String q){try(Cursor c=s.rawQuery(q,null)){return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";}catch(Exception e){return"";}}
    String clean(String x){if(x==null||x.trim().isEmpty())return"Screenshot";String s=x.trim();return s.length()>90?s.substring(0,90)+"…":s;}
    String age(long at){long d=Math.max(0,System.currentTimeMillis()-at);long sec=d/1000;if(sec<60)return"just now";long min=sec/60;if(min<60)return min+" min ago";return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(at));}
}
