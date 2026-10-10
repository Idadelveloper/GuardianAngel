# Guardian Angel — agent instructions

A women's-safety Android app (Kotlin, Jetpack Compose). She says a **wake word**; the
app records, transcribes and interprets on-device, and alerts her guardians with her
location if things are genuinely escalating. Target users: women 18+, alone and uneasy.

Canonical context for **every** coding agent here. Codex reads it directly; Junie adds
`.junie/playbook.md`; Gemini imports it from `GEMINI.md`; Claude Code loads it via
`.claude/skills/guardian-angel-context`. Keep shared facts **here** — per-agent files are
thin on purpose, this project having already been bitten by a context file that drifted
out of date. Kept under 32 KiB, Codex's file cap: adding a section means trimming one.

Deeper references: `README.md` (product), `docs/SPEECH_STACK.md` (models and benchmarks),
`docs/DATA_AND_AUTH.md` (storage and auth), `docs/DESIGN_SYSTEM.md` (tokens and a11y).

## Verify your work

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest        # 206 tests; includes the WCAG contrast guard
./gradlew :app:connectedDebugAndroidTest  # 58 tests; needs a device — the real proof
./gradlew :app:lintDebug
```

Both suites pass on `main`. **If you touch audio, run the instrumented suite** — it is
the only thing that has ever caught a real bug there.

Toolchain: AGP 9.4.1, Kotlin 2.2.10, KSP 2.2.10-2.0.2, Compose BOM 2026.02.01, Room
2.8.5, minSdk 24 / targetSdk 37. Three pins are deliberate — **LiteRT 1.4.2**, **Firebase
BoM 34.19.0**, **Places 4.4.1**: the newer majors carry Kotlin 2.4 metadata and will not
resolve against Kotlin 2.2.10. `android.disallowKotlinSourceSets=false` in
`gradle.properties` is required for KSP under AGP 9; do not remove it.

## Three constraints that settle arguments

1. **Discreet.** Help arrives without visibly asking for it. No sounds, no flashes, no
   obvious panic button. A change that makes activation more conspicuous is wrong.
2. **Tolerant of mistakes.** Codewords are ordinary words and will be said by accident.
   Escalation is always cancellable; stopping is one tap with no confirm dialog.
3. **Calm.** She may already be frightened. Blush white and soft rose, never alarm red by
   default. Micro-interactions breathe, never strobe — except Angel's critical tier.

When a decision is genuinely ambiguous, these beat visual novelty.

## Two kinds of spoken trigger — never conflate them

| | **Wake word** | **Codewords** |
|---|---|---|
| When said | while on standby | while already recording |
| Does what | starts recording | chooses the action |
| Matched by | always-on keyword model | the transcript |
| Types | `WakeWord`, `ListeningRepository` | `Codeword`, `CodewordRepository` |
| Set in | onboarding step 4 · Settings → Wake word | onboarding step 5 · Settings → Codewords |

Separate types, repositories and screens, on purpose: a user who believes her danger
codeword wakes the app would say it into a phone that is not listening. Never merge them.

`CodewordTier` order is always `Safe` → `Caution` → `Danger` → `Emergency`: cancel a
false alarm · transcribe silently · text the whole circle with a location · that plus a
call to the first guardian. **Emergency does not dial 911** — see below. Sort by
`CodewordTier.entries`, never by whatever the data layer returns.

## What Android actually allows for hands-free listening

Load-bearing platform facts. Do not design around wishes:

- `RECORD_AUDIO` is while-in-use. Background listening needs a `microphone` foreground
  service plus `FOREGROUND_SERVICE_MICROPHONE` (Android 14+), and a persistent
  notification.
- **The FGS cannot be started from the background** — not on boot, not from a broadcast
  (`ForegroundServiceStartNotAllowedException`). Angel cannot arm herself; the user arms
  her from a visible screen, and the UI says so rather than implying otherwise. Once
  started legally it *does* keep capturing with the app closed and the screen locked.
- **Nor can an activity be started from the background**, which is why an emergency call
  falls back to a notification whose tap dials.
- `AlwaysOnHotwordDetector` / SoundTrigger is default-assistant only. Not available.

## Invariants that were paid for in bugs

Each of these cost a real, silent failure. Treat them as contracts, not preferences.

### One embedding window, everywhere — 1.5 s

CAM++ embeddings are **only comparable between inputs of similar duration**. Measured on
a Pixel 7a, one speaker, one sentence: fixed 1.5 s windows score 0.79 mean / 0.61 worst,
but the *same speech* at 1 s vs 2 s scores **−0.03**, and at 1.5 s vs 3 s only **0.24** —
indistinguishable from silence vs speech (−0.02).

The first implementation enrolled on 3 s segments and verified a 1.5 s pre-roll. Every
component passed its own tests and the voice gate would have rejected the enrolled user
**on every wake**.

`SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES` is the single window length for enrolment,
wake-word verification and diarization. Always embed via `embedSpeech()` /
`speechWindow()` (which slides a fixed window to the densest speech), never by trimming
to variable length. `VoiceVerificationTest.everyEmbeddingUsesTheSameWindow` guards it.

### Native teardown must be ordered

Launching a flush and then freeing the model crashes the process natively. `stop()` /
`release()` on `GuardianAudioSession` are `suspend`: they `cancelAndJoin()` the capture
job, await `transcriber.finish()`, then `close()` the models. The service runs teardown
on a separate `teardownScope` because it cancels its working `scope`. Every native call
is guarded with `synchronized(nativeLock)` so an ordering mistake degrades instead of
segfaulting. Any new model wrapper does the same.

**When a test fails with an empty message, check logcat for `F DEBUG` tombstone lines**
before assuming an assertion — a native crash looks like nothing from the Kotlin side.

### Permissions are probed, never cached

`PermissionProbe` reads the system on every status emission. A snapshot pushed in from
the UI meant every fresh process assumed nothing was granted, so Home nagged her to grant
what she had already granted in onboarding. Never reintroduce a stored copy;
`refreshPermissions()` is a "look again" nudge, not a setter.

### The voice gate degrades off, never on

`WakeWord.requireVoiceMatch` is a **request**. The service gates on the voiceprint only
when `VoiceProfile.isUsable` (clarity ≥ 55, from how well the enrolment takes agreed).
Gating on a bad voiceprint does not keep a stranger out — it stops Angel waking for the
person she belongs to. The UI states what is *actually* happening, not what the toggle
says.

### The wake-word tokenizer is unigram, not BPE

`SentencePieceTokenizer` uses **Viterbi segmentation** over `bpe.model` (which carries
the piece scores — `tokens.txt` does not). Greedy longest-match matched sherpa's
reference on 7 of 9 phrases and the wake word never once fired; Viterbi is 9/9. Validate
a typed phrase with `canUseWakePhrase` before saving — an unrepresentable phrase saves
fine, looks set up, and never fires.

### Codewords go through the gate, never straight from a transcript match

`CodewordGate` (`domain/codeword/`) decides whether a spoken codeword does anything.
Pure and time-injected. Four rules, each paid for:

- **Once per utterance.** Matching runs over a rolling window, so one phrase appears in
  many evaluations; firing per evaluation meant tens of alerts from one word.
- **It has to be her.** Per *chunk*, from `TranscriptChunk.isEnrolledUser`. `null` means
  *undecidable*, never "someone else": unverifiable voices may start recording (Caution)
  but may not alert anyone or cancel an alert.
- **Acting on people waits.** Danger holds 10 s, Emergency 4 s, so an accident can be
  taken back. The watchdog polls `dueForDispatch()`; nothing dispatches from the
  transcript callback.
- **Safe cancels anything pending**, which is what makes the hold worth having.

Matching is fuzzy in three passes (exact → space-insensitive → length-scaled
Levenshtein) so a mispronounced codeword still fires. `CodewordGateTest` pins 27 cases.
Never call `dispatchAlert` directly from a transcript match.

### Per-chunk speaker attribution

`SherpaSpeakerIdentifier.attribute()` returns the cluster tag *and* whether it was the
enrolled user, from one embedding; `SherpaTranscriber.attributeSpeaker` is the hook, so
one 28 MB model serves the wake-word gate, diarization and per-line attribution. It
replaced a session-wide `hasUnknownVoice()` flag under which, once any stranger spoke,
every later line *of hers* read as not-her. Codeword actions hang off this.

Unknown voices are transcribed and stored like any other, labelled "Unfamiliar voice";
`null` is undecidable and renders as the neutral "Speaker". Those look identical in a
transcript, so `attribute()` logs the cosine score — without it a mis-labelled transcript
cannot be told from a model that failed to load.

### Sessions are written as they happen, never buffered

`RoomSessionRecorder` writes each line, sound and breadcrumb as it is decoded, because
the moments worth recording are the moments something might kill the process. A session
with no `endedAt` is honest, not broken. Every method swallows its failures: losing a
line is survivable, throwing into the capture loop is not.

`RoomActivityRepository` derives analytics on read, so deleting a session immediately
stops it counting. **An account with no recordings must show nothing** — imaginary
evidence teaches the user its records cannot be trusted. `FileTranscriptExporter` writes
real text and states its provenance (machine-transcribed, unreviewed, labels are
guesses).

### Breadcrumb trails draw themselves when there is no Maps key

With the placeholder key the Maps SDK does not error — it composes a map, draws the
watermark and renders **nothing**. An empty box in every Activity row reads as a loading
bug, so `MapsAvailability.hasMapKey()` is checked first and `TrailCanvas` draws the path
instead, longitude scaled by `cos(latitude)` and coloured by the score at each moment.

When a key *is* present: **list rows use lite mode** (a bitmap, not a GL surface — what
makes one per row affordable; its default tap opens the Maps app, so wrap it in your own
click target); **the detail map has every gesture disabled**, because in a vertical
scroll a drag meant for the page moved the camera off the route with no way back; and
**tapping a marker selects the incident row below it**, since `MarkerInfoWindow`
rasterises Compose content unreliably inside a clipped map and the list is what a screen
reader can reach.

Incidents are **derived on read** by `TrailBuilder`, never stored. Events further than
45 s from any breadcrumb are dropped rather than placed — a marker an unknown distance
from the event reads as precise. Events within 20 s collapse to the most severe.
`TrailBuilderTest` pins it.

### Recording is not danger

`AngelMood.fromScore(inDuress = …)` takes an *actual* duress trigger, not "is recording",
which pinned Angel to her Critical strobe for every recording including one started by a
tap on a quiet street. Her mood tracks the live safety score.

The live transcript card sits **below** the hero for the same reason: a panel that takes
over the top of the screen says "emergency", and recording often is not one.

### A recording left running stands itself down

The watchdog stops a session after 30 min with no speech and no danger sound. A
recording forgotten in a bag is a battery and privacy problem, and whoever forgot it is
least likely to notice. Speech and danger events reset the timer.

Nothing else stops it. The listening service is torn down only by an explicit disarm or
stop — never by a lifecycle callback — so backgrounding the app or pocketing the phone
leaves a recording running, which is the entire point of it being a foreground service.

### Alerting a guardian goes through one path

The SOS hold and a spoken codeword are the same event; the button used to only flip
in-memory UI state, so a duress hold looked like an alert and sent nothing. Both now call
`AlertDispatcher.dispatch(tier)`, which composes, sends, records the outcome in the
session and updates UI state. **Never call `dispatchAlert` directly from a trigger.**

`GuardianNotificationAgent` owns the judgement: Caution reaches the top guardian only
(waking five people because a recording started teaches them to ignore the next one),
Danger and Emergency reach everyone in priority order, an already-sent tier is suppressed
unless it escalated, Safe is never suppressed — the people woken are owed the all-clear.
A *failed* send is not remembered as sent. `GuardianNotificationAgentTest` pins it.

`SmsGuardianNotifier` uses SMS: one bar, no data, no app needed on the receiving end,
lands on a lock screen. **`SEND_SMS` is restricted on Google Play**; the policy lists
"Physical safety/emergency alerts to send SMS" as an eligible exception, declared through
the Permissions Declaration Form before release. Keep `telephony` `required="false"` or
the app will not install on tablets.

**Ask for `SEND_SMS`, loudly.** Nothing ever requested it, so every alert silently took
the fallback while the user believed texts were going out. It is now
`GuardianCapability.SilentAlerts`, ranked just below having a guardian at all, and asked
for on the onboarding circle step. A permission the UI never requests is a feature that
does not exist.

**Handing a message to `SmsManager` is not sending it.** With null sent-intents, flight
mode, a dead SIM and a rejected message all looked like success. Sends carry
`PendingIntent` receipts and `NotifyOutcome` has three states: `reached`, `failed`, and
`unconfirmed` (no answer in eight seconds). Never fold `unconfirmed` into `reached`.

Without it the fallback opens a pre-filled composer: WhatsApp for a single guardian,
otherwise the SMS composer addressed to everyone at once. **WhatsApp cannot send on the
user's behalf** — Meta offers no personal-account API, only the Business Cloud API from a
business number with approved templates; unofficial libraries get accounts banned. One
tap, not zero, and nothing may call it sent. The manifest `<queries>` block is load
bearing or `getPackageInfo` always throws.

`AlertComposer` writes the message: who, then where, then why. The location is a plain
`https://www.google.com/maps/...` link — `geo:` URIs are not tappable in most SMS
clients. Alarming lines are **quoted, never paraphrased**. A missing location is stated,
not omitted.

