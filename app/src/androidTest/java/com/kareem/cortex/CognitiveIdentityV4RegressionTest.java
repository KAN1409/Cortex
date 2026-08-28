package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveIdentityV4RegressionTest {

    @Test public void repeatedNotificationCallbackKeepsOneEvidenceRevision() {
        String hash = Fingerprint.text("Sarah\nLet's move the meeting to Wednesday");
        String a = CognitiveIdentityV4.evidenceId(
                CognitiveDomainV4.EvidenceSourceType.NOTIFICATION,
                "com.whatsapp",
                "notification-key-123",
                hash,
                "",
                1_000L);
        String b = CognitiveIdentityV4.evidenceId(
                CognitiveDomainV4.EvidenceSourceType.NOTIFICATION,
                "com.whatsapp",
                "notification-key-123",
                hash,
                "",
                90_000L);
        assertEquals(a, b);
    }

    @Test public void changedNotificationContentCreatesNewRevision() {
        String a = CognitiveIdentityV4.evidenceId(
                CognitiveDomainV4.EvidenceSourceType.NOTIFICATION,
                "com.whatsapp",
                "notification-key-123",
                Fingerprint.text("first body"),
                "",
                1_000L);
        String b = CognitiveIdentityV4.evidenceId(
                CognitiveDomainV4.EvidenceSourceType.NOTIFICATION,
                "com.whatsapp",
                "notification-key-123",
                Fingerprint.text("changed body"),
                "",
                2_000L);
        assertNotEquals(a, b);
    }

    @Test public void sameContactIdCanAutoMergeWorlds() {
        CognitiveIdentityV4.IdentityClaim left = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.CONTACT_ID,
                "contacts:408",
                CognitiveIdentityV4.ClaimStrength.STRONG,
                false,
                "ev_a");
        CognitiveIdentityV4.IdentityClaim right = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.CONTACT_ID,
                "contacts:408",
                CognitiveIdentityV4.ClaimStrength.STRONG,
                false,
                "ev_b");

        CognitiveIdentityV4.Match match = CognitiveIdentityV4.matchWorlds(
                CognitiveDomainV4.WorldTypeHint.PERSON,
                Arrays.asList(left),
                CognitiveDomainV4.WorldTypeHint.PERSON,
                Arrays.asList(right));

        assertEquals(CognitiveIdentityV4.MatchDecision.SAME, match.decision);
        assertTrue(match.canAutoMerge());
    }

    @Test public void weakSharedPhoneCannotAutoMergePeople() {
        CognitiveIdentityV4.IdentityClaim left = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.PHONE_E164,
                "+201001234567",
                CognitiveIdentityV4.ClaimStrength.WEAK,
                false,
                "ev_a");
        CognitiveIdentityV4.IdentityClaim right = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.PHONE_E164,
                "+201001234567",
                CognitiveIdentityV4.ClaimStrength.WEAK,
                false,
                "ev_b");

        CognitiveIdentityV4.Match match = CognitiveIdentityV4.matchWorlds(
                CognitiveDomainV4.WorldTypeHint.PERSON,
                Arrays.asList(left),
                CognitiveDomainV4.WorldTypeHint.PERSON,
                Arrays.asList(right));

        assertEquals(CognitiveIdentityV4.MatchDecision.POSSIBLE, match.decision);
        assertFalse(match.canAutoMerge());
    }

    @Test public void sameDisplayNameAloneNeverAutoMergesPeople() {
        CognitiveIdentityV4.IdentityClaim left = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.EXACT_NAME,
                "Ahmed",
                CognitiveIdentityV4.ClaimStrength.MEDIUM,
                false,
                "ev_a");
        CognitiveIdentityV4.IdentityClaim right = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.EXACT_NAME,
                "Ahmed",
                CognitiveIdentityV4.ClaimStrength.MEDIUM,
                false,
                "ev_b");

        CognitiveIdentityV4.Match match = CognitiveIdentityV4.matchWorlds(
                CognitiveDomainV4.WorldTypeHint.PERSON,
                Arrays.asList(left),
                CognitiveDomainV4.WorldTypeHint.PERSON,
                Arrays.asList(right));

        assertEquals(CognitiveIdentityV4.MatchDecision.POSSIBLE, match.decision);
        assertFalse(match.canAutoMerge());
    }

    @Test public void commitmentSituationRemainsSameAcrossRepeatedSignals() {
        String a = CognitiveIdentityV4.situationId(
                CognitiveDomainV4.SituationKind.COMMITMENT,
                "world_ahmed",
                "send revised plan",
                "message-1");
        String b = CognitiveIdentityV4.situationId(
                CognitiveDomainV4.SituationKind.COMMITMENT,
                "world_ahmed",
                "send revised plan",
                "message-9");
        assertEquals(a, b);
    }

    @Test public void eventShapedSituationUsesOccurrenceDiscriminator() {
        String a = CognitiveIdentityV4.situationId(
                CognitiveDomainV4.SituationKind.RISK,
                "world_cib",
                "card transaction declined at spotify",
                "2026-08-28T06");
        String b = CognitiveIdentityV4.situationId(
                CognitiveDomainV4.SituationKind.RISK,
                "world_cib",
                "card transaction declined at spotify",
                "2026-08-28T11");
        assertNotEquals(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void eventShapedSituationCannotOmitOccurrenceIdentity() {
        CognitiveIdentityV4.situationId(
                CognitiveDomainV4.SituationKind.RISK,
                "world_cib",
                "card transaction declined at spotify",
                "");
    }

    @Test public void factSlotStaysStableWhileVersionChanges() {
        String slotA = CognitiveIdentityV4.factSlotKey("world_cortex", "current branch");
        String slotB = CognitiveIdentityV4.factSlotKey("world_cortex", "current branch");
        assertEquals(slotA, slotB);

        String v1 = CognitiveIdentityV4.factVersionKey("world_cortex", "current branch", "main", null);
        String v2 = CognitiveIdentityV4.factVersionKey("world_cortex", "current branch", "cleanup/repo-consolidation", null);
        assertNotEquals(v1, v2);
    }
}
