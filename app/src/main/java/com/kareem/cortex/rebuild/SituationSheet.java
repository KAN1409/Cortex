package com.kareem.cortex.rebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Bottom-sheet interaction surface for a grounded Situation. */
public final class SituationSheet {
    private static final int BG=Color.rgb(8,10,8),SURFACE=Color.rgb(24,28,24),SURFACE2=Color.rgb(31,36,31),BORDER=Color.rgb(55,62,55);
    private static final int TEXT=Color.rgb(244,246,242),MUTED=Color.rgb(164,171,163),FAINT=Color.rgb(112,120,112),BRAND=Color.rgb(143,226,67),BLUE=Color.rgb(75,158,255),AMBER=Color.rgb(238,174,60),RED=Color.rgb(235,92,92);
    private SituationSheet(){}

    public static void show(Activity activity,CortexDb db,CortexDb.Row row,Runnable refresh){
        SituationActions.ensure(db);
        Dialog dialog=new Dialog(activity);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);
        LinearLayout sheet=new LinearLayout(activity);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(activity,20),dp(activity,14),dp(activity,20),dp(activity,24));sheet.setBackground(round(activity,SURFACE,BORDER,28,1));scroll.addView(sheet,new ScrollView.LayoutParams(-1,-2));

        LinearLayout head=new LinearLayout(activity);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(activity,row.title,22,TEXT,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView close=text(activity,"×",28,MUTED,false);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->dialog.dismiss());head.addView(close,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,44)));sheet.addView(head);
        if(!row.body.isEmpty()){TextView summary=text(activity,row.body,14,MUTED,false);summary.setLineSpacing(0,1.16f);summary.setPadding(0,dp(activity,5),0,0);linkify(summary);sheet.addView(summary);}
        TextView attention=text(activity,row.type.replace('_',' ').toUpperCase(Locale.ROOT),10,BRAND,true);attention.setPadding(0,dp(activity,9),0,dp(activity,8));sheet.addView(attention);

        LinearLayout quick=new LinearLayout(activity);quick.setOrientation(LinearLayout.HORIZONTAL);addQuick(activity,quick,"Done",BRAND,()->{SituationActions.done(db,row.id);dialog.dismiss();run(refresh);});addQuick(activity,quick,"Later",BLUE,()->later(activity,db,row.id,dialog,refresh));addQuick(activity,quick,"Edit",AMBER,()->editSituation(activity,db,row,dialog,refresh));addQuick(activity,quick,"Delete",RED,()->deleteSituation(activity,db,row.id,dialog,refresh));sheet.addView(quick,new LinearLayout.LayoutParams(-1,dp(activity,50)));

        SituationActions.Source source=SituationActions.source(db,row.id);
        section(activity,sheet,"SOURCE / EVIDENCE",BLUE);
        if(source==null){sheet.addView(text(activity,"No linked source evidence is available.",13,FAINT,false));}
        else{
            String sourceText=source.displayText();TextView evidence=text(activity,sourceText.isEmpty()?"No text extracted from this source.":sourceText,14,TEXT,false);evidence.setLineSpacing(0,1.16f);evidence.setPadding(dp(activity,12),dp(activity,11),dp(activity,12),dp(activity,11));evidence.setBackground(round(activity,SURFACE2,BORDER,15,1));linkify(evidence);sheet.addView(evidence);
            String meta=(source.displayName.isEmpty()?source.kind:source.displayName)+(source.sourcePackage.isEmpty()?"":" · "+source.sourcePackage)+(source.protocol.isEmpty()?"":" · "+source.protocol);TextView m=text(activity,meta,10,FAINT,false);m.setPadding(0,dp(activity,6),0,0);sheet.addView(m);
            LinearLayout srcActions=new LinearLayout(activity);srcActions.setOrientation(LinearLayout.HORIZONTAL);srcActions.setPadding(0,dp(activity,8),0,0);
            if(source.isVoice()&&!source.path.isEmpty())addSource(activity,srcActions,"Listen original",()->playVoice(activity,source.path));
            if(!source.url.isEmpty())addSource(activity,srcActions,"Open original",()->openUrl(activity,source.url));
            if(!source.url.isEmpty())addSource(activity,srcActions,"Summarize",()->summarize(activity,source.url,sheet));
            if(srcActions.getChildCount()>0)sheet.addView(srcActions);
            if(source.isVoice()&&!source.transcript.isEmpty()){TextView editTranscript=wide(activity,"Edit transcription",SURFACE2,TEXT);editTranscript.setOnClickListener(v->editTranscript(activity,db,source,dialog,refresh));sheet.addView(editTranscript,top(activity,8));}
        }

        addContextualActions(activity,sheet,row);

        section(activity,sheet,"SITUATION OPTIONS",BRAND);
        addWide(sheet,wide(activity,"Ask Cortex about this",SURFACE2,TEXT),()->{Intent i=new Intent(activity,MainActivity.class);i.putExtra("tab","ask");i.putExtra("ask_query",row.title+"\n"+row.body);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);activity.startActivity(i);dialog.dismiss();});
        addWide(sheet,wide(activity,"Split / re-run Brain",SURFACE2,TEXT),()->rerun(activity,db,row,source,dialog,refresh));
        addWide(sheet,wide(activity,"Merge with…",SURFACE2,TEXT),()->merge(activity,db,row,dialog,refresh));
        addWide(sheet,wide(activity,"Change priority",SURFACE2,TEXT),()->priority(activity,db,row.id,dialog,refresh));
        addWide(sheet,wide(activity,"Dismiss from Now",SURFACE2,TEXT),()->{SituationActions.dismiss(db,row.id);dialog.dismiss();run(refresh);});
        addWide(sheet,wide(activity,"Keep in Memory",SURFACE2,TEXT),()->{boolean ok=SituationActions.keepInMemory(db,row.id);Toast.makeText(activity,ok?"Kept in Memory":"Could not create Memory",Toast.LENGTH_SHORT).show();});
        addWide(sheet,wide(activity,"Wrong / Cortex misunderstood",SURFACE2,AMBER),()->wrong(activity,db,row,source,dialog,refresh));
        if(source!=null)addWide(sheet,wide(activity,"Delete source capture",SURFACE2,RED),()->deleteCapture(activity,db,source.evidenceId,dialog,refresh));

        dialog.setContentView(scroll);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.65f;w.setAttributes(lp);w.setGravity(Gravity.BOTTOM);}dialog.show();if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,Math.round(activity.getResources().getDisplayMetrics().heightPixels*.88f));
    }

    private static void later(Activity a,CortexDb db,long id,Dialog d,Runnable refresh){
        String[] items={"30 minutes","1 hour","Tonight","Tomorrow morning","Pick date & time…"};
        new AlertDialog.Builder(a).setTitle("Bring this back later").setItems(items,(x,which)->{
            long now=System.currentTimeMillis();
            if(which==4){pickDateTime(a,when->{SituationActions.snooze(db,id,when);d.dismiss();run(refresh);});return;}
            long until;
            if(which==0)until=now+30*60_000L;
            else if(which==1)until=now+60*60_000L;
            else{Calendar c=Calendar.getInstance();if(which==2){c.set(Calendar.HOUR_OF_DAY,19);c.set(Calendar.MINUTE,0);if(c.getTimeInMillis()<=now)c.add(Calendar.DATE,1);}else{c.add(Calendar.DATE,1);c.set(Calendar.HOUR_OF_DAY,8);c.set(Calendar.MINUTE,0);}c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);until=c.getTimeInMillis();}
            SituationActions.snooze(db,id,until);d.dismiss();run(refresh);
        }).show();
    }

    private interface TimeChoice{void chosen(long when);}
    private static void pickDateTime(Activity a,TimeChoice callback){
        Calendar seed=Calendar.getInstance();
        new DatePickerDialog(a,(date,y,m,day)->{
            Calendar picked=Calendar.getInstance();picked.set(y,m,day,seed.get(Calendar.HOUR_OF_DAY),seed.get(Calendar.MINUTE),0);picked.set(Calendar.MILLISECOND,0);
            new TimePickerDialog(a,(time,hour,minute)->{picked.set(Calendar.HOUR_OF_DAY,hour);picked.set(Calendar.MINUTE,minute);if(picked.getTimeInMillis()<=System.currentTimeMillis()){Toast.makeText(a,"Choose a future time",Toast.LENGTH_SHORT).show();return;}callback.chosen(picked.getTimeInMillis());},seed.get(Calendar.HOUR_OF_DAY),seed.get(Calendar.MINUTE),false).show();
        },seed.get(Calendar.YEAR),seed.get(Calendar.MONTH),seed.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static void editSituation(Activity a,CortexDb db,CortexDb.Row row,Dialog d,Runnable refresh){LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(a,20),0,dp(a,20),0);EditText title=new EditText(a);title.setText(row.title);title.setHint("Title");EditText summary=new EditText(a);summary.setText(row.body);summary.setHint("Summary");summary.setMinLines(3);summary.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);box.addView(title);box.addView(summary);new AlertDialog.Builder(a).setTitle("Edit Situation").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(x,w)->{SituationActions.edit(db,row.id,title.getText().toString(),summary.getText().toString());d.dismiss();run(refresh);}).show();}

    private static void editTranscript(Activity a,CortexDb db,SituationActions.Source source,Dialog d,Runnable refresh){EditText e=new EditText(a);e.setText(source.transcript);e.setMinLines(5);e.setMaxLines(12);e.setGravity(Gravity.TOP);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);int pad=dp(a,14);e.setPadding(pad,pad,pad,pad);new AlertDialog.Builder(a).setTitle("Correct transcription").setMessage("Your edit becomes the authoritative transcript. Cortex will discard cognition derived only from the old transcript and run the Brain again.").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save & re-run Brain",(x,w)->{String corrected=e.getText().toString().trim();if(corrected.isEmpty())return;if(!SituationActions.editTranscriptAndReset(db,source.evidenceId,corrected)){Toast.makeText(a,"Could not update transcript",Toast.LENGTH_LONG).show();return;}d.dismiss();Toast.makeText(a,"Transcript corrected · Brain re-running",Toast.LENGTH_SHORT).show();BrainIntakeQueue.processVoice(a,source.evidenceId,(id,result,error)->{run(refresh);if(error!=null)Toast.makeText(a,"Brain retry pending: "+clip(error.getMessage(),100),Toast.LENGTH_LONG).show();});}).show();}

    private static void rerun(Activity a,CortexDb db,CortexDb.Row row,SituationActions.Source source,Dialog d,Runnable refresh){if(source==null||!source.isVoice()||source.transcript.isEmpty()){Toast.makeText(a,"Re-run currently requires grounded voice transcription",Toast.LENGTH_LONG).show();return;}if(!SituationActions.resetForReprocess(db,source.evidenceId)){Toast.makeText(a,"Could not reset this capture",Toast.LENGTH_LONG).show();return;}d.dismiss();Toast.makeText(a,"GPT-OSS is re-reading and splitting this capture",Toast.LENGTH_SHORT).show();BrainIntakeQueue.processVoice(a,source.evidenceId,(id,result,error)->{run(refresh);if(error!=null)Toast.makeText(a,"Brain retry pending",Toast.LENGTH_LONG).show();});}

    private static void merge(Activity a,CortexDb db,CortexDb.Row row,Dialog d,Runnable refresh){List<CortexDb.Row> candidates=SituationActions.mergeCandidates(db,row.id,16);if(candidates.isEmpty()){Toast.makeText(a,"No other active Situations to merge with",Toast.LENGTH_SHORT).show();return;}String[] names=new String[candidates.size()];for(int i=0;i<names.length;i++)names[i]=candidates.get(i).title;new AlertDialog.Builder(a).setTitle("Merge into…").setItems(names,(x,which)->{SituationActions.mergeInto(db,row.id,candidates.get(which).id);d.dismiss();run(refresh);}).show();}
    private static void priority(Activity a,CortexDb db,long id,Dialog d,Runnable refresh){String[] labels={"High · needs attention","Watch","Quiet"};String[] values={"needs_attention","watching","quiet"};new AlertDialog.Builder(a).setTitle("Situation priority").setItems(labels,(x,which)->{SituationActions.priority(db,id,values[which]);d.dismiss();run(refresh);}).show();}

    private static void wrong(Activity a,CortexDb db,CortexDb.Row row,SituationActions.Source source,Dialog d,Runnable refresh){
        String[] items={"Wrong transcription","Wrong person / time / details","Not a task","Already done","Duplicate","Other misunderstanding"};
        new AlertDialog.Builder(a).setTitle("What did Cortex get wrong?").setItems(items,(x,which)->{
            SituationActions.markMisunderstood(db,row.id);
            if(which==0){if(source!=null&&source.isVoice()&&!source.transcript.isEmpty())editTranscript(a,db,source,d,refresh);else Toast.makeText(a,"No editable voice transcript is linked",Toast.LENGTH_LONG).show();}
            else if(which==1)editSituation(a,db,row,d,refresh);
            else if(which==2){SituationActions.dismiss(db,row.id);d.dismiss();run(refresh);}
            else if(which==3){SituationActions.done(db,row.id);d.dismiss();run(refresh);}
            else if(which==4)merge(a,db,row,d,refresh);
            else Toast.makeText(a,"Marked as misunderstood",Toast.LENGTH_SHORT).show();
        }).show();
    }

    private static void addContextualActions(Activity a,LinearLayout sheet,CortexDb.Row row){
        String value=(row.title+" "+row.body).toLowerCase(Locale.ROOT);
        boolean email=value.contains("email")||value.contains("e-mail")||value.contains("إيميل")||value.contains("ايميل");
        boolean call=value.contains("call ")||value.startsWith("call")||value.contains("اتصل")||value.contains("مكالمة");
        boolean sms=value.contains("sms")||value.contains("text ")||value.contains("message")||value.contains("رسالة");
        if(!email&&!call&&!sms)return;
        section(a,sheet,"CONTEXTUAL ACTIONS",BLUE);
        if(email)addWide(sheet,wide(a,"Compose email",SURFACE2,TEXT),()->launchIntent(a,new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:")),"No email app is available"));
        if(call)addWide(sheet,wide(a,"Open dialer",SURFACE2,TEXT),()->launchIntent(a,new Intent(Intent.ACTION_DIAL),"No dialer is available"));
        if(sms)addWide(sheet,wide(a,"Compose SMS / message",SURFACE2,TEXT),()->launchIntent(a,new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")),"No messaging app is available"));
    }

    private static void launchIntent(Activity a,Intent intent,String error){try{a.startActivity(intent);}catch(Exception e){Toast.makeText(a,error,Toast.LENGTH_LONG).show();}}
    private static void deleteSituation(Activity a,CortexDb db,long id,Dialog d,Runnable refresh){new AlertDialog.Builder(a).setTitle("Delete Situation?").setMessage("This removes the Situation from Cortex Now but keeps its underlying evidence unless you separately delete the source capture.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,w)->{SituationActions.deleteSituation(db,id);d.dismiss();run(refresh);}).show();}
    private static void deleteCapture(Activity a,CortexDb db,long evidenceId,Dialog d,Runnable refresh){new AlertDialog.Builder(a).setTitle("Delete source capture?").setMessage("This permanently removes the source evidence and its transcript/attachment from Cortex. Derived state supported only by this capture is retired.").setNegativeButton("Cancel",null).setPositiveButton("Delete capture",(x,w)->{boolean ok=SituationActions.deleteEvidence(db,evidenceId);d.dismiss();run(refresh);Toast.makeText(a,ok?"Capture deleted":"Capture could not be deleted",Toast.LENGTH_SHORT).show();}).show();}

    private static void playVoice(Activity a,String path){try{File f=new File(path);if(!f.isFile()){Toast.makeText(a,"Original voice file is unavailable",Toast.LENGTH_LONG).show();return;}MediaPlayer p=new MediaPlayer();p.setDataSource(path);p.setOnCompletionListener(MediaPlayer::release);p.setOnErrorListener((mp,what,extra)->{mp.release();Toast.makeText(a,"Could not play original voice",Toast.LENGTH_LONG).show();return true;});p.prepare();p.start();Toast.makeText(a,"Playing original voice",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(a,"Could not play original voice",Toast.LENGTH_LONG).show();}}
    private static void openUrl(Activity a,String url){try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(a,"No app can open this URL",Toast.LENGTH_LONG).show();}}
    private static void summarize(Activity a,String url,LinearLayout sheet){TextView status=text(a,"Reading source and summarizing with GPT-OSS 120B…",13,AMBER,false);sheet.addView(status,top(a,8));WebSummaryEngine.summarize(a,url,(summary,error)->{if(error!=null){status.setTextColor(RED);status.setText("Summary failed: "+clip(error.getMessage(),220));}else{status.setTextColor(TEXT);status.setText(summary);linkify(status);}});}

    private static void addQuick(Activity a,LinearLayout row,String label,int color,Runnable action){TextView b=text(a,label,12,label.equals("Done")?BG:TEXT,true);b.setGravity(Gravity.CENTER);b.setBackground(round(a,label.equals("Done")?color:SURFACE2,label.equals("Done")?Color.TRANSPARENT:color,14,label.equals("Done")?0:1));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(a,3),0,dp(a,3),0);row.addView(b,p);}
    private static void addSource(Activity a,LinearLayout row,String label,Runnable action){TextView b=text(a,label,12,TEXT,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(a,7),0,dp(a,7),0);b.setBackground(round(a,SURFACE2,BORDER,12,1));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,44),1);p.setMargins(dp(a,3),0,dp(a,3),0);row.addView(b,p);}
    private static void section(Activity a,LinearLayout sheet,String label,int color){TextView t=text(a,label,10,color,true);t.setLetterSpacing(.12f);t.setPadding(0,dp(a,18),0,dp(a,7));sheet.addView(t);}private static TextView wide(Activity a,String label,int fill,int color){TextView t=text(a,label,13,color,true);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(a,14),0,dp(a,14),0);t.setBackground(round(a,fill,BORDER,14,1));return t;}private static void addWide(LinearLayout sheet,TextView v,Runnable action){v.setOnClickListener(x->action.run());sheet.addView(v,top((Activity)sheet.getContext(),7));}
    private static LinearLayout.LayoutParams top(Activity a,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,48));p.setMargins(0,dp(a,top),0,0);return p;}private static TextView text(Activity a,String value,int sp,int color,boolean bold){TextView t=new TextView(a);t.setText(value);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.create("sans",Typeface.BOLD));return t;}private static void linkify(TextView t){Linkify.addLinks(t,Linkify.WEB_URLS);t.setLinksClickable(true);t.setMovementMethod(LinkMovementMethod.getInstance());}private static GradientDrawable round(Activity a,int fill,int stroke,int radius,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(a,radius));if(width>0)d.setStroke(dp(a,width),stroke);return d;}private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n)+"…";}private static void run(Runnable r){if(r!=null)r.run();}
}
