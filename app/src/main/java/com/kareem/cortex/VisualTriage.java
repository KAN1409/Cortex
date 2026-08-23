package com.kareem.cortex;

import java.util.Locale;
import java.util.regex.*;

/**
 * Ultra-cheap, fail-closed local gate before any cloud vision call.
 * This is deliberately conservative: uncertain private/account/document screens stay local
 * until the user explicitly overrides the block in the Visual Intelligence inspector.
 */
public final class VisualTriage {
    private static final Pattern CARD=Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern OTP=Pattern.compile("(?i)(?:otp|one[- ]?time|verification code|security code|auth(?:entication)? code|device code|رمز التحقق|كود التحقق|رمز الدخول|كود الدخول|رمز المصادقة|كود المصادقة)[^A-Z0-9]{0,28}([A-Z0-9-]{4,12})");
    private static final Pattern DEVICE_CODE=Pattern.compile("(?i)\\b[A-Z0-9]{4}-[A-Z0-9]{4}\\b");
    private static final Pattern LONG_ID=Pattern.compile("(?<!\\d)\\d{12,20}(?!\\d)");
    private static final Pattern EMAIL=Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE=Pattern.compile("(?<!\\d)(?:\\+?20|0)?1[0125][ -]?\\d{8}(?!\\d)");
    private static final Pattern CHAT_TIME=Pattern.compile("(?i)\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b");

    public static final class Result {
        public int valueScore;
        public boolean sensitive,ephemeral;
        public String reason="",privacy="safe",signals="";
        public int privacyScore;
    }
    private VisualTriage(){}

    public static Result evaluate(KnowledgeItem k){
        Result r=new Result();
        String title=nz(k.title),body=nz(k.extractedText),summary=nz(k.summary),category=nz(k.category),tags=nz(k.tags),source=nz(k.source);
        String t=(title+"\n"+body+"\n"+summary+"\n"+category+"\n"+tags+"\n"+source).trim();
        String n=t.toLowerCase(Locale.ROOT);
        r.valueScore=55;

        // Explicit credentials / temporary authorization secrets.
        if(OTP.matcher(t).find() || (DEVICE_CODE.matcher(t).find() && containsAny(n,"github","login","device","auth","authorize","one-time","one time","verification","كود","رمز","تسجيل الدخول"))){
            return block(r,100,"auth_secret","Possible OTP / temporary authentication code detected locally");
        }
        Matcher cm=CARD.matcher(t);while(cm.find()){
            String digits=cm.group().replaceAll("\\D","");
            if(digits.length()>=13&&digits.length()<=19)return block(r,100,"financial_number","Possible card/account number detected locally");
        }
        if(containsAny(n,"password","passcode","pin code","cvv","كلمة المرور","الرقم السري","رمز pin","كلمة السر"))
            return block(r,100,"credential_language","Password/passcode language detected locally");

        // Government, banking and identity documents. Long identifiers only become a blocker
        // when the surrounding screen looks like a document/account context.
        boolean government=containsAny(n,"وزارة المالية","مصلحة الضرائب","بطاقة ضريب","الرقم القومي","رقم قومي","بطاقة شخصية","بطاقة رقم قومي","جواز سفر","passport","national id","tax card","tax id","tax identification","commercial register","سجل تجاري","government id");
        boolean finance=containsAny(n,"instapay","bank account","banking","iban","swift","credit card","debit card","حساب بنكي","رقم الحساب","بطاقة ائتمان","محفظة","wallet balance");
        boolean longId=LONG_ID.matcher(t).find();
        if(government || finance || (longId && containsAny(n,"document","receipt","invoice","account","identity","هوية","بطاقة","ضرائب","بنك","bank")))
            return block(r,95,government?"government_document":"financial_or_identity","Government/financial/identity information detected locally");

        // Private communications and account/profile screens are local-only by default.
        boolean chatShape=n.contains("message")&&CHAT_TIME.matcher(t).find();
        boolean privateChat=containsAny(n,"whatsapp","telegram","messenger","signal","direct message","dm ","chat screen","chats","رسالة","محادثة")||chatShape;
        boolean mail=containsAny(n,"gmail","outlook","inbox","compose","email account","mail.google.com");
        boolean accountScreen=containsAny(n,"my account","account settings","two-factor authentication","passkeys","mobile number","birthday","username","partner connections") && (EMAIL.matcher(t).find()||PHONE.matcher(t).find());
        if(privateChat)return block(r,88,"private_conversation","Private conversation detected; cloud vision requires explicit user override");
        if(mail)return block(r,85,"private_mail","Email/inbox content detected; cloud vision requires explicit user override");
        if(accountScreen)return block(r,92,"account_profile","Account/profile information detected locally");

        // PII combinations: one email/phone alone is not always sensitive, but multiple personal
        // signals or contact fields on a profile/document should fail closed.
        boolean email=EMAIL.matcher(t).find(),phone=PHONE.matcher(t).find();
        if((email&&phone) || ((email||phone)&&containsAny(n,"name","birthday","address","mobile number","اسم","تاريخ الميلاد","العنوان","رقم الموبايل")))
            return block(r,82,"personal_profile","Personal contact/profile data detected locally");

        if(containsAny(n,"install this app?","package installer","screenshot saved","permission controller")){r.valueScore=20;r.ephemeral=true;r.reason="Looks like transient system UI";r.signals="transient_system_ui";}
        if(n.contains("loading...")&&t.length()<180){r.valueScore=15;r.ephemeral=true;r.reason="Looks like a transient loading screen";r.signals="loading_screen";}
        if(t.length()>700)r.valueScore+=12;
        if(containsAny(n,"price","review","product","design","prompt","project","invoice","receipt","سعر","منتج","تصميم","مشروع"))r.valueScore+=15;
        r.valueScore=Math.max(0,Math.min(100,r.valueScore));
        if(r.reason.isEmpty())r.reason="No local privacy blocker; strong vision may add useful context";
        r.privacyScore=Math.max(r.privacyScore,10);
        return r;
    }

    private static Result block(Result r,int score,String signal,String reason){
        r.sensitive=true;r.privacy="local_only";r.privacyScore=score;r.signals=signal;r.reason=reason;return r;
    }
    private static boolean containsAny(String n,String...xs){for(String x:xs)if(n.contains(x.toLowerCase(Locale.ROOT)))return true;return false;}
    private static String nz(String s){return s==null?"":s;}
}
