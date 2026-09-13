package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** Coordinates test-case export, mail delivery, verdict polling, and local acceptance. */
public final class ChatGptBridgeCoordinator {
    private static final int MAX_CONVERGENCE_PASSES = 5;
    private static final long[] CONVERGENCE_BACKOFF_MS = new long[]{0L, 1200L, 1800L, 2500L, 3200L};

    private final Context appContext;
    private final ChatGptBridgeStore store;
    private final BridgeMailTransport transport;

    public ChatGptBridgeCoordinator(Context context, BridgeMailTransport transport) {
        if (transport == null) throw new IllegalArgumentException("transport required");
        this.appContext = context.getApplicationContext();
        this.store = new ChatGptBridgeStore(appContext);
        this.transport = transport;
    }

    public DispatchResult dispatchTest(String runId,String testId,JSONObject originalInput,JSONObject cortexOutput,JSONObject referenceEvidence) {
        try { return dispatchTestWithRules(runId,testId,originalInput,cortexOutput,referenceEvidence,ChatGptBridgeProtocol.defaultJudgingRules()); }
        catch (Throwable t) { return DispatchResult.failed(null,null,t.getClass().getSimpleName()+": "+safeMessage(t),transport.name()); }
    }

    public DispatchResult dispatchTestWithRules(String runId,String testId,JSONObject originalInput,JSONObject cortexOutput,JSONObject referenceEvidence,JSONObject judgingRules) {
        try {
            JSONObject request=ChatGptBridgeProtocol.newTestRequest(runId,testId,originalInput,cortexOutput,referenceEvidence,judgingRules==null?ChatGptBridgeProtocol.defaultJudgingRules():judgingRules);
            File persisted=store.putPending(request);
            GmailBridgeTransport.SendResult sent=transport.sendEnvelope(request);
            if(!sent.ok)return DispatchResult.failed(request,persisted,sent.error,transport.name());
            return DispatchResult.sent(request,persisted,sent.messageId,sent.threadId,transport.name());
        } catch(Throwable t){return DispatchResult.failed(null,null,t.getClass().getSimpleName()+": "+safeMessage(t),transport.name());}
    }

    /**
     * Polls verdicts and, when a comparison run is active, automatically converges across
     * Gmail/IMAP indexing lag. A single caller action may therefore perform several bounded
     * fetch passes. Accepted verdicts remain idempotent in ChatGptBridgeStore, so refetched
     * messages become DUPLICATE_IGNORED rather than being counted twice.
     */
    public PollResult pollVerdicts(long newerThanEpochMs,int maxResults){
        int seen=0,accepted=0,rejected=0,ignored=0;
        JSONArray details=new JSONArray();
        String activeRun=safeActiveRunId();
        int previousPending=pendingFor(activeRun);
        int stagnantPasses=0;
        int passes=0;

        try{
            int limit=(activeRun.isEmpty()||previousPending<=0)?1:MAX_CONVERGENCE_PASSES;
            for(int pass=0;pass<limit;pass++){
                if(pass>0){
                    long delay=CONVERGENCE_BACKOFF_MS[Math.min(pass,CONVERGENCE_BACKOFF_MS.length-1)];
                    if(delay>0)Thread.sleep(delay);
                }

                passes++;
                int acceptedBefore=accepted;
                List<JSONObject> candidates=transport.fetchCandidateVerdicts(newerThanEpochMs,maxResults);
                for(JSONObject verdict:candidates){
                    seen++;
                    ChatGptBridgeStore.AcceptResult result=store.acceptVerdict(verdict);
                    if(result.accepted)accepted++;else if(result.ignored)ignored++;else rejected++;
                    details.put(new JSONObject()
                            .put("pass",passes)
                            .put("requestId",verdict.optString("requestId",""))
                            .put("runId",verdict.optString("runId",""))
                            .put("testId",verdict.optString("testId",""))
                            .put("accepted",result.accepted)
                            .put("ignored",result.ignored)
                            .put("detail",result.detail));
                }

                if(activeRun.isEmpty())break;
                int pendingNow=pendingFor(activeRun);
                if(pendingNow<=0)break;

                boolean progressed=accepted>acceptedBefore || (previousPending>=0 && pendingNow<previousPending);
                if(progressed)stagnantPasses=0;else stagnantPasses++;
                previousPending=pendingNow;

                if(stagnantPasses>=2)break;
            }

            details.put(new JSONObject()
                    .put("kind","CONVERGENCE_SUMMARY")
                    .put("passes",passes)
                    .put("activeRunId",activeRun)
                    .put("remainingPending",pendingFor(activeRun)));

            if(accepted==0&&rejected>0)return new PollResult(false,seen,accepted,rejected,ignored,details,rejectionSummary(details),transport.name());
            return new PollResult(true,seen,accepted,rejected,ignored,details,"",transport.name());
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            return new PollResult(false,seen,accepted,rejected,ignored,details,"InterruptedException: convergence poll interrupted",transport.name());
        }catch(Throwable t){return new PollResult(false,seen,accepted,rejected,ignored,details,t.getClass().getSimpleName()+": "+safeMessage(t),transport.name());}
    }

