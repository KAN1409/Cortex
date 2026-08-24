package com.kareem.cortex;

import android.telephony.PhoneNumberUtils;

/** Stable identity normalization for contact phone dedupe. Display text remains untouched. */
public final class PhoneNumberNormalizer {
    private PhoneNumberNormalizer(){}

    public static String canonical(String raw){
        String s=raw==null?"":raw.trim();if(s.isEmpty())return"";
        // Android's platform phone parser handles Egyptian mobile/landline local vs +20 forms without
        // adding another dependency. Keep digits-only fallback for extensions/odd imported values.
        try{String e164=PhoneNumberUtils.formatNumberToE164(s,"EG");if(e164!=null&&!e164.trim().isEmpty())return digits(e164);}
        catch(Throwable ignored){}
        String d=digits(s);if(d.startsWith("00")&&d.length()>4)d=d.substring(2);
        if(d.matches("01[0125][0-9]{8}"))return"20"+d.substring(1);
        if(d.matches("201[0125][0-9]{8}"))return d;
        return d;
    }
    private static String digits(String s){StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>='0'&&c<='9')b.append(c);}return b.toString();}
}
