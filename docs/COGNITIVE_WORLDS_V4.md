# Cognitive Worlds V4

Stage D adds the canonical `World` layer above grounded Memory without changing the current product navigation.

## Contract

A World is a persistent context such as a person, project, topic, organization, place, product, asset, or event series. It is not a generated folder and it is not created merely because two strings look similar.

Canonical flow:

`Evidence -> Episode -> Memory -> Worlds / Facts / Relations`

Worlds remain projections over grounded canonical data. Evidence and Memory provenance remains available below every accepted World association.

## Candidate pipeline

Stage D now separates four different questions that legacy Cortex often mixed together:

1. **Did a source mention something entity-shaped?**
2. **What semantic class is it?** Person, organization, group/conversation, app/system, or unknown.
3. **Do we have a durable identity boundary?** Contact ID, phone, source-scoped participant key/URI, external ID, etc.
4. **Is the semantic type safe enough to materialize as a new canonical World?**

Durable identity does not imply semantic type. A stable messaging participant key may prove that two observations came from the same sender without proving that the sender is a human PERSON rather than a business, bot, or service account.

## Structured extraction

`CognitiveWorldCandidateExtractorV4` reads structured metadata attached to Evidence linked to a Memory. Supported structured candidates include:

- person/contact/participant hints with optional contact ID, E.164 phone, account ID, participant key, or participant URI
- project name with optional project ID
- organization/company name with optional domain or explicit organization package
- place/location name with optional place ID
- topic/topic_name

`person_hint` is not a Person fact. It is sent through `CognitiveWorldCandidateClassifierV4` first.

## Semantic classification

`CognitiveWorldCandidateClassifierV4` classifies notification/entity-shaped metadata into:

- `PERSON`
- `ORGANIZATION`
- `GROUP_CONVERSATION`
- `APP_SYSTEM`
- `UNKNOWN`

Important safety behavior:

- group conversation titles do not become Person Worlds
- generic service labels such as backup/sync/battery/system messages do not become Person Worlds
- email sender labels remain unknown unless there is stronger semantic evidence because they may represent a person, organization, automation, or mailing list
- Android message participant keys/URIs remain durable identity anchors, but a brand-new Person World is deferred until human type is corroborated or user-confirmed
- explicit structured person/contact fields may approve the Person semantic type
- explicit structured organization fields may approve the Organization semantic type

## Preserved-analysis enrichment

Stage C preserved legacy analysis JSON in `v4_evidence_analysis`. `CognitiveWorldAnalysisCandidateExtractorV4` reads `entities[]` from those existing analysis results and converts supported entity kinds into grounded World proposals.

Analysis-derived proposals:

- always reference the Evidence and Memory that produced them
- use weak `MODEL_ALIAS` identity claims
- never authorize automatic merge
- never create a canonical World by themselves
- reject obvious generic app/system labels

This lets Stage D recover historical candidate coverage without re-running AI over all Memory and without pretending heuristic/model extraction is identity truth.

## Notification capture hardening

`CommunicationEvidenceNormalizer` no longer assumes that every notification emitted by WhatsApp, Telegram, Messenger, or another messaging package is a message. Actual message evidence requires Android message category / message payload evidence (or another explicit communication signal).

`NotificationIdentityHintsV4` records participant/conversation identity-shaped hints when Android exposes them. `CanonicalPersonResolver` now applies the semantic gate before writing new legacy PERSON nodes, reducing continued ontology pollution while V4 is being introduced.

## Resolution policy

`CognitiveWorldResolverV4` delegates identity matching to `CognitiveIdentityV4` and separately enforces semantic type approval.

Rules:

- exact name alone never authorizes automatic merge
- model aliases never authorize automatic merge
- weak/name-only candidates are deferred
- durable identity with unconfirmed semantic type is also deferred for a new World
- an existing canonical World may accept newly grounded evidence when a durable identity matches because the existing World already supplies the canonical type boundary
- multiple durable matches are ambiguous and require user resolution
- explicit user correction wins for canonical display identity; the previous name remains an alias
- merge/unmerge remains reversible through `v4_world_merges`

## Grounding

A candidate must reference at least one existing Evidence or Memory row. Accepted resolution creates grounded `ABOUT` relations:

- `Evidence -> World`
- `Memory -> World`

Memory-to-World relations inherit the Memory's backing Evidence as grounding support. Identity claims may also point directly to Evidence.

No World summary is generated during resolution. `summary` remains null until a separately grounded summarization policy exists.

## Projection

`CognitiveWorldProjectionV4` is V4-only and has no legacy fallback. It exposes active canonical Worlds with canonical name/type/maturity/activity plus grounded relation, claim, alias, Fact, and merge counts.

Search is literal and SQL wildcard characters are escaped. Merged child Worlds are not returned as top-level active rows.

## Stage D regression gates

Current regression coverage includes:

- same display name cannot auto-merge
- durable identity is source-scoped where required
- carrier app package cannot become organization identity accidentally
- group title cannot become Person
- `person_hint` remains weak/deferred
- stable participant identity does not by itself prove human type
- explicit contact/person evidence can approve Person materialization
- generic backup/system labels are rejected
- messaging-app package membership alone cannot classify service notifications as messages
- preserved analysis PERSON/PROJECT entities remain grounded, weak, and deferred
- projection returns only active canonical Worlds with grounded counts
- `%` and `_` remain literal in World search

The next gate is a clean `assembleDebug + assembleDebugAndroidTest` compile on the real Termux environment, followed by a read-only real-data analysis-entity dry run. Compiling the androidTest APK does not execute instrumentation tests.
