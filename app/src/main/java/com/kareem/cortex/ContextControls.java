package com.kareem.cortex;

import org.json.JSONObject;
import java.util.*;

/** User corrections always outrank inferred Context state and become durable learning feedback. */
public final class ContextControls {
    private ContextControls(){}

    public static boolean switchTo(VaultDb db,long contextId){
        if(db==null||contextId<=0)return false;ContextStateStore.ContextState x=ContextStateStore.get(db,contextId);if(x==null)return false;ContextStateStore.ContextState before=ContextStateStore.primary(db);long now=System.currentTimeMillis();ContextStateStore.Offer offer=ContextStateStore.offerPrimary(db,x.id,1.0,100,ContextBoundaryDetector.USER_SWITCH+" · explicit user choice",now,0);try{ContextStateStore.feedback(db,x.id,before==null?0:before.id,"PIN_OR_SWITCH",new JSONObject().put("explicit",true).put("from_context_id",before==null?0:before.id));}catch(Throwable ignored){}if(offer.becamePrimary)try{ContextFingerprintLearner.reinforceSelection(db,x,before);}catch(Throwable ignored){}return offer.becamePrimary;
    }

    public static boolean completeCurrent(VaultDb db){
        if(db==null)return false;ContextStateStore.ContextState current=ContextStateStore.primary(db);if(current==null)return false;try{ContextStateStore.feedback(db,current.id,0,"DONE",new JSONObject().put("explicit",true));}catch(Throwable ignored){}ContextStateStore.complete(db,current.id,ContextBoundaryDetector.USER_DONE+" · explicit user completion");
        for(ContextStateStore.ContextState x:ContextStateStore.stack(db,8)){if(x.id==current.id||!ContextStateStore.ROLE_BACKGROUND.equals(x.role))continue;ContextStateStore.Offer resumed=ContextStateStore.offerPrimary(db,x.id,Math.max(.90,x.stackConfidence),Math.max(85,x.priority),ContextBoundaryDetector.resume("Resume most recent suspended context after user completed the primary context"),System.currentTimeMillis(),0);if(resumed.becamePrimary)try{ContextFingerprintLearner.reinforceResume(db,x);}catch(Throwable ignored){}break;}return true;
    }

    public static String why(VaultDb db){
        if(db==null)return"No current context.";ContextStateStore.ContextState x=ContextStateStore.primary(db);if(x==null)return"No current context.";StringBuilder b=new StringBuilder();b.append(x.title).append("\nConfidence: ").append(Math.round(x.stackConfidence*100)).append("%\n");if(!x.transitionReason.isEmpty())b.append("Why now: ").append(x.transitionReason).append('\n');if(!x.goal.isEmpty())b.append("Goal: ").append(x.goal).append('\n');String provenance=provenance(db,x.id);if(!provenance.isEmpty())b.append("Evidence: ").append(provenance);return b.toString().trim();
    }

    private static String provenance(VaultDb db,long contextId){android.database.Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT from_type,from_id,relation,confidence FROM source_links WHERE to_type='context' AND to_id=? ORDER BY created_at DESC LIMIT 5",new String[]{String.valueOf(contextId)});StringBuilder b=new StringBuilder();while(c.moveToNext()){if(b.length()>0)b.append(" · ");b.append(c.getString(0)).append(" #").append(c.getLong(1)).append(" (").append(Math.round(c.getDouble(3)*100)).append("%)");}return b.toString();}catch(Throwable ignored){return"";}finally{if(c!=null)c.close();}}
}
