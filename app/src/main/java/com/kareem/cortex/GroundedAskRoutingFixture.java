package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/**
 * Rollback-only adversarial verifier for Grounded Ask and Cortex truth integrity.
 *
 * This deliberately creates conflicting/corrupt evidence so the test can catch bugs that ordinary
 * happy-path tests miss: losing an attached focal item, routing personal memory externally,
 * suppressing operational authority, aliasing citations, snapshot obligation leakage, promoting
 * noise into Context authority, leaving stale ACTIVE Context episodes behind, or confusing an
 * external notification with the user's own decision/context.
 */
public final class GroundedAskRoutingFixture {
    private GroundedAskRoutingFixture(){}

    public static final class Report {
        public final ArrayList<String> pass=new ArrayList<>(),fail=new ArrayList<>();
        public boolean ok(){return fail.isEmpty();}
        public String text(){StringBuilder b=new StringBuilder();for(String x:pass)b.append("PASS · ").append(x).append('\n');for(String x:fail)b.append("FAIL · ").append(x).append('\n');b.append("Summary: ").append(pass.size()).append(" pass · ").append(fail.size()).append(" fail");return b.toString().trim();}
    }

    public static Report run(Context context,VaultDb db)throws Exception{
        Report r=new Report();SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            exactFocalAuthority(context,db,sql,r);
            personalMemoryPolicy(r);
            operationalPrecedence(r);
            citationIdentity(db,r);
            productTruthPolicy(r);
            snapshotObligationIsolation(db,r);
            noiseAuthorityFirewall(db,r);
            truthReconciliation(db,sql,r);
            return r;
        }finally{sql.endTransaction();}
    }

    public static String verify(Context context,VaultDb db)throws Exception{Report r=run(context,db);if(!r.ok())throw new AssertionError(r.text());return r.text();}

    private static void exactFocalAuthority(Context context,VaultDb db,SQLiteDatabase sql,Report r)throws Exception{
        String nonce=Long.toHexString(System.nanoTime());String focalToken="FOCAL_ROUTE_"+nonce,decoyToken="DECOY_ROUTE_"+nonce;String q="What is the exact routing token in this attached capture?";
        long focal=db.insert("TEXT","diagnostic_rollback","Attached routing fixture","The exact routing token in this attached capture is "+focalToken+".","Diagnostics","grounded,routing,focal","",Fingerprint.text(focalToken),"{\"synthetic\":true}");
        long decoy=db.insert("TEXT","diagnostic_rollback","Semantic decoy","What is the exact routing token in this attached capture? The routing token is "+decoyToken+".","Diagnostics","grounded,routing,decoy","",Fingerprint.text(decoyToken),"{\"synthetic\":true}");
        if(focal<=0||decoy<=0){r.fail.add("focal authority fixture could not create synthetic evidence");return;}sql.execSQL("UPDATE knowledge_items SET status='analyzed',extracted_text=raw_text WHERE id IN (?,?)",new Object[]{focal,decoy});SemanticIndex.indexItem(db,focal);SemanticIndex.indexItem(db,decoy);LocalAskRouter.Result out=BrainRouter.fast(context,db,q,"your_data",focal,null);boolean first=!out.grounded.sources.isEmpty()&&out.grounded.sources.get(0).item.id==focal;boolean exact=out.answer!=null&&out.answer.contains(focalToken)&&!out.answer.contains(decoyToken);if(first&&exact)r.pass.add("Your Data attached focal remains M1 and beats a stronger semantic decoy");else r.fail.add("attached focal lost authority · first_source="+(out.grounded.sources.isEmpty()?"none":out.grounded.sources.get(0).item.id)+" · answer="+clip(out.answer,180));
    }

    private static void personalMemoryPolicy(Report r){String[] personal={"Who was the doctor I saw last week?","Which pharmacy did I use?","اسم الدكتور اللي كشفت عنده كان إيه؟","أنا اشتريت العربية منين؟"};String[] general={"What is the capital of France?","Explain photosynthesis simply.","I want to know the capital of France.","أنا عايز أعرف عاصمة فرنسا."};ArrayList<String> misses=new ArrayList<>(),falsePositives=new ArrayList<>();for(String q:personal)if(!BrainRouter.needsBroadContext(q))misses.add(q);for(String q:general)if(BrainRouter.needsBroadContext(q))falsePositives.add(q);if(misses.isEmpty()&&falsePositives.isEmpty())r.pass.add("personal-memory questions require Cortex grounding while obvious general questions keep fast-general eligibility");else r.fail.add("grounding intent classifier mismatch · personal_missed="+misses+" · general_overgrounded="+falsePositives);}

    private static void operationalPrecedence(Report r){boolean unrelated=BrainRouter.operationalFastPathEligible("What am I waiting for?",991L);boolean explicitFocal=BrainRouter.operationalFastPathEligible("What needs my attention in this attached document?",991L);if(unrelated&&!explicitFocal)r.pass.add("unrelated attachment cannot suppress authoritative operational state; explicit focal wording can keep focal ownership");else r.fail.add("operational/focal precedence mismatch · unrelated="+unrelated+" · explicit_focal="+explicitFocal);}

    private static void citationIdentity(VaultDb db,Report r){String nonce=Long.toHexString(System.nanoTime());long a=db.insert("TEXT","diagnostic_rollback","Citation source A","Citation source A "+nonce,"Diagnostics","citation,source","",Fingerprint.text("cite-a-"+nonce),"{\"synthetic\":true}");long b=db.insert("TEXT","diagnostic_rollback","Citation source B","Citation source B "+nonce,"Diagnostics","citation,source","",Fingerprint.text("cite-b-"+nonce),"{\"synthetic\":true}");KnowledgeItem ka=db.getById(a);ArrayList<SemanticHit> hits=new ArrayList<>();if(ka!=null)hits.add(new SemanticHit(ka,.9,"source A"));int present=SecondBrainEngine.sourceNumber(hits,a),missing=SecondBrainEngine.sourceNumber(hits,b);if(present==1&&missing==0)r.pass.add("citation identity uses M1 only for the actual source and returns 0 for an absent source");else r.fail.add("citation alias detected · present="+present+" · absent_resolved_to="+missing);}

    private static void productTruthPolicy(Report r){
        String cib="لقد تم رفض المعاملة من Google Spotify على بطاقتكم بقيمة EGP 109.00 لعدم كفاية رصيد البطاقة";
        boolean bankDecision=CortexTruthPolicy.confirmedDecision("CIB",cib,"CIB");
        boolean userDecision=CortexTruthPolicy.confirmedDecision("Architecture choice","قررت نستخدم Gemini كـ fallback","manual");
        boolean screenshotAmbient=CortexTruthPolicy.ambientContext("Screenshot saved Tap here to see your screenshot.","Android System");
        boolean workAmbient=CortexTruthPolicy.ambientContext("Review the Villa drawings with Ahmed before tomorrow's meeting","WhatsApp");
        MasterRelevanceFilter.Decision external=MasterRelevanceFilter.evaluateThread("تم الرفض للمعاملة بقيمة EGP 109.00");
        if(!bankDecision&&userDecision&&screenshotAmbient&&!workAmbient&&external.disposition!=MasterRelevanceFilter.Disposition.DECISION)
            r.pass.add("product truth keeps bank/system outcomes out of Decisions and ambient UI out of working Context while preserving explicit user choices");
        else r.fail.add("product truth mismatch · bank_decision="+bankDecision+" · user_decision="+userDecision+" · screenshot_ambient="+screenshotAmbient+" · work_ambient="+workAmbient+" · external="+external.disposition);
    }

    private static void snapshotObligationIsolation(VaultDb db,Report r){String nonce=Long.toHexString(System.nanoTime());long contextId=ContextStateStore.upsert(db,"truth:snapshot:"+nonce,"Truth snapshot fixture","TASK",ContextStateStore.LIFE_SUSPENDED,.8,"","Synthetic activity","{\"synthetic\":true}",System.currentTimeMillis());if(contextId<=0){r.fail.add("truth fixture could not create snapshot context");return;}ContextStateStore.recordSnapshot(db,contextId,"Synthetic activity","LEAKED_OPEN_LOOP_"+nonce,"LEAKED_NEXT_STEP_"+nonce,"synthetic contaminated historical snapshot","{\"synthetic\":true}");ContextOpenLoopResolver.State x=ContextOpenLoopResolver.resolve(db,contextId);if("Synthetic activity".equals(x.currentActivity)&&x.openLoop.isEmpty()&&x.nextStep.isEmpty()&&!x.hasObligation())r.pass.add("historical snapshot text cannot become obligation authority without exact linked provenance");else r.fail.add("snapshot obligation leak · open_loop="+clip(x.openLoop,120)+" · next_step="+clip(x.nextStep,120)+" · derived="+x.derivedId);}

    private static void noiseAuthorityFirewall(VaultDb db,Report r){String nonce=Long.toHexString(System.nanoTime());long contextId=ContextStateStore.upsert(db,"truth:noise:"+nonce,"Truth noise fixture","TASK",ContextStateStore.LIFE_SUSPENDED,.92,"","","{\"synthetic\":true}",System.currentTimeMillis());long signalId=800000000L+(Math.abs(System.nanoTime())%100000000L);ContextMemoryGate.Decision d=new ContextMemoryGate.Decision(ContextMemoryGate.Tier.EPHEMERAL,contextId,.92,0,"relevance governor marked signal as noise");ContextMemoryGate.linkEvidence(db,signalId,d);int n=count(db,"SELECT COUNT(*) FROM source_links WHERE from_type='raw_signal' AND from_id=? AND to_type='context' AND to_id=? AND relation='supports_context'",new String[]{String.valueOf(signalId),String.valueOf(contextId)});if(n==0)r.pass.add("EPHEMERAL/noise evidence is hard-blocked from supports_context authority");else r.fail.add("truth firewall failed · noise supports_context links="+n);}

    private static void truthReconciliation(VaultDb db,SQLiteDatabase sql,Report r){String nonce=Long.toHexString(System.nanoTime());long now=System.currentTimeMillis();long contextId=ContextStateStore.upsert(db,"truth:reconcile:"+nonce,"Truth reconcile fixture","TASK",ContextStateStore.LIFE_SUSPENDED,.9,"","","{\"synthetic\":true}",now);long signalId=700000000L+(Math.abs(System.nanoTime())%100000000L);if(contextId<=0){r.fail.add("truth reconciliation fixture could not create context");return;}sql.execSQL("INSERT OR REPLACE INTO source_links(from_type,from_id,to_type,to_id,relation,confidence,metadata_json,created_at) VALUES('raw_signal',?,'context',?,'supports_context',0.92,?,?)",new Object[]{signalId,contextId,"{\"memory_tier\":\"EPHEMERAL\",\"gate_reason\":\"relevance governor marked signal as noise\",\"synthetic\":true}",now});sql.execSQL("INSERT OR REPLACE INTO context_stack_state(context_id,role,priority,confidence,last_evidence_at,last_transition_at,transition_reason) VALUES(?,'BACKGROUND',40,0.9,?,?,?)",new Object[]{contextId,now,now,"synthetic stale background"});sql.execSQL("INSERT INTO context_episodes(context_id,state,transition,reason,confidence,anchor_signal_id,started_at,ended_at,metadata_json) VALUES(?,'ACTIVE','BACKGROUND','synthetic stale episode',0.9,0,?,0,'{\"synthetic\":true}')",new Object[]{contextId,now-60000L});ContextTruthIntegrity.ReconcileResult x=ContextTruthIntegrity.reconcile(db);int noise=count(db,"SELECT COUNT(*) FROM source_links WHERE from_type='raw_signal' AND from_id=? AND to_type='context' AND to_id=? AND relation='supports_context'",new String[]{String.valueOf(signalId),String.valueOf(contextId)});int active=count(db,"SELECT COUNT(*) FROM context_episodes WHERE context_id=? AND state='ACTIVE' AND ended_at=0",new String[]{String.valueOf(contextId)});if(noise==0&&active==0&&x.noiseLinksRemoved>0&&x.staleEpisodesClosed>0)r.pass.add("truth reconciler removes contradictory noise authority and closes stale non-primary ACTIVE episodes");else r.fail.add("truth reconciliation incomplete · "+x.summary()+" · synthetic_noise="+noise+" · synthetic_active="+active);}

    private static int count(VaultDb db,String sql,String[] args){Cursor c=null;try{c=db.getReadableDatabase().rawQuery(sql,args);return c.moveToFirst()?Math.max(0,c.getInt(0)):0;}finally{if(c!=null)c.close();}}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
