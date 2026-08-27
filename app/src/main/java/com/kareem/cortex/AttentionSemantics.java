package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;
import java.util.*;

/** Structured meaning layer for attention. Model decisions enrich meaning; they never assign the attention score. */
public final class AttentionSemantics {
    private AttentionSemantics(){}

    public enum Intent { INFORMATION, REQUEST, QUESTION, COMMITMENT, FOLLOW_UP, CANCELLATION, COMPLETION, DECISION, UNKNOWN }
    public static final class Result {
        public final long signalId,threadId;public final String source,title,body,metadata,finalDisposition;public final Intent intent;public final boolean incoming,outgoing,actionExpected,responseExpected,cancellation,completion,ongoing;public final int importance;public final double confidence;
        Result(long signalId,long threadId,String source,String title,String body,String metadata,String finalDisposition,Intent intent,boolean incoming,boolean outgoing,boolean actionExpected,boolean responseExpected,boolean cancellation,boolean completion,boolean ongoing,int importance,double confidence){this.signalId=signalId;this.threadId=threadId;this.source=n(source);this.title=n(title);this.body=n(body);this.metadata=n(metadata);this.finalDisposition=n(finalDisposition);this.intent=intent;this.incoming=incoming;this.outgoing=outgoing;this.actionExpected=actionExpected;this.responseExpected=responseExpected;this.cancellation=cancellation;this.completion=completion;this.ongoing=ongoing;this.importance=importance;this.confidence=confidence;}
    }

    public static Result extract(VaultDb db,long signalId,long fallbackThread){if(db==null||signalId<=0)return null;Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"source","title","body","metadata_json","disposition","importance","confidence","thread_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");if(!c.moveToFirst()){c.close();return null;}String source=n(c.getString(0)),title=n(c.getString(1)),body=n(c.getString(2)),metadata=n(c.getString(3)),baseline=n(c.getString(4));int importance=c.getInt(5);double confidence=c.getDouble(6);long thread=c.getLong(7)>0?c.getLong(7):fallbackThread;c.close();String finalDisposition=finalDisposition(db,signalId);if(finalDisposition.isEmpty())finalDisposition=baseline;
        JSONObject m=null;try{m=new JSONObject(metadata);}catch(Exception ignored){}String direction=m==null?"":n(m.optString("direction","")).toLowerCase(Locale.ROOT);boolean incoming="incoming".equals(direction)||"received".equals(direction)||"received_from_other".equals(direction),outgoing="outgoing".equals(direction)||"sent".equals(direction)||"sent_by_self".equals(direction);boolean ongoing=m!=null&&m.optBoolean("ongoing",false);String x=body.toLowerCase(Locale.ROOT);
        boolean cancellation=hasAny(x,"خلاص سيبك","مش محتاج","ولا يهمك","cancel that","never mind","nevermind","don't need it","do not need it");boolean completion=hasAny(x,"بعت","اتبعث","خلصت","تم","sent it","done","completed");boolean commitment=outgoing&&hasAny(x,"حاضر","تمام ه","هبعت","هعمل","هكلم","okay i'll","ok i'll","i will","will send");boolean explicitRequest=hasAny(x,"ابعتلي","ابعته","ممكن تبعت","محتاج منك","عاوز منك","كلمني","فكرني","send me","please send","can you send","could you send","call me","remind me");boolean question=x.contains("?")||x.contains("؟");boolean modelAction="ACTION".equalsIgnoreCase(finalDisposition);Intent intent=cancellation?Intent.CANCELLATION:completion&&outgoing?Intent.COMPLETION:commitment?Intent.COMMITMENT:explicitRequest?Intent.REQUEST:question?Intent.QUESTION:modelAction?Intent.FOLLOW_UP:Intent.INFORMATION;boolean actionExpected=!ongoing&&(explicitRequest||(modelAction&&!question));boolean responseExpected=intent==Intent.REQUEST||intent==Intent.QUESTION;double semanticConfidence=Math.max(confidence,modelConfidence(db,signalId));return new Result(signalId,thread,source,title,body,metadata,finalDisposition,intent,incoming,outgoing,actionExpected,responseExpected,cancellation,completion,ongoing,importance,semanticConfidence);
    }

    private static String finalDisposition(VaultDb db,long signalId){try{Cursor c=db.getReadableDatabase().query("relevance_evaluations",new String[]{"final_disposition"},"signal_id=? AND apply_status='APPLIED'",new String[]{String.valueOf(signalId)},null,null,null,"1");String x=c.moveToFirst()?n(c.getString(0)):"";c.close();return x;}catch(Throwable e){return"";}}
    private static double modelConfidence(VaultDb db,long signalId){try{Cursor c=db.getReadableDatabase().query("relevance_evaluations",new String[]{"final_confidence"},"signal_id=? AND apply_status='APPLIED'",new String[]{String.valueOf(signalId)},null,null,null,"1");double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}catch(Throwable e){return 0;}}
    private static boolean hasAny(String x,String... parts){for(String p:parts)if(x.contains(p.toLowerCase(Locale.ROOT)))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
}
