package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Collections;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Immediate V4 projection for analyzed user-intentional captures.
 *
 * Manual text/voice captures should not wait for a later historical backfill before they can
 * participate in Memory/Situation/Pulse. This bridge is idempotent with the normal V4 backfill:
 * it uses the same deterministic Evidence/Memory identities and legacy mappings.
 */
public final class CognitiveIntentionalRealtimeV4 {
    private CognitiveIntentionalRealtimeV4(){}

    public static Result project(VaultDb db,long itemId){
        if(db==null||itemId<=0)return Result.empty();
        CognitiveStoreV4.ensure(db);
        SQLiteDatabase sql=db.getReadableDatabase();
        Cursor c=sql.rawQuery(
                "SELECT type,COALESCE(source,''),COALESCE(title,''),COALESCE(raw_text,''),COALESCE(extracted_text,''),COALESCE(summary,''),COALESCE(attachment_path,''),COALESCE(status,''),COALESCE(fingerprint,''),COALESCE(metadata_json,''),created_at,updated_at FROM knowledge_items WHERE id=? LIMIT 1",
                new String[]{String.valueOf(itemId)});
        String type,source,title,raw,extracted,summary,attachment,status,fingerprint,metadata;long createdAt,updatedAt;
        try{
            if(!c.moveToFirst())return Result.empty();
            type=n(c.getString(0));source=n(c.getString(1));title=n(c.getString(2));raw=n(c.getString(3));extracted=n(c.getString(4));summary=n(c.getString(5));attachment=n(c.getString(6));status=n(c.getString(7));fingerprint=n(c.getString(8));metadata=n(c.getString(9));createdAt=c.getLong(10);updatedAt=c.getLong(11);
        }finally{c.close();}
        if(!intentionalSource(source))return Result.empty();
        if(createdAt<=0)createdAt=System.currentTimeMillis();

        String evidenceId=mapped(db,"knowledge_items",String.valueOf(itemId),"EVIDENCE");
        if(evidenceId.isEmpty()){
            String original=first(raw,title);
            String normalized=CognitiveIdentityV4.normalizeText(original);
            String hash=looksLikeSha256(fingerprint)?fingerprint.toLowerCase(Locale.ROOT):Fingerprint.text(type+"\n"+source+"\n"+original+"\n"+attachment);
            String externalId="legacy-knowledge-item:"+itemId;
            CognitiveDomainV4.EvidenceSourceType sourceType=CognitiveMemoryBackfillV4.sourceTypeForKnowledge(type);
            String expected=CognitiveIdentityV4.evidenceId(sourceType,source,externalId,hash,normalized,createdAt);
            CognitiveDomainV4.Evidence evidence=new CognitiveDomainV4.Evidence(
                    expected,sourceType,createdAt,updatedAt>0?updatedAt:createdAt,source,externalId,
                    original,normalized,hash,attachment.isEmpty()?null:attachment,
                    CognitiveDomainV4.Sensitivity.NORMAL,CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY,
                    CognitiveDomainV4.ProcessingState.READY);
            evidenceId=CognitiveStoreV4.putEvidence(db,evidence,legacyMetadata(itemId,metadata),createdAt+CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS);
            CognitiveStoreV4.mapLegacy(db,"knowledge_items",String.valueOf(itemId),CognitiveDomainV4.CanonicalObjectType.EVIDENCE,evidenceId,"FORWARD_INTENTIONAL");
        }

        if(!extracted.isEmpty())CognitiveStoreV4.appendEvidenceAnalysis(db,evidenceId,analysisKind(type),"legacy-knowledge-item","v1",extracted,null);
        if(!summary.isEmpty())CognitiveStoreV4.appendEvidenceAnalysis(db,evidenceId,"SUMMARY","legacy-knowledge-item","v1",summary,null);

        String body=first(raw,extracted,summary,title);
        if(body.isEmpty())return new Result("","",false,false);
        String memoryId=CognitiveIdentityV4.objectId("mem","legacy-knowledge-item|"+itemId);
        CognitiveDomainV4.Memory memory=new CognitiveDomainV4.Memory(
                memoryId,CognitiveMemoryBackfillV4.memoryKind(type),title,body,createdAt,null,
                Collections.singletonList(evidenceId),null,source,Collections.<String>emptyList(),
                importance(metadata),false,CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY);
        memoryId=CognitiveStoreV4.putMemory(db,memory,"legacy-knowledge-item:"+itemId,createdAt+CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS);
        CognitiveStoreV4.mapLegacy(db,"knowledge_items",String.valueOf(itemId),CognitiveDomainV4.CanonicalObjectType.MEMORY,memoryId,"FORWARD_INTENTIONAL");

        boolean genericMaterial=false;
        try{CognitiveSituationEngineV4.Result r=CognitiveSituationEngineV4.refresh(db);genericMaterial=r!=null&&r.materialChanges()>0;}catch(Throwable ignored){}
        boolean timedMaterial=ensureTimedIntentionalSituation(db,memoryId,title,body,source,createdAt,importance(metadata));
        try{CognitiveDeepBrainReconcilerV4.reconcile(db);}catch(Throwable ignored){}
        return new Result(evidenceId,memoryId,genericMaterial,timedMaterial);
    }

