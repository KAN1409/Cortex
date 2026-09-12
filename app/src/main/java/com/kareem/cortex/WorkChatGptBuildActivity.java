package com.kareem.cortex;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** User-controlled direct document builder: detailed requirements + reference files + grounded archive context. */
public final class WorkChatGptBuildActivity extends Activity {
    public static final String EXTRA_KIND="document_kind";
    private static final int REQ_REFERENCES=8611;

    private VaultDb db;
    private WorkDocumentRecipe.Kind kind=WorkDocumentRecipe.Kind.PROJECT_STATUS_REPORT;
    private EditText project;
    private EditText requirements;
    private TextView refsLabel;
    private final ArrayList<Uri> references=new ArrayList<>();

    int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());
        try{String raw=getIntent().getStringExtra(EXTRA_KIND);if(raw!=null)kind=WorkDocumentRecipe.Kind.valueOf(raw);}catch(Throwable ignored){}
        build();
    }

    @Override protected void onDestroy(){if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(18),dp(20),dp(28));scroll.addView(box);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));

        TextView title=CortexUi.plain(this,"Build with ChatGPT",28,CortexUi.TEXT);CortexUi.medium(title);box.addView(title);
        TextView type=CortexUi.text(this,WorkDocumentRecipe.displayName(kind),13,CortexUi.ACCENT);type.setPadding(0,dp(5),0,dp(16));box.addView(type);

        project=new EditText(this);project.setHint("Project name — optional");project.setSingleLine(true);project.setTextColor(CortexUi.TEXT);project.setHintTextColor(CortexUi.MUTED);box.addView(project,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView reqTitle=CortexUi.section(this,"What should ChatGPT create?");box.addView(reqTitle);
        requirements=new EditText(this);requirements.setHint("Required: describe the new document in detail — content, wording, layout, tables, sections, logo/header, language, exact model to follow, what to change from the reference, and what must stay the same.");requirements.setMinLines(7);requirements.setGravity(android.view.Gravity.TOP);requirements.setTextColor(CortexUi.TEXT);requirements.setHintTextColor(CortexUi.MUTED);requirements.setPadding(dp(14),dp(12),dp(14),dp(12));box.addView(requirements,new LinearLayout.LayoutParams(-1,dp(210)));

        TextView refsTitle=CortexUi.section(this,"Reference documents");box.addView(refsTitle);
        refsLabel=CortexUi.text(this,"No reference files selected yet. References are optional, but strongly recommended when ChatGPT should copy a model, structure or formatting style.",11,CortexUi.MUTED);refsLabel.setPadding(0,0,0,dp(10));box.addView(refsLabel);

        TextView choose=CortexUi.action(this,"SELECT REFERENCE FILES",CortexUi.ACCENT,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(48));box.addView(choose,cp);choose.setOnClickListener(v->chooseReferences());

        TextView send=CortexUi.action(this,"SEND DIRECTLY TO CHATGPT",CortexUi.ACCENT,true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));sp.setMargins(0,dp(12),0,0);box.addView(send,sp);send.setOnClickListener(v->sendToChatGpt(send,false));

        TextView note=CortexUi.text(this,"Cortex sends the selected reference files themselves, plus a structured JSON build package and your detailed requirements. ChatGPT is used as the document builder; the output remains a generated document, not new source evidence.",10,CortexUi.MUTED);note.setPadding(0,dp(14),0,0);box.addView(note);
        setContentView(root);
    }

    private void chooseReferences(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        String[] types={"application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/msword","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.presentationml.presentation","application/vnd.ms-powerpoint"};
        i.putExtra(Intent.EXTRA_MIME_TYPES,types);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_REFERENCES);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_REFERENCES||resultCode!=RESULT_OK||data==null)return;
        references.clear();ClipData clip=data.getClipData();if(clip!=null){for(int n=0;n<clip.getItemCount();n++)addReference(clip.getItemAt(n).getUri(),data.getFlags());}else if(data.getData()!=null)addReference(data.getData(),data.getFlags());updateRefsLabel();
    }

    private void addReference(Uri uri,int flags){
        if(uri==null||references.contains(uri))return;
        String mime=safeMime(uri);
        if(!WorkReferenceFilePolicy.isSupportedMime(mime)){
            android.widget.Toast.makeText(this,"Unsupported reference type: "+WorkReferenceFilePolicy.shortType(mime),android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        references.add(uri);
        int take=flags&Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try{getContentResolver().takePersistableUriPermission(uri,take);}catch(Throwable ignored){}
    }

    private void updateRefsLabel(){
        if(references.isEmpty()){
            refsLabel.setText("No reference files selected yet. References are optional, but strongly recommended when ChatGPT should copy a model, structure or formatting style.");return;
        }
        StringBuilder b=new StringBuilder();b.append(references.size()).append(" reference file").append(references.size()==1?"":"s").append(" selected:\n");
        for(int i=0;i<references.size();i++){
            RefMeta m=meta(references.get(i));
            b.append("• ").append(m.name).append("  •  ").append(WorkReferenceFilePolicy.shortType(m.mime)).append("  •  ").append(WorkReferenceFilePolicy.formatBytes(m.size));
            if(WorkReferenceFilePolicy.shouldWarnForSize(m.size))b.append("  ⚠ large file");
            if(i<references.size()-1)b.append("\n");
        }
        refsLabel.setText(b.toString());
    }

    private void sendToChatGpt(TextView button,boolean largeFilesConfirmed){
        String p=project.getText()==null?"":project.getText().toString().trim();String req=requirements.getText()==null?"":requirements.getText().toString().trim();
        if(req.isEmpty()){android.widget.Toast.makeText(this,"Write the new document requirements first",android.widget.Toast.LENGTH_LONG).show();requirements.requestFocus();return;}
        if(!largeFilesConfirmed&&hasLargeReference()){
            new AlertDialog.Builder(this)
                    .setTitle("Large reference file")
                    .setMessage("One or more selected references are larger than 50 MB. Cortex will not block them, but sharing may be slower or the receiving app may reject them. Continue?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Continue",(d,w)->sendToChatGpt(button,true))
                    .show();
            return;
        }
        button.setEnabled(false);button.setText("PREPARING PACKAGE…");ArrayList<Uri> refs=new ArrayList<>(references);
        new Thread(()->{try{
            WorkChatGptDirectBridge.Prepared prepared=WorkChatGptDirectBridge.prepare(this,db,kind,p,req,refs);
            runOnUiThread(()->{
                boolean ok=WorkChatGptDirectBridge.openChatGpt(this,prepared);
                try{WorkChatGptBuildRequestRegistry.markOpened(db.getWritableDatabase(),prepared.requestId,ok);}catch(Throwable ignored){}
                button.setEnabled(true);button.setText("SEND DIRECTLY TO CHATGPT");
                android.widget.Toast.makeText(this,ok?"Reference package sent to ChatGPT":"Could not open ChatGPT/share target",ok?android.widget.Toast.LENGTH_SHORT:android.widget.Toast.LENGTH_LONG).show();
            });
        }catch(Throwable e){runOnUiThread(()->{button.setEnabled(true);button.setText("SEND DIRECTLY TO CHATGPT");android.widget.Toast.makeText(this,"Could not prepare ChatGPT build package",android.widget.Toast.LENGTH_LONG).show();});}},"work-chatgpt-direct-builder").start();
    }

    private boolean hasLargeReference(){for(Uri u:references)if(WorkReferenceFilePolicy.shouldWarnForSize(meta(u).size))return true;return false;}

    private RefMeta meta(Uri uri){
        String name=uri==null?"reference":(uri.getLastPathSegment()==null?"reference":uri.getLastPathSegment());long size=-1;Cursor c=null;
        try{c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null);if(c!=null&&c.moveToFirst()){int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);int si=c.getColumnIndex(OpenableColumns.SIZE);if(ni>=0&&!c.isNull(ni))name=c.getString(ni);if(si>=0&&!c.isNull(si))size=c.getLong(si);}}catch(Throwable ignored){}finally{if(c!=null)c.close();}
        return new RefMeta(name,safeMime(uri),size);
    }

    private String safeMime(Uri uri){try{String m=getContentResolver().getType(uri);return m==null?"application/octet-stream":m;}catch(Throwable ignored){return "application/octet-stream";}}

    private static final class RefMeta {final String name,mime;final long size;RefMeta(String n,String m,long s){name=n;mime=m;size=s;}}
}
