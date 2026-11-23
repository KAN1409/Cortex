# Cortex Prime — Milestone 1 (Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An installable Cortex Prime APK with the database, a resilient job engine, the premium glass shell (Now and Memory), text-idea capture, share-in capture, and an honest Memory timeline.

**Architecture:** A standalone Gradle project in `prime/`. Everything captured becomes an immutable `Evidence` row plus an original file. Processing runs through a lease-based `job` table with per-item time limits, executed by one WorkManager worker. The UI is Compose and reads Room `Flow`s through small view models. Wiring is by hand, through one `AppGraph`.

**Tech Stack:** Kotlin 2.2.21 · AGP 8.10.1 · Gradle 8.11.1 · Compose BOM 2025.05.01 · Room 2.8.5 (KSP 2.2.21-2.0.5) · WorkManager 2.11.2 · JUnit 4.13.2 · Robolectric 4.14.1. All of these are versions this repo's CI already builds with.

**Spec:** `docs/superpowers/specs/2026-11-23-cortex-prime-design.md`

## Global Constraints

- Package and applicationId: `com.kareem.cortex.prime`. App label: `Cortex Prime`. Installs alongside the old app.
- minSdk 29, compileSdk 35, targetSdk 35, JVM target 17.
- No dependency-injection framework: constructor wiring through `AppGraph`.
- No network permission in milestone 1. Fonts come from the Google Fonts provider (`androidx.compose.ui:ui-text-google-fonts`), with a system fallback.
- Interface copy is in English. Arabic content is rendered RTL, with Readex Pro as the Arabic font.
- Originals are immutable. Corrections never overwrite history.
- Status must never lie: a state is shown only if the code computed it.
- No visible control without a working implementation behind it. Ideas and Ask stay hidden until their milestones.
- Colours, motion and the brain follow spec §7 exactly. Reduce-motion (system setting or app override) stops all ambient motion.
- Every Gradle command runs from `prime/`.
- **Zero cost** (spec §3.1): add only free, openly licensed or free-of-charge components. No paid APIs, keys, assets, ad or analytics SDKs. Each new dependency's licence goes in `prime/THIRD_PARTY.md` in the task that adds it.

## Review Focus

1. **The same file shared twice** → the existing evidence is returned (SHA-256 match) with no duplicate row or file. Test in Task 3.
2. **A very large shared file** (> 200 MB) → refused with "That file is too large for Cortex (limit 200 MB)", streamed rather than loaded into memory, and no partial file left behind. Test in Task 3.
3. **The app is killed mid-job** → the lease expires and the job is claimable again on the next run, with the attempt counted. Test in Task 4.
4. **A share that has neither text nor a supported file** → nothing saved, and the user sees "Cortex can't save this kind of item yet". Test in Task 7.
5. **An empty or whitespace-only idea** → Save is disabled and nothing is stored. Test in Task 7.

---

## File Structure (`prime/`)

```
prime/
  settings.gradle, build.gradle, gradle.properties
  app/build.gradle
  app/cortex-prime-debug.keystore          # fixed key so every CI build installs as an update
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/kareem/cortex/prime/
    CortexPrimeApp.kt                      # Application: builds AppGraph
    AppGraph.kt                            # hand wiring
    data/db/  CortexDatabase.kt, Entities.kt, EvidenceDao.kt, PerceptionDao.kt, JobDao.kt
    data/evidence/  EvidenceRepository.kt, OriginalStore.kt
    jobs/  JobQueue.kt, JobRunner.kt, StageHandler.kt, JobWorker.kt, TextPerceiveHandler.kt
    capture/  ShareParser.kt, ShareReceiverActivity.kt
    ui/theme/  Tokens.kt, Type.kt, Glass.kt, Motion.kt
    ui/brain/  BrainHero.kt
    ui/shell/  MainActivity.kt, PrimeShell.kt, GlassNavBar.kt
    ui/now/  NowScreen.kt
    ui/memory/  MemoryScreen.kt, MemoryViewModel.kt, ProcessingState.kt
    ui/capture/  IdeaSheet.kt
    ui/settings/  Prefs.kt, FirstRunNameSheet.kt
  app/src/main/res/raw/brain_loop.webp     # animated transparent WebP (spec §7.3)
  app/src/test/java/com/kareem/cortex/prime/...   # JVM + Robolectric tests
.github/workflows/prime-build.yml
```

---

### Task 1: Project scaffold, signing and CI

