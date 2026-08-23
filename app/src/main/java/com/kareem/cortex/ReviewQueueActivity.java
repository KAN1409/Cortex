package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

/** Temporary advanced test surface for the Review Queue. Prime UI will replace this later. */
public class ReviewQueueActivity extends Activity {
    VaultDb db;LinearLayout list;TextView count;
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){super.onDestroy();try{db.close();}catch(Throwable ignored){}}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(18),dp(12),dp(18),dp(8));
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Review queue",26,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);count=CortexUi.plain(this,"",11,CortexUi.MUTED);count.setPadding(0,dp(2),0,0);titles.addView(count);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));root.addView(head);
        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(20),dp(8),dp(20),dp(28));sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void refresh(){
        if(list==null)return;list.removeAllViews();ArrayList<ReviewQueueStore.Item> items=ReviewQueueStore.pending(db,100);count.setText(items.size()+" pending · test surface");
        if(items.isEmpty()){TextView e=CortexUi.text(this,"Nothing needs review right now.",14,CortexUi.MUTED);e.setPadding(0,dp(22),0,0);list.addView(e);return;}
        for(ReviewQueueStore.Item x:items)addItem(x);
    }

    void addItem(ReviewQueueStore.Item x){
        LinearLayout card=CortexUi.card(this,18);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(15),dp(14),dp(15),dp(13));
        TextView type=CortexUi.plain(this,"Possible "+friendly(x.candidateKind)+"  ·  "+Math.round(x.confidence*100)+"%",11,CortexUi.MUTED);CortexUi.medium(type);card.addView(type);
        TextView title=CortexUi.text(this,x.title,15,CortexUi.TEXT);CortexUi.medium(title);title.setPadding(0,dp(7),0,0);card.addView(title);
        if(!x.body.isEmpty()){TextView body=CortexUi.text(this,clip(x.body,420),13,CortexUi.TEXT);body.setPadding(0,dp(7),0,0);body.setTextIsSelectable(true);card.addView(body);}
        if(!x.reason.isEmpty()){TextView why=CortexUi.text(this,"Why review: "+x.reason,11,CortexUi.MUTED);why.setPadding(0,dp(8),0,0);card.addView(why);}
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(12),0,0);
        TextView confirm=smallAction("Confirm");confirm.setOnClickListener(v->{long id=ReviewQueueStore.confirm(db,x.id);toast(id>0?"Confirmed":"Could not confirm");refresh();});actions.addView(confirm,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView reject=smallAction("Dismiss");LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(40),1);rp.setMargins(dp(7),0,0,0);reject.setOnClickListener(v->{ReviewQueueStore.dismiss(db,x.id);toast("Dismissed");refresh();});actions.addView(reject,rp);card.addView(actions);
        if("ACTION".equals(x.candidateKind)){TextView notAction=CortexUi.plain(this,"Not an action",11,CortexUi.MUTED);notAction.setGravity(Gravity.CENTER);notAction.setPadding(0,dp(8),0,0);notAction.setOnClickListener(v->{ReviewQueueStore.notAction(db,x.id);toast("Marked not an action");refresh();});card.addView(notAction,new LinearLayout.LayoutParams(-1,dp(38)));}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));list.addView(card,p);
    }

    TextView smallAction(String s){TextView v=CortexUi.plain(this,s,12,CortexUi.TEXT);CortexUi.medium(v);v.setGravity(Gravity.CENTER);CortexUi.pressable(this,v,CortexUi.round(this,CortexUi.SURFACE_2,android.graphics.Color.TRANSPARENT,13));return v;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    String friendly(String x){String k=x==null?"":x.toLowerCase(Locale.US).replace('_',' ');return k.isEmpty()?"item":k;}
    String clip(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}
}
