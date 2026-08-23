package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Cheap thread-level pass over real signals. It only emits high-confidence derived
 * intelligence; ambiguous communication remains temporary context for the later model adjudicator.
 */
public final class ThreadRelevanceEngine {
    private static final String POLICY="thread_fast_001";
    private ThreadRelevanceEngine(){}

    public static void onSignal(VaultDb db,long threadId,long signalId){
        if(threadId<=0||signalId<=0)return;ThreadSnapshot t=load(db,threadId,signalId);if(t==null||!("communication".equals(t.kind)||"email".equals(t.kind)))return;
        Decision d=evaluate(t.latestBody);if(d==null)return;
        try{
            JSONObject meta=new JSONObject();meta.put("policy_version",POLICY);meta.put("thread_id",threadId);meta.put("raw_signal_id",signalId);meta.put("reason",d.reason);meta.put("source",t.source);
            String title=empty(t.title)?friendly(d.kind):t.title+" · "+friendly(d.kind);
            String fp=Fingerprint.text("thread-derived|"+d.kind+"|"+threadId+"|"+Fingerprint.text(t.latestBody));
            long derived=CognitiveStore.addDerived(db,d.kind,title,t.latestBody,"open",d.confidence,d.importance,fp,meta.toString());
            if(derived>0){CognitiveStore.link(db,"thread",threadId,"derived",derived,"produced",d.confidence,meta.toString());CognitiveStore.link(db,"raw_signal",signalId,"derived",derived,"supports",1.0,"");}
        }catch(Exception ignored){}
    }

    private static Decision evaluate(String text){
        String t=low(text);if(t.isEmpty())return null;
        if(has(t,"ممكن تبعت","ممكن تبعتلي","ابعتلي","ابعت لي","محتاج منك","محتاجك تبعت","لو سمحت ابعت","متنساش تبعت","please send","can you send","could you send","need you to send","please review","can you review","could you review","please confirm","can you confirm"))return new Decision("ACTION",0.90,68,"explicit incoming request directed to the user");
        if(has(t,"هبعتلك","هابعتلك","هبعتهولك","هراجع وارجعلك","هراجع و أرد عليك","هرد عليك","هرجعلك","هكلمك لما","i'll send you","i will send you","i'll get back to you","i will get back to you","i'll reply","i will reply"))return new Decision("WAITING",0.88,64,"explicit commitment from the other party");
        if(has(t,"تمت الموافقة","تمت الموافقه","تم الرفض","موافق على","approved","has been approved","rejected","has been rejected"))return new Decision("DECISION",0.87,66,"explicit approval or rejection in the thread");
        return null;
    }

    private static ThreadSnapshot load(VaultDb db,long threadId,long signalId){
        Cursor tc=db.getReadableDatabase().query("signal_threads",new String[]{"kind","source","title"},"id=?",new String[]{String.valueOf(threadId)},null,null,null,"1");if(!tc.moveToFirst()){tc.close();return null;}String kind=n(tc.getString(0)),source=n(tc.getString(1)),title=n(tc.getString(2));tc.close();
        Cursor sc=db.getReadableDatabase().query("raw_signals",new String[]{"body"},"id=? AND thread_id=?",new String[]{String.valueOf(signalId),String.valueOf(threadId)},null,null,null,"1");String body=sc.moveToFirst()?n(sc.getString(0)):"";sc.close();return new ThreadSnapshot(kind,source,title,body);
    }

    private static String friendly(String kind){if("ACTION".equals(kind))return"Possible action";if("WAITING".equals(kind))return"Waiting";if("DECISION".equals(kind))return"Decision";return"Update";}
    private static boolean has(String s,String... xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String n(String s){return s==null?"":s.trim();}
    private static final class Decision{final String kind,reason;final double confidence;final int importance;Decision(String k,double c,int i,String r){kind=k;confidence=c;importance=i;reason=r;}}
    private static final class ThreadSnapshot{final String kind,source,title,latestBody;ThreadSnapshot(String k,String s,String t,String b){kind=k;source=s;title=t;latestBody=b;}}
}
