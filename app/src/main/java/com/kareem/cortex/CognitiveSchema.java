package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Unified cognitive schema layered on top of the existing Cortex v4 database.
 *
 * The legacy knowledge_items/entities/actions/relations tables stay intact while
 * the new architecture separates raw evidence, durable memory, derived
 * intelligence, provenance, feedback and AI execution state.
 */
public final class CognitiveSchema {
    public static final int DB_VERSION = 5;
    public static final String REVISION = "cognitive_001";
    private static volatile boolean ready;

    private CognitiveSchema(){}

    public static void ensure(SQLiteDatabase db){
        if(ready)return;
        synchronized(CognitiveSchema.class){
            if(ready)return;
            createMeta(db);
            createRawSignals(db);
            createThreads(db);
            createDerivedItems(db);
            createEntityGraph(db);
            createSourceLinks(db);
            createFeedback(db);
            createAiJobs(db);
            createModelRuns(db);
            migrateLegacyEntities(db);
            db.execSQL("INSERT OR REPLACE INTO schema_meta(key,value,updated_at) VALUES('cognitive_schema','"+REVISION+"',strftime('%s','now')*1000)");
            ready=true;
        }
    }

    private static void createMeta(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS schema_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
    }

    private static void createRawSignals(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS raw_signals(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,source TEXT,title TEXT,body TEXT,metadata_json TEXT,fingerprint TEXT UNIQUE,state TEXT DEFAULT 'filtered',disposition TEXT,importance INTEGER DEFAULT 0,reason TEXT,promoted_item_id INTEGER DEFAULT 0,occurred_at INTEGER NOT NULL,retention_until INTEGER DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        addColumn(db,"raw_signals","thread_id","INTEGER DEFAULT 0");
        addColumn(db,"raw_signals","confidence","REAL DEFAULT 0");
        addColumn(db,"raw_signals","policy_version","TEXT DEFAULT ''");
        addColumn(db,"raw_signals","filter_engine","TEXT DEFAULT ''");
        addColumn(db,"raw_signals","content_hash","TEXT DEFAULT ''");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_time ON raw_signals(occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_disposition ON raw_signals(disposition,occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_source ON raw_signals(source,occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_thread ON raw_signals(thread_id,occurred_at ASC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_state ON raw_signals(state,updated_at DESC)");
    }

    private static void createThreads(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS signal_threads(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,source TEXT NOT NULL,external_key TEXT NOT NULL,title TEXT,participant_key TEXT,state TEXT DEFAULT 'open',summary TEXT,metadata_json TEXT,started_at INTEGER NOT NULL,last_event_at INTEGER NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(kind,source,external_key))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_threads_recent ON signal_threads(last_event_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_threads_state ON signal_threads(state,last_event_at DESC)");
    }

    private static void createDerivedItems(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS derived_items(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,title TEXT NOT NULL,body TEXT,state TEXT DEFAULT 'open',confidence REAL DEFAULT 0,importance INTEGER DEFAULT 0,fingerprint TEXT,metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,resolved_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_derived_fingerprint ON derived_items(fingerprint) WHERE fingerprint IS NOT NULL AND fingerprint<>''");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_derived_kind_state ON derived_items(kind,state,importance DESC,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_derived_recent ON derived_items(updated_at DESC)");
    }

    private static void createEntityGraph(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS entity_nodes(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,canonical_name TEXT NOT NULL,normalized_key TEXT NOT NULL UNIQUE,status TEXT DEFAULT 'active',metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entity_kind ON entity_nodes(kind,canonical_name)");
        db.execSQL("CREATE TABLE IF NOT EXISTS entity_aliases(id INTEGER PRIMARY KEY AUTOINCREMENT,entity_id INTEGER NOT NULL,source TEXT,alias TEXT NOT NULL,normalized_alias TEXT NOT NULL,confidence REAL DEFAULT 0,metadata_json TEXT,created_at INTEGER NOT NULL,UNIQUE(entity_id,source,normalized_alias))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entity_alias_lookup ON entity_aliases(normalized_alias,source)");
    }

    private static void createSourceLinks(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS source_links(id INTEGER PRIMARY KEY AUTOINCREMENT,from_type TEXT NOT NULL,from_id INTEGER NOT NULL,to_type TEXT NOT NULL,to_id INTEGER NOT NULL,relation TEXT NOT NULL,confidence REAL DEFAULT 0,metadata_json TEXT,created_at INTEGER NOT NULL,UNIQUE(from_type,from_id,to_type,to_id,relation))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_source_links_from ON source_links(from_type,from_id,relation)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_source_links_to ON source_links(to_type,to_id,relation)");
    }

    private static void createFeedback(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS feedback_events(id INTEGER PRIMARY KEY AUTOINCREMENT,target_type TEXT NOT NULL,target_id INTEGER NOT NULL,event_type TEXT NOT NULL,value_json TEXT,policy_version TEXT,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_target ON feedback_events(target_type,target_id,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_type ON feedback_events(event_type,created_at DESC)");
    }

    private static void createAiJobs(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS ai_jobs(id INTEGER PRIMARY KEY AUTOINCREMENT,job_key TEXT UNIQUE,kind TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'queued',priority INTEGER DEFAULT 50,source_mode TEXT DEFAULT 'auto',input_json TEXT,progress_json TEXT,output_json TEXT,error TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,started_at INTEGER DEFAULT 0,completed_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_jobs_state ON ai_jobs(state,priority DESC,created_at ASC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_jobs_recent ON ai_jobs(updated_at DESC)");
    }

    private static void createModelRuns(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS model_runs(id INTEGER PRIMARY KEY AUTOINCREMENT,job_id INTEGER DEFAULT 0,pass_index INTEGER DEFAULT 0,role TEXT,provider TEXT,model TEXT,route TEXT,state TEXT,input_hash TEXT,latency_ms INTEGER DEFAULT 0,tokens_in INTEGER DEFAULT 0,tokens_out INTEGER DEFAULT 0,confidence REAL DEFAULT 0,output_json TEXT,error TEXT,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_model_runs_job ON model_runs(job_id,pass_index,id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_model_runs_model ON model_runs(provider,model,created_at DESC)");
    }

    /** Seed the cross-source entity graph from the current item-scoped entity table. */
    private static void migrateLegacyEntities(SQLiteDatabase db){
        if(!tableExists(db,"entities"))return;
        db.execSQL("INSERT OR IGNORE INTO entity_nodes(kind,canonical_name,normalized_key,status,metadata_json,created_at,updated_at) SELECT upper(COALESCE(kind,'UNKNOWN')),trim(value),lower(trim(COALESCE(kind,'UNKNOWN')))||'|'||lower(trim(value)),'active','{\"migrated_from\":\"entities\"}',MIN(created_at),MAX(created_at) FROM entities WHERE value IS NOT NULL AND trim(value)<>'' GROUP BY lower(trim(COALESCE(kind,'UNKNOWN')))||'|'||lower(trim(value))");
        db.execSQL("INSERT OR IGNORE INTO source_links(from_type,from_id,to_type,to_id,relation,confidence,metadata_json,created_at) SELECT 'memory',e.item_id,'entity',n.id,'mentions',COALESCE(e.confidence,0.5),'{\"migrated_from\":\"entities\"}',e.created_at FROM entities e JOIN entity_nodes n ON n.normalized_key=lower(trim(COALESCE(e.kind,'UNKNOWN')))||'|'||lower(trim(e.value)) WHERE e.value IS NOT NULL AND trim(e.value)<>''");
    }

    private static void addColumn(SQLiteDatabase db,String table,String column,String definition){
        if(!hasColumn(db,table,column))db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
    }

    private static boolean tableExists(SQLiteDatabase db,String table){
        Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{table});
        boolean exists=c.moveToFirst();c.close();return exists;
    }

    private static boolean hasColumn(SQLiteDatabase db,String table,String column){
        Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);boolean found=false;
        while(c.moveToNext()){int i=c.getColumnIndex("name");if(i>=0&&column.equals(c.getString(i))){found=true;break;}}
        c.close();return found;
    }
}
