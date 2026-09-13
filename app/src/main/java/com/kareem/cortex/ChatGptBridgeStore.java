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

/** Private, non-canonical persistence for bridge requests/verdicts and run state. */
public final class ChatGptBridgeStore {
    private static final int STORE_VERSION=2;
    private static final String ROOT="chatgpt_bridge",PENDING="pending",VERDICTS="verdicts",REJECTED="rejected",RUNS="runs";
    private final File root,pendingDir,verdictDir,rejectedDir,runDir;

    public ChatGptBridgeStore(Context context){
        root=new File(context.getFilesDir(),ROOT);
        pendingDir=new File(root,PENDING);
        verdictDir=new File(root,VERDICTS);
        rejectedDir=new File(root,REJECTED);
        runDir=new File(root,RUNS);
        ensureDir(root);ensureDir(pendingDir);ensureDir(verdictDir);ensureDir(rejectedDir);ensureDir(runDir);
        repairLegacyState();
    }

    /** Validate once at dispatch, then persist immutable correlation metadata alongside the wire envelope. */
    public synchronized File putPending(JSONObject request)throws Exception{
        ChatGptBridgeProtocol.Validation v=ChatGptBridgeProtocol.validateEnvelope(request);
        if(!v.ok)throw new IllegalArgumentException("invalid request: "+v.reason);
        PendingRecord record=PendingRecord.fromValidatedRequest(request);
        File target=new File(pendingDir,safeName(record.requestId)+".json");
        atomicWrite(target,record.toJson().toString());
        upsertRunEntry(record,"PENDING",0L);
        return target;
    }

    public synchronized JSONObject getPending(String requestId)throws Exception{
        PendingRecord r=readPendingRecord(new File(pendingDir,safeName(requestId)+".json"),true);
        return r==null?null:r.envelope;
    }

    public synchronized AcceptResult acceptVerdict(JSONObject verdict){
        try{
            ChatGptBridgeProtocol.Validation verdictValidation=ChatGptBridgeProtocol.validateEnvelope(verdict);
            if(!verdictValidation.ok){reject(verdict,"verdict invalid: "+verdictValidation.reason);return AcceptResult.rejected("verdict invalid: "+verdictValidation.reason);}

            String requestId=verdict==null?"":verdict.optString("requestId","");
            String safeId=safeName(requestId);
            File existing=new File(verdictDir,safeId+".json");
            if(existing.isFile())return AcceptResult.duplicate(existing);

            File pendingFile=new File(pendingDir,safeId+".json");
            PendingRecord record=readPendingRecord(pendingFile,true);
            if(record==null){
                if(isKnownResolvedRequest(verdict)) return AcceptResult.duplicate(null);
                if(isKnownRequestId(requestId)){
                    reject(verdict,"KNOWN_REQUEST_MISSING_PENDING");
                    return AcceptResult.rejected("KNOWN_REQUEST_MISSING_PENDING");
                }
                return AcceptResult.ignored("UNRELATED_IGNORED");
            }

            ChatGptBridgeProtocol.Validation v=ChatGptBridgeProtocol.validateVerdictAgainstCorrelation(
                    verdict,record.requestId,record.runId,record.testId,record.payloadSha256);
            if(!v.ok){reject(verdict,v.reason);return AcceptResult.rejected(v.reason);}

            atomicWrite(existing,verdict.toString());
            upsertRunEntry(record,"RESOLVED",System.currentTimeMillis());
            if(pendingFile.exists()&&!pendingFile.delete()){
                return AcceptResult.acceptedWithWarning("VERDICT_ACCEPTED_PENDING_FILE_NOT_REMOVED",existing);
            }
            return AcceptResult.accepted(existing);
        }catch(Throwable t){
            try{reject(verdict,"EXCEPTION_"+t.getClass().getSimpleName());}catch(Throwable ignored){}
            return AcceptResult.rejected(t.getClass().getSimpleName()+": "+safeMessage(t));
        }
    }

    public synchronized void beginRun(String runId,String kind,int expectedCount)throws Exception{
        JSONObject manifest=loadRun(runId);
        manifest.put("storeVersion",STORE_VERSION)
                .put("runId",runId)
                .put("kind",kind==null?"":kind)
                .put("expectedCount",Math.max(0,expectedCount))
                .put("startedAtEpochMs",manifest.optLong("startedAtEpochMs",System.currentTimeMillis()))
                .put("updatedAtEpochMs",System.currentTimeMillis());
        if(manifest.optJSONObject("tests")==null)manifest.put("tests",new JSONObject());
        saveRun(runId,manifest);
    }

