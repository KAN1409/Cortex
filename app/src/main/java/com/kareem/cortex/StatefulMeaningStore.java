package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** v69 stateful ledger layered over immutable UE raw observations. */
public final class StatefulMeaningStore {
    public static final String SCHEMA_VERSION="stateful_meaning_schema_001";
    private StatefulMeaningStore(){}

    public static final class ObservationState {
        public final long instanceId,transitionId;
        public final String instanceKey,fromState,toState;
        public final boolean exactDuplicate,stateChanged;
        ObservationState(long i,long t,String k,String f,String to,boolean d,boolean c){instanceId=i;transitionId=t;instanceKey=k;fromState=f;toState=to;exactDuplicate=d;stateChanged=c;}
    }

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_source_instances(id INTEGER PRIMARY KEY AUTOINCREMENT,source_type TEXT NOT NULL,source_key TEXT NOT NULL,instance_key TEXT NOT NULL UNIQUE,state TEXT NOT NULL,content_hash TEXT,observation_count INTEGER NOT NULL DEFAULT 0,transition_count INTEGER NOT NULL DEFAULT 0,first_seen_at INTEGER NOT NULL,last_seen_at INTEGER NOT NULL,last_raw_observation_id INTEGER NOT NULL DEFAULT 0,removal_reason TEXT,metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_source_instances_recent ON ue_source_instances(last_seen_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_source_instances_source ON ue_source_instances(source_type,source_key,last_seen_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_source_transitions(id INTEGER PRIMARY KEY AUTOINCREMENT,source_instance_id INTEGER NOT NULL,raw_observation_id INTEGER NOT NULL,from_state TEXT,to_state TEXT NOT NULL,transition_kind TEXT NOT NULL,content_hash TEXT,reason TEXT,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL,UNIQUE(source_instance_id,raw_observation_id,to_state))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_source_transitions_instance ON ue_source_transitions(source_instance_id,occurred_at ASC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_situation_members_v2(id INTEGER PRIMARY KEY AUTOINCREMENT,situation_id INTEGER NOT NULL,semantic_event_id INTEGER NOT NULL,source_instance_id INTEGER NOT NULL DEFAULT 0,relation TEXT NOT NULL,confidence REAL NOT NULL DEFAULT 0,evidence_json TEXT,created_at INTEGER NOT NULL,UNIQUE(situation_id,semantic_event_id,relation))");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_situation_state_v2(situation_id INTEGER PRIMARY KEY,correlation_key TEXT NOT NULL UNIQUE,last_fact_hash TEXT,last_fact_text TEXT,last_transition_kind TEXT,member_count INTEGER NOT NULL DEFAULT 0,opened_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_situation_transitions(id INTEGER PRIMARY KEY AUTOINCREMENT,situation_id INTEGER NOT NULL,semantic_event_id INTEGER NOT NULL DEFAULT 0,kind TEXT NOT NULL,title TEXT,summary TEXT,confidence REAL NOT NULL DEFAULT 0,evidence_json TEXT,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL,UNIQUE(situation_id,semantic_event_id,kind))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_situation_transitions_recent ON ue_situation_transitions(occurred_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_projection_decisions(id INTEGER PRIMARY KEY AUTOINCREMENT,semantic_event_id INTEGER NOT NULL,situation_id INTEGER NOT NULL DEFAULT 0,projection TEXT NOT NULL,eligible INTEGER NOT NULL,reason TEXT,policy_version TEXT NOT NULL,created_at INTEGER NOT NULL,UNIQUE(semantic_event_id,projection,policy_version))");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_projection_revisions(name TEXT PRIMARY KEY,revision INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL)");
        addColumn(db,"ue_situation_state_v2","last_fact_text","TEXT");
    }

    public static ObservationState observeNotification(SQLiteDatabase db,long rawId,String pkg,String technical,String eventType,String title,String body,long occurredAt,JSONObject meta){
        ensure(db);long now=System.currentTimeMillis(),when=occurredAt>0?occurredAt:now;JSONObject m=meta==null?new JSONObject():meta;
        String notificationKey=m.optString("notification_key","");String shortcut=m.optString("shortcut_id","");String group=m.optString("group_key",m.optString("override_group_key",""));int notificationId=m.optInt("notification_id",0);String tag=m.optString("notification_tag","");String removal=m.optString("removal_reason","");
        String instanceKey=StatefulMeaningPolicy.notificationInstanceKey(pkg,notificationKey,shortcut,group,notificationId,tag,title);
        String to=StatefulMeaningPolicy.lifecycleState(technical,eventType,title,body,removal);String hash=Fingerprint.text(n(title)+"\n"+n(body)+"\n"+to);
        long id=0;String from="",oldHash="";int obs=0,transitions=0;Cursor c=db.rawQuery("SELECT id,state,content_hash,observation_count,transition_count FROM ue_source_instances WHERE instance_key=? LIMIT 1",new String[]{instanceKey});if(c.moveToFirst()){id=c.getLong(0);from=n(c.getString(1));oldHash=n(c.getString(2));obs=c.getInt(3);transitions=c.getInt(4);}c.close();
        boolean duplicate=StatefulMeaningPolicy.sameExactDelivery(instanceKey,oldHash,hash),changed=!to.equals(from);
        ContentValues v=new ContentValues();v.put("source_type","notification");v.put("source_key",n(pkg));v.put("instance_key",instanceKey);v.put("state",to);v.put("content_hash",hash);v.put("observation_count",obs+1);v.put("transition_count",transitions+(duplicate?0:1));v.put("last_seen_at",when);v.put("last_raw_observation_id",rawId);v.put("removal_reason",removal);v.put("metadata_json",m.toString());v.put("updated_at",now);
        if(id>0)db.update("ue_source_instances",v,"id=?",new String[]{String.valueOf(id)});else{v.put("first_seen_at",when);v.put("created_at",now);id=db.insertOrThrow("ue_source_instances",null,v);}
        if(duplicate)return new ObservationState(id,0,instanceKey,from,to,true,false);
        String kind=transitionKind(from,to);ContentValues t=new ContentValues();t.put("source_instance_id",id);t.put("raw_observation_id",rawId);t.put("from_state",from);t.put("to_state",to);t.put("transition_kind",kind);t.put("content_hash",hash);t.put("reason",removal);t.put("occurred_at",when);t.put("created_at",now);long transition=db.insertWithOnConflict("ue_source_transitions",null,t,SQLiteDatabase.CONFLICT_IGNORE);
        return new ObservationState(id,Math.max(0,transition),instanceKey,from,to,false,changed);
    }

    /** Links a validated semantic event to a reversible cross-source situation and records material deltas. */
    public static long correlate(SQLiteDatabase db,long semanticEventId,long sourceInstanceId,String source,String semanticType,String subject,String summary,double confidence,long occurredAt){
        ensure(db);long when=occurredAt>0?occurredAt:System.currentTimeMillis(),now=System.currentTimeMillis();String factText=LocalSemanticEmbedder.norm(n(subject)+" "+n(summary));String key=StatefulMeaningPolicy.correlationKey(semanticType,subject,summary,source,when),factHash=Fingerprint.text(n(semanticType)+"|"+factText);
        long situationId=0;String previousHash="",previousText="";int members=0;Cursor c=db.rawQuery("SELECT situation_id,last_fact_hash,COALESCE(last_fact_text,''),member_count FROM ue_situation_state_v2 WHERE correlation_key=? LIMIT 1",new String[]{key});if(c.moveToFirst()){situationId=c.getLong(0);previousHash=n(c.getString(1));previousText=n(c.getString(2));members=c.getInt(3);}c.close();
        String title=CanonicalPresentation.cleanTitle("situation",semanticType,subject,subject);String body=CanonicalPresentation.cleanBody(summary);
        if(situationId<=0){situationId=UniversalEventStore.upsertSituation(db,key,"stateful",title,body,"open",priority(semanticType),confidence,when,new JSONObject());ContentValues st=new ContentValues();st.put("situation_id",situationId);st.put("correlation_key",key);st.put("last_fact_hash",factHash);st.put("last_fact_text",factText);st.put("last_transition_kind","OPENED");st.put("member_count",1);st.put("opened_at",when);st.put("updated_at",now);db.insertOrThrow("ue_situation_state_v2",null,st);recordSituationTransition(db,situationId,semanticEventId,"OPENED",title,body,confidence,"new correlation key",when);}
        else{
            boolean same=previousHash.equals(factHash)||!StatefulMeaningPolicy.materialChange(previousText,factText);String transition=same?"SUPPORTING_EVIDENCE":"MATERIAL_UPDATE";ContentValues st=new ContentValues();st.put("last_fact_hash",factHash);st.put("last_fact_text",factText);st.put("last_transition_kind",transition);st.put("member_count",members+1);st.put("updated_at",now);db.update("ue_situation_state_v2",st,"situation_id=?",new String[]{String.valueOf(situationId)});if(StatefulMeaningPolicy.materialTransition(transition))recordSituationTransition(db,situationId,semanticEventId,transition,title,body,confidence,"material correlated fact",when);
        }
        ContentValues member=new ContentValues();member.put("situation_id",situationId);member.put("semantic_event_id",semanticEventId);member.put("source_instance_id",sourceInstanceId);member.put("relation","supports");member.put("confidence",confidence);member.put("evidence_json",evidenceJson("correlated source evidence"));member.put("created_at",now);db.insertWithOnConflict("ue_situation_members_v2",null,member,SQLiteDatabase.CONFLICT_IGNORE);UniversalEventStore.linkSituationEvent(db,situationId,semanticEventId,"supports");return situationId;
    }

    public static void recordProjectionDecision(SQLiteDatabase db,long semanticEventId,long situationId,StatefulMeaningPolicy.ProjectionDecision d){ensure(db);putDecision(db,semanticEventId,situationId,"CAPTURE",d.capture,d.reason);putDecision(db,semanticEventId,situationId,"NOW",d.now,d.reason);putDecision(db,semanticEventId,situationId,"BRIEF",d.brief,d.reason);putDecision(db,semanticEventId,situationId,"BRAIN",d.brain,d.reason);bump(db,"projection");}

    private static void putDecision(SQLiteDatabase db,long eventId,long situationId,String projection,boolean eligible,String reason){ContentValues v=new ContentValues();v.put("semantic_event_id",eventId);v.put("situation_id",situationId);v.put("projection",projection);v.put("eligible",eligible?1:0);v.put("reason",n(reason));v.put("policy_version",StatefulMeaningPolicy.VERSION);v.put("created_at",System.currentTimeMillis());db.insertWithOnConflict("ue_projection_decisions",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private static void recordSituationTransition(SQLiteDatabase db,long situationId,long eventId,String kind,String title,String summary,double confidence,String evidence,long when){ContentValues v=new ContentValues();v.put("situation_id",situationId);v.put("semantic_event_id",eventId);v.put("kind",kind);v.put("title",n(title));v.put("summary",n(summary));v.put("confidence",confidence);v.put("evidence_json",evidenceJson(evidence));v.put("occurred_at",when);v.put("created_at",System.currentTimeMillis());db.insertWithOnConflict("ue_situation_transitions",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    private static String evidenceJson(String reason){JSONObject j=new JSONObject();try{j.put("reason",reason).put("policy",StatefulMeaningPolicy.VERSION);}catch(Exception ignored){}return j.toString();}
    private static void bump(SQLiteDatabase db,String name){long now=System.currentTimeMillis();Cursor c=db.rawQuery("SELECT revision FROM ue_projection_revisions WHERE name=?",new String[]{name});long rev=c.moveToFirst()?c.getLong(0):0;c.close();ContentValues v=new ContentValues();v.put("name",name);v.put("revision",rev+1);v.put("updated_at",now);db.insertWithOnConflict("ue_projection_revisions",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private static void addColumn(SQLiteDatabase db,String table,String column,String definition){Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);boolean found=false;while(c.moveToNext()){int i=c.getColumnIndex("name");if(i>=0&&column.equals(c.getString(i))){found=true;break;}}c.close();if(!found)db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
    private static String transitionKind(String from,String to){if(n(from).isEmpty())return "OPENED";if(to.startsWith("removed:"))return "REMOVED";if(from.equals(to))return "CONTENT_UPDATE";if("completed".equals(to)||"ended".equals(to)||"missed".equals(to)||"failed".equals(to))return "TERMINAL";return "STATE_CHANGE";}
    private static int priority(String type){String x=n(type).toLowerCase();if(x.contains("security")||x.contains("request"))return 90;if(x.contains("decision")||x.contains("commitment"))return 75;return 40;}
    private static String n(String s){return s==null?"":s.trim();}
}
