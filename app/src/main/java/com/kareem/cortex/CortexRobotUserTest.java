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
 * Experimental user-journey crawler. It explores every discovered clickable Cortex control,
 * recursively explores resulting screens/dialogs, and backtracks. Destructive/private/external
 * actions are visited but intercepted by CortexExperimentalTestMode.
 */
public final class CortexRobotUserTest {
    private CortexRobotUserTest(){}
    private static final int MAX_STEPS=700,MAX_SCREENS=240;
    private static final long MAX_RUNTIME_MS=15L*60L*1000L;

    public interface Progress {void onStep(Step step);}

    public static final class Step {
        public int seq;public String path="",screenBefore="",action="",actionClass="",status="",screenAfter="",beforeText="",afterText="",detail="",error="";public long durationMs;
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("seq",seq);o.put("path",path);o.put("screen_before",screenBefore);o.put("action",action);o.put("action_class",actionClass);o.put("status",status);o.put("screen_after",screenAfter);o.put("before_text",beforeText);o.put("after_text",afterText);o.put("detail",detail);o.put("duration_ms",durationMs);o.put("error",error);}catch(Exception ignored){}return o;}
    }

    public static final class Report {
        public long startedAt=System.currentTimeMillis(),finishedAt;public boolean complete=true,accessibilityUsed;public int pressed,guarded,failed,external,noChange,screenCount;public String stopReason="";public final ArrayList<Step> steps=new ArrayList<>();
        public JSONObject json(){JSONObject root=new JSONObject();try{root.put("schema_version",1);root.put("suite","CORTEX_ROBOT_USER_TEST");root.put("started_at",startedAt);root.put("finished_at",finishedAt);root.put("duration_ms",Math.max(0,finishedAt-startedAt));root.put("complete",complete);root.put("stop_reason",stopReason);root.put("accessibility_used",accessibilityUsed);root.put("screen_count",screenCount);root.put("summary",new JSONObject().put("steps",steps.size()).put("pressed",pressed).put("guarded",guarded).put("failed",failed).put("external",external).put("no_change",noChange));JSONArray a=new JSONArray();for(Step s:steps)a.put(s.json());root.put("steps",a);}catch(Exception ignored){}return root;}
        public String markdown(){StringBuilder b=new StringBuilder();b.append("# Cortex Robot User Test\n\n").append("- Complete: **").append(complete?"YES":"NO").append("**\n").append("- Duration: ").append(Math.max(0,finishedAt-startedAt)).append(" ms\n").append("- Screens discovered: ").append(screenCount).append("\n").append("- Steps: ").append(steps.size()).append(" · pressed ").append(pressed).append(" · guarded ").append(guarded).append(" · failed ").append(failed).append(" · external ").append(external).append(" · no-change ").append(noChange).append("\n").append("- Accessibility crawler: ").append(accessibilityUsed?"YES":"NO — fallback View tree only").append("\n");if(!stopReason.isEmpty())b.append("- Stop reason: ").append(stopReason).append("\n");b.append("\n## Failures / guarded actions first\n\n");boolean any=false;for(Step s:steps)if("FAILED".equals(s.status)||s.status.startsWith("GUARDED")){any=true;append(b,s);}if(!any)b.append("None.\n\n");b.append("## Full user journey trace\n\n");for(Step s:steps)append(b,s);return b.toString();}
        private static void append(StringBuilder b,Step s){b.append("### ").append(s.seq).append(" · ").append(s.status).append(" · ").append(s.action).append("\n\n- Path: `").append(s.path.replace("`","'")).append("`\n- Before: ").append(s.screenBefore).append("\n- After: ").append(s.screenAfter).append("\n- Duration: ").append(s.durationMs).append(" ms\n");if(!s.detail.isEmpty())b.append("- Result: ").append(s.detail.replace("\n"," ")).append("\n");if(!s.error.isEmpty())b.append("- Error: `").append(s.error.replace("`","'")).append("`\n");if(!s.afterText.isEmpty())b.append("- Visible result: ").append(clip(s.afterText,360).replace("\n"," · ")).append("\n");b.append('\n');}
    }

    private static final class Tracker implements Application.ActivityLifecycleCallbacks {
        volatile Activity resumed;
        @Override public void onActivityResumed(Activity a){resumed=a;}@Override public void onActivityCreated(Activity a,Bundle b){}@Override public void onActivityStarted(Activity a){}@Override public void onActivityPaused(Activity a){}@Override public void onActivityStopped(Activity a){}@Override public void onActivitySaveInstanceState(Activity a,Bundle b){}@Override public void onActivityDestroyed(Activity a){if(resumed==a)resumed=null;}
    }
    private static final class Frame {String sig,label;Frame(String s,String l){sig=s;label=l;}}
    private static final class Snap {String pkg="",screen="",text="";Activity activity;}
    private static final class Target {String path="",label="",cls="",pkg="";View view;boolean accessibility;String key(){return path+"|"+label+"|"+cls;}}

    public static Report run(Activity origin,Progress progress){
        Report r=new Report();Application app=origin.getApplication();Tracker tracker=new Tracker();app.registerActivityLifecycleCallbacks(tracker);tracker.resumed=origin;Set<String> explored=new HashSet<>(),filled=new HashSet<>(),screens=new LinkedHashSet<>();ArrayDeque<Frame> stack=new ArrayDeque<>();
        try{
            CortexRobotFixtures.prepare(app);r.accessibilityUsed=CortexScreenAccessibilityService.connected();
            ui(origin,()->origin.startActivity(new Intent(origin,InputActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
            waitUntil(()->tracker.resumed!=null&&tracker.resumed instanceof InputActivity,5000);
            long deadline=SystemClock.elapsedRealtime()+MAX_RUNTIME_MS;
            while(r.steps.size()<MAX_STEPS&&screens.size()<MAX_SCREENS&&SystemClock.elapsedRealtime()<deadline){
                Snap snap=snapshot(tracker.resumed,app);if(snap==null){sleep(250);continue;}
                if(!app.getPackageName().equals(snap.pkg)&&!snap.pkg.isEmpty()){
                    Step ex=step(r,joinPath(stack,"[external window]"),snap.screen,"External window",snap.pkg,"EXTERNAL",snap.screen,"",snap.text,0,"Opened non-Cortex window; no controls pressed there","");r.external++;emit(progress,ex);back(tracker,origin);sleep(500);continue;
                }
                autofill(snap,filled);sleep(80);snap=snapshot(tracker.resumed,app);List<Target> actions=targets(snap,app);String sig=signature(snap,actions);screens.add(sig);r.screenCount=screens.size();
                if(stack.isEmpty())stack.addLast(new Frame(sig,"Input"));else if(!stack.getLast().sig.equals(sig)){int idx=findFrame(stack,sig);if(idx>=0)trimStack(stack,idx);else stack.addLast(new Frame(sig,snap.screen));}
                Target chosen=null;String chosenKey="";for(Target t:actions){String k=sig+"|"+t.key();if(!explored.contains(k)){chosen=t;chosenKey=k;break;}}
                if(chosen==null){if(stack.size()<=1)break;stack.removeLast();back(tracker,origin);sleep(450);continue;}
                explored.add(chosenKey);String label=display(chosen);String path=joinPath(stack,label);String beforeScreen=snap.screen,beforeText=snap.text;long t0=SystemClock.elapsedRealtime();
                if(CortexExperimentalTestMode.guardedLabel(label)){
                    Step s=step(r,path,beforeScreen,label,chosen.cls,privacyGuardLabel(label),beforeScreen,beforeText,beforeText,SystemClock.elapsedRealtime()-t0,"Visited but intentionally intercepted to protect real device/user state","");r.guarded++;emit(progress,s);continue;
                }
                boolean clicked=false;String err="";try{clicked=click(chosen,origin);}catch(Throwable e){err=e.getClass().getSimpleName()+": "+safe(e.getMessage());}
                if(!clicked){Step s=step(r,path,beforeScreen,label,chosen.cls,"FAILED",beforeScreen,beforeText,beforeText,SystemClock.elapsedRealtime()-t0,"Click action returned false",err);r.failed++;emit(progress,s);continue;}
                waitForSettle(app,beforeText,25000);Snap after=snapshot(tracker.resumed,app);if(after==null)after=snap;String status="PRESSED";String detail="Control executed";
                if(!app.getPackageName().equals(after.pkg)&&!after.pkg.isEmpty()){status="PRESSED_EXTERNAL";detail="Opened external/system surface; robot did not interact with that surface";r.external++;}
                else if(normalize(beforeText).equals(normalize(after.text))&&beforeScreen.equals(after.screen)){status="PRESSED_NO_CHANGE";detail="Click completed with no visible state change";r.noChange++;}
                else r.pressed++;
                Step s=step(r,path,beforeScreen,label,chosen.cls,status,after.screen,beforeText,after.text,SystemClock.elapsedRealtime()-t0,detail,err);emit(progress,s);
                if("PRESSED_EXTERNAL".equals(status)){back(tracker,origin);sleep(500);continue;}
                List<Target> afterActions=targets(after,app);String afterSig=signature(after,afterActions);if(!afterSig.equals(sig)){int idx=findFrame(stack,afterSig);if(idx>=0)trimStack(stack,idx);else stack.addLast(new Frame(afterSig,label));}
            }
            if(r.steps.size()>=MAX_STEPS){r.complete=false;r.stopReason="Maximum step budget reached";}else if(screens.size()>=MAX_SCREENS){r.complete=false;r.stopReason="Maximum unique-screen budget reached";}else if(SystemClock.elapsedRealtime()>=deadline){r.complete=false;r.stopReason="Maximum runtime reached";}
        }catch(Throwable e){r.complete=false;r.stopReason="Harness failure: "+e.getClass().getSimpleName()+": "+safe(e.getMessage());Step s=step(r,"harness","Robot harness","Harness exception","","FAILED","", "","",0,"",r.stopReason);r.failed++;emit(progress,s);}
        finally{
            returnToOrigin(tracker,origin);CortexRobotFixtures.cleanup(app);app.unregisterActivityLifecycleCallbacks(tracker);r.finishedAt=System.currentTimeMillis();
        }
        return r;
    }

    private static void autofill(Snap snap,Set<String> filled){if(snap==null||snap.pkg.isEmpty())return;if(CortexScreenAccessibilityService.connected()){for(CortexScreenAccessibilityService.RobotNode n:CortexScreenAccessibilityService.robotEditableNodes()){String k=snap.screen+"|"+n.path;if(filled.contains(k))continue;String value=fixtureFor(n.label);if(CortexScreenAccessibilityService.robotSetText(n.path,value))filled.add(k);}}}
    private static String fixtureFor(String label){String s=safe(label).toLowerCase(Locale.ROOT);if(s.contains("email"))return"robot@example.com";if(s.contains("phone")||s.contains("number"))return"01000000000";if(s.contains("search"))return"Cortex robot fixture";if(s.contains("transcript"))return"Finally now هنجرب Cortex transcription with English وعربي مع بعض.";if(s.contains("ask")||s.contains("question")||s.contains("prompt"))return"لخص النتيجة التجريبية في سطر واحد";return"Cortex robot test fixture";}

    private static Snap snapshot(Activity a,Context app){Snap s=new Snap();s.activity=a;CortexScreenAccessibilityService.Snapshot x=CortexScreenAccessibilityService.snapshot();if(x!=null){s.pkg=x.packageName;s.screen=(a==null?x.appLabel:a.getClass().getSimpleName())+" · "+x.appLabel;s.text=clip(x.text,5000);return s;}if(a==null)return null;s.pkg=app.getPackageName();s.screen=a.getClass().getSimpleName();s.text=clip(viewText(a.getWindow().getDecorView()),5000);return s;}
    private static List<Target> targets(Snap s,Context app){ArrayList<Target> out=new ArrayList<>();if(CortexScreenAccessibilityService.connected()){for(CortexScreenAccessibilityService.RobotNode n:CortexScreenAccessibilityService.robotClickableNodes()){if(!app.getPackageName().equals(n.packageName))continue;Target t=new Target();t.path=n.path;t.label=n.label;t.cls=n.className;t.pkg=n.packageName;t.accessibility=true;out.add(t);}if(!out.isEmpty())return out;}if(s!=null&&s.activity!=null)collectViews(s.activity.getWindow().getDecorView(),"",out);return out;}
    private static void collectViews(View v,String path,List<Target> out){if(v==null||out.size()>=180)return;if(v.isShown()&&v.isEnabled()&&v.isClickable()){Target t=new Target();t.path=path;t.label=label(v);t.cls=v.getClass().getName();t.view=v;t.accessibility=false;out.add(t);}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount()&&out.size()<180;i++)collectViews(g.getChildAt(i),path.isEmpty()?String.valueOf(i):path+"/"+i,out);}}
    private static boolean click(Target t,Activity origin)throws Exception{if(t.accessibility)return CortexScreenAccessibilityService.robotClick(t.path);final boolean[] ok={false};ui(origin,()->{try{ok[0]=t.view!=null&&t.view.performClick();}catch(Throwable ignored){}});return ok[0];}
    private static void back(Tracker tracker,Activity origin){if(CortexScreenAccessibilityService.connected()&&CortexScreenAccessibilityService.robotBack())return;Activity a=tracker.resumed;if(a!=null)ui(origin,()->{try{a.onBackPressed();}catch(Throwable ignored){}});}
    private static void returnToOrigin(Tracker tracker,Activity origin){for(int i=0;i<14&&tracker.resumed!=origin;i++){back(tracker,origin);sleep(180);}if(tracker.resumed!=origin)ui(origin,()->{try{origin.startActivity(new Intent(origin,EnvironmentActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(Throwable ignored){}});sleep(250);}

    private static void waitForSettle(Context app,String before,long maxMs){long end=SystemClock.elapsedRealtime()+maxMs;String last="";int stable=0;while(SystemClock.elapsedRealtime()<end){sleep(250);CortexScreenAccessibilityService.Snapshot x=CortexScreenAccessibilityService.snapshot();String now=x==null?"":normalize(x.text);if(now.equals(last))stable++;else stable=0;last=now;boolean transientState=containsTransient(now);if(stable>=2&&!transientState)return;if(!normalize(before).equals(now)&&stable>=1&&!transientState)return;}}
    private static boolean containsTransient(String s){String x=s.toLowerCase(Locale.ROOT);return x.contains("generating")||x.contains("thinking")||x.contains("combining sources")||x.contains("processing")||x.contains("loading")||x.contains("preparing")||x.contains("جاري")||x.contains("بيفكر");}

    private static String signature(Snap s,List<Target> a){StringBuilder b=new StringBuilder(s==null?"":s.pkg+"|"+s.screen+"|"+normalize(s.text));for(Target t:a)b.append('|').append(display(t)).append('@').append(t.cls);return Fingerprint.text(b.toString());}
    private static String normalize(String x){return safe(x).replaceAll("\\d+%","#%").replaceAll("\\b\\d{1,2}:\\d{2}\\b","#:#").replaceAll("\\b\\d{5,}\\b","#").replaceAll("\\s+"," ").trim();}
    private static int findFrame(ArrayDeque<Frame> stack,String sig){int i=0;for(Frame f:stack){if(f.sig.equals(sig))return i;i++;}return-1;}
    private static void trimStack(ArrayDeque<Frame> stack,int idx){while(stack.size()>idx+1)stack.removeLast();}
    private static String joinPath(ArrayDeque<Frame> stack,String action){ArrayList<String> xs=new ArrayList<>();for(Frame f:stack)if(!f.label.isEmpty())xs.add(f.label);if(action!=null&&!action.isEmpty())xs.add(action);return android.text.TextUtils.join(" > ",xs);}
    private static String display(Target t){String x=safe(t.label);return x.isEmpty()?"["+simple(t.cls)+" @ "+t.path+"]":clip(x,160);}
    private static String simple(String c){int i=safe(c).lastIndexOf('.');return i>=0?c.substring(i+1):safe(c);}
    private static String privacyGuardLabel(String label){String s=safe(label).toLowerCase(Locale.ROOT);return(s.contains("sync")||s.contains("import")||s.contains("record")||s.contains("contact")||s.contains("calendar")||s.contains("health"))?"GUARDED_PRIVACY":"GUARDED";}
    private static String label(View v){CharSequence d=v.getContentDescription();if(v instanceof TextView){CharSequence t=((TextView)v).getText();if(t!=null&&!t.toString().trim().isEmpty())return t.toString().trim();}return d==null?"":d.toString().trim();}
    private static String viewText(View v){LinkedHashSet<String> lines=new LinkedHashSet<>();collectText(v,lines);StringBuilder b=new StringBuilder();for(String x:lines){if(b.length()>0)b.append('\n');b.append(x);if(b.length()>5000)break;}return b.toString();}
    private static void collectText(View v,Set<String> out){if(v==null||!v.isShown())return;if(v instanceof TextView){String x=safe(((TextView)v).getText()==null?"":((TextView)v).getText().toString()).trim();if(!x.isEmpty())out.add(clip(x,500));}CharSequence d=v.getContentDescription();if(d!=null&&!d.toString().trim().isEmpty())out.add(clip(d.toString().trim(),500));if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectText(g.getChildAt(i),out);}}

    private static Step step(Report r,String path,String before,String action,String cls,String status,String after,String beforeText,String afterText,long ms,String detail,String error){Step s=new Step();s.seq=r.steps.size()+1;s.path=path;s.screenBefore=before;s.action=action;s.actionClass=cls;s.status=status;s.screenAfter=after;s.beforeText=clip(beforeText,900);s.afterText=clip(afterText,900);s.durationMs=ms;s.detail=detail;s.error=error;r.steps.add(s);return s;}
    private static void emit(Progress p,Step s){if(p!=null)try{p.onStep(s);}catch(Throwable ignored){}}
    private static void ui(Activity a,Runnable r){if(a==null||r==null)return;if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()){r.run();return;}CountDownLatch l=new CountDownLatch(1);a.runOnUiThread(()->{try{r.run();}finally{l.countDown();}});try{l.await(4,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
    private interface Cond{boolean ok();}
    private static boolean waitUntil(Cond c,long ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){if(c.ok())return true;sleep(80);}return c.ok();}
    private static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static String safe(String s){return s==null?"":s;}
    private static String clip(String s,int n){String x=safe(s);return x.length()<=n?x:x.substring(0,n)+"…";}
}
