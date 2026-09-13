package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class FailoverBridgeTransportTest {
    @Test public void authRegistrationFailureFallsBackWithSameEnvelope() throws Exception {
        JSONObject request=ChatGptBridgeProtocol.newTestRequest(
                "run-1","test-1",new JSONObject().put("input","x"),
                new JSONObject().put("decision","NOW"),new JSONObject().put("expected","NOW"),
                ChatGptBridgeProtocol.defaultJudgingRules());
        String expectedRequestId=request.getString("requestId");
        RecordingTransport primary=new RecordingTransport("GMAIL_API_OAUTH",false,"AuthenticatorException: UnregisteredOnApiConsole");
        RecordingTransport fallback=new RecordingTransport("GMAIL_IMAP_SMTP_APP_PASSWORD",true,"");
        FailoverBridgeTransport transport=new FailoverBridgeTransport(primary,fallback);
        GmailBridgeTransport.SendResult result=transport.sendEnvelope(request);
        assertTrue(result.ok);
        assertEquals(expectedRequestId,primary.last.getString("requestId"));
        assertEquals(expectedRequestId,fallback.last.getString("requestId"));
        assertEquals("GMAIL_IMAP_SMTP_APP_PASSWORD",transport.name());
    }

    private static final class RecordingTransport implements BridgeMailTransport {
        final String name; final boolean ok; final String error; JSONObject last;
        RecordingTransport(String name,boolean ok,String error){this.name=name;this.ok=ok;this.error=error;}
        @Override public GmailBridgeTransport.SendResult sendEnvelope(JSONObject envelope){last=envelope;return ok?GmailBridgeTransport.SendResult.ok("id",""):GmailBridgeTransport.SendResult.fail(error);}
        @Override public List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs,int maxResults){return new ArrayList<>();}
        @Override public String name(){return name;}
    }
}
