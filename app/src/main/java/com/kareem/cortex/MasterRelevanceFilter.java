package com.kareem.cortex;

import java.util.Locale;

/**
 * Product boundary between raw device signals and durable Cortex intelligence.
 *
 * evaluateFast() is the cheap hard gate. evaluateThread() is the first real-world
 * grouped communication policy. Ambiguity becomes REVIEW rather than a guessed
 * memory/action.
 */
public final class MasterRelevanceFilter {
    public enum Disposition { IGNORE, CONTEXT, REVIEW, MEMORY, ACTION, WAITING, DECISION }

    public static final class Signal {
        public final String kind,source,title,body,metadataJson;
        public final long occurredAt;
        public final boolean ongoing;
        public Signal(String kind,String source,String title,String body,String metadataJson,long occurredAt,boolean ongoing){
            this.kind=n(kind);this.source=n(source);this.title=n(title);this.body=n(body);this.metadataJson=n(metadataJson);this.occurredAt=occurredAt;this.ongoing=ongoing;
        }
        public String text(){return (title+"\n"+body).trim();}
    }

    public static final class Decision {
        public final Disposition disposition;
        public final int importance;
        public final String reason,candidateKind;
        public final double confidence;
        public Decision(Disposition disposition,int importance,String reason){this(disposition,importance,reason,"",defaultConfidence(disposition));}
        public Decision(Disposition disposition,int importance,String reason,String candidateKind){this(disposition,importance,reason,candidateKind,defaultConfidence(disposition));}
        public Decision(Disposition disposition,int importance,String reason,String candidateKind,double confidence){this.disposition=disposition;this.importance=importance;this.reason=n(reason);this.candidateKind=n(candidateKind).toUpperCase(Locale.ROOT);this.confidence=Math.max(0,Math.min(1,confidence));}
        public boolean durable(){return disposition==Disposition.MEMORY||disposition==Disposition.ACTION||disposition==Disposition.WAITING||disposition==Disposition.DECISION;}
        public boolean reviewable(){return disposition==Disposition.REVIEW&&!candidateKind.isEmpty();}
    }

    public static final String MASTER_PROMPT =
        "You are Cortex Relevance Governor, the single filtering boundary between RAW SIGNALS and the user's durable personal intelligence.\n"+
        "Your job is NOT to summarize everything. Decide what deserves IGNORE, CONTEXT, REVIEW, MEMORY, ACTION, WAITING or DECISION.\n\n"+
        "CORE PRINCIPLE\n"+
        "Cortex should feel like selective human memory, not a surveillance log, notification archive, telemetry database, or task generator.\n"+
        "Preserve information that can change a future decision, answer a future question, prove what happened, represent a commitment, or maintain important human/project context.\n"+
        "Discard repetitive machine state and low-value UI chatter.\n\n"+
        "REVIEW\n"+
        "Use REVIEW when there is plausible value but intent/responsibility/identity is not confident enough for durable promotion. Include the most likely candidate kind such as ACTION, WAITING or DECISION. Never guess when a user confirmation can resolve it.\n\n"+
        "ALWAYS IGNORE OR AGGREGATE, NEVER CREATE STANDALONE MEMORIES FOR\n"+
        "battery percentage, charging state, estimated time to full, signal strength, Wi-Fi connected/disconnected chatter, Bluetooth state, VPN state, media playback controls, typing indicators, app sync/progress, background service status, keyboard notices, clipboard notices, repeated system status, duplicate notifications, transient progress percentages, routine running-in-background notices.\n"+
        "Example: Charging 11% (1 h 30 m until full) => IGNORE.\n\n"+
        "CONTEXT BY DEFAULT\n"+
        "ordinary chat messages, ordinary email previews, social notifications and app updates should first become thread context. Promote only when grouped context contains a durable fact, request, commitment, decision, deadline, address/reference, meaningful relationship/project development, or other future value.\n\n"+
        "ACTION RULES\n"+
        "ACTION requires clear responsibility or intent for the user. WAITING requires evidence that completion depends on somebody/something else. If either is plausible but unclear, use REVIEW.\n\n"+
        "SCREENSHOTS AND IMAGES\n"+
        "A screenshot is evidence by default, not a task. OCR text must never automatically become ACTION without strong explicit evidence.\n\n"+
        "CALENDAR\n"+
        "Personal appointments/meetings may be MEMORY. Generic public holidays and imported calendar boilerplate are CONTEXT or IGNORE unless the user interacted with them or they affect a plan. Strip provider instructions/boilerplate.\n\n"+
        "PRIVACY\n"+
        "OTP/CVV/PIN/password-like secrets must never become durable semantic memory. They may be short-lived CONTEXT only when operationally needed.\n\n"+
        "IMPORTANCE SCORE\n"+
        "0-19 noise, 20-39 temporary context, 40-59 useful/review-worthy, 60-79 important follow-up/decision, 80-100 urgent/high consequence.\n\n"+
        "WHEN UNCERTAIN\n"+
        "Choose CONTEXT if there is weak evidence. Choose REVIEW if there is a concrete plausible interpretation that needs confirmation. Never fabricate intent, people, dates or tasks.";

