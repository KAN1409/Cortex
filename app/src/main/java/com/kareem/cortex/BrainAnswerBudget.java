package com.kareem.cortex;

import android.content.Context;
import java.util.concurrent.*;

/** Hard wall-clock budget around cloud Brain calls so Combined can fall back locally instead of hanging. */
public final class BrainAnswerBudget {
    // A provider socket can outlive Future.cancel on some Android stacks. Keep the pool bounded so
    // repeated timeouts cannot create an unbounded number of stuck network threads.
    private static final ThreadPoolExecutor EXEC=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"cortex-brain-provider");t.setPriority(Thread.NORM_PRIORITY-1);return t;},new ThreadPoolExecutor.AbortPolicy());
    static{EXEC.allowCoreThreadTimeOut(true);}
    private BrainAnswerBudget(){}

    public static ExternalBrainProvider.Result ask(Context context,String question,GroundedAnswer grounded,boolean combined,KnowledgeItem focal,String contextText,long timeoutMs)throws Exception{
        long budget=Math.max(4_000L,Math.min(30_000L,timeoutMs));Future<ExternalBrainProvider.Result> f;
        try{f=EXEC.submit(()->ExternalBrainProvider.ask(context,question,grounded,combined,focal,contextText));}catch(RejectedExecutionException e){throw new ExternalTimeoutException("External Brain is still clearing earlier provider requests");}
        try{return f.get(budget,TimeUnit.MILLISECONDS);}catch(TimeoutException e){f.cancel(true);throw new ExternalTimeoutException("External Brain exceeded "+budget+" ms answer budget");}catch(ExecutionException e){Throwable c=e.getCause();if(c instanceof Exception)throw (Exception)c;throw new RuntimeException(c);}catch(InterruptedException e){f.cancel(true);Thread.currentThread().interrupt();throw e;}
    }

    public static final class ExternalTimeoutException extends Exception {ExternalTimeoutException(String message){super(message);}}
}
