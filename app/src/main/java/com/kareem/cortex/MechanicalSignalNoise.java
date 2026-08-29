package com.kareem.cortex;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative deterministic machine-progress gate. This is noise filtering, never importance inference. */
public final class MechanicalSignalNoise {
    private static final Pattern COUNTER=Pattern.compile("(?i).*\\b\\d+\\s+(?:of|/)\\s*\\d+\\b.*");
    private static final Pattern PERCENT=Pattern.compile("(?i).*\\b\\d{1,3}%\\b.*");
    private MechanicalSignalNoise(){}

    public static boolean matches(MasterRelevanceFilter.Signal signal){
        if(signal==null)return false;
        String text=(n(signal.title)+" "+n(signal.body)).replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);
        if(text.isEmpty())return false;
        boolean operation=containsAny(text,
                "deleting item","deleting ","uploading ","downloading ","syncing ","processing ",
                "importing ","exporting ","backing up ","restoring ","scanning ","optimizing ",
                "moving item","copying item","preparing ");
        if(!operation)return false;
        return COUNTER.matcher(text).matches()||PERCENT.matcher(text).matches()
                ||containsAny(text,"progress","items remaining","remaining items");
    }

    private static boolean containsAny(String value,String... needles){for(String needle:needles)if(value.contains(needle))return true;return false;}
    private static String n(String s){return s==null?"":s;}
}
