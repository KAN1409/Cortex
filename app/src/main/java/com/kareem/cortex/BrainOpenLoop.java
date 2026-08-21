package com.kareem.cortex;

public final class BrainOpenLoop {
    public final long actionId,itemId,createdAt;
    public final String action,due,title,category;
    public BrainOpenLoop(long actionId,long itemId,String action,String due,String title,String category,long createdAt){this.actionId=actionId;this.itemId=itemId;this.action=action;this.due=due;this.title=title;this.category=category;this.createdAt=createdAt;}
}