    /**
     * Intentional capture fallback: an explicit future day + clock is actionable evidence even when
     * the user did not literally say "remind me" or "appointment". This is deliberately restricted
     * to user-initiated capture sources and explicit temporal language.
     */
    static boolean ensureTimedIntentionalSituation(VaultDb db,String memoryId,String title,String body,String source,long startedAt,double importance){
        if(db==null||memoryId==null||memoryId.isEmpty()||!intentionalSource(source))return false;
        String low=normalizeSpokenClock(n(title+" "+body).toLowerCase(Locale.ROOT));
        if(!hasExplicitFutureDay(low))return false;
        long anchor=startedAt>0?startedAt:System.currentTimeMillis();
        Long eventAt=CognitiveSituationEngineV4.parseExplicitFutureTime(low,anchor);
        if(eventAt==null)return false;

        String semanticAnchor="memory:"+memoryId+":upcoming_event";
        String identity="situation|UPCOMING_EVENT|"+semanticAnchor+"|"+memoryId;
        String id=CognitiveIdentityV4.objectId("si",identity);
        Cursor existing=db.getReadableDatabase().rawQuery("SELECT 1 FROM v4_situations WHERE id=? LIMIT 1",new String[]{id});
        try{if(existing.moveToFirst())return false;}finally{existing.close();}

        String headline=meaningfulHeadline(title,body);
        double attention=Math.max(.58,Math.min(.72,.58+Math.max(0,importance-.5)*.20));
        long evaluatedAt=System.currentTimeMillis();
        CognitiveDomainV4.Situation situation=new CognitiveDomainV4.Situation(
                id,CognitiveDomainV4.SituationKind.UPCOMING_EVENT,CognitiveDomainV4.SituationState.DETECTED,
                headline,"Intentional capture contains an explicit future day and time.",
                Collections.<String>emptyList(),Collections.<String>emptyList(),Collections.singletonList(memoryId),Collections.<String>emptyList(),
                startedAt,Long.valueOf(startedAt),Long.valueOf(eventAt.longValue()),evaluatedAt,attention,.12,.90,Collections.<String>emptyList());
        CognitiveStoreV4.putSituation(db,situation,"",semanticAnchor,memoryId);
        return true;
    }

