package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Conversation-first Cortex Brain UI. Product plumbing stays secondary to the dialogue. */
public class AskCortexActivity extends Activity {
    VaultDb db;
    LinearLayout conversation, modeBar;
    ScrollView scroll;
    EditText input;
    TextView send, modeNote, contextNote;
    volatile boolean busy=false,destroyed=false;
    String sourceMode="combined";
    long focalItemId=0;

    final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"cortex-brain-ui");
        t.setPriority(Thread.NORM_PRIORITY-1);
        return t;
    });

    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        db=new VaultDb(this);
        build();
        applyPrefill(getIntent());
    }

    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        setIntent(i);
        applyPrefill(i);
    }

    @Override protected void onDestroy(){
        destroyed=true;
        worker.shutdownNow();
        if(db!=null){try{db.close();}catch(Throwable ignored){}db=null;}
        super.onDestroy();
    }

    void applyPrefill(Intent i){
        if(i==null||input==null)return;
        long id=i.getLongExtra("item_id",0);
        if(id>0){focalItemId=id;showFocal(id);i.removeExtra("item_id");}
        String p=i.getStringExtra("prefill");
        if(p!=null&&!p.trim().isEmpty()){
            input.setText(p.trim());
            input.setSelection(input.length());
            i.removeExtra("prefill");
            input.requestFocus();
            scrollEnd();
        }
    }

    void showFocal(long id){
        if(contextNote==null)return;
        String label="Capture #"+id;
        try{
            KnowledgeItem k=db==null?null:db.getById(id);
            if(k!=null)label=clean(k.title)+" · "+friendlyType(k.type);
        }catch(Throwable ignored){}
        contextNote.setText("Context · "+label);
        contextNote.setVisibility(View.VISIBLE);
    }

    void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18),dp(9),dp(14),dp(7));

        TextView h=CortexUi.plain(this,"Cortex",22,CortexUi.TEXT);
        CortexUi.medium(h);
        header.addView(h,new LinearLayout.LayoutParams(0,dp(48),1));

        TextView modeMenu=CortexUi.chip(this,"Combined ▾",CortexUi.MUTED,false);
        modeMenu.setTag("modeMenu");
        modeMenu.setGravity(Gravity.CENTER);
        modeMenu.setOnClickListener(v->toggleModeChooser());
        header.addView(modeMenu,new LinearLayout.LayoutParams(dp(102),dp(36)));

        TextView settings=CortexUi.plain(this,"⋮",24,CortexUi.MUTED);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(40),dp(40));
        hp.setMargins(dp(4),0,0,0);
        header.addView(settings,hp);
        root.addView(header);

        modeBar=new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setPadding(dp(18),dp(2),dp(18),dp(5));
        modeBar.setVisibility(View.GONE);
        addMode("Your data","your_data");
        addMode("External","external");
        addMode("Combined","combined");
        root.addView(modeBar,new LinearLayout.LayoutParams(-1,dp(43)));

        modeNote=CortexUi.plain(this,"",10,CortexUi.MUTED);
        modeNote.setPadding(dp(20),0,dp(20),dp(5));
        modeNote.setVisibility(View.GONE);
        root.addView(modeNote);

        contextNote=CortexUi.plain(this,"",11,CortexUi.MUTED);
        contextNote.setPadding(dp(20),dp(4),dp(20),dp(7));
        contextNote.setVisibility(View.GONE);
        root.addView(contextNote);
        styleModes();

        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        conversation=new LinearLayout(this);
        conversation.setOrientation(LinearLayout.VERTICAL);
        conversation.setPadding(dp(18),dp(12),dp(18),dp(14));
        scroll.addView(conversation);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        suggestions();

        LinearLayout contextStrip=new LinearLayout(this);
        contextStrip.setGravity(Gravity.CENTER_VERTICAL);
        contextStrip.setPadding(dp(18),0,dp(18),dp(5));
        TextView context=CortexUi.plain(this,"Chat · Cortex memory",11,CortexUi.MUTED);
        contextStrip.addView(context,new LinearLayout.LayoutParams(0,dp(28),1));
        TextView sources=CortexUi.plain(this,"Sources ▾",11,CortexUi.MUTED);
        sources.setGravity(Gravity.CENTER);
        sources.setOnClickListener(v->{
            if(modeNote.getVisibility()==View.VISIBLE){modeNote.setVisibility(View.GONE);sources.setText("Sources ▾");}
            else{modeNote.setVisibility(View.VISIBLE);sources.setText("Sources ▴");}
        });
        contextStrip.addView(sources,new LinearLayout.LayoutParams(-2,dp(28)));
        root.addView(contextStrip);

        LinearLayout composerShell=new LinearLayout(this);
        composerShell.setOrientation(LinearLayout.HORIZONTAL);
        composerShell.setGravity(Gravity.CENTER_VERTICAL);
        composerShell.setPadding(dp(12),dp(7),dp(12),dp(10));

        input=new EditText(this);
        input.setHint("Ask Cortex anything…");
        input.setHintTextColor(CortexUi.FAINT);
        input.setTextColor(CortexUi.TEXT);
        input.setTextSize(15);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(15),dp(10),dp(10),dp(10));
        input.setBackground(CortexUi.round(this,CortexUi.SURFACE,Color.TRANSPARENT,16));
        composerShell.addView(input,new LinearLayout.LayoutParams(0,-2,1));

        send=CortexUi.action(this,"↑",CortexUi.ACCENT,true);
        send.setTextSize(22);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(48),dp(48));
        sp.setMargins(dp(8),0,0,0);
        composerShell.addView(send,sp);
        send.setOnClickListener(v->submit());
        root.addView(composerShell);

        CortexUi.addBottomNav(this,root,"ask",null);
        setContentView(root);
    }

    void toggleModeChooser(){
        modeBar.setVisibility(modeBar.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
    }

    void addMode(String label,String key){
        TextView c=CortexUi.chip(this,label,CortexUi.MUTED,false);
        c.setTag(key);
        c.setOnClickListener(v->{
            if(busy)return;
            sourceMode=(String)v.getTag();
            styleModes();
            modeBar.setVisibility(View.GONE);
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(36),1);
        if(modeBar.getChildCount()>0)p.setMargins(dp(7),0,0,0);
        modeBar.addView(c,p);
    }

    void styleModes(){
        if(modeBar!=null){
            for(int i=0;i<modeBar.getChildCount();i++){
                TextView t=(TextView)modeBar.getChildAt(i);
                boolean on=sourceMode.equals(t.getTag());
                t.setTextColor(on?CortexUi.TEXT:CortexUi.MUTED);
                t.setBackground(CortexUi.round(this,on?CortexUi.SURFACE_2:CortexUi.SURFACE,Color.TRANSPARENT,999));
                if(on)CortexUi.medium(t);
            }
        }
        if(modeNote!=null){
            if("combined".equals(sourceMode))modeNote.setText("Cortex memory + configured external AI · local fallback available");
            else if("external".equals(sourceMode))modeNote.setText("External AI only · Cortex memory stays private");
            else modeNote.setText("Cortex memory only · no cloud memory upload");
        }
        View root=modeBar==null?null:modeBar.getRootView();
        if(root!=null){
            View menu=root.findViewWithTag("modeMenu");
            if(menu instanceof TextView)((TextView)menu).setText(modeLabel(sourceMode)+" ▾");
        }
    }

    void suggestions(){
        TextView sh=CortexUi.plain(this,"Start with",11,CortexUi.MUTED);
        CortexUi.medium(sh);
        sh.setPadding(0,dp(6),0,dp(10));
        conversation.addView(sh);
        addSuggestion("What still needs my attention?");
        addSuggestion("What did I decide recently?");
        addSuggestion("What do I know about this project?");
    }

    void addSuggestion(String s){
        TextView c=CortexUi.plain(this,s,14,CortexUi.TEXT);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(14),0,dp(14),0);
        CortexUi.pressable(this,c,CortexUi.round(this,CortexUi.SURFACE,Color.TRANSPARENT,14));
        c.setOnClickListener(v->{if(destroyed)return;input.setText(s);input.setSelection(input.length());submit();});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));
        p.setMargins(0,0,0,dp(7));
        conversation.addView(c,p);
    }

    void submit(){
        if(busy||destroyed)return;
        String q=input.getText().toString().trim();
        if(q.isEmpty())return;
        busy=true;
        send.setText("…");
        send.setEnabled(false);
        hideKeyboard();
        addUser(q);
        input.setText("");
        TextView thinking=thinking("Thinking · 0%");
        scrollEnd();
        final String requestedMode=sourceMode;
        final long requestedFocal=focalItemId;
        try{
            worker.execute(()->{
                VaultDb workDb=null;
                try{
                    workDb=new VaultDb(getApplicationContext());
                    LocalAskRouter.Result r=BrainRouter.fast(getApplicationContext(),workDb,q,requestedMode,requestedFocal,(job,label,percent)->progress(thinking,label,percent));
                    postUi(()->{if(conversation!=null&&thinking.getParent()!=null)conversation.removeView(thinking);addAnswer(r,q,false);finishBusy();});
                }catch(Throwable e){
                    postUi(()->{if(conversation!=null&&thinking.getParent()!=null)conversation.removeView(thinking);addFailure("Cortex stopped safely before completing this answer.",e);finishBusy();});
                }finally{if(workDb!=null)try{workDb.close();}catch(Throwable ignored){}}
            });
        }catch(RejectedExecutionException e){finishBusy();}
    }

    void refine(String q,TextView button){
        if(destroyed)return;
        button.setEnabled(false);
        button.setText("Improving…");
        TextView thinking=thinking("Refining · 0%");
        scrollEnd();
        try{
            worker.execute(()->{
                VaultDb workDb=null;
                try{
                    workDb=new VaultDb(getApplicationContext());
                    LocalAskRouter.Result r=LocalAskRouter.ask(getApplicationContext(),workDb,q,(job,label,percent)->progress(thinking,label,percent));
                    postUi(()->{
                        if(conversation!=null&&thinking.getParent()!=null)conversation.removeView(thinking);
                        if("local-qwen".equals(r.provider))addAnswer(r,q,true);
                        else{
                            TextView e=CortexUi.text(this,r.error.isEmpty()?"Could not improve the answer right now.":"Grounded answer kept because local refinement is unavailable.",11,CortexUi.MUTED);
                            e.setPadding(dp(2),dp(10),dp(2),dp(10));
                            conversation.addView(e);
                            button.setText("Improve wording");
                            button.setEnabled(true);
                        }
                        scrollEnd();
                    });
                }catch(Throwable e){
                    postUi(()->{if(conversation!=null&&thinking.getParent()!=null)conversation.removeView(thinking);button.setText("Improve wording");button.setEnabled(true);addFailure("Local refinement stopped safely.",e);});
                }finally{if(workDb!=null)try{workDb.close();}catch(Throwable ignored){}}
            });
        }catch(RejectedExecutionException e){button.setText("Improve wording");button.setEnabled(true);}
    }

    void progress(TextView view,String label,int percent){
        postUi(()->{if(view!=null&&view.getParent()!=null){view.setText(label+" · "+Math.max(0,Math.min(100,percent))+"%");scrollEnd();}});
    }

    void postUi(Runnable r){
        if(destroyed||isFinishing()||isDestroyed())return;
        runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())try{r.run();}catch(Throwable ignored){}});
    }

    void finishBusy(){
        if(destroyed)return;
        busy=false;
        if(send!=null){send.setText("↑");send.setEnabled(true);}
        if(input!=null)input.requestFocus();
        scrollEnd();
    }

    void addFailure(String message,Throwable e){
        if(destroyed||conversation==null)return;
        String detail=e==null?"":e.getClass().getSimpleName();
        TextView v=CortexUi.text(this,message+(detail.isEmpty()?"":"\n"+detail),11,CortexUi.MUTED);
        v.setPadding(0,dp(8),0,dp(10));
        conversation.addView(v);
        scrollEnd();
    }

    void addUser(String q){
        LinearLayout wrap=new LinearLayout(this);
        wrap.setGravity(Gravity.RIGHT);
        TextView bubble=CortexUi.text(this,q,15,CortexUi.TEXT);
        bubble.setPadding(dp(14),dp(10),dp(14),dp(10));
        bubble.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,Color.TRANSPARENT,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);
        p.setMargins(dp(52),dp(16),0,dp(12));
        wrap.addView(bubble,p);
        conversation.addView(wrap);
    }

    TextView thinking(String s){
        TextView t=CortexUi.plain(this,s,11,CortexUi.MUTED);
        t.setPadding(0,dp(5),0,dp(7));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);
        p.setMargins(0,0,dp(48),dp(8));
        conversation.addView(t,p);
        return t;
    }

    void addAnswer(LocalAskRouter.Result r,String q,boolean refined){
        if(destroyed||r==null)return;
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0,dp(6),0,dp(16));

        TextView body=CortexUi.text(this,r.answer,16,CortexUi.TEXT);
        body.setPadding(0,0,0,0);
        body.setTextIsSelectable(true);
        card.addView(body);
        addStructuredActions(card,r);

        LinearLayout details=new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        details.setPadding(dp(12),dp(9),dp(12),dp(9));
        details.setBackground(CortexUi.round(this,CortexUi.SURFACE,Color.TRANSPARENT,12));
        TextView tech=CortexUi.plain(this,modeLabel(r.sourceMode)+" · "+friendlyMs(r.totalMs)+(r.jobId>0?" · job "+r.jobId:""),10,CortexUi.MUTED);
        details.addView(tech);
        for(int i=0;i<Math.min(4,r.grounded.sources.size());i++){
            KnowledgeItem k=r.grounded.sources.get(i).item;
            TextView source=CortexUi.text(this,"M"+(i+1)+" · "+clean(k.title),11,CortexUi.MUTED);
            source.setMaxLines(2);
            source.setPadding(0,dp(5),0,dp(2));
            details.addView(source);
        }
        if(!r.grounded.sources.isEmpty()){
            TextView toggle=CortexUi.plain(this,"Sources · "+r.grounded.sources.size()+" ▾",11,CortexUi.MUTED);
            CortexUi.medium(toggle);
            toggle.setPadding(0,dp(12),0,dp(8));
            toggle.setOnClickListener(v->{
                boolean show=details.getVisibility()!=View.VISIBLE;
                details.setVisibility(show?View.VISIBLE:View.GONE);
                toggle.setText(show?"Sources · "+r.grounded.sources.size()+" ▴":"Sources · "+r.grounded.sources.size()+" ▾");
            });
            card.addView(toggle);
            card.addView(details);
        }
        if(!refined&&"your_data".equals(r.sourceMode)&&LocalModelManager.installed(this)&&!"failed".equals(r.provider)){
            TextView improve=CortexUi.action(this,"Improve wording",CortexUi.MUTED,false);
            improve.setOnClickListener(v->refine(q,improve));
            LinearLayout.LayoutParams improveParams=new LinearLayout.LayoutParams(-1,dp(40));
            improveParams.setMargins(0,dp(8),0,0);
            card.addView(improve,improveParams);
        }
        conversation.addView(card);
    }

    void addStructuredActions(LinearLayout card,LocalAskRouter.Result r){
        if(db==null||r.jobId<=0||"your_data".equals(r.sourceMode))return;
        ArrayList<BrainActionStore.Action> xs;
        try{xs=BrainActionStore.list(db,r.jobId);}catch(Throwable e){return;}
        if(xs.isEmpty())return;
        TextView head=CortexUi.plain(this,"Next actions",11,CortexUi.MUTED);
        CortexUi.medium(head);
        head.setPadding(0,dp(14),0,dp(7));
        card.addView(head);
        for(BrainActionStore.Action x:xs){
            String state=x.ready()?"Ready":"Needs details";
            TextView chip=CortexUi.action(this,actionIcon(x.type)+"  "+x.title+" · "+state,x.ready()?CortexUi.ACCENT:CortexUi.MUTED,false);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setOnClickListener(v->CortexActionDispatcher.preview(this,db,x));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));
            if(card.getChildCount()>0)p.setMargins(0,dp(6),0,0);
            card.addView(chip,p);
        }
    }

    String actionIcon(String type){
        if("CALENDAR_EVENT".equals(type)||"CALENDAR_RESCHEDULE".equals(type))return"▣";
        if("REMINDER".equals(type))return"◉";
        if("CALL".equals(type))return"☎";
        if("MESSAGE_DRAFT".equals(type)||"EMAIL_DRAFT".equals(type))return"✉";
        if("WAIT_FOR".equals(type))return"◷";
        if("WEB_SEARCH".equals(type))return"⌕";
        if("PROJECT_LINK".equals(type))return"◇";
        return"✓";
    }

    String modeLabel(String mode){
        if("external".equals(mode))return"External";
        if("combined".equals(mode))return"Combined";
        return"Your data";
    }

    String friendlyType(String t){
        if("IMAGE".equals(t)||"SCREENSHOT".equals(t))return"Image";
        if("AUDIO".equals(t))return"Voice";
        if("FILE".equals(t))return"File";
        return"Memory";
    }

    String friendlyMs(long ms){return ms<1000?ms+" ms":String.format(Locale.US,"%.1f s",ms/1000f);}
    String clean(String s){if(empty(s))return"Memory";String x=s.trim();return x.length()>76?x.substring(0,76)+"…":x;}
    boolean empty(String s){return s==null||s.trim().isEmpty();}
    void scrollEnd(){if(scroll!=null&&!destroyed)scroll.postDelayed(()->{if(!destroyed)scroll.fullScroll(View.FOCUS_DOWN);},60);}
    void hideKeyboard(){try{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}catch(Throwable ignored){}}
}