    public synchronized JSONObject runStatus(String runId)throws Exception{
        JSONObject manifest=loadRun(runId);
        JSONObject tests=manifest.optJSONObject("tests");
        int expected=manifest.optInt("expectedCount",0),pending=0,resolved=0,missing=Math.max(0,expected);
        JSONObject states=new JSONObject();
        if(tests!=null){
            java.util.Iterator<String> it=tests.keys();
            int seen=0;
            while(it.hasNext()){
                String testId=it.next();seen++;
                JSONObject entry=tests.optJSONObject(testId);
                String state=entry==null?"UNKNOWN":entry.optString("state","UNKNOWN");
                states.put(testId,state);
                if("RESOLVED".equals(state))resolved++; else if("PENDING".equals(state))pending++;
            }
            missing=Math.max(0,expected-seen);
        }
        return new JSONObject()
                .put("runId",runId)
                .put("kind",manifest.optString("kind",""))
                .put("expected",expected)
                .put("pending",pending)
                .put("resolved",resolved)
                .put("missing",missing)
                .put("complete",expected>0&&resolved==expected&&pending==0&&missing==0)
                .put("states",states);
    }

    public synchronized String latestCompleteRun(String kind)throws Exception{
        File[] files=runDir.listFiles((d,n)->n.endsWith(".json"));
        if(files==null)return "";
        long best=-1;String bestRun="";
        for(File f:files){
            try{
                JSONObject m=new JSONObject(readAll(f));
                if(kind!=null&&!kind.equals(m.optString("kind","")))continue;
                JSONObject s=runStatus(m.optString("runId",""));
                if(!s.optBoolean("complete",false))continue;
                long t=m.optLong("updatedAtEpochMs",0L);
                if(t>best){best=t;bestRun=m.optString("runId","");}
            }catch(Throwable ignored){}
        }
        return bestRun;
    }

    public synchronized List<JSONObject> listVerdictsForRun(String runId){
        ArrayList<JSONObject> out=new ArrayList<>();
        for(JSONObject v:listVerdicts())if(runId.equals(v.optString("runId","")))out.add(v);
        return out;
    }

    public synchronized List<JSONObject> listPending(){
        File[] files=pendingDir.listFiles((d,n)->n.endsWith(".json"));
        if(files==null||files.length==0)return Collections.emptyList();
        ArrayList<File> ordered=new ArrayList<>();Collections.addAll(ordered,files);ordered.sort(Comparator.comparingLong(File::lastModified));
        ArrayList<JSONObject> out=new ArrayList<>();
        for(File file:ordered){try{PendingRecord r=readPendingRecord(file,true);if(r!=null)out.add(r.envelope);}catch(Throwable ignored){}}
        return out;
    }

    public synchronized List<JSONObject> listVerdicts(){return readDir(verdictDir);}
    public synchronized JSONObject summary()throws JSONException{return new JSONObject().put("pending",countJson(pendingDir)).put("verdicts",countJson(verdictDir)).put("rejected",countJson(rejectedDir)).put("runs",countJson(runDir)).put("root",root.getAbsolutePath());}

    private void repairLegacyState(){
        try{
            File[] pending=pendingDir.listFiles((d,n)->n.endsWith(".json"));
            if(pending!=null)for(File f:pending){try{PendingRecord r=readPendingRecord(f,true);if(r!=null)upsertRunEntry(r,"PENDING",0L);}catch(Throwable ignored){}}
            File[] verdicts=verdictDir.listFiles((d,n)->n.endsWith(".json"));
            if(verdicts!=null)for(File f:verdicts){
                try{
                    JSONObject v=new JSONObject(readAll(f));JSONObject p=v.optJSONObject("payload");
                    String requestHash=p==null?"":p.optString("requestPayloadSha256","");
                    if(v.optString("requestId","").isEmpty()||v.optString("runId","").isEmpty()||v.optString("testId","").isEmpty()||requestHash.isEmpty())continue;
                    PendingRecord r=new PendingRecord(v.optString("requestId",""),v.optString("runId",""),v.optString("testId",""),requestHash,null);
                    upsertRunEntry(r,"RESOLVED",f.lastModified());
                }catch(Throwable ignored){}
            }
        }catch(Throwable ignored){}
    }

