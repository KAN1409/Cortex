# Cortex Prime — Design

Status: approved by owner · 2026-11-23
Visual reference: Design canvas "Cortex Prime Directions", artboard **D** (playable mockup).

---

## 1. Intent

Cortex Prime is a **personal second brain** for one person on one Android phone.

The owner captures mostly **screenshots, voice notes and ideas**, in **Egyptian Arabic,
English, or both mixed**. What they want back:

1. **"Remind me what I forget."** Cortex finds commitments, deadlines and intentions inside
   what was captured, even when no reminder was set. Examples: "renew the car licence before
   the 30th" in a screenshot, or "لازم أكلم ماما بكرة" in a voice note. Cortex brings each one
   back with a timed notification.
2. **Smart voice transcription.** Every voice note becomes clean text with a title, a short
   summary, and the reminders, ideas, people and dates it mentions. All of this runs on the phone.
3. **Nothing is lost.** Every capture can be searched by words or meaning. Related ideas from
   different days come together into clusters.

The defining moment is opening the app and seeing *"Cortex noticed …"*: something the owner
would otherwise have forgotten, shown with the source it came from.

Work and procurement are **not** the focus. Work is one area of life (a filter), not a pillar.

## 2. Goals, non-goals, anti-goals

**Goals (v1)**
- Capture: voice note (one tap, also from a Quick Settings tile), screenshots (automatic, from
  the phone's screenshot folder), share-in (text, images, PDFs, audio, links), quick text idea.
- Perception: on-device OCR (Arabic and English) and on-device transcription (Arabic, English, mixed).
- Understanding: extract open loops (commitments, deadlines, intentions), dates, people, places
  and topics, each traceable to the exact source words.
- Reminders: timed notifications at sensible times, with Done / Snooze actions on the notification.
- NOW screen: today / soon / connecting, ranked, each item showing its source.
- Memory: a timeline of everything captured, plus search by words and by meaning.
- Ideas: automatic clusters of related notes and screenshots.
- Ask: questions about your own memory, answered from your memories with links to the sources.
- Backup and restore to Google Drive **only when the owner triggers it**.
- The premium visual and motion language of mockup D, on every screen.

**Non-goals (v1)**
- Reading other apps' notifications (possible later, opt-in).
- Work Vault, procurement, the price engine, multi-model "council": all of v159's work tooling.
- Accessibility screen-scraping of any kind.
- Cloud transcription or cloud reasoning running in the background.
- Multiple users, sync between devices, a web or desktop client.
- Importing v159 data (fresh start, decided).

**Anti-goals (a failure even if it "works")**
- A reminder or finding with no visible source.
- Status or counters that lie, like v159's "all caught up" while 1,036 screenshots waited.
- One bad file blocking the whole queue (v159's OCR stall).
- Notification spam: interrupting for guesses Cortex isn't confident about.
- Uploading anything without the owner tapping to do it.
- Decorative UI with nothing working behind it.

## 3. Constraints

- **Package `com.kareem.cortex.prime`**, installed **alongside** the old app (fresh start, no import).
- Sideloaded APK (not the Play Store); minSdk 29, target the latest stable SDK.
- Kotlin, Jetpack Compose, Room, WorkManager. No dependency-injection framework (constructor wiring).
- Everything runs offline once the models are downloaded. Model files download once, over Wi-Fi only.
- Build verification happens in GitHub Actions; this cloud environment has no Android SDK.
  Behaviour on the device is checked by the owner on their phone at each milestone.
- The new app lives in **`prime/`** in this repo as a standalone Gradle project with its own CI
  workflow. v159 stays untouched.

### 3.1 Zero cost (owner decision)
Cortex Prime and every component it uses must be **completely free**: free to build, free to
run, no usage fees, no subscriptions, no API keys for paid services, no paid assets. Any new
component must pass this check before it is added.

| Component | Use | Cost / licence |
|---|---|---|
| Kotlin, Jetpack Compose, Room, WorkManager, AndroidX | app framework | free, Apache 2.0 |
| ML Kit text recognition (bundled Latin model) | English OCR | free of charge, runs on-device (closed-source Google SDK) |
| Tesseract + `ara` trained data | Arabic OCR | free, Apache 2.0 |
| whisper.cpp + Whisper models | transcription | free, MIT |
| On-device language model (milestone 6) | flagged items, Ask | must be free with an open licence, e.g. Apache 2.0 or MIT |
| Multilingual sentence-embedding model (milestone 5) | meaning search, Ideas | must be free with an open licence, e.g. MIT or Apache 2.0 |
| Geist, Geist Mono, Readex Pro | fonts | free, SIL Open Font License |
| Brain mesh (fsaverage template) | the brain visual | free; redistribution licence to confirm, otherwise swap to a CC0/CC-BY model |
| Android text-to-speech | daily brief | free, built into the phone |
| Google Drive via the system file picker | owner-triggered backup | free with the Google account's free storage |
| GitHub Actions | build and tests | free for public repos; private repos use the free monthly minutes allowance |

Not allowed: cloud AI APIs (OpenAI, Gemini API, OpenRouter, ElevenLabs and others), paid model
or asset downloads, ad SDKs, analytics SDKs, and anything with a paid tier the app depends on.

## 4. Approaches (decided)

**How Cortex finds what you'd forget**

| | Approach | Verdict |
|---|---|---|
| A | Rules only (date and intent patterns) | Fast and predictable, but misses phrasing it doesn't know |
| B | On-device AI model reads everything | Best understanding, but slow and heavy on battery across 1,000+ screenshots |
| **C** | **Rules first, AI for flagged items, ask when unsure** | **Chosen**: fast on most items, smart on hard ones, every result traceable |

**Where transcription runs:** on the phone only (owner's choice). Google Drive is an
owner-triggered backup, not a processing service.

**How reminders reach you:** timed notifications (owner's choice), not a daily digest or NOW-only.

**Rejected structurally:** forking v159 (not a fresh start), and an AI-first design that
depends on paid APIs.

## 5. Product: screens

Navigation: a floating glass bar with **Now · Memory · [record] · Ideas · Ask**. Settings opens
from the avatar.

### 5.1 Now (home)
Matches mockup D. From top to bottom:
- **Header:** avatar (initial), a bell with the count of active reminders, and the date.
- **Brain hero:** the animated glass brain (§7.3) beside "Good evening, Kareem" with a moving sheen.
- **"You might forget"** stack: the highest-ranked open loops as stacked glass cards. Swipe
  between them; *Done* / *Later* (the snooze picker) / a link to the source.
- **Daily brief + rings:** *Daily brief* reads today's items aloud with on-device text-to-speech.
  The rings show Life / Health / Work progress, where progress = open loops closed this week ÷
  open loops due this week, per area.
- **Date tile and "Connecting" tile:** idea clusters that grew today, and the next deadline.
- Empty state: a calm brain with "Nothing you're likely to forget right now", only when that's true.

### 5.2 Record (centre button, Quick Settings tile)
- Full-screen listening view: the brain grows and fires faster, a live waveform, words appear
  as you speak (streaming transcription), and a stop button.
- On stop: the **"Understood." bottom sheet** slides up. It shows the clean transcript, key
  points, and the reminders it found, each with its time. Buttons: *Looks right* (confirms) and
  *Edit* (change the wording or time, delete an item).
- The original audio is always kept.

### 5.3 Memory
- A timeline of everything captured (voice, screenshots, shares, ideas), grouped by day.
  Filters: All / Voice / Screenshots / Ideas, and areas Life / Family / Health / Work.
- A search box that combines word matches and meaning matches.
- Item detail: the original (play the audio, view the image), the extracted text, what Cortex
  understood (people, dates, open loops), and the related items.
- Every item shows its processing state honestly: *Waiting · Reading · Done · Couldn't read
  (why) · Retry*.

### 5.4 Ideas
- Clusters of related captures (for example "Kids' summer plan · 3 voice notes, 2 screenshots"),
  each with a short title the cluster earns from its contents.
- Cluster detail: the members over time, and actions *Rename*, *Merge*, *Remove from cluster*.

### 5.5 Ask
- Type or speak a question ("When did I say I'd pay the school fees?").
- The answer is built only from the retrieved memories and cites them as tappable chips. If
  nothing relevant is found, it says so rather than guessing.

### 5.6 Settings
Models (download status and size, delete), screenshot folder access, notification permission
and quiet hours, reminder timing defaults, Drive backup and restore, privacy (what's stored, a
wipe button), and the reduce-motion override.

## 6. Architecture

### 6.1 Pipeline
Every stage reads typed input from the database and writes typed output back, so every step
can be inspected, resumed and retried:

```
Capture → Evidence → Perceive → Understand → Connect → Remember → Remind/Surface → Act → Learn
```

| Stage | Input | Output | Notes |
|---|---|---|---|
| Capture | audio, image, share, text | `Evidence` row + original file in app storage | Originals are immutable; a SHA-256 hash dedupes identical inputs |
| Perceive | Evidence | `Perception` (OCR text / transcript, language, confidence, engine) | Per-item time limit, see 6.2 |
| Understand | Perception | `Mention`s (dates, people, places, topics) and `OpenLoop` candidates, with source spans | Rules → AI for flagged items → ask the user if confidence is low |
| Connect | Mentions | `Entity` links, `Cluster` membership, semantic embedding | One entity list for everything (fixes v159's six lists) |
| Remember | all of the above | versioned facts; corrections never overwrite history | |
| Remind/Surface | OpenLoops | `Reminder` schedule, NOW ranking | §6.4, §6.5 |
| Act | user taps | `ActionReceipt` | Truthful states, carried over from today's v159 work |
| Learn | feedback | per-type ranking weights, bounded | Same bounded model as v159's feedback ranking |

### 6.2 The job engine
- One `Job` table: `(id, stage, evidence_id, state, attempts, lease_until, last_error, next_run_at)`.
- A worker **claims** a job with a lease (`RUNNING`, `lease_until = now + limit`). On startup,
  expired leases go back to `WAITING`.
- **Per-item time limit:** OCR 45 s and transcription 4× the audio length, both settings. An item
  that hits its limit is recorded as `FAILED(timeout)` and the queue moves on. One bad item can
  never stall the rest.
- Retries back off exponentially, at most 3 attempts, then `FAILED` with a reason the user can see.
- **Incremental only:** jobs are created for new evidence. There's no "re-analyse everything" loop.
- Heavy work (OCR backfill of old screenshots, the AI stage) runs only while charging or with
  the battery above 30%, in batches.

### 6.3 Perception
- **OCR:** ML Kit text recognition for Latin script, plus Tesseract with Arabic trained data.
  Large images are **shrunk** before Arabic OCR, never enlarged. Arabic OCR runs only when ML
  Kit found Arabic letters or too little text.
- **Transcription:** an on-device Whisper model (whisper.cpp via JNI) that handles Arabic,
  English and mixed speech. It streams partial text for the live view, then does a final pass
  for accuracy. The model downloads once over Wi-Fi from a pinned URL with a checksum.
- **Clean-up:** punctuation, removing filler words ("يعني", "um"), and normalising Arabic-Indic
  digits. Rule-based in v1.

### 6.4 Understanding: open loops
- **Rules layer, Arabic and English:**
  - relative dates: بكرة, بعد بكرة, الجمعة الجاية, next Friday, in 3 days
  - absolute dates: 30/11, ٣٠ نوفمبر, Nov 30
  - intent cues: لازم, محتاج, افتكر, ما تنساش, remind me, need to, don't forget, before, expires,
    deadline, due
  - document cues in screenshots: expiry / تاريخ الانتهاء, booking and order confirmations,
    return-by dates
- **AI layer:** a small on-device language model reads only items the rules flagged as
  *possible open loop with low confidence*. It returns a fixed-format result: action, due time,
  person, and confidence.
- **Confidence policy:**
  - ≥ 0.8: create the open loop and schedule its reminder.
  - 0.5–0.8: create it as *Unconfirmed*, show it in NOW with "Is this a reminder?", and don't
    notify yet.
  - < 0.5: keep only as a mention.
- Every open loop stores the **exact source span** (character offsets or image region) it came from.

### 6.5 Reminders
- **When they fire:**
  - deadline with a date only: the evening before at 20:00, and the day itself at 09:00
  - deadline with a time: 1 hour before
  - "tomorrow" with no time: 10:00
- Quiet hours (default 23:00–08:00) push a reminder to the end of the quiet window. All of
  these defaults can be changed in Settings.
- **Delivery:** exact alarms (`USE_EXACT_ALARM`, acceptable for a sideloaded app; verify against
  the target SDK at build time). If exact alarms aren't available, fall back to inexact alarms
  and say so in Settings. Reminders are rescheduled after a reboot or a time-zone change.
- The notification has *Done* and *Snooze* (1 h / tonight / tomorrow) actions and opens the source.
- Unconfirmed loops never notify.

### 6.6 Connect: entities and ideas
- **Entities:** people, places, topics and things, each with aliases in Arabic and English
  ("ماما" = "Mum"). Matching is exact alias, then fuzzy, then ask the user.
- **Embeddings:** an on-device multilingual sentence-embedding model. Vectors are stored in
  Room; search is brute-force cosine, which is fine at personal scale.
- **Ideas:** captures join a cluster when their similarity is above a threshold and they share
  an entity or topic. A new cluster appears in "Connecting" once it has 3 or more members.

### 6.7 Ask
Retrieve the most relevant memories (word search plus meaning search), then either:
- the on-device model writes an answer citing those memories, or
- with no model installed, show the ranked memories themselves.

Ask never answers without citations.

### 6.8 Backup to Google Drive
- **Backup:** the app writes an encrypted `.cortexbackup` file (a zip of the database and the
  originals, encrypted with a passphrase the owner sets). The owner saves it through Android's
  file picker, choosing Google Drive. No Google sign-in or API keys.
- **Restore:** pick the file, enter the passphrase, then replace or merge.
- Nothing uploads automatically.

## 7. Visual and motion language (from mockup D)

### 7.1 Tokens
- **Background:** `#000000`, with a deep blue/violet glow at the top and the **horizon glow**
  at the bottom (a planet rim, blue `#4D7CFF` → violet `#7A5CFF`).
- **Glass surface:** 8.5% → 2.5% white gradient, a 1 px top highlight at 16% white, a dark
  bottom edge, a deep drop shadow (0 24 48 / 65% black), and background blur.
- **Light bleed:** violet/blue radial glows under cards, pulsing slowly. Gold `#E8C26A` only for
  "connecting" or idea highlights.
- **Text:** `#F4F6FF` primary, `#C4CAE0` secondary, `#8C95B5` labels. Greeting sheen goes
  ice-blue `#7FE7FF` → white → `#8FB0FF`.
- **Primary action:** a blue gradient `#5A86FF` → `#2F5BFF` with an inner highlight and a glow.
- **Type:** Geist (Latin), Geist Mono for labels and times, Readex Pro (Arabic). Both are under
  the SIL Open Font License, bundled in the app.

### 7.2 Motion
- **Screen entry:** a staggered rise (translate 22 px, blur 8 → 0, about 1 s, ease-out).
- **Bottom sheets:** slide up with a spring.
- **Ambient:** card glows breathe (5 s), the record button pings, a gloss sweeps across the hero
  card and the record button, the progress rings fill on first appearance.
- **Reduce motion:** with the system setting on, everything ambient stops, entries become a
  simple fade, and the brain shows a still frame.
- **Performance:** a steady 60 fps on a mid-range phone, with ambient effects drawn on the GPU.

### 7.3 The brain
- **Source:** a real human cortex surface (the fsaverage template), with a synthetic cerebellum
  and brainstem.
- **Look:** a translucent glass look with a violet → blue rim, glowing folds and twinkling sparkles.
- **v1 implementation:** pre-rendered frame sequences (transparent WebP) at two sizes. They play
  as a turning loop: slow while idle, about 3× faster while recording. Small drifting particles
  and the light streak are drawn in Compose.
- **Later:** a real-time GPU render of the same mesh, so the brain can react to the voice and
  "fire" where the current note's topics live.
- **Licence:** the fsaverage template's licence for redistribution in an app must be confirmed
  before release (open question 1).

## 8. Data model (Room)

| Table | Key fields |
|---|---|
| `evidence` | id, kind (voice/screenshot/share/text), original_uri, sha256, captured_at, source_app, area |
| `perception` | evidence_id, kind (ocr/transcript), text, language, confidence, engine, created_at |
| `mention` | id, evidence_id, type (date/person/place/topic), value, normalized, span_start, span_end, confidence |
| `entity` | id, type, canonical_name; `entity_alias`(entity_id, alias, lang) |
| `entity_link` | mention_id, entity_id, confidence, confirmed_by_user |
| `open_loop` | id, evidence_id, text, due_at, due_precision, state (unconfirmed/open/done/dismissed/expired), confidence, span_start, span_end, created_by (rules/ai/user) |
| `reminder` | id, open_loop_id, fire_at, state (scheduled/fired/snoozed/cancelled), alarm_kind |
| `embedding` | evidence_id, model_id, vector (blob) |
| `cluster` / `cluster_member` | id, title, created_at / cluster_id, evidence_id, score |
| `job` | id, stage, evidence_id, state, attempts, lease_until, last_error, next_run_at |
| `action_receipt` | id, open_loop_id, kind, state, detail, updated_at |
| `feedback` | id, target_type, target_id, event, weight, created_at |
| `fact_version` | id, subject, field, value, source_evidence_id, valid_from, superseded_by |

## 9. Privacy
- All data stays in app-private storage. Nothing leaves the phone except a backup the owner exports.
- The app needs internet only to download models, and that can be switched off after download.
- The microphone records only while the record screen is open. Screenshots are read-only,
  through the photo permission (`READ_MEDIA_IMAGES`), limited to the Screenshots folder.
  No accessibility services.
- The owner can wipe everything from Settings.

## 10. Milestones (each produces an installable APK)

1. **Foundation:** the project in `prime/`, CI, the database and job engine, the shell with
   glass UI and motion, capture of text and shares, and an honest Memory timeline.
2. **Voice:** recording, the Quick Settings tile, the Whisper model download, live and final
   transcription, clean-up, and the "Understood." sheet.
3. **Screenshots:** folder access, backfill with the per-item time limit, Arabic and English
   OCR, and honest progress.
4. **Remind:** the rules extractor, open loops, the confidence policy, timed notifications with
   actions, and NOW with real data.
5. **Connect:** entities and aliases, embeddings, meaning search, Ideas clusters, Connecting.
6. **AI layer and Ask:** the on-device model for flagged items, and Ask with citations.
7. **Backup and polish:** Drive backup and restore, the daily brief text-to-speech, Settings,
   and a performance pass.

## 11. Testing
- **JVM unit tests:** the date and intent rules, with an Arabic/English fixture set of at least
  200 phrases taken from realistic notes (including "بكرة", "الجمعة الجاية", and mixed-language
  sentences), plus the confidence policy, reminder timing, and ranking.
- **Robolectric tests** for the database, the job engine (lease expiry, time limit, retry), and
  reminder rescheduling.
- **A golden journey test:**
  1. Capture the voice transcript "لازم أجدد رخصة العربية قبل يوم تلاتين".
  2. Check that an open loop exists with the source span.
  3. Check that reminders are scheduled for the 29th at 20:00 and the 30th at 09:00.
  4. Mark it done and check that its reminders are cancelled.
- **Each milestone APK** is installed on the owner's phone with a short checklist of what to try.

## 12. Calls made in review (owner may override)
- **Name in the greeting:** asked once at first launch, editable in Settings. Never taken from
  the account.
- **Existing screenshots:** all of them are backfilled, newest first, so recent things become
  useful first.
- **Quick text idea:** long-press the record button, or use the "Idea" field at the top of Memory.
- **New screenshots:** picked up within about a minute while the app runs, through a media
  observer, plus a periodic WorkManager scan.
- **Daily brief:** plays only when tapped. No automatic morning audio.

## 13. Left to the builder
- Choice and size of the on-device language model and the embedding model. They must handle
  Arabic, run on-device, and be downloadable on demand; quantised 1–2 B parameter class.
- The exact Whisper model size (the accuracy and speed trade-off on the owner's phone),
  benchmarked in milestone 2.
- Compose implementation details of the glass, glow and motion effects, within §7.
- The internal module layout inside `prime/`.

## 14. Owner decisions (resolved)
1. **Interface language: English.** Arabic content (transcripts, OCR text, quotes) is always
   shown right-to-left with Arabic-capable fonts inside the English UI.
2. **Areas: Cortex guesses** Life / Family / Health / Work for every capture, from its people,
   topics and wording. Each guess shows as a chip the owner can tap to change. Corrections feed
   the learning stage.
3. **Brain model: free only.** Before release, confirm the fsaverage template's licence allows
   redistribution in an app. If it doesn't, replace the mesh with a freely licensed (CC0 or
   CC-BY) human brain model and re-render the frames. No paid assets.
