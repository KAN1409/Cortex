package com.kareem.cortex;

import java.util.Locale;
import java.util.regex.*;

/** Ultra-cheap local gate before any cloud vision call. */
public final class VisualTriage {
    private static final Pattern CARD=Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern OTP=Pattern.compile("(?i)(?:otp|one[- ]?time|verification code|security code|رمز التحقق|كود التحقق|رمز الدخول|كود الدخول)[^0-9]{0,24}([0-9]{4,8})");
    public static final class Result {public int valueScore;public boolean sensitive,ephemeral;public String reason="",privacy="safe";}
    private VisualTriage(){}

    public static Result evaluate(KnowledgeItem k){Result r=new Result();String t=((k.title==null?"":k.title)+"\n"+(k.extractedText==null?"":k.extractedText)).trim();String n=t.toLowerCase(Locale.ROOT);r.valueScore=55;
        if(OTP.matcher(t).find()){r.sensitive=true;r.privacy="local_only";r.reason="Possible OTP / verification code detected locally";return r;}
        Matcher cm=CARD.matcher(t);while(cm.find()){String digits=cm.group().replaceAll("\\D","");if(digits.length()>=13&&digits.length()<=19){r.sensitive=true;r.privacy="local_only";r.reason="Possible card/account number detected locally";return r;}}
        if(n.contains("password")||n.contains("passcode")||n.contains("كلمة المرور")||n.contains("الرقم السري")){r.sensitive=true;r.privacy="local_only";r.reason="Password/passcode language detected locally";return r;}
        if(n.contains("install this app?")||n.contains("package installer")||n.contains("screenshot saved")||n.contains("permission controller")){r.valueScore=20;r.ephemeral=true;r.reason="Looks like transient system UI";}
        if(n.contains("loading...")&&t.length()<180){r.valueScore=15;r.ephemeral=true;r.reason="Looks like a transient loading screen";}
        if(t.length()>700)r.valueScore+=12;if(n.contains("price")||n.contains("review")||n.contains("product")||n.contains("design")||n.contains("prompt")||n.contains("project")||n.contains("invoice")||n.contains("receipt"))r.valueScore+=15;
        r.valueScore=Math.max(0,Math.min(100,r.valueScore));if(r.reason.isEmpty())r.reason="No local privacy blocker; vision analysis may add useful context";return r;}
}
