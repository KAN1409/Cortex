package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.webkit.MimeTypeMap;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.net.URLEncoder;
import java.util.*;

/** Execution layer for Cortex suggestions. External mutations are previewed in the owning app before the user confirms. */
public final class CortexActionExecutor {
    private CortexActionExecutor(){}

    public static boolean searchWeb(Activity a,String query){try{String q=URLEncoder.encode(safe(query),"UTF-8");a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+q)));return true;}catch(Throwable e){toast(a,"Could not open web search");return false;}}
    public static boolean openBrain(Activity a,long itemId,String prompt){try{Intent i=new Intent(a,ProposalAskCortexActivity.class);if(itemId>0)i.putExtra("item_id",itemId);i.putExtra("prefill",safe(prompt));a.startActivity(i);return true;}catch(Throwable e){toast(a,"Could not open Brain");return false;}}

    /** Open the stored original evidence itself. Android chooses the compatible viewer by MIME type. */
    public static boolean openEvidence(Activity a,KnowledgeItem item){
        if(a==null||item==null||safe(item.attachmentPath).isEmpty()){toast(a,"Original file is not available");return false;}
        try{
            File f=new File(item.attachmentPath);if(!f.isFile()){toast(a,"Original file could not be found");return false;}
            Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".feedback.files",f);
            String mime=mimeFor(f.getName());
            Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser=Intent.createChooser(view,"Open original file");chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);a.startActivity(chooser);return true;
        }catch(android.content.ActivityNotFoundException e){toast(a,"No compatible app is installed for this file");return false;}
        catch(Throwable e){toast(a,"Could not open original file");return false;}
    }
    public static boolean openEvidence(Activity a,VaultDb db,long itemId){try{return db!=null&&itemId>0&&openEvidence(a,db.getById(itemId));}catch(Throwable e){toast(a,"Could not open original file");return false;}}
    private static String mimeFor(String name){String x=safe(name).toLowerCase(Locale.ROOT);if(x.endsWith(".pdf"))return"application/pdf";if(x.endsWith(".xlsx"))return"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";if(x.endsWith(".xls"))return"application/vnd.ms-excel";if(x.endsWith(".docx"))return"application/vnd.openxmlformats-officedocument.wordprocessingml.document";if(x.endsWith(".doc"))return"application/msword";if(x.endsWith(".pptx"))return"application/vnd.openxmlformats-officedocument.presentationml.presentation";if(x.endsWith(".ppt"))return"application/vnd.ms-powerpoint";String ext=MimeTypeMap.getFileExtensionFromUrl(x);String m=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return m==null?"application/octet-stream":m;}

    /** Calendar app owns the final write. Cortex only prepares the draft and the user confirms it. */
    public static boolean calendarDraft(Activity a,String title,String description,long suggestedStartMs){try{Intent i=new Intent(Intent.ACTION_INSERT);i.setData(CalendarContract.Events.CONTENT_URI);i.putExtra(CalendarContract.Events.TITLE,safe(title));i.putExtra(CalendarContract.Events.DESCRIPTION,safe(description));if(suggestedStartMs>0)i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,suggestedStartMs);a.startActivity(i);return true;}catch(Throwable e){toast(a,"No calendar app could open this draft");return false;}}
    public static boolean emailDraft(Activity a,String to,String subject,String body){try{Uri u=Uri.parse("mailto:"+Uri.encode(safe(to)));Intent i=new Intent(Intent.ACTION_SENDTO,u);i.putExtra(Intent.EXTRA_SUBJECT,safe(subject));i.putExtra(Intent.EXTRA_TEXT,safe(body));a.startActivity(i);return true;}catch(Throwable e){toast(a,"No email app could open this draft");return false;}}
    public static boolean messageDraft(Activity a,String number,String body){try{Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(safe(number))));i.putExtra("sms_body",safe(body));a.startActivity(i);return true;}catch(Throwable e){toast(a,"No messaging app could open this draft");return false;}}

    /** User-confirmed local project link. No inferred project is silently created. */
    public static void chooseProject(Activity a,VaultDb db,long itemId,Runnable onLinked){if(a==null||db==null||itemId<=0)return;ArrayList<Long> ids=new ArrayList<>();ArrayList<String> names=new ArrayList<>();try{Cursor c=db.getReadableDatabase().rawQuery("SELECT n.id,n.canonical_name FROM entity_nodes n WHERE n.status='active' AND upper(n.kind)='PROJECT' AND COALESCE(n.metadata_json,'') LIKE '%\"created_from\":\"project_candidate\"%' ORDER BY n.updated_at DESC LIMIT 100",null);while(c.moveToNext()){ids.add(c.getLong(0));names.add(safe(c.getString(1)));}c.close();}catch(Throwable e){toast(a,"Could not load projects");return;}if(ids.isEmpty()){toast(a,"No confirmed projects yet");return;}new AlertDialog.Builder(a).setTitle("Add this capture to project").setItems(names.toArray(new String[0]),(d,which)->{long projectId=ids.get(which);boolean ok=false;try{ok=CognitiveStore.linkChecked(db,"memory",itemId,"entity",projectId,"related_project",1.0,new JSONObject().put("user_confirmed",true).toString());if(ok)CognitiveStore.feedback(db,"memory",itemId,"linked_project",new JSONObject().put("project_id",projectId).toString(),"action_executor_001");}catch(Throwable ignored){}toast(a,ok?"Linked to "+names.get(which):"Could not link project");if(ok&&onLinked!=null)onLinked.run();}).setNegativeButton("Cancel",null).show();}

    public static void recordFeedback(VaultDb db,long itemId,String event,String value){try{CognitiveStore.feedback(db,"memory",itemId,event,new JSONObject().put("value",safe(value)).toString(),"capture_feedback_001");}catch(Throwable ignored){}}
    private static void toast(Activity a,String s){if(a!=null)try{Toast.makeText(a,s,Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}}private static String safe(String s){return s==null?"":s.trim();}
}
