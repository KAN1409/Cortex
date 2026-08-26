package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Rollback-only adversarial proof for the clean-slate Truth boundary. No synthetic personal data survives. */
public final class TruthAdversarialFixture {
    public static final class Report {
        public final ArrayList<String> pass=new ArrayList<>(),fail=new ArrayList<>();
        public boolean ok(){return fail.isEmpty();}
        public String text(){StringBuilder b=new StringBuilder(ok()?"TRUTH ADVERSARIAL PASS":"TRUTH ADVERSARIAL FAIL");for(String x:pass)b.append("\nPASS · ").append(x);for(String x:fail)b.append("\nFAIL · ").append(x);return b.toString();}
    }

    private TruthAdversarialFixture(){}

    public static Report run(VaultDb db){
        Report r=new Report();if(db==null){r.fail.add("VaultDb unavailable");return r;}TruthSchema.ensure(db);
        SQLiteDatabase s=db.getWritableDatabase();boolean began=false;String token="truth_fixture_"+Long.toHexString(System.nanoTime());
        long base=8_000_000_000L+(System.nanoTime()&0x0fffffffL);
        try{
            s.beginTransaction();began=true;

            emit(db,base,0,"com.android.systemui","Battery","Charging 73% · 35 minutes until full · "+token);
            check(r,count(db,token,TruthObjectStore.ACTION)==0&&count(db,token,TruthObjectStore.WAITING)==0&&count(db,token,TruthObjectStore.DECISION)==0&&count(db,token,TruthObjectStore.IMPORTANT)==0,
                    "ambient battery/charging state produces no Truth Object");

            String action="ممكن تبعتلي الرسومات النهارده؟ "+token;
            emit(db,base+1,101,"com.whatsapp","Message",action);
            check(r,count(db,token,TruthObjectStore.ACTION)==1,"explicit incoming request becomes ACTION");

            String waiting="هبعتلك النسخة بكرة "+token;
            emit(db,base+2,102,"com.whatsapp","Message",waiting);
            check(r,count(db,token,TruthObjectStore.WAITING)==1,"explicit other-party commitment becomes WAITING");

            String approval="Your request has been approved "+token;
            emit(db,base+3,103,"mail","Update",approval);
            check(r,count(db,token,TruthObjectStore.DECISION)==0&&count(db,token,TruthObjectStore.IMPORTANT)==1,
                    "external approval is IMPORTANT and never the user's DECISION");

            String decision="قررت نستخدم الخيار ب "+token;
            emit(db,base+4,0,"manual","Note",decision);
            check(r,count(db,token,TruthObjectStore.DECISION)==1,"explicit user-owned choice becomes DECISION");

            String tentative="غالبا هنستخدم الخيار ج "+token;
            emit(db,base+5,0,"manual","Note",tentative);
            check(r,countText(db,tentative,TruthObjectStore.DECISION)==0,"tentative language does not become DECISION");

            emit(db,base+6,101,"com.whatsapp","Message",action);
            check(r,countText(db,action,TruthObjectStore.ACTION)==1,"repeated semantic request deduplicates active ACTION");

            TruthNowEngine.Snapshot snap=new TruthNowEngine.Snapshot(
                    TruthObjectStore.active(db,TruthObjectStore.ACTION,20),
                    TruthObjectStore.active(db,TruthObjectStore.WAITING,20),
                    TruthObjectStore.active(db,TruthObjectStore.DECISION,20),
                    TruthObjectStore.active(db,TruthObjectStore.IMPORTANT,20));
            check(r,snap.actions.size()>=1&&snap.waiting.size()>=1&&snap.decisions.size()>=1&&snap.important.size()>=1,
                    "NOW read model sees all four grounded truth lanes");

        }catch(Throwable e){r.fail.add(e.getClass().getSimpleName()+": "+safe(e.getMessage()));}
        finally{if(began)try{s.endTransaction();}catch(Throwable ignored){}}
        return r;
    }

    private static void emit(VaultDb db,long signalId,long threadId,String source,String title,String body){
        MasterRelevanceFilter.Signal s=new MasterRelevanceFilter.Signal("notification",source,title,body,"{\"synthetic\":true,\"rollback\":true}",System.currentTimeMillis(),false);
        EventEngine.onRawSignal(db,signalId,threadId,s);
    }

    private static int count(VaultDb db,String token,String kind){return scalar(db,"SELECT COUNT(*) FROM truth_objects WHERE kind=? AND state IN ('OPEN','CONFIRMED') AND (title LIKE ? OR body LIKE ?)",new String[]{kind,"%"+token+"%","%"+token+"%"});}
    private static int countText(VaultDb db,String text,String kind){return scalar(db,"SELECT COUNT(*) FROM truth_objects WHERE kind=? AND state IN ('OPEN','CONFIRMED') AND body=?",new String[]{kind,text});}
    private static int scalar(VaultDb db,String sql,String[] args){Cursor c=db.getReadableDatabase().rawQuery(sql,args);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static void check(Report r,boolean ok,String label){(ok?r.pass:r.fail).add(label);}
    private static String safe(String s){return s==null?"":s;}
}