**Files:**
- Create: `prime/settings.gradle`, `prime/build.gradle`, `prime/gradle.properties`, `prime/app/build.gradle`, `prime/app/src/main/AndroidManifest.xml`, `prime/app/src/main/java/com/kareem/cortex/prime/CortexPrimeApp.kt`, `prime/app/cortex-prime-debug.keystore`, `.github/workflows/prime-build.yml`
- Test: `prime/app/src/test/java/com/kareem/cortex/prime/BuildIdentityTest.kt`

**Interfaces:**
- Produces: `BuildConfig.APPLICATION_ID == "com.kareem.cortex.prime"`. CI artifact `Cortex-Prime-debug` holding `app-debug.apk`.

- [ ] **Step 1: Write the failing test**
```kotlin
class BuildIdentityTest {
    @Test fun applicationIdIsSideBySidePackage() =
        assertEquals("com.kareem.cortex.prime", BuildConfig.APPLICATION_ID)
}
```
- [ ] **Step 2: Create the Gradle files.** Plugins: `com.android.application 8.10.1`, `org.jetbrains.kotlin.android 2.2.21`, `org.jetbrains.kotlin.plugin.compose 2.2.21`, `com.google.devtools.ksp 2.2.21-2.0.5`. Repositories: google() and mavenCentral() only. `buildFeatures { compose true; buildConfig true }`, and `testOptions { unitTests.includeAndroidResources = true }`. Dependencies: Compose BOM 2025.05.01 (ui, foundation, material3, ui-text-google-fonts), activity-compose 1.10.1, lifecycle-viewmodel-compose and runtime-compose 2.9.0, room-runtime and room-ktx 2.8.5 plus `ksp room-compiler 2.8.5`, work-runtime-ktx 2.11.2, kotlinx-coroutines-android 1.9.0. Test: junit 4.13.2, robolectric 4.14.1, androidx.test:core 1.6.1, kotlinx-coroutines-test 1.9.0, room-testing 2.8.5, work-testing 2.11.2.
- [ ] **Step 3: Generate the fixed debug key.** `keytool -genkeypair -keystore prime/app/cortex-prime-debug.keystore -storepass android -keypass android -alias cortexprime -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Cortex Prime Debug"`. Wire it as the `debug` signingConfig.
- [ ] **Step 4: Add `CortexPrimeApp` (an empty Application)** and the manifest with an `application` entry only. No INTERNET permission.
- [ ] **Step 5: Add `.github/workflows/prime-build.yml`.** Triggers: push on `paths: prime/**` (any branch), plus workflow_dispatch. Steps: JDK 17 (temurin), `gradle/actions/setup-gradle@v4` with `gradle-version: '8.11.1'`, then `gradle -p prime :app:testDebugUnitTest :app:assembleDebug --stacktrace`, then upload `prime/app/build/outputs/apk/debug/app-debug.apk` as `Cortex-Prime-debug`.
- [ ] **Step 6: Push and dispatch the workflow.** Expected: the run is green, `BuildIdentityTest` passes, and the artifact exists. This environment has no Android SDK, so CI is the test runner for every task.
- [ ] **Step 7: Commit:** `feat(prime): scaffold Cortex Prime app, fixed debug key and CI`

### Task 2: Database (evidence, perception, job)

**Files:**
- Create: `data/db/Entities.kt`, `EvidenceDao.kt`, `PerceptionDao.kt`, `JobDao.kt`, `CortexDatabase.kt`
- Test: `data/db/CortexDatabaseTest.kt` (Robolectric, in-memory)

**Interfaces:**
- Produces:
  - `enum class EvidenceKind { TEXT, IMAGE, PDF, AUDIO, LINK }`
  - `@Entity("evidence") data class Evidence(id: Long = 0, kind: EvidenceKind, originalPath: String?, text: String?, sha256: String /*unique index*/, mime: String?, sizeBytes: Long, sourceApp: String?, capturedAt: Long)`
  - `@Entity("perception") data class Perception(id: Long = 0, evidenceId: Long, kind: String /*"text"|"ocr"|"transcript"*/, text: String, language: String?, confidence: Double, engine: String, createdAt: Long)`
  - `enum class JobState { WAITING, RUNNING, DONE, FAILED }`
  - `@Entity("job", indices=[stage,state,nextRunAt]) data class Job(id: Long = 0, stage: String, evidenceId: Long, state: JobState, attempts: Int, leaseUntil: Long, lastError: String?, nextRunAt: Long, updatedAt: Long)` with a unique index on (stage, evidenceId)
  - `EvidenceDao`: `insert(e): Long` (IGNORE), `bySha(sha): Evidence?`, `byId(id): Evidence?`, `timeline(): Flow<List<Evidence>>` (newest first)
  - `PerceptionDao`: `insert(p): Long`, `forEvidence(id): List<Perception>`
  - `JobDao`: `insert(j): Long` (IGNORE), `byId(id): Job`, `forEvidence(evidenceId: Long): Job?` (stage `perceive`), plus the claim/update queries Task 4 declares
  - `CortexDatabase.build(context, inMemory: Boolean = false)`, database name `cortex_prime.db`, `exportSchema = true` into `prime/app/schemas`

