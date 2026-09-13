package com.kareem.cortex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class AppPasswordImapLiteralTest {
    @Test public void literalBodyContainingALineIsNeverMistakenForTaggedResponse() throws Exception {
        String mime = "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n\r\n"
                + "{\"messageType\":\"CHATGPT_TEST_VERDICT\"}\n"
                + "A-this-is-body-data-not-an-imap-tag\n"
                + "tail-must-survive";
        byte[] literal = mime.getBytes(StandardCharsets.UTF_8);
        String prefix = "* 42 FETCH (UID 777 BODY[] {" + literal.length + "}\r\n";
        String suffix = "\r\n)\r\nA4 OK Success\r\n";

        ByteArrayOutputStream response = new ByteArrayOutputStream();
        response.write(prefix.getBytes(StandardCharsets.US_ASCII));
        response.write(literal);
        response.write(suffix.getBytes(StandardCharsets.US_ASCII));

        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(response.toByteArray()));
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        BufferedOutputStream out = new BufferedOutputStream(sent);

        byte[] actual = AppPasswordImapSmtpTransport.fetchSingleLiteral(
                out, in, "A4 UID FETCH 777 (BODY.PEEK[])", "A4");

        assertArrayEquals(literal, actual);
        assertTrue(new String(actual, StandardCharsets.UTF_8).contains("A-this-is-body-data-not-an-imap-tag"));
        assertTrue(new String(actual, StandardCharsets.UTF_8).endsWith("tail-must-survive"));
    }
}
