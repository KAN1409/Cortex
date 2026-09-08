package com.kareem.cortex;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Runs the same historical corpus through every ready ASR candidate, one candidate at a time. */
public final class AsrTournamentRunner {
    public interface Callback {
        void progress(String candidateName,int candidateIndex,int candidateTotal,int itemCompleted,int itemTotal);
        void done(ArrayList<Entry> entries);
    }
    public static final class Entry {
        public final String id,name;public final boolean ready;public final String note;
        public final AsrCorpusBenchmarkRunner.Result result;
        Entry(String id,String name,boolean ready,String note,AsrCorpusBenchmarkRunner.Result result){
            this.id=id;this.name=name;this.ready=ready;this.note=note;this.result=result;
        }
    }
    private AsrTournamentRunner(){}

    public static void run(Context context,List<KnowledgeItem> corpus,List<AsrCandidate> candidates,Callback callback){
        ArrayList<Entry> out=new ArrayList<>();
        if(candidates==null||candidates.isEmpty()){callback.done(out);return;}
        runNext(context,corpus,candidates,0,out,callback);
    }

    private static void runNext(Context context,List<KnowledgeItem> corpus,List<AsrCandidate> candidates,int index,ArrayList<Entry> out,Callback callback){
        if(index>=candidates.size()){callback.done(out);return;}
        AsrCandidate candidate=candidates.get(index);
        boolean ready=false;String readiness="";
        try{ready=candidate.isReady(context);}catch(Throwable t){readiness=String.valueOf(t.getMessage());}
        if(!ready){out.add(new Entry(candidate.id(),candidate.displayName(),false,readiness.isEmpty()?"Not available on this device":"Not ready: "+readiness,null));runNext(context,corpus,candidates,index+1,out,callback);return;}
        final int current=index;
        AsrCorpusBenchmarkRunner.run(context,corpus,candidate,new AsrCorpusBenchmarkRunner.Callback(){
            @Override public void progress(int completed,int total,long itemId){callback.progress(candidate.displayName(),current+1,candidates.size(),completed,total);}
            @Override public void ok(AsrCorpusBenchmarkRunner.Result r){out.add(new Entry(candidate.id(),candidate.displayName(),true,"",r));runNext(context,corpus,candidates,current+1,out,callback);}
            @Override public void fail(Exception error){out.add(new Entry(candidate.id(),candidate.displayName(),true,"Failed: "+(error==null?"unknown":String.valueOf(error.getMessage())),null));runNext(context,corpus,candidates,current+1,out,callback);}
        });
    }
}
