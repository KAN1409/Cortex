package com.kareem.cortex;

public class KnowledgeItem {
    public long id;
    public String type, source, title, rawText, extractedText, summary, category, tags;
    public String attachmentPath, status, fingerprint, analysisError, metadataJson;
    public long createdAt, updatedAt;

    public KnowledgeItem(long id, String type, String source, String title, String rawText,
                         String extractedText, String summary, String category, String tags,
                         String attachmentPath, String status, String fingerprint,
                         String analysisError, String metadataJson, long createdAt, long updatedAt) {
        this.id=id; this.type=type; this.source=source; this.title=title; this.rawText=rawText;
        this.extractedText=extractedText; this.summary=summary; this.category=category; this.tags=tags;
        this.attachmentPath=attachmentPath; this.status=status; this.fingerprint=fingerprint;
        this.analysisError=analysisError; this.metadataJson=metadataJson;
        this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
}
