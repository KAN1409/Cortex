# Card C — PR #69 Relay V2 rebased onto the final #66 line, with the Relay invariants proven

Kanban task: `t_5e184de4`. Branch `wt/relay-v2-rebase`.
Worktree: `/home/anombyte/atlas/work/cortex-pr-stack-20260831/cortex/.worktrees/t_5e184de4`.

**Head: `9d0095ae0782ca0c25c331ed74102614f3d1f3b8`.**
**Base rebased onto: `32870cd69a0c30ceb0bc134cd91c8eaebd00823f` (#66 head).**

Nothing was pushed. No upstream PR was opened, closed, commented on or merged. No branch was
deleted. `main` was not touched. `scripts/cortex-repo-audit.sh` was not modified. No signing-key
bytes were generated, rotated, replaced, committed, printed, decoded or copied.

---

## 1. Why #69 was CONFLICTING

Not disagreement — **base drift**, confirming Card B's topology correction first-hand.

```
git merge-base 3d542308 32870cd6  ->  0dd8453a9d1f86afde8cd0cd81f6b60242de48c3
```

#66 and #69 share the ancestor `0dd8453a` ("feat(cognitive): make V2 the primary authority path").
From there #66 advanced **33 commits** and #69 advanced **10**. #69 was never cut from the #66 head,
so GitHub compares two divergent lines. The Relay work itself does not contest anything in #66.

Only **three** files are touched by both sides, and only **two** actually conflicted:

| Path | #66 side (base → 32870cd6) | #69 side (base → 3d542308) | Conflicted? |
|---|---|---|---|
| `app/build.gradle` | keystore-b64 reconstruction removed; OCR asset pinned by sha512; `signingConfigs.cortexPersistentDebug` guarded on file existence; `versionCode 54`, `versionName '2.0-v54'` | `versionCode 54`, `versionName '2.0.1-cognitive-relay-v2-candidate'` | **YES** — both edited the same `versionName` line |
| `.github/workflows/build-apk.yml` | adds `fix/v2-reconciliation-v54` + `build/v2-brain-primary-v54` push triggers; wraps the audit step in the `build/v2-brain-primary-v54` bypass | adds `'integration/**'` push trigger; adds `:app:assembleRelease` to the build step; adds the unsigned-release upload | **YES** — adjacent additions in the same `on.push.branches` block |
| `app/src/main/AndroidManifest.xml` | `allowBackup="false"`; `StableSelfContainedReviewActivity` de-exported | adds `CortexRelayV2DiagnosticsActivity` + the exported `CortexLocalBusService` | no — disjoint hunks, auto-merged |

The Relay source files (`CortexRelayBridgeV2`, `CortexLocalBusService`, both protocol classes, the
connector ingest/registry/store, the diagnostics activity, both docs, both scripts) are **new files
in #69 that #66 never touches**. That is why the change set rebases cleanly.

## 2. What was done — a real rebase, not a port

```
git rebase --onto 32870cd69a0c30ceb0bc134cd91c8eaebd00823f 0dd8453a9d1f86afde8cd0cd81f6b60242de48c3
```

All **10** of #69's commits replayed with their original author (`KAN1409`) and messages intact.
No commit was skipped, squashed or reordered. One security commit of mine sits on top (§5).

### Conflict resolution, both by hand

**a. `app/build.gradle` — `versionName`.** Kept **#69's** `'2.0.1-cognitive-relay-v2-candidate'`.
#66's `'2.0-v54'` and #69's value are two names for the same `versionCode 54`; #69's is the later,
Relay-specific candidate name, and it is the identity this branch is a candidate *for*. Everything
else in #66's much larger `build.gradle` rewrite — the removed keystore-b64 reconstruction, the
pinned OCR digest, the existence-guarded signing config — is preserved untouched. Verified: the
audit assertions `build script never reconstructs signing material from tracked base64` and
`Arabic OCR digest matches the pinned 4.1.0 asset` both PASS at my head.

**b. `.github/workflows/build-apk.yml` — push triggers.** Took the **union**: #66's two branch
triggers *and* #69's `'integration/**'`. These are additive and independent; dropping either would
silently stop CI for that branch family. The rest of #69's commit (release build + unsigned-release
upload) applied unchanged.

