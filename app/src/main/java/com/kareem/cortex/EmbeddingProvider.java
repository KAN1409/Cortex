package com.kareem.cortex;

public interface EmbeddingProvider {
    String name();
    String version();
    int dimensions();
    float[] embed(String text);
}
