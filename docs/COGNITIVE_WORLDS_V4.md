# Cognitive Worlds V4

Stage D adds the canonical `World` layer above grounded Memory without changing the current product navigation.

## Contract

A World is a persistent context such as a person, project, topic, organization, place, product, asset, or event series. It is not a generated folder and it is not created merely because two strings look similar.

Canonical flow:

`Evidence -> Episode -> Memory -> Worlds / Facts / Relations`

Worlds remain projections over grounded canonical data. Evidence and Memory provenance remains available below every accepted World association.

## Candidate extraction

`CognitiveWorldCandidateExtractorV4` performs a deliberately conservative first pass. It reads only explicit structured metadata attached to Evidence linked to a Memory. Supported structured candidate classes currently include:

- person: explicit person/participant/sender/contact name, optionally contact ID, E.164 phone, or account ID
- project: explicit project name, optionally project ID
- organization: explicit organization/company name, optionally domain and source package
- place: explicit place/location name, optionally place ID
- topic: explicit topic/topic_name

Free text is not entity-guessed in this layer. A future model-assisted extractor or direct user action may produce the same `Candidate` contract, but it must still carry Evidence or Memory provenance.

## Identity policy

`CognitiveWorldResolverV4` delegates identity decisions to `CognitiveIdentityV4`.

Rules:

- exact name alone never authorizes an automatic merge
- model aliases never authorize an automatic merge
- durable identity claims may authorize reuse only when `matchWorlds()` returns `SAME` with confidence >= 0.98
- multiple durable matches are treated as ambiguous and require user resolution
- weak/name-only candidates use a provenance-scoped seed, so two unrelated people/projects with the same name remain distinct
- explicit user correction wins for canonical display identity; the previous name is retained as an alias
- merge/unmerge remains reversible through the existing `v4_world_merges` ledger

## Grounding

A candidate must reference at least one existing Evidence or Memory row. Resolution creates grounded `ABOUT` relations:

- `Evidence -> World`
- `Memory -> World`

Memory-to-World relations inherit the Memory's backing Evidence as their grounding support. Identity claims may also point directly to Evidence.

No World summary is generated during resolution. `summary` remains null until a separately grounded summarization policy exists.

## Projection

`CognitiveWorldProjectionV4` is V4-only and has no legacy fallback. It exposes active canonical Worlds with:

- canonical name, type, maturity, activity time
- alias and identity-claim counts
- grounded Memory/Evidence relation counts
- active Fact count
- merged-child count

Search is literal and supports canonical name, summary, and normalized aliases. SQL wildcard characters are escaped.

Merged child Worlds are not returned as top-level active rows.

## Stage D regression gates

`CognitiveWorldsV4RegressionTest` covers:

1. structured Evidence -> grounded person candidate
2. free text alone does not invent a World
3. same name alone cannot auto-merge
4. durable phone identity can auto-merge
5. projection returns only the active canonical World and grounded counts
6. `%` and `_` are treated literally in World search

The next gate is a clean `assembleDebug + assembleDebugAndroidTest` compile on the real Termux environment. Instrumentation execution remains a separate gate; compiling the androidTest APK does not execute the tests.
