package com.kareem.cortex;

import android.content.Context;
import java.util.concurrent.*;

/** Debounces raw phone evidence into low-frequency Context Engine refreshes. */
public final class ContextAwarenessScheduler {
    private static final ScheduledExecutorService EXEC=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"cortex-context-awareness");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static final Object LOCK=new Object();private static ScheduledFuture<?> pending;
    private ContextAwarenessScheduler(){}

    public static void request(Context context){if(context==null||CortexExperimentalTestMode.active(context))return;Context app=context.getApplicationContext();synchronized(LOCK){if(pending!=null)pending.cancel(false);pending=EXEC.schedule(()->refresh(app),2200,TimeUnit.MILLISECONDS);}}
    public static void refreshSoon(Context context){if(context==null)return;Context app=context.getApplicationContext();EXEC.schedule(()->refresh(app),250,TimeUnit.MILLISECONDS);}

    private static void refresh(Context app){VaultDb db=null;long started=System.currentTimeMillis();try{db=new VaultDb(app);ContextStateStore.ContextState state=ContextResolver.refresh(db);if(state!=null)try{DiagnosticsLog.info(db,"ContextEngine","refresh","ok",0,0,0,0,0,System.currentTimeMillis()-started,new org.json.JSONObject().put("context_id",state.id).put("role",state.role).put("confidence",state.stackConfidence));}catch(Throwable ignored){} }catch(Throwable e){if(db!=null)try{DiagnosticsLog.error(db,"ContextEngine","refresh",e,"CONTEXT_RESOLVE",0,0,0,0,0,null);}catch(Throwable ignored){} }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
}
