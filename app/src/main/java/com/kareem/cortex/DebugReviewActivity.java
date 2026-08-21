package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;

/** Temporary voice debugging console for the latest Cortex recording. */
public class DebugReviewActivity extends Activity {
    VaultDb db; long itemId; KnowledgeItem item;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new VaultDb(this);
        itemId=getIntent()==null?0:getIntent().getLongExtra("item_id",0);
        if(itemId<=0)itemId=ChatGptDebugReview.latestVoiceId(db);
        item=itemId<=0?null:db.getById(itemId);
        if(item==null){Toast.makeText(this,"No voice recording found in Cortex",Toast.LENGTH_LONG).show();finish();return;}
        showMenu();
    }

    void showMenu(){
        String[] options={"RE-ANALYZE WITH WHISPER","DEBUG WITH CHATGPT","EXPORT VOICE"};
        new AlertDialog.Builder(this)
                .setTitle("Cortex Voice Debug")
                .setMessage(WhisperRuntimeState.describe(this)+"\n\nRecording: "+item.title)
                .setItems(options,(d,w)->{
                    if(w==0)retryWhisper();
                    else if(w==1)openChatGpt();
                    else exportVoice();
                })
                .setNegativeButton("Close",(d,w)->finish())
                .setOnCancelListener(d->finish())
                .show();
    }

    void retryWhisper(){
        WhisperRuntimeState.forceWhisperOnly(this,itemId);
        db.retry(itemId);
        Toast.makeText(this,"Whisper-only re-analysis started on the same WAV",Toast.LENGTH_LONG).show();
        AnalysisQueue.kick(this,db,()->runOnUiThread(()->{
            KnowledgeItem fresh=db.getById(itemId);
            String state=WhisperRuntimeState.describe(this);
            Toast.makeText(this,(fresh!=null&&"analyzed".equals(fresh.status)?"Whisper analysis finished\n":"Whisper analysis stopped\n")+state,Toast.LENGTH_LONG).show();
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
