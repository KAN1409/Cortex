package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative local Stage-E detector: Memory -> grounded Situation candidates.
 *
 * <p>This layer detects explicit actionable shapes only. It does not decide final priority; Deep
 * Brain may later rank/update these Situations. Every emitted Situation is grounded to the Memory
 * that caused it and is idempotent via a deterministic semantic anchor.</p>
 */
public final class CognitiveSituationEngineV4 {
    private static final long LOOKBACK_MS=21L*24L*60L*60L*1000L;
    private static final int MAX_MEMORIES=320;
    private CognitiveSituationEngineV4(){}

    public static Result refresh(VaultDb db){
        if(db==null)throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        long now=System.currentTimeMillis(),cutoff=now-LOOKBACK_MS;
        SQLiteDatabase sql=db.getReadableDatabase();
        Cursor c=sql.rawQuery("SELECT id,kind,COALESCE(title,''),body,COALESCE(source_package,''),started_at,importance FROM v4_memories WHERE state='ACTIVE' AND started_at>=? ORDER BY started_at DESC,id DESC LIMIT ?",new String[]{String.valueOf(cutoff),String.valueOf(MAX_MEMORIES)});
        int scanned=0,detected=0;ArrayList<String> ids=new ArrayList<>();
        try{
            while(c.moveToNext()){
                scanned++;
                String memoryId=c.getString(0),kind=c.getString(1),title=clean(c.getString(2)),body=clean(c.getString(3)),source=clean(c.getString(4));
                long startedAt=c.getLong(5);double importance=c.getDouble(6);
                String text=clean((title+" "+body));if(text.length()<4)continue;
                Candidate x=detect(memoryId,kind,title,body,source,startedAt,importance,text,now);if(x==null)continue;
                String anchor="memory:"+memoryId+":"+x.kind.name().toLowerCase(Locale.ROOT);
                String occurrenceKey=memoryId;
                String identity="situation|"+x.kind.name()+"|"+anchor+"|"+occurrenceKey;
                String id=CognitiveIdentityV4.objectId("si",identity);
                ids.add(id);
                // Never reset an existing Situation to DETECTED. User deferral/dismissal and prior
                // Deep Brain relevance are durable state; immutable Memory does not need re-write.
                if(situationExists(sql,id))continue;
                CognitiveDomainV4.Situation s=new CognitiveDomainV4.Situation(
                        id,x.kind,CognitiveDomainV4.SituationState.DETECTED,x.headline,x.explanation,
                        Collections.<String>emptyList(),Collections.<String>emptyList(),Collections.singletonList(memoryId),Collections.<String>emptyList(),
                        startedAt,x.relevantFrom,x.relevantUntil,now,x.attention,x.interruption,x.confidence,Collections.<String>emptyList());
                CognitiveStoreV4.putSituation(db,s,"",anchor,occurrenceKey);
                detected++;
            }
        }finally{c.close();}
        return new Result(scanned,detected,ids);
    }

