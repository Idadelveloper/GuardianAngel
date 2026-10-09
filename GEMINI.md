# Guardian Angel — Gemini CLI context

Guardian Angel is a women's-safety Android app in Kotlin and Jetpack Compose. A spoken
**wake word** starts an on-device recording that is transcribed and interpreted, and
guardians are alerted with the user's location if the situation is genuinely escalating.

**The shared engineering context for every agent on this repo lives in `AGENTS.md`**, and
it is imported below. Read it before changing any screen, component, colour token, data
model or audio stage. It carries the product constraints, the Android platform limits on
background microphone access, the invariants that were paid for in real bugs, and the
state of play.

Per-agent files are kept thin deliberately. This project has already been bitten by a
context file that drifted out of date and sent an agent at four files that no longer
existed, so shared facts belong in `AGENTS.md` and nowhere else. If you learn something
durable, add it there rather than here.

@./AGENTS.md

## Gemini-specific notes

- **Verify before you report.** `./gradlew :app:assembleDebug` and
  `./gradlew :app:testDebugUnitTest` are cheap. The instrumented suite
  (`./gradlew :app:connectedDebugAndroidTest`) needs a connected device and is the only
  suite that has ever caught a real bug in the audio path — run it for any audio change.
- **`/memory refresh`** after editing `AGENTS.md` or this file, so the change is actually
  in context. `/memory show` prints the concatenated result if you want to check what the
  model is really seeing.
- Do not add a `GEMINI.md` inside a subdirectory unless that subdirectory genuinely needs
  different rules. The hierarchy is loaded most-specific-last, and a near-duplicate copy
  is how the staleness problem above started.
- `@` imports only resolve `.md` files and do not detect cycles, so keep this file
  importing `AGENTS.md` and never the other way round.
- `app/src/main/assets/` holds ~165 MB of ONNX and TFLite models. Do not read them, glob
  them into context, or try to summarise them; `docs/SPEECH_STACK.md` describes what each
  one is and why it was chosen.
