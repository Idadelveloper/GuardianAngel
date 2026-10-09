---
name: guardian-angel-context
description: Product, design-system, audio-stack and architecture context for the Guardian Angel women's-safety Android app. Load before building or changing any screen, component, colour, type style, data model or audio stage in this repo, and before answering questions about what the app does, its wake word and codeword tiers, voice enrolment, safety score or UI conventions.
---

# Guardian Angel — working context

A women's-safety Android app in Kotlin and Compose. A spoken **wake word** starts an
on-device recording that is transcribed and interpreted; guardians are alerted with the
user's location if things are genuinely escalating. Target users: women 18+, alone and
uneasy.

## Read `AGENTS.md` first

**`AGENTS.md` in the repo root is the canonical context, shared by every coding agent on
this project** (Claude Code, Codex, Gemini CLI, Junie). Read it before touching code. It
carries:

- the three product constraints that settle design arguments
- the two kinds of spoken trigger, and why merging them is dangerous
- what Android actually permits for background microphone access
- **the invariants that were paid for in bugs** — the fixed embedding window, ordered
  native teardown, probed-not-cached permissions, the voice gate degrading off, unigram
  tokenisation, and stubs never faking success
- the speech cascade, the audio package map, and the architecture seams
- the design-system tokens, paired colours and accessibility floors
- navigation shells, the mascot rules, the safety score
- current state, what is next, and what only Ida can test

This skill deliberately does **not** restate those facts. An earlier version of this file
did, drifted out of date, and ended up pointing at four files that no longer existed
(`KeywordSpotter`, `LiteRtKeywordSpotter`, `StubKeywordSpotter`, `WakeWordEngine`) while
describing a tokeniser that had been replaced. One canonical file, many thin pointers.

If you learn something durable about this codebase, add it to `AGENTS.md`, not here.

Deeper references: `README.md` (product), `docs/SPEECH_STACK.md` (model choices and
benchmarks), `docs/DATA_AND_AUTH.md` (storage and auth).

## Claude-specific working notes

**Verify, do not assume.** This repo has repeatedly had bugs that every component-level
test passed over:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest          # 34 tests, includes the WCAG contrast guard
./gradlew :app:connectedDebugAndroidTest  # 20 tests, needs a device
./gradlew :app:lintDebug
```

Run the **instrumented** suite for any change under `audio/` or `service/`. It is the only
suite that has ever caught a real bug there, and it has caught several: a tokeniser that
made detection impossible, an embedding-length mismatch that would have rejected the
enrolled user on every wake, and a native use-after-free on the disarm path.

**Reading test results.** `connectedDebugAndroidTest` writes JUnit XML to
`app/build/outputs/androidTest-results/connected/`; unit tests to
`app/build/test-results/testDebugUnitTest/`. Parse the XML rather than scrolling Gradle
output — per-device results are separate files, and a failure can be truncated in the
console. **A failure with an empty message is usually a native crash**, not an assertion:
check `adb logcat` for `F DEBUG` tombstone lines before theorising.

**Measure before diagnosing.** Twice in this repo a plausible theory about model behaviour
was wrong, and a throwaway instrumented test that logged actual cosine similarities
settled it in one run. Write the diagnostic, read the numbers, then fix — and delete the
diagnostic afterwards.

**Devices.** Ida's physical Pixel 7a is `34041JEHN26942`; the emulator is
`Pixel_8a_API_35` (`emulator-5554`). Prefer the emulator for anything that drives the UI:
the Pixel has a secure lock screen and taps silently land on the lockscreen once it
sleeps. Better still, prefer an instrumented test against a real repository over UI
automation — driving the UI was tried here and mostly tested the emulator's keyboard. See
the testing notes in `AGENTS.md` for the specific traps.

**Scratchpad.** Use the session scratchpad directory for pulled databases, UI dumps and
diagnostics, never the repo.

**Ida's working preferences.** She wants genuine research and an honest comparison of
alternatives before a tool or model choice, followed by an explicit list of actions she
has to take herself. Say plainly which parts are verified and which are not.
