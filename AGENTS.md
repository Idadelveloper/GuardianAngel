# Guardian Angel — agent instructions

A women's-safety Android app (Kotlin, Jetpack Compose). The user says a **wake word**;
the app starts recording, transcribes and interprets the situation on-device, and alerts
chosen guardians with her location if things are genuinely escalating. Target users:
women 18+, alone and uneasy.

This file is the canonical context for **every** coding agent on this repo. Codex reads
it directly; Junie reads it and adds `.junie/playbook.md`; Gemini imports it from
`GEMINI.md`; Claude Code loads it via `.claude/skills/guardian-angel-context`. Keep the
shared facts **here** — the per-agent files are thin on purpose, because this project has
already been bitten by a context file that drifted out of date.

Deeper references: `README.md` (product), `docs/SPEECH_STACK.md` (model choices and
benchmarks), `docs/DATA_AND_AUTH.md` (storage and auth).

## Verify your work

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest        # 34 tests; includes the WCAG contrast guard
./gradlew :app:connectedDebugAndroidTest  # 20 tests; needs a device — these are the real proof
./gradlew :app:lintDebug
```

Both test suites pass on `main`. **If you touch audio, run the instrumented suite** — it
is the only thing that has ever caught a real bug in this area, and it has caught several
that every unit test passed over.

Toolchain: AGP 9.4.1, Kotlin 2.2.10, KSP 2.2.10-2.0.2, Compose BOM 2026.02.01, Room
2.8.5, minSdk 24 / targetSdk 37. Two pins are deliberate: **LiteRT 1.4.2** and **Firebase
BoM 34.19.0** — the newer majors are compiled with Kotlin 2.4 metadata and will not
resolve against Kotlin 2.2.10. `android.disallowKotlinSourceSets=false` in
`gradle.properties` is required for KSP under AGP 9; do not remove it.

## Three constraints that settle arguments

1. **Discreet.** Help arrives without visibly asking for it. No sounds, no flashes, no
   obvious panic button. A change that makes activation more conspicuous is wrong.
2. **Tolerant of mistakes.** Codewords are ordinary words and will be said by accident.
   Escalation is always cancellable; stopping is one tap with no confirm dialog.
3. **Calm.** The user may already be frightened. Blush white and soft rose, never alarm
   red as a default. Micro-interactions breathe, never strobe — except Angel's critical
   tier, where the strobe is the point.

When a decision is genuinely ambiguous, these beat visual novelty.

## Two kinds of spoken trigger — never conflate them

| | **Wake word** | **Codewords** |
|---|---|---|
| When said | while on standby | while already recording |
| Does what | starts recording | chooses the action |
| Matched by | always-on keyword model | the transcript |
| Types | `WakeWord`, `ListeningRepository` | `Codeword`, `CodewordRepository` |
| Set in | onboarding step 4 · Settings → Wake word | onboarding step 5 · Settings → Codewords |

Separate types, repositories and screens, on purpose. A user who believes her danger
codeword wakes the app would say it into a phone that is not listening. Never merge them;
never let one screen offer both.

`CodewordTier` order is always `Safe` → `Caution` → `Danger` → `Emergency` (cancel false
alarm · transcribe silently · alert circle + location · call 911 + alert circle). Sort by
`CodewordTier.entries`, never by whatever the data layer returns.

## What Android actually allows for hands-free listening

Load-bearing platform facts. Do not design around wishes:

- `RECORD_AUDIO` is while-in-use. Background listening needs a `microphone` foreground
  service plus `FOREGROUND_SERVICE_MICROPHONE` (Android 14+).
- **The FGS cannot be started from the background** — not on boot, not from a broadcast
  (`ForegroundServiceStartNotAllowedException`). Angel cannot arm herself; the user arms
  her from a visible screen. "Arm before you set off" is the only legal model, and the UI
  says so rather than implying otherwise.
- Once started legally it *does* keep capturing with the app closed and the screen locked.
- A persistent notification is mandatory.
- `AlwaysOnHotwordDetector` / SoundTrigger is default-assistant only. Not available to us.

## Invariants that were paid for in bugs

Each of these cost a real, silent failure. Treat them as contracts, not preferences.

### One embedding window, everywhere — 1.5 s

CAM++ speaker embeddings are **only comparable between inputs of similar duration**.
Measured on a Pixel 7a, one speaker, one sentence:

| Comparison | Cosine |
|---|---|
| Fixed 1.5 s windows, same speaker | 0.79 mean / 0.61 worst |
| Same speech at 1 s vs 2 s | **−0.03** |
| Same speech at 1.5 s vs 3 s | **0.24** |
| Silence vs speech | −0.02 |

The first implementation trimmed takes to variable length, enrolled on 3 s segments and
verified a 1.5 s pre-roll. Every component passed its own tests and the voice gate would
have rejected the enrolled user **on every wake**.

`SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES` is the single window length for enrolment,
wake-word verification and diarization. Always produce embeddings via `embedSpeech()` /
`speechWindow()` (which slides a fixed window to the densest speech), never by trimming
to variable length. If the pre-roll length changes, the enrolment window changes with it.
`VoiceVerificationTest.everyEmbeddingUsesTheSameWindow` guards this.

### Native teardown must be ordered

Launching a flush and then freeing the model crashes the process natively. `stop()` /
`release()` on `GuardianAudioSession` are `suspend`: they `cancelAndJoin()` the capture
job, await `transcriber.finish()`, and only then `close()` the models.
`GuardianListeningService` runs teardown on a separate `teardownScope` because it cancels
its working `scope`. `SherpaTranscriber` and `SherpaSpeakerIdentifier` also guard every
native call with `synchronized(nativeLock)` so an ordering mistake degrades instead of
segfaulting. Any new model wrapper does the same.

**When a test fails with an empty message, check logcat for `F DEBUG` tombstone lines**
before assuming an assertion — a native crash looks like nothing from the Kotlin side.

### Permissions are probed, never cached

`PermissionProbe` reads the system on every status emission. Caching a snapshot pushed in
from the UI meant every fresh process assumed nothing was granted, so Home nagged the
user to grant permissions she had already granted in onboarding. Never reintroduce a
stored copy of permission state; `refreshPermissions()` is a "look again" nudge, not a
setter.

### The voice gate degrades off, never on

`WakeWord.requireVoiceMatch` is a **request**. `GuardianListeningService` gates on the
voiceprint only when `VoiceProfile.isUsable` (clarity ≥ 55, derived from how well the
enrolment takes agreed with each other). Gating on a bad voiceprint does not keep a
stranger out — it stops Angel waking for the person she belongs to, which is the one
failure this feature must never have. The UI states what is *actually* happening, not
what the toggle is set to.

### The wake-word tokenizer is unigram, not BPE

`SentencePieceTokenizer` uses **Viterbi segmentation** over `bpe.model` (which carries the
piece scores — `tokens.txt` does not). Greedy longest-match matched sherpa's reference on
7 of 9 phrases and the wake word never once fired. Viterbi is 9/9 exact. Load from
`bpe.model`; validate a typed phrase with `canUseWakePhrase` before saving, because an
unrepresentable phrase saves fine, looks set up, and never fires.

### Codewords go through the gate, never straight from a transcript match

`CodewordGate` (`domain/codeword/`) decides whether a spoken codeword does anything. Pure
and time-injected, so all of it is unit-tested. Four rules, each paid for:

- **Once per utterance.** Matching runs over a rolling transcript window, so one phrase
  appears in many consecutive evaluations. Firing per evaluation meant tens of alerts
  from one word.
- **It has to be her.** Decided per *chunk* from `TranscriptChunk.isEnrolledUser`, not
  from a session-wide flag. `null` means *undecidable*, never "someone else":
  unverifiable voices may start recording (Caution) but may not alert anyone or cancel an
  alert.
- **Acting on people waits.** Danger holds 10 s, Emergency 4 s, so an accident can be
  taken back. The service's watchdog polls `dueForDispatch()`; nothing dispatches from
  the transcript callback.
- **Safe cancels anything pending**, which is what makes the hold worth having.

`CodewordGateTest` pins twenty cases. Never add a path that calls `dispatchAlert`
directly from a transcript match.

### Per-chunk speaker attribution

`SherpaSpeakerIdentifier.attribute()` returns the cluster tag *and* whether it was the
enrolled user, from one embedding. `SherpaTranscriber.attributeSpeaker` is the hook, wired
by the session so one 28 MB speaker model serves the wake-word gate, diarization and
per-line attribution.

This replaced `!speakers.hasUnknownVoice()`, a session-wide flag: once any stranger had
spoken, every later line *of hers* read as not-her, and until then a stranger's words
read as hers. Codeword actions hang off this.

### Sessions are written as they happen, never buffered

`SessionRecorder` / `RoomSessionRecorder` writes each line, sound and breadcrumb as it is
decoded, because the moments worth recording are the moments something might kill the
process. A session with no `endedAt` is honest, not broken, and the insights say so.

Every method swallows its own failures: losing a line is survivable, throwing back into
the capture loop and stopping the recording is not.

`RoomActivityRepository` derives analytics on read, so deleting a session immediately
stops it counting. It replaced `FakeActivityRepository`, which served three invented
incidents to every account. **An account with no recordings must show nothing** — an app
that displays imaginary evidence teaches the user its records cannot be trusted.

`FileTranscriptExporter` writes real text to `getExternalFilesDir("exports")` and states
its own provenance (machine-transcribed, unreviewed, labels are guesses). The previous
implementation returned `"guardian-angel-<id>.pdf"` and wrote nothing.

### Breadcrumb trails draw themselves when there is no Maps key

With the placeholder key the Maps SDK does not error — it composes a map, draws the
Google watermark and renders **nothing**. An empty box in every Activity row reads as a
loading bug, so `MapsAvailability.hasMapKey()` is checked first and `TrailCanvas` draws
the path instead: fitted to the bounding box, longitude scaled by `cos(latitude)` so
walks are not stretched, and coloured segment by segment by the score at that moment.

When a key *is* present:

- **List rows use lite mode** (`GoogleMapOptions().liteMode(true)`). It renders a bitmap
  rather than a GL surface, which is what makes one per row affordable — the use case
  Google documents it for. Lite mode's default tap launches the Maps app, so the whole
  preview is wrapped in its own click target instead.
- **The detail map has every gesture disabled.** It sits in a vertical scroll, and with
  panning on, a drag meant for the page moved the camera off the route with no way back.
  "Open in Maps" is the way out.
- **Tapping a marker selects the incident row below it.** `MarkerInfoWindow` rasterises
  Compose content and does not reliably draw inside a clipped non-interactive map; the
  list is also the surface a screen reader can reach. The marker click returns `false`
  so the bubble still shows where it can.

Incidents are **derived on read** by `TrailBuilder`, never stored: what counts as notable
changes as the heuristics improve, and a session recorded last month should benefit from
today's understanding of it. Events further than 45 s from any breadcrumb are dropped
rather than placed — a marker an unknown distance from the event is read as precise.
Events within 20 s of each other collapse to the most severe, so a codeword is never
hidden behind a flagged sentence. `TrailBuilderTest` pins all of it.

### Recording is not danger

`AngelMood.fromScore(inDuress = …)` takes an *actual* duress trigger, not "is recording".
Passing `isRecording` pinned Angel to her Critical strobe for every recording, including
one started by a tap on a quiet street. Her mood tracks the live safety score, which is
an average of several factors and moves in real time.

The live transcript card sits **below** the hero for the same reason: a panel that takes
over the top of the screen says "emergency", and recording often is not one.

### A recording left running stands itself down

The service watchdog stops a session after 30 minutes with no speech and no danger sound.
A recording forgotten in a bag is a battery drain and a privacy problem, and the user who
forgot it is least likely to notice. Speech and danger events both reset the timer, so an
incident cannot time out mid-way.

### Stubs must never fake success

A detector or model stand-in that reports a match would make hands-free look like it
works, which for a safety app is dangerous to demo. When a model is unavailable the UI
says so and does not offer an Arm button that arms into silence.

## The speech stack is a cascade

Each tier runs only when the one below says it is worth it. Rationale and benchmarks in
`docs/SPEECH_STACK.md`.

```
0  VAD             always       is anyone speaking        (Silero)
1  wake word       armed        should I start recording  (sherpa-onnx KWS, zipformer)
2a streaming ASR   recording    what is being said        (sherpa-onnx + Moonshine Tiny)
2b audio tagging   recording    scream / glass / shouting (YAMNet via LiteRT)
2c speaker ID      recording    how many voices, whose    (CAM++ 512-d)
3  reasoning       on suspicion is this escalating        (HeuristicThreatAssessor)
```

**Never put a language model on the hot path.** On-device LLM is 2–5 s to first token and
~1.2 GB RAM; an MFCC+SVM distress classifier reaches ~95% / 1% FA for 3–5% battery per
10 h. `HeuristicThreatAssessor` runs on every chunk and must stay instant and auditable —
its severity breakdown is what the user reads back after an alert. An LLM is only for the
ambiguous 0.30–0.60 band, over **text**, never over audio.

Escalation requires **two or more** corroborating signals; one weak signal must never be
able to call someone's emergency contacts. `HeuristicThreatAssessorTest` pins both failure
modes — if you retune weights, that test is the contract.

Cloud ASR is better and cheaper and we still do not use it on the live path: audio never
leaving the device is the product promise, and connectivity fails where she needs it most.

### Audio package

```
audio/SpeechPipeline          the interfaces every tier implements, + the rationale
audio/AudioFeatures           log-mel + FFT front end, cosineSimilarity
audio/SentencePieceTokenizer  phrase → tokens, Viterbi over bpe.model
audio/SherpaWakeWordDetector  keyword spotting (fp32 encoder — int8 aborts natively)
audio/SherpaTranscriber       Silero VAD + Moonshine Tiny
audio/SherpaSpeakerIdentifier CAM++ voiceprint; embedSpeech() is the comparable path
audio/VoiceEnroller           records audio, folds a running mean, reports clarity
audio/YamnetAudioTagger       521 AudioSet classes via LiteRT
audio/HeuristicThreatAssessor the cheap reasoning tier
service/GuardianAudioSession  one AudioRecord, phase-routed; route() is the test seam
service/GuardianListeningService  the microphone FGS
```

The KWS encoder **must be fp32**. Both int8 conversions abort natively mid-stream
(`Reshape` input/requested shape mismatch in `KeywordSpotter_decode`).

## Architecture

```
di/AppContainer          the object graph; the single swap point for persistence
domain/model             plain Kotlin, no Android types
domain/repository        one interface per feature area
data/local               Room: entities, DAOs, Room*Repository implementations
data/platform            Android-specific implementations of domain interfaces
data/Fake*Repository     in-memory stand-ins + *Samples shared with @Preview
ui/…                     theme, mascot, components, icons, navigation, feature packages
```

Screens read one snapshot and never touch a data source. To surface something new, add it
to the domain model and push it through the repository — do not reach around it. Sample
data lives in `*Samples`, never inline in a preview, so previews and the running app
cannot drift.

Secrets are modelled by **status, not value**: `VoiceProfile` carries a clarity score, not
the voiceprint; `DisarmPin` carries whether a PIN is set, not the PIN. Keep it that way.

Room is at **version 2** with exported schemas and real migrations in
`GuardianDatabase.MIGRATIONS`. No destructive migration — losing a user's guardians and
codewords is a safety regression, not an inconvenience. Transcripts, the voiceprint and
the disarm PIN are **never uploaded**; `CloudSync` uses hand-written maps so a new column
cannot start syncing by accident.

`GuardianRepository`, `ActivityRepository` and `RouteRepository` are **still in-memory
fakes**, so the Home snapshot is sample content. This is why the "Not set up yet" card
reads codewords and guardians from their own Room repositories rather than from the
snapshot — do not "simplify" it to use the snapshot.

## Navigation

One `NavHost`; routes are constants in `Routes`; tabs in `TopLevelTab`. Three shells:

- **Onboarding** slides horizontally with no bottom bar — a tab bar invites the user out
  of a wizard that must complete in order.
- **Main tabs** cross-fade; a slide would imply a hierarchy that peers do not have.
- **Stacked sub-screens** slide in from the right.

The bottom bar lives in the NavHost, not in screens, so exactly one place decides when it
is visible. Use `GuardianTabScaffold` / `GuardianStackScaffold` / `GuardianWizardScaffold`
instead of hand-rolling insets — they own window insets, max content width and the
floating-nav clearance.

Sign-up and log-in are **outside** onboarding, as top-level destinations rather than a
nested graph: which one opens depends on live state, and a nested graph fixes its start
destination when the graph is built.

Onboarding is **5 steps** (permissions, voice, guardians, wake word, codewords) and every
step is skippable via the scaffold's `skipLabel`/`onSkip`. Adding a step means renumbering
every `stepLabel` and `progress`. A skipped step must resurface: `GuardianCapability` /
`guardianSetup()` derive what still does not work from live state, and `SetupNeededCard`
shows the most consequential gap with one button that fixes it.

Settings recalibration uses `Routes.SETTINGS_VOICE_RECALIBRATE`, a separate destination
from the onboarding `VOICE_CALIBRATION`, because the two differ in where Continue goes —
reusing the onboarding route dropped the user into the rest of the wizard.

## Design system

Theme lives in `ui/theme/`. Two accessors, no third:

- `MaterialTheme.colorScheme` / `.typography` / `.shapes` for standard Material roles
- `GuardianTheme.colors` / `.spacing` / `.shapes` / `.type` / `.windowSizeClass` for brand
  tokens Material has no slot for

**Never hard-code a hex value, dp spacing or font in a component.** If a token is missing,
add it to the theme.

### Paired colours

Accent tokens invert between light and dark; always use the paired content colour.

| Background | Content |
|---|---|
| `colors.accentSoft` | `colors.onAccentSoft` |
| `colors.accentWarm` | `colors.onAccentWarm` |
| `colors.activeContainer` | `colors.onActiveContainer` |
| `colors.safeContainer` | `colors.onSafeContainer` |
| `materialColors.primaryContainer` | `materialColors.onPrimaryContainer` |

Borrowing an unrelated `on*` role is the most likely bug here: it looks fine in light mode
and renders at ~1.3:1 in dark. `ColorContrastTest` catches it — run the unit tests after
any colour change.

### Accessibility floors

4.5:1 for text, 3:1 for any non-text element that carries meaning or identifies a control.
Three values from the original design document fail these and have accessible siblings
already: use `colors.focusRing` (not raw rose) for focus, `colors.borderControl` (not
`borderDefault`/`borderEmphasis`) for control outlines, and `colors.iconMuted` (not
espresso at 45%) for inactive nav icons. `borderDefault`/`borderEmphasis` are decorative
dividers only.

Colour is never the only signal — pair every status with a label and a distinct glyph.

### Other conventions

- Icons: `GuardianIcons`, stroke-based 24×24 with round caps. Add there rather than
  pulling in Material's filled glyphs.
- Shapes: `shapes.pill` for buttons and chips, `.lg` (16 dp) cards, `.xl` (24 dp) sheets
  and hero cards, `.md` (12 dp) inputs.
- Standard buttons are 52 dp; in-card pill actions are 44 dp.
- Elevation is ambient warm glow plus tonal layering via `guardianCardElevation`,
  `guardianFloatingElevation`, `focalHalo`, `ambientGlow` — **not** Material tonal
  elevation, which would double-tint the surface.
- No dynamic colour. The palette is a safety signal; wallpaper must not repaint it.
- Inside a vertically scrolling column, build grids from chunked `Row`s. A
  `LazyVerticalGrid` nested in a same-orientation scroll will crash.
- Window insets go **outside** the scroll modifier, or the padding scrolls away.

## Angel (the mascot)

`ui/mascot/` — a Compose-canvas mascot with five tiers: `Resting`, `Sanctuary`,
`Cautious`, `Warning`, `Critical`. Adding a sixth means updating `AngelStyles.kt`, which
declares all five side by side.

Derive mood only through `AngelMood.fromScore(score, atSafeHaven, isArmed, inDuress)` —
picking a mood by hand in a screen makes two surfaces disagree about how worried Angel is.
She is decorative by default; pass `contentDescription` only where she is the sole carrier
of a message, which should be nowhere.

## Home screen

The safety score sits directly under Angel, above everything optional. It used to live
inside the sanctuary/journey bodies, below the setup and hands-free cards, which put it
entirely below the fold on a not-yet-configured account — the one number the user opens
the app to see required scrolling past three cards about what she had not set up.

The app bar's left action is **record/stop**, not a quick alert. A duress shortcut there
put "call my emergency contacts" one stray tap from the top of the screen with no hold
and no undo; recording is the reversible action and the one wanted often. The duress
trigger stays a three-second hold further down.


One `GuardianMode` drives all three states: `Standby` (mic dormant, quiet surface) ·
`Listening` (armed, warm gradient hero plus telemetry) · `Recording` (panel expands at
top with timer, waveform, transcript, who was alerted, and a full-width Stop button).

The duress trigger is a **3-second hold**; stopping is an immediate tap. Do not make these
symmetrical — arming is deliberate, standing down is not.

A manual record path must always exist and must depend on **nothing but `RECORD_AUDIO`**:
no wake word, no keyword model, no notification permission.
`GuardianListeningService.record()` arms and records in one step, and it is the thing that
still works when everything clever has failed. It is offered both at home and mid-walk.

## Safety score

A probability estimate from public crime data, time of day, distance from safe base, safe
nodes and lighting. Lead with the band and a one-line rationale; the percentage is
secondary and every factor is listed. Never present it as a guarantee.

Demographic factors (race, age) are **opt-in and off by default**. Do not add them to the
defaults and do not infer them.

## Testing notes

- `GuardianAudioSession.route()` and `armForTest()` exist so the phase machine can be
  driven with recorded audio instead of a microphone. The fixture is
  `app/src/androidTest/assets/speech_light_up.wav` (sherpa's own clip, contains "LIGHT
  UP"). Registering that as the wake phrase means a detection can only come from the model
  genuinely matching audio.
- Prefer an instrumented test against a real repository over UI automation. Driving the UI
  was tried and mostly tested the emulator's keyboard.
- For UI automation use the **emulator** (`Pixel_8a_API_35`), not the physical Pixel 7a —
  it has a secure lock screen and taps silently go to the lockscreen. Set
  `adb shell svc power stayon true`, confirm foreground with
  `dumpsys activity activities | grep topResumedActivity`, and remember the IME covers the
  bottom ~40% of the screen, so dismiss it before tapping a bottom bar. Avoid
  `KEYCODE_MENU` / `KEYCODE_ESCAPE` on the emulator: they launch the Google voice
  assistant, which steals focus.
- `uiautomator dump` does work despite Angel's infinite animations; Espresso idling-resource
  waits are the thing that will time out.

## Current state and what is next

Done: the whole UI and navigation; the Angel mascot; Room with twelve tables, exported
schemas and Keystore-encrypted secrets; anonymous accounts with email/password and phone
linking ready for Firebase; opt-in cloud backup that cannot upload transcripts; the full
speech cascade wired and **proven on device** — detection on recorded speech, no false
accept on unrelated speech, the speaker gate in both directions, enrolment producing a
usable voiceprint, re-arming after release, manual recording, and wake-word persistence
through the real Room store; **Berkeley safest-route prototype** complete with live Google
Maps Compose screen, fused GPS tracking, Angel mascot live marker with mood synchronization
and floating text status bubble, Places Autocomplete destination search, Home & Safe Location
Room persistence (`SafePlaceDao`) with geofence hysteresis (80m enter, 105m exit) and Sanctuary
semantics (score 100% at Home unless under active emergency override), deterministic multi-factor
route safety evaluation over official Berkeley BPD/UCPD spatial cells and NWS weather, and privacy-preserving
markers with legend. Documents: `docs/BERKELEY_MAPS_SETUP.md`, `docs/BERKELEY_DATA_MANIFEST.md`,
`docs/BERKELEY_INGESTION_REPORT.md`.

Next, roughly in order:

1. **Tune detection thresholds against real speech.** Only Ida can do this — nothing
   automated can tell you how the wake word behaves with her voice, at arm's length,
   through a pocket, with a television on, or whether the speaker threshold is right for
   two real people. She has not yet said the wake word aloud to an armed build.
2. **Tier 3 reasoning** — an LLM assessor over text for the ambiguous 0.30–0.60 band.
3. **Room-back `GuardianRepository`, `ActivityRepository`, `RouteRepository`** so recorded
   sessions survive the process. The tables exist; only the implementations are missing.
4. **APK size** — 240 MB with models bundled. Needs first-run download or Play Asset
   Delivery before any release.
5. **Localise** the copy added with the Angel screens, which is still inline in the
   composables rather than in `strings.xml`.

Firebase code is complete but **no Firebase project exists yet** — Ida has to create it
and drop in `google-services.json`. The app is designed to work fully without it
(`FirebaseAvailability.isConfigured` gates the paths), so do not make it a hard dependency.

## House rules

- Match the surrounding code: this repo comments the **why**, not the what, and explains
  the failure a piece of code prevents. Keep that.
- Never weaken a test to make it pass. The instrumented audio tests exist because they
  caught bugs everything else missed.
- Do not commit or push unless asked. Models live in `app/src/main/assets/`; extract
  archives and delete `test_wavs/` before committing any new model. ABI filters are
  `arm64-v8a` + `x86_64` — do not re-add `armeabi-v7a`/`x86` (57 MB for nobody).
- Report honestly. If a test fails, say so with the output; if something is unverified,
  say which part and why.
