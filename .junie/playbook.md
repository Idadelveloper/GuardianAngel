# Guardian Angel — Junie playbook

Junie combines root **`AGENTS.md`** with this file, so everything in `AGENTS.md` already
applies: the product constraints, the two-kinds-of-spoken-trigger rule, the Android limits
on background microphone access, the hard-won invariants, the design-system tokens and the
current state of play. **Read `AGENTS.md` first.** This file adds only what is specific to
working inside the IDE.

Shared facts belong in `AGENTS.md` and nowhere else — a stale duplicate of this project's
context has already sent an agent at four files that no longer existed.

## Run configurations worth using

| Task | Gradle |
|---|---|
| Build | `:app:assembleDebug` |
| Unit tests (34) | `:app:testDebugUnitTest` |
| Instrumented tests (20) | `:app:connectedDebugAndroidTest` |
| Lint | `:app:lintDebug` |

The instrumented suite needs a connected device and is the only suite that has caught a
real bug in the audio path. Run it for any change under `audio/` or `service/`.

## Working in the IDE on this repo

- **Compose previews are the fast path for UI work.** Every home state is previewed in
  light and dark, and sample content comes from `*Samples` objects shared with the fake
  repositories — so a preview and the running app cannot drift. Add sample data there, not
  inline in a `@Preview`.
- **Room schemas are exported** to `app/schemas/`. Changing an entity means adding a
  `Migration` in `GuardianDatabase.MIGRATIONS` and bumping the version; the build fails if
  a schema change has no migration. Never switch on destructive migration — losing a
  user's guardians and codewords is a safety regression.
- **KSP runs for Room.** If the IDE shows unresolved generated DAO symbols, sync Gradle
  rather than editing generated output. `android.disallowKotlinSourceSets=false` in
  `gradle.properties` is required for KSP under AGP 9; do not remove it.
- **Two dependency pins are deliberate** and the IDE will offer to upgrade both: LiteRT is
  held at 1.4.2 and the Firebase BoM at 34.19.0, because the newer majors ship Kotlin 2.4
  metadata that will not resolve against this project's Kotlin 2.2.10. Decline those
  upgrade suggestions.
- **Do not accept an inspection that inlines a theme value.** Hex colours, dp spacing and
  fonts come from `GuardianTheme` / `MaterialTheme`; if a token is missing, add it to the
  theme. `ColorContrastTest` fails the build on a contrast regression.
- **`app/src/main/assets/` holds ~165 MB of models.** Do not open, index-dump or summarise
  them; `docs/SPEECH_STACK.md` says what each is and why it was chosen.

## If you are asked to continue the audio work

The next step is threshold tuning against real speech, which **only Ida can do** — no
automated test can tell you how the wake word behaves with her voice, through a pocket,
with a television on, or whether the speaker threshold separates two real people. Do not
substitute a synthetic proxy for that: resampling a recording to fake a second speaker was
tried and the similarity curve was non-monotonic (×1.15 → 0.18 but ×1.35 → 0.76), so it
proves nothing.

What you *can* do without her: items 2–5 of the "what is next" list in `AGENTS.md`.
