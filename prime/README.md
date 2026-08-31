# Cortex Prime

Fresh implementation of Cortex focused on reliable local context capture and organization.

## Product goal
Cortex Prime should continuously understand the user's phone context from three primary inputs:
- Notifications (Relay core reused as an internal module)
- Voice
- Vision / OCR

It organizes evidence with specialized local models, validates every derived result against source evidence, and renders useful structured state such as related context, tasks, calendar candidates, and saved items.

## Non-goals for V0
- No legacy Cortex Brain architecture
- No automatic ungrounded Memory / World / Situation generation
- No cross-app Relay bridge
- No model may directly mutate canonical state

## Prime pipeline
Capture -> Evidence Store -> Specialized Models -> Validator -> Canonical State -> UI / Actions

## Five model roles
1. ASR: audio -> transcript
2. Vision/OCR: image -> visible text and grounded visual facts
3. Extractor: text/evidence -> typed entities, dates, tasks, events, facts
4. Linker: relate/dedupe evidence across sources
5. Organizer: propose the final structured interpretation for Cortex

Canonical writes are performed only by deterministic application code after validation.
