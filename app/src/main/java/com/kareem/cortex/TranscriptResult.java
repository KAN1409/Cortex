package com.kareem.cortex;

import java.util.*;

public class TranscriptResult {
    public static class Segment {
        public long startMs,endMs; public String text; public float confidence;
        public Segment(long s,long e,String t,float c){startMs=s;endMs=e;text=t;confidence=c;}
    }
    public String text="",language="",engine="android_speech",version="1";
    public long durationMs=0;
    public final ArrayList<Segment> segments=new ArrayList<>();
}
