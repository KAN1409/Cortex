package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/**
 * Local descriptive trend layer over stored Health metrics.
 *
 * This engine never calls a model, never applies medical thresholds, and never labels a direction
 * as healthy/unhealthy. It compares two recent periods using one source per metric so duplicate
 * vendor/gateway records cannot silently double-count each other.
 */
public final class HealthTrendEngine {
    private static final long DAY=24L*60L*60L*1000L,PERIOD=7L*DAY;
    private static final String[] METRICS={"steps","sleep_duration","resting_heart_rate","heart_rate","oxygen_saturation","weight"};
    private HealthTrendEngine(){}

    public static final class Trend {
        public final String metric,label,unit,sourceKey,direction,detail,dataQuality;
        public final double latest,recentValue,previousValue,deltaPercent;
        public final int recentSamples,previousSamples;
        public final long latestAt;
        Trend(String metric,String label,String unit,String source,String direction,String detail,String quality,double latest,double recent,double previous,double delta,int rn,int pn,long latestAt){this.metric=n(metric);this.label=n(label);this.unit=n(unit);sourceKey=n(source);this.direction=n(direction);this.detail=n(detail);dataQuality=n(quality);this.latest=latest;recentValue=recent;previousValue=previous;deltaPercent=delta;recentSamples=rn;previousSamples=pn;this.latestAt=latestAt;}
        public boolean comparable(){return recentSamples>=minimum(metric)&&previousSamples>=minimum(metric)&&Double.isFinite(recentValue)&&Double.isFinite(previousValue);}
        public int coverage(){return recentSamples+previousSamples;}
    }

    public static final class Report {
        public final long generatedAt;
        public final ArrayList<Trend> trends;
        public final String text;
        Report(long at,ArrayList<Trend> trends,String text){generatedAt=at;this.trends=trends;this.text=n(text);}
        public boolean available(){return !trends.isEmpty();}
    }

    public static Report build(VaultDb db){
        long now=System.currentTimeMillis();ArrayList<Trend> out=new ArrayList<>();if(db==null)return new Report(now,out,"");HealthStore.ensure(db);
        for(String metric:METRICS){Trend t=trend(db,metric,now);if(t!=null)out.add(t);}
        StringBuilder text=new StringBuilder();for(Trend t:out){if(text.length()>0)text.append('\n');text.append("• ").append(t.label).append(": ").append(t.detail).append(" · ").append(sourceLabel(t.sourceKey));}
        return new Report(now,out,text.toString());
    }

    /**
     * Pick exactly one source per metric. Comparable two-period coverage beats a merely newer source;
     * among equally comparable candidates prefer more recorded coverage, then the freshest reading.
     */
    private static Trend trend(VaultDb db,String metric,long now){Trend best=null;for(String source:candidateSources(db,metric,now-2L*PERIOD)){Trend t=sourceTrend(db,metric,source,now);if(t==null)continue;if(best==null||better(t,best))best=t;}return best;}
    private static boolean better(Trend a,Trend b){if(a.comparable()!=b.comparable())return a.comparable();if(a.coverage()!=b.coverage())return a.coverage()>b.coverage();return a.latestAt>b.latestAt;}

    /** Test-only deterministic entry: exact source prevents unrelated real metrics from affecting a rollback fixture. */
    static Trend diagnosticForSource(VaultDb db,String metric,String source,long now){if(db==null||n(metric).isEmpty()||n(source).isEmpty())return null;HealthStore.ensure(db);return sourceTrend(db,n(metric),n(source),now);}

    private static Trend sourceTrend(VaultDb db,String metric,String source,long now){
        ArrayList<Point> points=points(db,metric,source,now-2L*PERIOD,now);if(points.isEmpty())return null;
        Point latest=points.get(points.size()-1);Stats recent=stats(metric,points,now-PERIOD,now),previous=stats(metric,points,now-2L*PERIOD,now-PERIOD);String unit=n(latest.unit),label=label(metric),direction="insufficient data",quality=quality(metric,recent.count,previous.count);double delta=Double.NaN;
        if(recent.count>=minimum(metric)&&previous.count>=minimum(metric)&&Double.isFinite(recent.value)&&Double.isFinite(previous.value)){
            double denom=Math.abs(previous.value);delta=denom<0.000001?Double.NaN:((recent.value-previous.value)/denom)*100.0;direction=direction(recent.value,previous.value,delta);
        }
        String detail=detail(metric,latest,recent,previous,direction,delta,quality);return new Trend(metric,label,unit,source,direction,detail,quality,latest.value,recent.value,previous.value,delta,recent.count,previous.count,latest.at);
    }

