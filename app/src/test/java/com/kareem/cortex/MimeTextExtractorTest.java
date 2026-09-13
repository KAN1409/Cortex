package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MimeTextExtractorTest {
    private static final String JSON = "{\"schemaVersion\":1,\"messageType\":\"CHATGPT_TEST_VERDICT\",\"requestId\":\"r1\",\"runId\":\"run1\",\"testId\":\"t1\",\"createdAtEpochMs\":1,\"payloadSha256\":\"x\",\"payload\":{}}";

    @Test public void decodesGmailStyleBase64TextPlain() {
        String encoded = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(JSON.getBytes(StandardCharsets.UTF_8));
        String raw = "To: x@gmail.com\r\n"
                + "Subject: [CORTEX-BRIDGE]\r\n"
                + "Content-Type: text/plain; charset=\"utf-8\"\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "MIME-Version: 1.0\r\n\r\n"
                + encoded + "\r\n";
        assertEquals(JSON, MimeTextExtractor.extractBestText(raw));
    }

    @Test public void decodesQuotedPrintableTextPlain() {
        String raw = "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n\r\n"
                + "{=22hello=22:=22world=22}";
        assertEquals("{\"hello\":\"world\"}", MimeTextExtractor.extractBestText(raw));
    }

    @Test public void prefersPlainTextInsideMultipartAlternative() {
        String b = "cortex-boundary-123";
        String raw = "Content-Type: multipart/alternative; boundary=\"" + b + "\"\r\n\r\n"
                + "--" + b + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n\r\n"
                + JSON + "\r\n"
                + "--" + b + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n"
                + "<pre>wrong</pre>\r\n"
                + "--" + b + "--\r\n";
        assertEquals(JSON, MimeTextExtractor.extractBestText(raw));
    }

    @Test public void stripsImapFetchWrapperBeforeMimeDecode() {
        String encoded = Base64.getEncoder().encodeToString(JSON.getBytes(StandardCharsets.UTF_8));
        String mime = "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + encoded;
        String fetched = "* 7 FETCH (UID 99 BODY[] {" + mime.getBytes(StandardCharsets.UTF_8).length + "})\n"
                + mime + "\n)\nA4 OK Success\n";
        String raw = AppPasswordImapSmtpTransport.extractFetchedMime(fetched);
        assertTrue(raw.startsWith("Content-Type:"));
        assertEquals(JSON, MimeTextExtractor.extractBestText(raw));
    }
}
