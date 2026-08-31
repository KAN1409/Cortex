package com.kareem.cortex.prime.capture.vision;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;

public final class ImageFingerprintTest {
    @Test
    public void rendersStableSha256Hex() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("cortex-prime".getBytes(StandardCharsets.UTF_8));
        assertEquals(
                "461f0c3f5b492d9ef2d2ab762f673f4396146bb82634c1b06541356e9fe3b7f4",
                ImageFingerprint.hex(digest)
        );
    }
}