- [ ] **Step 1: Write the failing tests**
```kotlin
@Test fun duplicateShaIsIgnored() { val a = ev(sha="x"); assertTrue(dao.insert(a) > 0); assertEquals(-1L, dao.insert(a.copy(capturedAt=2))) }
@Test fun timelineIsNewestFirst() = runTest { dao.insert(ev("a", t=1)); dao.insert(ev("b", t=2)); assertEquals(listOf("b","a"), dao.timeline().first().map { it.sha256 }) }
@Test fun oneJobPerStageAndEvidence() { assertTrue(jobs.insert(job("perceive",1)) > 0); assertEquals(-1L, jobs.insert(job("perceive",1))) }
```
- [ ] **Step 2: Push and run in CI.** Expected: FAIL (the classes don't exist).
- [ ] **Step 3: Implement the entities, DAOs and database** to the interfaces above. Enums use Room's built-in enum-name conversion.
- [ ] **Step 4: Run in CI.** Expected: PASS, and `prime/app/schemas/.../1.json` is generated and committed.
- [ ] **Step 5: Commit:** `feat(prime): Room database for evidence, perception and jobs`

### Task 3: Evidence capture repository

**Files:**
- Create: `data/evidence/OriginalStore.kt`, `data/evidence/EvidenceRepository.kt`
- Test: `data/evidence/EvidenceRepositoryTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `EvidenceDao`, and `JobQueue.enqueue(stage, evidenceId)` (Task 4; a fake is fine here)
- Produces:
  - `sealed interface CaptureResult { data class Saved(id: Long, duplicate: Boolean); data class Rejected(message: String) }`
  - `class EvidenceRepository(dao, store: OriginalStore, jobs: JobQueue, clock: () -> Long)`
    - `suspend fun captureText(text: String, sourceApp: String?): CaptureResult`
    - `suspend fun captureStream(open: () -> InputStream, mime: String, sourceApp: String?): CaptureResult`
  - `class OriginalStore(root: File)`: `fun write(open: () -> InputStream, maxBytes: Long): StoredFile` (streams to a temp file while computing SHA-256, then renames to `originals/<sha>`; throws `TooLargeException` past `maxBytes` and deletes the temp file)
  - `const val MAX_CAPTURE_BYTES = 200L * 1024 * 1024`
- `captureText` trims the text; a blank text gives `Rejected("Nothing to save")`. Its SHA-256 is over the UTF-8 trimmed text. Each new evidence enqueues `perceive`.

- [ ] **Step 1: Write the failing tests**
```kotlin
@Test fun sameFileTwiceReturnsExisting() = runTest {
  val a = repo.captureStream({ bytes(1024).inputStream() }, "image/png", "Gallery") as CaptureResult.Saved
  val b = repo.captureStream({ bytes(1024).inputStream() }, "image/png", "Gallery") as CaptureResult.Saved
  assertEquals(a.id, b.id); assertTrue(b.duplicate); assertEquals(1, store.root.resolve("originals").list()!!.size)
}
@Test fun oversizedFileIsRefusedAndLeavesNothing() = runTest {
  val r = repo.captureStream({ ZeroStream(MAX_CAPTURE_BYTES + 1) }, "video/mp4", null)
  assertEquals(CaptureResult.Rejected("That file is too large for Cortex (limit 200 MB)"), r)
  assertTrue(store.root.walk().none { it.isFile })
}
@Test fun blankTextIsRejected() = runTest { assertEquals(CaptureResult.Rejected("Nothing to save"), repo.captureText("  \n ", null)) }
@Test fun newEvidenceEnqueuesPerceive() = runTest { val s = repo.captureText("call mum", null) as CaptureResult.Saved; assertEquals(listOf("perceive" to s.id), fakeJobs.enqueued) }
```
`ZeroStream(n)` is a test `InputStream` that yields `n` zero bytes without allocating them.
- [ ] **Step 2: Run in CI.** Expected: FAIL.
- [ ] **Step 3: Implement `OriginalStore.write` and `EvidenceRepository`.** The kind comes from the MIME type: `image/*` → IMAGE, `application/pdf` → PDF, `audio/*` → AUDIO.
- [ ] **Step 4: Run in CI.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat(prime): immutable evidence capture with dedupe and size limit`

### Task 4: Job engine (leases, time limits, backoff)

**Files:**
- Create: `jobs/JobQueue.kt`, `jobs/StageHandler.kt`, `jobs/JobRunner.kt`, `jobs/JobWorker.kt`, `jobs/TextPerceiveHandler.kt`; modify `data/db/JobDao.kt`
- Test: `jobs/JobQueueTest.kt`, `jobs/JobRunnerTest.kt` (Robolectric, fake clock)

**Interfaces:**
- Produces:
  - `class JobQueue(dao: JobDao, clock: () -> Long)`:
    - `suspend fun enqueue(stage: String, evidenceId: Long)` (idempotent)
    - `suspend fun claim(stage: String, leaseMs: Long): Job?` (atomic: WAITING with `nextRunAt <= now`, or RUNNING with an expired lease → RUNNING, `attempts + 1`, `leaseUntil = now + leaseMs`)
    - `suspend fun complete(id)`
    - `suspend fun fail(id, error: String)` (`attempts < 3` → WAITING, `nextRunAt = now + 30s · 2^(attempts-1)`; otherwise FAILED)
    - `fun stateFor(evidenceId): Flow<Map<String, Job>>`
  - `interface StageHandler { val stage: String; val timeLimitMs: Long; suspend fun run(evidenceId: Long) }`
  - `class JobRunner(queue: JobQueue, handlers: List<StageHandler>)`: `suspend fun drain(maxJobs: Int = 50): Int` runs each claimed job inside `withTimeout(handler.timeLimitMs)`. On a timeout it calls `fail(id, "timeout")`; on any other exception, `fail(id, message)`. It returns the number processed.
  - `class JobWorker` (CoroutineWorker): calls `AppGraph.runner.drain()`. Unique work name `prime-jobs`, enqueued with `ExistingWorkPolicy.APPEND_OR_REPLACE` after each capture.
  - `class TextPerceiveHandler(evidenceDao, perceptionDao)`: stage `"perceive"`, limit 5 s. For TEXT evidence it writes `Perception(kind="text", engine="identity", confidence=1.0)`. For other kinds it throws `NotYetSupported`, which the runner treats as a **park**: the job returns to WAITING with `nextRunAt = Long.MAX_VALUE` and `lastError = "not_supported_yet"`, and its `attempts` is set back to its value before the claim, so parking never counts as a try.

- [ ] **Step 1: Write the failing tests**
```kotlin
@Test fun expiredLeaseIsReclaimed() = runTest { q.enqueue("perceive",1); val j=q.claim("perceive",1_000)!!; clock+=1_001; val again=q.claim("perceive",1_000)!!; assertEquals(j.id,again.id); assertEquals(2,again.attempts) }
@Test fun liveLeaseIsNotDoubleClaimed() = runTest { q.enqueue("perceive",1); q.claim("perceive",1_000); assertNull(q.claim("perceive",1_000)) }
@Test fun threeFailuresEndFailed() = runTest { repeat(3){ val j=q.claim("perceive",1_000)!!; q.fail(j.id,"boom"); clock+=10*60_000 }; assertEquals(JobState.FAILED, dao.byId(1).state) }
@Test fun slowHandlerTimesOutAndQueueMovesOn() = runTest { /* handler A sleeps past its 100 ms limit for evidence 1; evidence 2 is fast */ runner.drain(); assertEquals("timeout", dao.forEvidence(1).lastError); assertEquals(JobState.DONE, dao.forEvidence(2).state) }
@Test fun unsupportedKindIsParkedNotFailed() = runTest { /* IMAGE evidence */ runner.drain(); val j=dao.forEvidence(img); assertEquals(JobState.WAITING,j.state); assertEquals("not_supported_yet",j.lastError); assertEquals(0,j.attempts) }
```
- [ ] **Step 2: Run in CI.** Expected: FAIL.
- [ ] **Step 3: Implement.** Do `claim` as a Room `@Transaction` (select, then a conditional update that checks the row still matches) so two workers can't take the same job.
- [ ] **Step 4: Run in CI.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat(prime): lease-based job engine with per-item time limits`

