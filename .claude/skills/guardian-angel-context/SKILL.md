---
name: guardian-angel-context
description: Product, design-system and architecture context for the Guardian Angel women's-safety Android app. Load before building or changing any screen, component, colour, type style or data model in this repo, and before answering questions about what the app does, its codeword tiers, safety score, or UI conventions.
---

# Guardian Angel — working context

A women's-safety Android app. A spoken **codeword** makes the app record, transcribe and
evaluate the situation, then alert chosen contacts with the user's location if things are
genuinely escalating. Target users: women 18+ in the US, alone and uneasy.

Read `README.md` for the full product description. This file is the working context you
need before touching code.

## Three constraints that decide arguments

1. **Discreet.** Help arrives without visibly asking for it. No sounds, no flashes, no
   obvious panic button. If a change makes activation more conspicuous, it is wrong.
2. **Tolerant of mistakes.** Codewords are ordinary words and will be said by accident.
   Escalation is always cancellable, stopping is always one tap with no confirm dialog.
3. **Calm.** The user may already be frightened. Blush white and soft rose, never alarm
   red as the default. Micro-interactions breathe; they never strobe — except Angel's
   critical tier, where a strobe is the point.

When a decision is genuinely ambiguous, these three win over visual novelty.

## Codeword tiers

`CodewordTier`: `Safe` (cancel false alarm) → `Caution` (transcribe silently, notify
nobody) → `Danger` (alert circle + location) → `Emergency` (call 911 + alert circle).
Always present them in that order; the grid sorts by `CodewordTier.entries`, not by
whatever the data layer returns.

## Home screen states

One `GuardianMode` drives all three: `Standby` (mic dormant, quiet surface) ·
`Listening` (armed, warm gradient hero + telemetry) · `Recording` (panel expands at top
with timer, waveform, transcript, who was alerted, and a full-width Stop button).

The duress trigger is a **3-second hold**; stopping is an immediate tap. Do not make
these symmetrical — arming is deliberate, standing down is not.

## Safety score

A probability estimate from public crime data + time of day + distance from safe base +
safe nodes + lighting. Lead with the band and a one-line rationale; the percentage is
secondary and every factor is listed. Never present it as a guarantee.

Demographic factors (race, age) are **opt-in and off by default**. Do not add them to the
defaults, and do not infer them.

## Angel (the mascot)

`ui/mascot/` — a Compose-canvas mascot with five tiers: `Resting`, `Sanctuary`,
`Cautious`, `Warning`, `Critical`. Never add a sixth without updating `AngelStyles.kt`,
which declares all five side by side.

Derive mood only through `AngelMood.fromScore(score, atSafeHaven, isArmed, inDuress)` —
never pick a mood by hand in a screen, or two surfaces will disagree about how worried
Angel is. Angel is decorative by default; pass `contentDescription` only where she is the
sole carrier of a message, which should be nowhere.

## Navigation

`ui/navigation/` — one `NavHost`, routes as constants in `Routes`, tabs in
`TopLevelTab`. Three shells:

- **Onboarding** slides horizontally, no bottom bar (it is a wizard — a tab bar invites
  the user out of a flow that must complete in order).
- **Main tabs** cross-fade (a slide implies hierarchy peers do not have).
- **Stacked sub-screens** slide in from the right.

The bottom bar lives in the NavHost, not in screens, so exactly one place decides when it
is visible. Use `GuardianTabScaffold` / `GuardianStackScaffold` / `GuardianWizardScaffold`
rather than hand-rolling insets — they own window insets, max content width and the
floating-nav clearance.

## Architecture

```
di/AppContainer            the object graph; the single swap point for persistence
domain/model               plain Kotlin, no Android types
domain/repository          one interface per feature area
data/Fake*Repository       in-memory stand-ins + *Samples shared with @Preview
ui/…                       theme, mascot, components, icons, navigation, feature packages
```

Screens read one snapshot and never touch a data source. Add a field to the domain model
and surface it through the repository — do not reach around it. Put sample data in
`*Samples`, never inline in a preview, so previews and the running app cannot drift.

