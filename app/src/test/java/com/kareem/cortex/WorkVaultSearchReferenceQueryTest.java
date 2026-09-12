package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkVaultSearchReferenceQueryTest {
    @Test public void parsesPrWithSpace(){
        WorkVaultSearch.RefQuery q=WorkVaultSearch.parseReferenceQuery("PR 0262");
        assertEquals("PR",q.type);assertEquals("0262",q.value);
    }

    @Test public void parsesPoWithDash(){
        WorkVaultSearch.RefQuery q=WorkVaultSearch.parseReferenceQuery("PO-1047");
        assertEquals("PO",q.type);assertEquals("1047",q.value);
    }

    @Test public void parsesJoinedPrWithoutSeparator(){
        WorkVaultSearch.RefQuery q=WorkVaultSearch.parseReferenceQuery("PR0262");
        assertEquals("PR",q.type);assertEquals("0262",q.value);
    }

    @Test public void parsesArabicSurroundingText(){
        WorkVaultSearch.RefQuery q=WorkVaultSearch.parseReferenceQuery("هاتلي حالة PR 0262 دلوقتي");
        assertEquals("PR",q.type);assertEquals("0262",q.value);
    }

    @Test public void prefersLongestDigitBearingTokenWithoutExplicitType(){
        WorkVaultSearch.RefQuery q=WorkVaultSearch.parseReferenceQuery("compare 12 with 0262");
        assertEquals("",q.type);assertEquals("0262",q.value);
    }
}
