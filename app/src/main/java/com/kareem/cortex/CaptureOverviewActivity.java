package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** User-facing observability for everything Cortex captures. */
public final class CaptureOverviewActivity extends Activity {
    VaultDb db;LinearLayout content;Handler ui=new Handler(Looper.getMainLooper());boolean destroyed=false;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());PhoneContextStore.ensure(db);build();}
    @Override protected void onResume(){super.onResume();refresh();ui.removeCallbacks(tick);ui.post(tick);}
    @Override protected void onPause(){ui.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);if(db!=null)try{db.close();}catch(Throwable ignored){}db=null;super.onDestroy();}
    final Runnable tick=new Runnable(){@Override public void run(){if(!destroyed&&!isFinishing()){refresh();ui.postDelayed(this,1500);}}};

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"capture",null);setContentView(root);CortexUi.fitSystemBars(this,root);refresh();
    }

    void refresh(){
        if(content==null||db==null||destroyed)return;content.removeAllViews();
        header();long startOfDay=startOfDay();long today=PhoneContextStore.countSince(db,startOfDay)+knowledgeCountSince(startOfDay,null);long apps=PhoneContextStore.distinctAppsSince(db,startOfDay);int pending=db.pendingCount(),failed=db.failedCount();PhoneContextStore.Event latest=PhoneContextStore.latest(db);

        boolean notifications=notificationListenerEnabled(),screen=CortexScreenAccessibilityService.enabled(this);boolean anyLive=notifications||screen;
        LinearLayout live=CortexUi.card(this,24);live.setPadding(dp(15),dp(15),dp(15),dp(14));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,"capture",anyLive?CortexUi.GREEN:CortexUi.ORANGE,true),new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(12),0,dp(8),0);top.addView(tx,xp);
        TextView lh=CortexUi.plain(this,"Live Capture",19,CortexUi.TEXT);CortexUi.medium(lh);tx.addView(lh);TextView ls=CortexUi.text(this,anyLive?"Cortex is receiving phone activity now.":"Capture sources need access before Cortex can observe the phone.",11,CortexUi.MUTED);ls.setPadding(0,dp(4),0,0);tx.addView(ls);
        top.addView(CortexUi.chip(this,anyLive?"● LIVE":"SETUP",anyLive?CortexUi.GREEN:CortexUi.ORANGE,true),new LinearLayout.LayoutParams(-2,dp(32)));live.addView(top);
        TextView metrics=CortexUi.plain(this,"Today  "+today+"   •   Apps  "+apps+"   •   Processing  "+pending+"   •   Failed  "+failed,10,CortexUi.MUTED);metrics.setPadding(0,dp(12),0,0);live.addView(metrics);
        TextView last=CortexUi.text(this,latest==null?"No live event captured yet.":"Last activity  "+age(latest.occurredAt)+"  •  "+latest.human(),11,CortexUi.TEXT);last.setPadding(0,dp(8),0,0);last.setMaxLines(3);live.addView(last);content.addView(live,margins(0,dp(8),0,0));

        content.addView(CortexUi.section(this,"Capture sources"));
        category("Notifications",notifications?"Live · Android notification listener enabled":"Access needed · notification listener is off","phone",notifications?CortexUi.GREEN:CortexUi.ORANGE,countPhoneKind(startOfDay,"notification_context"),()->showPhoneEvents("Notifications",new String[]{"notification_context"}));
        category("Screen",screen?"Live · Android accessibility service enabled":"Access needed · screen understanding is off","open",screen?CortexUi.GREEN:CortexUi.ORANGE,countScreenSince(startOfDay),()->showPhoneEvents("Screen",new String[]{"app_transition","window_context","interaction"}));
        category("Voice","Raw recordings and transcription pipeline","voice",CortexUi.RED,knowledgeCountSince(startOfDay,new String[]{"AUDIO"}),()->showKnowledge("Voice",new String[]{"AUDIO"}));
        category("Images","Photos and visual evidence","photo",CortexUi.GREEN,knowledgeCountSince(startOfDay,new String[]{"IMAGE","SCREENSHOT"}),()->showKnowledge("Images",new String[]{"IMAGE","SCREENSHOT"}));
        category("Files","Documents, text, tables and imported attachments","file",CortexUi.ORANGE,knowledgeCountSince(startOfDay,new String[]{"FILE","DOCUMENT","PDF","TEXT","TABLE"}),()->showKnowledge("Files",new String[]{"FILE","DOCUMENT","PDF","TEXT","TABLE"}));
        category("Calls & SMS","Captured when Android exposes them through enabled sources","phone",CortexUi.YELLOW,countCallMessageSince(startOfDay),()->showCallsMessages());

        content.addView(CortexUi.section(this,"Live activity"));
        ArrayList<PhoneContextStore.Event> recent=PhoneContextStore.recent(db,System.currentTimeMillis()-6L*60L*60L*1000L,16);
        if(recent.isEmpty()){TextView empty=CortexUi.text(this,"No phone-context events in the last 6 hours.",12,CortexUi.MUTED);empty.setPadding(dp(2),dp(4),0,dp(10));content.addView(empty);}else for(PhoneContextStore.Event e:recent)activityRow(e);
    }

    void header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(8),dp(2),dp(8));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);row.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView h=CortexUi.plain(this,"Capture",30,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);TextView s=CortexUi.text(this,"Proof of what Cortex is seeing, storing and processing.",11,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);titles.addView(s);TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});row.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));content.addView(row);}

    void category(String title,String status,String icon,int color,long count,Runnable open){LinearLayout card=CortexUi.card(this,18);card.setPadding(dp(12),dp(11),dp(11),dp(11));card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(11),0,dp(8),0);card.addView(tx,tp);TextView h=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView s=CortexUi.text(this,status,10,CortexUi.MUTED);s.setPadding(0,dp(4),0,0);tx.addView(s);TextView n=CortexUi.chip(this,String.valueOf(count),color,true);card.addView(n,new LinearLayout.LayoutParams(-2,dp(32)));CortexUi.pressable(this,card,CortexUi.velvet(this,18));card.setOnClickListener(v->open.run());content.addView(card,margins(0,0,0,dp(8)));}

    void activityRow(PhoneContextStore.Event e){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(3),dp(10),dp(3),dp(10));TextView t=CortexUi.text(this,e.human(),12,CortexUi.TEXT);t.setMaxLines(3);row.addView(t);TextView meta=CortexUi.plain(this,clock(e.occurredAt)+"  •  "+e.kind+"  •  STORED",9,CortexUi.MUTED);meta.setPadding(0,dp(4),0,0);row.addView(meta);row.setOnClickListener(v->showEventDetail(e));content.addView(row);content.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}

    void showPhoneEvents(String title,String[] kinds){ArrayList<PhoneContextStore.Event> all=PhoneContextStore.recent(db,0,200);StringBuilder b=new StringBuilder();for(PhoneContextStore.Event e:all){if(!contains(kinds,e.kind))continue;b.append(clock(e.occurredAt)).append("  •  ").append(e.human()).append("\n").append("State: CAPTURED → STORED").append("\n\n");if(b.length()>16000)break;}showText(title,b.length()==0?"No matching captures yet.":b.toString());}
    void showCallsMessages(){ArrayList<PhoneContextStore.Event> all=PhoneContextStore.recent(db,0,200);StringBuilder b=new StringBuilder();for(PhoneContextStore.Event e:all){if(!"notification_context".equals(e.kind))continue;String m=e.metadataJson==null?"":e.metadataJson;if(!(m.contains("\"notification_kind\":\"call\"")||m.contains("\"notification_kind\":\"message\"")))continue;b.append(clock(e.occurredAt)).append("  •  ").append(e.human()).append("\n\n");}showText("Calls & SMS",b.length()==0?"No call/message capture evidence yet. Direct call-log/SMS provider access is not currently enabled; this view only proves events Android exposed through active Cortex sources.":b.toString());}
    void showKnowledge(String title,String[] types){StringBuilder b=new StringBuilder();try{for(KnowledgeItem k:db.lexicalSearch("",250)){if(!contains(types,k.type))continue;b.append(clock(k.createdAt)).append("  •  ").append(k.title==null||k.title.isEmpty()?k.type:k.title).append("\nType: ").append(k.type).append("  •  State: ").append(k.status).append("\n");if(k.analysisError!=null&&!k.analysisError.isEmpty())b.append("Error: ").append(k.analysisError).append("\n");b.append('\n');if(b.length()>16000)break;}}catch(Throwable ignored){}showText(title,b.length()==0?"No matching captures yet.":b.toString());}
    void showEventDetail(PhoneContextStore.Event e){StringBuilder b=new StringBuilder();b.append("Captured: ").append(exact(e.occurredAt)).append("\nSource: ").append(e.source).append("\nKind: ").append(e.kind).append("\nApp: ").append(e.appLabel).append("\nPackage: ").append(e.packageName).append("\nEvent: ").append(e.eventType).append("\n\nVisible text:\n").append(e.text==null||e.text.isEmpty()?"<none exposed by Android>":e.text).append("\n\nRaw metadata:\n").append(pretty(e.metadataJson));showText("Raw Capture",b.toString());}
    void showText(String title,String text){final TextView t=CortexUi.text(this,text,12,CortexUi.TEXT);t.setTextIsSelectable(true);t.setPadding(dp(8),dp(4),dp(8),dp(4));ScrollView s=new ScrollView(this);s.addView(t);new AlertDialog.Builder(this).setTitle(title).setView(s).setPositiveButton("Close",null).show();}

    boolean notificationListenerEnabled(){try{String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");if(enabled==null)return false;ComponentName c=new ComponentName(this,NotificationCaptureService.class);return enabled.contains(c.flattenToString())||enabled.contains(c.flattenToShortString());}catch(Throwable ignored){return false;}}
    long countPhoneKind(long since,String kind){return scalar("SELECT COUNT(*) FROM phone_context_events WHERE occurred_at>=? AND kind=?",new String[]{String.valueOf(since),kind});}
    long countScreenSince(long since){return scalar("SELECT COUNT(*) FROM phone_context_events WHERE occurred_at>=? AND kind IN ('app_transition','window_context','interaction')",new String[]{String.valueOf(since)});}
    long countCallMessageSince(long since){return scalar("SELECT COUNT(*) FROM phone_context_events WHERE occurred_at>=? AND kind='notification_context' AND (metadata_json LIKE '%\"notification_kind\":\"call\"%' OR metadata_json LIKE '%\"notification_kind\":\"message\"%')",new String[]{String.valueOf(since)});}
    long knowledgeCountSince(long since,String[] types){if(types==null)return scalar("SELECT COUNT(*) FROM knowledge_items WHERE created_at>=?",new String[]{String.valueOf(since)});StringBuilder q=new StringBuilder("SELECT COUNT(*) FROM knowledge_items WHERE created_at>=? AND UPPER(type) IN (");String[] args=new String[types.length+1];args[0]=String.valueOf(since);for(int i=0;i<types.length;i++){if(i>0)q.append(',');q.append('?');args[i+1]=types[i].toUpperCase(Locale.ROOT);}q.append(')');return scalar(q.toString(),args);}
    long scalar(String sql,String[] args){Cursor c=null;try{c=db.getReadableDatabase().rawQuery(sql,args);return c.moveToFirst()?c.getLong(0):0;}catch(Throwable ignored){return 0;}finally{if(c!=null)c.close();}}
    long startOfDay(){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    boolean contains(String[] xs,String x){if(xs==null||x==null)return false;for(String s:xs)if(s.equalsIgnoreCase(x))return true;return false;}
    String clock(long ms){return new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(ms));}String exact(long ms){return new SimpleDateFormat("dd MMM yyyy • HH:mm:ss",Locale.getDefault()).format(new Date(ms));}String age(long ms){long d=Math.max(0,System.currentTimeMillis()-ms),s=d/1000;if(s<60)return s+" sec ago";long m=s/60;if(m<60)return m+" min ago";return(m/60)+" h ago";}String pretty(String json){try{return new JSONObject(json==null?"{}":json).toString(2);}catch(Throwable ignored){return json==null?"{}":json;}}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