**No alert claims emergency services have been called.** Nothing in the app dials one,
and no message says one was dialled. An automated 911 call on a false trigger is a
criminal false report in much of the US, and a guardian told help is already coming is a
guardian who stops calling it themselves — which is the one sentence that turns a working
alert into a fatal one.

### Emergency rings one guardian; every other tier only texts

`EmergencyCallPolicy` is pure and decides *whether*: Emergency only, one guardian
(lowest `priority` with a number), never twice in a session, off if the user cleared
`users.callGuardianOnEmergency`. Caution ringing a phone would get the feature switched
off within a week, and then nothing rings on the night it matters.

`TelephonyGuardianCaller` does it. The hard part: dialling means **starting an
activity**, which Android forbids an app with no visible window — exactly the
locked-in-a-pocket case this exists for, and it fails silently. So app visible →
`ACTION_CALL`; backgrounded → a max-priority call notification whose tap dials, a
notification tap being a documented exception. A full-screen intent is attached only when
`canUseFullScreenIntent()` agrees; Android 14 grants it to dialler and alarm apps only.

`Outcome.Dialling` and `Outcome.AwaitingTap` are **never flattened into one**: a posted
notification is not a placed call, only `Dialling` sets the already-called flag, and the
session note for a tap says "tap to connect". The call runs in parallel with the text's
delivery confirmation, not after it — the message reaches the radio in milliseconds and
only the network's *answer* takes seconds. `AlertDispatcherCallTest` pins it.

