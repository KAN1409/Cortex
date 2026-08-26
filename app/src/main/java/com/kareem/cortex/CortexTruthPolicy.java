package com.kareem.cortex;

import java.util.Locale;

/**
 * Product-reset truth boundary.
 *
 * Precision beats coverage: Cortex may omit an uncertain item, but it must not surface machine
 * state, financial events, incoming notifications, or ambiguous text as the user's own decision,
 * obligation, or working context.
 */
public final class CortexTruthPolicy {
    private CortexTruthPolicy(){}

    public static boolean confirmedDecision(String title,String body,String source){
        String t=norm(join(title,body)),s=low(source);
        if(t.isEmpty()||ambientContext(t,s)||financialEvent(t,s))return false;
        return explicitUserDecision(t);
    }

    public static boolean confirmedAction(String title,String body,String source,double confidence){
        String t=norm(join(title,body));if(t.isEmpty()||confidence<.78||ambientContext(t,low(source)))return false;
        if(financialEvent(t,low(source)))return false;
        return has(t,"action:","محتاج منك","محتاجك","ابعتلي","ابعت لي","ممكن تبعت","لو سمحت ابعت","متنساش","please send","can you send","could you send","need you to","please review","can you review","could you review","please confirm","can you confirm","needs your review","needs your approval","محتاج مراجعتك","محتاج موافقتك");
    }

    public static boolean confirmedWaiting(String title,String body,String source,double confidence){
        String t=norm(join(title,body));if(t.isEmpty()||confidence<.76||ambientContext(t,low(source)))return false;
        if(financialEvent(t,low(source)))return false;
        return has(t,"waiting:","هبعتلك","هابعتلك","هبعتهولك","هراجع وارجعلك","هراجع و ارد عليك","هرد عليك","هرجعلك","هكلمك لما","i'll send you","i will send you","i'll get back to you","i will get back to you","i'll reply","i will reply","waiting for","awaiting");
    }

    /** Ambient phone/UI state is evidence at most; it is never a working Context by itself. */
    public static boolean ambientContext(String text,String source){
        String t=norm(text),s=low(source);if(t.isEmpty())return true;
        if(has(t,"screenshot saved","tap here to see your screenshot","response ready","fully charged","battery level","charging","until full","time to full","1 more notification","more notifications","edge lighting","now playing","media output","running in the background","is running in background","syncing","backup in progress","backing up","couldn't complete backup","could not complete backup","weather","temperature","forecast","tap to view","tap to open","copied to clipboard"))return true;
        if((s.contains("systemui")||s.contains("weather")||s.contains("shazam"))&&!explicitWorkLanguage(t))return true;
        return false;
    }

    public static boolean explicitUserDecision(String text){
        String t=norm(text);if(t.isEmpty())return false;
        if(has(t,"قررت ","قررنا ","اختارت ","اخترت ","اخترنا ","اتفقنا ","اعتمدنا ","خلاص هنستخدم","خلاص هستخدم","خلاص هنمشي","خلاص همشي","انا موافق","أنا موافق","i decided","we decided","i chose","we chose","we agreed","i'm going with","i am going with","we're going with","we are going with","let's use","we'll use","we will use"))return true;
        return false;
    }

    public static boolean externalApprovalOrRejection(String text){
        String t=norm(text);return has(t,"تمت الموافقه","تمت الموافقة","تم الرفض","approved","has been approved","rejected","has been rejected");
    }

    public static boolean financialEvent(String text,String source){
        String t=norm(text),s=low(source);
        boolean money=has(t,"egp","usd","eur","جنيه","جم","transaction","purchase","payment","card","بطاق","معامله","معاملة","تم خصم","تم تحويل","رصيد","حد الائتمان","credit limit","declined","رفض المعامله","رفض المعاملة");
        boolean bankSource=s.contains("bank")||s.contains("cib")||s.contains("wallet")||s.contains("instapay");
        return bankSource||money&&has(t,"transaction","payment","card","بطاق","معامله","معاملة","رصيد","credit limit","egp","جنيه","جم");
    }

    private static boolean explicitWorkLanguage(String t){return has(t,"project","document","drawing","meeting","reply","send","review","approve","deadline","task","مشروع","رسومات","مستند","اجتماع","رد","ابعت","راجع","موافقه","موعد");}
    private static String join(String a,String b){String x=n(a),y=n(b);return x+(x.isEmpty()||y.isEmpty()?"":"\n")+y;}
    private static boolean has(String s,String...xs){String t=norm(s);for(String x:xs)if(t.contains(norm(x)))return true;return false;}
    private static String norm(String s){return LocalSemanticEmbedder.norm(n(s));}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}
    private static String n(String s){return s==null?"":s.trim();}
}
