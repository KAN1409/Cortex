package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.database.sqlite.SQLiteDatabase;
import android.provider.CalendarContract;
import android.widget.Toast;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Approval-first execution bridge for structured Brain suggestions.
 * No model output mutates an external system directly: Cortex previews, the user approves,
 * then the owning app gets a draft/edit surface. Local cognitive tasks are created only after approval.
 */
public final class CortexActionDispatcher {
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
        String key=CortexActionReceipts.keyFor(x);
        try{
            long expected=0;String receiptTitle=x.title;
            if("CALENDAR_EVENT".equals(x.type)||"REMINDER".equals(x.type)){
                expected=parseWhen(x.payload,"CALENDAR_EVENT".equals(x.type)?"start_time":"trigger_time");
                if(expected<=0)expected=parseWhen(x.payload,"due_at");
                if(expected<=0)expected=parseDateTime(x.payload.optString("date",x.payload.optString("due_date","")),x.payload.optString("time",x.payload.optString("due_time","")));
                if(expected<=0){toast(a,"Cortex still needs an exact date and time");completeInBrain(a,x);return;}
                receiptTitle=calendarTitle(x);
            }
            // The receipt keeps the exact calendar title so the return check can read the event back.
            CortexActionReceipts.Receipt r=CortexActionReceipts.begin(db.getWritableDatabase(),key,x.rowId,x.type,receiptTitle,expected,System.currentTimeMillis());
            if(r.completed()){toast(a,r.verified()?"Already done and verified":"Already done (you confirmed it)");return;}
            if(CortexActionReceipts.CANCELLED.equals(r.state)){toast(a,"You cancelled this action earlier");return;}

            if(localType(x.type)){long id=createLocal(db,x);settle(db,x,key,derivedExists(db,id),"Local item "+id+" read back","Local item was not found after saving");toast(a,"Added to Cortex");return;}
            if("CALENDAR_EVENT".equals(x.type)||"REMINDER".equals(x.type)){
                String body=first(x.payload,"description","body");if(body.isEmpty())body=x.evidenceExcerpt;
                handoff(db,x,key,CortexActionExecutor.calendarDraft(a,receiptTitle,body,expected));return;
            }
            if("CALENDAR_RESCHEDULE".equals(x.type)){completeInBrain(a,x);return;}
            if("CALL".equals(x.type)){String number=x.payload.optString("phone_number","").trim();if(number.isEmpty()){completeInBrain(a,x);return;}boolean opened=false;try{a.startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(number))));opened=true;}catch(ActivityNotFoundException none){toast(a,"No dialer could open this number");}handoff(db,x,key,opened);return;}
            if("MESSAGE_DRAFT".equals(x.type)){String to=first(x.payload,"phone_number","recipient"),body=first(x.payload,"body","message");handoff(db,x,key,CortexActionExecutor.messageDraft(a,to,body));return;}
            if("EMAIL_DRAFT".equals(x.type)){handoff(db,x,key,CortexActionExecutor.emailDraft(a,x.payload.optString("to",""),x.payload.optString("subject",x.title),first(x.payload,"body","message")));return;}
            // onLinked runs only after the database confirms the link row was written.
            if("PROJECT_LINK".equals(x.type)){if(x.sourceItemId>0)CortexActionExecutor.chooseProject(a,db,x.sourceItemId,()->settle(db,x,key,true,"Project link saved locally",null));else completeInBrain(a,x);return;}
            if("WEB_SEARCH".equals(x.type)){handoff(db,x,key,CortexActionExecutor.searchWeb(a,x.payload.optString("query",x.title)));return;}
            if("OPEN_APP".equals(x.type)){String pkg=x.payload.optString("package","").trim();Intent i=pkg.isEmpty()?null:a.getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){a.startActivity(i);handoff(db,x,key,true);}else{toast(a,"Cortex needs the exact app before it can open it");completeInBrain(a,x);}return;}
            completeInBrain(a,x);
        }catch(Throwable e){
            try{CortexActionReceipts.transition(db.getWritableDatabase(),key,CortexActionReceipts.FAILED,"Stopped: "+safe(e.getMessage()),System.currentTimeMillis());}catch(Throwable ignored){}
            toast(a,"Action stopped safely: "+safe(e.getMessage()));
        }
    }

    /** External app opened with a draft. That is a handoff, never completion; Cortex asks on return. */
    private static void handoff(VaultDb db,BrainActionStore.Action x,String key,boolean opened){
        SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis();
        String to=opened?CortexActionReceipts.HANDED_OFF:CortexActionReceipts.FAILED;
        if(CortexActionReceipts.transition(s,key,to,opened?"Opened in another app":"No app could open this action",now))CortexActionReceipts.syncLegacy(db,x.rowId,to);
    }

    /** Local outcome decided by reading the result back from Cortex's own database. */
    private static void settle(VaultDb db,BrainActionStore.Action x,String key,boolean readBack,String okDetail,String failDetail){
        String to=readBack?CortexActionReceipts.VERIFIED:CortexActionReceipts.FAILED;
        if(CortexActionReceipts.transition(db.getWritableDatabase(),key,to,readBack?okDetail:failDetail,System.currentTimeMillis()))CortexActionReceipts.syncLegacy(db,x.rowId,to);
    }

    private static String calendarTitle(BrainActionStore.Action x){String t=first(x.payload,"event_title","reminder_text","title");return t.isEmpty()?x.title:t;}

    static boolean derivedExists(VaultDb db,long id){
        if(id<=0)return false;
        try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM derived_items WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst();}
    }

    private static boolean localType(String type){return "TASK".equals(type)||"FOLLOW_UP".equals(type)||"WAIT_FOR".equals(type)||"KNOWLEDGE_NOTE".equals(type);}
    private static long createLocal(VaultDb db,BrainActionStore.Action x)throws Exception{
        String kind="TASK";if("WAIT_FOR".equals(x.type))kind="WAITING";else if("KNOWLEDGE_NOTE".equals(x.type))kind="NOTE";else if("FOLLOW_UP".equals(x.type))kind="FOLLOW_UP";
        JSONObject meta=new JSONObject().put("created_from","brain_action").put("job_id",x.jobId).put("action_key",x.key).put("action_type",x.type).put("source_item_id",x.sourceItemId).put("payload",x.payload);
        String body=first(x.payload,"task_name","body","note","details");if(body.isEmpty())body=x.evidenceExcerpt;
        long id=CognitiveStore.addDerived(db,kind,x.title,body,"open",x.confidence,75,Fingerprint.text("brain-action|"+x.jobId+"|"+x.key),meta.toString());
        if(id<=0)throw new IllegalStateException("Could not create Cortex action");
        if(x.sourceItemId>0)CognitiveStore.linkChecked(db,"memory",x.sourceItemId,"derived",id,"suggested_action",Math.max(.5,x.confidence),new JSONObject().put("job_id",x.jobId).put("action_key",x.key).put("user_confirmed",true).toString());
        CognitiveStore.feedback(db,"derived",id,"created_from_brain_action",new JSONObject().put("job_id",x.jobId).put("action_key",x.key).toString(),"brain_actions_001");
        return id;
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