    public JSONObject status(){try{return store.summary().put("transport",transport.name());}catch(JSONException e){return new JSONObject();}}
    public List<JSONObject> pending(){return store.listPending();}
    public List<JSONObject> verdicts(){return store.listVerdicts();}
    public JSONObject runStatus(String runId){
        try{return store.runStatus(runId);}
        catch(Throwable t){
            JSONObject out=new JSONObject();
            try{out.put("runId",runId).put("error",safeMessage(t));}catch(Throwable ignored){}
            return out;
        }
    }

    private String safeActiveRunId(){
        try{
            String id=CortexComparisonTeachingSuite.activeRunId(appContext);
            return id==null?"":id;
        }catch(Throwable ignored){return "";}
    }

    private int pendingFor(String runId){
        if(runId==null||runId.isEmpty())return -1;
        try{return store.runStatus(runId).optInt("pending",-1);}
        catch(Throwable ignored){return -1;}
    }

    private static String rejectionSummary(JSONArray details){StringBuilder sb=new StringBuilder("VERDICT_REJECTED");int added=0;for(int i=0;i<details.length()&&added<4;i++){JSONObject d=details.optJSONObject(i);if(d==null||d.optBoolean("accepted",false)||d.optBoolean("ignored",false)||"CONVERGENCE_SUMMARY".equals(d.optString("kind","")))continue;sb.append(" · ").append(d.optString("testId","no-test")).append(": ").append(d.optString("detail","UNKNOWN"));added++;}if(added==0)sb.append(" · no rejection detail available");return sb.toString();}
    private static String safeMessage(Throwable t){return t.getMessage()==null?"":t.getMessage();}

    public static final class DispatchResult{
        public final boolean sent;public final JSONObject request;public final File persistedFile;public final String gmailMessageId,gmailThreadId,error,transport;
        private DispatchResult(boolean sent,JSONObject request,File persistedFile,String gmailMessageId,String gmailThreadId,String error,String transport){this.sent=sent;this.request=request;this.persistedFile=persistedFile;this.gmailMessageId=gmailMessageId;this.gmailThreadId=gmailThreadId;this.error=error;this.transport=transport;}
        static DispatchResult sent(JSONObject request,File file,String id,String threadId,String transport){return new DispatchResult(true,request,file,id,threadId,"",transport);}static DispatchResult failed(JSONObject request,File file,String error,String transport){return new DispatchResult(false,request,file,"","",error==null?"UNKNOWN":error,transport);}
    }

    public static final class PollResult{
        public final boolean ok;public final int seen,accepted,rejected,ignored;public final JSONArray details;public final String error,transport;
        PollResult(boolean ok,int seen,int accepted,int rejected,int ignored,JSONArray details,String error,String transport){this.ok=ok;this.seen=seen;this.accepted=accepted;this.rejected=rejected;this.ignored=ignored;this.details=details;this.error=error;this.transport=transport;}
    }
}
