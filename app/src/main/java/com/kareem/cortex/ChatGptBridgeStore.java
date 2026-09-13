package com.kareem.cortex;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Private, non-canonical persistence for bridge requests/verdicts. */
public final class ChatGptBridgeStore {
    private static final String ROOT="chatgpt_bridge",PENDING="pending",VERDICTS="verdicts",REJECTED="rejected";
    private final File root,pendingDir,verdictDir,rejectedDir;

    public ChatGptBridgeStore(Context context){root=new File(context.getFilesDir(),ROOT);pendingDir=new File(root,PENDING);verdictDir=new File(root,VERDICTS);rejectedDir=new File(root,REJECTED);ensureDir(root);ensureDir(pendingDir);ensureDir(verdictDir);ensureDir(rejectedDir);}

    public synchronized File putPending(JSONObject request)throws Exception{ChatGptBridgeProtocol.Validation v=ChatGptBridgeProtocol.validateEnvelope(request);if(!v.ok)throw new IllegalArgumentException("invalid request: "+v.reason);String id=safeName(request.optString("requestId",""));File target=new File(pendingDir,id+".json");atomicWrite(target,request.toString());return target;}
    public synchronized JSONObject getPending(String requestId)throws Exception{File file=new File(pendingDir,safeName(requestId)+".json");return file.isFile()?new JSONObject(readAll(file)):null;}

    public synchronized AcceptResult acceptVerdict(JSONObject verdict){
        try{
            String requestId=verdict==null?"":verdict.optString("requestId","");String safeId=safeName(requestId);File existing=new File(verdictDir,safeId+".json");
            if(existing.isFile())return AcceptResult.duplicate(existing);
            JSONObject request=getPending(requestId);if(request==null){reject(verdict,"NO_PENDING_REQUEST");return AcceptResult.rejected("NO_PENDING_REQUEST");}
            ChatGptBridgeProtocol.Validation v=ChatGptBridgeProtocol.validateVerdictAgainstRequest(request,verdict);if(!v.ok){reject(verdict,v.reason);return AcceptResult.rejected(v.reason);}
            atomicWrite(existing,verdict.toString());File pending=new File(pendingDir,safeId+".json");if(pending.exists()&&!pending.delete())return AcceptResult.acceptedWithWarning("VERDICT_ACCEPTED_PENDING_FILE_NOT_REMOVED",existing);return AcceptResult.accepted(existing);
        }catch(Throwable t){try{reject(verdict,"EXCEPTION_"+t.getClass().getSimpleName());}catch(Throwable ignored){}return AcceptResult.rejected(t.getClass().getSimpleName()+": "+safeMessage(t));}
    }

    public synchronized List<JSONObject> listPending(){return readDir(pendingDir);}public synchronized List<JSONObject> listVerdicts(){return readDir(verdictDir);}
    public synchronized JSONObject summary()throws JSONException{return new JSONObject().put("pending",countJson(pendingDir)).put("verdicts",countJson(verdictDir)).put("rejected",countJson(rejectedDir)).put("root",root.getAbsolutePath());}

    private void reject(JSONObject verdict,String reason)throws Exception{
        String normalizedReason=reason==null?"UNKNOWN":reason;
        JSONObject wrapper=new JSONObject().put("rejectedAtEpochMs",System.currentTimeMillis()).put("reason",normalizedReason).put("message",verdict==null?JSONObject.NULL:verdict);
        String id=verdict==null?"unknown":verdict.optString("requestId","unknown");
        String payloadHash=verdict==null?"nohash":verdict.optString("payloadSha256","nohash");
        String name=safeName(id)+"__"+safeName(payloadHash)+"__"+safeName(normalizedReason)+".json";
        atomicWrite(new File(rejectedDir,name),wrapper.toString());
    }
    private static List<JSONObject> readDir(File dir){File[] files=dir.listFiles((d,n)->n.endsWith(".json"));if(files==null||files.length==0)return Collections.emptyList();ArrayList<File> ordered=new ArrayList<>();Collections.addAll(ordered,files);ordered.sort(Comparator.comparingLong(File::lastModified));ArrayList<JSONObject> out=new ArrayList<>();for(File file:ordered){try{out.add(new JSONObject(readAll(file)));}catch(Throwable ignored){}}return out;}
    private static int countJson(File dir){File[] files=dir.listFiles((d,n)->n.endsWith(".json"));return files==null?0:files.length;}
    private static void atomicWrite(File target,String content)throws Exception{File temp=new File(target.getParentFile(),target.getName()+".tmp");try(FileOutputStream fos=new FileOutputStream(temp);BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(fos,StandardCharsets.UTF_8))){writer.write(content==null?"":content);writer.flush();fos.getFD().sync();}if(target.exists()&&!target.delete())throw new IllegalStateException("cannot replace "+target.getName());if(!temp.renameTo(target))throw new IllegalStateException("atomic rename failed for "+target.getName());}
    private static String readAll(File file)throws Exception{StringBuilder sb=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){String line;boolean first=true;while((line=r.readLine())!=null){if(!first)sb.append('\n');first=false;sb.append(line);}}return sb.toString();}
    private static void ensureDir(File dir){if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("cannot create "+dir);if(!dir.isDirectory())throw new IllegalStateException("not a directory: "+dir);}
    private static String safeName(String value){if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("blank id");String out=value.replaceAll("[^A-Za-z0-9._-]","_");if(out.length()>160)out=out.substring(0,160);return out;}
    private static String safeMessage(Throwable t){return t.getMessage()==null?"":t.getMessage();}

    public static final class AcceptResult{public final boolean accepted;public final String detail;public final File file;private AcceptResult(boolean accepted,String detail,File file){this.accepted=accepted;this.detail=detail;this.file=file;}public static AcceptResult accepted(File f){return new AcceptResult(true,"ACCEPTED",f);}public static AcceptResult acceptedWithWarning(String d,File f){return new AcceptResult(true,d,f);}public static AcceptResult duplicate(File f){return new AcceptResult(false,"DUPLICATE_IGNORED",f);}public static AcceptResult rejected(String d){return new AcceptResult(false,d,null);}}
}
