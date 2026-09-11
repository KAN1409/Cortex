package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.*;

/**
 * NEXUS migrated inside Cortex.
 *
 * NEXUS no longer owns a second observation database. Cortex stays the source of truth and this
 * engine projects the NEXUS product loop from canonical Cortex evidence:
 * Observe -> Remember -> Model interests -> Discover -> Rank -> Prepare action -> Approval -> Learn.
 */
public final class NexusEngine {
    public static final String VERSION="nexus_inside_cortex_001";
    private static final long DAY=86_400_000L;
    private static final long INTEREST_WINDOW=30L*DAY;
    private static final long DEFER_MS=24L*60L*60L*1000L;

    public static final class Interest {
        public final String id,label;public final double affinity,momentum,confidence,saturation;public final int evidenceCount;public final long updatedAt;
        Interest(String id,String label,double affinity,double momentum,double confidence,double saturation,int evidenceCount,long updatedAt){this.id=id;this.label=label;this.affinity=affinity;this.momentum=momentum;this.confidence=confidence;this.saturation=saturation;this.evidenceCount=evidenceCount;this.updatedAt=updatedAt;}
    }
    public static final class Discovery {
        public final String id,type,title,summary,whyThis,state;public final double score;public final long createdAt,updatedAt;
        Discovery(String id,String type,String title,String summary,String whyThis,double score,String state,long createdAt,long updatedAt){this.id=id;this.type=type;this.title=title;this.summary=summary;this.whyThis=whyThis;this.score=score;this.state=state;this.createdAt=createdAt;this.updatedAt=updatedAt;}
    }
    public static final class PreparedAction {
        public final long derivedId;public final String kind,title,body,state,source;public final double confidence;public final int importance;public final long updatedAt;
        PreparedAction(long derivedId,String kind,String title,String body,String state,String source,double confidence,int importance,long updatedAt){this.derivedId=derivedId;this.kind=kind;this.title=title;this.body=body;this.state=state;this.source=source;this.confidence=confidence;this.importance=importance;this.updatedAt=updatedAt;}
    }
    private static final class Score {double total,recent;int count;long latest;}

