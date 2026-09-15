package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkPriceComparisonEngineTest {
    private static WorkPriceComparisonEngine.Price price(String item,String unit,double value,String currency,long projectId){
        WorkPriceComparisonEngine.Price p=WorkPriceComparisonEngine.Price.of(item,unit,value,currency);
        p.projectId=projectId;
        return p;
    }

    @Test public void goldenMarbleScenarioCalculatesExactEighteenPercentIncrease(){
        WorkPriceComparisonEngine.Price previous=price("Galala marble supply","m2",1000,"EGP",42);
        WorkPriceComparisonEngine.Price current=price("Galala marble supply","sqm",1180,"LE",42);

        WorkPriceComparisonEngine.Comparison x=WorkPriceComparisonEngine.compare(current,previous);

        assertTrue(x.comparable);
        assertEquals("COMPARABLE",x.reason);
        assertEquals(180.0,x.delta,0.0001);
        assertEquals(18.0,x.percent,0.0001);
        assertEquals("INCREASE",x.direction());
    }

    @Test public void differentProjectIsNeverComparable(){
        WorkPriceComparisonEngine.Comparison x=WorkPriceComparisonEngine.compare(
                price("Galala marble supply","m2",1180,"EGP",20),
                price("Galala marble supply","m2",1000,"EGP",10));
        assertFalse(x.comparable);
        assertEquals("SCOPE_MISMATCH_OR_UNKNOWN",x.reason);
    }

    @Test public void unknownScopeIsRejectedInsteadOfCrossLinkingFiles(){
        WorkPriceComparisonEngine.Price current=price("Galala marble supply","m2",1180,"EGP",0);
        WorkPriceComparisonEngine.Price previous=price("Galala marble supply","m2",1000,"EGP",0);
        assertFalse(WorkPriceComparisonEngine.compare(current,previous).comparable);
    }

    @Test public void matchingTrustedReferenceCanScopeUnknownProject(){
        WorkPriceComparisonEngine.Price current=price("Galala marble supply","m2",1180,"EGP",0);
        WorkPriceComparisonEngine.Price previous=price("Galala marble supply","m2",1000,"EGP",0);
        current.referenceType="PR";current.referenceValue="PR-0262";
        previous.referenceType="PR";previous.referenceValue="PR–٠٢٦٢";
        assertTrue(WorkPriceComparisonEngine.compare(current,previous).comparable);
    }

    @Test public void alphabeticReferenceCannotCreateComparisonScope(){
        WorkPriceComparisonEngine.Price current=price("Galala marble supply","m2",1180,"EGP",0);
        WorkPriceComparisonEngine.Price previous=price("Galala marble supply","m2",1000,"EGP",0);
        current.referenceType="PR";current.referenceValue="PROJECT";
        previous.referenceType="PR";previous.referenceValue="PROJECT";
        assertFalse(WorkPriceComparisonEngine.compare(current,previous).comparable);
    }

    @Test public void incompatibleUnitOrCurrencyIsRejected(){
        WorkPriceComparisonEngine.Price base=price("Galala marble supply","m2",1000,"EGP",7);
        WorkPriceComparisonEngine.Price wrongUnit=price("Galala marble supply","lm",1180,"EGP",7);
        WorkPriceComparisonEngine.Price wrongCurrency=price("Galala marble supply","m2",1180,"USD",7);
        assertEquals("UNIT_MISMATCH",WorkPriceComparisonEngine.compare(wrongUnit,base).reason);
        assertEquals("CURRENCY_MISMATCH",WorkPriceComparisonEngine.compare(wrongCurrency,base).reason);
    }
}
