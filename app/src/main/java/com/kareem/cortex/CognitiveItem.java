package com.kareem.cortex;

import org.json.JSONObject;

/** One validated piece of knowledge proposed by a cognitive brain. */
public final class CognitiveItem {
    public final CognitiveKind kind;
    public final String summary;
    public final int importance;
    public final int urgency;
    public final String person;
    public final long dueAt;
    public final boolean requiresUserAction;
    public final boolean requiresFollowUp;
    public final boolean requiresContentExtraction;

    public CognitiveItem(CognitiveKind kind,String summary,int importance,int urgency,String person,long dueAt,
                         boolean requiresUserAction,boolean requiresFollowUp,boolean requiresContentExtraction){
        this.kind=kind;this.summary=n(summary);this.importance=clamp(importance);this.urgency=clamp(urgency);
        this.person=n(person);this.dueAt=Math.max(0,dueAt);this.requiresUserAction=requiresUserAction;
        this.requiresFollowUp=requiresFollowUp;this.requiresContentExtraction=requiresContentExtraction;
    }

    public JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("kind",kind==null?JSONObject.NULL:kind.name());o.put("summary",summary);o.put("importance",importance);o.put("urgency",urgency);o.put("person",person.isEmpty()?JSONObject.NULL:person);o.put("due_at",dueAt>0?dueAt:JSONObject.NULL);o.put("requires_user_action",requiresUserAction);o.put("requires_follow_up",requiresFollowUp);o.put("requires_content_extraction",requiresContentExtraction);}catch(Throwable ignored){}return o;}
    private static int clamp(int x){return Math.max(0,Math.min(100,x));}
    private static String n(String s){return s==null?"":s.trim();}
}
