# CORTEX PRODUCT CONSTITUTION V1

Status: LOCKED FOR PRODUCT DEVELOPMENT
Baseline: Cortex v62 (`91fe5ebc862467aff99057f1d5eeec821cda17a3`)

## 1. Product definition

Cortex is not an AI chatbot and is not a competing intelligence to ChatGPT.

Cortex is the phone-side data, context, memory, evidence and action layer. Its job is to collect, normalize, store, connect, retrieve and present personal data in a form that is useful to the user and understandable by ChatGPT.

ChatGPT is the semantic brain. When meaning, reconciliation, judgment or organization requires intelligence, Cortex prepares a bounded context pack and asks ChatGPT. Cortex then accepts only a strict machine-readable organization response that it can validate before applying.

## 2. Cortex responsibilities

Cortex MAY:
- Capture evidence from user-approved phone sources.
- Preserve provenance, timestamps, source identity and raw/processed forms.
- Perform mechanical perception: transcription, OCR, image description, translation and file extraction.
- Build bounded context packs for ChatGPT.
- Store explicit user data and validated organizational metadata.
- Link related evidence.
- Create user-visible follow-ups and project candidates when ChatGPT explicitly returns them in the Cortex organization protocol.
- Preview every proposed data change before it is applied.
- Apply only user-approved, schema-valid, grounded operations.
- Retrieve and present the organized result back to the user or to ChatGPT.

## 3. Cortex must NOT become a second brain

Cortex MUST NOT:
- Invent meaning, priorities, conclusions, life advice or interpretations by itself.
- Use local/cloud API models as competing reasoning brains.
- Treat OCR, vision, ASR or translation models as decision makers.
- Automatically promote model output into truth.
- Build product features whose main purpose is choosing among many reasoning models.
- Recreate ChatGPT as an in-app chat product.
- Execute destructive data operations from an AI response without explicit user approval.

Perception models are sensors. ChatGPT is the brain.

## 4. ChatGPT -> Cortex boundary

ChatGPT may think freely in the user's ChatGPT conversation. Cortex never needs those chains of thought, creative branches, lenses, debates or speculative ideas.

The only data allowed to return to Cortex is explicit application-oriented organization data using a strict Cortex protocol.

First allowed operation family:
- `TAG_EVIDENCE`
- `LINK_EVIDENCE`
- `CREATE_FOLLOW_UP`
- `CREATE_PROJECT_CANDIDATE`

Operations must cite existing Cortex evidence IDs. Unknown IDs fail closed. Unsupported operation types fail closed. No delete operation exists in V1.

## 5. User control

The invariant is:

Observe -> Prepare -> ChatGPT organizes -> Preview -> User approves -> Apply

No ChatGPT response changes Cortex data on receipt. Validation and preview always happen first.

## 6. Privacy and handoff

Before Cortex opens/shares a context pack with ChatGPT, the UI must show what is about to leave Cortex.

The first organizer version sends text/metadata only. Raw attachments, live screen frames and API keys are not included unless a future feature explicitly says otherwise and the user approves it.

## 7. Development rules that prevent drift

Every product release must answer:

> After this release, what can the user visibly do that they could not do before?

A release is not justified only by:
- adding tests;
- adding a provider;
- changing routing;
- refactoring an engine;
- adding telemetry;
- changing an internal model;
- increasing benchmark scores.

Internal work is allowed only when it enables or protects a visible product result in the same release.

Testing is a release guard, not the product roadmap. Once the acceptance path passes, stop testing and move to the next visible capability unless a real blocker remains.

## 8. Current product sequence

1. ChatGPT Organizer V1: Cortex prepares data, ChatGPT returns strict organizational operations, user previews and applies selected operations.
2. Make organized results visible and useful in Evidence / Projects / follow-ups.
3. Expand context sources only when they produce an obvious user benefit.
4. Improve retrieval back into ChatGPT.
5. Add phone actions only through explicit capability + approval boundaries.

Do not merge any development branch without the user's exact instruction: `merge`.
