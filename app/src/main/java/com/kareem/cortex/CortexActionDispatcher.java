package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Approval-first execution bridge backed by durable action receipts. */
public final class CortexActionDispatcher {
    private static final long STALE_EXTERNAL_DISPATCH_MS=120_000L;
    private CortexActionDispatcher(){}

    public static void preview(Activity a,VaultDb db,BrainActionStore.Action action){
        if(a==null||db==null||action==null)return;
        StringBuilder message=new StringBuilder();
        message.append(friendlyType(action.type)).append("\n").append(action.title);
        if(!action.evidenceExcerpt.isEmpty())message.append("\n\nFrom Cortex evidence:\n").append(action.evidenceExcerpt);
        if(action.missing.length()>0)message.append("\n\nNeeds: ").append(join(action.missing));
        message.append("\n\nNothing will be sent or changed until you confirm the next step.");
        AlertDialog.Builder b=new AlertDialog.Builder(a).setTitle(action.ready()?"Prepare this action?":"This action needs details").setMessage(message.toString()).setNegativeButton("Cancel",null);
        if(action.ready())b.setPositiveButton(primaryLabel(action.type),(d,w)->executeApproved(a,db,action));
        else b.setPositiveButton("Complete details",(d,w)->completeInBrain(a,action));
        b.setNeutralButton("Dismiss suggestion",(d,w)->{BrainActionStore.markStatus(db,action.rowId,"DISMISSED");toast(a,"Suggestion dismissed");});
        b.show();
    }

