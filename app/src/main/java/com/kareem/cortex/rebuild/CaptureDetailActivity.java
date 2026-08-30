package com.kareem.cortex.rebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** User-facing provenance surface for a captured photo. */
public final class CaptureDetailActivity extends Activity {
    private static final int BG=Color.rgb(8,10,8),SURFACE=Color.rgb(24,28,24),SURFACE2=Color.rgb(31,36,31),BORDER=Color.rgb(55,62,55),TEXT=Color.rgb(244,246,242),MUTED=Color.rgb(164,171,163),FAINT=Color.rgb(112,120,112),BRAND=Color.rgb(143,226,67),BLUE=Color.rgb(75,158,255),AMBER=Color.rgb(238,174,60),RED=Color.rgb(235,92,92);
    private CortexDb db;private long evidenceId;private LinearLayout page;

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);db=new CortexDb(this);evidenceId=getIntent().getLongExtra("evidence_id",0);build();render();}
    @Override protected void onDestroy(){try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(18),dp(20),dp(32));scroll.addView(page,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);}
    private void render(){page.removeAllViews();CaptureRecordStore.Record r=CaptureRecordStore.get(db,evidenceId);if(r==null){page.addView(text("Capture not found",24,TEXT,true));return;}
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("‹",34,MUTED,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView h=text("Photo capture",22,TEXT,true);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));page.addView(top);
        TextView meta=text((r.displayName.isEmpty()?"Photo":r.displayName)+" · "+new SimpleDateFormat("dd MMM · h:mm a",Locale.getDefault()).format(new Date(r.occurredAt)),11,FAINT,false);meta.setPadding(0,dp(10),0,dp(12));page.addView(meta);
        if(r.isImage()&&!r.path.isEmpty()){ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);Bitmap b=sample(r.path,1200);if(b!=null)preview.setImageBitmap(b);preview.setBackground(round(SURFACE2,BORDER,18,1));preview.setOnClickListener(v->showOriginal(r.path));page.addView(preview,new LinearLayout.LayoutParams(-1,dp(240)));}
        TextView status=text("STATE · "+r.state.toUpperCase(Locale.ROOT),10,stateColor(r.state),true);status.setPadding(0,dp(12),0,0);page.addView(status);
        String summary=CaptureRecordStore.visionSummary(r),ocr=CaptureRecordStore.extractedText(r);
        section("VISION DESCRIPTION",BLUE);cardText(summary.isEmpty()?visionPlaceholder(r):summary);
        section("EXTRACTED TEXT",BRAND);cardText(ocr.isEmpty()?"No readable text extracted yet.":ocr);
        BrainStore.BrainOutcome outcome=BrainStore.outcome(db,evidenceId);if(outcome!=null){section("CORTEX BRAIN",AMBER);String s=outcome.status.isEmpty()?r.state:outcome.status;String body="Status: "+s+(outcome.reason.isEmpty()?"":"\n"+outcome.reason)+(outcome.error.isEmpty()?"":"\n"+outcome.error);cardText(body);}
        section("ACTIONS",BRAND);
        addAction("View original photo",TEXT,()->showOriginal(r.path));
        addAction("Edit extracted text",TEXT,()->editText(r));
        addAction("Re-analyze photo",TEXT,()->reanalyze());
        addAction("Think with ChatGPT",BRAND,()->askChatGpt(r));
        addAction("Delete capture",RED,this::deleteCapture);
    }

    private String visionPlaceholder(CaptureRecordStore.Record r){if("vision_analyzing".equals(r.state))return"Analyzing photo…";if("vision_failed".equals(r.state)||"brain_failed".equals(r.state))return"Photo is preserved. Vision analysis can be retried.";return"This photo has not been analyzed yet.";}
    private void editText(CaptureRecordStore.Record r){EditText e=new EditText(this);e.setText(CaptureRecordStore.extractedText(r));e.setMinLines(6);e.setGravity(Gravity.TOP);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);new AlertDialog.Builder(this).setTitle("Correct extracted text").setMessage("Your edit becomes authoritative visible text for this capture. Cortex will retire state derived only from the old text and run the Brain again.").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save & re-run Brain",(d,w)->{try{if(!CaptureRecordStore.editExtractedText(db,evidenceId,e.getText().toString()))return;Toast.makeText(this,"Text corrected · Cortex Brain re-running",Toast.LENGTH_SHORT).show();render();ImageCapturePipeline.rebrainStored(this,evidenceId,(id,out)->{render();if(out.brainError!=null)Toast.makeText(this,"Photo saved · Brain retry available",Toast.LENGTH_LONG).show();});}catch(Exception ex){Toast.makeText(this,"Could not save correction",Toast.LENGTH_LONG).show();}}).show();}
    private void reanalyze(){Toast.makeText(this,"Re-analyzing original photo…",Toast.LENGTH_SHORT).show();ImageCapturePipeline.reanalyze(this,evidenceId,(id,out)->{render();if(out.visionError!=null)Toast.makeText(this,"Vision retry failed: "+clip(out.visionError.getMessage(),120),Toast.LENGTH_LONG).show();else Toast.makeText(this,out.brainSucceeded()?"Photo re-analyzed · Cortex updated":"Photo re-analyzed · Brain retry available",Toast.LENGTH_LONG).show();});}
    private void askChatGpt(CaptureRecordStore.Record r){String body=CaptureRecordStore.visionSummary(r);String ocr=CaptureRecordStore.extractedText(r);if(!ocr.isEmpty())body+=(body.isEmpty()?"":"\n\n")+"Visible text:\n"+ocr;CortexDb.Row row=new CortexDb.Row(r.id,r.displayName.isEmpty()?"Photo capture":r.displayName,body,r.occurredAt,"EVIDENCE");ChatGptHandoff.showForObject(this,db,row,"PHOTO");}
    private void deleteCapture(){new AlertDialog.Builder(this).setTitle("Delete this capture?").setMessage("The original photo, extracted text and Cortex state supported only by this capture will be removed.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{if(CaptureRecordStore.deleteCapture(db,evidenceId)){Toast.makeText(this,"Capture deleted",Toast.LENGTH_SHORT).show();finish();}}).show();}

    private void showOriginal(String path){if(path==null||path.isEmpty()||!new File(path).isFile()){Toast.makeText(this,"Original photo is unavailable",Toast.LENGTH_LONG).show();return;}Dialog d=new Dialog(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(12),dp(12),dp(12));root.setBackgroundColor(BG);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);Bitmap b=sample(path,2400);if(b!=null)image.setImageBitmap(b);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));TextView close=text("Close",14,BG,true);close.setGravity(Gravity.CENTER);close.setBackground(round(BRAND,Color.TRANSPARENT,14,0));close.setOnClickListener(v->d.dismiss());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(50));cp.setMargins(0,dp(8),0,0);root.addView(close,cp);d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}d.show();if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}
    private Bitmap sample(String path,int max){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);int s=1;while(Math.max(o.outWidth/s,o.outHeight/s)>max)s*=2;BitmapFactory.Options x=new BitmapFactory.Options();x.inSampleSize=Math.max(1,s);return BitmapFactory.decodeFile(path,x);}
    private void section(String label,int color){TextView t=text(label,10,color,true);t.setLetterSpacing(.12f);t.setPadding(0,dp(20),0,dp(7));page.addView(t);}private void cardText(String value){TextView t=text(value,14,MUTED,false);t.setLineSpacing(0,1.15f);t.setPadding(dp(14),dp(13),dp(14),dp(13));t.setBackground(round(SURFACE,BORDER,16,1));page.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private void addAction(String label,int color,Runnable action){TextView t=text(label,14,color,true);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(14),0,dp(14),0);t.setBackground(round(SURFACE2,BORDER,14,1));t.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,0);page.addView(t,p);}private int stateColor(String s){return s.contains("failed")?AMBER:s.contains("analy")||s.contains("brain_")?BRAND:FAINT;}
    private TextView text(String v,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(v);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.create("sans",Typeface.BOLD));return t;}private GradientDrawable round(int fill,int stroke,int radius,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(width>0)d.setStroke(dp(width),stroke);return d;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
