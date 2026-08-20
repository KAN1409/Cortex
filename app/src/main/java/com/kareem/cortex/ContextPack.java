package com.kareem.cortex;

import java.util.ArrayList;

public class ContextPack {
    public final String title;
    public final String reason;
    public final ArrayList<KnowledgeItem> items;

    public ContextPack(String title, String reason, ArrayList<KnowledgeItem> items) {
        this.title = title;
        this.reason = reason;
        this.items = items;
    }
}
