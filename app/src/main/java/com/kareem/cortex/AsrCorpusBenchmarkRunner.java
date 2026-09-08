package com.kareem.cortex;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Sequential shadow benchmark across multiple historical Cortex voice notes.
 * Uses stored working transcripts only as a reference baseline, never as human gold.
 * Does not mutate VaultDb or source WAV files.
 */
public final class AsrCorpusBenchmarkRunner {
    public interface Callback {
        void progress(int completed, int total, long itemId);
        void ok(Result result);
        void fail(Exception error);
    }

    public static final class Result {
        public final String candidateId;
        public final int requested;
        public final int eligible;
        public final int completed;
        public final int failed;
        public final long totalLatencyMs;
        public final double wer;
        public final double cer;
        public final double arabicWer;
        public final double latinWer;
        public final double numberRecall;
        public final int referenceScriptSwitches;
        public final int hypothesisScriptSwitches;
        public final int adjacentDuplicates;
        public final File reportFile;

        Result(String candidateId, int requested, int eligible, int completed, int failed,
               long totalLatencyMs, double wer, double cer, double arabicWer, double latinWer,
               double numberRecall, int referenceScriptSwitches, int hypothesisScriptSwitches,
               int adjacentDuplicates, File reportFile) {
            this.candidateId=candidateId;this.requested=requested;this.eligible=eligible;
            this.completed=completed;this.failed=failed;this.totalLatencyMs=totalLatencyMs;
            this.wer=wer;this.cer=cer;this.arabicWer=arabicWer;this.latinWer=latinWer;
            this.numberRecall=numberRecall;this.referenceScriptSwitches=referenceScriptSwitches;
            this.hypothesisScriptSwitches=hypothesisScriptSwitches;
            this.adjacentDuplicates=adjacentDuplicates;this.reportFile=reportFile;
        }
    }

    private static final class Acc {
        int requested, eligible, completed, failed;
        long latency;
        long refWords, wordEdits, refChars, charEdits;
        long arRef, arEdits, latRef, latEdits;
        long refNums, matchedNums;
        int refSwitches, hypSwitches, duplicates;
        final JSONArray items=new JSONArray();
    }

    private AsrCorpusBenchmarkRunner(){}

    public static void run(Context context, List<KnowledgeItem> sourceItems,
                           AsrCandidate candidate, Callback callback) {
        try {
            if(candidate==null)throw new IllegalArgumentException("ASR candidate is missing");
            if(!candidate.isReady(context))throw new IllegalStateException(candidate.displayName()+" is not ready on this device");
            ArrayList<KnowledgeItem> eligible=new ArrayList<>();
            if(sourceItems!=null)for(KnowledgeItem k:sourceItems){
                if(k==null||!"AUDIO".equals(k.type))continue;
                if(k.attachmentPath==null||k.attachmentPath.trim().isEmpty())continue;
                if(k.extractedText==null||k.extractedText.trim().isEmpty())continue;
                File f=new File(k.attachmentPath);
                if(f.exists()&&f.isFile())eligible.add(k);
            }
            if(eligible.isEmpty())throw new IllegalStateException("No eligible historical voice notes with WAV + working transcript");
            Acc acc=new Acc();acc.requested=sourceItems==null?0:sourceItems.size();acc.eligible=eligible.size();
            runNext(context,eligible,0,candidate,acc,callback);
        }catch(Exception e){callback.fail(e);}
    }