    private static void executeApproved(Activity a,VaultDb db,BrainActionStore.Action x){
        long receiptId=0;
        try{
            String canonical=canonicalActionId(x.type);
            if(canonical.isEmpty()){completeInBrain(a,x);return;}
            receiptId=startApprovedReceipt(db,x,canonical);
            CortexActionReceiptStore.Receipt prior=CortexActionReceiptStore.get(db,receiptId);
            if(prior!=null&&CortexActionReceiptStore.VERIFIED.equals(prior.executionStatus)){BrainActionStore.markStatus(db,x.rowId,"DONE");toast(a,"Already verified in Cortex");return;}
            if(prior!=null&&CortexActionReceiptStore.DISPATCHING.equals(prior.executionStatus)){toast(a,"This action is already being handed off");return;}

            if(localType(x.type)){createLocal(db,x,receiptId);toast(a,"Added to Cortex · verified");return;}
            if("CALENDAR_EVENT".equals(x.type)||"REMINDER".equals(x.type)){
                long when=parseWhen(x.payload,"CALENDAR_EVENT".equals(x.type)?"start_time":"trigger_time");
                if(when<=0)when=parseWhen(x.payload,"due_at");
                if(when<=0)when=parseDateTime(x.payload.optString("date",x.payload.optString("due_date","")),x.payload.optString("time",x.payload.optString("due_time","")));
                if(when<=0){CortexActionReceiptStore.markFailed(db,receiptId,"NEEDS_DETAILS","Exact date/time unavailable at dispatch");BrainActionStore.markStatus(db,x.rowId,"NEEDS_DETAILS");toast(a,"Cortex still needs an exact date and time");completeInBrain(a,x);return;}
                String title=first(x.payload,"event_title","reminder_text","title");if(title.isEmpty())title=x.title;
                String body=first(x.payload,"description","body");if(body.isEmpty())body=x.evidenceExcerpt;
                CortexActionReceiptStore.markDispatching(db,receiptId);
                boolean ok=CortexActionExecutor.calendarDraft(a,title,body,when);
                finishHandoff(db,x,receiptId,ok,"Calendar draft handed to Android for user confirmation");return;
            }
            if("CALENDAR_RESCHEDULE".equals(x.type)){CortexActionReceiptStore.markFailed(db,receiptId,"NEEDS_SELECTION","Existing calendar event must be selected explicitly");completeInBrain(a,x);return;}
            if("CALL".equals(x.type)){
                String number=x.payload.optString("phone_number","").trim();if(number.isEmpty()){CortexActionReceiptStore.markFailed(db,receiptId,"NEEDS_DETAILS","Phone number missing");completeInBrain(a,x);return;}
                CortexActionReceiptStore.markDispatching(db,receiptId);boolean ok=false;try{a.startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(number))));ok=true;}catch(Throwable ignored){}
                finishHandoff(db,x,receiptId,ok,"Dialer opened; call completion is not observed by Cortex");return;
            }
            if("MESSAGE_DRAFT".equals(x.type)){
                CortexActionReceiptStore.markDispatching(db,receiptId);String to=first(x.payload,"phone_number","recipient"),body=first(x.payload,"body","message");boolean ok=CortexActionExecutor.messageDraft(a,to,body);finishHandoff(db,x,receiptId,ok,"Message draft handed to Android; send is not verified");return;
            }
            if("EMAIL_DRAFT".equals(x.type)){
                CortexActionReceiptStore.markDispatching(db,receiptId);boolean ok=CortexActionExecutor.emailDraft(a,x.payload.optString("to",""),x.payload.optString("subject",x.title),first(x.payload,"body","message"));finishHandoff(db,x,receiptId,ok,"Email draft handed to Android; send is not verified");return;
            }
            if("PROJECT_LINK".equals(x.type)){
                if(x.sourceItemId<=0){CortexActionReceiptStore.markFailed(db,receiptId,"MISSING_SOURCE","Project link requires source evidence");completeInBrain(a,x);return;}
                CortexActionReceiptStore.markDispatching(db,receiptId);
                final long rid=receiptId;
                CortexActionExecutor.chooseProject(a,db,x.sourceItemId,new CortexActionExecutor.ProjectSelectionCallback(){
                    @Override public void onLinked(long projectId){verifyProjectLink(a,db,x,rid,projectId);}
                    @Override public void onCancelled(){CortexActionReceiptStore.markCancelled(db,rid,"Project selection cancelled before mutation");}
                });return;
            }
            if("WEB_SEARCH".equals(x.type)){
                CortexActionReceiptStore.markDispatching(db,receiptId);boolean ok=CortexActionExecutor.searchWeb(a,x.payload.optString("query",x.title));finishHandoff(db,x,receiptId,ok,"Web search handed to browser; search outcome is not verified");return;
            }
            if("OPEN_APP".equals(x.type)){
                String pkg=x.payload.optString("package","").trim();Intent i=pkg.isEmpty()?null:a.getPackageManager().getLaunchIntentForPackage(pkg);if(i==null){CortexActionReceiptStore.markFailed(db,receiptId,"APP_NOT_RESOLVED","Exact app could not be resolved");toast(a,"Cortex needs the exact app before it can open it");completeInBrain(a,x);return;}
                CortexActionReceiptStore.markDispatching(db,receiptId);boolean ok=false;try{a.startActivity(i);ok=true;}catch(Throwable ignored){}finishHandoff(db,x,receiptId,ok,"App launch handed to Android; downstream effect is not verified");return;
            }
            CortexActionReceiptStore.markFailed(db,receiptId,"UNSUPPORTED_ACTION","No bounded executor for action type "+x.type);completeInBrain(a,x);
        }catch(Throwable e){
            if(receiptId>0)try{CortexActionReceiptStore.markFailed(db,receiptId,"EXECUTION_EXCEPTION",e.getClass().getSimpleName()+": "+safe(e.getMessage()));}catch(Throwable ignored){}
            BrainActionStore.markStatus(db,x.rowId,"FAILED");toast(a,"Action stopped safely: "+safe(e.getMessage()));
        }
    }

    private static long startApprovedReceipt(VaultDb db,BrainActionStore.Action x,String canonical)throws Exception{
        CortexActionRegistry.Action contract=CortexActionRegistry.require(canonical);
        String inputHash=Fingerprint.text(canonical+"|"+x.payload.toString()+"|"+x.sourceItemId+"|"+x.title);
        String baseKey=Fingerprint.text("brain-action|"+x.jobId+"|"+x.key+"|"+canonical);
        long id=CortexActionReceiptStore.begin(db,"brain_action:"+x.rowId,canonical,x.sourceItemId,contract.executor,inputHash,baseKey,contract.undoPolicy==CortexActionRegistry.UndoPolicy.LOCAL_ROLLBACK,new JSONObject().put("job_id",x.jobId).put("action_key",x.key).put("brain_type",x.type).toString());
        CortexActionReceiptStore.Receipt r=CortexActionReceiptStore.get(db,id);
        if(r!=null&&!localType(x.type)&&CortexActionReceiptStore.DISPATCHING.equals(r.executionStatus)){
            long age=r.updatedAt<=0?Long.MAX_VALUE:Math.max(0L,System.currentTimeMillis()-r.updatedAt);
            if(age<STALE_EXTERNAL_DISPATCH_MS)return id;
            if(!CortexActionReceiptStore.markUnknown(db,id,"Previous external handoff remained in DISPATCHING and was recovered before confirmed retry"))throw new IllegalStateException("Could not recover stale external dispatch");
            r=CortexActionReceiptStore.get(db,id);
        }
        if(r!=null&&(CortexActionReceiptStore.FAILED.equals(r.executionStatus)||CortexActionReceiptStore.CANCELLED.equals(r.executionStatus)||CortexActionReceiptStore.UNKNOWN.equals(r.executionStatus)||CortexActionReceiptStore.HANDED_OFF.equals(r.executionStatus)||CortexActionReceiptStore.ROLLED_BACK.equals(r.executionStatus))){
            id=CortexActionReceiptStore.begin(db,"brain_action:"+x.rowId,canonical,x.sourceItemId,contract.executor,inputHash,baseKey+"-retry-"+System.currentTimeMillis(),contract.undoPolicy==CortexActionRegistry.UndoPolicy.LOCAL_ROLLBACK,new JSONObject().put("job_id",x.jobId).put("action_key",x.key).put("brain_type",x.type).put("user_confirmed_retry",true).toString());r=CortexActionReceiptStore.get(db,id);
        }
        if(r!=null&&CortexActionReceiptStore.CREATED.equals(r.executionStatus)){if(!CortexActionReceiptStore.markValidated(db,id))throw new IllegalStateException("Could not validate action receipt");r=CortexActionReceiptStore.get(db,id);}
        if(r!=null&&CortexActionReceiptStore.VALIDATED.equals(r.executionStatus)){if(!CortexActionReceiptStore.markApproved(db,id))throw new IllegalStateException("Could not record user approval");}
        return id;
    }

    private static void finishHandoff(VaultDb db,BrainActionStore.Action x,long receiptId,boolean ok,String summary){
        if(ok){if(!CortexActionReceiptStore.markHandedOff(db,receiptId,summary,"brain_action:"+x.rowId))throw new IllegalStateException("Could not persist handoff receipt");BrainActionStore.markStatus(db,x.rowId,"PREPARED");}
        else{CortexActionReceiptStore.markFailed(db,receiptId,"HANDOFF_FAILED",summary);BrainActionStore.markStatus(db,x.rowId,"FAILED");}
    }

    private static boolean localType(String type){return "TASK".equals(type)||"FOLLOW_UP".equals(type)||"WAIT_FOR".equals(type)||"KNOWLEDGE_NOTE".equals(type);}

    private static void createLocal(VaultDb db,BrainActionStore.Action x,long receiptId)throws Exception{
        String kind="TASK";if("WAIT_FOR".equals(x.type))kind="WAITING";else if("KNOWLEDGE_NOTE".equals(x.type))kind="NOTE";else if("FOLLOW_UP".equals(x.type))kind="FOLLOW_UP";
        JSONObject meta=new JSONObject().put("created_from","brain_action").put("job_id",x.jobId).put("action_key",x.key).put("action_type",x.type).put("source_item_id",x.sourceItemId).put("payload",x.payload);
        String body=first(x.payload,"task_name","body","note","details");if(body.isEmpty())body=x.evidenceExcerpt;
        String fingerprint=Fingerprint.text("brain-action|"+x.jobId+"|"+x.key);
        if(!CortexActionReceiptStore.markDispatching(db,receiptId))throw new IllegalStateException("Receipt was not dispatchable");
        long id=CognitiveStore.addDerived(db,kind,x.title,body,"open",x.confidence,75,fingerprint,meta.toString());
        if(id<=0)throw new IllegalStateException("Could not create Cortex action");
        if(!CortexActionReceiptStore.markDispatched(db,receiptId,"Local derived item written as "+id))throw new IllegalStateException("Could not persist local dispatch state");
        if(x.sourceItemId>0){boolean linked=CognitiveStore.linkChecked(db,"memory",x.sourceItemId,"derived",id,"suggested_action",Math.max(.5,x.confidence),new JSONObject().put("job_id",x.jobId).put("action_key",x.key).put("user_confirmed",true).toString());if(!linked||!sourceLinkExists(db,"memory",x.sourceItemId,"derived",id,"suggested_action")){CortexActionReceiptStore.markFailed(db,receiptId,"PROVENANCE_WRITE_FAILED","Derived item exists but required source provenance was not verified");BrainActionStore.markStatus(db,x.rowId,"FAILED");throw new IllegalStateException("Required provenance link could not be verified");}}
        if(!CortexActionReceiptStore.markVerifying(db,receiptId,"sqlite_read_back+provenance"))throw new IllegalStateException("Could not enter verification state");
        String evidence=derivedEvidence(db,id,fingerprint,kind);if(evidence.isEmpty()){CortexActionReceiptStore.markFailed(db,receiptId,"READ_BACK_FAILED","Derived item could not be read back with expected identity");BrainActionStore.markStatus(db,x.rowId,"FAILED");throw new IllegalStateException("Local write could not be verified");}
        String ref="derived:"+id+(x.sourceItemId>0?";source_link:memory:"+x.sourceItemId+"->derived:"+id:"");
        if(!CortexActionReceiptStore.markVerified(db,receiptId,"Local action verified by read-back","local_sqlite_read_back",ref,Fingerprint.text(evidence),"sqlite_read_back+provenance"))throw new IllegalStateException("Could not persist verified receipt");
        CognitiveStore.feedback(db,"derived",id,"created_from_brain_action",new JSONObject().put("job_id",x.jobId).put("action_key",x.key).put("receipt_id",receiptId).toString(),"brain_actions_002");
        BrainActionStore.markStatus(db,x.rowId,"DONE");
    }

    private static void verifyProjectLink(Activity a,VaultDb db,BrainActionStore.Action x,long receiptId,long projectId){
        try{
            if(!CortexActionReceiptStore.markDispatched(db,receiptId,"Project relation written"))throw new IllegalStateException("Could not persist project dispatch state");
            if(!CortexActionReceiptStore.markVerifying(db,receiptId,"sqlite_source_link_read_back"))throw new IllegalStateException("Could not enter verification state");
            if(!sourceLinkExists(db,"memory",x.sourceItemId,"entity",projectId,"related_project")){CortexActionReceiptStore.markFailed(db,receiptId,"READ_BACK_FAILED","Project relation was not found after write");BrainActionStore.markStatus(db,x.rowId,"FAILED");toast(a,"Project link could not be verified");return;}
            String evidence="memory|"+x.sourceItemId+"|entity|"+projectId+"|related_project";
            if(!CortexActionReceiptStore.markVerified(db,receiptId,"Project link verified by read-back","local_sqlite_read_back","source_link:"+x.sourceItemId+"->"+projectId,Fingerprint.text(evidence),"sqlite_source_link_read_back"))throw new IllegalStateException("Could not persist verified project receipt");
            BrainActionStore.markStatus(db,x.rowId,"DONE");
        }catch(Throwable e){CortexActionReceiptStore.markFailed(db,receiptId,"PROJECT_VERIFY_FAILED",safe(e.getMessage()));BrainActionStore.markStatus(db,x.rowId,"FAILED");toast(a,"Project link stopped safely");}
    }

    private static String derivedEvidence(VaultDb db,long id,String fingerprint,String expectedKind){
        Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id","kind","title","state","fingerprint"},"id=?",new String[]{String.valueOf(id)},null,null,null,"1");String out="";if(c.moveToFirst()){String kind=safe(c.getString(1)),state=safe(c.getString(3)),fp=safe(c.getString(4));if(id==c.getLong(0)&&expectedKind.equalsIgnoreCase(kind)&&fingerprint.equals(fp)&&("open".equalsIgnoreCase(state)||"pending".equalsIgnoreCase(state)))out=id+"|"+kind+"|"+safe(c.getString(2))+"|"+state+"|"+fp;}c.close();return out;
    }

    private static boolean sourceLinkExists(VaultDb db,String fromType,long fromId,String toType,long toId,String relation){Cursor c=db.getReadableDatabase().query("source_links",new String[]{"id"},"from_type=? AND from_id=? AND to_type=? AND to_id=? AND relation=?",new String[]{fromType,String.valueOf(fromId),toType,String.valueOf(toId),relation},null,null,null,"1");boolean exists=c.moveToFirst();c.close();return exists;}

    private static String canonicalActionId(String type){
        if("TASK".equals(type))return"task.create";if("FOLLOW_UP".equals(type))return"follow_up.create";if("WAIT_FOR".equals(type))return"wait_for.create";if("KNOWLEDGE_NOTE".equals(type))return"knowledge_note.create";if("CALENDAR_EVENT".equals(type)||"REMINDER".equals(type)||"CALENDAR_RESCHEDULE".equals(type))return"calendar.prepare";if("CALL".equals(type))return"call.prepare";if("MESSAGE_DRAFT".equals(type))return"message.prepare";if("EMAIL_DRAFT".equals(type))return"email.prepare";if("PROJECT_LINK".equals(type))return"project.link";if("WEB_SEARCH".equals(type))return"web.search";if("OPEN_APP".equals(type))return"app.open";return"";
    }

    private static void completeInBrain(Activity a,BrainActionStore.Action x){String prompt="Complete this Cortex action without inventing missing information. Action: "+x.title+" ["+x.type+"]"+(x.missing.length()>0?". Missing: "+join(x.missing):"")+". Ask only for the minimum information needed, then return an executable action suggestion.";CortexActionExecutor.openBrain(a,x.sourceItemId,prompt);}
    private static long parseWhen(JSONObject p,String key){String s=p.optString(key,"").trim();if(s.isEmpty())return 0;String[] formats={"yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mmXXX","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd'T'HH:mm","yyyy-MM-dd HH:mm"};for(String f:formats)try{SimpleDateFormat d=new SimpleDateFormat(f,Locale.US);d.setLenient(false);return d.parse(s).getTime();}catch(Exception ignored){}return 0;}
    private static long parseDateTime(String date,String time){date=safe(date);time=safe(time);if(date.isEmpty()||time.isEmpty())return 0;try{SimpleDateFormat d=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);d.setLenient(false);return d.parse(date+" "+time).getTime();}catch(Exception e){return 0;}}
    private static String first(JSONObject p,String... keys){for(String k:keys){String x=p.optString(k,"").trim();if(!x.isEmpty())return x;}return"";}
    private static String friendlyType(String t){return safe(t).replace('_',' ').toLowerCase(Locale.ROOT);}
    private static String primaryLabel(String t){if(localType(t))return"Add to Cortex";if("CALL".equals(t))return"Open dialer";if("WEB_SEARCH".equals(t))return"Search";if("OPEN_APP".equals(t))return"Open app";return"Prepare draft";}
    private static String join(org.json.JSONArray a){ArrayList<String> xs=new ArrayList<>();for(int i=0;i<a.length();i++){String x=a.optString(i,"").trim();if(!x.isEmpty())xs.add(x);}return android.text.TextUtils.join(", ",xs);}
    private static void toast(Activity a,String s){try{Toast.makeText(a,s,Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}}
    private static String safe(String s){return s==null?"":s.trim();}
}
