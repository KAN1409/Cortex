package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;

/** Premium capture composer: neutral graphite surfaces with localized lime/amber signal color. */
public final class ProposalCaptureActivity extends SatinCaptureActivity {
    private static final int REQ_CAMERA_CAPTURE=774;
    private LinearLayout photoPanel;
    private Uri pendingCameraUri;

    @Override void build(){
        Window w=getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.72f;w.setAttributes(lp);
        root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setOnClickListener(v->finish());sheet=CortexUi.card(this,28);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(16),dp(14),dp(16),dp(18));sheet.setOnClickListener(v->{});CortexUi.raised(this,sheet,9);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.LIME,Color.TRANSPARENT,999));head.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));TextView c=CortexUi.plain(this,"C O R T E X",13,CortexUi.TEXT);CortexUi.bold(c);if(android.os.Build.VERSION.SDK_INT>=21)c.setLetterSpacing(.18f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.setMargins(dp(11),0,0,0);head.addView(c,cp);View d=CortexUi.divider(this);LinearLayout.LayoutParams dv=new LinearLayout.LayoutParams(dp(1),dp(26));dv.setMargins(dp(11),0,dp(11),0);head.addView(d,dv);TextView mode=CortexUi.plain(this,"CAPTURE",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)mode.setLetterSpacing(.09f);head.addView(mode,new LinearLayout.LayoutParams(0,dp(38),1));TextView close=CortexUi.chip(this,"CLOSE",CortexUi.MUTED,false);close.setOnClickListener(v->finish());head.addView(close,new LinearLayout.LayoutParams(-2,dp(34)));sheet.addView(head);
        importState=CortexUi.plain(this,"Importing safely…",10,CortexUi.YELLOW);CortexUi.medium(importState);importState.setPadding(dp(2),dp(7),0,dp(5));importState.setVisibility(View.GONE);sheet.addView(importState);
        choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(0,dp(12),0,0);LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);captureTile(top,"Voice","Speak naturally","wave",CortexUi.LIME,this::startVoice,0);captureTile(top,"Text","Type or paste","text",CortexUi.TEXT,this::quickNote,8);choices.addView(top,new LinearLayout.LayoutParams(-1,dp(116)));LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);captureTile(bottom,"File","Import evidence","file",CortexUi.TEXT,this::pickFile,0);captureTile(bottom,"Photo","Visual evidence","photo",CortexUi.YELLOW,this::pickPhoto,8);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(116));bp.setMargins(0,dp(8),0,0);choices.addView(bottom,bp);sheet.addView(choices);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sp.setMargins(dp(12),0,dp(12),dp(12));root.addView(sheet,sp);setContentView(root);applyInsets();
        if(CortexMotion.allowed(this)){sheet.setTranslationY(dp(36));sheet.setAlpha(0f);sheet.animate().translationY(0).alpha(1f).setDuration(320).start();CortexMotion.enter(top,1);CortexMotion.enter(bottom,2);}CortexMotion.breathe(dot);
    }

    void captureTile(LinearLayout row,String title,String sub,String icon,int color,Runnable action,int left){LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(12),dp(10),dp(12),dp(10));CortexUi.pressable(this,tile,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,20));tile.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(42),dp(42)));TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);t.setPadding(0,dp(7),0,0);tile.addView(t);TextView s=CortexUi.plain(this,sub,10,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);tile.addView(s);tile.setOnClickListener(v->{CortexMotion.haptic(v,false);action.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);}

    @Override void pickPhoto(){
        if(photoPanel!=null)return;
        if(choices!=null)choices.setVisibility(View.GONE);
        photoPanel=new LinearLayout(this);photoPanel.setOrientation(LinearLayout.VERTICAL);photoPanel.setPadding(0,dp(14),0,0);
        TextView title=CortexUi.plain(this,"Add an image",22,CortexUi.TEXT);CortexUi.medium(title);photoPanel.addView(title);
        TextView sub=CortexUi.text(this,"Take a new photo or choose an existing image. Cortex keeps the original as evidence before understanding it.",11,CortexUi.MUTED);sub.setPadding(0,dp(5),0,dp(12));photoPanel.addView(sub);
        photoChoice("Open camera","Take a new photo","camera",CortexUi.YELLOW,this::openCamera);
        photoChoice("Choose from gallery","Select an existing image","photo",CortexUi.TEXT,this::openGallery);
        TextView cancel=CortexUi.action(this,"Back",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(10),0,0);photoPanel.addView(cancel,cp);cancel.setOnClickListener(v->closePhotoPanel());
        sheet.addView(photoPanel);
        if(CortexMotion.allowed(this))CortexMotion.enter(photoPanel,1);
    }

    private void photoChoice(String title,String sub,String icon,int color,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));CortexUi.pressable(this,row,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,18));row.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(12),0,0,0);TextView h=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(h);text.addView(h);TextView b=CortexUi.plain(this,sub,10,CortexUi.MUTED);b.setPadding(0,dp(3),0,0);text.addView(b);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=CortexUi.plain(this,"›",25,color);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(42)));row.setOnClickListener(v->{CortexMotion.haptic(v,false);action.run();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(76));if(photoPanel.getChildCount()>2)rp.setMargins(0,dp(8),0,0);photoPanel.addView(row,rp);
    }

    private void openGallery(){super.pickPhoto();}

    private void openCamera(){
        Uri uri=null;
        try{
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"Cortex_"+System.currentTimeMillis()+".jpg");values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(Build.VERSION.SDK_INT>=29)values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Cortex");
            uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IllegalStateException("Could not create camera destination");
            Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);if(camera.resolveActivity(getPackageManager())==null)throw new IllegalStateException("No camera app available");camera.putExtra(MediaStore.EXTRA_OUTPUT,uri);camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);pendingCameraUri=uri;startActivityForResult(camera,REQ_CAMERA_CAPTURE);
        }catch(Throwable e){if(uri!=null)try{getContentResolver().delete(uri,null,null);}catch(Throwable ignored){}pendingCameraUri=null;Toast.makeText(this,"Camera could not be opened",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        if(req==REQ_CAMERA_CAPTURE){Uri uri=pendingCameraUri;pendingCameraUri=null;if(result==RESULT_OK&&uri!=null){removePhotoPanelForImport();importUriAsync(uri,"image/jpeg",REQ_PHOTO);}else if(uri!=null){try{getContentResolver().delete(uri,null,null);}catch(Throwable ignored){}}return;}
        if(req==REQ_PHOTO&&result==RESULT_OK)removePhotoPanelForImport();
        super.onActivityResult(req,result,data);
    }

    private void removePhotoPanelForImport(){if(photoPanel!=null){try{sheet.removeView(photoPanel);}catch(Throwable ignored){}photoPanel=null;}}
    private void closePhotoPanel(){removePhotoPanelForImport();if(choices!=null)choices.setVisibility(View.VISIBLE);}

    @Override void showResult(long id){try{NexusScheduler.kick(this);}catch(Throwable ignored){}try{Intent i=new Intent(this,ProposalCaptureResultActivity.class);i.putExtra("item_id",id);startActivity(i);finish();}catch(Throwable e){Toast.makeText(this,"Captured successfully. Open Brief to see it.",Toast.LENGTH_LONG).show();try{startActivity(new Intent(this,PremiumHomeActivity.class));}catch(Throwable ignored){}finish();}}
}
