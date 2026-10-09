# Guardian Angel

An Android safety companion for women who find themselves alone and uneasy — walking home
at night, meeting a stranger for the first time, stuck in a group that has started to
feel wrong.

Guardian Angel listens for a **codeword**. Say it, and the app quietly starts recording
and transcribing, works out from the conversation whether the situation is actually
escalating, and — only if it is — alerts the people you chose and shares your location.
No fumbling for a phone, no obvious panic button, no sound.

> **Status:** the full interface is built and navigable — Angel, four tabs, the setup
> wizard and every sub-screen, running on sample data. What is *not* built is everything
> behind it: the wake-word model itself, speech recognition, the escalation model,
> persistence, real maps and live location. Treat it as a working prototype of the experience, not of the system. See
> [Roadmap](#roadmap).

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

When a codeword fires, the app records, transcribes, and attributes speech to separate
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

Four tabs, a setup wizard, and three stacked sub-screens.

### Home

Two faces of one screen, chosen by whether a guarded walk is under way:

| State | What it shows |
|---|---|
| **Sanctuary** | At a safe haven. Angel rests, the gauge is pinned at 100%, and the only prominent action is starting a walk. |
| **Out & about** | A journey is live. Angel turns watchful, the gauge goes live with lighting and arrival tiles, and the duress trigger and check-in take over. |

Recording cuts across both: when a trigger fires, a recording panel expands above
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

> The map itself is `RouteCanvas`, a stylised Compose canvas. There is no Maps SDK key in
> this project yet, and shipping a half-wired map view would be worse than an honest
> abstraction. Routes arrive as normalised 0..1 points, so swapping in a real map means
> replacing one composable.

### Activity

A log of monitored sessions and a movement-intelligence rollup behind one segmented
control. Tapping a session opens the full diarized timeline — who said what when, which
sounds the acoustic model flagged, and what Angel did about it.

The design brief had the log and the analytics as separate screens. Folding them into one
tab keeps the bottom bar at four entries and means the user does not have to remember
which tab holds which half of the same subject.

### Settings

Profile, a word from Angel, then the safeguard list. Each row reports live state in its
subtitle, so the whole setup is auditable without opening anything. Three sub-screens
manage codewords, guardians, and voice sensitivity.

### Onboarding

Sign up → permissions → voice → guardians → codewords. Each step is a wizard page with a
pinned call to action and Angel explaining what she needs and why.

Two places where the reference was simplified deliberately:

- **Voice calibration** carried a countdown, a progress bar, three telemetry chips and
  two transport controls at once. During setup that reads as a studio console; it is now
  one prompt, one button, one progress ring, with clarity chips appearing only once there
  is something real to report.
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
  Fake*Repository           in-memory stand-ins, same shapes the real sources will emit
  *Samples                  sample content shared by the fakes and every @Preview
audio/
  AudioFeatures             log-mel + FFT front end, shared by any keyword model
  KeywordSpotter            the model seam; LiteRT and stub implementations
  WakeWordEngine            capture, sliding windows, matching, refractory period
service/
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

| Repository | Owns | Likely storage |
|---|---|---|
| `AccountRepository` | profile, onboarding progress, voice profile, disarm PIN | **Room + keystore** (done) |
| `ContactsRepository` | the trusted circle | **Room** (done) |
| `CodewordRepository` | the four tiers and their phrases | **Room** (done) |
| `ActivityRepository` | sessions, transcript lines, analytics rollups | Room; analytics as a query |
| `RouteRepository` | destinations and route planning | Room + routing service |
| `ListeningRepository` | wake word, enrolment, sensitivity, permission state | **Room** (done) |
| `AuthRepository` | who is signed in, and how | **Firebase, or local** (done) |
| `GuardianRepository` | the live snapshot the home screen renders | composed from the above + sensors |

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
| 0 · VAD | always | Is anyone speaking? | ~1 MB, negligible |
| 1 · Wake word | while armed | Should I start recording? | 5 MB int8, few ms/window |
| 2a · Streaming ASR | while recording | What is being said | 119 MB, RTF ≈ 0.05 |
| 2b · Audio tagging | while recording | Scream, glass, raised voices | ~4 MB |
| 2c · Diarization | while recording | How many voices, whose | ~8 MB |
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

Built: every seam — `SpeechTranscriber`, `AudioTagger`, `ThreatAssessor`,
`SituationSnapshot`; the mel/FFT front end; `KeywordSpotter` with a LiteRT
implementation; the wake-word engine; the microphone foreground service; and
`HeuristicThreatAssessor` with tests pinning both failure modes — staying quiet when
something is happening, and crying wolf when nothing is.

The wake word runs on **sherpa-onnx keyword spotting** — Apache-2.0, 3.3 M parameters,
open vocabulary, so any phrase registers at runtime with no retraining. `BpeTokenizer`
turns a typed phrase into the tokens it expects. Phrase detection and speaker identity
stay independent: the spotter decides *the phrase was said*, the CAM++ voiceprint decides
*she said it*.

Tiers 0–2c run end to end on device: one `AudioRecord` feeds the wake-word spotter while
waiting, and a detection flips the same stream into the transcriber, audio tagger and
speaker identifier. Still pending: tier 3's LLM assessor for the ambiguous band,
persistence, and tuning thresholds against real speech. APK is 234 MB with models
bundled — fine for sideloading, over Play's ceiling. Checklist:
**[§8 of the speech stack doc](docs/SPEECH_STACK.md#8-what-you-need-to-do)**.

### Tech### Tech

Kotlin · Jetpack Compose (BOM 2026.02.01, Material 3 1.4.0) · Navigation Compose 2.10.2 ·
Coroutines + Flow · ViewModel · LiteRT 1.4.2 · sherpa-onnx 1.13.8 ·
`minSdk` 24, `targetSdk` 37

## Building

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests, including the contrast guard
./gradlew :app:lintDebug            # lint, including accessibility checks
./gradlew :app:installDebug         # install on a connected device
```

Seventeen `@Preview` functions across thirteen files cover every screen, including all
three home states, both map states and the four onboarding steps.

---

## Roadmap

- [x] Design system, theme and component library
- [x] Angel mascot — five animated tiers driven by the safety score
- [x] Navigation: four tabs, setup wizard, stacked sub-screens
- [x] Home — sanctuary, out & about, and recording states
- [x] Map — standby and safest-route comparison (stylised canvas)
- [x] Activity — session log, diarized transcript, movement insights
- [x] Settings — hub plus codewords, guardians and voice sub-screens
- [x] Onboarding — account, permissions, voice, guardians, codewords
- [x] Domain models and repository contracts for every feature area
- [ ] **Localise the new screens.** The theme, navigation and original Home copy live in
      `strings.xml`; copy added with the Angel screens is still inline in the composables
      and needs a pass before any non-English build.
- [x] Hands-free plumbing — mel/FFT front end, keyword-spotter seam, sliding-window
      engine, microphone foreground service, permission flow and UI
- [x] Speech-stack architecture — tiered pipeline, all seams, heuristic reasoning tier
- [x] sherpa-onnx integrated; ASR, wake word, VAD and speaker models in place
- [x] Wake word on sherpa KWS — Apache-2.0, open vocabulary, verified loading on device
- [x] Wake word wired end to end — detection starts a recording session, proven on
      device by feeding recorded speech through the real capture path
- [x] YAMNet audio tagging — loading on device, danger classes mapped
- [x] sherpa-backed transcriber (VAD + Moonshine) and speaker diarization
- [ ] APK size: move models to first-run download or Play Asset Delivery
- [x] Speaker verification so only your voice wakes Angel — enrolment records real
      audio, the voiceprint is CAM++ and encrypted at rest, and the gate engages only
      when the voiceprint is consistent enough to trust
- [ ] LLM-backed reasoning for the ambiguous severity band
- [x] Room database — twelve tables, exported schemas, Keystore-encrypted secrets
- [x] Anonymous accounts, with email/password and phone linking ready for Firebase
- [x] Opt-in cloud backup that cannot upload transcripts
- [x] Contact picker, and real runtime permission requests in onboarding
- [ ] Room-back the remaining repositories (sessions, analytics, routes) — the home
      snapshot is still sample content, which is why the setup card reads live state
      from the repositories that are real rather than from the snapshot
- [ ] SQLCipher for the whole database, not just the credential columns
- [ ] Real Maps SDK behind `RouteCanvas`, plus live location
- [ ] Speech recognition, diarisation and on-device codeword spotting
- [ ] AI escalation model over the live transcript
- [ ] Real safety-score model over public crime data
- [ ] Background service, notifications and the accidental-trigger flow
- [ ] Transcript export, annotation and deletion
- [ ] Wearable companion

## Privacy

This app records audio and tracks location — the two most sensitive permissions a phone
can grant. The intended design: recording only after an explicit trigger, transcripts
encrypted at rest, the user able to delete any transcript permanently, demographic factors
opt-in and off by default, and location shared only with contacts the user named and only
while an alert is live.

None of that is implemented yet. Treat the current build as a UI prototype and do not put
real personal data into it.
