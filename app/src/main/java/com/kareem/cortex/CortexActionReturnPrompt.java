package com.kareem.cortex;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * When the user returns to Cortex after an action was handed to another app, settle it truthfully:
 * calendar drafts are read back from the calendar provider; everything else is asked.
 * A "yes" is recorded as USER_CONFIRMED, never as VERIFIED.
 */
final class CortexActionReturnPrompt implements Application.ActivityLifecycleCallbacks {
    /** Returning within this window is the same trip (e.g. a permission sheet), not a return. */
    static final long MIN_AWAY_MS=4_000L;
    /** Older handoffs are not asked about any more; they stay honestly HANDED_OFF. */
    static final long MAX_AGE_MS=2L*60L*60L*1000L;
    static final long CALENDAR_WINDOW_MS=15L*60L*1000L;

    private static final AtomicBoolean INSTALLED=new AtomicBoolean(false);
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-action-return");t.setDaemon(true);return t;});

    private CortexActionReturnPrompt(){}

    static void install(Application app){
        if(app!=null&&INSTALLED.compareAndSet(false,true))app.registerActivityLifecycleCallbacks(new CortexActionReturnPrompt());
    }

    @Override public void onActivityResumed(Activity a){
        if(a==null||StartupSafetyGate.active()||!BUSY.compareAndSet(false,true))return;
        IO.execute(()->{
            boolean handedToUi=false;
            VaultDb vault=null;
            try{
                vault=new VaultDb(a.getApplicationContext());
                SQLiteDatabase db=vault.getWritableDatabase();
                long now=System.currentTimeMillis();
                CortexActionReceipts.Receipt r=CortexActionReceipts.pendingConfirmation(db,now,MIN_AWAY_MS,MAX_AGE_MS);
                if(r==null)return;
                if(isCalendar(r.type)&&calendarReadable(a)&&calendarEventExists(a.getContentResolver(),r.title,r.expectedAt)){
                    if(CortexActionReceipts.transition(db,r.actionKey,CortexActionReceipts.VERIFIED,"Calendar event found",now)){
                        CortexActionReceipts.syncLegacy(vault,r.actionRowId,CortexActionReceipts.VERIFIED);
                        a.runOnUiThread(()->{try{Toast.makeText(a,"Calendar event verified: "+r.title,Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}});
                    }
                    return;
                }
                // Asked once per handoff, even if the dialog is dismissed.
                CortexActionReceipts.markAsked(db,r.actionKey,now);
                handedToUi=true;
                a.runOnUiThread(()->ask(a,r));
            }catch(Throwable ignored){
            }finally{
                if(vault!=null)try{vault.close();}catch(Throwable ignored){}
                if(!handedToUi)BUSY.set(false);
            }
        });
    }

    private static void ask(Activity a,CortexActionReceipts.Receipt r){
        if(a.isFinishing()||(Build.VERSION.SDK_INT>=17&&a.isDestroyed())){BUSY.set(false);return;}
        try{
            new AlertDialog.Builder(a)
                    .setTitle(question(r.type))
                    .setMessage(r.title+"\n\nCortex opened this in another app and cannot see whether it happened.")
                    .setPositiveButton("Yes, done",(d,w)->settle(a,r,CortexActionReceipts.USER_CONFIRMED,"Confirmed by you"))
                    .setNeutralButton("Not yet",null)
                    .setNegativeButton("Cancelled",(d,w)->settle(a,r,CortexActionReceipts.CANCELLED,"Cancelled by you"))
                    .setOnDismissListener(d->BUSY.set(false))
                    .show();
        }catch(Throwable e){BUSY.set(false);}
    }

    private static void settle(Activity a,CortexActionReceipts.Receipt r,String state,String detail){
        android.content.Context app=a.getApplicationContext();
        IO.execute(()->{
            VaultDb vault=null;
            try{
                vault=new VaultDb(app);
                if(CortexActionReceipts.transition(vault.getWritableDatabase(),r.actionKey,state,detail,System.currentTimeMillis()))
                    CortexActionReceipts.syncLegacy(vault,r.actionRowId,state);
            }catch(Throwable ignored){
            }finally{if(vault!=null)try{vault.close();}catch(Throwable ignored){}}
        });
    }

    static String question(String type){
        if("EMAIL_DRAFT".equals(type))return "Did you send the email?";
        if("MESSAGE_DRAFT".equals(type))return "Did you send the message?";
        if("CALL".equals(type))return "Did you make the call?";
        if("WEB_SEARCH".equals(type))return "Did you find what you needed?";
        if(isCalendar(type))return "Did you save it in your calendar?";
        return "Did you finish this?";
    }

    static boolean isCalendar(String type){return "CALENDAR_EVENT".equals(type)||"REMINDER".equals(type);}

    private static boolean calendarReadable(Activity a){
        return Build.VERSION.SDK_INT<23||a.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;
    }

    /** Read-back: an undeleted event with the drafted title starting within 15 minutes of the suggested time. */
    static boolean calendarEventExists(ContentResolver cr,String title,long expectedAt){
        if(cr==null||title==null||title.trim().isEmpty()||expectedAt<=0)return false;
        String[] args={title.trim(),String.valueOf(expectedAt-CALENDAR_WINDOW_MS),String.valueOf(expectedAt+CALENDAR_WINDOW_MS)};
        try(Cursor c=cr.query(CalendarContract.Events.CONTENT_URI,new String[]{CalendarContract.Events._ID},
                CalendarContract.Events.TITLE+"=? AND "+CalendarContract.Events.DTSTART+">=? AND "+CalendarContract.Events.DTSTART+"<=? AND "+CalendarContract.Events.DELETED+"=0",args,null)){
            return c!=null&&c.moveToFirst();
        }catch(Throwable e){return false;}
    }

    @Override public void onActivityCreated(Activity a,Bundle b){}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public void onActivityDestroyed(Activity a){}
}
