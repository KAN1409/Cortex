# Cortex ↔ Cortex Relay Signal V2 — Joint Device Acceptance

## Goal

Prove the optional `CORTEX_SIGNAL_V2` path end-to-end on the real Android device while preserving the validated `CORTEX_INGEST_V1` compatibility path.

The acceptance target is not merely that both apps compile. It must prove:

1. authenticated HELLO negotiation selects V2,
2. existing durable Relay backlog keeps the same exact event IDs,
3. V2 evidence reaches the existing canonical Cortex ingest boundary and receives correlated ACKs,
4. bounded mechanical policy can round-trip Cortex → Relay → Cortex,
5. a currently live Android notification action can be explicitly approved in Cortex and executed by Relay,
6. Relay's Full System Test observes and records those facts.

## Required builds

### Cortex Relay

- package: `com.kareem.secondbrain`
- branch: `major/cortex-relay-v2`
- candidate: `2.0.0-candidate2`
- versionCode: `21`
- must update in place with its existing permanent signer

### Cortex

- package: `com.kareem.cortex`
- branch: `integration/cortex-relay-signal-v2`
- candidate: `1.0.0-v51-relay-v2-candidate`
- versionCode: `51`
- must update in place with the existing permanent Cortex signer

## Protocol invariants

- V1 action/service remains `com.kareem.cortex.LOCAL_BUS_V1`.
- Relay preserves the legacy HELLO `capabilities_json` field.
- Relay adds `relay_capabilities_json`.
- Cortex selects V2 only for the Android-UID-authenticated `second_brain` connector when `CORTEX_SIGNAL_V2` is explicitly advertised.
- Cortex returns `selected_protocol=CORTEX_SIGNAL_V2` in the HELLO ACK.
- If V2 is not selected, Relay continues sending validated V1 without behavior changes.
- V2 ingest uses Messenger message `20` and envelope schema `CORTEX_RELAY_SIGNAL_V2`.
- The exact wire `event_id` is preserved while Cortex adapts the V2 envelope into its existing canonical Local Bus V1 ingest boundary.
- Existing Cortex event-id dedupe/store/ACK logic remains authoritative.

## Device sequence

### A. Establish the negotiated session

1. Install/update both candidate builds in place.
2. Open Cortex once, then Cortex Relay.
3. Cortex Relay must show the Cortex connection as ready.
4. Relay diagnostics / Full System Test must report `negotiated_protocol=CORTEX_SIGNAL_V2`.

Failure to select V2 is a blocker for V2 acceptance, but V1 fallback itself remains a compatibility success.

### B. Drain the inherited durable backlog

1. Do not delete or clear the Relay outbox.
2. Allow Relay to send its restored pending events.
3. Each event must retain its original exact wire event ID.
4. Cortex may return `ACCEPTED` or `DUPLICATE_ACCEPTED` for an event that was already ingested before the interruption.
5. Relay removes a durable copy only after the correlated terminal Cortex response.
6. Waiting/outbox count should trend toward zero without new duplicate Cortex evidence.

### C. Prove fresh V2 notification data traffic

1. Receive one real notification from another app while Relay capture is running.
2. Confirm Relay produces a fresh semantic V2 signal.
3. Confirm Cortex returns a correlated ACK with a Cortex signal ID.
4. Confirm the same signal appears in Cortex bridge diagnostics as the latest V2 signal.

### D. Prove bounded policy control traffic

Open:

`Cortex → Advanced → Relay V2 bridge`

Tap **Test policy round-trip**.

The probe deliberately changes no filtering behavior: it requests 72-hour forensic retention and disables no mechanical noise rules. Only the monotonically increasing policy version changes so the control request/result path can be proven.

Expected:

- Cortex sends `MSG_POLICY_UPDATE=201`.
- Relay accepts `CORTEX_RELAY_MECHANICAL_POLICY_V1`.
- Relay returns `MSG_POLICY_RESULT=203`.
- Cortex bridge diagnostics show the returned result.
- Relay V2 operational metrics show `policy_version > 0`.

### E. Prove explicit Action Bridge execution

1. Keep a real notification live so Relay still owns its runtime Android action handles.
2. Open `Cortex → Advanced → Relay V2 bridge`.
3. Inspect **Available Android actions** for the latest V2 signal.
4. Choose one action deliberately.
5. Cortex must show a confirmation dialog before sending anything.
6. Reply actions require the user to type the exact reply text.
7. Confirm the chosen action.

Expected:

- Cortex sends `MSG_ACTION_REQUEST=200` with exact `request_id`, `logical_signal_id`, and `capability_id`.
- Relay executes only the matching still-live capability.
- Duplicate `request_id` does not execute twice.
- Relay returns `MSG_ACTION_RESULT=202` with execution status.
- Cortex bridge diagnostics show the result.
- Relay action audit/metrics record the attempt.

A stale notification/action must fail explicitly rather than selecting a different action.

## Full System Test adjudication

After the steps above, run **Full system test** in Cortex Relay and share the generated JSON report.

The report should be able to promote these real-event cases automatically when grounded runtime evidence is present:

- `real.notification_listener`
- `real.multi_account` when two source/profile identities were actually observed
- `real.live_message_delta` when a real bounded message delta exists
- `real.action_execution` after a successful user-approved action
- `real.v2_roundtrip` after V2 negotiation + accepted V2 data + action or policy control traffic

The following remain separate real-device scenarios until specifically exercised:

- process-death/reboot durable recovery
- Accessibility screen capture
- Usage reconciliation
- voice staging/recovery
- Android share ingest
- OCR enrichment

## Failure rules

- Never clear app data to make an acceptance check pass.
- Never delete the durable outbox to hide a backlog.
- Never treat a missing real Android event as a synthetic PASS.
- Never execute an Android action merely because it is available or because a model classified a notification as important.
- Never replace the exact V2 event ID with a newly generated Cortex-side transport ID.