    private static void runNext(Context context, ArrayList<KnowledgeItem> items, int index,
                                AsrCandidate candidate, Acc acc, Callback callback){
        if(index>=items.size()){
            try{callback.ok(finish(context,candidate,acc));}catch(Exception e){callback.fail(e);}return;
        }
        KnowledgeItem k=items.get(index);
        File wav=new File(k.attachmentPath);
        AsrShadowBenchmarkRunner.run(context,k.id,wav,k.extractedText,candidate,new AsrShadowBenchmarkRunner.Callback(){
            @Override public void ok(AsrShadowBenchmarkRunner.Result r){
                try{
                    AsrBenchmarkMetrics.Score s=r.score;
                    acc.completed++;acc.latency+=r.latencyMs;
                    acc.refWords+=s.referenceWords;acc.wordEdits+=s.wordEdits;
                    acc.refChars+=s.referenceChars;acc.charEdits+=s.charEdits;
                    acc.arRef+=s.arabicReferenceWords;acc.arEdits+=s.arabicWordEdits;
                    acc.latRef+=s.latinReferenceWords;acc.latEdits+=s.latinWordEdits;
                    acc.refNums+=s.referenceNumbers;acc.matchedNums+=s.matchedNumbers;
                    acc.refSwitches+=s.referenceScriptSwitches;acc.hypSwitches+=s.hypothesisScriptSwitches;
                    acc.duplicates+=s.duplicateAdjacentWords;
                    JSONObject j=new JSONObject();j.put("item_id",k.id);j.put("status","ok");
                    j.put("latency_ms",r.latencyMs);j.put("wer",s.wer);j.put("cer",s.cer);
                    j.put("arabic_wer",s.arabicWer);j.put("latin_wer",s.latinWer);
                    j.put("number_recall",s.numberRecall);j.put("reference_script_switches",s.referenceScriptSwitches);
                    j.put("hypothesis_script_switches",s.hypothesisScriptSwitches);j.put("adjacent_duplicates",s.duplicateAdjacentWords);
                    j.put("report_file",r.reportFile.getAbsolutePath());acc.items.put(j);
                }catch(Exception ignored){}
                callback.progress(acc.completed+acc.failed,items.size(),k.id);
                runNext(context,items,index+1,candidate,acc,callback);
            }
            @Override public void fail(Exception error){
                acc.failed++;
                try{JSONObject j=new JSONObject();j.put("item_id",k.id);j.put("status","failed");j.put("error",error==null?"unknown":String.valueOf(error.getMessage()));acc.items.put(j);}catch(Exception ignored){}
                callback.progress(acc.completed+acc.failed,items.size(),k.id);
                runNext(context,items,index+1,candidate,acc,callback);
            }
        });
    }

    private static Result finish(Context context, AsrCandidate candidate, Acc a)throws Exception{
        double wer=ratio(a.wordEdits,a.refWords),cer=ratio(a.charEdits,a.refChars);
        double ar=ratio(a.arEdits,a.arRef),lat=ratio(a.latEdits,a.latRef);
        double nums=a.refNums==0?1.0:(double)a.matchedNums/(double)a.refNums;
        File dir=new File(context.getFilesDir(),"asr_benchmarks");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Could not create benchmark directory");
        File out=new File(dir,"corpus_"+candidate.id()+"_"+System.currentTimeMillis()+".json");
        JSONObject aggregate=new JSONObject();aggregate.put("wer",wer);aggregate.put("cer",cer);
        aggregate.put("arabic_wer",ar);aggregate.put("latin_wer",lat);aggregate.put("number_recall",nums);
        aggregate.put("reference_script_switches",a.refSwitches);aggregate.put("hypothesis_script_switches",a.hypSwitches);
        aggregate.put("adjacent_duplicates",a.duplicates);aggregate.put("total_latency_ms",a.latency);
        aggregate.put("average_latency_ms",a.completed==0?0:(double)a.latency/a.completed);
        JSONObject root=new JSONObject();root.put("schema","CORTEX_ASR_CORPUS_V1");
        root.put("reference_kind","current_working_transcripts_not_human_gold");
        root.put("candidate_id",candidate.id());root.put("candidate_name",candidate.displayName());
        root.put("requested",a.requested);root.put("eligible",a.eligible);root.put("completed",a.completed);root.put("failed",a.failed);
        root.put("aggregate",aggregate);root.put("items",a.items);
        try(FileOutputStream stream=new FileOutputStream(out)){stream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));}
        return new Result(candidate.id(),a.requested,a.eligible,a.completed,a.failed,a.latency,wer,cer,ar,lat,nums,a.refSwitches,a.hypSwitches,a.duplicates,out);
    }

    private static double ratio(long numerator,long denominator){return denominator==0?0.0:(double)numerator/(double)denominator;}
}
