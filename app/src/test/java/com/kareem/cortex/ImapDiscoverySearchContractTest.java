package com.kareem.cortex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImapDiscoverySearchContractTest {
    @Test public void discoveryDoesNotDependOnSubjectEncoding() {
        String q = AppPasswordImapSmtpTransport.buildDiscoverySearchForTest("13-Sep-2026", "engkareemnasser@gmail.com");
        assertTrue(q.contains("SINCE 13-Sep-2026"));
        assertTrue(q.contains("FROM \"engkareemnasser@gmail.com\""));
        assertTrue(q.contains("TO \"engkareemnasser@gmail.com\""));
        assertFalse(q.contains("SUBJECT"));
        assertFalse(q.contains("CHATGPT_TEST_VERDICT"));
    }
}