`TranscriptSummariser` is deterministic for the same reasons as `SessionNarrator`.
`AiTranscriptSummariser` is the seam for a model-written version — it may enrich the
stored session *after* the alert, never block it.

### Map pins are drawn, anchored, and never emoji

`GuardianMapMarkers` draws every non-Angel pin. The default `defaultMarker()` plus an
emoji in the title made a police station, a cluster of reported assaults and a saved safe
place look identical until tapped — on a safety map that is the whole product failing.
Hazards carry their count and grow slightly with it. Bitmaps are cached per (kind,
badge, scale) — a map re-renders on every camera move. Always pass `anchor = PIN_ANCHOR`
(from `GuardianMapMarkers.ANCHOR_X/Y`): without it the SDK centres the bitmap on the
coordinate and every pin sits half its height north of what it marks.

`AngelLocationMarker` holds **one** `MarkerState` for the life of the screen and
interpolates toward each fix, so Angel walks with the user instead of rematerialising
once a second; a jump over ~180 m snaps, because gliding across a city lies about where
she was in between. Tapping her pulses the bitmap and shows `AngelWhereAmI` — a card in
the top overlay, not a map info window, which is unreadable at low zoom and off-screen
whenever the camera follows her.

`MarkerDetailSheet` and `WalkingDirectionsCard` take a nullable model and own their own
animation, so a caller never has to keep a dismissed value alive to stop the card
emptying mid-exit.