    private static ArrayList<String> candidateSources(VaultDb db,String metric,long since){ArrayList<String> out=new ArrayList<>();Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT source_key,MAX(end_at) latest FROM health_metrics WHERE metric_type=? AND end_at>=? GROUP BY source_key ORDER BY latest DESC LIMIT 12",new String[]{metric,String.valueOf(since)});while(c.moveToNext()){String s=n(c.getString(0));if(!s.isEmpty())out.add(s);}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return out;}

    private static ArrayList<Point> points(VaultDb db,String metric,String source,long since,long until){ArrayList<Point> out=new ArrayList<>();Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT value_real,unit,end_at FROM health_metrics WHERE metric_type=? AND source_key=? AND end_at>=? AND end_at<=? ORDER BY end_at ASC,id ASC",new String[]{metric,source,String.valueOf(since),String.valueOf(until)});while(c.moveToNext()){double v=c.getDouble(0);if(Double.isFinite(v))out.add(new Point(v,n(c.getString(1)),c.getLong(2)));}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return out;}

    private static Stats stats(String metric,ArrayList<Point> points,long start,long end){if("steps".equals(metric))return stepStats(points,start,end);ArrayList<Double> values=new ArrayList<>();for(Point p:points)if(p.at>=start&&p.at<end)values.add(p.value);if(values.isEmpty())return new Stats(Double.NaN,0);Collections.sort(values);double value;if("heart_rate".equals(metric)||"oxygen_saturation".equals(metric)){int m=values.size()/2;value=values.size()%2==0?(values.get(m-1)+values.get(m))/2.0:values.get(m);}else{double sum=0;for(double x:values)sum+=x;value=sum/values.size();}return new Stats(value,values.size());}

    /** Steps are interval totals. Sum each calendar-day bucket, then compare average recorded day. */
    private static Stats stepStats(ArrayList<Point> points,long start,long end){LinkedHashMap<Long,Double> days=new LinkedHashMap<>();TimeZone tz=TimeZone.getDefault();for(Point p:points){if(p.at<start||p.at>=end)continue;long key=localDay(p.at,tz);days.put(key,days.containsKey(key)?days.get(key)+p.value:p.value);}if(days.isEmpty())return new Stats(Double.NaN,0);double sum=0;for(double v:days.values())sum+=v;return new Stats(sum/days.size(),days.size());}
    private static long localDay(long at,TimeZone tz){Calendar c=Calendar.getInstance(tz);c.setTimeInMillis(at);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}

    private static String detail(String metric,Point latest,Stats recent,Stats previous,String direction,double delta,String quality){StringBuilder b=new StringBuilder();b.append("latest ").append(fmt(latest.value)).append(unitSuffix(latest.unit));if(recent.count>0&&Double.isFinite(recent.value)){b.append(" · recent 7d ").append(metricValue(metric,recent.value,latest.unit));if(previous.count>0&&Double.isFinite(previous.value))b.append(" vs prior 7d ").append(metricValue(metric,previous.value,latest.unit));}if(!"insufficient data".equals(direction)){b.append(" · ").append(direction);if(Double.isFinite(delta))b.append(" ").append(fmt(Math.abs(delta))).append('%');}else b.append(" · not enough comparable data");b.append(" · ").append(quality);return b.toString();}
    private static String metricValue(String metric,double value,String unit){if("steps".equals(metric))return fmt(value)+" steps/recorded day";return fmt(value)+unitSuffix(unit);}

    /** Similar means less than 3% arithmetic difference; this is not a clinical normal range. */
    private static String direction(double recent,double previous,double delta){if(!Double.isFinite(delta))return recent>previous?"higher":recent<previous?"lower":"similar";if(Math.abs(delta)<3.0)return"similar";return recent>previous?"higher":"lower";}
    private static String quality(String metric,int recent,int previous){int total=recent+previous,min=minimum(metric);if(recent<min||previous<min)return"limited data";if(total>=14)return"stronger coverage";if(total>=8)return"moderate coverage";return"basic coverage";}
    private static int minimum(String metric){return"heart_rate".equals(metric)?3:2;}
    private static String label(String metric){if("steps".equals(metric))return"Steps";if("sleep_duration".equals(metric))return"Sleep duration";if("resting_heart_rate".equals(metric))return"Resting heart rate";if("heart_rate".equals(metric))return"Heart rate";if("oxygen_saturation".equals(metric))return"Oxygen saturation";if("weight".equals(metric))return"Weight";return metric;}
    private static String sourceLabel(String source){if("samsung_health".equals(source))return"Samsung Health via Health Connect";if("huawei_health".equals(source))return"Huawei-origin via Health Connect";if("health_connect".equals(source))return"Health Connect";if(source.startsWith("health_connect:"))return"Health Connect · "+source.substring("health_connect:".length());return source;}
    private static String unitSuffix(String unit){String u=n(unit);return u.isEmpty()?"":" "+u;}
    private static String fmt(double x){if(!Double.isFinite(x))return"—";if(Math.abs(x)>=100)return String.valueOf(Math.round(x));if(Math.rint(x)==x)return String.valueOf((long)x);return String.format(Locale.US,"%.1f",x);}
    private static String n(String s){return s==null?"":s.trim();}
    private static final class Point{final double value;final String unit;final long at;Point(double v,String u,long a){value=v;unit=u;at=a;}}
    private static final class Stats{final double value;final int count;Stats(double v,int c){value=v;count=c;}}
}
