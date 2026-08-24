package com.kareem.cortex;

/** Stable identity normalization for contact phone dedupe. Display text remains untouched. */
public final class PhoneNumberNormalizer {
    private PhoneNumberNormalizer(){}

    public static String canonical(String raw){
        String s=raw==null?"":raw.trim();
        if(s.isEmpty())return "";
        String digits=s.replaceAll("[^0-9]","");
        if(digits.startsWith("00")&&digits.length()>4)digits=digits.substring(2);

        // Normalize Egyptian mobile local/international variants to the same E.164-like digits.
        if(digits.matches("01[0125][0-9]{8}"))return "20"+digits.substring(1);
        if(digits.matches("201[0125][0-9]{8}"))return digits;
        return digits;
    }
}
