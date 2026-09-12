package com.kareem.cortex;

import android.content.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Sends a grounded document-build package to ChatGPT without making ChatGPT a source of canonical evidence. */
public final class WorkDocumentBuilderBridge {
    public static final String VERSION="work_document_builder_bridge_002";
    public static final String CHATGPT_PACKAGE="com.openai.chatgpt";
    private WorkDocumentBuilderBridge(){}

    public static final class Prepared {
        public final File jsonFile;
        public final Uri contentUri;
        public final String prompt;
        public final JSONObject payload;
        Prepared(File f,Uri u,String p,JSONObject j){jsonFile=f;contentUri=u;prompt=p;payload=j;}
    }

    public static Prepared prepare(Context context,VaultDb vault,WorkDocumentRecipe.Kind kind,String projectFilter) throws Exception {
        JSONObject payload=WorkDocumentBuildPackage.build(vault,kind,projectFilter);
        File dir=new File(context.getFilesDir(),"document_build_packages");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create build package directory");
        String base="cortex_"+kind.name().toLowerCase()+"_"+System.currentTimeMillis();
        File json=new File(dir,base+".json");
        try(OutputStream out=new FileOutputStream(json)){out.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));}
        Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".feedback.files",json);
        WorkDocumentRecipe.Recipe recipe=WorkDocumentRecipe.forKind(kind);
        WorkGeneratedDocumentRegistry.register(vault.getWritableDatabase(),kind,recipe.outputFormat,projectFilter,json.getAbsolutePath(),uri.toString());
        String prompt=prompt(kind,payload);
        return new Prepared(json,uri,prompt,payload);
    }

    public static boolean openChatGpt(Context context,Prepared prepared){
        if(context==null||prepared==null)return false;
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_STREAM,prepared.contentUri);
        i.putExtra(Intent.EXTRA_TEXT,prepared.prompt);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage(CHATGPT_PACKAGE);
        try{context.startActivity(i);return true;}catch(Throwable first){
            try{
                Intent fallback=new Intent(Intent.ACTION_SEND);fallback.setType("application/json");
                fallback.putExtra(Intent.EXTRA_STREAM,prepared.contentUri);fallback.putExtra(Intent.EXTRA_TEXT,prepared.prompt);
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(fallback,"Send document build package"));return true;
            }catch(Throwable ignored){return false;}
        }
    }

    public static String prompt(WorkDocumentRecipe.Kind kind,JSONObject payload){
        WorkDocumentRecipe.Recipe r=WorkDocumentRecipe.forKind(kind);
        String project=payload==null?"":payload.optString("projectFilter","");
        return "Cortex Work Vault document build request.\n\n"+
                "Create the finished "+r.outputFormat+" file for: "+WorkDocumentRecipe.displayName(kind)+".\n"+
                (project.isEmpty()?"":"Project filter: "+project+".\n")+
                "The attached JSON is the authoritative build package for this generation request.\n\n"+
                "Rules:\n"+
                "- Use only facts present in the JSON and its sourceEvidence.\n"+
                "- Do not invent missing commercial, contractual, pricing, quantity, tax, date, approval, PR, PO, vendor or project facts.\n"+
                "- Missing required data must remain blank or be clearly marked FOR REVIEW.\n"+
                "- Preserve Arabic RTL layout where Arabic is used.\n"+
                "- Follow documentKind, orderFamily and orderScope exactly.\n"+
                "- Produce a professional production-ready document, not a text-only draft.\n"+
                "- Return the actual finished "+r.outputFormat+" file.\n"+
                "- Treat the produced file as GENERATED_DOCUMENT derived from the listed sources, not as new original evidence.\n";
    }
}