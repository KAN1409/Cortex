package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;

/** Voice debugging console for the latest Cortex Prime recording. */
public class DebugReviewActivity extends Activity {
    VaultDb db; long itemId; KnowledgeItem item;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new VaultDb(this);
        itemId=getIntent()==null?0:getIntent().getLongExtra("item_id",0);
        if(itemId<=0)itemId=ChatGptDebugReview.latestVoiceId(db);
        item=itemId<=0?null:db.getById(itemId);
        if(item==null){Toast.makeText(this,"No voice recording found in Cortex Prime",Toast.LENGTH_LONG).show();finish();return;}
        showMenu();
    }

    void showMenu(){
        String[] options={"RE-TRANSCRIBE IN CLOUD","DEBUG WITH CHATGPT","EXPORT VOICE"};
        new AlertDialog.Builder(this)
                .setTitle("Cortex Prime Voice Debug")
                .setMessage(statusMessage())
                .setItems(options,(d,w)->{
                    if(w==0)retryCloud();
                    else if(w==1)openChatGpt();
                    else exportVoice();
                })
                .setNegativeButton("Close",(d,w)->finish())
                .setOnCancelListener(d->finish())
                .show();
    }

    String statusMessage(){
        String status=item.status==null?"unknown":item.status;
        String engine="";
        try{String info=AudioStore.info(db,itemId);if(info!=null&&!info.trim().isEmpty())engine="\n"+info;}catch(Exception ignored){}
        return "Cloud ASR • gpt-transcribe → Google Chirp 3 → Azure Speech\n"
                +"No local ASR model is used.\n\nRecording: "+item.title
                +"\nStatus: "+status+engine;
    }

    void retryCloud(){
        db.retry(itemId);
        Toast.makeText(this,"Cloud re-transcription queued on the original recording",Toast.LENGTH_LONG).show();
        AnalysisQueue.kick(this,db,()->runOnUiThread(()->{
            KnowledgeItem fresh=db.getById(itemId);
            if(fresh!=null&&"analyzed".equals(fresh.status)){
                Toast.makeText(this,"Cloud transcription finished",Toast.LENGTH_LONG).show();
            }else if(fresh!=null&&"analysis_failed".equals(fresh.status)){
                Toast.makeText(this,"Cloud transcription will retry if the provider/network failure is temporary",Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this,"Cloud transcription queued",Toast.LENGTH_LONG).show();
            }
            finish();
        }));
    }

    void openChatGpt(){
        try{ChatGptDebugReview.share(this,db,itemId);}catch(Exception e){Toast.makeText(this,"Could not open ChatGPT debug review: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        finish();
    }

    void exportVoice(){
        File f=item.attachmentPath==null?null:new File(item.attachmentPath);
        new Thread(()->{try{
            String path=VoiceExporter.export(this,f,itemId);
            runOnUiThread(()->{Toast.makeText(this,"Saved original WAV to "+path,Toast.LENGTH_LONG).show();finish();});
        }catch(Exception e){runOnUiThread(()->{Toast.makeText(this,"Voice export failed: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();});}}).start();
    }
}
