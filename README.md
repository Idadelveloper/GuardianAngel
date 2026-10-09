# Guardian Angel

An Android safety companion for women who find themselves alone and uneasy — walking home
at night, meeting a stranger for the first time, stuck in a group that has started to
feel wrong.

Guardian Angel listens for your **wake word**. Say it, and the app quietly starts
recording and transcribing, works out from the conversation whether the situation is
actually escalating, and — only if it is — alerts the people you chose and shares your
location. Your **codewords**, said while it is already recording, tell it what to do.
No fumbling for a phone, no obvious panic button, no sound.

> **Status:** the interface is complete and navigable — Angel, four tabs, the setup
> wizard and every sub-screen. Hands-free activation **works on device**: the wake word
> starts a recording, the voiceprint can gate it to your voice, and the recording is
> transcribed, tagged and diarized locally. Storage and accounts are real (Room, twelve
> tables, encrypted secrets).
>
> Still outstanding: tier-3 LLM reasoning for ambiguous situations, Room-backed session
> history, a real map and live location, and threshold tuning against real speech — which
> only testing in real rooms can settle. See [Roadmap](#roadmap).

---

## Why it works this way

Three constraints shaped every decision in this app:

**It has to be discreet.** The whole point is getting help without visibly asking for it.
So the trigger is a spoken word rather than a tap, alerts dispatch silently, and nothing
flashes or chimes when the guardian activates.

**It has to tolerate mistakes.** Codewords are ordinary words — you will say "pineapple"
in a normal sentence eventually. The AI evaluates context before escalating, you get a
notification you can act on, and stopping a recording is always one tap with no
confirmation dialog. A safety app that cries wolf gets uninstalled.

**It has to stay calm.** The interface is blush white and soft rose, not red klaxons. A
person reading this screen may already be frightened; the UI's job is to lower their
heart rate, not raise it. The one place that rule bends is Angel's critical tier, where a
crimson strobe is exactly the point.

---

## Angel

The app has a mascot. Angel is a chibi guardian who sits at the top of the home screen
and mirrors the user's safety state — halo colour, wing posture, expression and aura all
shift with the score. In most sessions she is the only thing the user actually reads.

She is drawn entirely with Compose `Canvas` primitives rather than shipped as five SVGs
or Lottie files. That buys three things the asset route could not: every tier is one
component so the geometry cannot drift between moods, moods can cross-fade as the score
moves, and the whole thing costs a few kilobytes of code instead of five animation
payloads.

| Tier | When | How she looks |
|---|---|---|
| **Resting** | Mic dormant, on standby | Soft golden halo breathing at 2.5s, gentle acoustic rings |
| **Sanctuary** | At a safe haven, or 100% | Serene 4s float, rosy cheeks, golden sparkles |
| **Cautious** | 75–95% — evening, unfamiliar route | Amber halo tilted 3°, eyes scanning, wings tucked, one worry drop |
| **Warning** | 50–74% — poor lighting, acoustic anomaly | Trembling, flickering orange halo, wings raised to shield, two drops |
| **Critical** | Below 50%, or a duress trigger | Crimson strobe, red shockwaves, full enclosing wing shield, racing heartbeat |

`AngelMood.fromScore(score, atSafeHaven, isArmed, inDuress)` is the single place that
mapping lives, so every screen derives the same mood from the same inputs.

Angel is decorative by default — the card around her always says the same thing in words,
so nothing is lost if she is not perceived.

## Features

### Hands-free activation — two kinds of spoken trigger

This is the distinction the whole product rests on, and the easiest one to muddle:

| | **Wake word** | **Codewords** |
|---|---|---|
| When | Angel is on standby, nothing is recorded | Angel is already recording |
| How many | One phrase | Four, one per escalation tier |
| What it does | Wakes her and **starts** recording | Tells her **what to do** next |
| How it is matched | Always-on keyword model, ~1 MB, on a battery budget | Against the live transcript, which already exists |
| Where it is set | Onboarding step 4 · Settings → Wake word | Onboarding step 5 · Settings → Codewords |

Getting this wrong is dangerous rather than merely confusing: a user who thinks her
danger codeword wakes the app would say it into a phone that is not listening and assume
help was coming. Onboarding therefore teaches the wake word *first*, on its own screen,
with both kinds laid out side by side before a single codeword is set.

#### The four codeword tiers

Said while Angel is recording. She weighs what she hears against the tier you invoked
before acting.

| Tier | What saying it does |
|---|---|
| **Safe** | Tells chosen contacts you're okay — cancels a false alarm |
| **Caution** | Starts transcribing quietly. Nobody is notified |
| **Danger** | Alerts your trusted circle with your live location |
| **Emergency** | Calls emergency services *and* alerts your circle |

#### What Android actually allows

Hands-free listening is shaped by platform rules more than by product preference, so the
app states them plainly rather than implying capabilities it cannot have:

- `RECORD_AUDIO` is a **while-in-use** permission. Listening with the phone pocketed
  requires a foreground service typed `microphone`, which from Android 14 also needs
  `FOREGROUND_SERVICE_MICROPHONE`.
- **That service cannot be started from the background.** Not on boot, not from a
  broadcast — Android throws `ForegroundServiceStartNotAllowedException` and there is no
  exemption an ordinary app can rely on. **So Angel cannot arm herself.** You arm her
  from the home screen before you set off.
- Once armed legally from a visible screen, she **keeps listening with the app closed and
  the screen locked**. That is exactly what the `microphone` service type is for, and it
  is what makes the real scenario work: arm before you walk home, then never touch the
  phone again.
- A persistent notification is mandatory while the microphone is held. For an app
  listening to someone's surroundings that is the right thing to show anyway.
- True always-on hotword with the app closed needs `AlwaysOnHotwordDetector` and the
  SoundTrigger HAL, which are reserved for the device's default assistant. The UI says
  so instead of pretending otherwise.

Permission state is read from the system on every status emission rather than cached, so
a permission granted during onboarding is never asked for again on Home, and one revoked
in system settings shows up immediately. Caching it was a real bug: every fresh process
started by assuming nothing was granted.

Nothing that is already done is asked for again. The permissions step reports what the
system actually grants and turns its button into *Continue* once everything is there, and
a wake word or codeword that has been saved stops being prompted for. Seeded codeword
suggestions do **not** count as configured — a brand-new account has four of them, and
counting rows meant the app reported setup as finished before the user had chosen
anything.

A **Not set up yet** card sits above the hands-free card whenever a capability is
missing. Onboarding steps are all skippable, which makes this necessary rather than
decorative — a skipped step otherwise leaves the app silently unable to do part of its
job. It shows the single most consequential gap (no guardian beats no location beats no
voiceprint) with one button that fixes it, and keeps the rest behind a tap.

The home screen's **Hands-free** card is where this lives: it arms listening, names the
wake word while armed, and lists any missing permission with the one action that fixes
it. A listening feature that silently does nothing because a permission was declined is
worse than no feature at all.

### Recording & transcription

When the wake word fires, the app records, transcribes, and attributes speech to separate
voices (yours is enrolled during setup). It flags danger signals in the transcript —
repeated refusals, raised voices, sounds that imply a struggle — and uses them, together
with location and time of day, to decide whether to escalate.

Every session lands in the Activity log with Angel's summary, the peak volume, the
lowest safety score it reached, and the full diarized timeline. Each one is pinned to
where it was recorded. Review, export and delete are modelled today; annotating and
bookmarking are on the roadmap.

### Location Safety Score

A live estimate of how likely you are to run into trouble where you are right now, shown
on the home screen so you can avoid a bad situation rather than react to one.

It is built from public crime data plus contextual factors: time of day, distance from
your safe base, proximity to safe nodes (police stations, open businesses, friends'
homes), street lighting, and how long you have been away from home.

The score is a **probability estimate, not a guarantee**, so the UI leads with a plain
band — Safe Haven, Moderate Vigilance, Heightened Risk, High Risk — and a one-line
explanation, with the percentage and every contributing factor shown underneath. Colour
runs green through amber to red, warmed to fit the palette, and is never the only signal:
each band carries its own label and glyph.

Any factor beyond the defaults (time, location, destination, crime data) must be switched
on by hand. Demographic inputs are **opt-in only and off by default** — they can improve a
risk estimate, but silently profiling a user by race or age is not something an app should
do on her behalf.

### Safest-route navigation

The Map tab routes by safest path rather than shortest, using the same data as the score.
Pick a destination and it compares a well-lit corridor against the faster shortcut, with
the safety score, lighting and safe-haven count for each. Committing to a route arms the
guardian and starts the walk.

*The routing and the map surface are both simulated — see the note under [Map](#map).*

### Safe Walk

A monitored journey with an expected arrival time. If you don't check in by the ETA, your
guardians are notified automatically.

### Trusted circle

Up to five emergency contacts, ranked, each with a name, relationship and number. The
home screen shows who is actually reachable right now — knowing your top contact is
offline *before* something happens is the point. Your first guardian cannot be removed;
an empty circle would make every other feature pointless.

---

## Screens

Four tabs, a six-page setup wizard, and five stacked sub-screens — a session detail view
plus wake word, codewords, guardians and voice calibration.

### Home

Two faces of one screen, chosen by whether a guarded walk is under way:

| State | What it shows |
|---|---|
| **Sanctuary** | At a safe haven. Angel rests, the gauge is pinned at 100%, and the only prominent action is starting a walk. |
| **Out & about** | A journey is live. Angel turns watchful, the gauge goes live with lighting and arrival tiles, and the duress trigger and check-in take over. |

The safety score sits directly under Angel, above everything else, because it is the one
number you open the app to check. The app bar's left action is **record/stop** — one tap
starts a real recording, another stops it.

Recording needs the microphone **and** location. An alert that cannot say where you are
leaves your guardians with an emergency and no address, so the record button asks for
whichever is missing and starts nothing until it has both.

Recording cuts across both states: when a trigger fires, a recording panel expands above
everything with an elapsed timer, live waveform, running transcript, exactly who has been
alerted, and a full-width **Stop recording** button.

The duress trigger is a **three-second hold**; stopping is one immediate tap. Those are
deliberately asymmetric — arming dispatches a silent alert and can call police, so a
pocket brush must not fire it, but a frightened user standing down should not face a
confirmation dialog.

### Map

Standby shows the home geofence, a search bar and quick destinations. Picking one draws
both corridors — the safe route as a solid glowing line, the unlit shortcut dashed with a
warning marker — and opens a sheet comparing them. **Walk with Angel** arms the guardian
and hands off to Home's journey state.

If location is off, a banner at the top offers to turn it on — the map is the screen where
its absence is most obvious, so it is where the offer belongs.

> **The Maps API key is a placeholder.** `maps-compose` is wired in and the map composes,
> but `com.google.android.geo.API_KEY` in `AndroidManifest.xml` is still
> `AIzaSyPlaceholderGuardianAngelKey`, so no tiles will load until a real key is dropped
> in. `RouteCanvas`, the stylised canvas the map replaced, is still used for the route
> preview on a session's detail screen.

### Activity

A log of monitored sessions and a movement-intelligence rollup behind one segmented
control. Tapping a session opens the full diarized timeline — who said what when, which
sounds the acoustic model flagged, and what Angel did about it.

The design brief had the log and the analytics as separate screens. Folding them into one
tab keeps the bottom bar at four entries and means the user does not have to remember
which tab holds which half of the same subject.

### Settings

Tap your name to edit it and your number. Then a word from Angel, then the safeguard
list. Each row reports live state in its
subtitle, so the whole setup is auditable without opening anything. Four sub-screens
manage the wake word, codewords, guardians and voice calibration.

### Sign up & log in

The app opens on an auth gate. Both screens exist, and which one you see depends on
whether this device already has an account — a returning user sent to a sign-up form
either makes a second account or concludes her data is gone.

Once you are in, the auth screens are gone: entering the app clears the whole back stack,
and a relaunch resumes the session rather than asking again. Logging out ends the session
without deleting anything, so logging back in returns your guardians, codewords and wake
word intact.

Login works with no Firebase project configured. The password is salted, hashed and
Keystore-encrypted on the device, and a wrong password and an unknown address give the
same message — distinguishing them would confirm whether an address has an account on
this phone.

**Set up without an account** is still offered, one tap below. A woman downloading this
at 11pm should be able to arm a panic button before she is asked for an email address;
credentials can be added later from Settings and are linked onto the same account, so
nothing set up first is lost.

### Onboarding

Permissions → voice → guardians → **wake word** → codewords. Each step is a wizard page
with a pinned call to action and Angel explaining what she needs and why.

**Every step is skippable**, and the skip link says what skipping costs rather than a
neutral "later" — "Skip — no hands-free for now" instead of "Skip". Whatever is skipped
resurfaces on Home as a **Not set up yet** card, because a skipped step otherwise leaves
the app silently unable to do part of its job.

Two places where the reference was simplified deliberately:

- **Voice calibration** carried a countdown, a progress bar, three telemetry chips and
  two transport controls at once. During setup that reads as a studio console; it is now
  one prompt, one button, one progress ring. The clarity figure is real — it is how well
  the recorded segments agreed with each other — so a noisy room reports a low number
  rather than a reassuring one, and says plainly that voice matching will stay off until
  it improves.
- **Codewords** put all four tiers plus a PIN section on one page. That is a lot of
  consequence to absorb at once and the tiers only make sense in order, so they are
  paginated — one word, one explanation, and an example built from what you just typed.

## Design system

Plus Jakarta Sans throughout, on a soft blush canvas (`#FFF8F8`) with rose and apricot
accents and a protective midnight-navy (`#1E2238`) for type and high-contrast actions —
closer to Partiful or Luma than to a security product.

Everything lives in `ui/theme/` and is consumed through two accessors: `MaterialTheme` for
standard Material roles and `GuardianTheme` for the brand tokens Material has no slot for
(`GuardianTheme.colors.accentSoft`, `GuardianTheme.spacing.lg`, `GuardianTheme.shapes.pill`,
`GuardianTheme.type.labelSm`). No component hard-codes a hex value.

- **Type** — Plus Jakarta Sans, bundled as five static instances cut from the upstream
  variable font. Static rather than variable because `minSdk` is 24 while font variation
  settings only take effect from API 26.
- **Elevation** — ambient warm glows and tonal layering rather than hard drop shadows.
- **Icons** — a hand-built stroke set (2px, round caps); Material's filled glyphs don't
  match the spec.
- **Mascot** — Angel is part of the design system, not decoration bolted on: her tier
  colours come from the same ramp as the safety score. See [Angel](#angel).
- **Duress** — `#D50000` carries the SOS button label at 5.48:1; the brighter `#FF1744`
  is reserved for auras and strobes, where it never has to pass a text contrast check.
- **Dark mode** — a warm espresso night scheme, since this app gets used after dark.
- **Accessibility** — every on-screen colour pairing is verified against WCAG 2.1 AA by
  `ColorContrastTest`, which fails the build if a pairing regresses. Where the design
  document's values fell short for elements that *identify* a control (focus rings, form
  borders, inactive nav icons), accessible siblings were added and documented in
  `Color.kt`.

---

## Architecture

```
di/
  AppContainer              the object graph — the single swap point for persistence
domain/
  model/                    GuardianSnapshot, SafetyScore, Codeword, MonitoredSession,
                            SafeRoute, AccountSnapshot … plain Kotlin, no Android types
  repository/               one interface per feature area
data/
  local/                    Room — entities, DAOs, Room*Repository implementations
  auth/                     anonymous, email/password and phone, Firebase or local
  crypto/                   KeystoreCrypto — AES-GCM for the voiceprint and PIN
  platform/                 Android implementations of domain interfaces
  sync/                     CloudSync — opt-in backup that cannot upload transcripts
  Fake*Repository           in-memory stand-ins, same shapes the real sources emit
  *Samples                  sample content shared by the fakes and every @Preview
audio/
  SpeechPipeline            the interfaces every tier implements, and the rationale
  AudioFeatures             log-mel + FFT front end, cosine similarity
  SentencePieceTokenizer    typed phrase → tokens, Viterbi over the model vocabulary
  SherpaWakeWordDetector    keyword spotting
  SherpaTranscriber         Silero VAD + Moonshine Tiny
  SherpaSpeakerIdentifier   CAM++ voiceprint — verification and diarization
  VoiceEnroller             records audio, folds a running mean, reports clarity
  YamnetAudioTagger         521 AudioSet classes via LiteRT
  HeuristicThreatAssessor   the cheap reasoning tier
service/
  GuardianAudioSession      one AudioRecord, routed by phase
  GuardianListeningService  the microphone foreground service
ui/
  theme/                    colour schemes, type scale, shapes, spacing, elevation,
                            the green-to-red safety ramp
  mascot/                   Angel — mood model, per-tier style table, canvas renderer
  components/               buttons, cards, chips, inputs, switches, nav, scaffolds
  icons/                    GuardianIcons — stroke-based icon set
  navigation/               routes, tabs, and the single NavHost
  onboarding/ home/ map/ activities/ settings/
```

### Storage and accounts

Room, twelve tables, all hanging off one `users` row — full detail in
**[docs/DATA_AND_AUTH.md](docs/DATA_AND_AUTH.md)**. Schemas are exported to
`app/schemas/` and checked in, so every migration is a reviewable diff.

Accounts start **anonymous**: sign-in happens silently on first launch, because someone
downloading a safety app at 11pm should be protected before she is asked for an email
address. Real credentials are *linked onto* that account later so her guardians and
codewords come with her. Email/password and phone are implemented and need a Firebase
project; without `google-services.json` the app keeps a real local account and says
plainly that multi-device restore is unavailable.

Cloud backup is opt-in from Settings and uploads guardians, codewords, safe places and
session metadata. **Transcripts, the voiceprint and the disarm PIN are never uploaded** —
and `CloudSync` is written so it cannot start doing so by accident.

### Where data will live

Repositories are split by feature area rather than one god-object, so each can migrate
independently when the database lands — contacts and codewords might move to an
encrypted Room table first while analytics stays derived.

| Repository | Owns | Storage |
|---|---|---|
| `AccountRepository` | profile, onboarding progress, disarm PIN | **Room + keystore** (done) |
| `VoiceProfileRepository` | the voiceprint and its clarity | **Room, Keystore-encrypted** (done) |
| `ContactsRepository` | the trusted circle | **Room** (done) |
| `CodewordRepository` | the four tiers and their phrases | **Room** (done) |
| `ListeningRepository` | wake word, enrolment count, sensitivity | **Room** (done) |
| `AuthRepository` | who is signed in, and how | **Firebase, or local** (done) |
| `PermissionProbe` | what the system currently grants | **the system itself** — never cached |
| `ActivityRepository` | sessions, transcript lines, analytics rollups | *pending* — Room; analytics as a query |
| `RouteRepository` | destinations and route planning | *pending* — Room + routing service |
| `GuardianRepository` | the live snapshot the home screen renders | *pending* — composed from the above + sensors |

The three pending rows are still in-memory fakes, so the Home snapshot is sample content.
That is why the **Not set up yet** card reads codewords and guardians from their own Room
repositories rather than from the snapshot — a setup card built on samples would reassure
the user about things she has not actually set up.

Secrets are represented by their *status*, never their value: `VoiceProfile` carries a
clarity score rather than the voiceprint, and `DisarmPin` carries only whether a PIN is
set. The real vectors belong in the hardware keystore, and keeping them out of the domain
model means they can never reach a log, a screenshot or a backup.

Screens read one snapshot and never touch a data source. `AppContainer` is constructed
once in `GuardianAngelApp`; swapping `InMemoryAppContainer` for a persistence-backed one
is the only change needed, and no screen or view model knows the difference.

Reads are `Flow` because the data is genuinely live: the score re-evaluates as the user
moves, contacts come online, and a session accumulates transcript lines while open.

### Speech stack

Sound becomes a decision through **four tiers, each only running when the one below says
it is worth it**. Full research, benchmarks and sources: **[docs/SPEECH_STACK.md](docs/SPEECH_STACK.md)**.

| Tier | Runs | Job | Budget |
|---|---|---|---|
| 0 · VAD | always | Is anyone speaking? | 632 KB, negligible |
| 1 · Wake word | while armed | Should I start recording? | 13 MB fp32, few ms/window |
| 2a · Streaming ASR | while recording | What is being said | 118 MB, RTF ≈ 0.05 |
| 2b · Audio tagging | while recording | Scream, glass, raised voices | 3.9 MB |
| 2c · Speaker ID | while recording | How many voices, whose | 28 MB |
| 3 · Reasoning | on suspicion only | Is this escalating? | heuristic always, LLM rarely |

**Do we need an LLM?** For detection, no — and using one would make the app worse. An
MFCC+SVM distress classifier reaches ~95% detection at ~1% false alarm for 3–5% battery
over ten hours; an on-device LLM costs 2–5 s to first token and ~1.2 GB of RAM. Running
the expensive thing continuously would flatten the battery during exactly the walk home
it exists to protect, and answer *slower*.

For **judgement** on the ambiguous middle — whether *"leave me alone"* plus a stranger's
voice plus 11 pm plus an unlit street is escalation — yes. `HeuristicThreatAssessor` runs
on every chunk and escalates when signals are unambiguous; a language model is invoked
only in the 0.30–0.60 severity band, over text, never over audio.

**Chosen runtime: sherpa-onnx** (Apache-2.0) running **Moonshine Tiny**. It covers ASR,
diarization, speaker ID, VAD, keyword spotting and audio tagging in one framework, which
matters more for maintenance than any single model's WER — and the runtime choice is
worth more than the model choice anyway: sherpa-onnx is reported 51× faster than
whisper.cpp on the *same* Whisper Tiny on Android.

**Cloud is better and we are still not using it** for the live path. AssemblyAI streams
at 307 ms P50 / 8.14% WER for ~$0.21–0.46/hour — better than anything that fits on a
phone. But the app's promise is that audio never leaves the device, connectivity fails
exactly where she needs it most, and streaming someone's surroundings to a third party
during an assault is a different threat model. Cloud is legitimate only as opt-in,
after-the-fact re-transcription of a saved session.

### What is built, and what you need to supply

Tiers 0–2c run end to end on device. One `AudioRecord` feeds the wake-word spotter while
waiting, and a detection flips the same stream into the transcriber, audio tagger and
speaker identifier.

The wake word runs on **sherpa-onnx keyword spotting** — Apache-2.0, 3.3 M parameters,
open vocabulary, so any phrase registers at runtime with no retraining.
`SentencePieceTokenizer` turns a typed phrase into the tokens it expects, and a phrase the
model cannot pronounce is refused *before* it is saved rather than saving fine, looking
set up, and never firing. Phrase detection and speaker identity stay independent: the
spotter decides *the phrase was said*, the CAM++ voiceprint decides *she said it*.

This is verified rather than assumed. Twenty instrumented tests run on a physical Pixel 7a
and an emulator, feeding recorded speech through the real capture loop: detection fires,
unrelated speech does not, the voice gate accepts its owner and rejects noise and silence,
enrolment produces a usable voiceprint, re-arming after teardown works, and manual
recording works with no wake word set. Writing those tests is what found three bugs that
every component-level test had passed over — a tokeniser that made detection impossible,
an embedding-length mismatch that would have rejected the enrolled user on every wake, and
a native use-after-free on the disarm path.

Two design rules fell out of that work and are worth stating:

- **The voice-match toggle is a request, not the outcome.** The gate engages only when the
  voiceprint is consistent enough to trust, because gating on a bad voiceprint does not
  keep a stranger out — it stops Angel waking for the person she belongs to.
- **Every speaker embedding comes from one fixed 1.5 s window.** CAM++ embeddings are only
  comparable between inputs of similar duration; the same speech at 1 s versus 2 s scores
  −0.03, as if two strangers.

Still pending: tier 3's LLM assessor for the ambiguous band, Room-backed session history,
and tuning thresholds against real speech — which no automated test can settle. The APK is
240 MB with models bundled: fine for sideloading, over Play's 150 MB ceiling. Checklist of
what only you can do: **[§8 of the speech stack doc](docs/SPEECH_STACK.md#8-what-you-need-to-do)**.

### Tech

Kotlin · Jetpack Compose (BOM 2026.02.01, Material 3 1.4.0) · Navigation Compose 2.10.2 ·
Coroutines + Flow · ViewModel · LiteRT 1.4.2 · sherpa-onnx 1.13.8 ·
`minSdk` 24, `targetSdk` 37

## Building

```bash
./gradlew :app:assembleDebug              # build
./gradlew :app:testDebugUnitTest          # 34 unit tests, including the contrast guard
./gradlew :app:connectedDebugAndroidTest  # 20 instrumented tests, needs a device
./gradlew :app:lintDebug                  # lint, including accessibility checks
./gradlew :app:installDebug               # install on a connected device
```

The instrumented suite is the one that matters for the audio path — it feeds recorded
speech through the real capture loop and is the only suite that has ever caught a bug
there. Run it for any change under `audio/` or `service/`.

Twenty-one `@Preview` functions across seventeen files cover every screen, including all
three home states, both map states and all five onboarding steps.

### Working with AI coding agents

**`AGENTS.md` in the repo root is the canonical context for every agent**, and the
per-agent files are thin pointers to it on purpose:

| Agent | Reads |
|---|---|
| OpenAI Codex | `AGENTS.md` directly |
| JetBrains Junie | `AGENTS.md` + `.junie/playbook.md` |
| Gemini CLI | `GEMINI.md`, which imports `AGENTS.md` with `@./AGENTS.md` |
| Claude Code | `.claude/skills/guardian-angel-context/`, which points at `AGENTS.md` |

Keep shared facts in `AGENTS.md` alone. An earlier per-agent file carried its own copy of
the architecture notes, drifted, and ended up directing an agent at four source files that
no longer existed while describing a tokeniser that had been replaced. One canonical file,
many thin pointers.

---

## Roadmap

### Interface

- [x] Design system, theme and component library
- [x] Angel mascot — five animated tiers driven by the safety score
- [x] Navigation: four tabs, setup wizard, stacked sub-screens
- [x] Home — sanctuary, out & about, and recording states
- [x] Map — standby and safest-route comparison (stylised canvas)
- [x] Activity — session log, diarized transcript, movement insights
- [x] Settings — hub plus wake word, codewords, guardians and voice sub-screens
- [x] Onboarding — account, permissions, voice, guardians, wake word, codewords
- [x] Skippable onboarding, with a **Not set up yet** card on Home that resurfaces
      whatever was skipped and names what it costs
- [x] Auth gate — sign up and log in, both screens, with the gate gone once passed and
      sign-out that ends the session without deleting the account
- [x] Local credentials so login works with no Firebase project configured
- [x] Editable profile, and settings screens that persist what they show
- [x] Record/stop toggle in the app bar, gated on microphone and location
- [x] Safety score above the fold, never behind the setup cards
- [ ] **Localise the new screens.** The theme, navigation and original Home copy live in
      `strings.xml` (102 strings); copy added with the Angel screens is still inline in
      the composables and needs a pass before any non-English build.

### Hands-free activation

- [x] Microphone foreground service, permission flow and UI
- [x] Permission state probed from the system, never cached, so a permission granted in
      onboarding is never asked for again
- [x] Speech-stack architecture — tiered pipeline, all seams, heuristic reasoning tier
- [x] sherpa-onnx integrated; ASR, wake word, VAD and speaker models in place
- [x] Wake word on sherpa KWS — Apache-2.0, open vocabulary, phrases validated against
      the model vocabulary before they can be saved
- [x] Wake word wired end to end and **proven on device** — recorded speech through the
      real capture path starts a recording; unrelated speech does not
- [x] Speaker verification so only your voice wakes Angel — enrolment records real audio,
      the voiceprint is CAM++ and encrypted at rest, and the gate engages only when the
      voiceprint is consistent enough to trust
- [x] YAMNet audio tagging — loading on device, danger classes mapped
- [x] sherpa-backed transcriber (VAD + Moonshine) and speaker diarization
- [x] Manual recording that depends on nothing but the microphone — no wake word, no
      model, no notification permission
- [x] Instrumented suite on a physical device and an emulator covering the whole path
- [ ] Tune detection and speaker thresholds against real speech in real rooms
- [ ] On-device codeword spotting against the live transcript
- [ ] LLM-backed reasoning for the ambiguous 0.30–0.60 severity band
- [ ] The accidental-trigger flow — the disarm PIN is modelled and stored, but the
      cancel-and-stand-down journey is not built
- [ ] APK size: move models to first-run download or Play Asset Delivery

### Data

- [x] Room database — twelve tables, exported schemas, Keystore-encrypted secrets
- [x] Anonymous accounts, with email/password and phone linking ready for Firebase
- [x] Opt-in cloud backup that cannot upload transcripts
- [x] Contact picker, and real runtime permission requests in onboarding
- [x] Domain models and repository contracts for every feature area
- [ ] Room-back the remaining repositories (sessions, analytics, routes) — the home
      snapshot is still sample content, which is why the setup card reads live state
      from the repositories that are real rather than from the snapshot
- [ ] SQLCipher for the whole database, not just the credential columns
- [ ] Transcript export, annotation and deletion

### Still to come

- [x] Google Maps and a real device location source behind the Map tab
- [ ] **A real Maps API key.** The manifest ships a placeholder, so tiles do not load yet.
- [ ] Real safety-score model over public crime data
- [ ] Wearable companion

## Privacy

This app records audio and tracks location — the two most sensitive permissions a phone
can grant. So it is worth being precise about what the current build actually does, rather
than describing an intention.

**True today:**

- **Nothing is recorded until you trigger it.** While armed, roughly a second and a half
  of audio is held in a fixed buffer and continuously overwritten so the wake word can be
  matched against it. It is never written to disk.
- **Audio never leaves the device.** All six tiers run locally. `CloudSync` has no code
  path that reads the transcript tables, and its payloads are hand-written maps so a new
  column cannot start syncing by accident.
- **The voiceprint is a vector, not a recording**, is encrypted with a hardware-backed
  Keystore key, and is never uploaded. The audio it was derived from is discarded as soon
  as the vector exists.
- **The disarm PIN is hashed and Keystore-encrypted**, never stored in the clear.
- **Demographic factors are opt-in and off by default.** They are never inferred.
- **Cloud backup is opt-in** and covers guardians, codewords, safe places and session
  metadata only.

**Not true yet** — do not rely on these:

- **Transcript text is stored unencrypted** in Room. Only the voiceprint and the PIN are
  encrypted; whole-database encryption (SQLCipher) is on the roadmap.
- **There is no way to delete an individual transcript** from the UI yet.
- **Location is not wired.** The safety score and session breadcrumbs use placeholder
  values, so nothing real is shared with anyone.

Treat this as a build under active development: the privacy architecture is real, but it
is not finished, and you should not put sensitive personal data into it yet.
