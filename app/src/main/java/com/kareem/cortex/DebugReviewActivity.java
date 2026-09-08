package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.util.Locale;

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
        String[] options={"RUN LOCAL SHADOW BENCHMARK","RE-TRANSCRIBE IN CLOUD","DEBUG WITH CHATGPT","EXPORT VOICE"};
        new AlertDialog.Builder(this)
                .setTitle("Cortex Prime Voice Debug")
                .setMessage(statusMessage())
                .setItems(options,(d,w)->{
                    if(w==0)runLocalShadowBenchmark();
                    else if(w==1)retryCloud();
                    else if(w==2)openChatGpt();
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
        boolean localReady=false;
        try{localReady=new LocalWhisperAsrCandidate().isReady(this);}catch(Throwable ignored){}
        return "Production voice baseline: cloud ASR remains unchanged.\n"
                +"Local Whisper: "+(localReady?"ready for shadow benchmark":"model not ready")+"\n"
                +"Shadow runs never overwrite the stored transcript or source WAV.\n\nRecording: "+item.title
                +"\nStatus: "+status+engine;
    }

    void runLocalShadowBenchmark(){
        File source=item.attachmentPath==null?null:new File(item.attachmentPath);
        String reference=item.extractedText==null?"":item.extractedText.trim();
        if(source==null||!source.exists()){
            Toast.makeText(this,"Original WAV is missing",Toast.LENGTH_LONG).show();finish();return;
        }
        if(reference.isEmpty()){
            Toast.makeText(this,"This recording has no working reference transcript yet",Toast.LENGTH_LONG).show();finish();return;
        }

        LocalWhisperAsrCandidate candidate=new LocalWhisperAsrCandidate();
        if(!candidate.isReady(this)){
            Toast.makeText(this,"Select/verify a supported local Whisper model first",Toast.LENGTH_LONG).show();finish();return;
        }

        Toast.makeText(this,"Running local ASR in shadow mode. Stored transcript will not change.",Toast.LENGTH_LONG).show();
        AsrShadowBenchmarkRunner.run(this,itemId,source,reference,candidate,new AsrShadowBenchmarkRunner.Callback(){
            @Override public void ok(AsrShadowBenchmarkRunner.Result r){
                runOnUiThread(()->showBenchmarkResult(r));
            }
            @Override public void fail(Exception error){
                runOnUiThread(()->new AlertDialog.Builder(DebugReviewActivity.this)
                        .setTitle("Shadow benchmark failed")
                        .setMessage(error==null?"Unknown benchmark failure":String.valueOf(error.getMessage()))
                        .setPositiveButton("Close",(d,w)->finish())
                        .show());
            }
        });
    }

    void showBenchmarkResult(AsrShadowBenchmarkRunner.Result r){
        AsrBenchmarkMetrics.Score s=r.score;
        String message="Reference: current working transcript (not human gold)\n\n"
                +"WER: "+pct(s.wer)+"\n"
                +"CER: "+pct(s.cer)+"\n"
                +"Arabic WER: "+pct(s.arabicWer)+"\n"
                +"English WER: "+pct(s.latinWer)+"\n"
                +"Number recall: "+pct(1.0-s.numberRecall)+" error / "+pct(s.numberRecall)+" recall\n"
                +"Code-switch boundaries: "+s.hypothesisScriptSwitches+" / "+s.referenceScriptSwitches+"\n"
                +"Adjacent duplicates: "+s.duplicateAdjacentWords+"\n"
                +"Latency: "+r.latencyMs+" ms\n\n"
                +"LOCAL TRANSCRIPT\n"+r.candidateText+"\n\n"
                +"Report saved internally:\n"+r.reportFile.getAbsolutePath();
        new AlertDialog.Builder(this)
                .setTitle("Local Whisper shadow result")
                .setMessage(message)
                .setPositiveButton("Close",(d,w)->finish())
                .show();
    }

    String pct(double value){return String.format(Locale.US,"%.1f%%",value*100.0);}

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