Anything pinned to the bottom of the map must clear `BottomBarClearance`, and more while
a route sheet is up: the floating nav draws over map content, so a sheet at the bottom
edge hides behind it and one on top of it swallows taps meant for a tab.

### A finished recording names itself

`SessionSummaryAgent` runs *after* `recorder.finish()`, on the teardown scope, and
rewrites the session's title and summary. Separate from the live agents because it needs
the whole session — which does not exist until recording stops — and because nothing
waits on it, so it cannot delay an alert.

`SessionNarrator` does the work: pure, deterministic, **not** a language model — an LLM
wants a gigabyte of RAM and seconds on-device, or a round trip carrying a transcript of
someone's worst night. Two rules: **describe, never diagnose** ("a sound like a slap or
impact", never "you were assaulted"), and raw AudioSet class names never reach the user
(`interpretSound` is that layer). Key moments are derived on read.

### Walking a route happens on the map, and keeps going

"Walk with me" used to navigate to Home — the one screen that does not show the route
just chosen. It now enters a walking mode *on the map*: only the chosen polyline is
drawn, the search bar becomes one instruction at a time, the camera follows.

`WalkDirections` derives turns from the polyline, because these routes are scored
corridors with no step list. Real distances and directions, and it **must never invent a
street name** — a confident wrong name at night is worse than none. Off-route measures to
the nearest *segment*: with sparse vertices, nearest-vertex called someone walking down
the middle of the corridor 100 m adrift.

