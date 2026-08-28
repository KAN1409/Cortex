package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Non-invasive domain contracts for the Pulse / Memory / Worlds / Think architecture.
 *
 * <p>This file deliberately has no Android, SQLite, UI, model-provider, or repository dependency.
 * It is a contract layer only. Existing Cortex persistence and screens are not migrated by adding
 * these types.</p>
 */
public final class CognitiveDomainV4 {
    private CognitiveDomainV4() {}

    public enum EvidenceSourceType {
        NOTIFICATION,
        SCREEN,
        VOICE,
        IMAGE,
        SHARE,
        NOTE,
        LINK,
        FILE,
        APP_ACTIVITY,
        CALENDAR,
        CONTACT,
        LOCATION,
        SYSTEM
    }

    public enum Sensitivity {
        NORMAL,
        PRIVATE,
        RESTRICTED
    }

    public enum RetentionClass {
        EPISODIC_90_DAY,
        PINNED,
        LONG_TERM_SOURCE,
        TRANSIENT
    }

    public enum ProcessingState {
        CAPTURED,
        ENRICHING,
        READY,
        FAILED
    }

    public static final class Evidence {
        public final String id;
        public final EvidenceSourceType sourceType;
        public final long occurredAt;
        public final long capturedAt;
        public final String sourcePackage;
        public final String externalId;
        public final String originalText;
        public final String normalizedText;
        public final String contentHash;
        public final String assetRef;
        public final Sensitivity sensitivity;
        public final RetentionClass retentionClass;
        public final ProcessingState processingState;

        public Evidence(
                String id,
                EvidenceSourceType sourceType,
                long occurredAt,
                long capturedAt,
                String sourcePackage,
                String externalId,
                String originalText,
                String normalizedText,
                String contentHash,
                String assetRef,
                Sensitivity sensitivity,
                RetentionClass retentionClass,
                ProcessingState processingState) {
            this.id = requireId(id, "evidence");
            this.sourceType = require(sourceType, "sourceType");
            this.occurredAt = occurredAt;
            this.capturedAt = capturedAt;
            this.sourcePackage = sourcePackage;
            this.externalId = externalId;
            this.originalText = originalText;
            this.normalizedText = normalizedText;
            this.contentHash = contentHash;
            this.assetRef = assetRef;
            this.sensitivity = require(sensitivity, "sensitivity");
            this.retentionClass = require(retentionClass, "retentionClass");
            this.processingState = require(processingState, "processingState");
        }
    }

    public enum EpisodeKind {
        CONVERSATION,
        APP_SESSION,
        RESEARCH,
        MEETING,
        TRAVEL,
        CAPTURE,
        DOCUMENT_WORK,
        HEALTH_EVENT,
        GENERIC
    }

    public enum EpisodeState {
        OPEN,
        CLOSED,
        REOPENED
    }

    public static final class Episode {
        public final String id;
        public final EpisodeKind kind;
        public final long startedAt;
        public final Long endedAt;
        public final String primarySourcePackage;
        public final List<String> evidenceIds;
        public final List<String> worldIds;
        public final EpisodeState state;

        public Episode(
                String id,
                EpisodeKind kind,
                long startedAt,
                Long endedAt,
                String primarySourcePackage,
                List<String> evidenceIds,
                List<String> worldIds,
                EpisodeState state) {
            this.id = requireId(id, "episode");
            this.kind = require(kind, "kind");
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.primarySourcePackage = primarySourcePackage;
            this.evidenceIds = immutableIds(evidenceIds);
            this.worldIds = immutableIds(worldIds);
            this.state = require(state, "state");
        }
    }

    public enum MemoryKind {
        MOMENT,
        CONVERSATION,
        SCREEN_CONTEXT,
        VOICE,
        IMAGE,
        DOCUMENT,
        NOTE,
        LINK,
        APP_SESSION,
        EPISODE_SUMMARY
    }

    public static final class Memory {
        public final String id;
        public final MemoryKind kind;
        public final String title;
        public final String body;
        public final long startedAt;
        public final Long endedAt;
        public final List<String> evidenceIds;
        public final String episodeId;
        public final String sourcePackage;
        public final List<String> worldIds;
        public final double importance;
        public final boolean pinned;
        public final RetentionClass retentionClass;

