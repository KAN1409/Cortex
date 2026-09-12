package com.kareem.cortex;

import java.util.Locale;

/** Conservative semantic-quality boundary between understood evidence and stateful world state. */
public final class CanonicalSemanticQualityGate {
    public static final String VERSION="canonical_semantic_quality_002";
    private CanonicalSemanticQualityGate(){}

    public static final class Result {
        public final boolean eligible; public final double confidence; public final String canonicalState,reason;
        Result(boolean eligible,double confidence,String state,String reason){this.eligible=eligible;this.confidence=clamp(confidence);this.canonicalState=state;this.reason=reason;}
    }

    public static Result evaluate(String sourceType,String technicalType,String semanticType,String intent,String subject,String summary,double confidence){
        String source=n(sourceType).toLowerCase(Locale.ROOT),tech=n(technicalType).toLowerCase(Locale.ROOT),type=n(semanticType).toLowerCase(Locale.ROOT),in=n(intent).toLowerCase(Locale.ROOT);
        String text=norm(n(subject)+" "+n(summary));
        boolean rawVisual=isVisual(source,tech);
        boolean evidenceOnly=type.equals("captured_artifact")||type.equals("evidence")||in.equals("evidence");
        boolean strong=strongSemantic(type,in,text);

        if(rawVisual&&(evidenceOnly||strong))
            return new Result(false,Math.min(confidence,.62),"rejected","visual/OCR evidence cannot establish a stateful obligation by itself");
        if(evidenceOnly&&!strong)
            return new Result(false,Math.min(confidence,.64),"rejected","capture/evidence event is not a stateful fact by itself");
        if(isBenignTechnical(sourceType,technicalType,semanticType,intent,subject,summary))
            return new Result(false,Math.min(confidence,.62),"rejected","routine technical/UI state stays below world-state situation creation");
        if(text.isEmpty())
            return new Result(false,Math.min(confidence,.40),"rejected","empty semantic content");
        if(lowInformation(text)&&!strong)
            return new Result(false,Math.min(confidence,.55),"rejected","low-information fragment is not sufficient for a situation");
        if(uiChrome(text)&&!strong)
            return new Result(false,Math.min(confidence,.58),"rejected","UI chrome/instructional text is evidence, not world state");
        if(confidence<.70&&!strong)
            return new Result(false,confidence,"rejected","semantic confidence below stateful correlation threshold");

        double adjusted=confidence;
        if(strong)adjusted=Math.max(.78,confidence);
        else if(type.contains("conversation_message"))adjusted=Math.min(.86,Math.max(.70,confidence));
        else adjusted=Math.min(.88,Math.max(.70,confidence));
        return new Result(true,adjusted,"supported",strong?"grounded stateful semantic signal":"sufficient semantic content for conservative correlation");
    }

    public static boolean isBenignTechnical(String sourceType,String technicalType,String semanticType,String intent,String subject,String summary){
        String source=n(sourceType).toLowerCase(Locale.ROOT),tech=n(technicalType).toLowerCase(Locale.ROOT),type=n(semanticType).toLowerCase(Locale.ROOT),in=n(intent).toLowerCase(Locale.ROOT),text=norm(n(subject)+" "+n(summary));
        if(strongSemantic(type,in,text))return false;
        if(containsAny(text,"failed","failure","error","warning","critical","security","unauthorized","missed call","فشل","خطأ","تحذير","أمني","مكالمة فائتة"))return false;
        if(type.equals("technical_state")||type.equals("technical_event"))return true;
        if(containsAny(tech,"progress","download","service_state","foreground","mediaongoing","equalizer","connection_state"))return true;
        if(type.equals("notification_event")&&containsAny(text,"screenshot saved","tap here to see your screenshot","download complete","waiting for network","connected to","global equalization","response ready","more notification"))return true;
        return source.equals("system")&&type.equals("notification_event")&&!containsAny(in,"request","action","security");
    }

    private static boolean strongSemantic(String type,String in,String text){
        return containsAny(type+" "+in,"action_request","request","commitment","decision","security","missed_call","call_event","alarm_event","reminder_event","deadline","payment","required_response")||StatefulMeaningPolicy.isSecurity(text);
    }
    private static boolean isVisual(String source,String tech){return "visual_evidence".equals(source)||containsAny(source,"picbrain","knowledge_v2","screen_understand","screenshot")||containsAny(tech,"ocr_evidence","visual");}
    private static boolean lowInformation(String text){String clean=text.replaceAll("[^\\p{L}\\p{N}]+"," ").trim();if(clean.length()<8)return true;String[] words=clean.split("\\s+");int meaningful=0,letters=0;for(String w:words){if(w.length()>=3)meaningful++;for(int i=0;i<w.length();i++)if(Character.isLetter(w.charAt(i)))letters++;}return meaningful<2||letters<5;}
    private static boolean uiChrome(String text){String s=text.toLowerCase(Locale.ROOT);int hits=0;String[] tokens={"add comment","search","settings","cancel","save","open","close","download","share","reply to chatgpt","unsent retry","home","pgup","pgdn","ctrl alt","followed by","find similar","ask cortex anything","capture","brief","people","brain"};for(String x:tokens)if(s.contains(x))hits++;return hits>=3||(hits>=2&&s.length()<120);}
    private static boolean containsAny(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String norm(String s){return n(s).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static String n(String s){return s==null?"":s.trim();}
    private static double clamp(double x){if(Double.isNaN(x))return 0;return Math.max(0,Math.min(1,x));}
}
