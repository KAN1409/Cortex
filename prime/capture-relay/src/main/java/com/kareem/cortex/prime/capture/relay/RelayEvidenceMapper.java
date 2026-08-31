package com.kareem.cortex.prime.capture.relay;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Maps one immutable notification observation to one immutable Cortex evidence revision. */
public final class RelayEvidenceMapper {
    private RelayEvidenceMapper() {}

    public static EvidenceRecord toEvidence(NotificationObservation observation) {
        String payload = observation.canonicalPayloadJson();
        String identityMaterial = observation.packageName + "\n"
                + observation.notificationKey + "\n"
                + observation.occurredAtEpochMs + "\n"
                + payload;
        return new EvidenceRecord(
                "ev_notif_" + sha256(identityMaterial).substring(0, 24),
                EvidenceSource.NOTIFICATION,
                observation.occurredAtEpochMs,
                observation.textForEvidence(),
                observation.sourceRef(),
                payload
        );
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