    static Candidate detect(String memoryId,String kind,String title,String body,String source,long startedAt,double importance,String text,long now){
        String low=text.toLowerCase(Locale.ROOT);
        if(isNoise(low,source))return null;

        if(containsAny(low,"security alert","security review","sign-in request","sign in request","new sign-in","new sign in","new device logged","logged into your account","access to your google account","if this was not you","reset your password","تسجيل الدخول","تسجيل دخول","تنبيه أمان","تنبيه امن","جهاز جديد","الوصول إلى حساب","الوصول لحساب")){
            return new Candidate(CognitiveDomainV4.SituationKind.RISK,headline(title,body,"Review account security activity"),
                    "Recent memory contains an explicit account-security or sign-in signal.",startedAt,null,baseAttention(.58,importance),.28,.88);
        }

        // Relative language such as "Saturday at 6" is interpreted from when the Memory occurred,
        // not from every later refresh. This avoids sliding an old event into a future week.
        long timeAnchor=startedAt>0?startedAt:now;
        Long eventAt=parseExplicitFutureTime(low,timeAnchor);
        if(eventAt!=null&&(containsAny(low,"reminder","appointment","meeting","scan","scans","hospital","موعد","ميعاد","تذكير","reminder ضروري","مستشفى","اشعة","أشعة","اجتماع","كشف")||hasWeekday(low))){
            return new Candidate(CognitiveDomainV4.SituationKind.UPCOMING_EVENT,headline(title,body,"Upcoming event"),
                    "Memory contains an explicit upcoming event/time signal.",startedAt,eventAt,baseAttention(.52,importance),.20,.86);
        }

        if(containsAny(low,"deadline","due by","due today","due tomorrow","آخر موعد","اخر موعد","قبل الساعة","قبل الساعه")){
            return new Candidate(CognitiveDomainV4.SituationKind.DEADLINE,headline(title,body,"Deadline"),
                    "Memory contains an explicit deadline signal.",startedAt,eventAt,baseAttention(.56,importance),.24,.82);
        }

        if(containsAny(low,"missed call","مكالمة فائتة","مكالمه فائته")){
            return new Candidate(CognitiveDomainV4.SituationKind.FOLLOW_UP,headline(title,body,"Missed call to review"),
                    "A missed call may need follow-up; urgency is not assumed locally.",startedAt,null,baseAttention(.28,importance),.08,.74);
        }

        if(containsAny(low,"waiting for","pending response","awaiting","منتظر رد","في انتظار رد","مستني رد")){
            return new Candidate(CognitiveDomainV4.SituationKind.WAITING,headline(title,body,"Waiting for response"),
                    "Memory explicitly indicates a waiting state.",startedAt,null,baseAttention(.34,importance),.05,.72);
        }

        if(containsAny(low,"i'll send","i will send","i'll do","i will do","هبعت","هعمل","هخلص","لازم أعمل","لازم اعمل","محتاج أعمل","محتاج اعمل")){
            return new Candidate(CognitiveDomainV4.SituationKind.COMMITMENT,headline(title,body,"Open commitment"),
                    "Memory contains an explicit future commitment phrase.",startedAt,eventAt,baseAttention(.42,importance),.10,.70);
        }
        return null;
    }

    private static boolean situationExists(SQLiteDatabase sql,String id){Cursor c=sql.rawQuery("SELECT 1 FROM v4_situations WHERE id=? LIMIT 1",new String[]{id});try{return c.moveToFirst();}finally{c.close();}}
    private static boolean isNoise(String low,String source){
        if(low.isEmpty())return true;
        if(low.contains("% off")||low.contains("massive offers")||low.contains("delivered straight to your door"))return true;
        if("com.talabat".equals(source)&&!containsAny(low,"security","payment","order problem","refund"))return true;
        return false;
    }