    static String normalizeSpokenClock(String text){
        String x=n(text);
        String[][] values={
                {"واحدة","1"},{"واحده","1"},{"الواحدة","1"},{"الواحده","1"},{"واحد","1"},
                {"اتنين","2"},{"اثنين","2"},{"الإثنين","2"},{"الاثنين","2"},
                {"تلاتة","3"},{"تلاته","3"},{"ثلاثة","3"},{"ثلاثه","3"},
                {"اربعة","4"},{"اربعه","4"},{"أربعة","4"},{"أربعه","4"},
                {"خمسة","5"},{"خمسه","5"},
                {"ستة","6"},{"سته","6"},
                {"سبعة","7"},{"سبعه","7"},
                {"تمانية","8"},{"تمانيه","8"},{"ثمانية","8"},{"ثمانيه","8"},
                {"تسعة","9"},{"تسعه","9"},
                {"عشرة","10"},{"عشره","10"},{"العاشرة","10"},{"العاشره","10"},
                {"حداشر","11"},{"حدعشر","11"},{"أحد عشر","11"},{"احد عشر","11"},
                {"اتناشر","12"},{"اثناشر","12"},{"اثنا عشر","12"},{"اثنتا عشر","12"}
        };
        for(String[] pair:values){
            x=x.replaceAll("((?:الساعة|الساعه)\\s+)"+java.util.regex.Pattern.quote(pair[0])+"(?=\\s|$)","$1"+pair[1]);
        }
        return x;
    }

    static boolean hasExplicitFutureDay(String low){
        return containsAny(low,"بكرة","بكره","غدا","غدًا","tomorrow","النهارده","النهاردة","اليوم","today",
                "السبت","الأحد","الاحد","الاثنين","الإثنين","الثلاثاء","الأربعاء","الاربعاء","الخميس","الجمعة","الجمعه",
                "saturday","sunday","monday","tuesday","wednesday","thursday","friday");
    }

    static boolean intentionalSource(String source){String s=n(source);return"manual".equals(s)||"manual_recording".equals(s)||"quick_capture".equals(s);}
    private static String meaningfulHeadline(String title,String body){String t=n(title);String low=t.toLowerCase(Locale.ROOT);if(t.isEmpty()||"voice recording".equals(low)||"recording".equals(low)||"voice note".equals(low))t=n(body);return clip(t.isEmpty()?"Upcoming reminder":t,180);}
    private static String analysisKind(String type){String x=n(type).toLowerCase(Locale.ROOT);if(x.contains("voice")||x.contains("audio"))return"TRANSCRIPT";if(x.contains("image")||x.contains("screenshot")||x.contains("photo"))return"OCR";return"EXTRACTION";}
    private static String mapped(VaultDb db,String table,String legacyId,String objectType){Cursor c=db.getReadableDatabase().query("v4_legacy_map",new String[]{"object_id"},"legacy_table=? AND legacy_id=? AND object_type=?",new String[]{table,legacyId,objectType},null,null,null,"1");try{return c.moveToFirst()?n(c.getString(0)):"";}finally{c.close();}}
    private static String legacyMetadata(long itemId,String metadata){try{JSONObject o=new JSONObject();o.put("migrated_from","knowledge_items");o.put("legacy_id",itemId);if(!n(metadata).isEmpty())o.put("legacy_metadata",metadata);o.put("forward_projection","intentional");return o.toString();}catch(Exception e){return"{}";}}
    private static double importance(String metadata){try{double v=new JSONObject(n(metadata)).optDouble("importance",.5);if(v>1)v/=100.0;return Math.max(0,Math.min(1,v));}catch(Exception e){return .5;}}
    private static boolean looksLikeSha256(String value){return value!=null&&value.matches("(?i)[0-9a-f]{64}");}
    private static boolean containsAny(String s,String...values){for(String v:values)if(s.contains(v))return true;return false;}
    private static String first(String...values){if(values!=null)for(String v:values)if(!n(v).isEmpty())return n(v);return"";}
    private static String clip(String s,int max){String x=n(s);return x.length()<=max?x:x.substring(0,max)+"…";}
    private static String n(String s){return s==null?"":s.replace('\u0000',' ').replaceAll("\\s+"," ").trim();}

    public static final class Result{
        public final String evidenceId,memoryId;public final boolean genericMaterial,timedMaterial;
        Result(String evidenceId,String memoryId,boolean genericMaterial,boolean timedMaterial){this.evidenceId=evidenceId;this.memoryId=memoryId;this.genericMaterial=genericMaterial;this.timedMaterial=timedMaterial;}
        static Result empty(){return new Result("","",false,false);}
        public boolean material(){return genericMaterial||timedMaterial;}
    }
}
