package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SmartInboxActivity extends Activity {
    VaultDb db;LinearLayout feed;TextView status;String mode="needs";
    int bg=Color.rgb(16,17,20),panel=Color.rgb(24,26,31),text=Color.rgb(243,244,246),muted=Color.rgb(165,168,176),accent=Color.rgb(143,169,255),danger=Color.rgb(255,139,139),ok=Color.rgb(143,220,170),warn=Color.rgb(255,211,120);
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}void pad(View v,int x){v.setPadding(dp(x),dp(x),dp(x),dp(x));}
    TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setTextIsSelectable(true);return v;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(9);b.setTextColor(text);b.setBackgroundColor(panel);return b;}

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new VaultDb(this);FeatureStore.ensure(db);String m=getIntent().getStringExtra("mode");if(m!=null&&!m.isEmpty())mode=m;build();refresh();}
    @Override protected void onResume(){super.onResume();if(db!=null)refresh();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);pad(root,16);
        TextView title=tv("SMART INBOX",27,text);title.setTypeface(null,1);root.addView(title);root.addView(tv("Cortex decides what can wait — and what needs you.",14,muted));
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);tabs.setPadding(0,dp(13),0,0);Button needs=button("NEEDS"),inbox=button("INBOX"),waiting=button("WAITING"),actions=button("ACTIONS");addEq(tabs,needs,0);addEq(tabs,inbox,6);addEq(tabs,waiting,6);addEq(tabs,actions,6);root.addView(tabs);
        status=tv("",12,muted);status.setPadding(0,dp(10),0,dp(8));root.addView(status);
        ScrollView sv=new ScrollView(this);feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);sv.addView(feed);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        needs.setOnClickListener(v->{mode="needs";refresh();});inbox.setOnClickListener(v->{mode="inbox";refresh();});waiting.setOnClickListener(v->{mode="waiting";refresh();});actions.setOnClickListener(v->{mode="action";refresh();});
    }
    void addEq(LinearLayout row,View v,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(left),0,0,0);row.addView(v,p);}

    void refresh(){
        int needs=FeatureStore.needsCount(db),inbox=FeatureStore.inbox(db,500).size();status.setText(needs+" need attention  •  "+inbox+" in inbox  •  "+mode.toUpperCase(Locale.US));feed.removeAllViews();
        ArrayList<FeatureStore.InboxEntry> xs=entries();
        if(xs.isEmpty()){TextView e=tv(emptyMessage(),14,muted);e.setPadding(0,dp(22),0,0);feed.addView(e);return;}
        if("needs".equals(mode)){TextView h=tv("PRIORITIZED BY CORTEX",11,accent);h.setTypeface(null,1);h.setPadding(0,dp(8),0,dp(7));feed.addView(h);}
        for(FeatureStore.InboxEntry e:xs)addEntry(e);
    }

    ArrayList<FeatureStore.InboxEntry> entries(){
        if("needs".equals(mode))return FeatureStore.needs(db,150);
        if("inbox".equals(mode))return FeatureStore.inbox(db,150);
        String bucket="waiting".equals(mode)?"Waiting":"Action";LinkedHashMap<Long,FeatureStore.InboxEntry> map=new LinkedHashMap<>();
        for(FeatureStore.InboxEntry e:FeatureStore.needs(db,300))if(bucket.equals(e.bucket))map.put(e.item.id,e);
        for(FeatureStore.InboxEntry e:FeatureStore.inbox(db,300))if(bucket.equals(e.bucket))map.put(e.item.id,e);
        return new ArrayList<>(map.values());
    }
    String emptyMessage(){if("needs".equals(mode))return "Nothing needs your attention right now.";if("waiting".equals(mode))return "No waiting items detected.";if("action".equals(mode))return "No active action items detected.";return "Inbox clear. New captures will appear here until reviewed.";}

    void addEntry(FeatureStore.InboxEntry e){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackgroundResource(R.drawable.rounded_panel);pad(c,12);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);TextView t=tv((e.pinned?"★ ":"")+e.item.title,15,text);t.setTypeface(null,1);head.addView(t,new LinearLayout.LayoutParams(0,-2,1));if(e.score>0){TextView pri=tv(FeatureStore.priorityLabel(e.score),10,priorityColor(e.score));pri.setTypeface(null,1);pri.setGravity(Gravity.END);head.addView(pri);}c.addView(head);
        c.addView(tv(e.bucket+"  •  "+e.item.category+"  •  "+age(e.item.createdAt),11,muted));
        if(!empty(e.reason)){TextView why=tv(e.reason,12,e.score>=70?warn:accent);why.setPadding(0,dp(5),0,0);c.addView(why);}
        String p=preview(e.item);if(p.length()>360)p=p.substring(0,360)+"…";TextView body=tv(LanguageBlockFormatter.format(p),13,text);body.setPadding(0,dp(7),0,0);c.addView(body);
        ArrayList<String> acts=FeatureStore.openActions(db,e.item.id);if(!acts.isEmpty()){TextView a=tv("OPEN ACTIONS\n"+bullets(acts),12,warn);a.setPadding(0,dp(8),0,0);c.addView(a);}
        LinearLayout r1=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);Button primary=button(acts.isEmpty()?"CLEAR":"DONE"),snooze=button("SNOOZE"),pin=button(e.pinned?"UNPIN":"PIN");addControl(r1,primary,0);addControl(r1,snooze,5);addControl(r1,pin,5);c.addView(r1);
        LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);Button bucket=button("BUCKET"),detail=button("DETAIL");addControl(r2,bucket,0);addControl(r2,detail,5);if("failed_retryable".equals(e.item.status)||"analysis_failed".equals(e.item.status)){Button retry=button("RE-ANALYZE");addControl(r2,retry,5);retry.setOnClickListener(v->reanalyze(e.item));}c.addView(r2);
        primary.setOnClickListener(v->{if(acts.isEmpty())FeatureStore.dismissAttention(db,e.item.id);else FeatureStore.markDone(db,e.item.id);Toast.makeText(this,acts.isEmpty()?"Cleared":"Action closed",Toast.LENGTH_SHORT).show();refresh();});
        snooze.setOnClickListener(v->snooze(e));pin.setOnClickListener(v->{FeatureStore.pin(db,e.item.id,!e.pinned);refresh();});bucket.setOnClickListener(v->chooseBucket(e));detail.setOnClickListener(v->detail(e));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));feed.addView(c,cp);
    }
    void addControl(LinearLayout row,View v,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);p.setMargins(dp(left),dp(8),0,0);row.addView(v,p);}

    void snooze(FeatureStore.InboxEntry e){String[] x={"3 hours","Tomorrow","3 days","7 days"};new AlertDialog.Builder(this).setTitle("Snooze • "+e.item.title).setItems(x,(d,w)->{long now=System.currentTimeMillis(),until;if(w==0)until=now+3L*60*60*1000;else if(w==1)until=now+24L*60*60*1000;else if(w==2)until=now+3L*24*60*60*1000;else until=now+7L*24*60*60*1000;FeatureStore.snooze(db,e.item.id,until);Toast.makeText(this,"Snoozed until "+fmt(until),Toast.LENGTH_LONG).show();refresh();}).setNegativeButton("Cancel",null).show();}
    void chooseBucket(FeatureStore.InboxEntry e){String[] b={"Action","Waiting","Decision","Project","Person","Reference","Needs attention"};new AlertDialog.Builder(this).setTitle("Move to bucket").setItems(b,(d,w)->{FeatureStore.setBucket(db,e.item.id,b[w]);Toast.makeText(this,"Moved to "+b[w],Toast.LENGTH_SHORT).show();refresh();}).setNegativeButton("Cancel",null).show();}

    void reanalyze(KnowledgeItem k){FeatureStore.resetAttention(db,k.id);db.retry(k.id);Toast.makeText(this,"Re-analyzing…",Toast.LENGTH_SHORT).show();AnalysisQueue.kick(this,db,()->runOnUiThread(this::refresh));refresh();}

    void detail(FeatureStore.InboxEntry e){
        KnowledgeItem k=db.getById(e.item.id);if(k==null)return;ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,15);sv.addView(box);
        label(box,"WHY THIS IS HERE");box.addView(tv(empty(e.reason)?e.bucket:e.reason,13,e.score>=70?warn:text));
        label(box,"SUMMARY");box.addView(tv(LanguageBlockFormatter.format(empty(k.summary)?"No summary":k.summary),14,text));
        if(!empty(k.extractedText)){label(box,"EXTRACTED / TRANSCRIPT");String x=k.extractedText;if(x.length()>6000)x=x.substring(0,6000)+"…";box.addView(tv(LanguageBlockFormatter.format(x),13,text));}
        ArrayList<String> acts=FeatureStore.openActions(db,k.id);if(!acts.isEmpty()){label(box,"OPEN ACTIONS");box.addView(tv(bullets(acts),13,warn));}
        label(box,"STATE");box.addView(tv("Bucket: "+e.bucket+"\nPriority: "+FeatureStore.priorityLabel(e.score)+"\nStatus: "+k.status+"\nCaptured: "+fmt(k.createdAt),12,muted));
        new AlertDialog.Builder(this).setTitle(k.title).setView(sv).setNegativeButton("Close",null).show();
    }
    void label(LinearLayout p,String s){TextView h=tv(s,11,accent);h.setTypeface(null,1);h.setPadding(0,dp(13),0,dp(4));p.addView(h);}

    int priorityColor(int score){if(score>=100)return danger;if(score>=70)return warn;if(score>=45)return accent;return muted;}
    String preview(KnowledgeItem k){String p=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);return p==null?"":p;}
    String bullets(ArrayList<String> xs){StringBuilder s=new StringBuilder();for(String x:xs){if(s.length()>0)s.append('\n');s.append("• ").append(x);}return s.toString();}
    String age(long ms){long d=Math.max(0,System.currentTimeMillis()-ms);long h=d/(60*60*1000);if(h<1)return "just now";if(h<24)return h+"h ago";long days=h/24;return days+"d ago";}
    String fmt(long ms){return new SimpleDateFormat("dd MMM • HH:mm",Locale.getDefault()).format(new Date(ms));}
    boolean empty(String s){return s==null||s.trim().isEmpty();}
}