### Task 5: Design tokens, glass and motion

**Files:**
- Create: `ui/theme/Tokens.kt`, `Type.kt`, `Glass.kt`, `Motion.kt`
- Test: `ui/theme/TokensTest.kt` (JVM)

**Interfaces:**
- Produces:
  - `object CortexColors`: `Bg=0xFF000000, TextPrimary=0xFFF4F6FF, TextSecondary=0xFFC4CAE0, Label=0xFF8C95B5, Blue=0xFF4D7CFF, Violet=0xFF7A5CFF, Gold=0xFFE8C26A, SheenA=0xFF7FE7FF, SheenB=0xFF8FB0FF, ActionTop=0xFF5A86FF, ActionBottom=0xFF2F5BFF`
  - `fun contrastRatio(fg: Long, bg: Long): Double`
  - `Modifier.glass(shape: Shape = RoundedCornerShape(28.dp))`: an 8.5% → 2.5% white vertical gradient, a 1 dp top highlight at 16% white, a bottom edge at 40% black, and a shadow of elevation 24 dp with a 65% black spot colour.
  - `Modifier.lightBleed(color: Color, breathing: Boolean)`: a radial glow drawn behind the content, alpha 0.55 → 1.0 over 5 s while `breathing`.
  - `@Composable fun HorizonBackground()`: the planet-rim glow from spec §7.1.
  - `CortexType`: Geist, Geist Mono and Readex Pro from the Google Fonts provider, falling back to the default system fonts.
  - `@Composable fun rememberMotionEnabled(): Boolean`: false if the system animator scale is 0 or `Prefs.reduceMotion` is on.
  - `fun Modifier.staggeredRise(index: Int, enabled: Boolean)`: translate 22 dp → 0, alpha 0 → 1, 1000 ms, delay `index * 150 ms`, FastOutSlowIn. A plain fade when not enabled.