    private MasterRelevanceFilter(){}

    public static Decision evaluateFast(Signal s){
        String text=ruleNorm(s.text()),src=low(s.source);
        if(text.isEmpty())return d(Disposition.IGNORE,0,"empty signal");
        if(secret(text))return d(Disposition.CONTEXT,25,"sensitive one-time credential; short-lived context only");
        if(deviceNoise(text,src,s.ongoing))return d(Disposition.IGNORE,3,"ephemeral device/system state");
        if(mediaNoise(text,src,s.ongoing))return d(Disposition.IGNORE,4,"ongoing media/UI state");
        if(security(text))return d(Disposition.MEMORY,78,"security or account event with future value");
        if(payment(text))return d(Disposition.MEMORY,68,"transaction/payment evidence");
        if(appointment(text))return d(Disposition.MEMORY,64,"appointment/booking information");
        if(delivery(text))return d(Disposition.MEMORY,55,"material delivery/order state");
        if(missedCall(text))return d(Disposition.MEMORY,52,"missed communication event");
        if(isMessagingSource(src))return d(Disposition.CONTEXT,32,"message context; wait for thread-level adjudication");
        if(isMailSource(src))return d(Disposition.CONTEXT,34,"mail context; wait for thread-level adjudication");
        if(s.ongoing)return d(Disposition.CONTEXT,20,"ongoing app signal; keep temporary context only");
        return d(Disposition.CONTEXT,28,"ambiguous raw signal; defer to master grouped filter");
    }

    public static Decision evaluateThread(String text){return evaluateThread(text,text);}

    /**
     * The newest message anchors a new durable event. Recent context can strengthen an
     * ambiguous interpretation, but historical text alone is downgraded to REVIEW so an
     * old request is not recreated whenever a later message arrives.
     */
    public static Decision evaluateThread(String latestText,String recentContext){
        String latest=ruleNorm(latestText);if(latest.isEmpty())return d(Disposition.CONTEXT,25,"empty or unusable thread text");
        if(secret(latest))return d(Disposition.CONTEXT,25,"sensitive credential; never derive durable intelligence");
        Decision direct=evaluateRuleText(latest);
        if(direct.disposition!=Disposition.CONTEXT)return direct;

        String context=ruleNorm(recentContext);
        if(!context.isEmpty()&&!context.equals(latest)){
            Decision historical=evaluateRuleText(context);
            if(historical.durable())return review(historical.disposition.name(),Math.max(45,historical.importance-10),"recent thread context suggests "+historical.disposition.name().toLowerCase(Locale.ROOT)+", but the newest message alone does not establish a new durable event",Math.min(0.67,historical.confidence));
            if(historical.reviewable())return new Decision(Disposition.REVIEW,historical.importance,"recent thread context: "+historical.reason,historical.candidateKind,Math.min(0.64,historical.confidence));
        }
        return d(Disposition.CONTEXT,34,"ordinary thread context; no new durable interpretation yet");
    }