    private PendingRecord readPendingRecord(File file,boolean migrate)throws Exception{
        if(file==null||!file.isFile())return null;
        JSONObject raw=new JSONObject(readAll(file));
        if(raw.optInt("storeVersion",0)==STORE_VERSION&&raw.optJSONObject("requestEnvelope")!=null){
            return PendingRecord.fromRecord(raw);
        }
        // Legacy raw envelope: preserve the original wire hash exactly. Do not re-hash the stored body.
        if(!ChatGptBridgeProtocol.MessageType.CORTEX_TEST_REQUEST.name().equals(raw.optString("messageType","")))throw new IllegalArgumentException("legacy request wrong messageType");
        String requestId=raw.optString("requestId","");String runId=raw.optString("runId","");String testId=raw.optString("testId","");String hash=raw.optString("payloadSha256","");
        if(requestId.isEmpty()||runId.isEmpty()||testId.isEmpty()||hash.isEmpty())throw new IllegalArgumentException("legacy request missing immutable correlation metadata");
        PendingRecord r=new PendingRecord(requestId,runId,testId,hash,raw);
        if(migrate)atomicWrite(file,r.toJson().toString());
        return r;
    }

    private boolean isKnownRequestId(String requestId)throws Exception{
        File[] files=runDir.listFiles((d,n)->n.endsWith(".json"));if(files==null)return false;
        for(File f:files){JSONObject m=new JSONObject(readAll(f));JSONObject tests=m.optJSONObject("tests");if(tests==null)continue;java.util.Iterator<String> it=tests.keys();while(it.hasNext()){JSONObject e=tests.optJSONObject(it.next());if(e!=null&&requestId.equals(e.optString("requestId","")))return true;}}
        return false;
    }

    private boolean isKnownResolvedRequest(JSONObject verdict)throws Exception{
        String requestId=verdict==null?"":verdict.optString("requestId","");
        File[] files=runDir.listFiles((d,n)->n.endsWith(".json"));if(files==null)return false;
        for(File f:files){JSONObject m=new JSONObject(readAll(f));JSONObject tests=m.optJSONObject("tests");if(tests==null)continue;java.util.Iterator<String> it=tests.keys();while(it.hasNext()){JSONObject e=tests.optJSONObject(it.next());if(e!=null&&requestId.equals(e.optString("requestId",""))&&"RESOLVED".equals(e.optString("state","")))return true;}}
        return false;
    }

    private void upsertRunEntry(PendingRecord r,String state,long resolvedAt)throws Exception{
        if(r==null||r.runId.isEmpty()||r.testId.isEmpty())return;
        JSONObject m=loadRun(r.runId);m.put("storeVersion",STORE_VERSION).put("runId",r.runId).put("updatedAtEpochMs",System.currentTimeMillis());
        if(m.optLong("startedAtEpochMs",0L)==0L)m.put("startedAtEpochMs",System.currentTimeMillis());
        JSONObject tests=m.optJSONObject("tests");if(tests==null){tests=new JSONObject();m.put("tests",tests);}
        JSONObject e=tests.optJSONObject(r.testId);if(e==null)e=new JSONObject();
        e.put("requestId",r.requestId).put("payloadSha256",r.payloadSha256).put("state",state);
        if(resolvedAt>0)e.put("resolvedAtEpochMs",resolvedAt);
        tests.put(r.testId,e);saveRun(r.runId,m);
    }

    private JSONObject loadRun(String runId)throws Exception{
        if(runId==null||runId.trim().isEmpty())return new JSONObject();
        File f=new File(runDir,safeName(runId)+".json");return f.isFile()?new JSONObject(readAll(f)):new JSONObject().put("runId",runId).put("tests",new JSONObject());
    }
    private void saveRun(String runId,JSONObject m)throws Exception{atomicWrite(new File(runDir,safeName(runId)+".json"),m.toString());}

