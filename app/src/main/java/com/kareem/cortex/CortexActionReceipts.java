package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Durable, truthful record of every action Cortex prepares.
 *
 * States:
 *   REQUESTED      the user approved; nothing has happened outside Cortex yet
 *   HANDED_OFF     another app was opened with a draft; Cortex cannot know whether it was sent
 *   VERIFIED       Cortex read the result back itself (local row exists, calendar event found)
 *   USER_CONFIRMED the user said it was done; never shown as verified
 *   CANCELLED      the user said it will not happen
 *   FAILED         the handoff or the local read-back failed; may be retried
 *
 * Opening an external app is never completion. One receipt exists per action key, so a
 * repeated tap on an already completed action does not run it again.
 */
public final class CortexActionReceipts {
    public static final String REQUESTED="REQUESTED",HANDED_OFF="HANDED_OFF",VERIFIED="VERIFIED",
            USER_CONFIRMED="USER_CONFIRMED",CANCELLED="CANCELLED",FAILED="FAILED";
    static final String TABLE="cortex_action_receipts";
    private CortexActionReceipts(){}

    public static final class Receipt {
        public final long id,actionRowId,expectedAt,handedOffAt,askedAt,updatedAt;
        public final String actionKey,type,state,title,detail;
        Receipt(long id,long actionRowId,String actionKey,String type,String state,String title,long expectedAt,long handedOffAt,long askedAt,String detail,long updatedAt){
            this.id=id;this.actionRowId=actionRowId;this.actionKey=actionKey;this.type=type;this.state=state;this.title=title;
            this.expectedAt=expectedAt;this.handedOffAt=handedOffAt;this.askedAt=askedAt;this.detail=detail;this.updatedAt=updatedAt;
        }
        public boolean completed(){return isCompleted(state);}
        public boolean verified(){return VERIFIED.equals(state);}
    }

    public static boolean isCompleted(String state){return VERIFIED.equals(state)||USER_CONFIRMED.equals(state);}
    static boolean terminal(String state){return isCompleted(state)||CANCELLED.equals(state);}

    /** Allowed transitions. Anything else is rejected so a stale UI cannot rewrite history. */
    static boolean allowed(String from,String to){
        if(from==null)return REQUESTED.equals(to);
        switch(from){
            case REQUESTED: return HANDED_OFF.equals(to)||VERIFIED.equals(to)||FAILED.equals(to)||CANCELLED.equals(to);
            case HANDED_OFF: return HANDED_OFF.equals(to)||VERIFIED.equals(to)||USER_CONFIRMED.equals(to)||CANCELLED.equals(to)||FAILED.equals(to);
            case FAILED: return REQUESTED.equals(to)||CANCELLED.equals(to);
            default: return false; // VERIFIED, USER_CONFIRMED, CANCELLED are final
        }
    }

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS "+TABLE+"("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "action_key TEXT NOT NULL UNIQUE,"+
                "action_row_id INTEGER NOT NULL DEFAULT 0,"+
                "type TEXT NOT NULL,"+
                "title TEXT NOT NULL DEFAULT '',"+
                "state TEXT NOT NULL,"+
                "expected_at INTEGER NOT NULL DEFAULT 0,"+
                "handed_off_at INTEGER NOT NULL DEFAULT 0,"+
                "asked_at INTEGER NOT NULL DEFAULT 0,"+
                "detail TEXT NOT NULL DEFAULT '',"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_receipts_pending ON "+TABLE+"(state,handed_off_at DESC)");
    }

    public static String keyFor(BrainActionStore.Action x){return "brain|"+x.jobId+"|"+x.key;}

    /**
     * Start (or restart) an action. Returns the existing receipt unchanged when the action is
     * already completed or cancelled, so the caller can skip running it a second time.
     */
    public static Receipt begin(SQLiteDatabase db,String actionKey,long actionRowId,String type,String title,long expectedAt,long now){
        ensure(db);
        Receipt existing=find(db,actionKey);
        if(existing!=null&&terminal(existing.state))return existing;
        ContentValues v=new ContentValues();
        v.put("action_row_id",actionRowId);v.put("type",type==null?"":type);v.put("title",title==null?"":title);v.put("state",REQUESTED);
        v.put("expected_at",expectedAt);v.put("handed_off_at",0);v.put("asked_at",0);v.put("detail","");v.put("updated_at",now);
        if(existing==null){v.put("action_key",actionKey);v.put("created_at",now);db.insertOrThrow(TABLE,null,v);}
        else db.update(TABLE,v,"id=?",new String[]{String.valueOf(existing.id)});
        return find(db,actionKey);
    }

    /** Moves a receipt to a new state if the transition is allowed. Returns false when rejected. */
    public static boolean transition(SQLiteDatabase db,String actionKey,String to,String detail,long now){
        ensure(db);
        Receipt r=find(db,actionKey);
        if(r==null||!allowed(r.state,to))return false;
        ContentValues v=new ContentValues();v.put("state",to);v.put("updated_at",now);
        if(detail!=null)v.put("detail",detail);
        if(HANDED_OFF.equals(to)){v.put("handed_off_at",now);v.put("asked_at",0);}
        return db.update(TABLE,v,"id=? AND state=?",new String[]{String.valueOf(r.id),r.state})==1;
    }

    /** "Not yet": keep the honest HANDED_OFF state but stop asking until the next handoff. */
    public static void markAsked(SQLiteDatabase db,String actionKey,long now){
        ensure(db);ContentValues v=new ContentValues();v.put("asked_at",now);v.put("updated_at",now);
        db.update(TABLE,v,"action_key=? AND state=?",new String[]{actionKey,HANDED_OFF});
    }

    /**
     * Newest handoff that still needs an answer: handed off at least minAgeMs ago (so returning
     * from the dialer a moment later does not count as "returned") and at most maxAgeMs ago.
     */
    public static Receipt pendingConfirmation(SQLiteDatabase db,long now,long minAgeMs,long maxAgeMs){
        ensure(db);
        try(Cursor c=db.rawQuery("SELECT "+COLS+" FROM "+TABLE+" WHERE state=? AND asked_at=0 AND handed_off_at>0 AND handed_off_at<=? AND handed_off_at>=? ORDER BY handed_off_at DESC LIMIT 1",
                new String[]{HANDED_OFF,String.valueOf(now-minAgeMs),String.valueOf(now-maxAgeMs)})){
            return c.moveToFirst()?read(c):null;
        }
    }

    public static Receipt find(SQLiteDatabase db,String actionKey){
        ensure(db);
        try(Cursor c=db.rawQuery("SELECT "+COLS+" FROM "+TABLE+" WHERE action_key=?",new String[]{actionKey})){return c.moveToFirst()?read(c):null;}
    }

    /**
     * Legacy BrainActionStore status kept in step with the receipt so existing screens stay correct.
     * Returns null for REQUESTED/FAILED: the suggestion must stay READY so the user can retry it.
     */
    public static String legacyStatus(String state){
        if(isCompleted(state))return "DONE";
        if(CANCELLED.equals(state))return "CANCELLED";
        if(HANDED_OFF.equals(state))return "PREPARED";
        return null;
    }
    public static void syncLegacy(VaultDb vault,long actionRowId,String state){
        String legacy=legacyStatus(state);
        if(vault!=null&&actionRowId>0&&legacy!=null)BrainActionStore.markStatus(vault,actionRowId,legacy);
    }

    private static final String COLS="id,action_row_id,action_key,type,state,title,expected_at,handed_off_at,asked_at,detail,updated_at";
    private static Receipt read(Cursor c){
        return new Receipt(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getString(9),c.getLong(10));
    }
}