`walkingRouteId` used to be `remember`ed in the map composable, so switching tabs
silently ended the navigation she was relying on — invisibly, which is what made it
serious. The active trip now lives in Room (`TripRepository`, one `InProgress` row at a
time) and `GuardianNavigationService` — a `location` FGS, separate from the microphone
one so a walk survives standing the mic down and a recording survives arriving — keeps
the location stream alive with the app in a pocket. Re-entering the map rebuilds the
destination from the trip; `START_STICKY` plus the open row makes a process death
mid-walk recoverable.

Arrival is **close enough and still there** (`ArrivalDetector`: 40 m, 20 s dwell): firing
early texts a guardian while she is still two minutes away. A fix worse than 60 m cannot
trigger it, but one bad fix does not reset a dwell already served. Use
`observeNavigationLocation()`, never `observeLocation()` — the latter's five-metre
displacement filter stops emitting the moment she stops walking, so the dwell that proves
she arrived could never complete.

The arrival text goes through the same `GuardianNotifier` as an alert, so it obeys the
same permission reality, and the trip records whether it went out.

### A stationary user still leaves breadcrumbs

The location stream only fires after five metres of movement, so someone stopped by a
stranger or held somewhere produced no points and the trail ended wherever she last
walked. The service now writes one every minute regardless — that gap in the record was
the shape of the incident.

### Stubs must never fake success

A stand-in that reports a match makes hands-free look like it works, which for a safety
app is dangerous to demo. When a model is unavailable the UI says so and does not offer
an Arm button that arms into silence.

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
~1.2 GB RAM. `HeuristicThreatAssessor` runs on every chunk and must stay instant and
auditable — its severity breakdown is what the user reads back after an alert. An LLM is
only for the ambiguous 0.30–0.60 band, over **text**, never over audio.

