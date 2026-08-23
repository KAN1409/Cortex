package com.kareem.cortex;

import java.util.Locale;

/**
 * Product boundary between raw device signals and durable Cortex memory.
 *
 * The prompt below is the canonical policy for the model-based adjudicator that will
 * run on grouped/batched signals. evaluateFast() is deliberately conservative: it
 * removes only obvious noise and promotes only high-confidence durable information.
 */
public final class MasterRelevanceFilter {
    public enum Disposition { IGNORE, CONTEXT, MEMORY, ACTION, WAITING, DECISION }

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
        public final String reason;
        public Decision(Disposition disposition,int importance,String reason){this.disposition=disposition;this.importance=importance;this.reason=reason;}
        public boolean durable(){return disposition==Disposition.MEMORY||disposition==Disposition.ACTION||disposition==Disposition.WAITING||disposition==Disposition.DECISION;}
    }

    /** Canonical policy for the batch/thread intelligence filter. Keep this centralized. */
    public static final String MASTER_PROMPT =
        "You are Cortex Relevance Governor, the single filtering boundary between RAW SIGNALS and the user's durable personal intelligence.\n"+
        "Your job is NOT to summarize everything. Your job is to decide what deserves attention, temporary context, durable memory, or no retention.\n\n"+
        "CORE PRINCIPLE\n"+
        "Cortex should feel like a selective human memory, not a surveillance log, notification archive, telemetry database, or task generator.\n"+
        "Preserve information that can change a future decision, answer a future question, prove what happened, represent a commitment, or maintain important human/project context.\n"+
        "Discard repetitive machine state and low-value UI chatter.\n\n"+
        "INPUT MAY INCLUDE\n"+
        "notifications, messages, email previews, calendar events, screenshots/OCR, voice transcripts, shared files/links, calls, downloads, device state and app events.\n\n"+
        "OUTPUT EXACTLY ONE DISPOSITION PER SIGNAL OR GROUP\n"+
        "IGNORE: no future value. Do not promote to Vault.\n"+
        "CONTEXT: temporarily useful for threading/understanding, but not a standalone Vault memory.\n"+
        "MEMORY: durable fact/event/reference worth recalling later.\n"+
        "ACTION: a real commitment/request requiring the user to do something.\n"+
        "WAITING: the user is waiting for another person, result, delivery, approval, appointment outcome, response or external event.\n"+
        "DECISION: a meaningful choice, approval, rejection, preference or conclusion that should be remembered.\n\n"+
        "ALWAYS IGNORE OR AGGREGATE, NEVER CREATE STANDALONE MEMORIES FOR\n"+
        "battery percentage, charging state, estimated time to full, signal strength, Wi-Fi connected/disconnected chatter, Bluetooth state, VPN state, media playback controls, typing indicators, app sync/progress, background service status, keyboard notices, clipboard notices, repeated system status, duplicate notifications, transient progress percentages, routine 'running in background' notices.\n"+
        "Example: 'Charging 11% (1 h 30 m until full)' => IGNORE.\n\n"+
        "CONTEXT BY DEFAULT\n"+
        "ordinary chat messages, ordinary email previews, social notifications and app updates should first become thread context. Promote only when the grouped context contains a durable fact, request, commitment, decision, deadline, address/reference, meaningful relationship/project development, or other future value.\n\n"+
        "HIGH-VALUE DURABLE SIGNALS\n"+
        "appointments and bookings; payments/transactions with meaningful reference; security/account changes; medical/test/result logistics; travel reservations; deliveries when status materially changes; deadlines; approvals/rejections; addresses; reference/order numbers; commitments; decisions; important documents; project changes; meaningful personal updates.\n\n"+
        "ACTION RULES\n"+
        "Do not infer a task merely because text contains a verb. ACTION requires clear responsibility or intent for the user. If uncertain, use CONTEXT.\n"+
        "WAITING requires evidence that completion depends on somebody/something else.\n\n"+
        "SCREENSHOTS AND IMAGES\n"+
        "A screenshot is evidence by default, not a task. OCR text must never automatically become ACTION without strong explicit evidence.\n\n"+
        "CALENDAR\n"+
        "Personal appointments/meetings may be MEMORY. Generic public holidays and imported calendar boilerplate are CONTEXT or IGNORE unless the user interacted with them or they affect a plan. Strip provider instructions/boilerplate.\n\n"+
        "DEDUPLICATION AND GROUPING\n"+
        "Prefer one evolving thread/event over many nearly identical items. Repeated state changes should update/aggregate rather than create new memories.\n\n"+
        "PRIVACY\n"+
        "OTP/CVV/PIN/password-like secrets must never become durable semantic memory. They may be short-lived CONTEXT only when operationally needed.\n\n"+
        "IMPORTANCE SCORE\n"+
        "0-19 noise, 20-39 temporary context, 40-59 useful memory, 60-79 important follow-up/decision, 80-100 urgent/high consequence.\n\n"+
        "WHEN UNCERTAIN\n"+
        "Choose CONTEXT, not MEMORY or ACTION. Cortex can promote later after more evidence arrives. Never fabricate intent, people, dates or tasks.";

    private MasterRelevanceFilter(){}

    /** Cheap first-stage filter. Ambiguous signals remain CONTEXT for the batch master filter. */
    public static Decision evaluateFast(Signal s){
        String text=low(s.text()),src=low(s.source);
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
        return d(Disposition.CONTEXT,28,"ambiguous raw signal; defer to master batch filter");
    }

    private static boolean deviceNoise(String t,String src,boolean ongoing){
        if(has(t,"charging","until full","time to full","battery level","battery saver","fully charged","الشحن","البطارية"))return true;
        if(has(t,"usb debugging","android system","vpn is active","connected to wifi","wi-fi connected","bluetooth connected","syncing","running in the background","is running in background"))return true;
        return src.contains("systemui")&&(ongoing||has(t,"battery","charging","usb","hotspot"));
    }
    private static boolean mediaNoise(String t,String src,boolean ongoing){return ongoing&&(has(t,"pause","playing","now playing","media output")||src.contains("spotify")||src.contains("youtube.music"));}
    private static boolean secret(String t){return has(t,"otp","one-time password","one time password","verification code","cvv","pin code","رمز التحقق","كود التحقق","كلمة السر");}
    private static boolean security(String t){return has(t,"security alert","new sign-in","new login","password changed","password was changed","device signed in","unusual activity","محاولة تسجيل دخول","تسجيل دخول جديد","تنبيه أمان");}
    private static boolean payment(String t){return has(t,"payment received","payment sent","transaction","purchase","card charged","transfer received","transfer sent","تم خصم","تم تحويل","عملية شراء","تحويل بنكي");}
    private static boolean appointment(String t){return has(t,"appointment","booking confirmed","reservation confirmed","meeting confirmed","موعدك","تم تأكيد الحجز","الحجز مؤكد");}
    private static boolean delivery(String t){return has(t,"delivered","out for delivery","ready for pickup","order cancelled","order canceled","تم التوصيل","خرج للتوصيل","جاهز للاستلام","تم إلغاء الطلب");}
    private static boolean missedCall(String t){return has(t,"missed call","مكالمة فائتة");}
    private static boolean isMessagingSource(String s){return has(s,"whatsapp","telegram","messenger","signal","messages","sms");}
    private static boolean isMailSource(String s){return has(s,"gmail","outlook","mail");}
    private static boolean has(String s,String... xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}
    private static String n(String s){return s==null?"":s.trim();}
    private static Decision d(Disposition x,int score,String reason){return new Decision(x,score,reason);}
}
