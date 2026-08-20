package com.kareem.cortex;

public class SemanticHit {
    public final KnowledgeItem item;
    public final double score;
    public final String snippet;
    public SemanticHit(KnowledgeItem item,double score,String snippet){this.item=item;this.score=score;this.snippet=snippet;}
}
