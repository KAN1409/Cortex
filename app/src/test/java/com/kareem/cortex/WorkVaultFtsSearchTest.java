package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkVaultFtsSearchTest {
    @Test public void buildsEnglishProcurementPrefixQuery(){
        assertEquals("\"pr\"* AND \"0262\"*",WorkVaultFtsSearch.matchQuery("PR 0262"));
    }

    @Test public void buildsArabicPrefixQuery(){
        assertEquals("\"سقف\"* AND \"جبس\"*",WorkVaultFtsSearch.matchQuery("سقف جبس"));
    }

    @Test public void stripsPunctuationWithoutBreakingMatchSyntax(){
        assertEquals("\"po\"* AND \"1047\"* AND \"lifestyle\"*",WorkVaultFtsSearch.matchQuery("PO-1047 / LifeStyle"));
    }

    @Test public void emptyOrSingleCharacterNoiseReturnsEmpty(){
        assertEquals("",WorkVaultFtsSearch.matchQuery("  / - a  "));
    }
}
