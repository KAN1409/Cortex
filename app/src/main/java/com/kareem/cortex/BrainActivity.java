package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BrainActivity extends Activity {
    VaultDb db;LinearLayout feed;TextView status;int bg=Color.rgb(16,17,20),panel=Color.rgb(24,26,31),text=Color.rgb(243,244,246),muted=Color.rgb(165,168,176),accent=Color.rgb(143,169,255),ok=Color.rgb(143,220,170);
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}void pad(View v,int x){v.setPadding(dp(x),dp(x),dp(x),dp(x));}
    TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);return v;}
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new VaultDb(this);build();refresh();}
    @Override protected void onResume(){super.onResume();if(db!=null)refresh();}

    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);pad(root,16);
        TextView title=tv("CORTEX",27,text);title.setTypeface(null,1);root.addView(title);root.addView(tv("Real Second Brain • ask → connect → close loops",14,muted));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(14),0,0);
        Button ask=button("ASK CORTEX"),loops=button("OPEN LOOPS"),graph=button("GRAPH"),capture=button("CAPTURE");addEq(row,ask,0);addEq(row,loops,7);addEq(row,graph,7);addEq(row,capture,7);root.addView(row);
        status=tv("",12,muted);status.setPadding(0,dp(10),0,dp(10));root.addView(status);
        ScrollView sv=new ScrollView(this);feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);sv.addView(feed);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        ask.setOnClickListener(v->askDialog());loops.setOnClickListener(v->loopsDialog());graph.setOnClickListener(v->graphDialog());capture.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
    }
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(9);b.setTextColor(text);b.setBackgroundColor(panel);return b;}void addEq(LinearLayout r,View v,int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(m),0,0,0);r.addView(v,p);}

    void refresh(){feed.removeAllViews();int loops=SecondBrainEngine.openLoops(db,200).size(),items=count("knowledge_items","1=1"),failed=count("knowledge_items","status='analysis_failed'");ArrayList<BrainNode> nodes=SecondBrainEngine.graph(db,10);status.setText(items+" memories  •  "+loops+" open loops  •  "+nodes.size()+" active graph nodes"+(failed>0?"  •  "+failed+" failed":""));
        sectionTitle("ATTENTION NOW");ArrayList<BrainOpenLoop> os=SecondBrainEngine.openLoops(db,5);if(os.isEmpty())feed.addView(tv("No open loops detected.",14,muted));else for(BrainOpenLoop l:os)addLoopCard(feed,l,false);
        sectionTitle("ACTIVE CONTEXT");if(nodes.isEmpty())feed.addView(tv("Add more memories and Cortex will build people/project/topic context here.",14,muted));else for(int i=0;i<Math.min(6,nodes.size());i++)addNodeCard(feed,nodes.get(i));
        sectionTitle("RECENT MEMORY");for(KnowledgeItem k:db.lexicalSearch("",6))addMemoryCard(feed,k,null);
    }
    int count(String table,String where){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table+" WHERE "+where,null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    void sectionTitle(String s){TextView h=tv(s,11,accent);h.setTypeface(null,1);h.setPadding(0,dp(16),0,dp(7));feed.addView(h);}

    void askDialog(){EditText q=new EditText(this);q.setHint("What do I know about…? What is still pending? What did we decide?");q.setMinLines(3);AlertDialog d=new AlertDialog.Builder(this).setTitle("Ask Cortex").setMessage("Grounded answer • only your saved memory is used").setView(q).setNegativeButton("Cancel",null).setPositiveButton("ANSWER",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String s=q.getText().toString().trim();if(s.isEmpty())return;d.dismiss();Toast.makeText(this,"Building grounded answer…",Toast.LENGTH_SHORT).show();new Thread(()->{GroundedAnswer a=SecondBrainEngine.ask(db,s);runOnUiThread(()->answerDialog(a));}).start();}));d.show();}
    void answerDialog(GroundedAnswer a){ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,16);sv.addView(box);label(box,"ANSWER");box.addView(tv(a.answer,15,text));label(box,"GROUNDING");box.addView(tv("Confidence "+Math.round(a.confidence*100)+"%  •  "+a.sources.size()+" memory sources\nCortex does not add facts that are not present in these sources.",12,muted));
        if(!a.openLoops.isEmpty()){label(box,"OPEN LOOPS");box.addView(tv(bullets(a.openLoops),13,text));}if(!a.decisions.isEmpty()){label(box,"DECISIONS");box.addView(tv(bullets(a.decisions),13,text));}
        if(!a.sources.isEmpty()){label(box,"SOURCES");for(int i=0;i<a.sources.size();i++)addMemoryCard(box,a.sources.get(i).item,"M"+(i+1)+" • "+Math.round(a.sources.get(i).score*100)+"%");}
        new AlertDialog.Builder(this).setTitle("Ask Cortex").setView(sv).setNegativeButton("Close",null).show();}

    void loopsDialog(){ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,14);sv.addView(box);ArrayList<BrainOpenLoop> loops=SecondBrainEngine.openLoops(db,120);if(loops.isEmpty())box.addView(tv("No open loops detected.",14,muted));else for(BrainOpenLoop l:loops)addLoopCard(box,l,true);new AlertDialog.Builder(this).setTitle("Open Loops • "+loops.size()).setView(sv).setNegativeButton("Close",null).show();}
    void addLoopCard(LinearLayout parent,BrainOpenLoop l,boolean doneButton){LinearLayout c=card();TextView t=tv(l.action,14,text);t.setTypeface(null,1);c.addView(t);c.addView(tv(l.title+(empty(l.due)?"":"  •  due: "+l.due),11,muted));if(doneButton){Button done=button("MARK DONE");done.setOnClickListener(v->{android.content.ContentValues x=new android.content.ContentValues();x.put("status","done");db.getWritableDatabase().update("actions",x,"id=?",new String[]{String.valueOf(l.actionId)});Toast.makeText(this,"Loop closed",Toast.LENGTH_SHORT).show();refresh();});c.addView(done,new LinearLayout.LayoutParams(-1,dp(42)));}addCard(parent,c);}

    void graphDialog(){ArrayList<BrainNode> nodes=SecondBrainEngine.graph(db,80);String[] labels=new String[nodes.size()];for(int i=0;i<nodes.size();i++){BrainNode n=nodes.get(i);labels[i]=n.type+" • "+n.label+"  ("+n.mentions+")";}if(labels.length==0){Toast.makeText(this,"No graph nodes yet",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("People • Projects • Topics").setItems(labels,(d,w)->nodeContext(nodes.get(w))).setNegativeButton("Close",null).show();}
    void addNodeCard(LinearLayout parent,BrainNode n){LinearLayout c=card();c.setClickable(true);c.setOnClickListener(v->nodeContext(n));TextView t=tv(n.label,15,text);t.setTypeface(null,1);c.addView(t);c.addView(tv(n.type+"  •  "+n.mentions+" mentions  •  "+fmt(n.latestAt),11,muted));addCard(parent,c);}
    void nodeContext(BrainNode n){ArrayList<KnowledgeItem> items=SecondBrainEngine.context(db,n,12);ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,14);sv.addView(box);box.addView(tv(n.type+" • "+n.mentions+" mentions",12,muted));for(KnowledgeItem k:items)addMemoryCard(box,k,null);new AlertDialog.Builder(this).setTitle(n.label).setView(sv).setNegativeButton("Close",null).show();}

    void addMemoryCard(LinearLayout parent,KnowledgeItem k,String prefix){LinearLayout c=card();c.setClickable(true);c.setOnClickListener(v->memoryDetail(k));TextView t=tv((prefix==null?"":prefix+"  ")+k.title,14,text);t.setTypeface(null,1);c.addView(t);c.addView(tv(k.category+"  •  "+k.type+"  •  "+fmt(k.createdAt),11,muted));String p=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);if(p==null)p="";if(p.length()>220)p=p.substring(0,220)+"…";TextView b=tv(p,13,text);b.setPadding(0,dp(5),0,0);c.addView(b);addCard(parent,c);}
    void memoryDetail(KnowledgeItem k){ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,15);sv.addView(box);label(box,"SUMMARY");box.addView(tv(empty(k.summary)?"No summary":k.summary,14,text));if(!empty(k.extractedText)){label(box,"EXTRACTED / TRANSCRIPT");String x=k.extractedText;if(x.length()>5000)x=x.substring(0,5000)+"…";box.addView(tv(x,13,text));}ArrayList<String> acts=db.actions(k.id);if(!acts.isEmpty()){label(box,"OPEN ACTIONS");box.addView(tv(bullets(acts),13,text));}ArrayList<SemanticHit> rel=SemanticIndex.related(db,k,5);if(!rel.isEmpty()){label(box,"RELATED MEMORIES");for(SemanticHit h:rel)addMemoryCard(box,h.item,Math.round(h.score*100)+"%");}new AlertDialog.Builder(this).setTitle(k.title).setView(sv).setNegativeButton("Close",null).show();}

    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackgroundResource(R.drawable.rounded_panel);pad(c,12);return c;}void addCard(LinearLayout p,View c){LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(-1,-2);x.setMargins(0,0,0,dp(8));p.addView(c,x);}void label(LinearLayout p,String s){TextView h=tv(s,11,accent);h.setTypeface(null,1);h.setPadding(0,dp(12),0,dp(5));p.addView(h);}String bullets(ArrayList<String> xs){StringBuilder s=new StringBuilder();for(String x:xs){if(s.length()>0)s.append('\n');s.append("• ").append(x);}return s.toString();}boolean empty(String s){return s==null||s.trim().isEmpty();}String fmt(long ms){return new SimpleDateFormat("dd MMM • HH:mm",Locale.getDefault()).format(new Date(ms));}
}
