package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Experimental user-journey crawler.
 *
 * The crawler is intentionally breadth-biased: one logical control on a Cortex screen is explored
 * once even when background progress text changes. It scrolls before backtracking, ignores passive
 * progress/status labels, records first visible consequence separately from background settling,
 * and never interacts with external/system windows or guarded real-device actions.
 */
public final class CortexRobotUserTest {
    private CortexRobotUserTest(){}
    private static final int MAX_STEPS=700,MAX_SCREENS=240;
    private static final long MAX_RUNTIME_MS=15L*60L*1000L;
    private static final long FIRST_CHANGE_BUDGET_MS=1800L,ASYNC_SETTLE_BUDGET_MS=3000L;

    public interface Progress {void onStep(Step step);}

    public static final class Step {
        public int seq;public String path="",screenBefore="",action="",actionClass="",status="",screenAfter="",beforeText="",afterText="",detail="",error="";
        public long durationMs,clickDispatchMs,firstVisualChangeMs,windowTransitionMs,asyncSettleMs;
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("seq",seq);o.put("path",path);o.put("screen_before",screenBefore);o.put("action",action);o.put("action_class",actionClass);o.put("status",status);o.put("screen_after",screenAfter);o.put("before_text",beforeText);o.put("after_text",afterText);o.put("detail",detail);o.put("duration_ms",durationMs);o.put("click_dispatch_ms",clickDispatchMs);o.put("first_visual_change_ms",firstVisualChangeMs);o.put("window_transition_ms",windowTransitionMs);o.put("async_settle_ms",asyncSettleMs);o.put("error",error);}catch(Exception ignored){}return o;}
    }

    public static final class Report {
        public long startedAt=System.currentTimeMillis(),finishedAt;public boolean complete=true,accessibilityUsed;public int pressed,guarded,failed,external,noChange,screenCount,statusSkipped,scrolls;public String stopReason="";public final ArrayList<Step> steps=new ArrayList<>();
        public JSONObject json(){JSONObject root=new JSONObject();try{root.put("schema_version",2);root.put("suite","CORTEX_ROBOT_USER_TEST");root.put("started_at",startedAt);root.put("finished_at",finishedAt);root.put("duration_ms",Math.max(0,finishedAt-startedAt));root.put("complete",complete);root.put("stop_reason",stopReason);root.put("accessibility_used",accessibilityUsed);root.put("screen_count",screenCount);root.put("summary",new JSONObject().put("steps",steps.size()).put("pressed",pressed).put("guarded",guarded).put("failed",failed).put("external",external).put("no_change",noChange).put("status_skipped",statusSkipped).put("scrolls",scrolls));JSONArray a=new JSONArray();for(Step s:steps)a.put(s.json());root.put("steps",a);}catch(Exception ignored){}return root;}
        public String markdown(){StringBuilder b=new StringBuilder();b.append("# Cortex Robot User Test\n\n").append("- Complete: **").append(complete?"YES":"NO").append("**\n").append("- Duration: ").append(Math.max(0,finishedAt-startedAt)).append(" ms\n").append("- Screens discovered: ").append(screenCount).append("\n").append("- Steps: ").append(steps.size()).append(" · pressed ").append(pressed).append(" · guarded ").append(guarded).append(" · failed ").append(failed).append(" · external ").append(external).append(" · no-change ").append(noChange).append("\n").append("- Passive status controls skipped: ").append(statusSkipped).append(" · scroll explorations ").append(scrolls).append("\n").append("- Accessibility crawler: ").append(accessibilityUsed?"YES":"NO — fallback View tree only").append("\n");if(!stopReason.isEmpty())b.append("- Stop reason: ").append(stopReason).append("\n");b.append("\n## Failures / guarded actions first\n\n");boolean any=false;for(Step s:steps)if("FAILED".equals(s.status)||s.status.startsWith("GUARDED")){any=true;append(b,s);}if(!any)b.append("None.\n\n");b.append("## Full user journey trace\n\n");for(Step s:steps)append(b,s);return b.toString();}
        private static void append(StringBuilder b,Step s){b.append("### ").append(s.seq).append(" · ").append(s.status).append(" · ").append(s.action).append("\n\n- Path: `").append(s.path.replace("`","'")).append("`\n- Before: ").append(s.screenBefore).append("\n- After: ").append(s.screenAfter).append("\n- Timing: dispatch ").append(s.clickDispatchMs).append(" ms · first visual ").append(s.firstVisualChangeMs).append(" ms · window ").append(s.windowTransitionMs).append(" ms · async settle ").append(s.asyncSettleMs).append(" ms\n");if(!s.detail.isEmpty())b.append("- Result: ").append(s.detail.replace("\n"," ")).append("\n");if(!s.error.isEmpty())b.append("- Error: `").append(s.error.replace("`","'")).append("`\n");if(!s.afterText.isEmpty())b.append("- Visible result: ").append(clip(s.afterText,360).replace("\n"," · ")).append("\n");b.append('\n');}
    }

    private static final class Tracker implements Application.ActivityLifecycleCallbacks {
        volatile Activity resumed;
        @Override public void onActivityResumed(Activity a){resumed=a;}@Override public void onActivityCreated(Activity a,Bundle b){}@Override public void onActivityStarted(Activity a){}@Override public void onActivityPaused(Activity a){}@Override public void onActivityStopped(Activity a){}@Override public void onActivitySaveInstanceState(Activity a,Bundle b){}@Override public void onActivityDestroyed(Activity a){if(resumed==a)resumed=null;}
    }
    private static final class Frame {String sig,label;Frame(String s,String l){sig=s;label=l;}}
    private static final class Snap {String pkg="",screen="",text="";Activity activity;}
    private static final class Target {String path="",label="",cls="",pkg="";View view;boolean accessibility;String logicalKey(String screen){String l=normalizeLabel(label);return screenBase(screen)+"|"+(l.isEmpty()?"path:"+path:l)+"|"+simple(cls);}}
    private static final class Consequence {Snap snap;long firstVisualMs,windowMs,settleMs;}

    public static Report run(Activity origin,Progress progress){
        Report r=new Report();Application app=origin.getApplication();Tracker tracker=new Tracker();app.registerActivityLifecycleCallbacks(tracker);tracker.resumed=origin;
        Set<String> explored=new HashSet<>(),filled=new HashSet<>(),screens=new LinkedHashSet<>(),scrollTried=new HashSet<>();ArrayDeque<Frame> stack=new ArrayDeque<>();
        try{
            CortexRobotFixtures.prepare(app);r.accessibilityUsed=CortexScreenAccessibilityService.connected();ui(origin,()->origin.startActivity(new Intent(origin,InputActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));waitUntil(()->tracker.resumed!=null&&tracker.resumed instanceof InputActivity,5000);
            long deadline=SystemClock.elapsedRealtime()+MAX_RUNTIME_MS;
            while(r.steps.size()<MAX_STEPS&&screens.size()<MAX_SCREENS&&SystemClock.elapsedRealtime()<deadline){
                Snap snap=snapshot(tracker.resumed,app);if(snap==null){sleep(120);continue;}
                if(!app.getPackageName().equals(snap.pkg)&&!snap.pkg.isEmpty()){Step ex=makeStep(r,joinPath(stack,"[external window]"),snap.screen,"External window",snap.pkg,"EXTERNAL",snap.screen,"",snap.text,"Opened non-Cortex window; no controls pressed there","");r.external++;emit(progress,ex);back(tracker,origin);sleep(350);continue;}
                autofill(snap,filled);sleep(60);snap=snapshot(tracker.resumed,app);List<Target> actions=targets(snap,app,r);String sig=signature(snap,actions);screens.add(sig);r.screenCount=screens.size();updateStack(stack,sig,snap.screen);

                Target chosen=null;for(Target t:actions)if(!explored.contains(t.logicalKey(snap.screen))){chosen=t;break;}
                if(chosen==null){
                    String scrollKey=screenBase(snap.screen)+"|"+Fingerprint.text(stableText(snap.text));
                    if(CortexScreenAccessibilityService.connected()&&!scrollTried.contains(scrollKey)){scrollTried.add(scrollKey);long t0=SystemClock.elapsedRealtime();if(CortexScreenAccessibilityService.robotScrollForward()){r.scrolls++;sleep(420);Snap afterScroll=snapshot(tracker.resumed,app);if(afterScroll!=null&&!normalize(afterScroll.text).equals(normalize(snap.text)))continue;}long ignored=SystemClock.elapsedRealtime()-t0;}
                    if(stack.size()<=1)break;stack.removeLast();back(tracker,origin);sleep(350);continue;
                }

                explored.add(chosen.logicalKey(snap.screen));String label=display(chosen),path=joinPath(stack,label),beforeScreen=snap.screen,beforeText=snap.text;
                if(CortexExperimentalTestMode.guardedLabel(label)){Step s=makeStep(r,path,beforeScreen,label,chosen.cls,privacyGuardLabel(label),beforeScreen,beforeText,beforeText,"Visited but intentionally intercepted to protect real device/user state","");r.guarded++;emit(progress,s);continue;}

                long t0=SystemClock.elapsedRealtime();boolean clicked=false;String err="";try{clicked=click(chosen,origin);}catch(Throwable e){err=e.getClass().getSimpleName()+": "+safe(e.getMessage());}long dispatch=SystemClock.elapsedRealtime()-t0;
                if(!clicked){Step s=makeStep(r,path,beforeScreen,label,chosen.cls,"FAILED",beforeScreen,beforeText,beforeText,"Click action returned false",err);s.clickDispatchMs=dispatch;s.durationMs=dispatch;r.failed++;emit(progress,s);continue;}

                Consequence c=observeConsequence(tracker,app,beforeScreen,beforeText,t0);Snap after=c.snap==null?snap:c.snap;String status="PRESSED",detail="Control executed";
                if(!app.getPackageName().equals(after.pkg)&&!after.pkg.isEmpty()){status="PRESSED_EXTERNAL";detail="Opened external/system surface; robot did not interact with that surface";r.external++;}
                else if(normalize(beforeText).equals(normalize(after.text))&&beforeScreen.equals(after.screen)){status="PRESSED_NO_CHANGE";detail="Click completed with no visible state change";r.noChange++;}
                else r.pressed++;
                Step s=makeStep(r,path,beforeScreen,label,chosen.cls,status,after.screen,beforeText,after.text,detail,err);s.clickDispatchMs=dispatch;s.firstVisualChangeMs=c.firstVisualMs;s.windowTransitionMs=c.windowMs;s.asyncSettleMs=c.settleMs;s.durationMs=Math.max(dispatch,c.firstVisualMs);emit(progress,s);
                if("PRESSED_EXTERNAL".equals(status)){back(tracker,origin);sleep(350);continue;}
                List<Target> afterActions=targets(after,app,r);String afterSig=signature(after,afterActions);if(!afterSig.equals(sig)){int idx=findFrame(stack,afterSig);if(idx>=0)trimStack(stack,idx);else stack.addLast(new Frame(afterSig,label));}
            }
            if(r.steps.size()>=MAX_STEPS){r.complete=false;r.stopReason="Maximum step budget reached";}else if(screens.size()>=MAX_SCREENS){r.complete=false;r.stopReason="Maximum unique-screen budget reached";}else if(SystemClock.elapsedRealtime()>=deadline){r.complete=false;r.stopReason="Maximum runtime reached";}
        }catch(Throwable e){r.complete=false;r.stopReason="Harness failure: "+e.getClass().getSimpleName()+": "+safe(e.getMessage());Step s=makeStep(r,"harness","Robot harness","Harness exception","","FAILED","","","","",r.stopReason);r.failed++;emit(progress,s);}
        finally{returnToOrigin(tracker,origin);CortexRobotFixtures.cleanup(app);app.unregisterActivityLifecycleCallbacks(tracker);r.finishedAt=System.currentTimeMillis();}
        return r;
    }

    private static Consequence observeConsequence(Tracker tracker,Context app,String beforeScreen,String beforeText,long clickAt){
        Consequence out=new Consequence();long end=SystemClock.elapsedRealtime()+FIRST_CHANGE_BUDGET_MS;Snap latest=null;String before=normalize(beforeText);
        while(SystemClock.elapsedRealtime()<end){sleep(80);latest=snapshot(tracker.resumed,app);if(latest==null)continue;long elapsed=SystemClock.elapsedRealtime()-clickAt;boolean window=!beforeScreen.equals(latest.screen)||(!latest.pkg.isEmpty()&&!app.getPackageName().equals(latest.pkg));boolean visual=window||!before.equals(normalize(latest.text));if(window&&out.windowMs==0)out.windowMs=elapsed;if(visual){out.firstVisualMs=elapsed;break;}}
        if(latest==null)latest=snapshot(tracker.resumed,app);out.snap=latest;long settleStart=SystemClock.elapsedRealtime();String last=latest==null?"":normalize(latest.text);int stable=0;long settleEnd=settleStart+ASYNC_SETTLE_BUDGET_MS;
        while(SystemClock.elapsedRealtime()<settleEnd){sleep(180);Snap now=snapshot(tracker.resumed,app);if(now==null)continue;String text=normalize(now.text);if(text.equals(last))stable++;else{stable=0;last=text;out.snap=now;}if(stable>=2)break;}
        out.settleMs=SystemClock.elapsedRealtime()-settleStart;return out;
    }

    private static void updateStack(ArrayDeque<Frame> stack,String sig,String screen){if(stack.isEmpty())stack.addLast(new Frame(sig,"Input"));else if(!stack.getLast().sig.equals(sig)){int idx=findFrame(stack,sig);if(idx>=0)trimStack(stack,idx);else stack.addLast(new Frame(sig,screenBase(screen)));}}
    private static void autofill(Snap snap,Set<String> filled){if(snap==null||snap.pkg.isEmpty()||!CortexScreenAccessibilityService.connected())return;for(CortexScreenAccessibilityService.RobotNode n:CortexScreenAccessibilityService.robotEditableNodes()){String k=screenBase(snap.screen)+"|"+n.path;if(filled.contains(k))continue;String value=fixtureFor(n.label);if(CortexScreenAccessibilityService.robotSetText(n.path,value))filled.add(k);}}
    private static String fixtureFor(String label){String s=safe(label).toLowerCase(Locale.ROOT);if(s.contains("email"))return"robot@example.com";if(s.contains("phone")||s.contains("number"))return"01000000000";if(s.contains("search"))return"Cortex robot fixture";if(s.contains("transcript"))return"Finally now هنجرب Cortex transcription with English وعربي مع بعض.";if(s.contains("ask")||s.contains("question")||s.contains("prompt"))return"لخص النتيجة التجريبية في سطر واحد";return"Cortex robot test fixture";}

    private static Snap snapshot(Activity a,Context app){Snap s=new Snap();s.activity=a;CortexScreenAccessibilityService.Snapshot x=CortexScreenAccessibilityService.snapshot();if(x!=null){s.pkg=x.packageName;s.screen=(a==null?x.appLabel:a.getClass().getSimpleName())+" · "+x.appLabel;s.text=clip(x.text,5000);return s;}if(a==null)return null;s.pkg=app.getPackageName();s.screen=a.getClass().getSimpleName();s.text=clip(viewText(a.getWindow().getDecorView()),5000);return s;}
    private static List<Target> targets(Snap s,Context app,Report report){ArrayList<Target> out=new ArrayList<>();if(CortexScreenAccessibilityService.connected()){for(CortexScreenAccessibilityService.RobotNode n:CortexScreenAccessibilityService.robotClickableNodes()){if(!app.getPackageName().equals(n.packageName)||!n.enabled)continue;if(isStatusOnly(n.label,n.className)){report.statusSkipped++;continue;}Target t=new Target();t.path=n.path;t.label=n.label;t.cls=n.className;t.pkg=n.packageName;t.accessibility=true;out.add(t);}if(!out.isEmpty())return out;}if(s!=null&&s.activity!=null)collectViews(s.activity.getWindow().getDecorView(),"",out,report);return out;}
    private static void collectViews(View v,String path,List<Target> out,Report report){if(v==null||out.size()>=180)return;if(v.isShown()&&v.isEnabled()&&v.isClickable()){String lab=label(v);if(isStatusOnly(lab,v.getClass().getName()))report.statusSkipped++;else{Target t=new Target();t.path=path;t.label=lab;t.cls=v.getClass().getName();t.view=v;t.accessibility=false;out.add(t);}}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount()&&out.size()<180;i++)collectViews(g.getChildAt(i),path.isEmpty()?String.valueOf(i):path+"/"+i,out,report);}}
    private static boolean isStatusOnly(String label,String cls){String x=normalizeLabel(label);if(x.isEmpty())return false;String c=safe(cls).toLowerCase(Locale.ROOT);if(!(c.contains("textview")||c.contains("view")))return false;return x.matches(".*\\b\\d{1,3}%.*")||x.equals("improving")||x.startsWith("improving ")||x.startsWith("generating ")||x.startsWith("thinking")||x.startsWith("loading")||x.startsWith("processing")||x.startsWith("combining sources")||x.startsWith("understanding request")||x.startsWith("local model refining")||x.startsWith("preparing")||x.startsWith("جاري")||x.startsWith("بيفكر");}
    private static boolean click(Target t,Activity origin)throws Exception{if(t.accessibility)return CortexScreenAccessibilityService.robotClick(t.path);final boolean[] ok={false};ui(origin,()->{try{ok[0]=t.view!=null&&t.view.performClick();}catch(Throwable ignored){}});return ok[0];}
    private static void back(Tracker tracker,Activity origin){if(CortexScreenAccessibilityService.connected()&&CortexScreenAccessibilityService.robotBack())return;Activity a=tracker.resumed;if(a!=null)ui(origin,()->{try{a.onBackPressed();}catch(Throwable ignored){}});}
    private static void returnToOrigin(Tracker tracker,Activity origin){for(int i=0;i<14&&tracker.resumed!=origin;i++){back(tracker,origin);sleep(150);}if(tracker.resumed!=origin)ui(origin,()->{try{origin.startActivity(new Intent(origin,EnvironmentActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(Throwable ignored){}});sleep(220);}

    private static String signature(Snap s,List<Target> a){StringBuilder b=new StringBuilder(s==null?"":s.pkg+"|"+screenBase(s.screen)+"|"+stableText(s.text));for(Target t:a)b.append('|').append(t.logicalKey(s.screen));return Fingerprint.text(b.toString());}
    private static String stableText(String text){StringBuilder b=new StringBuilder();for(String line:safe(text).split("\\n")){String x=line.trim();if(x.isEmpty()||isTransientLine(x))continue;if(b.length()>0)b.append('|');b.append(normalize(x));if(b.length()>1400)break;}return b.toString();}
    private static boolean isTransientLine(String x){String n=normalizeLabel(x);return n.matches(".*\\b\\d{1,3}%.*")||n.startsWith("generating ")||n.startsWith("improving")||n.startsWith("thinking")||n.startsWith("processing")||n.startsWith("loading")||n.startsWith("combining sources")||n.startsWith("local model refining")||n.startsWith("understanding request");}
    private static String normalize(String x){return safe(x).replaceAll("\\d+%","#%").replaceAll("\\b\\d{1,2}:\\d{2}\\b","#:#").replaceAll("\\b\\d{5,}\\b","#").replaceAll("\\s+"," ").trim();}
    private static String normalizeLabel(String x){return normalize(x).toLowerCase(Locale.ROOT).replace('…',' ').trim();}
    private static String screenBase(String s){String x=safe(s);int i=x.indexOf(" · ");return i>0?x.substring(0,i):x;}
    private static int findFrame(ArrayDeque<Frame> stack,String sig){int i=0;for(Frame f:stack){if(f.sig.equals(sig))return i;i++;}return-1;}
    private static void trimStack(ArrayDeque<Frame> stack,int idx){while(stack.size()>idx+1)stack.removeLast();}
    private static String joinPath(ArrayDeque<Frame> stack,String action){ArrayList<String> xs=new ArrayList<>();for(Frame f:stack)if(!f.label.isEmpty())xs.add(f.label);if(action!=null&&!action.isEmpty())xs.add(action);return android.text.TextUtils.join(" > ",xs);}
    private static String display(Target t){String x=safe(t.label);return x.isEmpty()?"["+simple(t.cls)+" @ "+t.path+"]":clip(x,160);}
    private static String simple(String c){String x=safe(c);int i=x.lastIndexOf('.');return i>=0?x.substring(i+1):x;}
    private static String privacyGuardLabel(String label){String s=safe(label).toLowerCase(Locale.ROOT);return(s.contains("sync")||s.contains("import")||s.contains("record")||s.contains("contact")||s.contains("calendar")||s.contains("health"))?"GUARDED_PRIVACY":"GUARDED";}
    private static String label(View v){CharSequence d=v.getContentDescription();if(v instanceof TextView){CharSequence t=((TextView)v).getText();if(t!=null&&!t.toString().trim().isEmpty())return t.toString().trim();}return d==null?"":d.toString().trim();}
    private static String viewText(View v){LinkedHashSet<String> lines=new LinkedHashSet<>();collectText(v,lines);StringBuilder b=new StringBuilder();for(String x:lines){if(b.length()>0)b.append('\n');b.append(x);if(b.length()>5000)break;}return b.toString();}
    private static void collectText(View v,Set<String> out){if(v==null||!v.isShown())return;if(v instanceof TextView){String x=safe(((TextView)v).getText()==null?"":((TextView)v).getText().toString()).trim();if(!x.isEmpty())out.add(clip(x,500));}CharSequence d=v.getContentDescription();if(d!=null&&!d.toString().trim().isEmpty())out.add(clip(d.toString().trim(),500));if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectText(g.getChildAt(i),out);}}

    private static Step makeStep(Report r,String path,String before,String action,String cls,String status,String after,String beforeText,String afterText,String detail,String error){Step s=new Step();s.seq=r.steps.size()+1;s.path=path;s.screenBefore=before;s.action=action;s.actionClass=cls;s.status=status;s.screenAfter=after;s.beforeText=clip(beforeText,900);s.afterText=clip(afterText,900);s.detail=detail;s.error=error;r.steps.add(s);return s;}
    private static void emit(Progress p,Step s){if(p!=null)try{p.onStep(s);}catch(Throwable ignored){}}
    private static void ui(Activity a,Runnable r){if(a==null||r==null)return;if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()){r.run();return;}CountDownLatch l=new CountDownLatch(1);a.runOnUiThread(()->{try{r.run();}finally{l.countDown();}});try{l.await(4,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
    private interface Cond{boolean ok();}
    private static boolean waitUntil(Cond c,long ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){if(c.ok())return true;sleep(80);}return c.ok();}
    private static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static String safe(String s){return s==null?"":s;}
    private static String clip(String s,int n){String x=safe(s);return x.length()<=n?x:x.substring(0,n)+"…";}
}
