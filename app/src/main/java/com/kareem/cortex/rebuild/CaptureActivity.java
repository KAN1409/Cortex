package com.kareem.cortex.rebuild;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fresh capture sheet, deliberately reusing the proven Cortex capture architecture. */
public final class CaptureActivity extends Activity {
    private static final int REQ_MIC=771, REQ_FILE=772, REQ_PHOTO=773;
    private static final int BG=Color.rgb(8,10,8), SURFACE=Color.rgb(24,28,24), SURFACE2=Color.rgb(31,36,31);
    private static final int BORDER=Color.rgb(55,62,55), TEXT=Color.rgb(244,246,242), MUTED=Color.rgb(164,171,163), BRAND=Color.rgb(143,226,67), AMBER=Color.rgb(238,174,60);

    private final AudioCapture recorder = new AudioCapture();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r,"cortex-capture-io"));
    private CortexDb db;
    private LinearLayout sheet, choices, recordPanel;
    private TextView state, timer;
    private long recordingStarted;
    private Runnable tick;
    private volatile boolean destroyed;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new CortexDb(this);
        build();
        handleIncoming(getIntent());
        handler.postDelayed(() -> handleMode(getIntent()), 120);
    }

    @Override protected void onDestroy() {
        destroyed=true;
        if(tick!=null)handler.removeCallbacks(tick);
        if(recorder.isRunning()) try { File f=recorder.stop(); if(f!=null)f.delete(); } catch(Throwable ignored){}
        try{db.close();}catch(Throwable ignored){}
        io.shutdown();
        super.onDestroy();
    }

    private void build() {
        Window w=getWindow();
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.6f;w.setAttributes(lp);

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setOnClickListener(v->finish());
        sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(20),dp(16),dp(20),dp(20));sheet.setBackground(round(SURFACE,BORDER,28,1));sheet.setOnClickListener(v->{});
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Capture",24,TEXT,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=text("×",28,MUTED,false);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->finish());head.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));sheet.addView(head);
        state=text("",12,MUTED,false);state.setPadding(0,dp(6),0,0);state.setVisibility(View.GONE);sheet.addView(state);

        choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(0,dp(14),0,0);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);addTile(top,"Voice",this::startVoice,0);addTile(top,"Text",this::quickNote,8);choices.addView(top,new LinearLayout.LayoutParams(-1,dp(92)));
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);addTile(bottom,"File",this::pickFile,0);addTile(bottom,"Photo",this::pickPhoto,8);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(92));bp.setMargins(0,dp(8),0,0);choices.addView(bottom,bp);sheet.addView(choices);

        TextView asr=text("Voice: Gemini 3.6 Flash primary · Groq Whisper fallback",10,MUTED,false);asr.setPadding(0,dp(14),0,0);asr.setOnClickListener(v->startActivity(new Intent(this,AsrSettingsActivity.class)));sheet.addView(asr);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sp.setMargins(dp(12),0,dp(12),dp(16));root.addView(sheet,sp);setContentView(root);
    }

    private void addTile(LinearLayout row,String label,Runnable action,int left){TextView tile=text(label,16,TEXT,true);tile.setGravity(Gravity.CENTER);tile.setBackground(round(SURFACE2,Color.TRANSPARENT,20,0));tile.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);}

    private void handleMode(Intent i){if(i==null)return;String mode=i.getStringExtra("mode");if(mode==null)return;i.removeExtra("mode");if("voice".equals(mode))startVoice();else if("text".equals(mode))quickNote();else if("photo".equals(mode))pickPhoto();else if("file".equals(mode))pickFile();}
    private void handleIncoming(Intent i){if(i==null)return;String a=i.getAction();if(Intent.ACTION_SEND.equals(a))importSend(i);else if(Intent.ACTION_SEND_MULTIPLE.equals(a))importMultiple(i);}

    private void quickNote(){
        choices.setVisibility(View.GONE);
        EditText e=new EditText(this);e.setHint("Thought, note, reminder, idea…");e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setTextSize(15);e.setGravity(Gravity.TOP);e.setMinLines(4);e.setMaxLines(8);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(round(SURFACE2,BORDER,18,1));sheet.addView(e,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(12),0,0);TextView cancel=action("Cancel",false),save=action("Save to Memory",true);actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(48),1);sp.setMargins(dp(8),0,0,0);actions.addView(save,sp);sheet.addView(actions);
        cancel.setOnClickListener(v->{sheet.removeView(e);sheet.removeView(actions);choices.setVisibility(View.VISIBLE);});
        save.setOnClickListener(v->{String s=e.getText().toString().trim();if(s.isEmpty()){e.setError("Write something first");return;}if(db.capture(s)>0){Toast.makeText(this,"Saved to Memory",Toast.LENGTH_SHORT).show();route("memory");}});
        e.requestFocus();
    }

    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_FILE);}
    private void pickPhoto(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_PHOTO);}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();String mime="";try{mime=getContentResolver().getType(uri);}catch(Throwable ignored){}importUri(uri,mime,req==REQ_PHOTO);}

    private void importUri(Uri uri,String mime,boolean forceImage){setBusy("Importing safely…");io.execute(()->{long id=0;Exception error=null;try{id=copyAndStore(uri,mime,forceImage);}catch(Exception e){error=e;}final long evidenceId=id;final Exception failure=error;runOnUiThread(()->{if(destroyed)return;if(evidenceId>0){CortexDb.AttachmentEvidence ev=db.attachmentEvidence(evidenceId);if(ev!=null&&ev.kind.startsWith("AUDIO")){setBusy("Transcribing Egyptian Arabic + English…");CaptureAnalysisQueue.analyzeVoice(this,evidenceId,this::voiceDone);}else{Toast.makeText(this,"Captured as evidence",Toast.LENGTH_SHORT).show();route("memory");}}else{setReady();Toast.makeText(this,failure==null?"Could not import":"Import failed: "+failure.getMessage(),Toast.LENGTH_LONG).show();}});});}

    private long copyAndStore(Uri uri,String mime,boolean forceImage)throws Exception{
        String name=displayName(uri,forceImage?"image":"file");String effective=mime==null?"":mime;boolean audio=effective.startsWith("audio/");boolean image=forceImage||effective.startsWith("image/");String folder=audio?"audio":"imports";File dir=new File(getFilesDir(),folder);if(!dir.exists()&&!dir.mkdirs())throw new Exception("Could not create capture directory");String safe=name.replaceAll("[^A-Za-z0-9._-]","_");if(safe.length()>80)safe=safe.substring(safe.length()-80);File out=new File(dir,UUID.randomUUID()+"_"+safe);try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream os=new FileOutputStream(out)){if(in==null)throw new Exception("Could not open selected item");byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)os.write(b,0,n);}JSONObject meta=new JSONObject();meta.put("mime",effective);meta.put("name",name);meta.put("bytes",out.length());meta.put("imported_at",System.currentTimeMillis());if(image){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(out.getAbsolutePath(),o);meta.put("width",o.outWidth);meta.put("height",o.outHeight);}String kind=audio?"AUDIO_IMPORT":image?"IMAGE":"FILE";String body=audio?"Voice/audio file pending transcription":name;String state=audio?"pending_transcription":"observed";return db.captureAttachment(kind,name,effective,out.getAbsolutePath(),body,meta.toString(),state);
    }

    private void importSend(Intent i){Uri uri=i.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null){importUri(uri,i.getType(),i.getType()!=null&&i.getType().startsWith("image/"));return;}String text=i.getStringExtra(Intent.EXTRA_TEXT);if(text!=null&&!text.trim().isEmpty()){db.capture(text);Toast.makeText(this,"Shared text saved to Memory",Toast.LENGTH_SHORT).show();route("memory");}}
    private void importMultiple(Intent i){ArrayList<Uri> uris=i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(uris==null||uris.isEmpty())return;setBusy("Importing shared files…");io.execute(()->{int count=0;for(Uri u:uris)try{if(copyAndStore(u,i.getType(),i.getType()!=null&&i.getType().startsWith("image/"))>0)count++;}catch(Exception ignored){}final int n=count;runOnUiThread(()->{Toast.makeText(this,n+" items captured",Toast.LENGTH_SHORT).show();route("memory");});});}

    private void startVoice(){
        if(!GeminiKeyStore.has(this)&&!GroqKeyStore.has(this)){new AlertDialog.Builder(this).setTitle("Voice transcription setup").setMessage("This rebuild uses the same Cortex ASR architecture: Gemini 3.6 Flash primary and Groq Whisper Large v3 fallback. Configure at least one provider once for this fresh app.").setNegativeButton("Cancel",null).setPositiveButton("Set up",(d,w)->startActivity(new Intent(this,AsrSettingsActivity.class))).show();return;}
        if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}beginVoice();
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==REQ_MIC&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)beginVoice();else if(req==REQ_MIC)Toast.makeText(this,"Microphone permission is required",Toast.LENGTH_LONG).show();}

    private void beginVoice(){try{recorder.start(this);recordingStarted=System.currentTimeMillis();showRecordingPanel();}catch(Throwable e){Toast.makeText(this,"Could not start recording",Toast.LENGTH_LONG).show();}}
    private void showRecordingPanel(){choices.setVisibility(View.GONE);recordPanel=new LinearLayout(this);recordPanel.setOrientation(LinearLayout.VERTICAL);recordPanel.setPadding(0,dp(14),0,0);timer=text("00:00",38,TEXT,true);timer.setGravity(Gravity.CENTER);recordPanel.addView(timer);TextView mode=text("16 kHz mono WAV · verbatim Egyptian Arabic + English",11,MUTED,false);mode.setGravity(Gravity.CENTER);mode.setPadding(0,dp(4),0,0);recordPanel.addView(mode);LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(14),0,0);TextView discard=action("Discard",false),save=action("Stop & transcribe",true);actions.addView(discard,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(48),1);sp.setMargins(dp(8),0,0,0);actions.addView(save,sp);recordPanel.addView(actions);sheet.addView(recordPanel);discard.setOnClickListener(v->finishVoice(false));save.setOnClickListener(v->finishVoice(true));tick=new Runnable(){public void run(){long sec=(System.currentTimeMillis()-recordingStarted)/1000;if(timer!=null)timer.setText(String.format(Locale.US,"%02d:%02d",sec/60,sec%60));if(recorder.isRunning())handler.postDelayed(this,500);}};handler.post(tick);}

    private void finishVoice(boolean save){try{File f=recorder.stop();if(tick!=null)handler.removeCallbacks(tick);if(f==null)return;if(!save){f.delete();finish();return;}if(recordPanel!=null)recordPanel.setVisibility(View.GONE);JSONObject meta=new JSONObject();meta.put("mime","audio/wav");meta.put("bytes",f.length());meta.put("recorded_at",System.currentTimeMillis());long id=db.captureAttachment("AUDIO","Voice recording","audio/wav",f.getAbsolutePath(),"Voice recording · transcription queued",meta.toString(),"pending_transcription");setBusy("Transcribing Egyptian Arabic + English…");CaptureAnalysisQueue.analyzeVoice(this,id,this::voiceDone);}catch(Throwable e){Toast.makeText(this,"Could not save recording",Toast.LENGTH_LONG).show();}}

    private void voiceDone(long evidenceId,CaptureAnalysisQueue.Outcome outcome){
        if(destroyed)return;
        if(outcome==null||!outcome.transcriptionSucceeded()){
            setReady();state.setVisibility(View.VISIBLE);state.setTextColor(AMBER);Exception e=outcome==null?null:outcome.transcriptionError;String m=e==null?"unknown error":e.getMessage();state.setText("Recording is safe, but transcription failed.\n"+clip(m,260));return;
        }
        state.setVisibility(View.VISIBLE);state.setTextColor(TEXT);
        if(outcome.brainSucceeded()){
            String destination=outcome.brainResult.destination();
            state.setText("Cortex brain → "+destination+"\n\n"+clip(outcome.transcript.text,760)+(outcome.brainResult.reason.isEmpty()?"":"\n\n"+clip(outcome.brainResult.reason,260)));
            Toast.makeText(this,"Cortex → "+destination,Toast.LENGTH_SHORT).show();
            handler.postDelayed(()->route(tabFor(outcome.brainResult)),2200);
        }else{
            Exception e=outcome.brainError;
            String m=e==null?"Brain decision unavailable":e.getMessage();
            state.setTextColor(AMBER);
            state.setText("Transcript saved. Cortex brain retry pending.\n\n"+clip(outcome.transcript.text,650)+"\n\n"+clip(m,260));
            Toast.makeText(this,"Transcript safe · brain retry pending",Toast.LENGTH_LONG).show();
        }
    }

    private String tabFor(BrainStore.ApplyResult result){if(result==null)return"memory";if(result.situationId>0)return"now";if(result.memoryId>0)return"memory";if(result.entityIds!=null&&!result.entityIds.isEmpty())return"world";return"memory";}
    private void route(String tab){Intent i=new Intent(this,MainActivity.class);i.putExtra("tab",tab);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);finish();}

    private void setBusy(String message){choices.setVisibility(View.GONE);state.setVisibility(View.VISIBLE);state.setTextColor(BRAND);state.setText(message);}
    private void setReady(){choices.setVisibility(View.VISIBLE);state.setVisibility(View.GONE);}
    private String displayName(Uri uri,String fallback){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String x=c.getString(0);if(x!=null&&!x.trim().isEmpty())return x;}}catch(Exception ignored){}return fallback+"_"+System.currentTimeMillis();}
    private TextView action(String label,boolean primary){TextView t=text(label,14,primary?BG:TEXT,true);t.setGravity(Gravity.CENTER);t.setBackground(round(primary?BRAND:SURFACE2,primary?Color.TRANSPARENT:BORDER,14,primary?0:1));return t;}
    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.create("sans",Typeface.BOLD));return t;}
    private GradientDrawable round(int fill,int stroke,int radius,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(width>0)d.setStroke(dp(width),stroke);return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