    private static Decision evaluateRuleText(String t){
        if(has(t,"ممكن تبعت","ممكن تبعتلي","ابعتلي","ابعت لي","محتاج منك","محتاجك تبعت","لو سمحت ابعت","متنساش تبعت","please send","can you send","could you send","need you to send","please review","can you review","could you review","please confirm","can you confirm"))
            return new Decision(Disposition.ACTION,68,"explicit incoming request directed to the user","",0.90);
        if(has(t,"هبعتلك","هابعتلك","هبعتهولك","هراجع وارجعلك","هراجع و ارد عليك","هرد عليك","هرجعلك","هكلمك لما","i'll send you","i will send you","i'll get back to you","i will get back to you","i'll reply","i will reply"))
            return new Decision(Disposition.WAITING,64,"explicit commitment from the other party","",0.88);
        if(has(t,"تمت الموافقه","تم الرفض","موافق علي","approved","has been approved","rejected","has been rejected"))
            return new Decision(Disposition.DECISION,66,"explicit approval or rejection in the thread","",0.87);
        if(has(t,"لازم يتبعت","لازم تبعت","المفروض تبعت","يفضل تبعت","لما تقدر ابعت","when you can send","we need the drawing","we need the file","needs your review","needs your approval","محتاج مراجعتك","محتاج موافقتك"))
            return review("ACTION",52,"possible user responsibility, but direction/ownership is not explicit enough",0.64);
        if(has(t,"هحاول ابعت","مفروض ابعتلك","المفروض يرد","مفروض يرد","should get back to you","should reply","probably send you","expect a reply"))
            return review("WAITING",50,"possible external commitment or expected response, but not firm enough",0.62);
        if(has(t,"غالبا هنمشي علي","مبدئيا موافق","probably approved","likely approved","tentatively approved"))
            return review("DECISION",51,"possible decision, but wording is tentative",0.61);
        return d(Disposition.CONTEXT,34,"ordinary thread context");
    }

    /** Arabic/English normalization for deterministic matching only. */
    public static String ruleNorm(String s){return LocalSemanticEmbedder.norm(s);}

    private static boolean deviceNoise(String t,String src,boolean ongoing){
        if(has(t,"charging","until full","time to full","battery level","battery saver","fully charged","الشحن","البطاريه"))return true;
        if(has(t,"usb debugging","android system","vpn is active","connected to wifi","wi-fi connected","bluetooth connected","syncing","running in the background","is running in background"))return true;
        return src.contains("systemui")&&(ongoing||has(t,"battery","charging","usb","hotspot"));
    }
    private static boolean mediaNoise(String t,String src,boolean ongoing){return ongoing&&(has(t,"pause","playing","now playing","media output")||src.contains("spotify")||src.contains("youtube.music"));}
    private static boolean secret(String t){return has(t,"otp","one-time password","one time password","verification code","cvv","pin code","رمز التحقق","كود التحقق","كلمه السر");}
    private static boolean security(String t){return has(t,"security alert","new sign-in","new login","password changed","password was changed","device signed in","unusual activity","محاوله تسجيل دخول","تسجيل دخول جديد","تنبيه امان");}
    private static boolean payment(String t){return has(t,"payment received","payment sent","transaction","purchase","card charged","transfer received","transfer sent","تم خصم","تم تحويل","عمليه شراء","تحويل بنكي");}
    private static boolean appointment(String t){return has(t,"appointment","booking confirmed","reservation confirmed","meeting confirmed","موعدك","تم تاكيد الحجز","الحجز موكد");}
    private static boolean delivery(String t){return has(t,"delivered","out for delivery","ready for pickup","order cancelled","order canceled","تم التوصيل","خرج للتوصيل","جاهز للاستلام","تم الغاء الطلب");}
    private static boolean missedCall(String t){return has(t,"missed call","مكالمه فايته","مكالمه فائته");}
    private static boolean isMessagingSource(String s){return has(s,"whatsapp","telegram","messenger","signal","messages","sms");}
    private static boolean isMailSource(String s){return has(s,"gmail","outlook","mail");}
    private static boolean has(String s,String... xs){for(String x:xs)if(s.contains(ruleNorm(x)))return true;return false;}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}
    private static String n(String s){return s==null?"":s.trim();}
    private static double defaultConfidence(Disposition d){if(d==Disposition.IGNORE)return 0.98;if(d==Disposition.MEMORY)return 0.82;if(d==Disposition.ACTION)return 0.90;if(d==Disposition.WAITING)return 0.88;if(d==Disposition.DECISION)return 0.87;if(d==Disposition.REVIEW)return 0.62;return 0.55;}
    private static Decision d(Disposition x,int score,String reason){return new Decision(x,score,reason);}
    private static Decision review(String candidate,int score,String reason,double confidence){return new Decision(Disposition.REVIEW,score,reason,candidate,confidence);}
}