        public Memory(
                String id,
                MemoryKind kind,
                String title,
                String body,
                long startedAt,
                Long endedAt,
                List<String> evidenceIds,
                String episodeId,
                String sourcePackage,
                List<String> worldIds,
                double importance,
                boolean pinned,
                RetentionClass retentionClass) {
            this.id = requireId(id, "memory");
            this.kind = require(kind, "kind");
            this.title = title;
            this.body = body == null ? "" : body;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.evidenceIds = immutableIds(evidenceIds);
            this.episodeId = episodeId;
            this.sourcePackage = sourcePackage;
            this.worldIds = immutableIds(worldIds);
            this.importance = clamp01(importance);
            this.pinned = pinned;
            this.retentionClass = require(retentionClass, "retentionClass");
        }
    }

    public enum WorldTypeHint {
        PERSON,
        PROJECT,
        TOPIC,
        ORGANIZATION,
        PLACE,
        PRODUCT,
        ASSET,
        EVENT_SERIES,
        OTHER
    }

    public enum WorldMaturity {
        EMERGING,
        ESTABLISHED,
        DORMANT,
        ARCHIVED
    }

    public static final class World {
        public final String id;
        public final String canonicalName;
        public final WorldTypeHint typeHint;
        public final WorldMaturity maturity;
        public final String summary;
        public final List<String> aliases;
        public final long createdAt;
        public final long lastActiveAt;
        public final Long archivedAt;

        public World(
                String id,
                String canonicalName,
                WorldTypeHint typeHint,
                WorldMaturity maturity,
                String summary,
                List<String> aliases,
                long createdAt,
                long lastActiveAt,
                Long archivedAt) {
            this.id = requireId(id, "world");
            this.canonicalName = requireText(canonicalName, "canonicalName");
            this.typeHint = require(typeHint, "typeHint");
            this.maturity = require(maturity, "maturity");
            this.summary = summary;
            this.aliases = immutableStrings(aliases);
            this.createdAt = createdAt;
            this.lastActiveAt = lastActiveAt;
            this.archivedAt = archivedAt;
        }
    }

    public enum GroundingKind {
        OBSERVED,
        INFERRED
    }

    public enum FactStatus {
        ACTIVE,
        SUPERSEDED,
        DISPUTED,
        RETRACTED
    }

    public static final class Fact {
        public final String id;
        public final String subjectWorldId;
        public final String predicate;
        public final String value;
        public final GroundingKind grounding;
        public final double confidence;
        public final Long validFrom;
        public final Long validUntil;
        public final List<String> evidenceIds;
        public final List<String> memoryIds;
        public final String supersedesFactId;
        public final FactStatus status;

        public Fact(
                String id,
                String subjectWorldId,
                String predicate,
                String value,
                GroundingKind grounding,
                double confidence,
                Long validFrom,
                Long validUntil,
                List<String> evidenceIds,
                List<String> memoryIds,
                String supersedesFactId,
                FactStatus status) {
            this.id = requireId(id, "fact");
            this.subjectWorldId = subjectWorldId;
            this.predicate = requireText(predicate, "predicate");
            this.value = requireText(value, "value");
            this.grounding = require(grounding, "grounding");
            this.confidence = clamp01(confidence);
            this.validFrom = validFrom;
            this.validUntil = validUntil;
            this.evidenceIds = immutableIds(evidenceIds);
            this.memoryIds = immutableIds(memoryIds);
            this.supersedesFactId = supersedesFactId;
            this.status = require(status, "status");

            if (this.grounding == GroundingKind.OBSERVED
                    && this.evidenceIds.isEmpty()
                    && this.memoryIds.isEmpty()) {
                throw new IllegalArgumentException("Observed fact requires provenance");
            }
        }
    }

    public enum CanonicalObjectType {
        EVIDENCE,
        EPISODE,
        MEMORY,
        WORLD,
        FACT,
        SITUATION
    }