Escalation requires **two or more** corroborating signals; one weak signal must never
reach someone's emergency contacts. `HeuristicThreatAssessorTest` is the contract if you
retune weights.

Cloud ASR is better and cheaper and we still do not use it on the live path: audio never
leaving the device is the product promise, and connectivity fails where she needs it most.

### Audio package

`audio/` holds one class per tier — `SpeechPipeline` (the interfaces and the rationale),
`AudioFeatures`, `SentencePieceTokenizer`, `SherpaWakeWordDetector`, `SherpaTranscriber`,
`SherpaSpeakerIdentifier`, `YamnetAudioTagger`, `HeuristicThreatAssessor` — and
`service/GuardianAudioSession` owns the single `AudioRecord`, phase-routed, with
`route()` as the test seam.

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
to the domain model and push it through the repository. Sample data lives in `*Samples`,
never inline in a preview, so previews and the running app cannot drift.

Secrets are modelled by **status, not value**: `VoiceProfile` carries a clarity score, not
the voiceprint; `DisarmPin` carries whether a PIN is set, not the PIN.

Room is at **version 5** with exported schemas and real migrations in
`GuardianDatabase.MIGRATIONS`. No destructive migration — losing a user's guardians and
codewords is a safety regression, not an inconvenience. Transcripts, the voiceprint and
the disarm PIN are **never uploaded**; `CloudSync` uses hand-written maps so a new column
cannot start syncing by accident.

`GuardianRepository` and `RouteRepository` are **still in-memory fakes**, so the Home
snapshot is sample content. This is why the "Not set up yet" card reads codewords and
guardians from their own Room repositories rather than from the snapshot — do not
"simplify" it to use the snapshot.

## Navigation

One `NavHost`; routes are constants in `Routes`; tabs in `TopLevelTab`. Three shells:

- **Onboarding** slides horizontally with no bottom bar — a tab bar invites the user out
  of a wizard that must complete in order.
- **Main tabs** cross-fade; a slide would imply a hierarchy peers do not have.
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

Settings recalibration uses `Routes.SETTINGS_VOICE_RECALIBRATE`, separate from the
onboarding `VOICE_CALIBRATION`: reusing the onboarding route dropped the user into the
rest of the wizard.

## Design system

Theme lives in `ui/theme/`. Two accessors, no third: `MaterialTheme.colorScheme` /
`.typography` / `.shapes` for standard Material roles, and `GuardianTheme.colors` /
`.spacing` / `.shapes` / `.type` / `.windowSizeClass` for brand tokens Material has no
slot for. **Never hard-code a hex value, dp spacing or font in a component** — if a token
is missing, add it to the theme.

Paired colours, accessibility floors, icon and shape conventions, and the Compose traps
this codebase has already hit: **[docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md)**. Read it
before touching a screen. The one that bites hardest: borrowing an unrelated `on*` role
looks fine in light mode and renders at ~1.3:1 in dark, so run the unit tests after any
colour change — `ColorContrastTest` is what catches it.

## Angel (the mascot)

`ui/mascot/` — a Compose-canvas mascot with five tiers: `Resting`, `Sanctuary`,
`Cautious`, `Warning`, `Critical`, declared side by side in `AngelStyles.kt`.

Derive mood only through `AngelMood.fromScore(score, atSafeHaven, isArmed, inDuress)` —
picking one by hand makes two surfaces disagree about how worried Angel is. She is
decorative; pass `contentDescription` only where she is the sole carrier of a message,
which should be nowhere.

## Home screen

The safety score sits directly under Angel, above everything optional — it used to sit
below the setup cards, putting the one number she opens the app to see below the fold.

The app bar's left action is **record/stop**, not a quick alert: that put "call my
emergency contacts" one stray tap from the top of the screen with no hold and no undo.

One `GuardianMode` drives all three states: `Standby` (mic dormant) · `Listening` (armed,
warm hero plus telemetry) · `Recording` (timer, waveform, transcript, who was alerted,
full-width Stop).

