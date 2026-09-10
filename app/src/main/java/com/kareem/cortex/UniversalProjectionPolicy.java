package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/**
 * Trust boundary between semantic interpretation and user-facing projections.
 * A model may describe any event, but ACTION/WAITING/DECISION require typed evidence.
 */
public final class UniversalProjectionPolicy {
    public static final String VERSION="projection_policy_004";
    private UniversalProjectionPolicy(){}

    public static String validateAttention(String semanticType,String intent,String proposed,double confidence){
        String kind=n(proposed).toUpperCase(Locale.ROOT),t=norm(semanticType),i=norm(intent);
        if(confidence<0.70)return "NONE";
        if("ACTION".equals(kind))return actionType(t,i)?"ACTION":"INFO";
        if("WAITING".equals(kind))return waitingType(t,i)?"WAITING":"INFO";
        if("DECISION".equals(kind))return decisionType(t,i)?"DECISION":"INFO";
        return "INFO".equals(kind)?"INFO":"NONE";
    }

    public static boolean userFacingSemantic(String type,String state,double confidence,String subject,String summary){
        String t=norm(type);
        if(!"complete".equalsIgnoreCase(n(state))||confidence<0.70)return false;
        if(t.isEmpty()||"notification_event".equals(t)||"technical_state".equals(t)||"cortexevent".equals(t))return false;
        return !n(subject).isEmpty()||!n(summary).isEmpty();
    }

    public static void sanitizeExisting(SQLiteDatabase db){
        UniversalEventStore.ensure(db);
        Cursor c=db.rawQuery("SELECT a.id,a.kind,e.semantic_type,e.intent,e.confidence FROM ue_attention_items a JOIN ue_semantic_events e ON e.id=a.semantic_event_id WHERE a.state='open'",null);
        while(c.moveToNext()){
            long id=c.getLong(0);String original=n(c.getString(1));String valid=validateAttention(c.getString(2),c.getString(3),original,c.getDouble(4));
            if(!original.equals(valid)){
                ContentValues v=new ContentValues();v.put("state","invalidated");v.put("resolved_at",System.currentTimeMillis());v.put("updated_at",System.currentTimeMillis());v.put("reason","Projection invalidated by "+VERSION+": semantic type/intent does not support "+original);db.update("ue_attention_items",v,"id=?",new String[]{String.valueOf(id)});
            }
        }
        c.close();
        db.execSQL("UPDATE ue_situations SET state='resolved',resolved_at=?,updated_at=? WHERE state='open' AND id NOT IN (SELECT DISTINCT situation_id FROM ue_attention_items WHERE state='open' AND situation_id>0) AND kind='context'",new Object[]{System.currentTimeMillis(),System.currentTimeMillis()});
    }

    private static boolean actionType(String t,String i){
        return has(t,"action_request","request","task","action_required","deadline","payment_due","security_alert","required_response") || has(i,"request","command","send","reply","respond","call_back","pay","submit","provide","confirm","schedule","remind");
    }
    private static boolean waitingType(String t,String i){return has(t,"waiting","commitment","follow_up","pending_response","dependency")||has(i,"waiting","awaiting","follow_up","pending");}
    private static boolean decisionType(String t,String i){return has(t,"decision","choice","approval")||has(i,"decide","decision","approved","rejected","selected");}
    private static boolean has(String value,String...needles){for(String x:needles)if(value.contains(x))return true;return false;}
    private static String norm(String s){return n(s).toLowerCase(Locale.ROOT).replace(' ','_').replace('-','_');}
    private static String n(String s){return s==null?"":s.trim();}
}