    private void reject(JSONObject verdict,String reason)throws Exception{
        String normalizedReason=reason==null?"UNKNOWN":reason;
        JSONObject wrapper=new JSONObject().put("rejectedAtEpochMs",System.currentTimeMillis()).put("reason",normalizedReason).put("message",verdict==null?JSONObject.NULL:verdict);
        String id=verdict==null?"unknown":verdict.optString("requestId","unknown");String payloadHash=verdict==null?"nohash":verdict.optString("payloadSha256","nohash");
        String name=safeName(id)+"__"+safeName(payloadHash)+"__"+safeName(normalizedReason)+".json";File target=new File(rejectedDir,name);
        if(!target.exists())atomicWrite(target,wrapper.toString());
    }

    private static List<JSONObject> readDir(File dir){File[] files=dir.listFiles((d,n)->n.endsWith(".json"));if(files==null||files.length==0)return Collections.emptyList();ArrayList<File> ordered=new ArrayList<>();Collections.addAll(ordered,files);ordered.sort(Comparator.comparingLong(File::lastModified));ArrayList<JSONObject> out=new ArrayList<>();for(File file:ordered){try{out.add(new JSONObject(readAll(file)));}catch(Throwable ignored){}}return out;}
    private static int countJson(File dir){File[] files=dir.listFiles((d,n)->n.endsWith(".json"));return files==null?0:files.length;}
    private static void atomicWrite(File target,String content)throws Exception{File temp=new File(target.getParentFile(),target.getName()+".tmp");try(FileOutputStream fos=new FileOutputStream(temp);BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(fos,StandardCharsets.UTF_8))){writer.write(content==null?"":content);writer.flush();fos.getFD().sync();}if(target.exists()&&!target.delete())throw new IllegalStateException("cannot replace "+target.getName());if(!temp.renameTo(target))throw new IllegalStateException("atomic rename failed for "+target.getName());}
    private static String readAll(File file)throws Exception{StringBuilder sb=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){String line;boolean first=true;while((line=r.readLine())!=null){if(!first)sb.append('\n');first=false;sb.append(line);}}return sb.toString();}
    private static void ensureDir(File dir){if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("cannot create "+dir);if(!dir.isDirectory())throw new IllegalStateException("not a directory: "+dir);}
    private static String safeName(String value){if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("blank id");String out=value.replaceAll("[^A-Za-z0-9._-]","_");if(out.length()>160)out=out.substring(0,160);return out;}
    private static String safeMessage(Throwable t){return t.getMessage()==null?"":t.getMessage();}

    static final class PendingRecord{
        final String requestId,runId,testId,payloadSha256;final JSONObject envelope;
        PendingRecord(String requestId,String runId,String testId,String payloadSha256,JSONObject envelope){this.requestId=requestId;this.runId=runId;this.testId=testId;this.payloadSha256=payloadSha256;this.envelope=envelope;}
        static PendingRecord fromValidatedRequest(JSONObject request){return new PendingRecord(request.optString("requestId",""),request.optString("runId",""),request.optString("testId",""),request.optString("payloadSha256",""),request);}
        static PendingRecord fromRecord(JSONObject record){return new PendingRecord(record.optString("requestId",""),record.optString("runId",""),record.optString("testId",""),record.optString("payloadSha256",""),record.optJSONObject("requestEnvelope"));}
        JSONObject toJson()throws JSONException{return new JSONObject().put("storeVersion",STORE_VERSION).put("state","PENDING").put("requestId",requestId).put("runId",runId).put("testId",testId).put("payloadSha256",payloadSha256).put("requestEnvelope",envelope==null?JSONObject.NULL:envelope);}
    }

    public static final class AcceptResult{
        public final boolean accepted,ignored;public final String detail;public final File file;
        private AcceptResult(boolean accepted,boolean ignored,String detail,File file){this.accepted=accepted;this.ignored=ignored;this.detail=detail;this.file=file;}
        public static AcceptResult accepted(File f){return new AcceptResult(true,false,"ACCEPTED",f);}public static AcceptResult acceptedWithWarning(String d,File f){return new AcceptResult(true,false,d,f);}public static AcceptResult duplicate(File f){return new AcceptResult(false,true,"DUPLICATE_IGNORED",f);}public static AcceptResult ignored(String d){return new AcceptResult(false,true,d,null);}public static AcceptResult rejected(String d){return new AcceptResult(false,false,d,null);}
    }
}
