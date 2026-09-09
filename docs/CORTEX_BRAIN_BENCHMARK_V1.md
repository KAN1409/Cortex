# Cortex Brain Benchmark v1

## Purpose

This benchmark exists to prevent Cortex from being optimized for a clean Capture feed instead of useful attention decisions.

The benchmark question is:

> Given everything Cortex currently knows, what actually deserves the user's attention now?

Capture is evidence. Now is a ranked attention projection. Noise in Capture is acceptable if it does not leak into Now and if important situations are not missed.

## Architectural hypothesis

The current v69 projection policy is primarily event-local. It can correctly recognize obvious items such as a security alert, but it does not yet represent a full personal world state before deciding what belongs in Now.

v70 experiments with a separate attention layer:

Observation -> source lifecycle -> fact -> situation -> world state -> attention candidate -> global ranking -> Now

The attention layer must not delete raw evidence and must not mutate Capture as a side effect of ranking.

## Benchmark dimensions

Every candidate is evaluated on independent dimensions rather than one `meaningful` boolean:

- semantic confidence
- unresolved state
- urgency
- actionability
- personal relevance
- risk
- novelty / material change
- deadline proximity
- repeated attempts
- relation to an open commitment
- supporting evidence count

The final decision is global: Cortex ranks candidates against one another and returns only the items that deserve attention now.

## Error priorities

Two primary errors are measured:

1. False negative: something that should be in Now is absent.
2. False positive: something that should stay out of Now is surfaced.

For Cortex, false negatives on high-risk or clearly actionable situations are more severe than a small number of low-cost false positives. However, a useful Now surface must remain sparse.

## v1 golden scenarios

| Scenario | Expected Now | Reason |
|---|---:|---|
| Google compromised-password security situation | YES | high risk + explicit action |
| Duplicate Google Play Services evidence for same security situation | NO second item | supporting evidence, not a second situation |
| Routine local weather | NO | no action or material risk |
| Severe weather affecting an imminent plan | YES | contextual impact + urgency |
| Social story notification | NO | low salience by default |
| Screenshot saved | NO | technical evidence only |
| Ordinary incoming message | NO | no obligation or material context by itself |
| Repeated missed calls with no known context | MAYBE / low rank | repetition is evidence, not sufficient meaning |
| Repeated missed calls linked to an open commitment due today | YES | context converts call evidence into actionable situation |
| Open commitment with deadline approaching | YES | unresolved + deadline + actionability |
| Resolved situation | NO | no longer deserves current attention |
| Low-confidence inference | NO | Capture only until confidence improves |

## Release rule

This benchmark is experimental and does not make v70 release-eligible by itself. No production replacement of v69 attention behavior should happen until:

- golden scenarios pass,
- real-device replay data is evaluated,
- false-negative and false-positive rates are reported separately,
- UPDATE/signing lineage gates remain green,
- the new attention engine is proven better than v69 on the same evidence.

## Next benchmark expansion

The initial synthetic golden set must be followed by replay cases generated from real Cortex Capture data. Each replay case should include the observations, reconstructed situation/world state, expected attention decision, expected reason, and expected rank band. The benchmark must compare candidate architectures on identical cases before choosing an on-device language model or replacing the production projection path.
