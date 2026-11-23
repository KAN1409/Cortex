package com.kareem.cortex;
import android.database.sqlite.SQLiteDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Map;
import static org.junit.Assert.*;

/** Feedback reorders NOW within a hard +/-30% bound and fades with time. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class FeedbackRankingTest {
    static final long DAY=24L*60L*60L*1000L, NOW=400L*DAY;
    SQLiteDatabase db;
    @Before public void setup()throws Exception{
        java.lang.reflect.Field f=DiscoveryV3Schema.class.getDeclaredField("ready");f.setAccessible(true);f.setBoolean(null,false);
        db=SQLiteDatabase.create(null);DiscoveryV3Schema.ensure(db);
        insight(1,"PRICE_CHANGE","WORK");insight(2,"PRICE_CHANGE","LIFE");insight(3,"DEADLINE","WORK");
    }
    @After public void close(){db.close();}
    void insight(long id,String family,String domain){
        db.execSQL("INSERT INTO discovery_v3_insights(id,situation_id,issue_key,family,domain,title,what_found,why_matters,why_now,suggested_action,confidence,score,state,evidence_count,last_evidence_at,created_at,updated_at) VALUES(?,?,?,?,?,'t','f','w','n','a',.9,.8,'published',2,1,1,1)",
                new Object[]{id,id,"k"+id,family,domain});
    }
    void feedback(long insight,String family,String event,double weight,long at){
        db.execSQL("INSERT INTO discovery_v3_feedback(insight_id,family,event,weight,created_at) VALUES(?,?,?,?,?)",new Object[]{insight,family,event,weight,at});
    }

    @Test public void multiplierIsBoundedBothWays(){
        assertEquals(1.0,FeedbackRanking.multiplier(0),1e-9);
        assertTrue(FeedbackRanking.multiplier(1e9)<=1.30+1e-9);
        assertTrue(FeedbackRanking.multiplier(-1e9)>=0.70-1e-9);
        assertEquals(1.0,FeedbackRanking.multiplier(Double.NaN),1e-9);
    }
    @Test public void singleWrongTapCannotBuryCategory(){
        double m=FeedbackRanking.multiplier(-1.5);
        assertTrue(m>0.85&&m<0.87);
    }
    @Test public void feedbackFadesWithThirtyDayHalfLife(){
        assertEquals(1.0,FeedbackRanking.faded(1,NOW,NOW),1e-9);
        assertEquals(0.5,FeedbackRanking.faded(1,NOW-30*DAY,NOW),1e-6);
        assertEquals("future clock counts as now",1.0,FeedbackRanking.faded(1,NOW+DAY,NOW),1e-9);
    }
    @Test public void learningIsScopedToFamilyAndDomain(){
        feedback(1,"PRICE_CHANGE","useful",1,NOW);feedback(1,"PRICE_CHANGE","acted",1.4,NOW);
        feedback(3,"DEADLINE","wrong",-1.5,NOW);
        Map<String,Double> m=FeedbackRanking.load(db,NOW);
        assertTrue(m.get("PRICE_CHANGE|WORK")>1);
        assertNull("Life price findings are unaffected by Work feedback",m.get("PRICE_CHANGE|LIFE"));
        assertTrue(m.get("DEADLINE|WORK")<1);
        assertTrue(FeedbackRanking.adjusted(m,"PRICE_CHANGE","WORK",.8)>FeedbackRanking.adjusted(m,"PRICE_CHANGE","LIFE",.8));
    }
    @Test public void feedbackOlderThanHorizonIsIgnored(){
        feedback(1,"PRICE_CHANGE","wrong",-1.5,NOW-181*DAY);
        assertTrue(FeedbackRanking.load(db,NOW).isEmpty());
    }
}
