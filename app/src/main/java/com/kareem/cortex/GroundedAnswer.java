package com.kareem.cortex;

import java.util.*;

public final class GroundedAnswer {
    public final String question;
    public final String answer;
    public final double confidence;
    public final ArrayList<SemanticHit> sources;
    public final ArrayList<String> openLoops;
    public final ArrayList<String> decisions;
    public GroundedAnswer(String q,String a,double c,ArrayList<SemanticHit> s,ArrayList<String> loops,ArrayList<String> decisions){
        question=q;answer=a;confidence=c;sources=s;openLoops=loops;this.decisions=decisions;
    }
}