    public enum RelationType {
        ABOUT,
        RELATED_TO,
        SUPPORTED_BY,
        INVOLVES,
        CAUSED_BY,
        FOLLOWS,
        PART_OF,
        REFERENCES,
        SUPERSEDES
    }

    public static final class Relation {
        public final String id;
        public final CanonicalObjectType sourceType;
        public final String sourceId;
        public final CanonicalObjectType targetType;
        public final String targetId;
        public final RelationType relationType;
        public final GroundingKind grounding;
        public final double confidence;
        public final List<String> evidenceIds;

        public Relation(
                String id,
                CanonicalObjectType sourceType,
                String sourceId,
                CanonicalObjectType targetType,
                String targetId,
                RelationType relationType,
                GroundingKind grounding,
                double confidence,
                List<String> evidenceIds) {
            this.id = requireId(id, "relation");
            this.sourceType = require(sourceType, "sourceType");
            this.sourceId = requireId(sourceId, "source");
            this.targetType = require(targetType, "targetType");
            this.targetId = requireId(targetId, "target");
            this.relationType = require(relationType, "relationType");
            this.grounding = require(grounding, "grounding");
            this.confidence = clamp01(confidence);
            this.evidenceIds = immutableIds(evidenceIds);
        }
    }

    public enum SituationKind {
        COMMITMENT,
        WAITING,
        DEADLINE,
        UPCOMING_EVENT,
        MEANINGFUL_CHANGE,
        RISK,
        DECISION,
        OPPORTUNITY,
        UNRESOLVED_QUESTION,
        PREPARATION,
        PATTERN,
        FOLLOW_UP
    }

    public enum SituationState {
        DETECTED,
        RELEVANT,
        SURFACED,
        DEFERRED,
        WAITING,
        RESOLVED,
        CANCELLED,
        DISMISSED
    }

    public static final class Situation {
        public final String id;
        public final SituationKind kind;
        public final SituationState state;
        public final String headline;
        public final String explanation;
        public final List<String> worldIds;
        public final List<String> evidenceIds;
        public final List<String> memoryIds;
        public final List<String> factIds;
        public final long createdAt;
        public final Long relevantFrom;
        public final Long relevantUntil;
        public final long lastEvaluatedAt;
        public final double attentionScore;
        public final double interruptionScore;
        public final double confidence;
        public final List<String> actionProposalIds;

        public Situation(
                String id,
                SituationKind kind,
                SituationState state,
                String headline,
                String explanation,
                List<String> worldIds,
                List<String> evidenceIds,
                List<String> memoryIds,
                List<String> factIds,
                long createdAt,
                Long relevantFrom,
                Long relevantUntil,
                long lastEvaluatedAt,
                double attentionScore,
                double interruptionScore,
                double confidence,
                List<String> actionProposalIds) {
            this.id = requireId(id, "situation");
            this.kind = require(kind, "kind");
            this.state = require(state, "state");
            this.headline = requireText(headline, "headline");
            this.explanation = explanation == null ? "" : explanation;
            this.worldIds = immutableIds(worldIds);
            this.evidenceIds = immutableIds(evidenceIds);
            this.memoryIds = immutableIds(memoryIds);
            this.factIds = immutableIds(factIds);
            this.createdAt = createdAt;
            this.relevantFrom = relevantFrom;
            this.relevantUntil = relevantUntil;
            this.lastEvaluatedAt = lastEvaluatedAt;
            this.attentionScore = clamp01(attentionScore);
            this.interruptionScore = clamp01(interruptionScore);
            this.confidence = clamp01(confidence);
            this.actionProposalIds = immutableIds(actionProposalIds);
        }

        public boolean isResolved() {
            return state == SituationState.RESOLVED
                    || state == SituationState.CANCELLED
                    || state == SituationState.DISMISSED;
        }
    }

    public enum ActionRisk {
        SAFE,
        CONFIRMATION_REQUIRED,
        SENSITIVE,
        BLOCKED
    }

    public enum ActionState {
        PROPOSED,
        CONFIRMED,
        EXECUTING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum ActionType {
        OPEN,
        REPLY,
        CALL,
        DRAFT,
        SEND,
        REMIND,
        SCHEDULE,
        COMPLETE,
        DECIDE,
        REVIEW,
        CUSTOM
    }

