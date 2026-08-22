package com.kareem.cortex;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SmartInboxActivity extends Activity {
    VaultDb db;LinearLayout feed,statsRow;String mode="needs";
    Button tabNeeds,tabInbox,tabWaiting,tabActions;
    int bg=Color.rgb(12,13,15),panel=Color.rgb(25,27,31),panel2=Color.rgb(31,33,38),text=Color.rgb(244,244,242),muted=Color.rgb(157,160,168),accent=Color.rgb(232,177,72),danger=Color.rgb(246,124,118),ok=Color.rgb(120,205,150),warn=Color.rgb(238,190,96),border=Color.rgb(48,50,56);
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}void pad(View v,int x){v.setPadding(dp(x),dp(x),dp(x),dp(x));}

    TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setTextSize(sp);v.setTextColor(c);v.setTextIsSelectable(true);CortexTextUi.setReadable(v,s);return v;}
    TextView plain(String s,int sp,int c){TextView v=new TextView(this);v.setTextSize(sp);v.setTextColor(c);CortexTextUi.setPlain(v,s);return v;}

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new VaultDb(this);FeatureStore.ensure(db);String m=getIntent().getStringExtra("mode");if(m!=null&&!m.isEmpty())mode=m;build();refresh();}
    @Override protected void onResume(){super.onResume();if(db!=null)refresh();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(16),dp(14),dp(16),0);
        TextView title=plain("SMART INBOX",27,text);title.setTypeface(null,1);root.addView(title);
        TextView sub=plain("Cortex decides what can wait — and what needs you.",14,muted);sub.setPadding(0,dp(2),0,dp(12));root.addView(sub);

        statsRow=new LinearLayout(this);statsRow.setOrientation(LinearLayout.HORIZONTAL);root.addView(statsRow,new LinearLayout.LayoutParams(-1,dp(36)));

        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);tabs.setPadding(0,dp(8),0,dp(8));
        tabNeeds=tab("NEEDS");tabInbox=tab("INBOX");tabWaiting=tab("WAITING");tabActions=tab("ACTIONS");
        addEq(tabs,tabNeeds,0);addEq(tabs,tabInbox,6);addEq(tabs,tabWaiting,6);addEq(tabs,tabActions,6);root.addView(tabs);

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);sv.addView(feed);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        tabNeeds.setOnClickListener(v->{mode="needs";refresh();});tabInbox.setOnClickListener(v->{mode="inbox";refresh();});tabWaiting.setOnClickListener(v->{mode="waiting";refresh();});tabActions.setOnClickListener(v->{mode="action";refresh();});
    }

    Button tab(String s){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setTextColor(muted);b.setAllCaps(false);b.setPadding(0,0,0,0);return b;}
    void addEq(LinearLayout row,View v,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(left),0,0,0);row.addView(v,p);}

    void refresh(){
        int needs=FeatureStore.needsCount(db),inbox=FeatureStore.inbox(db,500).size();
        statsRow.removeAllViews();addStatChip(needs+" Needs",needs>0?accent:muted);addStatChip(inbox+" Inbox",muted);
        styleTabs();feed.removeAllViews();ArrayList<FeatureStore.InboxEntry> xs=entries();
        if(xs.isEmpty()){emptyState();return;}
        if("needs".equals(mode)){
            ArrayList<FeatureStore.InboxEntry> urgent=new ArrayList<>(),high=new ArrayList<>(),review=new ArrayList<>();
            for(FeatureStore.InboxEntry e:xs){if(e.score>=100)urgent.add(e);else if(e.score>=70)high.add(e);else review.add(e);}
            addGroup("URGENT NOW",urgent,danger);addGroup("HIGH PRIORITY",high,warn);addGroup("NEEDS REVIEW",review,accent);
        }else{
            for(FeatureStore.InboxEntry e:xs)addEntry(e);
        }
    }

    void addStatChip(String s,int color){TextView c=chip(s,color,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));p.setMargins(0,0,dp(7),0);statsRow.addView(c,p);}
    void styleTabs(){Button[] bs={tabNeeds,tabInbox,tabWaiting,tabActions};String[] ms={"needs","inbox","waiting","action"};for(int i=0;i<bs.length;i++){boolean sel=ms[i].equals(mode);bs[i].setTextColor(sel?text:muted);bs[i].setBackground(round(sel?Color.rgb(41,35,24):panel,sel?accent:border,14,sel?1:0));}}
    void addGroup(String title,ArrayList<FeatureStore.InboxEntry> xs,int color){if(xs.isEmpty())return;TextView h=plain(title,11,color);h.setTypeface(null,1);h.setPadding(dp(3),dp(13),0,dp(8));feed.addView(h);for(FeatureStore.InboxEntry e:xs)addEntry(e);}

    ArrayList<FeatureStore.InboxEntry> entries(){
        if("needs".equals(mode))return FeatureStore.needs(db,150);
        if("inbox".equals(mode))return FeatureStore.inbox(db,150);
        String bucket="waiting".equals(mode)?"Waiting":"Action";LinkedHashMap<Long,FeatureStore.InboxEntry> map=new LinkedHashMap<>();
        for(FeatureStore.InboxEntry e:FeatureStore.needs(db,300))if(bucket.equals(e.bucket))map.put(e.item.id,e);
        for(FeatureStore.InboxEntry e:FeatureStore.inbox(db,300))if(bucket.equals(e.bucket))map.put(e.item.id,e);
        return new ArrayList<>(map.values());
    }

    void emptyState(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(20),dp(52),dp(20),dp(36));TextView icon=plain("✓",28,ok);icon.setGravity(Gravity.CENTER);box.addView(icon);TextView h=plain(emptyTitle(),18,text);h.setTypeface(null,1);h.setGravity(Gravity.CENTER);box.addView(h);TextView b=plain(emptyMessage(),13,muted);b.setGravity(Gravity.CENTER);b.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);b.setPadding(0,dp(6),0,0);box.addView(b);feed.addView(box);}
    String emptyTitle(){if("needs".equals(mode))return "Nothing needs you right now";if("waiting".equals(mode))return "Nothing waiting";if("action".equals(mode))return "No open actions";return "Inbox clear";}
    String emptyMessage(){if("needs".equals(mode))return "Cortex will surface anything urgent, due, waiting, or unresolved here.";if("waiting".equals(mode))return "Pending results and replies will appear here.";if("action".equals(mode))return "Detected tasks will appear here automatically.";return "New captures stay here until Cortex or you sorts them.";}

    void addEntry(FeatureStore.InboxEntry e){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackground(round(panel,border,22,1));c.setPadding(dp(16),dp(15),dp(16),dp(13));c.setClickable(true);c.setOnClickListener(v->detail(e));

        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.TOP);
        TextView t=tv(cleanTitle(e.item.title),17,text);t.setTypeface(null,1);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);head.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        if(e.score>0){TextView pri=chip(FeatureStore.priorityLabel(e.score),priorityColor(e.score),true);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,dp(28));pp.setMargins(dp(8),0,0,0);head.addView(pri,pp);}c.addView(head);

        TextView meta=plain(e.bucket+"  •  "+friendlyType(e.item.type)+"  •  "+age(e.item.createdAt),11,muted);meta.setPadding(0,dp(5),0,0);c.addView(meta);
        addReasonChips(c,e);

        ArrayList<String> acts=FeatureStore.openActions(db,e.item.id);
        String p=preview(e.item);int maxPreview=acts.isEmpty()?300:190;if(p.length()>maxPreview)p=p.substring(0,maxPreview)+"…";
        if(!empty(p)){
            TextView body=tv(p,14,text);body.setPadding(0,dp(11),0,0);c.addView(body);
        }
        if(!acts.isEmpty())addActionRows(c,acts);

        LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.HORIZONTAL);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(12),0,0);
        Button primary=actionButton(acts.isEmpty()?"CLEAR":"DONE",acts.isEmpty()?muted:ok);Button snooze=actionButton("SNOOZE",muted);Button pin=actionButton(e.pinned?"UNPIN":"PIN",e.pinned?accent:muted);Button more=actionButton("⋯",muted);
        addControl(footer,primary,0,1);addControl(footer,snooze,6,1);addControl(footer,pin,6,1);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(48),dp(48));mp.setMargins(dp(6),0,0,0);footer.addView(more,mp);c.addView(footer);

        primary.setOnClickListener(v->{if(acts.isEmpty())FeatureStore.dismissAttention(db,e.item.id);else FeatureStore.markDone(db,e.item.id);Toast.makeText(this,acts.isEmpty()?"Cleared":"Action closed",Toast.LENGTH_SHORT).show();refresh();});
        snooze.setOnClickListener(v->snooze(e));pin.setOnClickListener(v->{FeatureStore.pin(db,e.item.id,!e.pinned);refresh();});more.setOnClickListener(v->moreMenu(e));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(12));feed.addView(c,cp);
    }

    void addReasonChips(LinearLayout parent,FeatureStore.InboxEntry e){
        if(empty(e.reason)&&!e.pinned)return;HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        ArrayList<String> labels=new ArrayList<>();if(!empty(e.reason)){for(String x:e.reason.split("•")){String v=reasonLabel(x.trim());if(!empty(v)&&!labels.contains(v))labels.add(v);}}if(e.pinned&&!labels.contains("Pinned"))labels.add("Pinned");
        for(String x:labels){TextView ch=chip(x,chipColor(x),false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(28));p.setMargins(0,0,dp(6),0);row.addView(ch,p);}hsv.addView(row);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(34));hp.setMargins(0,dp(9),0,0);parent.addView(hsv,hp);
    }

    String reasonLabel(String x){String l=x.toLowerCase(Locale.US);if(l.contains("open action"))return x.replace("open action","action");if(l.contains("due date"))return "Due";if(l.contains("follow-up"))return "Follow-up";if(l.contains("pinned"))return "Pinned";if(l.contains("waiting"))return "Waiting";if(l.contains("failed"))return "Needs retry";return x;}
    int chipColor(String x){String l=x.toLowerCase(Locale.US);if(l.contains("due")||l.contains("follow"))return warn;if(l.contains("retry"))return danger;if(l.contains("pinned"))return accent;if(l.contains("action"))return ok;return muted;}

    void addActionRows(LinearLayout parent,ArrayList<String> acts){
        TextView h=plain("OPEN ACTIONS",11,accent);h.setTypeface(null,1);h.setPadding(0,dp(13),0,dp(4));parent.addView(h);
        for(String a:acts){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.TOP);TextView box=plain("□",18,accent);box.setGravity(Gravity.TOP);row.addView(box,new LinearLayout.LayoutParams(dp(28),dp(34)));TextView body=tv(a,13,text);body.setPadding(dp(2),0,0,dp(5));row.addView(body,new LinearLayout.LayoutParams(0,-2,1));parent.addView(row);}
    }

    Button actionButton(String s,int color){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setTextColor(color);b.setAllCaps(false);b.setPadding(0,0,0,0);b.setBackground(round(panel2,border,14,1));return b;}
    void addControl(LinearLayout row,View v,int left,int weight){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),weight);p.setMargins(dp(left),0,0,0);row.addView(v,p);}

    TextView chip(String s,int color,boolean strong){TextView v=plain(s,strong?10:11,color);v.setTypeface(null,strong?1:0);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setBackground(round(Color.argb(34,Color.red(color),Color.green(color),Color.blue(color)),Color.argb(110,Color.red(color),Color.green(color),Color.blue(color)),999,1));return v;}
    GradientDrawable round(int fill,int stroke,int radius,int strokeDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(strokeDp>0)g.setStroke(dp(strokeDp),stroke);return g;}

    void moreMenu(FeatureStore.InboxEntry e){ArrayList<String> xs=new ArrayList<>();xs.add("Detail");xs.add("Move bucket");if("failed_retryable".equals(e.item.status)||"analysis_failed".equals(e.item.status))xs.add("Re-analyze");xs.add("Clear from attention");String[] arr=xs.toArray(new String[0]);new AlertDialog.Builder(this).setTitle(cleanTitle(e.item.title)).setItems(arr,(d,w)->{String x=arr[w];if("Detail".equals(x))detail(e);else if("Move bucket".equals(x))chooseBucket(e);else if("Re-analyze".equals(x))reanalyze(e.item);else{FeatureStore.dismissAttention(db,e.item.id);refresh();}}).setNegativeButton("Cancel",null).show();}

    void snooze(FeatureStore.InboxEntry e){String[] x={"3 hours","Tomorrow","3 days","7 days"};new AlertDialog.Builder(this).setTitle("Snooze • "+cleanTitle(e.item.title)).setItems(x,(d,w)->{long now=System.currentTimeMillis(),until;if(w==0)until=now+3L*60*60*1000;else if(w==1)until=now+24L*60*60*1000;else if(w==2)until=now+3L*24*60*60*1000;else until=now+7L*24*60*60*1000;FeatureStore.snooze(db,e.item.id,until);Toast.makeText(this,"Snoozed until "+fmt(until),Toast.LENGTH_LONG).show();refresh();}).setNegativeButton("Cancel",null).show();}
    void chooseBucket(FeatureStore.InboxEntry e){String[] b={"Action","Waiting","Decision","Project","Person","Reference","Needs attention"};new AlertDialog.Builder(this).setTitle("Move to bucket").setItems(b,(d,w)->{FeatureStore.setBucket(db,e.item.id,b[w]);Toast.makeText(this,"Moved to "+b[w],Toast.LENGTH_SHORT).show();refresh();}).setNegativeButton("Cancel",null).show();}
    void reanalyze(KnowledgeItem k){FeatureStore.resetAttention(db,k.id);db.retry(k.id);Toast.makeText(this,"Re-analyzing…",Toast.LENGTH_SHORT).show();AnalysisQueue.kick(this,db,()->runOnUiThread(this::refresh));refresh();}

    void detail(FeatureStore.InboxEntry e){
        KnowledgeItem k=db.getById(e.item.id);if(k==null)return;ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(8),dp(18),dp(18));sv.addView(box);
        addReasonChips(box,e);
        ArrayList<String> acts=FeatureStore.openActions(db,k.id);if(!acts.isEmpty())addActionRows(box,acts);
        label(box,"WHAT CORTEX UNDERSTOOD");box.addView(tv(empty(e.reason)?e.bucket:e.reason,13,text));
        if(!empty(k.summary)){label(box,"SUMMARY");box.addView(tv(k.summary,14,text));}
        if(!empty(k.extractedText)){label(box,"TRANSCRIPT");String x=k.extractedText;if(x.length()>6000)x=x.substring(0,6000)+"…";box.addView(tv(x,14,text));}
        label(box,"STATE");box.addView(plain("Bucket: "+e.bucket+"\nPriority: "+FeatureStore.priorityLabel(e.score)+"\nStatus: "+k.status+"\nCaptured: "+fmt(k.createdAt),12,muted));
        new AlertDialog.Builder(this).setTitle(cleanTitle(k.title)).setView(sv).setNegativeButton("Close",null).show();
    }
    void label(LinearLayout p,String s){TextView h=plain(s,11,accent);h.setTypeface(null,1);h.setPadding(0,dp(15),0,dp(5));p.addView(h);}

    int priorityColor(int score){if(score>=100)return danger;if(score>=70)return warn;if(score>=45)return accent;return muted;}
    String preview(KnowledgeItem k){String p=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);return p==null?"":p.trim();}
    String cleanTitle(String s){if(s==null)return "Memory";String x=s.trim();if(x.regionMatches(true,0,"Voice:",0,6))x=x.substring(6).trim();return x;}
    String friendlyType(String s){if("AUDIO".equals(s))return "Voice";if("SCREENSHOT".equals(s)||"IMAGE".equals(s))return "Image";return s==null?"Memory":s;}
    String age(long ms){long d=Math.max(0,System.currentTimeMillis()-ms);long h=d/(60*60*1000);if(h<1)return "just now";if(h<24)return h+"h ago";long days=h/24;return days+"d ago";}
    String fmt(long ms){return new SimpleDateFormat("dd MMM • HH:mm",Locale.getDefault()).format(new Date(ms));}
    boolean empty(String s){return s==null||s.trim().isEmpty();}
}
