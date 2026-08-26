package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Deterministic rollback-only fixture for the local Health trend arithmetic. */
public final class HealthTrendFixture {
    private static final long DAY=24L*60L*60L*1000L;
    private HealthTrendFixture(){}

    public static String verify(VaultDb db)throws Exception{
        if(db==null)throw new IllegalArgumentException("db required");HealthStore.ensure(db);SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();String source="diagnostic_trend_fixture_"+System.nanoTime();long now=System.currentTimeMillis();
        try{
            // Seven recorded days in each window: recent daily steps are exactly double prior period.
            for(int i=0;i<7;i++){
                long previousAt=now-(13L-i)*DAY-DAY/3;long recentAt=now-(6L-i)*DAY-DAY/3;
                add(db,source,"steps",4000,"steps",previousAt,"psteps"+i);add(db,source,"steps",8000,"steps",recentAt,"rsteps"+i);
            }
            HealthTrendEngine.Trend steps=HealthTrendEngine.diagnosticForSource(db,"steps",source,now);if(steps==null||!steps.comparable())throw new AssertionError("steps trend not comparable");if(steps.previousSamples!=7||steps.recentSamples!=7)throw new AssertionError("steps day buckets "+steps.previousSamples+"/"+steps.recentSamples);if(Math.abs(steps.previousValue-4000)>0.01||Math.abs(steps.recentValue-8000)>0.01)throw new AssertionError("steps averages "+steps.previousValue+"/"+steps.recentValue);if(!"higher".equals(steps.direction))throw new AssertionError("steps direction "+steps.direction);

            // Median guard: extreme high samples must not dominate heart-rate period comparison.
            double[] oldHr={60,61,200},newHr={70,71,300};for(int i=0;i<3;i++){long oldAt=now-(10L-i)*DAY;long newAt=now-(3L-i)*DAY;add(db,source,"heart_rate",oldHr[i],"bpm",oldAt,"phr"+i);add(db,source,"heart_rate",newHr[i],"bpm",newAt,"rhr"+i);}
            HealthTrendEngine.Trend heart=HealthTrendEngine.diagnosticForSource(db,"heart_rate",source,now);if(heart==null||!heart.comparable())throw new AssertionError("heart trend not comparable");if(Math.abs(heart.previousValue-61)>0.01||Math.abs(heart.recentValue-71)>0.01)throw new AssertionError("heart median "+heart.previousValue+"/"+heart.recentValue);if(!"higher".equals(heart.direction))throw new AssertionError("heart direction "+heart.direction);
            return"steps 4000→8000 recorded-day average · heart median 61→71 bpm · exact source · rollback";
        }finally{sql.endTransaction();}
    }

    private static void add(VaultDb db,String source,String metric,double value,String unit,long at,String suffix)throws Exception{long id=HealthStore.addMetric(db,source,metric,value,unit,at-1000,at,source+"_"+suffix,"{\"synthetic\":true,\"rollback\":true}");if(id<=0)throw new AssertionError("fixture insert failed: "+metric+"/"+suffix);}
}