    private NexusEngine(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS nx_interests(id TEXT PRIMARY KEY,label TEXT NOT NULL,affinity REAL DEFAULT 0,momentum REAL DEFAULT 0,confidence REAL DEFAULT 0,saturation REAL DEFAULT 0,evidence_count INTEGER DEFAULT 0,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nx_interests_rank ON nx_interests(affinity DESC,momentum DESC,confidence DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS nx_discoveries(id TEXT PRIMARY KEY,type TEXT NOT NULL,title TEXT NOT NULL,summary TEXT,why_this TEXT,score REAL DEFAULT 0,state TEXT DEFAULT 'open',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nx_discoveries_rank ON nx_discoveries(state,score DESC,updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS nx_action_state(derived_id INTEGER PRIMARY KEY,state TEXT NOT NULL,deferred_until INTEGER DEFAULT 0,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nx_action_state ON nx_action_state(state,updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS nx_meta(key TEXT PRIMARY KEY,value TEXT,updated_at INTEGER NOT NULL)");
    }

    public static void refresh(VaultDb db){
        if(db==null)return;
        CognitiveStore.ensure(db);
        SQLiteDatabase s=db.getWritableDatabase();ensure(s);
        long now=System.currentTimeMillis();
        LinkedHashMap<String,Score> scores=new LinkedHashMap<>();
        collectRawSignals(s,scores,now);
        collectKnowledge(s,scores,now);
        collectKnowledgeCategories(s,scores,now);
        writeInterests(s,scores,now);
        writeDiscoveries(s,now);
        seedActionLifecycle(s,now);
        ContentValues meta=new ContentValues();meta.put("key","last_refresh");meta.put("value",String.valueOf(now));meta.put("updated_at",now);s.insertWithOnConflict("nx_meta",null,meta,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void refreshIfStale(VaultDb db,long maxAgeMs){
        if(db==null)return;SQLiteDatabase s=db.getWritableDatabase();ensure(s);long last=metaLong(s,"last_refresh");if(last<=0||System.currentTimeMillis()-last>Math.max(60_000L,maxAgeMs))refresh(db);
    }

    private static void collectRawSignals(SQLiteDatabase s,Map<String,Score> out,long now){
        if(!table(s,"raw_signals"))return;long since=now-INTEREST_WINDOW;
        Cursor c=s.rawQuery("SELECT COALESCE(source,''),COALESCE(title,''),COALESCE(body,''),COALESCE(importance,0),occurred_at,COALESCE(state,''),COALESCE(disposition,'') FROM raw_signals WHERE occurred_at>=? ORDER BY occurred_at DESC LIMIT 1600",new String[]{String.valueOf(since)});
        while(c.moveToNext()){
            String source=n(c.getString(0)),title=n(c.getString(1)),body=n(c.getString(2)),state=n(c.getString(5)),disp=n(c.getString(6));int importance=c.getInt(3);long at=c.getLong(4);
            if("discard".equalsIgnoreCase(disp)||"suppressed".equalsIgnoreCase(state))continue;
            if(AttentionNoisePolicy.suppress(source,title,body,"",""))continue;
            String text=(source+" "+title+" "+body).toLowerCase(Locale.ROOT);
            double weight=(0.45+Math.max(0,Math.min(100,importance))/120.0)*recency(now,at);
            addThemes(out,text,weight,at,now);
        }c.close();
    }

    private static void collectKnowledge(SQLiteDatabase s,Map<String,Score> out,long now){
        if(!table(s,"knowledge_items"))return;long since=now-INTEREST_WINDOW;
        Cursor c=s.rawQuery("SELECT COALESCE(source,''),COALESCE(title,''),COALESCE(summary,''),COALESCE(category,''),COALESCE(tags,''),created_at FROM knowledge_items WHERE status='analyzed' AND created_at>=? ORDER BY created_at DESC LIMIT 1400",new String[]{String.valueOf(since)});
        while(c.moveToNext()){
            String source=n(c.getString(0)),title=n(c.getString(1)),summary=n(c.getString(2)),category=n(c.getString(3)),tags=n(c.getString(4));long at=c.getLong(5);
            if("knowledge_v2".equalsIgnoreCase(source))continue;
            String text=(source+" "+title+" "+summary+" "+category+" "+tags).toLowerCase(Locale.ROOT);
            if(AttentionNoisePolicy.suppress(source,title,summary,category,""))continue;
            addThemes(out,text,0.65*recency(now,at),at,now);
            String canonical=KnowledgeV2Maintenance.canonicalCategory(category);if(usefulLabel(canonical))add(out,canonical,0.42*recency(now,at),at,now);
        }c.close();
    }

    private static void collectKnowledgeCategories(SQLiteDatabase s,Map<String,Score> out,long now){
        if(!table(s,"kv2_categories")||!table(s,"kv2_category_memberships"))return;
        Cursor c=s.rawQuery("SELECT c.canonical_name,COUNT(m.knowledge_id),MAX(c.last_active_at) FROM kv2_categories c JOIN kv2_category_memberships m ON m.category_id=c.id WHERE c.state='active' GROUP BY c.id,c.canonical_name HAVING COUNT(m.knowledge_id)>0 ORDER BY COUNT(m.knowledge_id) DESC LIMIT 20",null);
        while(c.moveToNext()){
            String label=KnowledgeV2Maintenance.canonicalCategory(n(c.getString(0)));int count=c.getInt(1);long at=c.getLong(2);if(!usefulLabel(label))continue;
            double weight=Math.min(4.0,0.55+Math.log1p(Math.max(1,count))*0.45)*recency(now,at);
            add(out,label,weight,at,now);
        }c.close();
    }

    private static void addThemes(Map<String,Score> out,String text,double w,long at,long now){
        if(text.isEmpty())return;
        if(any(text,"whatsapp","telegram","messenger","truecaller","call","phone","sms","رسالة","مكالمة"))add(out,"Communication",w,at,now);
        if(any(text,"chatgpt","cortex","picbrain","nexus","github","termux","notion","docs","ai ","artificial intelligence"))add(out,"AI & productivity",w,at,now);
        if(any(text,"chrome","browser","search","googlequicksearchbox","research","article","مقال","بحث"))add(out,"Web & research",w,at,now);
        if(any(text,"instagram","facebook","tiktok","twitter","reddit","social"))add(out,"Social",w,at,now);
        if(any(text,"netflix","youtube","spotify","music","movie","series","فيلم","مسلسل"))add(out,"Entertainment",w,at,now);
        if(any(text,"maps","uber","careem","navigation","location","خرائط","لوكيشن"))add(out,"Places & mobility",w,at,now);
        if(any(text,"gallery","photos","camera","screenshot","image","photo","صورة","صور"))add(out,"Photos & media",w,at,now);
        if(any(text,"talabat","restaurant","food","meal","اكل","أكل","مطعم"))add(out,"Food",w,at,now);
        if(any(text,"android","kotlin","compose","gradle","apk","adb","shizuku","code","developer"))add(out,"App development",w*1.15,at,now);
        if(any(text,"design","ui","ux","icon","visual","mockup","logo","تصميم"))add(out,"Design",w,at,now);
        if(any(text,"project","villa","hotel","site","drawing","boq","quotation","purchase order","po ","pr ","مشروع"))add(out,"Work & projects",w*1.1,at,now);
        if(any(text,"buy","purchase","price","egp","order","shopping","بيع","شراء","سعر"))add(out,"Money & purchases",w,at,now);
        if(any(text,"appointment","doctor","hospital","scan","medicine","medical","clinic","دكتور","مستشفى","دواء","كشف"))add(out,"Health & appointments",w,at,now);
    }

    private static void add(Map<String,Score> out,String label,double weight,long at,long now){
        if(weight<=0||!usefulLabel(label))return;Score s=out.get(label);if(s==null){s=new Score();out.put(label,s);}s.total+=weight;s.count++;s.latest=Math.max(s.latest,at);if(at>=now-7L*DAY)s.recent+=weight;
    }

    private static void writeInterests(SQLiteDatabase s,Map<String,Score> scores,long now){
        ArrayList<Map.Entry<String,Score>> ranked=new ArrayList<>(scores.entrySet());ranked.sort((a,b)->Double.compare(b.getValue().total,a.getValue().total));
        double max=ranked.isEmpty()?1.0:Math.max(.001,ranked.get(0).getValue().total);
        s.delete("nx_interests",null,null);int limit=Math.min(14,ranked.size());
        for(int i=0;i<limit;i++){
            String label=ranked.get(i).getKey();Score sc=ranked.get(i).getValue();double affinity=clamp(sc.total/max),momentum=clamp(sc.recent/Math.max(.001,sc.total)),confidence=clamp(.30+Math.min(.40,sc.count*.055)+affinity*.30),saturation=clamp(sc.total/18.0);
            ContentValues v=new ContentValues();v.put("id","interest_"+slug(label));v.put("label",label);v.put("affinity",affinity);v.put("momentum",momentum);v.put("confidence",confidence);v.put("saturation",saturation);v.put("evidence_count",sc.count);v.put("updated_at",Math.max(now,sc.latest));s.insertWithOnConflict("nx_interests",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static void writeDiscoveries(SQLiteDatabase s,long now){
        Cursor c=s.rawQuery("SELECT id,label,affinity,momentum,confidence,evidence_count FROM nx_interests ORDER BY (affinity*confidence+momentum*.35) DESC LIMIT 8",null);int made=0;
        while(c.moveToNext()&&made<5){String interestId=c.getString(0),label=c.getString(1);double affinity=c.getDouble(2),momentum=c.getDouble(3),confidence=c.getDouble(4);int evidence=c.getInt(5);if(evidence<2||affinity<.22)continue;
            String id="discovery_"+slug(label);String oldState=string(s,"SELECT state FROM nx_discoveries WHERE id=?",new String[]{id});if("dismissed".equals(oldState))continue;
            double score=clamp(affinity*.52+momentum*.25+confidence*.23);String summary=momentum>=.55?"This theme is active across your recent Cortex context.":"This theme keeps recurring across your Cortex context.";String why=evidence+" grounded signal"+(evidence==1?"":"s")+" contributed.";
            ContentValues v=new ContentValues();v.put("id",id);v.put("type","DISCOVERY");v.put("title",label);v.put("summary",summary);v.put("why_this",why);v.put("score",score);v.put("state","open");v.put("created_at",existingCreatedAt(s,id,now));v.put("updated_at",now);s.insertWithOnConflict("nx_discoveries",null,v,SQLiteDatabase.CONFLICT_REPLACE);made++;
        }c.close();
    }

    private static void seedActionLifecycle(SQLiteDatabase s,long now){
        s.execSQL("UPDATE nx_action_state SET state='READY_FOR_APPROVAL',deferred_until=0,updated_at=? WHERE state='DRAFT' AND deferred_until>0 AND deferred_until<=?",new Object[]{now,now});
        Cursor c=s.rawQuery("SELECT id,kind,title,body,source_key,confidence,importance FROM derived_items WHERE state='open' AND kind IN ('ACTION','WAITING') AND confidence>=0.65 AND importance>=60 ORDER BY importance DESC,updated_at DESC LIMIT 80",null);
        while(c.moveToNext()){
            long id=c.getLong(0);String kind=n(c.getString(1)),title=n(c.getString(2)),body=n(c.getString(3)),source=n(c.getString(4));
            if(title.isEmpty()||AttentionNoisePolicy.suppress(source,title,body,kind,kind))continue;
            Cursor x=s.rawQuery("SELECT 1 FROM nx_action_state WHERE derived_id=? LIMIT 1",new String[]{String.valueOf(id)});boolean exists=x.moveToFirst();x.close();if(exists)continue;
            ContentValues v=new ContentValues();v.put("derived_id",id);v.put("state","READY_FOR_APPROVAL");v.put("deferred_until",0);v.put("updated_at",now);s.insertWithOnConflict("nx_action_state",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }c.close();
    }

    public static ArrayList<Interest> interests(VaultDb db,int limit){SQLiteDatabase s=db.getReadableDatabase();ensure(s);ArrayList<Interest> out=new ArrayList<>();Cursor c=s.rawQuery("SELECT id,label,affinity,momentum,confidence,saturation,evidence_count,updated_at FROM nx_interests ORDER BY (affinity*confidence+momentum*.35) DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});while(c.moveToNext())out.add(new Interest(c.getString(0),c.getString(1),c.getDouble(2),c.getDouble(3),c.getDouble(4),c.getDouble(5),c.getInt(6),c.getLong(7)));c.close();return out;}
    public static ArrayList<Discovery> discoveries(VaultDb db,int limit){SQLiteDatabase s=db.getReadableDatabase();ensure(s);ArrayList<Discovery> out=new ArrayList<>();Cursor c=s.rawQuery("SELECT id,type,title,summary,why_this,score,state,created_at,updated_at FROM nx_discoveries WHERE state='open' ORDER BY score DESC,updated_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});while(c.moveToNext())out.add(new Discovery(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),c.getString(6),c.getLong(7),c.getLong(8)));c.close();return out;}
    public static ArrayList<PreparedAction> actions(VaultDb db,int limit){SQLiteDatabase s=db.getReadableDatabase();ensure(s);ArrayList<PreparedAction> out=new ArrayList<>();Cursor c=s.rawQuery("SELECT d.id,d.kind,d.title,d.body,n.state,d.source_key,d.confidence,d.importance,d.updated_at FROM nx_action_state n JOIN derived_items d ON d.id=n.derived_id WHERE d.state='open' AND n.state IN ('READY_FOR_APPROVAL','APPROVED','EXECUTING','DRAFT') ORDER BY CASE n.state WHEN 'READY_FOR_APPROVAL' THEN 0 WHEN 'APPROVED' THEN 1 WHEN 'EXECUTING' THEN 2 ELSE 3 END,d.importance DESC,d.updated_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});while(c.moveToNext())out.add(new PreparedAction(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getDouble(6),c.getInt(7),c.getLong(8)));c.close();return out;}

    public static int observationCount(VaultDb db){SQLiteDatabase s=db.getReadableDatabase();long since=System.currentTimeMillis()-INTEREST_WINDOW;int raw=count(s,"SELECT COUNT(*) FROM raw_signals WHERE occurred_at>=?",new String[]{String.valueOf(since)});int evidence=table(s,"kv2_evidence")?count(s,"SELECT COUNT(*) FROM kv2_evidence WHERE observed_at>=?",new String[]{String.valueOf(since)}):0;return raw+evidence;}
    public static int readyActionCount(VaultDb db){return count(db.getReadableDatabase(),"SELECT COUNT(*) FROM nx_action_state n JOIN derived_items d ON d.id=n.derived_id WHERE d.state='open' AND n.state='READY_FOR_APPROVAL'",null);}

    public static void dismissDiscovery(VaultDb db,String id){SQLiteDatabase s=db.getWritableDatabase();ensure(s);ContentValues v=new ContentValues();v.put("state","dismissed");v.put("updated_at",System.currentTimeMillis());s.update("nx_discoveries",v,"id=?",new String[]{id});}
    public static void approve(VaultDb db,long id){transition(db,id,"APPROVED","APPROVED",0,false);}
    public static void defer(VaultDb db,long id){transition(db,id,"DRAFT","DEFERRED",System.currentTimeMillis()+DEFER_MS,false);}
    public static void reject(VaultDb db,long id){transition(db,id,"REJECTED","REJECTED",0,true);}
    public static void start(VaultDb db,long id){transition(db,id,"EXECUTING","STARTED",0,false);}
    public static void complete(VaultDb db,long id){transition(db,id,"COMPLETED","COMPLETED",0,true);}
    public static void fail(VaultDb db,long id){transition(db,id,"FAILED","FAILED",0,true);}

    private static void transition(VaultDb db,long id,String state,String event,long deferUntil,boolean resolve){
        if(db==null||id<=0)return;SQLiteDatabase s=db.getWritableDatabase();ensure(s);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("state",state);v.put("deferred_until",Math.max(0,deferUntil));v.put("updated_at",now);s.update("nx_action_state",v,"derived_id=?",new String[]{String.valueOf(id)});
        if(resolve){ContentValues d=new ContentValues();d.put("state","resolved");d.put("resolved_at",now);d.put("updated_at",now);s.update("derived_items",d,"id=?",new String[]{String.valueOf(id)});}
        JSONObject o=new JSONObject();try{o.put("nexus_state",state);o.put("engine",VERSION);}catch(Exception ignored){}CognitiveStore.feedback(db,"derived",id,event,o.toString(),VERSION);
    }

    private static double recency(long now,long at){if(at<=0)return .55;long age=Math.max(0,now-at);if(age<=DAY)return 1.6;if(age<=7L*DAY)return 1.25;if(age<=14L*DAY)return 1.0;if(age<=30L*DAY)return .72;return .45;}
    private static boolean usefulLabel(String label){String x=n(label);if(x.length()<3||x.length()>64)return false;String l=x.toLowerCase(Locale.ROOT);if(l.equals("url")||l.equals("date")||l.equals("money")||l.equals("phone")||l.equals("email")||l.equals("hashtag"))return false;if(l.contains("://")||l.startsWith("www.")||l.contains("failed in ")||l.contains("exception"))return false;return true;}
    private static boolean any(String text,String...xs){for(String x:xs)if(text.contains(x))return true;return false;}
    private static String slug(String s){String x=n(s).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+","_").replaceAll("^_+|_+$","");return x.isEmpty()?Integer.toHexString(n(s).hashCode()):x;}
    private static long existingCreatedAt(SQLiteDatabase s,String id,long fallback){Cursor c=s.rawQuery("SELECT created_at FROM nx_discoveries WHERE id=? LIMIT 1",new String[]{id});long v=c.moveToFirst()?c.getLong(0):fallback;c.close();return v;}
    private static long metaLong(SQLiteDatabase s,String key){Cursor c=s.rawQuery("SELECT value FROM nx_meta WHERE key=? LIMIT 1",new String[]{key});long v=0;if(c.moveToFirst())try{v=Long.parseLong(n(c.getString(0)));}catch(Exception ignored){}c.close();return v;}
    private static String string(SQLiteDatabase s,String sql,String[] args){Cursor c=s.rawQuery(sql,args);String v=c.moveToFirst()?n(c.getString(0)):"";c.close();return v;}
    private static int count(SQLiteDatabase s,String sql,String[] args){try(Cursor c=s.rawQuery(sql,args)){return c.moveToFirst()?c.getInt(0):0;}catch(Throwable ignored){return 0;}}
    private static boolean table(SQLiteDatabase s,String name){Cursor c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{name});boolean yes=c.moveToFirst();c.close();return yes;}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static String n(String s){return s==null?"":s.trim();}
}
