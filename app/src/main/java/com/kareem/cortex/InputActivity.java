package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Content-first PRIME input: current context, one voice orb, compact secondary capture. */
public class InputActivity extends Activity {
    boolean accessOnboardingLaunchAttempted=false;
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();maybeLaunchAccessOnboarding();}
    @Override protected void onPostResume(){super.onPostResume();StartupMaintenance.schedule(this);}

    void maybeLaunchAccessOnboarding(){
        if(accessOnboardingLaunchAttempted)return;
        if(getSharedPreferences(AccessOnboardingActivity.PREFS,MODE_PRIVATE).getBoolean(AccessOnboardingActivity.KEY_SEEN,false))return;
        accessOnboardingLaunchAttempted=true;
        try{startActivity(new Intent(this,AccessOnboardingActivity.class));}catch(Throwable ignored){}
    }

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(header());addContext(body);

        // Voice is not one tile among many. The Cortex orb remains the single primary capture action,
        // but it now sits inside the user's current cognitive situation instead of above it.
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(0,dp(17),0,dp(8));
        CortexRingButton orb=new CortexRingButton(this);orb.setGlyph(CortexRingButton.Glyph.RECORD);orb.setAccent(CortexUi.RED);orb.setProgress(0f);orb.setOnClickListener(v->capture("voice"));hero.addView(orb,new LinearLayout.LayoutParams(dp(112),dp(112)));
        TextView prompt=CortexUi.plain(this,"What's on your mind?",21,CortexUi.TEXT);CortexUi.medium(prompt);prompt.setGravity(Gravity.CENTER);prompt.setPadding(0,dp(7),0,0);hero.addView(prompt);
        TextView hint=CortexUi.text(this,"Speak, type, share or attach. Cortex keeps the original evidence and connects it to what is happening now.",11,CortexUi.MUTED);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(18),dp(5),dp(18),0);hero.addView(hint);body.addView(hero);

        LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);quick.setGravity(Gravity.CENTER);quick.setPadding(0,dp(4),0,0);
        addQuick(quick,"Text","text",CortexUi.YELLOW,"text",0);addQuick(quick,"Photo","photo",CortexUi.GREEN,"photo",7);addQuick(quick,"File","file",CortexUi.ORANGE,"file",7);body.addView(quick,new LinearLayout.LayoutParams(-1,dp(70)));

        addRecent(body);

        body.addView(section("SYSTEM",CortexUi.GREEN));
        LinearLayout system=CortexUi.card(this,18);system.setPadding(dp(11),dp(10),dp(11),dp(10));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER_VERTICAL);
        boolean screenReady=CortexScreenAccessibilityService.connected();
        TextView screen=CortexUi.action(this,screenReady?"Screen ✓":"Screen setup",screenReady?CortexUi.GREEN:CortexUi.ORANGE,false);screen.setOnClickListener(v->{try{startActivity(new Intent(screenReady?"android.settings.QUICK_SETTINGS_SETTINGS":Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable e){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}}});controls.addView(screen,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView share=CortexUi.action(this,"Share",CortexUi.YELLOW,false);share.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Share to Cortex").setMessage("From any app, use Android Share and choose Cortex. The original item enters the same grounded capture and proposal flow.").setPositiveButton("Got it",null).show());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(42),1);sp.setMargins(dp(7),0,0,0);controls.addView(share,sp);
        TextView access=CortexUi.action(this,"Access",CortexUi.GREEN,false);access.setOnClickListener(v->open(PhoneContextAccessActivity.class));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(42),1);ap.setMargins(dp(7),0,0,0);controls.addView(access,ap);system.addView(controls);
        TextView sysHint=CortexUi.plain(this,"Screen context, Android Share and phone-awareness access stay available without occupying the capture surface.",10,CortexUi.MUTED);sysHint.setPadding(dp(3),dp(8),dp(3),0);system.addView(sysHint);body.addView(system);

        CortexUi.addBottomNav(this,root,"input",null);setContentView(root);
    }

    View header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(5),dp(8),dp(2),dp(8));View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.ORANGE,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));TextView c=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(c);if(android.os.Build.VERSION.SDK_INT>=21)c.setLetterSpacing(.20f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(c,cp);View d=CortexUi.divider(this);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(28));dpv.setMargins(dp(12),0,dp(12),0);row.addView(d,dpv);TextView sys=CortexUi.plain(this,"INPUT",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)sys.setLetterSpacing(.10f);row.addView(sys,new LinearLayout.LayoutParams(0,dp(40),1));CortexGlyphView settings=CortexUi.glyph(this,"settings",CortexUi.ORANGE,false);settings.setOnClickListener(v->{CortexHaptics.press(v);open(SettingsActivity.class);});row.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46)));return row;}

    void addContext(LinearLayout body){
        VaultDb db=null;try{db=new VaultDb(this);ContextPacketBuilder.Packet p=ContextPacketBuilder.buildLocal(db,190);if(p==null||!p.available())return;LinearLayout card=CortexUi.card(this,18);card.setPadding(dp(12),dp(10),dp(12),dp(10));CortexUi.pressable(this,card,CortexUi.matte(this,18));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);int color=p.confidence>=.82?CortexUi.GREEN:CortexUi.ORANGE;row.addView(CortexUi.glyph(this,"brain",color,true),new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(8),0);row.addView(tx,xp);TextView label=CortexUi.plain(this,"CURRENT CONTEXT · "+Math.round(p.confidence*100)+"%",8,color);CortexUi.medium(label);tx.addView(label);TextView title=CortexUi.text(this,p.title,13,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(1);title.setPadding(0,dp(2),0,0);tx.addView(title);String detail=!p.nextStep.isEmpty()?"Next: "+p.nextStep:(!p.currentActivity.isEmpty()?p.currentActivity:p.goal);if(detail!=null&&!detail.trim().isEmpty()){TextView d=CortexUi.plain(this,clip(detail,100),9,CortexUi.MUTED);d.setMaxLines(2);d.setPadding(0,dp(3),0,0);tx.addView(d);}row.addView(CortexUi.plain(this,"›",24,CortexUi.MUTED),new LinearLayout.LayoutParams(dp(28),dp(38)));card.addView(row);card.setOnClickListener(v->open(ContextNowActivity.class));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(5),0,0);body.addView(card,cp);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}

    void addQuick(LinearLayout row,String label,String icon,int color,String mode,int left){
        LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);b.setPadding(dp(7),0,dp(7),0);CortexUi.pressable(this,b,CortexUi.matte(this,16));
        b.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView t=CortexUi.plain(this,label,11,CortexUi.TEXT);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMargins(dp(6),0,0,0);b.addView(t,tp);b.setOnClickListener(v->capture(mode));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.setMargins(dp(left),dp(8),0,0);row.addView(b,p);
    }

    void addRecent(LinearLayout body){
        VaultDb db=null;KnowledgeItem k=null;try{db=new VaultDb(this);ArrayList<KnowledgeItem> xs=db.lexicalSearch("",1);if(xs!=null&&!xs.isEmpty())k=xs.get(0);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
        if(k==null)return;final KnowledgeItem item=k;body.addView(section("RECENT",CortexUi.MUTED));LinearLayout card=CortexUi.card(this,18);card.setPadding(dp(12),dp(11),dp(12),dp(11));CortexUi.pressable(this,card,CortexUi.matte(this,18));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);int color="AUDIO".equalsIgnoreCase(item.type)?CortexUi.RED:CortexUi.semanticFor(item.type);row.addView(CortexUi.glyph(this,"AUDIO".equalsIgnoreCase(item.type)?"wave":"note",color,true),new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(8),0);row.addView(tx,xp);TextView title=CortexUi.text(this,item.title==null||item.title.trim().isEmpty()?"Recent capture":item.title,13,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(1);tx.addView(title);TextView meta=CortexUi.plain(this,(item.type==null?"Capture":item.type)+"  •  "+(item.status==null?"":item.status),9,CortexUi.MUTED);meta.setPadding(0,dp(3),0,0);tx.addView(meta);TextView go=CortexUi.plain(this,"›",24,CortexUi.MUTED);go.setGravity(Gravity.CENTER);row.addView(go,new LinearLayout.LayoutParams(dp(30),dp(40)));card.addView(row);card.setOnClickListener(v->{Intent i=new Intent(this,ProposalCaptureResultActivity.class);i.putExtra("item_id",item.id);startActivity(i);});body.addView(card);
    }

    TextView section(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.09f);h.setPadding(dp(1),dp(17),0,dp(8));return h;}
    void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);startActivity(i);}catch(Throwable ignored){}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable ignored){}}
    String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