    private static String headline(String title,String body,String fallback){String x=clean(title);if(x.isEmpty())x=clean(body);if(x.isEmpty())x=fallback;return clip(x,180);}
    private static double baseAttention(double base,double importance){return clamp01(base+Math.max(0,importance-.5)*.25);}
    private static boolean containsAny(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static boolean hasWeekday(String s){return weekday(s)>=1;}

    /**
     * Parse only when the Memory contains an explicit clock time; avoid inventing dates.
     *
     * <p>Explicit "today/النهارده" is a hard date constraint. When a 1-11 clock hour has no AM/PM,
     * choose the earliest still-future occurrence on that same day (05:00 vs 17:00). If both have
     * passed, keep the latest same-day occurrence so an overdue deadline remains overdue instead of
     * silently sliding into tomorrow. This is what makes "قبل الساعة 5 النهارده" at 14:04 resolve
     * to 17:00 today.</p>
     */
    static Long parseExplicitFutureTime(String low,long now){
        int targetDay=weekday(low);
        Matcher m=Pattern.compile("(?:الساعة|الساعه|at)\\s*(\\d{1,2})(?:[:٫](\\d{2}))?\\s*(ص|صباحا|صباحًا|am|م|مساء|مساءً|pm)?",Pattern.CASE_INSENSITIVE).matcher(low);
        if(!m.find())return null;
        int rawHour=parseInt(m.group(1),-1),minute=parseInt(m.group(2),0);if(rawHour<0||rawHour>23||minute<0||minute>59)return null;
        String ap=m.group(3)==null?"":m.group(3).toLowerCase(Locale.ROOT);
        boolean hasMeridiem=!ap.isEmpty();
        int hour=rawHour;
        if((ap.startsWith("م")||ap.startsWith("مس")||"pm".equals(ap))&&hour<12)hour+=12;
        if((ap.startsWith("ص")||"am".equals(ap))&&hour==12)hour=0;

        boolean explicitToday=containsAny(low,"النهارده","النهاردة","اليوم","today");
        boolean explicitTomorrow=containsAny(low,"بكره","بكرة","غدا","غدًا","tomorrow");
        if(explicitToday&&targetDay<0){
            if(!hasMeridiem&&rawHour>=1&&rawHour<=11)return sameDayAmbiguousClock(now,rawHour,minute);
            return sameDayClock(now,hour,minute);
        }

        Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);
        if(targetDay>0){
            int current=c.get(Calendar.DAY_OF_WEEK),delta=(targetDay-current+7)%7;
            if(delta==0&&c.getTimeInMillis()<=now)delta=7;
            c.add(Calendar.DAY_OF_MONTH,delta);
        }else if(explicitTomorrow){
            c.add(Calendar.DAY_OF_MONTH,1);
        }else if(c.getTimeInMillis()<=now){
            c.add(Calendar.DAY_OF_MONTH,1);
        }
        return c.getTimeInMillis();
    }

    private static long sameDayClock(long now,int hour,int minute){
        Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);return c.getTimeInMillis();
    }

    private static long sameDayAmbiguousClock(long now,int hour,int minute){
        long am=sameDayClock(now,hour,minute),pm=sameDayClock(now,hour+12,minute);
        boolean amFuture=am>=now,pmFuture=pm>=now;
        if(amFuture&&pmFuture)return Math.min(am,pm);
        if(amFuture)return am;
        if(pmFuture)return pm;
        return Math.max(am,pm);
    }

    private static int weekday(String s){if(containsAny(s,"الأحد","الاحد","sunday"))return Calendar.SUNDAY;if(containsAny(s,"الاثنين","الإثنين","monday"))return Calendar.MONDAY;if(containsAny(s,"الثلاثاء","tuesday"))return Calendar.TUESDAY;if(containsAny(s,"الأربعاء","الاربعاء","wednesday"))return Calendar.WEDNESDAY;if(containsAny(s,"الخميس","thursday"))return Calendar.THURSDAY;if(containsAny(s,"الجمعة","الجمعه","friday"))return Calendar.FRIDAY;if(containsAny(s,"السبت","saturday"))return Calendar.SATURDAY;return-1;}
    private static int parseInt(String s,int d){try{return s==null||s.isEmpty()?d:Integer.parseInt(s);}catch(Throwable e){return d;}}
    private static double clamp01(double x){return Math.max(0,Math.min(1,x));}
    private static String clean(String s){return s==null?"":s.replace('\u0000',' ').replaceAll("\\s+"," ").trim();}
    private static String clip(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}

    static final class Candidate{
        final CognitiveDomainV4.SituationKind kind;final String headline,explanation;final Long relevantFrom,relevantUntil;final double attention,interruption,confidence;
        Candidate(CognitiveDomainV4.SituationKind kind,String headline,String explanation,Long relevantFrom,Long relevantUntil,double attention,double interruption,double confidence){this.kind=kind;this.headline=headline;this.explanation=explanation;this.relevantFrom=relevantFrom;this.relevantUntil=relevantUntil;this.attention=attention;this.interruption=interruption;this.confidence=confidence;}
    }
    public static final class Result{public final int memoriesScanned,situationsDetected;public final List<String>situationIds;Result(int scanned,int detected,List<String>ids){memoriesScanned=scanned;situationsDetected=detected;situationIds=Collections.unmodifiableList(new ArrayList<>(ids));}}
}
