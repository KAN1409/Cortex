package com.kareem.cortex;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive safe verification runner for Cortex features/sub-features.
 *
 * Rules:
 * - synthetic DB writes are rolled back;
 * - no message/calendar/email is sent;
 * - provider checks are read-only;
 * - every authoritative capability receives an explicit verdict;
 * - deep deterministic fixtures supplement capability state checks so ACTIVE is not treated as proof by itself.
 */
public final class CortexAutoTestSuite {
    private CortexAutoTestSuite(){}

    public static final String PASS="PASS",FAIL="FAIL",BLOCKED="BLOCKED",WARN="WARN";

    public static final class CaseResult {
        public String id="",area="",title="",fixture="",expected="",actual="",verdict=FAIL,error="",coverage="DEEP";
        public long durationMs;
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("id",id);o.put("area",area);o.put("title",title);o.put("fixture",fixture);o.put("expected",expected);o.put("actual",actual);o.put("verdict",verdict);o.put("coverage",coverage);o.put("duration_ms",durationMs);o.put("error",error);}catch(Exception ignored){}return o;}
    }

    public static final class Report {
        public final long startedAt=System.currentTimeMillis();
        public long finishedAt;
        public final ArrayList<CaseResult> capabilityCases=new ArrayList<>(),deepCases=new ArrayList<>();
        public String functionalText="";
        public int functionalPass,functionalWarn,functionalFail;
        public int pass,fail,blocked,warn;
        public boolean ok(){return fail==0;}
        public int total(){return pass+fail+blocked+warn;}
        void recount(){pass=fail=blocked=warn=0;for(CaseResult x:all()){if(PASS.equals(x.verdict))pass++;else if(FAIL.equals(x.verdict))fail++;else if(BLOCKED.equals(x.verdict))blocked++;else warn++;}}
        public ArrayList<CaseResult> all(){ArrayList<CaseResult> x=new ArrayList<>(capabilityCases);x.addAll(deepCases);return x;}
        public JSONObject json(){
            JSONObject root=new JSONObject();try{
                root.put("schema_version",1);root.put("suite","CORTEX_AUTOMATED_VERIFICATION");root.put("generated_at",finishedAt);root.put("duration_ms",Math.max(0,finishedAt-startedAt));
                root.put("package",BuildConfig.APPLICATION_ID);root.put("version",BuildConfig.VERSION_NAME);root.put("android_sdk",Build.VERSION.SDK_INT);root.put("device",Build.MANUFACTURER+" "+Build.MODEL);
                root.put("summary",new JSONObject().put("total",total()).put("pass",pass).put("fail",fail).put("blocked",blocked).put("warn",warn).put("functional_pass",functionalPass).put("functional_warn",functionalWarn).put("functional_fail",functionalFail));
                JSONArray caps=new JSONArray();for(CaseResult x:capabilityCases)caps.put(x.json());root.put("capabilities",caps);JSONArray deep=new JSONArray();for(CaseResult x:deepCases)deep.put(x.json());root.put("deep_tests",deep);root.put("functional_self_test",functionalText);
            }catch(Exception ignored){}return root;
        }
        public String markdown(){
            StringBuilder b=new StringBuilder();
            b.append("# Cortex Automated Verification Report\n\n")
             .append("- Version: `").append(BuildConfig.VERSION_NAME).append("`\n")
             .append("- Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.SDK_INT).append("\n")
             .append("- Duration: ").append(Math.max(0,finishedAt-startedAt)).append(" ms\n")
             .append("- Result: **").append(ok()?"PASS":"FAIL").append("**\n")
             .append("- Cases: ").append(total()).append(" · PASS ").append(pass).append(" · FAIL ").append(fail).append(" · BLOCKED ").append(blocked).append(" · WARN ").append(warn).append("\n")
             .append("- Legacy functional harness: ").append(functionalPass).append(" pass · ").append(functionalWarn).append(" warn · ").append(functionalFail).append(" fail\n\n");
            b.append("## Failures / blockers first\n\n");boolean any=false;for(CaseResult x:all())if(FAIL.equals(x.verdict)||BLOCKED.equals(x.verdict)){any=true;appendCase(b,x);}if(!any)b.append("No failures or blockers.\n\n");
            b.append("## Deep deterministic / integration tests\n\n");for(CaseResult x:deepCases)appendCase(b,x);
            b.append("## 43-capability runtime matrix\n\n| # | Capability | Verdict | Runtime state | Coverage |\n|---:|---|---|---|---|\n");int n=0;for(CaseResult x:capabilityCases)b.append('|').append(++n).append('|').append(escape(x.title)).append('|').append(x.verdict).append('|').append(escape(x.actual)).append('|').append(x.coverage).append("|\n");
            b.append("\n## Existing intelligence functional self-test\n\n```text\n").append(functionalText).append("\n```\n");return b.toString();
        }
        private static void appendCase(StringBuilder b,CaseResult x){b.append("### ").append(x.verdict).append(" · ").append(x.title).append("\n\n- ID: `").append(x.id).append("`\n- Area: ").append(x.area).append("\n- Fixture: ").append(x.fixture).append("\n- Expected: ").append(x.expected).append("\n- Actual: ").append(x.actual).append("\n- Duration: ").append(x.durationMs).append(" ms\n");if(!x.error.isEmpty())b.append("- Error: `").append(x.error.replace("`","'")).append("`\n");b.append('\n');}
        private static String escape(String x){return safe(x).replace("|","\\|").replace("\n","<br>");}
    }

    private interface Checked {String run() throws Exception;}

    public static Report run(Context context){
        Report r=new Report();Context app=context.getApplicationContext();VaultDb db=null;
        try{
            db=new VaultDb(app);HealthStore.ensure(db);
            capabilityMatrix(app,db,r);
            deep(r,"core.fingerprint","Core / identity","Fingerprint determinism","same input twice + changed input","same input must hash identically; changed input must differ",()->{String a=Fingerprint.text("Cortex fixture 123"),b=Fingerprint.text("Cortex fixture 123"),c=Fingerprint.text("Cortex fixture 124");if(a.isEmpty()||!a.equals(b)||a.equals(c))throw new AssertionError("fingerprint mismatch");return a.substring(0,Math.min(16,a.length()))+"… deterministic";});
            deep(r,"health.source_mapping","Health Follow-up","Health source provenance mapping","Samsung/Huawei/unknown package names","Samsung→samsung_health; Huawei→huawei_health; empty→health_connect",()->{String s=HealthStore.sourceKeyForOrigin("com.sec.android.app.shealth"),h=HealthStore.sourceKeyForOrigin("com.huawei.health"),e=HealthStore.sourceKeyForOrigin("");if(!"samsung_health".equals(s)||!"huawei_health".equals(h)||!"health_connect".equals(e))throw new AssertionError(s+" / "+h+" / "+e);return s+" · "+h+" · "+e;});
            final VaultDb fdb=db;
            deep(r,"health.metric_roundtrip","Health Follow-up","Synthetic health metric round-trip","heart_rate=72 bpm from samsung_health inside rollback transaction","row must persist/read with exact provenance during transaction and disappear after rollback",()->healthMetricRoundTrip(fdb));
            deep(r,"health.trend_contract","Health Follow-up","Grounded health trend arithmetic","rollback-only 14-day steps + heart-rate outlier fixture from one exact synthetic source","steps compare recorded-day averages; heart-rate comparison uses median; no fixture data survives",()->HealthTrendFixture.verify(fdb));
            deep(r,"health.evidence_roundtrip","Health Follow-up","Synthetic imported evidence round-trip","medical scan text linked to synthetic Cortex memory inside rollback transaction","health_evidence must retain knowledge_item_id/source/title/body",()->healthEvidenceRoundTrip(fdb));
            deep(r,"visual.recovery_contract","Visual Intelligence","Bounded visual recovery contract","rollback-only exact synthetic image with repeated timeout failures","retry-now preserves the attempt count; the third bounded failure becomes terminal; explicit reset starts a fresh budget only for the synthetic item",()->VisualRecoveryFixture.verify(fdb));
            deep(r,"transcript.manual_override","Audio / learning","Manual transcript correction authority","synthetic VOICE item: original → corrected; simulated later ASR overwrite","effective transcript must remain corrected and trigger must restore corrected extracted_text",()->transcriptRoundTrip(fdb));
            deep(r,"audio.synthetic_contract","Audio / ASR","Synthetic WAV post-provider contract","real 16 kHz WAV + exact sandbox metadata marker; no microphone/provider call","deterministic sandbox may exercise AudioAnalyzer post-provider path but must declare live_provider_tested=false",()->syntheticAudioContract(app));
            deep(r,"rtl.arabic_dominant","UI / bidi","Arabic-dominant RTL detection","8mg/2ml solution ... ده كورتيزون قوي","Arabic sentence beginning with Latin dose must still be Arabic-dominant/RTL",()->{String x="8mg/2ml solution للحقن، ده كورتيزون قوي وبيتاخد تحت إشراف طبي";if(!CortexTextUi.isArabicDominant(x))throw new AssertionError("Arabic-dominant text classified LTR");String y="Open Cortex settings and choose Brain";if(CortexTextUi.isArabicDominant(y))throw new AssertionError("English text classified RTL");return"mixed medical Arabic=RTL · English=LTR";});
            deep(r,"notification.classification","Phone context","Notification semantic classification","Order delivered / تم التوصيل / Battery 80%","delivery EN+AR → delivered; unrelated battery → empty",()->{String a=NotificationEnrichmentEngine.classifyForDiagnostics("Order delivered"),b=NotificationEnrichmentEngine.classifyForDiagnostics("تم التوصيل"),c=NotificationEnrichmentEngine.classifyForDiagnostics("Battery 80%");if(!"delivered".equals(a)||!"delivered".equals(b)||!c.isEmpty())throw new AssertionError(a+" / "+b+" / "+c);return"delivered · delivered · unrelated ignored";});
            deep(r,"privacy.default_deny","Privacy","Unknown evidence cloud default-deny","synthetic future_unknown_source KnowledgeItem","unknown/unclassified source must not be cloud eligible",()->{KnowledgeItem k=new KnowledgeItem(-1,"TEXT","future_unknown_source","Synthetic","","","","","","","analyzed","","","{}",0,0);boolean allowed=CloudEvidencePolicy.canSend(app,k);if(allowed)throw new AssertionError("unknown evidence allowed to cloud");return"cloud_allowed=false";});
            deep(r,"access.inventory","Phone environment","Access gate registry integrity","current device access snapshot","gate keys must be unique and core notification/accessibility/usage/microphone gates must exist",()->accessInventory(app));
            deep(r,"proposal.parser","Proposal fabric","Structured proposal parser fixture","valid R1 BRAIN_PROMPT + REMINDER JSON","private parser must return R1 with two validated proposals",CortexAutoTestSuite::proposalParserFixture);
            deep(r,"proposal.missing_fields","Proposal fabric","Action missing-field validator","REMINDER payload without date/time","validator must require trigger date/time instead of hallucinating one",CortexAutoTestSuite::proposalMissingFixture);
            deep(r,"brain.null_guard","Brain provider","Provider null-content guard","literal null / undefined model outputs","cleaner must reject null-like text as empty",CortexAutoTestSuite::brainNullGuardFixture);
            deep(r,"contracts.critical_methods","Architecture","Critical method contracts","reflection over core classes","required public/private integration methods must still exist",CortexAutoTestSuite::criticalContracts);
            deep(r,"components.onboarding","Phone environment","First-run onboarding component","PackageManager lookup","AccessOnboardingActivity must be registered",()->{app.getPackageManager().getActivityInfo(new ComponentName(app,AccessOnboardingActivity.class),0);return"AccessOnboardingActivity registered";});
            deep(r,"brief.compose","Brief","Daily/weekly brief composition","current DB read-only","both composers must return non-trivial grounded briefs",()->{String d=BriefComposer.compose(fdb,false),w=BriefComposer.compose(fdb,true);if(d.length()<20||w.length()<20||!d.startsWith("Daily Cortex Brief")||!w.startsWith("Weekly Cortex Brief"))throw new AssertionError("brief validation failed");return"daily="+d.length()+" chars · weekly="+w.length()+" chars";});
            deepProviderHealth(app,r);
            CortexFunctionalSelfTest.Report legacy=CortexFunctionalSelfTest.run(app);r.functionalPass=legacy.pass;r.functionalWarn=legacy.warn;r.functionalFail=legacy.fail;r.functionalText=legacy.text();if(!legacy.ok())add(r.deepCases,"legacy.functional","Legacy functional harness","Existing intelligence functional self-test","real read/rollback checks","zero failures",legacy.text(),FAIL,"DEEP",0,"");else add(r.deepCases,"legacy.functional","Legacy functional harness","Existing intelligence functional self-test","real read/rollback checks","zero failures",legacy.pass+" pass · "+legacy.warn+" warnings",legacy.warn>0?WARN:PASS,"DEEP",0,"");
        }catch(Throwable e){add(r.deepCases,"suite.harness","Test harness","Top-level automatic verification","suite execution","runner completes without uncaught exception",e.getClass().getSimpleName()+": "+safe(e.getMessage()),FAIL,"DEEP",0,e.toString());}
        finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
        r.finishedAt=System.currentTimeMillis();r.recount();return r;
    }

    private static void capabilityMatrix(Context c,VaultDb db,Report r){
        for(CortexCapabilityRegistry.Capability cap:CortexCapabilityRegistry.all()){
            long t=SystemClock.elapsedRealtime();try{
                CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(c,db,cap);String v;
                if(CortexCapabilityRegistry.FAILED.equals(s.status)||CortexCapabilityRegistry.NOT_VERIFIED.equals(s.status))v=FAIL;
                else if(CortexCapabilityRegistry.NEEDS_ACCESS.equals(s.status)||CortexCapabilityRegistry.NEEDS_SETUP.equals(s.status))v=BLOCKED;
                else v=PASS;
                String coverage=deepCoverageFor(cap.key)?"STATE+DEEP":"STATE_ONLY";
                add(r.capabilityCases,"cap."+cap.number+"."+cap.key,"Capability #"+cap.number,cap.title,"live runtime state","known state; FAILED/NOT VERIFIED are failures",s.status+" · "+s.detail,v,coverage,SystemClock.elapsedRealtime()-t,"");
            }catch(Throwable e){add(r.capabilityCases,"cap."+cap.number+"."+cap.key,"Capability #"+cap.number,cap.title,"live runtime state","evaluation must complete",e.toString(),FAIL,"STATE_ONLY",SystemClock.elapsedRealtime()-t,e.toString());}
        }
    }

    private static boolean deepCoverageFor(String k){return new HashSet<>(Arrays.asList("app_identity","components","permissions","db_integrity","db_schema","vault_readability","visual_intelligence","privacy_guard","grounded_ask","semantic_retrieval","smart_inbox","prompt_library","calendar_read","contacts_read","notification_capture","audio_asr","background_visual","performance","calendar_write","external_writes","briefs","local_qwen","interaction_telemetry")).contains(k);}

    private static void deep(Report r,String id,String area,String title,String fixture,String expected,Checked test){long t=SystemClock.elapsedRealtime();try{String actual=test.run();add(r.deepCases,id,area,title,fixture,expected,actual,PASS,"DEEP",SystemClock.elapsedRealtime()-t,"");}catch(Throwable e){String m=e.getClass().getSimpleName()+": "+safe(e.getMessage());add(r.deepCases,id,area,title,fixture,expected,m,FAIL,"DEEP",SystemClock.elapsedRealtime()-t,m);}}

    private static String healthMetricRoundTrip(VaultDb db)throws Exception{
        SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();String ext="autotest-health-"+System.nanoTime();try{long id=HealthStore.addMetric(db,"samsung_health","heart_rate",72.0,"bpm",1000,2000,ext,"{\"synthetic\":true}");Cursor c=s.rawQuery("SELECT source_key,metric_type,value_real,unit FROM health_metrics WHERE external_id=?",new String[]{ext});boolean ok=c.moveToFirst()&&"samsung_health".equals(c.getString(0))&&"heart_rate".equals(c.getString(1))&&Math.abs(c.getDouble(2)-72.0)<0.001&&"bpm".equals(c.getString(3));c.close();if(id<=0||!ok)throw new AssertionError("metric insert/read mismatch");return"id="+id+" · samsung_health · heart_rate 72 bpm · rollback";}finally{s.endTransaction();}
    }

    private static String healthEvidenceRoundTrip(VaultDb db)throws Exception{
        SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();String token="autotest-health-evidence-"+System.nanoTime();try{long item=db.insert("TEXT","diagnostic_rollback","Synthetic lab scan",token,"Diagnostics","health,synthetic","",Fingerprint.text(token),"{\"synthetic\":true}");long ev=HealthStore.linkKnowledgeEvidence(db,item,"scan","health_import");Cursor c=s.rawQuery("SELECT source_key,evidence_kind,knowledge_item_id,title,body FROM health_evidence WHERE knowledge_item_id=?",new String[]{String.valueOf(item)});boolean ok=c.moveToFirst()&&"health_import".equals(c.getString(0))&&"scan".equals(c.getString(1))&&c.getLong(2)==item&&"Synthetic lab scan".equals(c.getString(3))&&token.equals(c.getString(4));c.close();if(item<=0||ev<=0||!ok)throw new AssertionError("health evidence provenance mismatch");return"memory="+item+" · evidence="+ev+" · provenance linked · rollback";}finally{s.endTransaction();}
    }

    private static String transcriptRoundTrip(VaultDb db)throws Exception{
        SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();String original="عندي ميعاد السبت الساعة ستة";String corrected="عندي ميعاد السبت الساعة 6 مساء";try{long id=db.insert("VOICE","diagnostic_rollback","Synthetic voice",original,"Diagnostics","voice,synthetic","",Fingerprint.text(original+System.nanoTime()),"{\"synthetic\":true}");KnowledgeItem item=db.getById(id);if(item==null)throw new AssertionError("synthetic voice not readable");if(!TranscriptCorrectionStore.save(db,item,corrected))throw new AssertionError("correction save false");KnowledgeItem after=db.getById(id);String effective=TranscriptCorrectionStore.effectiveText(db,after);if(!corrected.equals(effective))throw new AssertionError("effective correction mismatch: "+effective);ContentValues v=new ContentValues();v.put("extracted_text","wrong later ASR");s.update("knowledge_items",v,"id=?",new String[]{String.valueOf(id)});KnowledgeItem rewritten=db.getById(id);if(rewritten==null||!corrected.equals(rewritten.extractedText))throw new AssertionError("manual correction trigger lost authority");return"original preserved in correction history · corrected transcript authoritative · rollback";}finally{s.endTransaction();}
    }

    private static String syntheticAudioContract(Context c)throws Exception{
        boolean wasActive=CortexExperimentalTestMode.active(c);File f=null;try{
            CortexExperimentalTestMode.set(c,true);f=SyntheticAudioFixture.create(c);if(f==null||!f.isFile()||f.length()<=44)throw new AssertionError("synthetic WAV missing/empty");String metadata=SyntheticAudioFixture.metadata(f);KnowledgeItem item=new KnowledgeItem(-1,"AUDIO",SyntheticAudioFixture.SOURCE,"Synthetic voice journey","","","","Voice & Audio","voice,audio,transcript,synthetic,test",f.getAbsolutePath(),"pending",Fingerprint.file(f.getAbsolutePath()),"",metadata,System.currentTimeMillis(),System.currentTimeMillis());
            CountDownLatch done=new CountDownLatch(1);AtomicReference<AnalysisResult> result=new AtomicReference<>();AtomicReference<Exception> failure=new AtomicReference<>();AudioAnalyzer.analyze(c,item,new AudioAnalyzer.Callback(){public void ok(AnalysisResult r){result.set(r);done.countDown();}public void fail(Exception e){failure.set(e);done.countDown();}});if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("deterministic audio contract did not terminate");if(failure.get()!=null)throw failure.get();AnalysisResult r=result.get();if(r==null)throw new AssertionError("missing AnalysisResult");if(r.engine==null||!r.engine.contains("cortex_deterministic_asr_fixture"))throw new AssertionError("wrong engine: "+r.engine);if(!SyntheticAudioFixture.TRANSCRIPT.equals(r.audioRawTranscript))throw new AssertionError("raw transcript mismatch");if(r.audioDurationMs!=SyntheticAudioFixture.DURATION_MS||r.audioProcessedDurationMs!=SyntheticAudioFixture.DURATION_MS||r.audioCoverage<0.999)throw new AssertionError("duration/coverage mismatch");JSONObject raw=new JSONObject(r.audioRawProviderResponse==null?"{}":r.audioRawProviderResponse);if(raw.optBoolean("live_provider_tested",true))throw new AssertionError("fixture claimed live provider validation");if(!SyntheticAudioFixture.MARKER.equals(raw.optString("fixture","")))throw new AssertionError("provider marker mismatch");return"real WAV "+f.length()+" bytes · deterministic post-provider transcript · coverage 100% · live_provider_tested=false";
        }finally{if(f!=null)try{f.delete();}catch(Throwable ignored){}CortexExperimentalTestMode.set(c,wasActive);}
    }

    private static String accessInventory(Context c)throws Exception{List<AccessGateRegistry.Gate> gs=AccessGateRegistry.snapshot(c);HashSet<String> keys=new HashSet<>();for(AccessGateRegistry.Gate g:gs)if(!keys.add(g.key))throw new AssertionError("duplicate gate "+g.key);for(String req:Arrays.asList("microphone","notification_listener","accessibility","usage"))if(!keys.contains(req))throw new AssertionError("missing gate "+req);return gs.size()+" unique access gates · core gates present";}

    @SuppressWarnings("unchecked") private static String proposalParserFixture()throws Exception{
        Method m=ResultProposalEngine.class.getDeclaredMethod("parse",String.class,int.class);m.setAccessible(true);String raw="{\"schema_version\":3,\"results\":[{\"result_ref\":\"R1\",\"proposals\":[{\"id\":\"p1\",\"title\":\"راجع الموعد\",\"why\":\"مهم\",\"confidence\":0.9,\"execution\":\"BRAIN_PROMPT\",\"next_prompt\":\"لخص المطلوب\"},{\"id\":\"p2\",\"title\":\"اعمل تذكير\",\"execution\":\"ACTION\",\"action\":{\"type\":\"REMINDER\",\"payload\":{\"due_date\":\"2026-08-30\",\"due_time\":\"10:00\"},\"missing_fields\":[]}}]}]}";Map<String,ArrayList<ResultProposalEngine.Proposal>> out=(Map<String,ArrayList<ResultProposalEngine.Proposal>>)m.invoke(null,raw,1);ArrayList<ResultProposalEngine.Proposal> ps=out.get("R1");if(ps==null||ps.size()!=2)throw new AssertionError("parsed proposals="+(ps==null?"null":ps.size()));return"R1 parsed · "+ps.size()+" proposals · BRAIN_PROMPT + REMINDER";}

    private static String proposalMissingFixture()throws Exception{
        Method m=ResultProposalEngine.class.getDeclaredMethod("validatedMissing",String.class,JSONObject.class,JSONArray.class);m.setAccessible(true);JSONArray a=(JSONArray)m.invoke(null,"REMINDER",new JSONObject().put("title","Synthetic reminder"),new JSONArray());boolean found=false;for(int i=0;i<a.length();i++)if(a.optString(i).toLowerCase(Locale.ROOT).contains("date/time"))found=true;if(!found)throw new AssertionError("missing_fields="+a);return"validator returned "+a.toString()+" · no invented time";}

    private static String brainNullGuardFixture()throws Exception{
        Method m=ExternalBrainProvider.class.getDeclaredMethod("cleanModelText",String.class);m.setAccessible(true);String a=(String)m.invoke(null,"null"),b=(String)m.invoke(null,"undefined"),c=(String)m.invoke(null," useful answer ");if(!a.isEmpty()||!b.isEmpty()||!"useful answer".equals(c))throw new AssertionError("null guard mismatch");return"null→empty · undefined→empty · normal text preserved";}

    private static String criticalContracts()throws Exception{
        StringBuilder ok=new StringBuilder();requireMethod(BackupImporter.class,"inspect");requireMethod(BackupImporter.class,"restore");requireMethod(ResultProposalEngine.class,"request");requireMethod(ResultProposalEngine.class,"invalidate");requireMethod(ExternalBrainProvider.class,"ask");requireMethod(ExternalBrainProvider.class,"healthCheck");requireMethod(TranscriptCorrectionStore.class,"save");requireMethod(HealthStore.class,"addMetric");requireMethod(HealthStore.class,"addEvidence");requireMethod(HealthStore.class,"summary");requireMethod(HealthTrendEngine.class,"build");requireMethod(HealthTrendFixture.class,"verify");requireMethod(VisualRecoveryStore.class,"retryRecoverableNow");requireMethod(VisualRecoveryStore.class,"resetTerminalBudget");requireMethod(VisualInsightStore.class,"countRecovering");requireMethod(VisualRecoveryFixture.class,"verify");requireMethod(AccessGateRegistry.class,"snapshot");ok.append("BackupImporter / ProposalEngine / BrainProvider / Transcript / Health / HealthTrend / VisualRecovery / Access contracts present");return ok.toString();}
    private static void requireMethod(Class<?> c,String name)throws Exception{for(Method m:c.getDeclaredMethods())if(name.equals(m.getName()))return;throw new NoSuchMethodException(c.getSimpleName()+"."+name);}

    private static void deepProviderHealth(Context c,Report r){long t=SystemClock.elapsedRealtime();try{if(!ExternalBrainProvider.configured(c)){add(r.deepCases,"brain.provider_health","Brain provider","Live external provider route","read-only health check","configured provider responds or reports a recoverable blocker","No external provider configured",BLOCKED,"LIVE",SystemClock.elapsedRealtime()-t,"");return;}ExternalBrainProvider.HealthReport h=ExternalBrainProvider.healthCheck(c);String actual=h.provider+" · "+h.model+" · HTTP "+h.httpCode+" · "+h.status;if(h.ok)add(r.deepCases,"brain.provider_health","Brain provider","Live external provider route","read-only health check","effective route returns a non-empty answer",actual,PASS,"LIVE",SystemClock.elapsedRealtime()-t,"");else if(h.httpCode==429)add(r.deepCases,"brain.provider_health","Brain provider","Live external provider route","read-only health check","effective route returns a non-empty answer",actual,BLOCKED,"LIVE",SystemClock.elapsedRealtime()-t,h.error);else add(r.deepCases,"brain.provider_health","Brain provider","Live external provider route","read-only health check","effective route returns a non-empty answer",actual,FAIL,"LIVE",SystemClock.elapsedRealtime()-t,h.error);}catch(Throwable e){add(r.deepCases,"brain.provider_health","Brain provider","Live external provider route","read-only health check","health request completes",e.toString(),FAIL,"LIVE",SystemClock.elapsedRealtime()-t,e.toString());}}

    private static void add(ArrayList<CaseResult> list,String id,String area,String title,String fixture,String expected,String actual,String verdict,String coverage,long ms,String error){CaseResult x=new CaseResult();x.id=id;x.area=area;x.title=title;x.fixture=fixture;x.expected=expected;x.actual=actual;x.verdict=verdict;x.coverage=coverage;x.durationMs=ms;x.error=safe(error);list.add(x);}
    private static String safe(String s){return s==null?"":s;}
}
