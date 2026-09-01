package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * CORTEX UI DESIGN LOCK V1 launcher.
 * Chat-first shell using the existing grounded BrainRouter/AskCortex runtime.
 */
public class InputActivity extends AskCortexActivity {
    FrameLayout shell;
    LinearLayout drawer;
    View drawerDim;
    boolean drawerOpen=false;

    @Override protected void onResume(){super.onResume();StartupMaintenance.schedule(this);}

    @Override void build(){
        shell=new FrameLayout(this);shell.setBackgroundColor(CortexUi.BG);

        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);main.setBackgroundColor(CortexUi.BG);
        main.addView(header(),new LinearLayout.LayoutParams(-1,dp(70)));

        contextNote=CortexUi.text(this,"",10,CortexUi.LIME);contextNote.setPadding(dp(22),dp(4),dp(22),dp(6));contextNote.setVisibility(View.GONE);main.addView(contextNote);

        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        conversation=new LinearLayout(this);conversation.setOrientation(LinearLayout.VERTICAL);conversation.setPadding(dp(22),dp(8),dp(22),dp(24));scroll.addView(conversation);main.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addReadyState();

        main.addView(composer(),new LinearLayout.LayoutParams(-1,dp(84)));
        shell.addView(main,new FrameLayout.LayoutParams(-1,-1));

        drawerDim=new View(this);drawerDim.setBackgroundColor(Color.argb(178,0,0,0));drawerDim.setVisibility(View.GONE);drawerDim.setAlpha(0f);drawerDim.setOnClickListener(v->closeDrawer());shell.addView(drawerDim,new FrameLayout.LayoutParams(-1,-1));

        drawer=drawer();int width=(int)(getResources().getDisplayMetrics().widthPixels*.82f);FrameLayout.LayoutParams dpv=new FrameLayout.LayoutParams(width,-1,Gravity.START);shell.addView(drawer,dpv);drawer.setVisibility(View.GONE);drawer.setTranslationX(-width);

