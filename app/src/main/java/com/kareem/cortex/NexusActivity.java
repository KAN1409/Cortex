package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** NEXUS product surface migrated into Cortex. */
public final class NexusActivity extends Activity {
    VaultDb db;LinearLayout body;volatile boolean destroyed=false;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);NexusEngine.ensure(db.getWritableDatabase());buildShell();render();refreshAsync();}
    @Override protected void onResume(){super.onResume();if(db!=null)refreshAsync();}
    @Override protected void onDestroy(){destroyed=true;try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"now",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void render(){
        if(destroyed||db==null)return;body.removeAllViews();
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout hbox=new LinearLayout(this);hbox.setOrientation(LinearLayout.VERTICAL);TextView title=CortexUi.plain(this,"NEXUS",28,CortexUi.TEXT);CortexUi.medium(title);hbox.addView(title);hbox.addView(CortexUi.text(this,"Inside Cortex · Observe → Remember → Model interests → Discover → Prepare action.",11,CortexUi.MUTED));head.addView(hbox,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        ArrayList<NexusEngine.Interest> interests=NexusEngine.interests(db,12);ArrayList<NexusEngine.Discovery> discoveries=NexusEngine.discoveries(db,8);ArrayList<NexusEngine.PreparedAction> actions=NexusEngine.actions(db,12);
        int observations=NexusEngine.observationCount(db),ready=NexusEngine.readyActionCount(db);
        LinearLayout stats=CortexUi.card(this,20);stats.setPadding(dp(14),dp(13),dp(14),dp(13));stats.addView(CortexUi.text(this,observations+" observations  •  "+interests.size()+" interests  •  "+discoveries.size()+" discoveries  •  "+ready+" ready",12,CortexUi.TEXT));body.addView(stats,margin(10));
        shortcuts();

        body.addView(CortexUi.section(this,"What Cortex is learning"));
        if(interests.isEmpty())body.addView(CortexUi.text(this,"NEXUS is waiting for enough grounded Cortex context to model interests.",12,CortexUi.MUTED),margin(6));
        for(NexusEngine.Interest x:interests)interestCard(x);

        body.addView(CortexUi.section(this,"Prepared actions"));
        if(actions.isEmpty())body.addView(CortexUi.text(this,"Nothing is waiting for approval. Cortex will prepare actions only when existing grounded intelligence justifies one.",12,CortexUi.MUTED),margin(6));
        for(NexusEngine.PreparedAction x:actions)actionCard(x);

        body.addView(CortexUi.section(this,"For you"));
        if(discoveries.isEmpty())body.addView(CortexUi.text(this,"Nothing noisy here. NEXUS will surface a pattern only when it has repeated grounded support.",12,CortexUi.MUTED),margin(6));
        for(NexusEngine.Discovery x:discoveries)discoveryCard(x);

        renderActivity();
        renderObservedContext();
    }

    void shortcuts(){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(8),0,0);
        shortcut(row,"Memory",VaultActivity.class);shortcut(row,"Knowledge",KnowledgeExplorerActivity.class);shortcut(row,"Access",PhoneContextAccessActivity.class);body.addView(row);
    }
    void shortcut(LinearLayout row,String label,Class<?> target){TextView v=CortexUi.action(this,label,CortexUi.MUTED,false);v.setGravity(Gravity.CENTER);v.setOnClickListener(x->{try{startActivity(new Intent(this,target));}catch(Throwable ignored){}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(0,0,dp(6),0);row.addView(v,p);}

    void interestCard(NexusEngine.Interest x){
        LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(14),dp(12),dp(14),dp(12));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView t=CortexUi.text(this,x.label,14,CortexUi.TEXT);CortexUi.medium(t);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));row.addView(CortexUi.chip(this,Math.round(x.affinity*100)+"%",CortexUi.LIME,true),new LinearLayout.LayoutParams(-2,dp(30)));c.addView(row);
        c.addView(CortexUi.plain(this,x.evidenceCount+" grounded signals  •  momentum "+Math.round(x.momentum*100)+"%  •  confidence "+Math.round(x.confidence*100)+"%",10,CortexUi.MUTED));body.addView(c,margin(7));
    }

    void discoveryCard(NexusEngine.Discovery x){
        LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(14),dp(12),dp(14),dp(12));TextView label=CortexUi.plain(this,x.type,9,CortexUi.LIME);CortexUi.medium(label);c.addView(label);TextView t=CortexUi.text(this,x.title,15,CortexUi.TEXT);CortexUi.medium(t);t.setPadding(0,dp(4),0,0);c.addView(t);c.addView(CortexUi.text(this,x.summary,12,CortexUi.MUTED));c.addView(CortexUi.plain(this,"Why this: "+x.whyThis+"  •  score "+Math.round(x.score*100)+"%",10,CortexUi.MUTED));
        TextView dismiss=CortexUi.action(this,"Dismiss",CortexUi.MUTED,false);dismiss.setOnClickListener(v->{NexusEngine.dismissDiscovery(db,x.id);render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.setMargins(0,dp(7),0,0);c.addView(dismiss,p);body.addView(c,margin(7));
    }

    void actionCard(NexusEngine.PreparedAction x){
        LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(14),dp(12),dp(14),dp(12));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView t=CortexUi.text(this,x.title,15,CortexUi.TEXT);CortexUi.medium(t);top.addView(t,new LinearLayout.LayoutParams(0,-2,1));top.addView(CortexUi.chip(this,friendlyState(x.state),stateColor(x.state),true),new LinearLayout.LayoutParams(-2,dp(30)));c.addView(top);if(!x.body.isEmpty()){TextView b=CortexUi.text(this,x.body,12,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);c.addView(b);}c.addView(CortexUi.plain(this,x.kind+"  •  "+Math.round(x.confidence*100)+"%  •  importance "+x.importance+(x.source.isEmpty()?"":"  •  "+x.source),10,CortexUi.MUTED));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(8),0,0);
        if("READY_FOR_APPROVAL".equals(x.state)){
            addAction(actions,"Approve",()->NexusEngine.approve(db,x.derivedId),true);addAction(actions,"Later",()->NexusEngine.defer(db,x.derivedId),false);addAction(actions,"Dismiss",()->NexusEngine.reject(db,x.derivedId),false);
        }else if("APPROVED".equals(x.state))addAction(actions,"Start",()->NexusEngine.start(db,x.derivedId),true);
        else if("EXECUTING".equals(x.state)){addAction(actions,"Complete",()->NexusEngine.complete(db,x.derivedId),true);addAction(actions,"Failed",()->NexusEngine.fail(db,x.derivedId),false);}
        else if("DRAFT".equals(x.state))addAction(actions,"Approve now",()->NexusEngine.approve(db,x.derivedId),false);
        if(actions.getChildCount()>0)c.addView(actions);body.addView(c,margin(7));
    }

    void renderActivity(){
        body.addView(CortexUi.section(this,"Recent NEXUS activity"));
        Cursor c=db.getReadableDatabase().rawQuery("SELECT f.event_type,COALESCE(d.title,'Cortex action'),COALESCE(d.body,''),f.created_at FROM feedback_events f LEFT JOIN derived_items d ON d.id=f.target_id WHERE f.policy_version=? ORDER BY f.created_at DESC LIMIT 16",new String[]{NexusEngine.VERSION});
        int n=0;while(c.moveToNext()){
            String event=c.getString(0),title=c.getString(1),detail=c.getString(2);long at=c.getLong(3);LinearLayout card=CortexUi.card(this,16);card.setPadding(dp(12),dp(10),dp(12),dp(10));TextView k=CortexUi.plain(this,event.replace('_',' '),9,CortexUi.LIME);CortexUi.medium(k);card.addView(k);TextView t=CortexUi.text(this,title,13,CortexUi.TEXT);CortexUi.medium(t);card.addView(t);if(detail!=null&&!detail.trim().isEmpty())card.addView(CortexUi.text(this,clip(detail,160),11,CortexUi.MUTED));card.addView(CortexUi.plain(this,friendlyAge(at),9,CortexUi.MUTED));body.addView(card,margin(6));n++;}
        c.close();if(n==0)body.addView(CortexUi.text(this,"No approval or action decisions yet. They will appear here without duplicating Cortex evidence.",12,CortexUi.MUTED),margin(6));
    }

    void renderObservedContext(){
        body.addView(CortexUi.section(this,"Recent observed context"));
        Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(source,''),COALESCE(title,''),COALESCE(body,''),occurred_at FROM raw_signals WHERE state<>'suppressed' AND COALESCE(disposition,'')<>'discard' ORDER BY occurred_at DESC LIMIT 20",null);int shown=0;
        while(c.moveToNext()&&shown<8){String source=c.getString(0),title=c.getString(1),detail=c.getString(2);long at=c.getLong(3);if(AttentionNoisePolicy.suppress(source,title,detail,"",""))continue;LinearLayout card=CortexUi.card(this,16);card.setPadding(dp(12),dp(10),dp(12),dp(10));TextView k=CortexUi.plain(this,source.isEmpty()?"OBSERVED":source,9,CortexUi.MUTED);CortexUi.medium(k);card.addView(k);card.addView(CortexUi.text(this,clip(title.isEmpty()?detail:title,160),12,CortexUi.TEXT));card.addView(CortexUi.plain(this,friendlyAge(at),9,CortexUi.MUTED));body.addView(card,margin(6));shown++;}
        c.close();if(shown==0)body.addView(CortexUi.text(this,"No recent grounded observations are ready to show.",12,CortexUi.MUTED),margin(6));
    }

    void addAction(LinearLayout row,String label,Runnable op,boolean primary){TextView v=CortexUi.action(this,label,primary?CortexUi.LIME:CortexUi.MUTED,primary);v.setGravity(Gravity.CENTER);v.setOnClickListener(x->{try{op.run();render();}catch(Throwable e){Toast.makeText(this,"Action update failed safely",Toast.LENGTH_SHORT).show();}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(0,0,dp(6),0);row.addView(v,p);}

    void refreshAsync(){if(destroyed||db==null)return;new Thread(()->{try{NexusEngine.refreshIfStale(db,2L*60L*1000L);}catch(Throwable ignored){}if(!destroyed)runOnUiThread(this::render);},"cortex-nexus-refresh").start();}
    String friendlyState(String s){if("READY_FOR_APPROVAL".equals(s))return"Ready";if("APPROVED".equals(s))return"Approved";if("EXECUTING".equals(s))return"In progress";if("DRAFT".equals(s))return"Later";return s.replace('_',' ');}
    int stateColor(String s){if("READY_FOR_APPROVAL".equals(s)||"APPROVED".equals(s))return CortexUi.LIME;if("EXECUTING".equals(s))return CortexUi.SAGE;return CortexUi.COPPER;}
    String friendlyAge(long at){long d=Math.max(0,System.currentTimeMillis()-at);if(d<60_000)return"now";long m=d/60_000;if(m<60)return m+"m ago";long h=m/60;if(h<24)return h+"h ago";return(h/24)+"d ago";}
    String clip(String s,int n){String x=s==null?"":s.trim().replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}
    LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,0);return p;}
}
