package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
        String[] options={
                "RUN ASR TOURNAMENT",
                "RUN 10-VOICE RECOVERED WHISPER",
                "RUN LOCAL SHADOW BENCHMARK",
                "PROBE GEMINI NANO",
                "RE-TRANSCRIBE IN CLOUD",
                "DEBUG WITH CHATGPT",
                "EXPORT VOICE"
        };
        new AlertDialog.Builder(this)
                .setTitle("Cortex Prime Voice Lab")
                .setMessage(statusMessage())
                .setItems(options,(d,w)->{
                    if(w==0)runTournament();
                    else if(w==1)runCorpusBenchmark();
                    else if(w==2)runLocalShadowBenchmark();
                    else if(w==3)probeGeminiNano();
                    else if(w==4)retryCloud();
                    else if(w==5)openChatGpt();
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
        boolean recovered=false,clean=false,android=false;
        try{recovered=new LocalWhisperAsrCandidate().isReady(this);}catch(Throwable ignored){}
        try{clean=new CleanWhisperAsrCandidate().isReady(this);}catch(Throwable ignored){}
        try{android=new AndroidOnDeviceAsrCandidate().isReady(this);}catch(Throwable ignored){}
        int corpus=0;try{corpus=db.recentVoiceCorpus(10).size();}catch(Throwable ignored){}
        return "Production voice baseline: unchanged cloud reference.\n"
                +"Recovered Whisper: "+yes(recovered)+"\n"
                +"Clean Whisper: "+yes(clean)+"\n"
                +"Android on-device ASR: "+yes(android)+"\n"
                +"Historical corpus: "+corpus+" / 10\n"
                +"All tournament runs are shadow-only and never overwrite transcripts or WAVs.\n\nRecording: "+item.title
                +"\nStatus: "+status+engine;
    }

    String yes(boolean v){return v?"ready":"not ready";}

    void runTournament(){
        ArrayList<KnowledgeItem> corpus=db.recentVoiceCorpus(10);
        if(corpus.isEmpty()){
            Toast.makeText(this,"No historical recordings have both original WAV and working transcript",Toast.LENGTH_LONG).show();return;
        }
        ArrayList<AsrCandidate> candidates=new ArrayList<>(Arrays.asList(
                new LocalWhisperAsrCandidate(),
                new CleanWhisperAsrCandidate(),
                new AndroidOnDeviceAsrCandidate()
        ));
        Toast.makeText(this,"ASR tournament started across "+corpus.size()+" historical recordings",Toast.LENGTH_LONG).show();
        AsrTournamentRunner.run(this,corpus,candidates,new AsrTournamentRunner.Callback(){
            @Override public void progress(String name,int candidateIndex,int candidateTotal,int itemCompleted,int itemTotal){
                runOnUiThread(()->Toast.makeText(DebugReviewActivity.this,name+" · "+itemCompleted+"/"+itemTotal,Toast.LENGTH_SHORT).show());
            }
            @Override public void done(ArrayList<AsrTournamentRunner.Entry> entries){runOnUiThread(()->showTournament(entries));}
        });
    }

    void showTournament(ArrayList<AsrTournamentRunner.Entry> entries){
        StringBuilder m=new StringBuilder("Reference: stored working transcripts, not human gold.\n\n");
        for(AsrTournamentRunner.Entry e:entries){
            m.append(e.name).append('\n');
            if(!e.ready||e.result==null){m.append("  ").append(e.note==null?"Unavailable":e.note).append("\n\n");continue;}
            AsrCorpusBenchmarkRunner.Result r=e.result;
            m.append("  WER: ").append(pct(r.wer)).append('\n');
            m.append("  Arabic WER: ").append(pct(r.arabicWer)).append('\n');
            m.append("  English WER: ").append(pct(r.latinWer)).append('\n');
            m.append("  Numbers: ").append(pct(r.numberRecall)).append(" recall\n");
            m.append("  Code-switch: ").append(r.hypothesisScriptSwitches).append(" / ").append(r.referenceScriptSwitches).append('\n');
            m.append("  Avg latency: ").append(r.completed==0?0:r.totalLatencyMs/r.completed).append(" ms\n");
            m.append("  Completed: ").append(r.completed).append("  Failed: ").append(r.failed).append("\n\n");
        }
        m.append("Lower WER is better. A candidate is not promoted automatically; production voice remains unchanged.");
        new AlertDialog.Builder(this).setTitle("Cortex ASR Tournament").setMessage(m.toString())
                .setPositiveButton("Close",(d,w)->finish()).show();
    }

    void probeGeminiNano(){
        Toast.makeText(this,"Checking Gemini Nano / AICore locally",Toast.LENGTH_SHORT).show();
        GeminiNanoProbe.run(this,r->runOnUiThread(()->new AlertDialog.Builder(this)
                .setTitle("Gemini Nano probe")
                .setMessage("Status: "+r.label+"\n\n"+r.detail+"\n\nThis probe does not replace Cortex voice transcription and does not call a paid Cortex API.")
                .setPositiveButton("Close",(d,w)->finish()).show()));
    }

    void runCorpusBenchmark(){
        LocalWhisperAsrCandidate candidate=new LocalWhisperAsrCandidate();
        if(!candidate.isReady(this)){
            Toast.makeText(this,"Select/verify a supported local Whisper model first",Toast.LENGTH_LONG).show();return;
        }
        ArrayList<KnowledgeItem> corpus=db.recentVoiceCorpus(10);
        if(corpus.isEmpty()){
            Toast.makeText(this,"No historical recordings have both original WAV and working transcript",Toast.LENGTH_LONG).show();return;
        }
        Toast.makeText(this,"Running recovered Whisper across "+corpus.size()+" historical recordings",Toast.LENGTH_LONG).show();
        AsrCorpusBenchmarkRunner.run(this,corpus,candidate,new AsrCorpusBenchmarkRunner.Callback(){
            @Override public void progress(int completed,int total,long currentItemId){
                runOnUiThread(()->Toast.makeText(DebugReviewActivity.this,"Corpus benchmark "+completed+" / "+total,Toast.LENGTH_SHORT).show());
            }
            @Override public void ok(AsrCorpusBenchmarkRunner.Result r){runOnUiThread(()->showCorpusResult(r));}
            @Override public void fail(Exception error){runOnUiThread(()->new AlertDialog.Builder(DebugReviewActivity.this)
                    .setTitle("Corpus benchmark failed")
                    .setMessage(error==null?"Unknown benchmark failure":String.valueOf(error.getMessage()))
                    .setPositiveButton("Close",(d,w)->finish()).show());}
        });
    }

    void showCorpusResult(AsrCorpusBenchmarkRunner.Result r){
        String message="Reference: stored working transcripts (not human gold)\n\n"
                +"Completed: "+r.completed+" / "+r.eligible+"\n"
                +"Failed: "+r.failed+"\n"
                +"Aggregate WER: "+pct(r.wer)+"\n"
                +"Aggregate CER: "+pct(r.cer)+"\n"
                +"Arabic WER: "+pct(r.arabicWer)+"\n"
                +"English WER: "+pct(r.latinWer)+"\n"
                +"Number recall: "+pct(r.numberRecall)+"\n"
                +"Code-switch boundaries: "+r.hypothesisScriptSwitches+" / "+r.referenceScriptSwitches+"\n"
                +"Adjacent duplicates: "+r.adjacentDuplicates+"\n"
                +"Average latency: "+(r.completed==0?0:r.totalLatencyMs/r.completed)+" ms\n\n"
                +"Corpus report:\n"+r.reportFile.getAbsolutePath();
        new AlertDialog.Builder(this).setTitle("Recovered Whisper corpus result").setMessage(message)
                .setPositiveButton("Close",(d,w)->finish()).show();
    }

    void runLocalShadowBenchmark(){
        File source=item.attachmentPath==null?null:new File(item.attachmentPath);
        String reference=item.extractedText==null?"":item.extractedText.trim();
        if(source==null||!source.exists()){
            Toast.makeText(this,"Original WAV is missing",Toast.LENGTH_LONG).show();return;
        }
        if(reference.isEmpty()){
            Toast.makeText(this,"This recording has no working reference transcript yet",Toast.LENGTH_LONG).show();return;
        }
        LocalWhisperAsrCandidate candidate=new LocalWhisperAsrCandidate();
        if(!candidate.isReady(this)){
            Toast.makeText(this,"Select/verify a supported local Whisper model first",Toast.LENGTH_LONG).show();return;
        }
        Toast.makeText(this,"Running local ASR in shadow mode. Stored transcript will not change.",Toast.LENGTH_LONG).show();
        AsrShadowBenchmarkRunner.run(this,itemId,source,reference,candidate,new AsrShadowBenchmarkRunner.Callback(){
            @Override public void ok(AsrShadowBenchmarkRunner.Result r){runOnUiThread(()->showBenchmarkResult(r));}
            @Override public void fail(Exception error){runOnUiThread(()->new AlertDialog.Builder(DebugReviewActivity.this)
                    .setTitle("Shadow benchmark failed")
                    .setMessage(error==null?"Unknown benchmark failure":String.valueOf(error.getMessage()))
                    .setPositiveButton("Close",(d,w)->finish()).show());}
        });
    }

    void showBenchmarkResult(AsrShadowBenchmarkRunner.Result r){
        AsrBenchmarkMetrics.Score s=r.score;
        String message="Reference: current working transcript (not human gold)\n\n"
                +"WER: "+pct(s.wer)+"\n"
                +"CER: "+pct(s.cer)+"\n"
                +"Arabic WER: "+pct(s.arabicWer)+"\n"
                +"English WER: "+pct(s.latinWer)+"\n"
                +"Number recall: "+pct(s.numberRecall)+"\n"
                +"Code-switch boundaries: "+s.hypothesisScriptSwitches+" / "+s.referenceScriptSwitches+"\n"
                +"Adjacent duplicates: "+s.duplicateAdjacentWords+"\n"
                +"Latency: "+r.latencyMs+" ms\n\n"
                +"LOCAL TRANSCRIPT\n"+r.candidateText+"\n\n"
                +"Report saved internally:\n"+r.reportFile.getAbsolutePath();
        new AlertDialog.Builder(this).setTitle("Recovered Whisper shadow result").setMessage(message)
                .setPositiveButton("Close",(d,w)->finish()).show();
    }

    String pct(double value){return String.format(Locale.US,"%.1f%%",value*100.0);}

    void retryCloud(){
        db.retry(itemId);
        Toast.makeText(this,"Cloud re-transcription queued on the original recording",Toast.LENGTH_LONG).show();
        AnalysisQueue.kick(this,db,()->runOnUiThread(()->{
            KnowledgeItem fresh=db.getById(itemId);
            if(fresh!=null&&"analyzed".equals(fresh.status))Toast.makeText(this,"Cloud transcription finished",Toast.LENGTH_LONG).show();
            else if(fresh!=null&&"analysis_failed".equals(fresh.status))Toast.makeText(this,"Cloud transcription will retry if the provider/network failure is temporary",Toast.LENGTH_LONG).show();
            else Toast.makeText(this,"Cloud transcription queued",Toast.LENGTH_LONG).show();
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