- [ ] **Step 1: Write the failing test**
```kotlin
@Test fun textMeetsContrastOnBlack() {
  assertTrue(contrastRatio(CortexColors.TextPrimary, CortexColors.Bg) >= 4.5)
  assertTrue(contrastRatio(CortexColors.TextSecondary, CortexColors.Bg) >= 4.5)
  assertTrue(contrastRatio(CortexColors.Label, CortexColors.Bg) >= 4.5)
}
```
- [ ] **Step 2: Run in CI.** Expected: FAIL.
- [ ] **Step 3: Implement** `contrastRatio` (WCAG relative luminance) and the modifiers and composables above.
- [ ] **Step 4: Run in CI.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat(prime): glass, glow, type and motion tokens`

### Task 6: Shell, Now screen and the brain

**Files:**
- Create: `ui/brain/BrainHero.kt`, `ui/shell/MainActivity.kt`, `PrimeShell.kt`, `GlassNavBar.kt`, `ui/now/NowScreen.kt`, `ui/settings/Prefs.kt`, `FirstRunNameSheet.kt`, `AppGraph.kt`, `res/raw/brain_loop.webp`; modify the `CortexPrimeApp.kt` and manifest (launcher activity)
- Test: `ui/settings/PrefsTest.kt`, `ui/now/GreetingTest.kt` (JVM / Robolectric)

**Interfaces:**
- Consumes: Tasks 2–5
- Produces:
  - `class Prefs(context)`: `var displayName: String?`, `var reduceMotion: Boolean`
  - `fun greeting(hour: Int, name: String?): String` returns "Good morning" (05–11), "Good afternoon" (12–16) or "Good evening" (17–04), followed by `", $name"` when the name isn't blank.
  - `enum class Tab { NOW, MEMORY }` (Ideas and Ask are added in milestones 5 and 6)
  - `@Composable fun BrainHero(sizeDp: Dp, animate: Boolean)`: decodes `R.raw.brain_loop` with `ImageDecoder` into an `AnimatedImageDrawable` with infinite repeats, starting it only when `animate` and showing frame 0 otherwise. A slow 6 s float of ±6 dp, plus drifting particles drawn with `Canvas`.
- **Brain asset:** regenerate `brain_loop.webp` from the spec §7.3 render as an **animated** transparent WebP: 360×300 px frames, the 24 frames played forward then backward (48 frames), 230 ms per frame. It's produced by `tools/brain/render_brain.py`, which is committed alongside it (the fsaverage mesh via nilearn plus the cerebellum and brainstem additions).
- **Now in milestone 1** shows what is true today: the header (initial plus date), the brain hero with the greeting, then a "Recently captured" glass stack of the last 3 evidence items, then one honest line: "Reminders start once Cortex can read your voice notes and screenshots." The centre nav button opens the Idea sheet (Task 7).

- [ ] **Step 1: Write the failing tests**
```kotlin
@Test fun greetingByHour() { assertEquals("Good morning, Kareem", greeting(9,"Kareem")); assertEquals("Good evening", greeting(22,null)); assertEquals("Good afternoon", greeting(13," ")) }
@Test fun prefsPersistName() { prefs.displayName = "Kareem"; assertEquals("Kareem", Prefs(ctx).displayName) }
```
- [ ] **Step 2: Run in CI.** Expected: FAIL.
- [ ] **Step 3: Implement** the shell, Now, the nav bar (glass, two tabs plus the centre glossy button), the first-run name sheet (shown while `displayName == null`, with a Skip option), and `BrainHero`. Generate and commit the asset and its script.
- [ ] **Step 4: Run in CI.** Expected: PASS, and the APK builds.
- [ ] **Step 5: Commit:** `feat(prime): glass shell, Now screen and animated brain`

### Task 7: Capture — idea sheet and share-in

**Files:**
- Create: `capture/ShareParser.kt`, `capture/ShareReceiverActivity.kt`, `ui/capture/IdeaSheet.kt`; modify the manifest
- Test: `capture/ShareParserTest.kt` (Robolectric), `ui/capture/IdeaSheetStateTest.kt` (JVM)

**Interfaces:**
- Consumes: `EvidenceRepository` (Task 3), `JobWorker` enqueue (Task 4)
- Produces:
  - `sealed interface ShareItem { data class Text(value: String); data class Stream(uri: Uri, mime: String) }`
  - `object ShareParser { fun parse(intent: Intent, resolveMime: (Uri) -> String?): List<ShareItem> }`. It handles `ACTION_SEND` (EXTRA_TEXT and/or EXTRA_STREAM) and `ACTION_SEND_MULTIPLE` (EXTRA_STREAM list). Supported MIME types: `image/*`, `application/pdf`, `audio/*`, `text/plain`. Anything else is dropped.
  - `ShareReceiverActivity`: a translucent activity with no UI. It captures each item, then shows a toast: "Saved to Cortex", "Already in Cortex" (if every item was a duplicate), the Rejected message, or "Cortex can't save this kind of item yet" when the parser returns nothing. Then it finishes. The manifest filter covers SEND and SEND_MULTIPLE for the four MIME families.
  - `fun ideaSaveEnabled(text: String): Boolean = text.isNotBlank()`
  - `IdeaSheet`: a glass bottom sheet that slides up (spring). It has a multi-line field (auto RTL for Arabic) and Save, which calls `captureText` and dismisses.

- [ ] **Step 1: Write the failing tests**
```kotlin
@Test fun sendTextIsText() = assertEquals(listOf(ShareItem.Text("hi")), ShareParser.parse(send(text="hi")) { null })
@Test fun sendMultipleImages() = assertEquals(2, ShareParser.parse(sendMultiple(uriA, uriB)) { "image/png" }.size)
@Test fun unsupportedMimeYieldsNothing() = assertTrue(ShareParser.parse(send(stream=uriZip)) { "application/zip" }.isEmpty())
@Test fun blankIdeaCannotBeSaved() { assertFalse(ideaSaveEnabled("   ")); assertTrue(ideaSaveEnabled("call mum")) }
```
- [ ] **Step 2: Run in CI.** Expected: FAIL.
- [ ] **Step 3: Implement** the parser, the receiver activity, the manifest filters and the sheet.
- [ ] **Step 4: Run in CI.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat(prime): idea capture and share-in`

### Task 8: Honest Memory timeline

**Files:**
- Create: `ui/memory/ProcessingState.kt`, `MemoryViewModel.kt`, `MemoryScreen.kt`
- Test: `ui/memory/ProcessingStateTest.kt` (JVM), `ui/memory/MemoryViewModelTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `EvidenceDao.timeline()`, `JobQueue.stateFor()`, `PerceptionDao`
- Produces:
  - `enum class ProcessingLabel(val text: String) { WAITING("Waiting"), READING("Reading"), DONE("Done"), FAILED("Couldn't read"), LATER("Saved · reading comes in a later update") }`
  - `fun processingLabel(job: Job?): ProcessingLabel`:
    - no job → DONE
    - WAITING with `lastError == "not_supported_yet"` → LATER
    - WAITING → WAITING; RUNNING → READING; DONE → DONE; FAILED → FAILED
  - `data class MemoryRow(id: Long, kind: EvidenceKind, title: String, preview: String?, capturedAt: Long, label: ProcessingLabel, error: String?)`, where `title` is the first line of the text (≤ 60 chars) or the kind's name plus the time.
  - `MemoryViewModel.rows: StateFlow<Map<LocalDate, List<MemoryRow>>>` (grouped by local day, newest first)
  - `MemoryScreen`: day headers, glass rows with staggered rise, the state chip, and a **Retry** button on FAILED rows that re-enqueues `perceive`. Arabic previews render RTL.

- [ ] **Step 1: Write the failing tests**
```kotlin
@Test fun parkedJobIsLaterNotFailed() = assertEquals(ProcessingLabel.LATER, processingLabel(job(WAITING, err="not_supported_yet")))
@Test fun failedShowsCouldntRead() = assertEquals("Couldn't read", processingLabel(job(FAILED, err="timeout")).text)
@Test fun rowsGroupByDayNewestFirst() = runTest { /* two items today, one yesterday */ val g = vm.rows.first { it.isNotEmpty() }; assertEquals(listOf(today, yesterday), g.keys.toList()); assertEquals(2, g[today]!!.size) }
```
- [ ] **Step 2: Run in CI.** Expected: FAIL.
- [ ] **Step 3: Implement** the label mapping, the view model (combining the two flows) and the screen.
- [ ] **Step 4: Run in CI.** Expected: PASS, the APK builds, and the artifact `Cortex-Prime-debug` is available.
- [ ] **Step 5: Commit:** `feat(prime): honest Memory timeline`
- [ ] **Step 6: Owner phone check.** Install the APK. Then:
  1. Enter your name.
  2. Add an idea in Arabic.
  3. Share a screenshot from Gallery, then share the same one again.
  4. Confirm Memory shows the idea as Done, the screenshot as "Saved · reading comes in a later update", and "Already in Cortex" on the second share.
  5. Turn on reduce motion and confirm the brain stops.

---

## Milestones 2–7 (outline; each gets its own detailed plan when it starts)

2. **Voice:** `RecordScreen`, `AudioRecorder` (16 kHz mono PCM → Opus file as the original), the Quick Settings tile, `ModelManager` (Wi-Fi-only download with a SHA-256 check, adding the INTERNET permission), `WhisperEngine` (whisper.cpp JNI, streaming partials plus a final pass), `TranscriptCleaner`, the "Understood." sheet, and the fast-brain asset. Benchmark model sizes on the owner's phone.
3. **Screenshots:** the `READ_MEDIA_IMAGES` flow, `ScreenshotScanner` (MediaStore Screenshots bucket, newest first, observer plus periodic work), and the `OcrHandler` (ML Kit Latin plus Tesseract Arabic, shrink-only scaling, a 45 s limit). Unparks the IMAGE jobs.
4. **Remind:** `DateRules` and `IntentRules` (Arabic/English, a fixture set of 200+ phrases), `OpenLoopExtractor` with source spans, the confidence policy, `ReminderScheduler` (exact alarms with a fallback, quiet hours, reboot and time-zone receivers), notification actions, NOW with real open loops, area guessing with an editable chip, and the golden journey test.
5. **Connect:** entities and Arabic/English aliases, the embedding model plus brute-force cosine search, Memory search, Ideas clustering, the Connecting tile, and the Ideas tab.
6. **AI layer and Ask:** the on-device LLM for flagged items (fixed-format output), and the Ask tab with mandatory citations.
7. **Backup and polish:** the encrypted `.cortexbackup` export and restore through the file picker (Google Drive), daily-brief text-to-speech, the full Settings screen, the brain licence check (or a swap to a CC0/CC-BY model), and a 60 fps performance pass.