Secrets are modelled by *status*, not value (`VoiceProfile` has a clarity score, not the
voiceprint). Keep it that way.

## Design system — the rules that matter

Theme lives in `ui/theme/`. Two accessors, no third:

- `MaterialTheme.colorScheme` / `.typography` / `.shapes` for standard Material roles
- `GuardianTheme.colors` / `.spacing` / `.shapes` / `.type` / `.windowSizeClass` for brand
  tokens Material has no slot for

**Never hard-code a hex value, dp spacing value, or font in a component.** If a token is
missing, add it to the theme rather than inlining it.

### Paired colours

Accent tokens invert between light and dark. Always use the paired content colour:

| Background | Content |
|---|---|
| `colors.accentSoft` | `colors.onAccentSoft` |
| `colors.accentWarm` | `colors.onAccentWarm` |
| `colors.activeContainer` | `colors.onActiveContainer` |
| `colors.safeContainer` | `colors.onSafeContainer` |
| `materialColors.primaryContainer` | `materialColors.onPrimaryContainer` |

Borrowing an unrelated `on*` role is the single most likely bug here: it looks fine in
light mode and renders at ~1.3:1 in dark. `ColorContrastTest` catches it — run
`./gradlew :app:testDebugUnitTest` after any colour change.

### Accessibility floors

4.5:1 for text, 3:1 for any non-text element that carries meaning or identifies a
control. Three design-document values fail these and have accessible siblings already —
use `colors.focusRing` (not raw rose) for focus, `colors.borderControl` (not
`borderDefault`/`borderEmphasis`) for control outlines, and `colors.iconMuted` (not
espresso at 45%) for inactive nav icons. `borderDefault`/`borderEmphasis` are decorative
dividers only.

Colour is never the only signal — pair every status with a label and a distinct glyph.

### Other conventions

- Icons: `GuardianIcons`, stroke-based 24×24 with round caps. Add to that object rather
  than pulling in Material's filled glyphs.
- Shapes: `shapes.pill` for buttons/chips, `.lg` (16dp) cards, `.xl` (24dp) sheets and
  hero cards, `.md` (12dp) inputs.
- Standard buttons are 52dp; in-card pill actions are 44dp.
- Elevation is ambient warm glow + tonal layering, via `guardianCardElevation`,
  `guardianFloatingElevation`, `focalHalo`, `ambientGlow`. Not Material tonal elevation —
  layering both double-tints the surface.
- No dynamic colour. The palette is a safety signal; wallpaper must not repaint it.
- Inside a vertically scrolling column, build grids from chunked `Row`s. A
  `LazyVerticalGrid` nested in a same-orientation scroll will crash.
- Window insets go **outside** the scroll modifier, or the padding scrolls away.

## Architecture

```
domain/model       GuardianSnapshot and friends — no Android types
domain/repository  GuardianRepository — the only seam to data
data/              FakeGuardianRepository (in-memory) + GuardianSamples
ui/home/           HomeScreen, HomeViewModel, components/
```

Screens read one `GuardianSnapshot` and never touch a data source. The repository is
constructed once in `GuardianAngelApp`. When adding a field the UI needs, put it on the
domain model and surface it through the snapshot — don't reach around the repository.

`GuardianSamples` is shared by the fake repository and every `@Preview`, so previews and
the running app can't drift. Add sample data there, not inline in a preview.

## Known gotchas

- Angel's infinite animations keep the window non-idle, so `uiautomator dump` and any
  Espresso idling-resource wait will time out. Drive UI checks with coordinates and
  screenshots, or stub the mascot in tests.
- Copy added with the Angel screens is inline, not in `strings.xml` (tracked in the
  README roadmap). New user-facing text should go to resources where practical.
- `RouteCanvas` is a stylised stand-in for a real map — no Maps SDK key exists yet.

## Verifying

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest   # includes the WCAG contrast guard
./gradlew :app:lintDebug
```

Previews cover all three home states in light and dark. For visual checks prefer an
emulator (`Pixel_8a_API_35`) over the user's physical device.
