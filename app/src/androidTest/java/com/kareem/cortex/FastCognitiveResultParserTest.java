package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FastCognitiveResultParserTest {

    @Test
    public void typeOnlyActionUsesGroundedDefaults() throws Exception {
        CognitiveResult result = FastCognitiveResultParser.parse("{\"t\":\"ACTION\"}", "Send file");
        assertEquals(CognitiveDisposition.DERIVE, result.disposition);
        assertEquals(0.82, result.confidence, 0.0001);
        assertEquals(1, result.items.size());
        CognitiveItem item = result.items.get(0);
        assertEquals(CognitiveKind.ACTION, item.kind);
        assertEquals("Send file", item.summary);
        assertTrue(item.requiresUserAction);
        assertFalse(item.requiresFollowUp);
        assertFalse(item.requiresContentExtraction);
        assertEquals("", item.person);
        assertNull(item.dueAt);
        assertEquals("POLICY_DEFAULT", FastCognitiveResultParser.confidenceSource("{\"t\":\"ACTION\"}"));
    }

    @Test
    public void actionMayCarryModelConfidence() throws Exception {
        CognitiveResult result = FastCognitiveResultParser.parse("{\"t\":\"ACTION\",\"c\":93}", "Send file");
        assertEquals(CognitiveDisposition.DERIVE, result.disposition);
        assertEquals(CognitiveKind.ACTION, result.items.get(0).kind);
        assertEquals(0.93, result.confidence, 0.0001);
        assertEquals("MODEL", FastCognitiveResultParser.confidenceSource("{\"t\":\"ACTION\",\"c\":93}"));
    }

    @Test
    public void shortEventAliasParses() throws Exception {
        CognitiveResult result = FastCognitiveResultParser.parse("{\"t\":\"EV\"}", "Dentist tomorrow at 4");
        assertEquals(CognitiveDisposition.DERIVE, result.disposition);
        assertEquals(CognitiveKind.EVENT, result.items.get(0).kind);
        assertEquals("Dentist tomorrow at 4", result.items.get(0).summary);
    }

    @Test
    public void eventTypeParses() throws Exception {
        CognitiveResult result = FastCognitiveResultParser.parse("{\"t\":\"EVENT\"}", "Dentist tomorrow at 4");
        assertEquals(CognitiveDisposition.DERIVE, result.disposition);
        assertEquals(CognitiveKind.EVENT, result.items.get(0).kind);
    }

    @Test
    public void contextTypeParses() throws Exception {
        CognitiveResult result = FastCognitiveResultParser.parse("{\"t\":\"CONTEXT\"}", "Thanks");
        assertEquals(CognitiveDisposition.CONTEXT, result.disposition);
        assertEquals(0.86, result.confidence, 0.0001);
        assertTrue(result.items.isEmpty());
    }

    @Test
    public void ignoreTypeParses() throws Exception {
        CognitiveResult result = FastCognitiveResultParser.parse("{\"t\":\"IGNORE\"}", "Charging 84%");
        assertEquals(CognitiveDisposition.IGNORE, result.disposition);
        assertEquals(0.70, result.confidence, 0.0001);
        assertTrue(result.items.isEmpty());
    }

    @Test
    public void emptyThinkWrapperDoesNotBreakTypeOnlyJson() throws Exception {
        String raw = "<think>\n\n</think>\n\n{\"t\":\"ACTION\"}";
        CognitiveResult result = FastCognitiveResultParser.parse(raw, "Send file");
        assertEquals(CognitiveDisposition.DERIVE, result.disposition);
        assertEquals(CognitiveKind.ACTION, result.items.get(0).kind);
        assertFalse(FastCognitiveResultParser.hasNonEmptyThinking(raw));
    }

    @Test
    public void oldDkEventWireStillParsesDuringTransition() throws Exception {
        CognitiveResult result = FastCognitiveResultParser.parse("{\"d\":\"EV\",\"k\":\"EVENT\"}", "Dentist tomorrow at 4");
        assertEquals(CognitiveDisposition.DERIVE, result.disposition);
        assertEquals(CognitiveKind.EVENT, result.items.get(0).kind);
    }

    @Test
    public void unknownTypeFailsClosed() throws Exception {
        try {
            FastCognitiveResultParser.parse("{\"t\":\"BANANA\"}", "Anything");
            fail("BANANA must be rejected");
        } catch (CognitiveContractException expected) {
            assertTrue(expected.getMessage().contains("Unknown fast cognitive type"));
        }
    }
}