    public static final class ActionProposal {
        public final String id;
        public final String situationId;
        public final String worldId;
        public final ActionType type;
        public final String label;
        public final ActionRisk risk;
        public final String payloadJson;
        public final ActionState state;

        public ActionProposal(
                String id,
                String situationId,
                String worldId,
                ActionType type,
                String label,
                ActionRisk risk,
                String payloadJson,
                ActionState state) {
            this.id = requireId(id, "action");
            this.situationId = situationId;
            this.worldId = worldId;
            this.type = require(type, "type");
            this.label = requireText(label, "label");
            this.risk = require(risk, "risk");
            this.payloadJson = payloadJson;
            this.state = require(state, "state");
        }
    }

    public enum ThoughtIntent {
        RECALL,
        EXPLAIN,
        COMPARE,
        PLAN,
        REFLECT,
        ACT,
        FIND
    }

    public enum ReasoningBlockType {
        ANSWER,
        OBSERVATION,
        INFERENCE,
        SUGGESTION,
        EVIDENCE,
        TIMELINE,
        COMPARISON,
        PLAN,
        LIST,
        ASSET_RESULT,
        ACTION_PROPOSAL,
        WARNING
    }

    public enum StatementGrounding {
        OBSERVED,
        INFERRED,
        SUGGESTED
    }

    public static final class ThoughtRequest {
        public final String query;
        public final String contextWorldId;
        public final String contextSituationId;
        public final List<String> explicitMemoryIds;
        public final long requestedAt;

        public ThoughtRequest(
                String query,
                String contextWorldId,
                String contextSituationId,
                List<String> explicitMemoryIds,
                long requestedAt) {
            this.query = requireText(query, "query");
            this.contextWorldId = contextWorldId;
            this.contextSituationId = contextSituationId;
            this.explicitMemoryIds = immutableIds(explicitMemoryIds);
            this.requestedAt = requestedAt;
        }
    }

    public static final class ReasoningBlock {
        public final ReasoningBlockType type;
        public final StatementGrounding grounding;
        public final String text;
        public final List<String> evidenceIds;
        public final List<String> memoryIds;
        public final List<String> factIds;
        public final String actionProposalId;

        public ReasoningBlock(
                ReasoningBlockType type,
                StatementGrounding grounding,
                String text,
                List<String> evidenceIds,
                List<String> memoryIds,
                List<String> factIds,
                String actionProposalId) {
            this.type = require(type, "type");
            this.grounding = require(grounding, "grounding");
            this.text = text == null ? "" : text;
            this.evidenceIds = immutableIds(evidenceIds);
            this.memoryIds = immutableIds(memoryIds);
            this.factIds = immutableIds(factIds);
            this.actionProposalId = actionProposalId;

            if (grounding == StatementGrounding.OBSERVED
                    && this.evidenceIds.isEmpty()
                    && this.memoryIds.isEmpty()
                    && this.factIds.isEmpty()) {
                throw new IllegalArgumentException("Observed reasoning block requires provenance");
            }
        }
    }

    public static final class ReasoningResult {
        public final ThoughtIntent intent;
        public final List<ReasoningBlock> blocks;
        public final List<String> evidenceIds;
        public final List<String> memoryIds;
        public final List<String> factIds;
        public final boolean insufficientEvidence;
        public final long generatedAt;

        public ReasoningResult(
                ThoughtIntent intent,
                List<ReasoningBlock> blocks,
                List<String> evidenceIds,
                List<String> memoryIds,
                List<String> factIds,
                boolean insufficientEvidence,
                long generatedAt) {
            this.intent = require(intent, "intent");
            this.blocks = immutableList(blocks);
            this.evidenceIds = immutableIds(evidenceIds);
            this.memoryIds = immutableIds(memoryIds);
            this.factIds = immutableIds(factIds);
            this.insufficientEvidence = insufficientEvidence;
            this.generatedAt = generatedAt;
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requireId(String value, String type) {
        return requireText(value, type + " id");
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static List<String> immutableIds(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        ArrayList<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<String> immutableStrings(List<String> values) {
        return immutableIds(values);
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
