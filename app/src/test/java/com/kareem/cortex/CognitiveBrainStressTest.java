package com.kareem.cortex;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class CognitiveBrainStressTest {
    private static final long NOW = 2_000_000_000L;
    private static final long HOUR = 60L * 60L * 1000L;

    private AttentionDecisionEngine.Candidate c(
            long id, String type, String state, String subject, String summary,
            double confidence, double urgency, double actionability, double relevance,
            double risk, double novelty, long deadline, int repeated, int evidence,
            boolean unresolved, boolean commitment, boolean material,
            boolean request, boolean severe) {
        return new AttentionDecisionEngine.Candidate(
                id, type, state, subject, summary, confidence, urgency, actionability,
                relevance, risk, novelty, deadline, NOW, repeated, evidence,
                unresolved, commitment, material, request, severe);
    }

    @Test public void randomizedResolvedSituationsNeverSurface() {
        Random r = new Random(1409L);
        for (int i = 0; i < 3000; i++) {
            AttentionDecisionEngine.Candidate x = c(i + 1, "security_alert", "RESOLVED", "X", "Compromised password",
                    1, r.nextDouble(), r.nextDouble(), r.nextDouble(), 1, 1,
                    NOW - HOUR, 5, 5, false, true, true, true, true);
            assertFalse(AttentionDecisionEngine.evaluate(x).surfaceNow);
        }
    }

    @Test public void randomizedLowConfidenceNeverEscapesCapture() {
        Random r = new Random(1410L);
        for (int i = 0; i < 3000; i++) {
            AttentionDecisionEngine.Candidate x = c(i + 1, "action_request", "OPEN", "X", "Send this urgently",
                    r.nextDouble() * 0.699999, 1, 1, 1, 1, 1,
                    NOW, 10, 10, true, true, true, true, true);
            assertFalse(AttentionDecisionEngine.evaluate(x).surfaceNow);
        }
    }

    @Test public void randomizedTechnicalEvidenceNeverSurfaces() {
        Random r = new Random(1411L);
        for (int i = 0; i < 1500; i++) {
            AttentionDecisionEngine.Candidate x = c(i + 1, "technical_event", "OPEN", "MediaOngoingActivity", "foreground service",
                    .99, r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(),
                    NOW, 20, 20, true, true, true, true, true);
            assertFalse(AttentionDecisionEngine.evaluate(x).surfaceNow);
        }
    }

    @Test public void routineWeatherFloodDoesNotFillNow() {
        List<AttentionDecisionEngine.Candidate> xs = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            xs.add(c(i + 1, "weather_event", "OPEN", "New Cairo", (20 + (i % 15)) + "° clear",
                    .98, .2, .1, .3, .05, .2, 0, 1, 1,
                    true, false, false, false, false));
        }
        assertTrue(AttentionDecisionEngine.rankForNow(xs, 5).isEmpty());
    }

    @Test public void socialNoiseFloodDoesNotFillNow() {
        List<AttentionDecisionEngine.Candidate> xs = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            xs.add(c(i + 1, "social_update", "OPEN", "Person " + i, "has a new story",
                    .95, .1, .05, .3, .01, .2, 0, 1, 1,
                    true, false, false, false, false));
        }
        assertTrue(AttentionDecisionEngine.rankForNow(xs, 5).isEmpty());
    }

    @Test public void oneCriticalSituationWinsAgainstThousandsOfNoiseItems() {
        List<AttentionDecisionEngine.Candidate> xs = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            xs.add(c(i + 1, "conversation_message", "OPEN", "Person " + i, "Hello",
                    .95, .1, .05, .2, .01, .2, 0, 1, 1,
                    true, false, false, false, false));
        }
        long criticalId = 999999;
        xs.add(c(criticalId, "security_alert", "OPEN", "Google security alert", "Unauthorized login; change password now",
                .99, .98, .98, .95, .99, .95, 0, 2, 3,
                true, false, true, true, false));
        List<AttentionDecisionEngine.Decision> ranked = AttentionDecisionEngine.rankForNow(xs, 5);
        assertEquals(1, ranked.size());
        assertEquals(criticalId, ranked.get(0).candidate.situationId);
    }

    @Test public void maxItemsIsAlwaysRespectedUnderHeavyLoad() {
        List<AttentionDecisionEngine.Candidate> xs = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            xs.add(c(i + 1, "action_request", "OPEN", "Task " + i, "Please submit item " + i,
                    .99, .8, .9, .8, .2, .8, NOW + HOUR, 1, 1,
                    true, false, true, true, false));
        }
        for (int max = 1; max <= 20; max++) {
            assertEquals(max, AttentionDecisionEngine.rankForNow(xs, max).size());
        }
    }

    @Test public void rankingIsDeterministicAcrossInputPermutations() {
        List<AttentionDecisionEngine.Candidate> base = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            base.add(c(i + 1, "action_request", "OPEN", "Task " + i, "Please respond",
                    .95, .65 + (i % 5) * .05, .8, .7, .1 + (i % 3) * .1, .6,
                    NOW + (i % 4) * HOUR, 1, 1, true, false, true, true, false));
        }
        List<Long> expected = ids(AttentionDecisionEngine.rankForNow(base, 10));
        Random r = new Random(1412L);
        for (int i = 0; i < 100; i++) {
            List<AttentionDecisionEngine.Candidate> shuffled = new ArrayList<>(base);
            Collections.shuffle(shuffled, r);
            assertEquals(expected, ids(AttentionDecisionEngine.rankForNow(shuffled, 10)));
        }
    }

    @Test public void noDuplicateSituationIdsAppearInWorldRanking() {
        CognitiveWorldState world = new CognitiveWorldState();
        for (int i = 0; i < 100; i++) {
            long sid = 100 + (i % 10);
            world.observe(new CognitiveWorldState.Observation(
                    sid, "task:" + sid, "action_request", "OPEN", "Task " + sid,
                    "Please respond update " + i, .95, .8, .9, .8, .2, .7,
                    NOW + HOUR, NOW + i, true, true, false));
        }
        List<AttentionDecisionEngine.Decision> ranked = world.rankNow(NOW, 20);
        Set<Long> seen = new HashSet<>();
        for (AttentionDecisionEngine.Decision d : ranked) {
            assertTrue(seen.add(d.candidate.situationId));
        }
        assertTrue(ranked.size() <= 10);
    }

    @Test public void evidenceAccumulationDoesNotExplodeSituationCount() {
        CognitiveWorldState world = new CognitiveWorldState();
        for (int i = 0; i < 10000; i++) {
            world.observe(new CognitiveWorldState.Observation(
                    77, "account:google", "security_alert", "OPEN", "Google",
                    "Password warning " + i, .95, .9, .9, .9, .95, .5,
                    0, NOW + i, i % 100 == 0, false, false));
        }
        CognitiveWorldState.Situation s = world.getSituation(77);
        assertNotNull(s);
        assertEquals(10000, s.evidenceCount);
        assertEquals(10000, s.repeatedCount);
        assertEquals(1, world.attentionCandidates(NOW).size());
        assertEquals(1, world.rankNow(NOW, 5).size());
    }

    @Test public void closedContextCannotResurrectResolvedSituation() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(new CognitiveWorldState.Observation(
                88, "person:a", "commitment", "RESOLVED", "A", "Done",
                .98, .9, .9, .9, .9, .9, NOW, NOW, true, true, false));
        world.putContext(new CognitiveWorldState.Context("person:a", 1, 1, 1, NOW + HOUR));
        world.putCommitment(new CognitiveWorldState.Commitment("c88", "person:a", "Old task", NOW - HOUR, true, 1));
        assertTrue(world.rankNow(NOW, 5).isEmpty());
    }

    @Test public void severeWeatherCanBeatRoutineRequestWhenRiskIsCritical() {
        List<AttentionDecisionEngine.Candidate> xs = new ArrayList<>();
        xs.add(c(1, "action_request", "OPEN", "Task", "Please send document",
                .95, .7, .8, .8, .1, .7, NOW + 4 * HOUR, 1, 1,
                true, false, true, true, false));
        xs.add(c(2, "weather_event", "OPEN", "New Cairo", "Flash flooding expected; avoid travel",
                .98, .95, .75, .9, .95, .9, 0, 1, 2,
                true, false, true, false, true));
        assertEquals(2L, AttentionDecisionEngine.rankForNow(xs, 2).get(0).candidate.situationId);
    }

    @Test public void overdueCommitmentSurfacesButResolvedOneDoesNot() {
        AttentionDecisionEngine.Candidate open = c(1, "commitment", "WAITING", "Quote", "Send revised quote",
                .95, .5, .65, .9, .1, .6, NOW - HOUR, 1, 1,
                true, true, true, false, false);
        AttentionDecisionEngine.Candidate done = c(2, "commitment", "RESOLVED", "Quote", "Quote sent",
                .99, .9, .9, .9, .2, .9, NOW - HOUR, 3, 3,
                false, true, true, false, false);
        assertTrue(AttentionDecisionEngine.evaluate(open).surfaceNow);
        assertFalse(AttentionDecisionEngine.evaluate(done).surfaceNow);
    }

    @Test public void nullsNaNsAndOutOfRangeValuesRemainSafe() {
        AttentionDecisionEngine.Candidate x = c(1, null, null, null, null,
                Double.NaN, 5, -3, Double.NaN, 9, -1, 0, -5, 0,
                true, false, false, false, false);
        assertEquals(0.0, x.confidence, 0.0);
        assertEquals(1.0, x.urgency, 0.0);
        assertEquals(0.0, x.actionability, 0.0);
        assertEquals(0.0, x.personalRelevance, 0.0);
        assertEquals(1.0, x.risk, 0.0);
        assertEquals(0.0, x.novelty, 0.0);
        assertEquals(0, x.repeatedCount);
        assertEquals(1, x.evidenceCount);
        assertFalse(AttentionDecisionEngine.evaluate(x).surfaceNow);
    }

    @Test public void emptyNullAndZeroCapacityRankingIsSafe() {
        assertTrue(AttentionDecisionEngine.rankForNow(null, 5).isEmpty());
        assertTrue(AttentionDecisionEngine.rankForNow(Collections.emptyList(), 5).isEmpty());
        List<AttentionDecisionEngine.Candidate> xs = Collections.singletonList(
                c(1, "security_alert", "OPEN", "Google", "Compromised password",
                        .99, 1, 1, 1, 1, 1, 0, 1, 1,
                        true, false, true, false, false));
        assertTrue(AttentionDecisionEngine.rankForNow(xs, 0).isEmpty());
        assertTrue(AttentionDecisionEngine.rankForNow(xs, -10).isEmpty());
    }

    @Test public void repeatedRankingDoesNotMutateCandidatesOrChangeOutput() {
        List<AttentionDecisionEngine.Candidate> xs = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            xs.add(c(i + 1, "action_request", "OPEN", "Task", "Please respond " + i,
                    .95, .7, .8, .75, .2, .6, NOW + 2 * HOUR,
                    i % 4, 1 + (i % 5), true, false, true, true, false));
        }
        List<Long> first = ids(AttentionDecisionEngine.rankForNow(xs, 15));
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, ids(AttentionDecisionEngine.rankForNow(xs, 15)));
        }
        assertEquals(250, xs.size());
    }

    private static List<Long> ids(List<AttentionDecisionEngine.Decision> ds) {
        List<Long> out = new ArrayList<>();
        for (AttentionDecisionEngine.Decision d : ds) out.add(d.candidate.situationId);
        return out;
    }
}