**Consequence worth flagging for the maintainer.** The rebase carried #66's `build/v2-brain-primary-v54`
CI bypass forward — the branch of the audit step that runs one `grep` *instead of* the whole
structural audit. #69's own base had already dropped it. Card B proved the bypass is unnecessary
(that branch passes the full audit unaided, exit 0). I have **deliberately not removed it here**:
it is #66's line, it is outside this card's scope, and removing it would mean this branch no longer
carries #66's content faithfully. It should be removed by a card that owns #66.

### Keystore deletion preserved

The single most important thing a rebase could have silently undone. `app/cortex-debug.keystore.b64`
(3556 bytes of real PKCS#12 base64, per Card B) is present at #69's original head and **absent** at
#66, which deleted it in `836e5a45` / `c2137e5e`. My head **preserves #66's deletion**:

```
$ git ls-files | grep -c 'cortex-debug.keystore'
0
AUDIT PASS: no encoded signing material tracked
```

This is also the only reason `app/cortex-debug.keystore.b64` appears in `git diff 3d542308 HEAD` —
it is the deletion being correctly inherited, not a change I made.

### Nothing was taken from #67 or #68

```
git merge-base --is-ancestor 4cfb4536 HEAD  ->  NOT-ANCESTOR   (#67)
git merge-base --is-ancestor 9f7e18d9 HEAD  ->  NOT-ANCESTOR   (#68)
```

No cherry-pick, no merge, no file copied. Every commit on my branch since #66 is listed in §7; nine
are #69's own, one is mine. Nothing was needed from those branches, so the card's stop-and-report
condition was never triggered.

### Fidelity check: the Relay payload is byte-identical to #69's

Object hashes compared at `3d542308` vs my head for all eight Relay classes plus both docs and both
scripts: **identical**. `git diff --name-only 3d542308 HEAD` lists only the 19 files that differ
because of #66's own content (plus my one security commit) — **no Relay source file appears in that
list**. The rebase preserved the change set exactly.

---

## 3. The five invariants, each with its evidence

Each was verified by reading the code at my head, and — where a control could be built — by making
the check fail on purpose.

### 3.1 Android caller UID + signer authentication on the Relay entry points — PRESERVED

`CortexLocalBusService.java:46` is the **first statement** of `handle(Message)`, before any payload
is read:

```java
CortexConnectorRegistryV1.Identity identity = CortexConnectorRegistryV1.resolve(this, msg.sendingUid);
if (identity == null) { logUnauthorised(msg.sendingUid); reply(msg, false, "UNAUTHORIZED_CALLER", ...); return true; }
```

`msg.sendingUid` is supplied by the Android kernel, not by the caller, so it cannot be spoofed from
the payload. `CortexConnectorRegistryV1.resolve` (`:17-30`) requires **both** that the UID maps to
package `com.kareem.secondbrain` **and** that `signerMatches` confirms the installed APK's signing
certificate hashes to `fd402eef…4b4c7d74` (`:14`). `signerMatches` (`:32-48`) uses
`GET_SIGNING_CERTIFICATES` on API ≥ 28 and compares SHA-256 of the signature bytes.

Order verified by reading: identity resolution precedes `msg.getData()` (`:57`), so an unregistered
caller's Bundle is never parsed. A second identity check exists at the payload layer — `:134` and
`:156` reject an event whose `connector_id` disagrees with the authenticated caller, and `:90`
rejects a HELLO whose claimed `connector_id` does not match the UID-derived one.

**Verified how:** read every entry path in `handle()`; there are exactly five message types and all
five sit below the `identity == null` guard.

### 3.2 Explicit V2 negotiation with working V1 fallback — PRESERVED

`CortexLocalBusService.handleHello:95-98`:

```java
boolean selectV2 = "second_brain".equals(identity.connectorId)
        && CortexLocalBusProtocolV2.relayAdvertisesSignalV2(relayCapabilities);
String selectedProtocol = selectV2 ? CortexLocalBusProtocolV2.SIGNAL_PROTOCOL : CortexLocalBusProtocolV1.PROTOCOL;
```

V2 is opt-in and **never** the default: `relayAdvertisesSignalV2` (`CortexLocalBusProtocolV2.java:35-43`)
returns true only if the client's capability array literally contains `CORTEX_SIGNAL_V2`; a malformed
array returns false. The negotiated result is echoed back in the HELLO ACK under
`KEY_SELECTED_PROTOCOL` (`CortexLocalBusService.java:287`), so selection is explicit on both sides.

V1 remains fully functional and unconditional: `MSG_PING` (`:52`) and `MSG_INGEST` (`:80`) require
no V2 session at all, and `handleV1Ingest` (`:129-143`) is unchanged from the V1 contract. If HELLO
throws, the failure ACK explicitly reports `CortexLocalBusProtocolV1.PROTOCOL` (`:124`) — it degrades
to V1 rather than leaving the protocol undefined. Conversely `MSG_INGEST_V2` without a negotiated
session is refused with `V2_NOT_NEGOTIATED` (`:146-150`).

**Verified how:** `CortexLocalBusV2RegressionTest.negotiatesOnlyWhenSignalV2IsAdvertised` pins all
three cases (advertised, not advertised, malformed) and compiles at my head under
`compileDebugAndroidTestJavaWithJavac`. `CortexLocalBusV1RegressionTest` still compiles unchanged.

### 3.3 Exact event IDs unchanged — PRESERVED

`CortexLocalBusProtocolV2.toCanonicalV1:92` copies the event id verbatim:

```java
v1.put("event_id", event.eventId);
```

No prefixing, hashing, namespacing or regeneration anywhere in the adapter. The id is bounded but
not rewritten at parse (`:56`, max 180 chars). Because dedupe (§3.4) keys on `event.eventId`, a V2
event and the same event replayed as V1 collide correctly — which is the entire point.

**Verified how:** `CortexLocalBusV2RegressionTest.adaptsV2ToCanonicalV1WithoutChangingEventIdentity:64`
asserts `assertEquals("sb_evt_v2_1", v1.eventId)` after a full parse → adapt round trip.

### 3.4 Canonical ingest / dedupe / ACK semantics — PRESERVED

**One ingest path.** Both `handleV1Ingest` (`:142`) and `handleV2Ingest` (`:165`) terminate in the
same `ingestCanonical(...)`. V2 does not get a private ingest route: `toCanonicalV1` re-serialises
the adapted object and re-parses it through `CortexLocalBusProtocolV1.parseEvent` (`:117`), so a V2
payload must satisfy the V1 validator to be admitted at all.

**Dedupe.** `ingestCanonical:177` calls `CortexLocalBusStoreV1.alreadyAccepted(db, event.eventId)`
before any admission, and a duplicate short-circuits to `DUPLICATE_ACCEPTED` carrying the
**original** `signal_id` looked up at `:178` / `:270-275` — idempotent, not merely rejected.

**ACK.** Every terminal branch replies exactly once, and every reply carries `KEY_EVENT_ID` and
`KEY_SIGNAL_ID` (`:293-305`), so the ACK is correlated to the event that caused it. The four
outcomes — `DUPLICATE_ACCEPTED` (`:184`), `ACCEPTED` (`:200`), rejection with the store's status
(`:205`), `INGEST_FAILED` (`:215`) — are mutually exclusive, and the failure path still records the
rejection (`:210`) so a crash cannot leave an event silently un-acked.

V2 evidence is preserved without disturbing any of this: the V2 semantic block, action capabilities
and a `local_bus_signal_v2` marker are nested under `metadata` (`CortexLocalBusProtocolV2.java:105-115`).

**Verified how:** traced all four exit paths by reading; `adaptsV2ToCanonicalV1…` asserts the
metadata nesting (`:72-77`); the audit's `Today reads canonical derived_items state` /
`no parallel attention/open-loop SQL truth store` assertions PASS, confirming no second truth store.

### 3.5 Explicit confirmation required for executable actions — PRESERVED

Executable actions reach Relay through exactly one caller. `CortexRelayBridgeV2.requestAction` is
invoked from precisely one site — `CortexRelayV2DiagnosticsActivity.java:193` — and that site is
inside the positive-button lambda of an `AlertDialog` (`:186`), reached only from `confirmAction`
(`:167`), which is reached only from a `Confirm & send to Relay` button (`:161-162`).

The dialog names the action, states "Cortex will ask Relay to execute this exact Android capability…
No other action will be chosen automatically", and offers Cancel (`:170-173`). Text-input actions
additionally refuse to proceed on empty input (`:188-191`). The screen's own header states that
nothing executes without explicit confirmation (`:71-73`).

Nothing auto-executes: I searched the whole `app/src` tree for `CortexRelayBridgeV2.` — the only
references are the four in `CortexLocalBusService` (session lifecycle and inbound recording, which
execute nothing) and the four in the diagnostics activity. There is no background worker, receiver
or service call site.

**Verified how:** exhaustive call-site search across `app/src`, then read the full path from button
to dialog to send.

---

## 4. The control-result correlation gap — CLOSED BY IMPLEMENTATION

This was a genuine gap, and the ambiguity the card warned about was real.

### The gap as found

At #69's head, `CortexLocalBusService:64-74` accepted `MSG_ACTION_RESULT` / `MSG_POLICY_RESULT` from
an authenticated V2 session and passed the raw JSON straight to
`CortexRelayBridgeV2.recordControlResult`, which wrote it **unconditionally** into
`last_action_result` / `last_policy_result` and logged it as `accepted`.

There was **no** request-id correlation at any layer:

- `MSG_ACTION_REQUEST` carried a `request_id` (`CortexLocalBusProtocolV2.actionRequest:128`), but
  nothing retained it, so an inbound result naming *any* id — or none — was recorded identically;
- `MSG_POLICY_UPDATE` had **no `request_id` field at all** (`mechanicalPolicy:140-153`), so a policy
  result was structurally incapable of being correlated;
- there was no replay bound: the same result message replayed N times overwrote the slot N times;
- and the channel was **not** diagnostics-only in a documented sense — the recorded value is read
  back by `diagnosticSnapshot` and rendered as "Relay execution result", i.e. as Cortex's belief
  about what happened, with nothing distinguishing a correlated result from an unsolicited claim.

So the diagnostics-only escape hatch was not honestly available: the boundary was undocumented and
the value was presented as authoritative. Per the card, that ambiguity is the finding — and the
requirement is to close it.

### What I implemented

New file `app/src/main/java/com/kareem/cortex/CortexRelayControlCorrelatorV2.java`, plus the
enforcement wiring in `CortexRelayBridgeV2`. Correlation is now a **precondition of authority**:

1. **Outstanding-request registry.** Every outbound control request registers its id *before* the
   message is sent (`CortexRelayBridgeV2.send`, the `registerOutstanding` guard). If the id is
   unusable or already used, the send is refused with `REQUEST_ID_NOT_CORRELATABLE` — so an
   uncorrelatable request is never issued in the first place.
2. **Policy requests now carry a request id.** `updateMechanicalPolicy` mints
   `cortex_policy_<UUID>`, closing the structural hole.
3. **Inbound gate.** `recordControlResult` calls `correlate(requestId, kind, now)` and returns one
   of five verdicts. Only `ACCEPTED_FIRST` — a live outstanding id, of the **same kind**, within the
   TTL, **not already answered** — may write `last_<kind>_result`. Everything else is written to a
   separate `last_<kind>_uncorrelated_result` key and can never be mistaken for authoritative state.
4. **Bounded replay/duplicate handling.** The id is consumed on acceptance and remembered in a
   bounded answered-set, so a replay yields `DUPLICATE_REPLAY`, not a second effect. Bounds:
   ≤ 64 outstanding (oldest evicted first), ≤ 256 answered ids retained, 10-minute TTL, request-id
   length capped at 180 chars. **Ids, kinds and timestamps only — no payload bytes are retained.**
5. **Session-scoped.** `clearAuthenticatedSession` resets all correlation state, so outstanding
   requests do not survive the authenticated session that minted them.
6. **Visible in the UI.** The diagnostics activity now renders uncorrelated results under
   "Uncorrelated results (diagnostic only)" with an explicit statement that they were **not** treated
   as authoritative, and the snapshot exposes `outstanding_requests`.

The trust boundary is stated in the class javadoc and at the gate in `recordControlResult`, so the
next reader cannot mistake the channel's status. **This is implementation, not documentation of a
diagnostics-only boundary** — the card's first option.

Also fixed in passing: the whole gate is Android-free, which is why Control 6 can compile and attack
the real production class on a plain JVM.

---

## 5. Gate output

All run by me at head `9d0095ae`, on the toolchain Card A published (Gradle 8.9 at
`work/cortex-pr-stack-20260831/toolchain/gradle-8.9`, JDK 17.0.18, `ANDROID_HOME=/home/anombyte/Android/Sdk`).
**Card A's recipe reproduced exactly** — `platforms;android-35` was already installed by A, and
`gradle --version` confirms 8.9 / JVM 17.0.18.

### Gate 1 — `bash scripts/cortex-repo-audit.sh` → exit 0

```
 ================ CORTEX REPO AUDIT ================
Commit: ee1c5bd92414        (audit shown at the rebase head; re-run at 9d0095ae in the controls below)
AUDIT PASS: scanned 332 tracked source/config files
AUDIT PASS: exactly one launcher intent
AUDIT PASS: V2 reconciliation versionCode is >=54
AUDIT PASS: automatic Android backup is disabled for private Cortex state
AUDIT PASS: phone-only self review is not externally triggerable
AUDIT PASS: Arabic OCR digest matches the pinned 4.1.0 asset
AUDIT PASS: build script never reconstructs signing material from tracked base64
AUDIT PASS: no encoded signing material tracked
AUDIT PASS: exactly one GitHub Actions workflow
AUDIT PASS: no tracked APK binaries
AUDIT WARN: 8 TODO/FIXME/placeholder source hit(s) require review
-----------------------------------------------------
Files scanned: 332  Warnings: 1  Failures: 0
CORTEX_REPO_AUDIT=PASS
EXIT=0
```

The one warning is pre-existing (`AUDIT WARN`, not a failure) and is present at #66 and #69 alike.
The controls run re-confirms exit 0 at the final head `9d0095ae`.

### Gate 2 — `bash -n` on every tracked shell script

```
OK scripts/build-local-snapshot-cognitive-relay-v2-v54.sh
OK scripts/build-sign-install-cognitive-relay-v2-candidate.sh
OK scripts/cortex-cognitive-major-validate.sh
OK scripts/cortex-repo-audit.sh
OK termux-build-cortex.sh
OK tools/preserve-v2-local-work.sh
OK tools/run-instrumented-self-user-test.sh
OK tools/run-phone-only-self-review.sh
```

Plus `bash -n` clean on the one shell script I authored (`evidence/relay-v2-pr69/negative-controls.sh`).

### Gate 3 — the CI build command

```
$ gradle :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --stacktrace
> Task :app:compileDebugJavaWithJavac
> Task :app:compileDebugAndroidTestJavaWithJavac
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 41s
49 actionable tasks: 49 executed
```

`compileDebugAndroidTestJavaWithJavac` compiles my new `CortexRelayControlCorrelatorV2Test`, so the
test source is proven to compile — see §6 for what that does **not** prove.

### Gate 4 — negative controls: 13/13, every control proven to fire

`evidence/relay-v2-pr69/negative-controls.sh`, output at `negative-controls-output.txt`:

```
BASELINE
  PASS  repo audit GREEN at head (exit 0)
CONTROL 1 — repo audit must fire when a second workflow appears
  PASS  audit went RED with two workflows (exit 2)
  PASS  audit restored GREEN after removal
CONTROL 2 — repo audit must fire when tracked encoded signing material returns
  PASS  audit went RED on tracked encoded signing material (exit 2)
  PASS  audit restored GREEN after removal
CONTROL 3 — bash -n must fire on a broken shell script
  PASS  bash -n went RED on truncated if
CONTROL 4 — build must fire when the correlation gate's test contract is violated
  PASS  androidTest compile went RED after removing KIND_MISMATCH
  PASS  androidTest compile restored GREEN
CONTROL 5 — the debug build must fire on a corrupt module build file
  PASS  assembleDebug went RED with a corrupt build.gradle
  PASS  assembleDebug restored GREEN
CONTROL 6 — the NEW correlation gate must refuse authority to every attack
  PASS  correlator compiles standalone (no Android dependency in the gate)
  ATTACK 1  PASS  forged request id refused authority (verdict=UNKNOWN_REQUEST)
  ATTACK 2  PASS  genuine first result granted authority (verdict=ACCEPTED_FIRST)
            PASS  replayed result refused authority (verdict=DUPLICATE_REPLAY)
            PASS  second replay still refused (verdict=DUPLICATE_REPLAY)
  ATTACK 3  PASS  empty request id refused / null request id refused
  ATTACK 4  PASS  cross-kind result refused authority (verdict=KIND_MISMATCH)
  ATTACK 5  PASS  expired request refused authority (verdict=UNKNOWN_REQUEST)
  ATTACK 6  PASS  outstanding set stayed bounded at 64 <= 64
  ATTACK 7  PASS  answered id could not be re-armed for a second authoritative result
  HARNESS SELF-CHECK
            FAIL  DELIBERATE control: forged id treated as authoritative (this MUST fail)
            (self-check fired correctly; discounted from totals)
  PASS  all correlation attacks refused authority, and the harness proved it can fail
FINAL TREE STATE
  PASS  tree is clean after all controls
=== RESULT: 13 passed, 0 failed ===
```

**Two controls did not fire on the first run, and that is the most useful result here.** I report
both rather than quietly fixing them:

- **Control 1 (second workflow) initially stayed GREEN.** Cause: the audit counts **tracked** files
  (`cortex-repo-audit.sh:180`, `git ls-files '.github/workflows/*.yml'`); my control had only created
  the file on disk. Staging it made the gate go RED (`expected one workflow, found 2`). **The gate is
  sound; my control was wrong.** Worth knowing: this gate cannot see an untracked workflow file.
- **Control 2 (keystore b64) stayed GREEN even after `git add`.** Cause: `.gitignore:26` carries
  `*keystore*.b64`, so the `git add` was silently refused. With `git add -f` the audit goes RED
  (exit 2). Two independent defences in series — `.gitignore` prevents the mistake, the audit
  catches it if forced. Both verified live.

Control 6's harness contains a deliberate self-check that must fail; it fired, proving the harness
is capable of reporting failure rather than passing vacuously.

**Unplanned seventh control — the audit went RED on my own commit.** After committing this report,
`cortex-repo-audit.sh` returned **exit 2, `CORTEX_REPO_AUDIT=FAIL`**:

```
AUDIT FAIL: merge-conflict marker in deliverables/cortex-pr-stack-20260831/relay-v2/pr69-rebase-report.md
Files scanned: 335  Warnings: 1  Failures: 1
```

Cause: the audit's conflict-marker scan (`cortex-repo-audit.sh:24`) matches any line beginning
`=======`, and the pasted Gate 1 banner above (`====… CORTEX REPO AUDIT …====`) is such a line. It
was a false positive on quoted evidence, **not** a real conflict marker — but it is the most
valuable control in this report, because I did not design it: the gate fired unprompted, on my own
work, and blocked a commit. Resolved by indenting that one banner line by a single space; the
pasted output is otherwise verbatim. The audit returns exit 0 at the final head.

This also documents a real property of the gate for future workers: **the audit cannot distinguish a
conflict marker from quoted output that looks like one**, so any report committed into this repo
must not begin a line with `=======`, `<<<<<<<` or `>>>>>>>`.

### Gate 5 — device/acceptance evidence

**I ran no device validation, and none of the above implies any.** No Android device, emulator,
Termux host or Shizuku session was reachable from this box. Everything above is static analysis,
compilation and JVM-level behavioural testing.

What a real-device run would still have to prove, precisely:

1. That an actual `com.kareem.secondbrain` build signed with a **different** certificate is refused
   by `signerMatches` — the hash comparison is proven by reading, never by a failed bind.
2. That `msg.sendingUid` on a real binder transaction resolves to the expected package (the UID →
   package mapping is `PackageManager` behaviour I stubbed nothing for).
3. That a real Relay performs the V2 HELLO handshake and receives `selected_protocol` correctly, and
   that a genuine V1-only Relay still ingests.
4. That end-to-end dedupe holds against the real `VaultDb` — `alreadyAccepted` and the
   `connector_ingest_events` query were read, not executed; no SQLite ran.
5. That my correlation gate behaves correctly against a **real** Relay round trip — Control 6 drives
   the correlator directly, which proves the algorithm but not the `SharedPreferences` persistence,
   the service threading, or the real request→result timing.
6. That the confirmation dialog actually blocks execution on a real screen (the code path is proven;
   the rendered refusal is not).
7. The instrumented suite (`androidTest`) has been **compiled, never executed** — no
   `connectedDebugAndroidTest` ran. A compiling test is not a passing test.

---

## 6. What I could NOT prove

- **No test was executed.** `compileDebugAndroidTestJavaWithJavac` compiles the instrumented sources;
  it does not run them. My correlator assertions were separately *executed* on a plain JVM via
  Control 6, so the algorithm is behaviourally proven — but `CortexRelayControlCorrelatorV2Test`
  itself has only ever been compiled.
- **Nothing on a device** — see Gate 5's seven-point list.
- **Upstream CI was never triggered.** The repo is pull-only; Gate 3 is a local reproduction of the
  exact CI command, not a CI run. #69's new `:app:assembleRelease` step and the unsigned-release
  upload have not executed anywhere. I did not run `assembleRelease` locally.
- **Whether the maintainer wants #69's `versionName` or #66's.** I chose #69's with a stated reason;
  it is a judgement call on a one-line conflict and is trivially reversible.
- **The `build/v2-brain-primary-v54` CI bypass is still present**, inherited from #66 by design (§2).
  It should be removed by a card owning #66.
- **Whether the committed keystore at other heads is still live** — I did not decode it, and Card B
  deliberately did not either. My branch does not carry it.
- **Relay's own side of the contract.** Everything here is Cortex-side. Whether Relay actually echoes
  `request_id` in its result messages is **unverified** — and it matters: if Relay does not echo it,
  every result will now correctly land as `MISSING_REQUEST_ID`/diagnostic rather than authoritative.
  That is the safe failure direction, but it means **the Relay side may need a corresponding change**,
  and a device round trip is the only thing that will reveal it. This is the single most important
  follow-up.

## 7. Commits on this branch since #66

```
9d0095ae  Astra worker (card C)  security(relay-v2): correlate Relay control results to outstanding request ids
ee1c5bd9  KAN1409  integration: preserve local manifest and Advanced UI in v54 snapshot
3ce30682  KAN1409  integration: pin local snapshot overlay to CI-validated Relay code
a838915e  KAN1409  integration: fix local snapshot version patch
766f2dbf  KAN1409  integration: add non-destructive local v53 snapshot builder for Relay v54
e69d46af  KAN1409  ci: validate v54 integration debug and release paths
66d5b4f8  KAN1409  integration: update signer-guarded installer for v54
dbe7edd5  KAN1409  integration: expose Relay V2 diagnostics in Advanced
0b16c4e9  KAN1409  integration: declare Relay V2 bridge components
32d00b45  KAN1409  integration: version combined cognitive Relay candidate v54
39484ac7  KAN1409  integration: port Relay V2 bridge onto current cognitive head
```

## 8. Evidence store

`/home/anombyte/atlas/work/cortex-pr-stack-20260831/evidence/relay-v2-pr69/`

| File | What it is |
|---|---|
| `negative-controls.sh` | Gate 4. Six controls, each breaking one invariant and asserting RED then GREEN. Asserts a clean tree at the end. |
| `negative-controls-output.txt` | Captured run: 13/13. |
| `CorrelatorAttack.java` | Control 6's adversarial harness against the real compiled correlator, including its own must-fail self-check. |

Reproduce: `bash evidence/relay-v2-pr69/negative-controls.sh` (override `RELAY_C_WORKTREE` /
`RELAY_C_GRADLE` if paths differ).

## 9. Capability discoveries (capability_map returned UNKNOWN)

- **A negative control must attack the gate the way the gate actually observes the world.** Two of
  six controls failed first time because the audit reads `git ls-files` (tracked state) while my
  controls wrote to the working tree. A control that misses the observation mechanism produces a
  false GREEN that looks exactly like a passing gate. Always confirm *how* the gate reads state
  before asserting it cannot fail.
- **A control that fails is a result, not an error.** Both first-run failures were my controls, not
  the gates — but the same signal would have been the only warning if a gate had genuinely been
  toothless. Reporting them is what makes the other eleven credible.
- **Build a self-check into any adversarial harness.** Control 6 deliberately asserts something false
  and confirms the failure registers. Without it, a harness whose assertions silently no-op reports
  a perfect score.
- **Base drift reads as disagreement.** `CONFLICTING` on #69 was 33 commits of parent movement, not
  authorial conflict. Computing the merge base before resolving anything turned an 18-file scare into
  two one-line conflicts.
- **A rebase can silently resurrect deleted security material.** #66 deleted the committed keystore;
  #69's line still carried it. The deletion had to be explicitly verified as preserved after the
  rebase — an audit assertion (`no encoded signing material tracked`) is what makes that checkable
  rather than a matter of trust.
