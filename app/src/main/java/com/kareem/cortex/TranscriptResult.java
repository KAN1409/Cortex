package com.kareem.cortex;

import java.util.*;

public class TranscriptResult {
    public static class Segment {
        public long startMs,endMs;
        public String text;
        public float confidence;

        public Segment(long s,long e,String t,float c){
            startMs=s;
            endMs=e;
            text=t==null?"":t;
            confidence=c;
        }
    }

    public String text="";
    public String language="";
    public String engine="android_speech";
    public String version="1";

    public long durationMs=0;
    public long processedDurationMs=0;
    public double coverage=0.0;

    public String rawTranscript="";
    public String providerMergedTranscript="";
    public String qualityWarning="";
    public String rawProviderResponse="";

    public final ArrayList<Segment> segments=new ArrayList<>();
}
