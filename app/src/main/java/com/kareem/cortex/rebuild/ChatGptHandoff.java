package com.kareem.cortex.rebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * User-controlled Cortex -> ChatGPT handoff.
 *
 * Cortex only packages grounded context and a task-specific prompt. Nothing is sent until the user
 * explicitly taps Open in ChatGPT. ChatGPT remains an external deep-thinking surface rather than an
 * implicit source of canonical Cortex state.
 */
public final class ChatGptHandoff {
    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    private static final int BG=Color.rgb(8,10,8),SURFACE=Color.rgb(24,28,24),SURFACE2=Color.rgb(31,36,31),BORDER=Color.rgb(55,62,55);
    private static final int TEXT=Color.rgb(244,246,242),MUTED=Color.rgb(164,171,163),FAINT=Color.rgb(112,120,112),BRAND=Color.rgb(143,226,67),BLUE=Color.rgb(75,158,255);

    private ChatGptHandoff() {}

    public static void showForQuestion(Activity activity,CortexDb db,String question){
        String q=clean(question);
        if(q.isEmpty()){Toast.makeText(activity,"Write what you want to think about first",Toast.LENGTH_SHORT).show();return;}
        List<CortexDb.Row> context=related(db,q,-1);
        chooseContext(activity,q,"QUESTION","","",null,context);
    }

    public static void showForSituation(Activity activity,CortexDb db,CortexDb.Row situation,SituationActions.Source source){
        if(situation==null)return;
        String seed=clean(situation.title)+" "+clean(situation.body)+(source==null?"":" "+clean(source.displayText()));
        List<CortexDb.Row> context=related(db,seed,situation.id);
        String question=defaultQuestionFor("SITUATION",situation.title,situation.body,source);
        chooseContext(activity,question,"SITUATION",situation.title,situation.body,source,context);
    }

    public static void showForObject(Activity activity,CortexDb db,CortexDb.Row row,String objectType){
        if(row==null)return;
        String type=clean(objectType).isEmpty()?clean(row.type):clean(objectType);
        String question=defaultQuestionFor(type,row.title,row.body,null);
        List<CortexDb.Row> context=related(db,row.title+" "+row.body,-1);
        chooseContext(activity,question,type,row.title,row.body,null,context);
    }

    private static void chooseContext(Activity activity,String question,String focusType,String focusTitle,String focusBody,SituationActions.Source source,List<CortexDb.Row> candidates){
        if(candidates.isEmpty()){
            preview(activity,buildPrompt(question,focusType,focusTitle,focusBody,source,candidates,new boolean[0]));
            return;
        }
        String[] labels=new String[candidates.size()];
        boolean[] checked=new boolean[candidates.size()];
        for(int i=0;i<candidates.size();i++){
            CortexDb.Row r=candidates.get(i);checked[i]=true;
            labels[i]=displayType(r.type)+" · "+clip(r.title,62);
        }
        new AlertDialog.Builder(activity)
                .setTitle("Context to share with ChatGPT")
                .setMessage("Cortex selected grounded context that may help. Uncheck anything you do not want included.")
                .setMultiChoiceItems(labels,checked,(d,which,isChecked)->checked[which]=isChecked)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Preview prompt",(d,w)->preview(activity,buildPrompt(question,focusType,focusTitle,focusBody,source,candidates,checked)))
                .show();
    }

