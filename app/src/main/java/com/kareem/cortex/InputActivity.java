package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Cortex input surface: capture only. Evidence and Deep Review remain dedicated full-screen destinations. */
public class InputActivity extends Activity {
    VaultDb db;
    EditText composer;
    TextView screenMeta;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        db=new VaultDb(this);
        build();
    }
    @Override protected void onResume(){super.onResume();StartupMaintenance.schedule(this);refreshState();}
    @Override protected void onDestroy(){try{if(db!=null)db.close();}catch(Throwable ignored){}super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(24));
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(header());

        body.addView(composerCard(),margins(0,8,0,0));

        body.addView(section("EVERYWHERE",CortexUi.GREEN));
        body.addView(screenCard());

        TextView share=CortexUi.plain(this,"Share from any app → Cortex keeps the original as Evidence.",9,CortexUi.FAINT);
        share.setPadding(dp(2),dp(12),dp(2),dp(2));body.addView(share);

        CortexUi.addBottomNav(this,root,"input",null);
        setContentView(root);
    }

    View composerCard(){
        LinearLayout card=CortexUi.card(this,24);card.setPadding(dp(16),dp(15),dp(16),dp(15));
        TextView eyebrow=CortexUi.plain(this,"CAPTURE",9,CortexUi.ORANGE);CortexUi.medium(eyebrow);if(android.os.Build.VERSION.SDK_INT>=21)eyebrow.setLetterSpacing(.10f);card.addView(eyebrow);
        TextView title=CortexUi.plain(this,"What’s on your mind?",24,CortexUi.TEXT);CortexUi.medium(title);title.setPadding(0,dp(6),0,0);card.addView(title);
        TextView sub=CortexUi.text(this,"Type it, say it, show it or attach it. Cortex keeps the source and builds understanding from there.",11,CortexUi.MUTED);sub.setPadding(0,dp(5),0,0);card.addView(sub);

        LinearLayout inputRow=new LinearLayout(this);inputRow.setGravity(Gravity.BOTTOM);inputRow.setPadding(0,dp(14),0,0);
        composer=new EditText(this);composer.setHint("Type or paste anything…");composer.setHintTextColor(CortexUi.FAINT);composer.setTextColor(CortexUi.TEXT);composer.setTextSize(14);composer.setGravity(Gravity.TOP|Gravity.START);composer.setMinLines(2);composer.setMaxLines(5);composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);composer.setPadding(dp(14),dp(12),dp(14),dp(12));composer.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,18));
        inputRow.addView(composer,new LinearLayout.LayoutParams(0,dp(82),1));
        TextView send=CortexUi.plain(this,"↑",27,CortexUi.BG);send.setGravity(Gravity.CENTER);CortexUi.medium(send);CortexUi.pressable(this,send,CortexUi.round(this,CortexUi.LIME,Color.rgb(207,230,120),18));send.setOnClickListener(v->submitText());
        LinearLayout.LayoutParams sendp=new LinearLayout.LayoutParams(dp(54),dp(54));sendp.setMargins(dp(9),0,0,0);inputRow.addView(send,sendp);card.addView(inputRow);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(11),0,0);
        addMiniAction(actions,"Voice","wave",CortexUi.RED,()->capture("voice"),0);
        addMiniAction(actions,"Photo","photo",CortexUi.GREEN,()->capture("photo"),7);
        addMiniAction(actions,"File","file",CortexUi.ORANGE,()->capture("file"),7);
        addMiniAction(actions,"Paste","text",CortexUi.YELLOW,this::pasteIntoComposer,7);
        card.addView(actions,new LinearLayout.LayoutParams(-1,dp(58)));
        return card;
    }

    void addMiniAction(LinearLayout row,String label,String glyph,int color,Runnable action,int left){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(5),0,dp(5),0);CortexUi.pressable(this,item,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,16));
        item.addView(CortexUi.glyph(this,glyph,color,false),new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView t=CortexUi.plain(this,label,9,CortexUi.MUTED);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMargins(dp(3),0,0,0);item.addView(t,tp);item.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(item,p);
    }

    View screenCard(){
        LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(13),dp(12),dp(13),dp(12));CortexUi.pressable(this,card,CortexUi.velvet(this,20));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(CortexUi.glyph(this,"open",CortexUi.GREEN,true),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(8),0);row.addView(tx,xp);
        TextView h=CortexUi.plain(this,"Understand this screen",14,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);
        screenMeta=CortexUi.text(this,"",10,CortexUi.MUTED);screenMeta.setPadding(0,dp(4),0,0);tx.addView(screenMeta);
        TextView open=CortexUi.plain(this,"›",28,CortexUi.GREEN);open.setGravity(Gravity.CENTER);row.addView(open,new LinearLayout.LayoutParams(dp(28),dp(44)));card.addView(row);
        card.setOnClickListener(v->openScreenSetup());return card;
    }

    void refreshState(){
        if(screenMeta!=null){boolean ready=CortexScreenAccessibilityService.connected();screenMeta.setText(ready?"Ready · use the Quick Settings tile from any app":"Setup needed · enable screen understanding once");screenMeta.setTextColor(ready?CortexUi.GREEN:CortexUi.MUTED);}
    }

    void submitText(){
        if(composer==null)return;String s=composer.getText().toString().trim();if(s.isEmpty()){composer.setError("Write something first");return;}
        try{String cat=AutoClassifier.category(s,"text/plain");long id=db.insert("TEXT","manual",AutoClassifier.title(s,"text/plain"),s,cat,AutoClassifier.tags(s,cat),"",Fingerprint.text(s),"{}");if(id<0){Toast.makeText(this,"Already in Cortex",Toast.LENGTH_SHORT).show();return;}composer.setText("");AnalysisQueue.kick(this,null,null);Intent i=new Intent(this,ProposalCaptureResultActivity.class);i.putExtra("item_id",id);startActivity(i);}catch(Throwable e){Toast.makeText(this,"Could not capture text",Toast.LENGTH_LONG).show();}
    }

    void pasteIntoComposer(){try{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm==null||!cm.hasPrimaryClip()||cm.getPrimaryClip().getItemCount()==0){Toast.makeText(this,"Clipboard is empty",Toast.LENGTH_SHORT).show();return;}CharSequence s=cm.getPrimaryClip().getItemAt(0).coerceToText(this);if(s!=null){composer.setText(s.toString());composer.setSelection(composer.length());composer.requestFocus();}}catch(Throwable e){Toast.makeText(this,"Could not read clipboard",Toast.LENGTH_SHORT).show();}}

    void openScreenSetup(){boolean ready=CortexScreenAccessibilityService.connected();try{startActivity(new Intent(ready?"android.settings.QUICK_SETTINGS_SETTINGS":Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable e){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}}}

    View header(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(5),dp(8),dp(2),dp(11));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.ORANGE,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));
        TextView c=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(c);if(android.os.Build.VERSION.SDK_INT>=21)c.setLetterSpacing(.20f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(c,cp);
        View d=CortexUi.divider(this);LinearLayout.LayoutParams dv=new LinearLayout.LayoutParams(dp(1),dp(28));dv.setMargins(dp(12),0,dp(12),0);row.addView(d,dv);
        TextView sys=CortexUi.plain(this,"INPUT",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)sys.setLetterSpacing(.10f);row.addView(sys,new LinearLayout.LayoutParams(0,dp(40),1));

        CortexGlyphView evidence=CortexUi.glyph(this,"nodes",CortexUi.GREEN,false);evidence.setContentDescription("Evidence");evidence.setOnClickListener(v->open(EvidenceActivity.class));row.addView(evidence,new LinearLayout.LayoutParams(dp(40),dp(40)));
        CortexGlyphView review=CortexUi.glyph(this,"brain",CortexUi.RED,false);review.setContentDescription("Deep Review");review.setOnClickListener(v->open(DeepReviewActivity.class));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(40),dp(40));rp.setMargins(dp(4),0,0,0);row.addView(review,rp);
        CortexGlyphView settings=CortexUi.glyph(this,"settings",CortexUi.ORANGE,false);settings.setContentDescription("Settings");settings.setOnClickListener(v->open(SettingsActivity.class));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(40),dp(40));sp.setMargins(dp(4),0,0,0);row.addView(settings,sp);
        return row;
    }

    TextView section(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.09f);h.setPadding(dp(1),dp(18),0,dp(8));return h;}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);startActivity(i);}catch(Throwable ignored){}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable ignored){}}
}
