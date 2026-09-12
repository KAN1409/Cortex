package com.kareem.cortex;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Direct user-invoked ChatGPT document builder with explicit reference attachments and detailed requirements. */
public final class WorkChatGptDirectBridge {
    public static final String VERSION="work_chatgpt_direct_bridge_001";
    public static final String CHATGPT_PACKAGE="com.openai.chatgpt";
    private WorkChatGptDirectBridge(){}

    public static final class Prepared {
        public final File jsonFile;
        public final Uri jsonUri;
        public final ArrayList<Uri> attachments;
        public final String prompt;
        public final JSONObject payload;
        Prepared(File f,Uri j,ArrayList<Uri> a,String p,JSONObject o){jsonFile=f;jsonUri=j;attachments=a;prompt=p;payload=o;}
    }

    public static Prepared prepare(Context context,VaultDb vault,WorkDocumentRecipe.Kind kind,String project,String requirements,ArrayList<Uri> references) throws Exception {
        JSONObject payload=WorkDocumentBuildPackage.build(vault,kind,project);
        payload.put("requestMode","DIRECT_CHATGPT_REFERENCE_BUILD");
        payload.put("userRequirements",requirements==null?"":requirements.trim());
        payload.put("selectedReferenceDocuments",referenceMetadata(context,references));
        payload.put("referenceDocumentCount",references==null?0:references.size());
        payload.put("referenceUsePolicy",referencePolicy());

        File dir=new File(context.getFilesDir(),"document_build_packages");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create build package directory");
        File json=new File(dir,"cortex_direct_chatgpt_"+kind.name().toLowerCase()+"_"+System.currentTimeMillis()+".json");
        try(OutputStream out=new FileOutputStream(json)){out.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));}
        Uri jsonUri=FileProvider.getUriForFile(context,context.getPackageName()+".feedback.files",json);

        ArrayList<Uri> all=new ArrayList<>();all.add(jsonUri);if(references!=null)all.addAll(references);
        WorkDocumentRecipe.Recipe recipe=WorkDocumentRecipe.forKind(kind);
        WorkGeneratedDocumentRegistry.register(vault.getWritableDatabase(),kind,recipe.outputFormat,project,"CHATGPT_DIRECT_REFERENCE_BUILDER",json.getAbsolutePath(),jsonUri.toString());
        return new Prepared(json,jsonUri,all,prompt(kind,project,requirements,references),payload);
    }

    public static boolean openChatGpt(Context context,Prepared p){
        if(context==null||p==null)return false;
        Intent i=new Intent(Intent.ACTION_SEND_MULTIPLE);
        i.setType("*/*");
        i.putParcelableArrayListExtra(Intent.EXTRA_STREAM,p.attachments);
        i.putExtra(Intent.EXTRA_TEXT,p.prompt);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage(CHATGPT_PACKAGE);
        ClipData clip=null;
        for(Uri u:p.attachments){if(u==null)continue;if(clip==null)clip=ClipData.newRawUri("Cortex document references",u);else clip.addItem(new ClipData.Item(u));}
        if(clip!=null)i.setClipData(clip);
        try{context.startActivity(i);return true;}catch(Throwable directFailure){
            try{
                Intent fallback=new Intent(Intent.ACTION_SEND_MULTIPLE);fallback.setType("*/*");fallback.putParcelableArrayListExtra(Intent.EXTRA_STREAM,p.attachments);fallback.putExtra(Intent.EXTRA_TEXT,p.prompt);fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);if(clip!=null)fallback.setClipData(clip);context.startActivity(Intent.createChooser(fallback,"Send references to document builder"));return true;
            }catch(Throwable ignored){return false;}
        }
    }

    private static JSONArray referenceMetadata(Context context,ArrayList<Uri> refs){
        JSONArray a=new JSONArray();if(refs==null)return a;
        for(Uri uri:refs){if(uri==null)continue;JSONObject o=new JSONObject();try{
            o.put("uri",uri.toString());o.put("displayName",displayName(context,uri));o.put("mimeType",safeMime(context,uri));o.put("role","USER_SELECTED_REFERENCE_DOCUMENT");a.put(o);
        }catch(Throwable ignored){}}
        return a;
    }

    private static JSONObject referencePolicy(){JSONObject o=new JSONObject();try{
        o.put("referenceDocumentsArePrimaryStyleAndStructureGuides",true);
        o.put("preserveLayoutWhenReasonable",true);
        o.put("preserveTablesAndSectionOrderWhenRequested",true);
        o.put("doNotInventMissingFacts",true);
        o.put("archiveFactsRemainGroundedEvidence",true);
        o.put("generatedOutputIsNotOriginalEvidence",true);
    }catch(Throwable ignored){}return o;}

    private static String prompt(WorkDocumentRecipe.Kind kind,String project,String requirements,ArrayList<Uri> refs){
        WorkDocumentRecipe.Recipe recipe=WorkDocumentRecipe.forKind(kind);
        int count=refs==null?0:refs.size();String req=requirements==null?"":requirements.trim();
        return "Cortex Work Vault direct document build request.\n\n"+
                "Create the finished "+recipe.outputFormat+" file for: "+WorkDocumentRecipe.displayName(kind)+".\n"+
                (project==null||project.trim().isEmpty()?"":"Project: "+project.trim()+".\n")+
                "Attached: one Cortex JSON build package plus "+count+" user-selected reference document(s).\n\n"+
                "USER REQUIREMENTS:\n"+(req.isEmpty()?"No extra free-text requirements were supplied; follow the JSON and references precisely.":req)+"\n\n"+
                "EXECUTION RULES:\n"+
                "- Read every attached reference document before building the output.\n"+
                "- Use the reference documents to understand the intended model, layout, headings, table structure, wording style and level of detail.\n"+
                "- Use the attached JSON for structured Work Vault facts and provenance.\n"+
                "- Follow the user requirements above with high priority unless they contradict grounded source facts.\n"+
                "- Never invent missing prices, quantities, dates, approvals, PR numbers, PO numbers, vendors, taxes or contractual facts.\n"+
                "- If a required factual field is not supported by the attachments or JSON, leave it blank or mark it FOR REVIEW.\n"+
                "- Preserve Arabic RTL and professional formatting where relevant.\n"+
                "- For an order, follow orderFamily and orderScope exactly.\n"+
                "- Produce the actual finished "+recipe.outputFormat+" file, not only draft text or instructions.\n"+
                "- Treat the result as GENERATED_DOCUMENT derived from the supplied references and evidence.\n";
    }

    private static String displayName(Context c,Uri u){Cursor x=null;try{x=c.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(x!=null&&x.moveToFirst())return x.getString(0);}catch(Throwable ignored){}finally{if(x!=null)x.close();}return u.getLastPathSegment()==null?"reference":u.getLastPathSegment();}
    private static String safeMime(Context c,Uri u){try{String m=c.getContentResolver().getType(u);return m==null?"application/octet-stream":m;}catch(Throwable ignored){return "application/octet-stream";}}
}