    private static void preview(Activity activity,String prompt){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(activity,18),0,dp(activity,18),0);
        TextView note=text(activity,"Nothing is sent automatically. Edit anything below, then explicitly open ChatGPT.",12,MUTED,false);note.setPadding(0,0,0,dp(activity,8));box.addView(note);
        EditText editor=new EditText(activity);editor.setText(prompt);editor.setTextColor(TEXT);editor.setHintTextColor(FAINT);editor.setTextSize(13);editor.setGravity(Gravity.TOP);editor.setMinLines(12);editor.setMaxLines(22);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);editor.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12));editor.setBackground(round(activity,SURFACE2,BORDER,14,1));box.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("ChatGPT prompt preview")
                .setView(box)
                .setNeutralButton("Copy",null)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Open in ChatGPT",null)
                .create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{copy(activity,editor.getText().toString());Toast.makeText(activity,"Prompt copied",Toast.LENGTH_SHORT).show();});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String edited=clean(editor.getText().toString());if(edited.isEmpty()){Toast.makeText(activity,"Prompt is empty",Toast.LENGTH_SHORT).show();return;}openChatGpt(activity,edited);dialog.dismiss();});
        });
        dialog.show();
    }

    private static String buildPrompt(String question,String focusType,String focusTitle,String focusBody,SituationActions.Source source,List<CortexDb.Row> candidates,boolean[] checked){
        String combined=(question+" "+focusTitle+" "+focusBody+(source==null?"":" "+source.displayText()+" "+source.url)).toLowerCase(Locale.ROOT);
        Mode mode=modeFor(combined,focusType,source);
        StringBuilder out=new StringBuilder(1600);
        out.append("CORTEX -> CHATGPT HANDOFF\n\n");
        out.append("USER QUESTION\n").append(clean(question)).append("\n\n");
        if(!clean(focusType).isEmpty()&&!focusType.equals("QUESTION")){
            out.append("CURRENT ").append(focusType.toUpperCase(Locale.ROOT)).append("\n");
            if(!clean(focusTitle).isEmpty())out.append("Title: ").append(clean(focusTitle)).append("\n");
            if(!clean(focusBody).isEmpty())out.append("Details: ").append(clean(focusBody)).append("\n");
            out.append("\n");
        }
        if(source!=null){
            out.append("ORIGINAL SOURCE\n");
            out.append("Type: ").append(clean(source.kind).isEmpty()?"evidence":source.kind).append("\n");
            if(!source.displayName.isEmpty())out.append("Name: ").append(source.displayName).append("\n");
            if(!source.transcript.isEmpty())out.append("Original transcript: ").append(source.transcript).append("\n");
            else if(!source.body.isEmpty())out.append("Source text: ").append(source.body).append("\n");
            if(!source.url.isEmpty())out.append("URL: ").append(source.url).append("\n");
            out.append("\n");
        }
        out.append("TASK MODE: ").append(mode.label).append("\n").append(mode.instructions).append("\n\n");
        out.append("GROUNDING RULES\n");
        out.append("- Treat Cortex context as evidence, not guaranteed interpretation.\n");
        out.append("- Do not invent history, facts, relationships, deadlines, or outcomes not present below.\n");
        out.append("- Distinguish the user's original source from Cortex's derived interpretation.\n");
        out.append("- If something is ambiguous or contradictory, say exactly what is uncertain.\n");
        out.append("- Answer the user's question directly and surface useful next actions when appropriate.\n\n");
        int included=0;
        for(int i=0;i<candidates.size();i++)if(i<checked.length&&checked[i])included++;
        out.append("USER-SELECTED CORTEX CONTEXT");
        if(included==0)out.append("\nNo additional Cortex context selected.\n");
        else{
            out.append("\n");int n=1;
            for(int i=0;i<candidates.size();i++){
                if(i>=checked.length||!checked[i])continue;
                CortexDb.Row r=candidates.get(i);
                out.append("\n[").append(n++).append("] ").append(displayType(r.type)).append(" #").append(r.id).append("\n");
                out.append("Title: ").append(clean(r.title)).append("\n");
                if(!clean(r.body).isEmpty())out.append("Content: ").append(clean(r.body)).append("\n");
            }
        }
        return out.toString().trim();
    }

    private static Mode modeFor(String combined,String focusType,SituationActions.Source source){
        String t=combined==null?"":combined;
        boolean hasUrl=(source!=null&&!source.url.isEmpty())||t.contains("http://")||t.contains("https://");
        boolean video=t.contains("youtube")||t.contains("youtu.be")||t.contains("video")||t.contains("فيديو");
        if(hasUrl&&video)return new Mode("VIDEO ANALYSIS","Analyze the linked video/source deeply: give the useful summary, key claims, actionable points, uncertainties, and timestamps only when supported by available content. Do not pretend you watched material that is not actually available in the prompt or URL access.");
        if(hasUrl)return new Mode("SOURCE / WEB ANALYSIS","Analyze the linked source: summarize the important parts, separate facts from claims or opinion, identify what matters for the user's question, and flag anything worth verifying or remembering.");
        if(t.contains("email")||t.contains("e-mail")||t.contains("reply")||t.contains("respond")||t.contains("message")||t.contains("sms")||t.contains("إيميل")||t.contains("ايميل")||t.contains("رسالة"))return new Mode("COMMUNICATION","Understand the communication need, identify missing context, and when useful propose or draft the clearest response. Do not claim anything was sent.");
        if("SITUATION".equalsIgnoreCase(focusType)||t.contains("plan")||t.contains("next")||t.contains("do ")||t.contains("task")||t.contains("deadline")||t.contains("موعد")||t.contains("لازم")||t.contains("اعمل"))return new Mode("DECIDE / PLAN","Work out what this situation actually requires, what is missing or ambiguous, the best next action, sequencing or timing, and anything the user may be overlooking.");
        if("MEMORY".equalsIgnoreCase(focusType))return new Mode("MEMORY EXPLORATION","Use the memory as grounded historical context. Explain what it may imply for the current question without turning an old fact into a current task unless the evidence supports that.");
        if("WORLD".equalsIgnoreCase(focusType)||"PERSON".equalsIgnoreCase(focusType)||"PROJECT".equalsIgnoreCase(focusType)||"ORGANIZATION".equalsIgnoreCase(focusType))return new Mode("ENTITY / PROJECT THINKING","Reason about this person, project, organization, or topic using only the supplied context. Identify relevant connections, open questions, risks, and useful next steps without inventing relationships.");
        if(t.contains("why")||t.contains("understand")||t.contains("diagnos")||t.contains("ليه")||t.contains("افهم"))return new Mode("DIAGNOSE / UNDERSTAND","Look for the best explanation supported by the context, challenge weak assumptions, consider alternatives, and identify what additional evidence would distinguish between them.");
        return new Mode("DEEP THINKING","Think through the question carefully, challenge assumptions, connect only grounded context, and give a concise useful conclusion plus next steps when appropriate.");
    }

    private static String defaultQuestionFor(String type,String title,String body,SituationActions.Source source){
        String combined=(clean(title)+" "+clean(body)+(source==null?"":" "+source.displayText()+" "+source.url)).toLowerCase(Locale.ROOT);
        if((source!=null&&!source.url.isEmpty())||combined.contains("http://")||combined.contains("https://"))return "Analyze this source in the context Cortex has captured. What matters, what should I know, and what should I do with it?";
        if(combined.contains("email")||combined.contains("message")||combined.contains("sms")||combined.contains("إيميل")||combined.contains("ايميل")||combined.contains("رسالة"))return "Help me understand this communication situation and decide the best response or next action.";
        if("SITUATION".equalsIgnoreCase(type))return "Help me think through this situation. What does it actually require, what might Cortex be missing, and what is the best next action?";
        if("MEMORY".equalsIgnoreCase(type))return "Help me think about this memory and whether it has any useful implications for me now.";
        if("WORLD".equalsIgnoreCase(type)||"PERSON".equalsIgnoreCase(type)||"PROJECT".equalsIgnoreCase(type)||"ORGANIZATION".equalsIgnoreCase(type))return "Help me think more deeply about this and identify the useful connections, open questions, or next actions supported by the context.";
        return "Help me think deeply about this using the grounded Cortex context below.";
    }

    private static List<CortexDb.Row> related(CortexDb db,String seed,long excludeSituationId){
        LinkedHashMap<String,CortexDb.Row> found=new LinkedHashMap<>();
        String cleanSeed=clean(seed);
        add(found,db.searchGrounded(cleanSeed,6));
        List<String> keys=keywords(cleanSeed);
        for(String key:keys){if(found.size()>=8)break;add(found,db.searchGrounded(key,3));}
        for(CortexDb.Row s:SituationActions.activeSituations(db,24)){
            if(found.size()>=10)break;
            if(s.id==excludeSituationId)continue;
            if(overlap(keys,(s.title+" "+s.body).toLowerCase(Locale.ROOT))>0){
                CortexDb.Row wrapped=new CortexDb.Row(s.id,s.title,s.body,s.updatedAt,"SITUATION");
                found.putIfAbsent("SITUATION:"+s.id,wrapped);
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void add(Map<String,CortexDb.Row> out,List<CortexDb.Row> rows){
        for(CortexDb.Row r:rows){if(out.size()>=10)return;out.putIfAbsent(displayType(r.type)+":"+r.id,r);}
    }

    private static List<String> keywords(String text){
        String normalized=clean(text).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}،؛؟]+"," ");
        String[] raw=normalized.split("\\s+");
        Set<String> stop=new HashSet<>();
        String[] common={"this","that","with","from","what","when","where","which","about","into","have","need","help","think","cortex","chatgpt","the","and","for","you","your","عاوز","عايز","على","إلى","الى","اللي","فيه","في","من","ده","دي","إيه","ايه","أنا","انا","محتاج"};
        for(String s:common)stop.add(s);
        ArrayList<String> out=new ArrayList<>();
        for(String r:raw){String k=clean(r);if(k.length()<3||stop.contains(k)||out.contains(k))continue;out.add(k);if(out.size()>=8)break;}
        return out;
    }

    private static int overlap(List<String> keys,String text){int n=0;for(String k:keys)if(text.contains(k))n++;return n;}

    private static void openChatGpt(Activity activity,String prompt){
        Intent direct=shareIntent(prompt);direct.setPackage(CHATGPT_PACKAGE);
        try{activity.startActivity(direct);return;}catch(Exception ignored){}
        Intent fallback=shareIntent(prompt);
        try{activity.startActivity(Intent.createChooser(fallback,"Share prompt to ChatGPT"));Toast.makeText(activity,"ChatGPT app was not resolved directly; choose it from Share",Toast.LENGTH_LONG).show();}
        catch(Exception e){copy(activity,prompt);Toast.makeText(activity,"Could not open Share. Prompt copied instead.",Toast.LENGTH_LONG).show();}
    }

    private static Intent shareIntent(String prompt){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,prompt);i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);return i;}
    private static void copy(Context context,String value){ClipboardManager cm=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Cortex ChatGPT prompt",value));}
    private static String displayType(String type){String t=clean(type).toUpperCase(Locale.ROOT);if(t.equals("NEEDS_ATTENTION")||t.equals("WATCHING")||t.equals("QUIET"))return"SITUATION";return t.isEmpty()?"CONTEXT":t;}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String clip(String s,int n){String x=clean(s);return x.length()<=n?x:x.substring(0,n)+"…";}
    private static TextView text(Activity a,String value,int sp,int color,boolean bold){TextView t=new TextView(a);t.setText(value);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.create("sans",Typeface.BOLD));return t;}
    private static GradientDrawable round(Activity a,int fill,int stroke,int radius,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(a,radius));if(width>0)d.setStroke(dp(a,width),stroke);return d;}
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}

    private static final class Mode{final String label,instructions;Mode(String label,String instructions){this.label=label;this.instructions=instructions;}}
}