        setContentView(shell);CortexUi.fitSystemBars(this,shell);
    }

    View header(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(20),dp(8),dp(17),0);
        row.addView(circleButton("menu",CortexUi.TEXT,this::openDrawer),new LinearLayout.LayoutParams(dp(48),dp(48)));
        CortexLineIconView logo=new CortexLineIconView(this,"logo",CortexUi.LIME);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(34),dp(34));lp.setMargins(dp(18),0,0,0);row.addView(logo,lp);
        TextView title=CortexUi.plain(this,"Cortex",24,CortexUi.TEXT);CortexUi.bold(title);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(8),0,0,0);row.addView(title,tp);
        View search=circleButton("search",CortexUi.TEXT,()->open(VaultActivity.class));row.addView(search,new LinearLayout.LayoutParams(dp(46),dp(46)));
        View more=circleButton("more",CortexUi.MUTED,()->open(SettingsActivity.class));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(44),dp(46));mp.setMargins(dp(5),0,0,0);row.addView(more,mp);
        return row;
    }

    View composer(){
        LinearLayout outer=new LinearLayout(this);outer.setGravity(Gravity.CENTER_VERTICAL);outer.setPadding(dp(18),dp(7),dp(18),dp(11));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(6),dp(5),dp(6),dp(5));bar.setBackground(CortexUi.round(this,Color.rgb(15,17,15),Color.rgb(50,54,49),999));
        bar.addView(circleButton("plus",CortexUi.TEXT,()->capture(null)),new LinearLayout.LayoutParams(dp(46),dp(46)));
        input=new EditText(this);input.setHint("Ask Cortex anything…");input.setHintTextColor(Color.rgb(105,109,103));input.setTextColor(CortexUi.TEXT);input.setTextSize(14);input.setMinLines(1);input.setMaxLines(4);input.setSingleLine(false);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setPadding(dp(12),dp(8),dp(9),dp(8));input.setBackgroundColor(Color.TRANSPARENT);bar.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        View mic=circleButton("mic",CortexUi.TEXT,()->capture("voice"));bar.addView(mic,new LinearLayout.LayoutParams(dp(46),dp(46)));
        send=CortexUi.plain(this,"↑",28,CortexUi.BG);send.setGravity(Gravity.CENTER);CortexUi.medium(send);CortexUi.pressable(this,send,CortexUi.round(this,CortexUi.LIME,Color.rgb(208,235,116),999));send.setOnClickListener(v->submit());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(50),dp(50));sp.setMargins(dp(6),0,0,0);bar.addView(send,sp);
        outer.addView(bar,new LinearLayout.LayoutParams(-1,dp(62)));return outer;
    }

    LinearLayout drawer(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(18),dp(22),dp(18));panel.setBackgroundColor(Color.rgb(5,6,5));
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);brand.addView(new CortexLineIconView(this,"logo",CortexUi.LIME),new LinearLayout.LayoutParams(dp(34),dp(34)));TextView t=CortexUi.plain(this,"Cortex",24,CortexUi.TEXT);CortexUi.bold(t);LinearLayout.LayoutParams tpp=new LinearLayout.LayoutParams(0,-2,1);tpp.setMargins(dp(9),0,0,0);brand.addView(t,tpp);TextView avatar=CortexUi.plain(this,"KA",13,CortexUi.TEXT);avatar.setGravity(Gravity.CENTER);avatar.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,Color.rgb(75,79,73),999));avatar.setOnClickListener(v->open(SettingsActivity.class));brand.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));panel.addView(brand,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout search=new LinearLayout(this);search.setGravity(Gravity.CENTER_VERTICAL);search.setPadding(dp(13),0,dp(13),0);search.setBackground(CortexUi.round(this,Color.rgb(20,22,20),Color.rgb(54,58,52),18));search.addView(new CortexLineIconView(this,"search",CortexUi.MUTED),new LinearLayout.LayoutParams(dp(28),dp(28)));TextView st=CortexUi.plain(this,"Search Cortex",13,CortexUi.MUTED);LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(0,-2,1);stp.setMargins(dp(10),0,0,0);search.addView(st,stp);search.setOnClickListener(v->{closeDrawer();open(VaultActivity.class);});LinearLayout.LayoutParams srp=new LinearLayout.LayoutParams(-1,dp(52));srp.setMargins(0,dp(10),0,dp(8));panel.addView(search,srp);

        ScrollView sv=new ScrollView(this);sv.setVerticalScrollBarEnabled(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sv.addView(body);
        body.addView(drawerSection("PINNED"));
        body.addView(drawerItem("brief","Brief",ProposalBriefActivity.class,false));
        body.addView(drawerItem("project","People & Projects",ProposalPeopleProjectsActivity.class,false));
        body.addView(drawerItem("evidence","Evidence",EvidenceActivity.class,false));
        body.addView(drawerItem("deep","Deep Review",DeepReviewActivity.class,false));
        body.addView(divider());
        body.addView(drawerSection("CHATS"));
        body.addView(drawerAction("chat","New chat",this::newChat,false));
        body.addView(drawerAction("chat","Current chat",this::closeDrawer,true));
        body.addView(divider());
        body.addView(drawerItem("project","Projects",ProposalPeopleProjectsActivity.class,false));
        body.addView(drawerItem("evidence","Evidence",EvidenceActivity.class,false));
        body.addView(drawerItem("deep","Deep Review",DeepReviewActivity.class,false));
        body.addView(drawerItem("archive","Archive",VaultActivity.class,false));
        body.addView(divider());
        body.addView(drawerItem("settings","Settings",SettingsActivity.class,false));
        body.addView(drawerItem("chat","Help & feedback",FeatureHubActivity.class,false));
        panel.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(11),dp(8),dp(11),dp(8));footer.setBackground(CortexUi.round(this,Color.rgb(18,20,18),Color.rgb(38,42,37),16));footer.addView(new CortexLineIconView(this,"logo",CortexUi.LIME),new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout ft=new LinearLayout(this);ft.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams ftp=new LinearLayout.LayoutParams(0,-2,1);ftp.setMargins(dp(10),0,0,0);footer.addView(ft,ftp);TextView fn=CortexUi.plain(this,"Cortex",12,CortexUi.TEXT);CortexUi.medium(fn);ft.addView(fn);TextView fs=CortexUi.plain(this,"Local context ready",9,CortexUi.LIME);ft.addView(fs);panel.addView(footer,new LinearLayout.LayoutParams(-1,dp(58)));
        return panel;
    }

    TextView drawerSection(String s){TextView v=CortexUi.plain(this,s,9,CortexUi.MUTED);CortexUi.medium(v);if(android.os.Build.VERSION.SDK_INT>=21)v.setLetterSpacing(.08f);v.setPadding(dp(5),dp(16),0,dp(8));return v;}
    View drawerItem(String icon,String label,Class<?> cls,boolean selected){return drawerAction(icon,label,()->{closeDrawer();open(cls);},selected);}
    View drawerAction(String icon,String label,Runnable action,boolean selected){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(8),0,dp(6),0);r.setBackground(CortexUi.round(this,selected?Color.rgb(27,29,27):Color.TRANSPARENT,Color.TRANSPARENT,14));CortexLineIconView iv=new CortexLineIconView(this,icon,selected?CortexUi.TEXT:CortexUi.LIME);r.addView(iv,new LinearLayout.LayoutParams(dp(30),dp(30)));TextView tx=CortexUi.plain(this,label,13,selected?CortexUi.TEXT:Color.rgb(225,227,222));LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(12),0,0,0);r.addView(tx,xp);if(!selected)r.addView(new CortexLineIconView(this,"chevron",CortexUi.FAINT),new LinearLayout.LayoutParams(dp(20),dp(20)));r.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,dp(1),0,0);r.setLayoutParams(p);return r;}
    View divider(){View v=new View(this);v.setBackgroundColor(Color.rgb(38,41,37));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(0,dp(9),0,dp(4));v.setLayoutParams(p);return v;}

    View circleButton(String kind,int color,Runnable action){FrameLayout b=new FrameLayout(this);b.setBackground(CortexUi.round(this,Color.rgb(23,25,23),Color.rgb(55,59,53),999));CortexLineIconView i=new CortexLineIconView(this,kind,color);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(27),dp(27),Gravity.CENTER);b.addView(i,ip);b.setOnClickListener(v->action.run());return b;}

    void addReadyState(){TextView day=CortexUi.chip(this,"Today",CortexUi.MUTED,false);day.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-2,dp(30));dpv.gravity=Gravity.CENTER_HORIZONTAL;dpv.setMargins(0,dp(6),0,dp(12));conversation.addView(day,dpv);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.LIME,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView ready=CortexUi.plain(this,"Cortex  ·  Ready to help",10,CortexUi.MUTED);ready.setPadding(dp(8),0,0,0);row.addView(ready);conversation.addView(row);}

    @Override void styleModes(){}

    @Override void addUser(String q){
        LinearLayout wrap=new LinearLayout(this);wrap.setGravity(Gravity.RIGHT);LinearLayout bubble=new LinearLayout(this);bubble.setGravity(Gravity.CENTER_VERTICAL);bubble.setPadding(0,0,dp(13),0);bubble.setBackground(CortexUi.round(this,Color.rgb(27,30,28),Color.rgb(67,72,65),17));View rail=new View(this);rail.setBackground(CortexUi.round(this,CortexUi.LIME,Color.TRANSPARENT,999));bubble.addView(rail,new LinearLayout.LayoutParams(dp(2),-1));TextView text=CortexUi.text(this,q,14,CortexUi.TEXT);text.setPadding(dp(13),dp(11),0,dp(11));bubble.addView(text,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels*.72f),-2);bp.setMargins(dp(58),dp(14),0,dp(10));wrap.addView(bubble,bp);conversation.addView(wrap);
    }

    @Override TextView thinking(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);CortexLineIconView logo=new CortexLineIconView(this,"logo",CortexUi.LIME);row.addView(logo,new LinearLayout.LayoutParams(dp(34),dp(34)));TextView t=CortexUi.plain(this,s,10,CortexUi.MUTED);t.setPadding(dp(9),dp(9),dp(12),dp(9));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);row.addView(t,tp);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,dp(6),dp(38),dp(8));conversation.addView(row,rp);return t;}

    @Override void addAnswer(LocalAskRouter.Result r,String q,boolean refined){
        if(destroyed||r==null)return;LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.TOP);line.addView(new CortexLineIconView(this,"logo",CortexUi.LIME),new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(13),dp(14),dp(13));card.setBackground(CortexUi.round(this,Color.rgb(25,28,26),Color.rgb(62,67,60),18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(10),0,0,0);line.addView(card,cp);
        TextView body=CortexUi.text(this,r.answer,14,CortexUi.TEXT);body.setTextIsSelectable(true);card.addView(body);
        addStructuredActions(card,r);
        int sourceCount=r.grounded==null||r.grounded.sources==null?0:r.grounded.sources.size();if(sourceCount>0){View d=new View(this);d.setBackgroundColor(Color.rgb(55,59,53));LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,dp(1));dpv.setMargins(0,dp(12),0,dp(8));card.addView(d,dpv);LinearLayout ev=new LinearLayout(this);ev.setGravity(Gravity.CENTER_VERTICAL);ev.addView(new CortexLineIconView(this,"evidence",CortexUi.LIME),new LinearLayout.LayoutParams(dp(22),dp(22)));TextView e=CortexUi.plain(this,"Evidence linked",10,CortexUi.LIME);e.setPadding(dp(7),0,0,0);ev.addView(e,new LinearLayout.LayoutParams(0,-2,1));TextView count=CortexUi.chip(this,String.valueOf(sourceCount),CortexUi.MUTED,false);ev.addView(count,new LinearLayout.LayoutParams(-2,dp(28)));ev.addView(new CortexLineIconView(this,"chevron",CortexUi.MUTED),new LinearLayout.LayoutParams(dp(20),dp(20)));ev.setOnClickListener(v->open(EvidenceActivity.class));card.addView(ev,new LinearLayout.LayoutParams(-1,dp(34)));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(12));conversation.addView(line,lp);scrollEnd();
    }

    @Override void addStructuredActions(LinearLayout card,LocalAskRouter.Result r){
        if(db==null||r.jobId<=0||"your_data".equals(r.sourceMode))return;ArrayList<BrainActionStore.Action> xs;try{xs=BrainActionStore.list(db,r.jobId);}catch(Throwable e){return;}if(xs.isEmpty())return;TextView h=CortexUi.plain(this,"Recommended next actions",11,CortexUi.LIME);CortexUi.medium(h);h.setPadding(0,dp(12),0,dp(7));card.addView(h);int n=0;for(BrainActionStore.Action x:xs){if(n>=4)break;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView no=CortexUi.plain(this,String.valueOf(++n),10,CortexUi.LIME);no.setGravity(Gravity.CENTER);no.setBackground(CortexUi.round(this,Color.argb(15,185,218,77),CortexUi.LIME,999));row.addView(no,new LinearLayout.LayoutParams(dp(28),dp(28)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(9),0,0,0);row.addView(tx,xp);TextView title=CortexUi.text(this,x.title,12,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);TextView state=CortexUi.plain(this,x.ready()?"Ready · preview before action":"Needs details",9,CortexUi.MUTED);state.setPadding(0,dp(2),0,0);tx.addView(state);row.setOnClickListener(v->CortexActionDispatcher.preview(this,db,x));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,dp(6),0,0);card.addView(row,rp);}}

    @Override void addFailure(String message,Throwable e){TextView v=CortexUi.text(this,message,11,CortexUi.MUTED);v.setPadding(dp(14),dp(11),dp(14),dp(11));v.setBackground(CortexUi.round(this,Color.rgb(25,27,25),Color.rgb(60,64,58),16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(48),dp(7),0,dp(9));conversation.addView(v,p);scrollEnd();}

    @Override void finishBusy(){if(destroyed)return;busy=false;if(send!=null){send.setText("↑");send.setEnabled(true);}if(input!=null)input.requestFocus();scrollEnd();}

    void openDrawer(){if(drawerOpen)return;drawerOpen=true;drawerDim.setVisibility(View.VISIBLE);drawer.setVisibility(View.VISIBLE);drawerDim.animate().alpha(1f).setDuration(160).start();drawer.animate().translationX(0).setDuration(190).start();}
    void closeDrawer(){if(!drawerOpen)return;drawerOpen=false;int w=drawer.getWidth()>0?drawer.getWidth():(int)(getResources().getDisplayMetrics().widthPixels*.82f);drawer.animate().translationX(-w).setDuration(170).withEndAction(()->drawer.setVisibility(View.GONE)).start();drawerDim.animate().alpha(0f).setDuration(150).withEndAction(()->drawerDim.setVisibility(View.GONE)).start();}
    void newChat(){closeDrawer();focalItemId=0;if(conversation!=null){conversation.removeAllViews();addReadyState();}if(input!=null){input.setText("");input.requestFocus();}}
    void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);if(mode!=null)i.putExtra("mode",mode);startActivity(i);}catch(Throwable ignored){}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(Throwable ignored){}}

    @Override public void onBackPressed(){if(drawerOpen){closeDrawer();return;}super.onBackPressed();}
}
