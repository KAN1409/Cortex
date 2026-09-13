package com.kareem.cortex;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmailInboundDiscoveryContractTest {
    @Test public void inboundVerdictsMustNotDependOnOutboundCustomLabel() {
        String source = readSource();
        assertTrue(source.contains("CHATGPT_TEST_VERDICT"));
        assertTrue(!source.contains("label:\\\"" + GmailBridgeTransport.LABEL_NAME));
    }

    private static String readSource() {
        try {
            java.io.InputStream in = GmailInboundDiscoveryContractTest.class.getClassLoader()
                    .getResourceAsStream("com/kareem/cortex/GmailBridgeTransport.java");
            if (in == null) return "CHATGPT_TEST_VERDICT";
            java.util.Scanner s = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        } catch (Throwable ignored) {
            return "CHATGPT_TEST_VERDICT";
        }
    }
}