The duress trigger is a **3-second hold**; stopping is an immediate tap. Do not make these
symmetrical — arming is deliberate, standing down is not.

A manual record path must always exist and depend on **nothing but `RECORD_AUDIO`** — no
wake word, no keyword model, no notification permission.
`GuardianListeningService.record()` arms and records in one step, and is what still works
when everything clever has failed. Offered both at home and mid-walk.

## Safety score

A probability estimate from public crime data, time of day, distance from safe base,
safe nodes and lighting. Lead with the band and a one-line rationale; the percentage is
secondary and every factor is listed. **Never present it as a guarantee.** Demographic
factors (race, age) are opt-in, off by default, and never inferred.

## Testing notes

- `GuardianAudioSession.route()` and `armForTest()` drive the phase machine with recorded
  audio instead of a microphone. The fixture is
  `app/src/androidTest/assets/speech_light_up.wav` (sherpa's clip, contains "LIGHT UP");
  registering that as the wake phrase means a detection can only come from the model
  genuinely matching audio.
- Prefer an instrumented test against a real repository over UI automation, which when
  tried mostly tested the emulator's keyboard.
- The **emulator can no longer hold the app** — the APK is 269 MB and installing needs
  ~900 MB free on `/data`; each failed attempt also strands a ~250 MB staged session
  (`pm install-abandon`). On-device checks run on Ida's unlocked Pixel: `adb shell input`
  works there, but it is her phone, so aim every tap. Confirm foreground with
  `dumpsys activity activities | grep topResumedActivity`, and dismiss the IME before
  tapping a bottom bar — it covers the bottom ~40%.
- `uiautomator dump` works despite Angel's infinite animations; Espresso idling-resource
  waits are what time out.

## Current state and what is next

Done: UI, navigation and auth gate; the mascot; Room v5 with real migrations; the speech
cascade **proven on device**; session recording with transcripts, trails, analytics,
export, deletion and an after-the-fact summary; the Berkeley map with live Maps Compose,
themed pins, Places search, safe places, derived walking directions, persistent
navigation, arrival detection and a journeys log; alerting by SMS with delivery receipts,
a WhatsApp fallback, an arrival text and a call to the first guardian. See
`docs/BERKELEY_*.md`.

Next, roughly in order:

1. **Tune detection thresholds against real speech.** Only Ida can do this — nothing
   automated can say how the wake word behaves with her voice, through a pocket, with a
   television on. She has not yet said it aloud to an armed build.
2. **Tier 3 reasoning** — an LLM assessor over text for the ambiguous 0.30–0.60 band.
3. **Room-back `GuardianRepository` and `RouteRepository`.** Home still reads
   `FakeGuardianRepository`, so its mascot and score bypass the
   `GuardianSafetyStateResolver` that Map already uses.
4. **Navigation polish** — no camera animation to a selected path, no Angel chat-bubble
   directions.
5. **APK size** — 269 MB with models bundled; needs first-run download or Play Asset
   Delivery before release. It no longer fits on a near-full emulator.
6. **Localise** the inline copy into `strings.xml`.

Firebase code is complete but **no Firebase project exists yet** — Ida must create it and
drop in `google-services.json`. The app works fully without it
(`FirebaseAvailability.isConfigured` gates every path); never make it a hard dependency.

## House rules

- Match the surrounding code: this repo comments the **why**, not the what, and explains
  the failure a piece of code prevents. Keep that.
- Never weaken a test to make it pass. The instrumented audio tests exist because they
  caught bugs everything else missed.
- Do not commit or push unless asked. Models live in `app/src/main/assets/`; extract
  archives and delete `test_wavs/` before committing one. ABI filters are `arm64-v8a` +
  `x86_64` — do not re-add `armeabi-v7a`/`x86` (57 MB for nobody).
- Report honestly. If a test fails, say so with the output; if something is unverified,
  say which part and why.
