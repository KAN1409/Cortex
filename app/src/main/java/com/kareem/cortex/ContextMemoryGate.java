package com.kareem.cortex;

import org.json.JSONObject;
import java.util.Locale;

/**
 * Memory is downstream of relevance and Context.
 *
 * MasterRelevanceFilter decides what a signal means. This gate decides how long that evidence
 * deserves to survive. A current Context may strengthen provenance and retention, but it can never
 * turn ordinary phone/app activity into a durable memory or invent user intent.
 */
public final class ContextMemoryGate {
    public enum Tier { EPHEMERAL, CONTEXTUAL, DURABLE }

    public static final class Decision {
        public final Tier tier;
        public final long contextId;
        public final double contextConfidence;
        public final int effectiveImportance;
        public final String reason;
        Decision(Tier tier,long contextId,double contextConfidence,int importance,String reason){
            this.tier=tier;this.contextId=Math.max(0,contextId);this.contextConfidence=clamp(contextConfidence);
            this.effectiveImportance=Math.max(0,Math.min(100,importance));this.reason=n(reason);
        }
        public boolean durable(){return tier==Tier.DURABLE;}
        public boolean contextual(){return tier==Tier.CONTEXTUAL;}
    }

    private ContextMemoryGate(){}

    public static Decision evaluate(VaultDb db,MasterRelevanceFilter.Signal signal,MasterRelevanceFilter.Decision relevance,long threadId){
        if(relevance==null)return new Decision(Tier.EPHEMERAL,0,0,0,"missing relevance decision");
        ContextStateStore.ContextState context=safePrimary(db);
        long contextId=context==null?0:context.id;
        double contextConfidence=context==null?0:Math.max(context.confidence,context.stackConfidence);
        String kind=low(signal==null?"":signal.kind),source=low(signal==null?"":signal.source),text=MasterRelevanceFilter.ruleNorm(signal==null?"":signal.text());

        if(relevance.disposition==MasterRelevanceFilter.Disposition.IGNORE)
            return new Decision(Tier.EPHEMERAL,contextId,contextConfidence,relevance.importance,"relevance governor marked signal as noise");

        if(secret(text))
            return new Decision(Tier.EPHEMERAL,contextId,contextConfidence,Math.min(25,relevance.importance),"credential-like evidence may support the immediate operation only; never durable memory");

        // Perception/context sensors are evidence, not memories. Their job is to explain the live
        // situation; durable facts must come from an explicit semantic event, thread adjudication,
        // intentional capture, decision, action or waiting state.
        if(sensorOnly(kind,source)&&!relevance.durable())
            return new Decision(Tier.CONTEXTUAL,contextId,contextConfidence,Math.min(39,Math.max(20,relevance.importance)),"phone/screen/process evidence is context-only unless a separate durable semantic event is established");

        if(relevance.durable()){
            int importance=relevance.importance;
            String why="authoritative relevance decision permits durable memory";
            if(contextId>0&&contextConfidence>=0.72){
                // Context can add a small bounded salience boost; it never changes disposition.
                importance=Math.min(100,importance+Math.min(8,(int)Math.round((contextConfidence-.70)*20)));
                why+="; linked to active Context with bounded salience boost";
            }
            if(threadId>0)why+="; thread provenance preserved";
            return new Decision(Tier.DURABLE,contextId,contextConfidence,importance,why);
        }

        // REVIEW remains temporary until the ambiguity is resolved. CONTEXT stays contextual.
        return new Decision(Tier.CONTEXTUAL,contextId,contextConfidence,relevance.importance,
            relevance.disposition==MasterRelevanceFilter.Disposition.REVIEW?"ambiguous candidate stays contextual until review resolves it":"useful evidence belongs to the live/recent Context, not durable memory");
    }

    /** Attach useful raw evidence to the active Context without promoting it to the Vault. */
    public static void linkEvidence(VaultDb db,long signalId,Decision d){
        // Truth firewall: EPHEMERAL means ignored/noise or operation-only sensitive evidence. It may
        // exist transiently in the raw ledger, but it can never become Context authority.
        if(db==null||signalId<=0||d==null||d.contextId<=0||d.tier==Tier.EPHEMERAL)return;
        try{
            JSONObject meta=new JSONObject();meta.put("memory_tier",d.tier.name());meta.put("gate_reason",d.reason);meta.put("local_only",true);
            ContextStateStore.linkEvidence(db,"raw_signal",signalId,d.contextId,"supports_context",Math.max(.45,d.contextConfidence),meta);
        }catch(Throwable ignored){}
    }

    /** Attach the materialized memory back to the Context that made it relevant. */
    public static void linkPromotedMemory(VaultDb db,long itemId,Decision d){
        if(db==null||itemId<=0||d==null||d.contextId<=0)return;
        try{
            JSONObject meta=new JSONObject();meta.put("memory_tier",d.tier.name());meta.put("gate_reason",d.reason);meta.put("local_only",true);
            ContextStateStore.linkEvidence(db,"memory",itemId,d.contextId,"remembered_in_context",Math.max(.55,d.contextConfidence),meta);
        }catch(Throwable ignored){}
    }

    public static String provenanceJson(Decision d){
        if(d==null)return "{}";
        try{return new JSONObject().put("tier",d.tier.name()).put("context_id",d.contextId).put("context_confidence",d.contextConfidence).put("effective_importance",d.effectiveImportance).put("reason",d.reason).put("local_only",true).toString();}
        catch(Throwable ignored){return "{}";}
    }

    private static ContextStateStore.ContextState safePrimary(VaultDb db){try{return db==null?null:ContextStateStore.primary(db);}catch(Throwable ignored){return null;}}
    private static boolean sensorOnly(String kind,String source){return has(kind,"screen_context","phone_context","usage","accessibility","process","device_state")||has(source,"accessibility","usage_stats","shizuku_context","phone_context");}
    private static boolean secret(String t){return has(t,"otp","one-time password","one time password","verification code","cvv","pin code","رمز التحقق","كود التحقق","كلمه السر");}
    private static boolean has(String s,String...xs){String x=low(s);for(String q:xs)if(x.contains(low(q)))return true;return false;}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}
    private static String n(String s){return s==null?"":s.trim();}
}
