# Speech & understanding stack

How Guardian Angel turns sound into a decision, which models do which job, and why.

Written after benchmarking the 2026 landscape. Every number below is cited; where the
evidence was thin or pointed the other way from what I expected, that is said plainly.

---

## 1. The short answer

**Four tiers, each only running when the one below it says it is worth it.**

| Tier | Runs | Job | Budget |
|---|---|---|---|
| 0 · VAD | always | Is anyone speaking? | ~1 MB, negligible |
| 1 · Wake word | while armed | Should I start recording? | ~1 MB, few ms/window |
| 2a · Streaming ASR | while recording | What is being said | ~40 MB, RTF ≈ 0.05 |
| 2b · Audio tagging | while recording | Scream, glass, raised voices | ~4 MB |
| 2c · Diarization | while recording | How many voices, whose | ~8 MB |
| 3 · Reasoning | on suspicion only | Is this actually escalating? | heuristic always; LLM rarely |

**Do we need an LLM?** For detection, no — and using one would make the app worse.
For *judgement* on the ambiguous middle, yes, sparingly. Section 4 has the numbers.

---

## 2. Why a cascade and not one big model

The tempting design is to point one multimodal model at the microphone and ask "is she
in danger". The measurements say don't:

- An MFCC + SVM distress classifier reaches **~95% detection at ~1% false alarm**, and
  costs **3–5% battery over ten hours** on a phone.¹
- An on-device LLM costs **2–5 s to first token** and **~1.2 GB RAM at int4**.²

Running the expensive thing continuously would flatten the battery during exactly the
walk home it exists to protect, and would answer *slower* than the cheap thing. The cheap
tiers are not a compromise — for detection they are simply better.

---

## 3. Tier 2a — real-time transcription

Measured on Android:³

| Model | Inference | RTF | Notes |
|---|---|---|---|
| **Moonshine Tiny** | 1,363 ms | **0.05** | Purpose-built for edge; lowest latency |
| Whisper Tiny | 2,068 ms | 0.07 | 30 s windows; awkward for streaming |
| Zipformer streaming | 3,568 ms | 0.12 | True streaming transducer |

Moonshine v2 (Feb 2026) reports **33.6 M params / 12.0% WER** at tiny, **123 M / 7.8%**
at small, with a position-free sliding-window encoder giving ~80 ms lookahead, and
**50 ms** response latency at tiny on an M3. Medium matches Whisper Large v3 accuracy at
6× smaller. Permissively licensed.⁴

**Runtime matters as much as model.** sherpa-onnx is reported **51× faster than
whisper.cpp running the same Whisper Tiny on Android**.³ Picking the model without
picking the runtime is most of the way to a wrong answer.

### Decision

**sherpa-onnx running Moonshine Tiny**, falling back to streaming Zipformer where a true
transducer is wanted.

sherpa-onnx (Apache-2.0) covers ASR, **speaker diarization, speaker identification and
verification, VAD (Silero), keyword spotting and audio tagging in one framework**, with
Kotlin/Java bindings and Android support.⁵ That collapses tiers 0, 1, 2a, 2b and 2c onto
a single runtime instead of four, which matters more for maintenance than any single
model's WER.

---

## 4. Tier 3 — do we need an LLM?

### The honest answer: not for detection, yes for judgement

**Against an LLM on the hot path:**

- Latency: on-device LLM is **2–5 s to first token**; a fine-tuned classifier answers in
  microseconds to milliseconds.² In an escalating situation seconds are the whole game.
- Memory: ~1.2 GB at int4; Gemma 3n wants **3–4 GB**.⁶
- The literature is blunt that for "a stable, high-QPS, latency-critical path the
  fine-tuned model is the obvious choice", with the LLM justified "only where its
  adaptability or out-of-scope handling is required".²
- A classifier's decision is auditable. "The model thought so" is not an acceptable
  explanation for having called the police.

**For an LLM on the cold path:**

Deciding whether *"leave me alone"* + a stranger's voice + rising volume + 11 pm + an
unlit street constitutes escalation **is** out-of-scope handling. It is contextual
reasoning over a short text window, which is what language models are good at and what
keyword rules are bad at. The cost is acceptable precisely because it runs rarely.

### Decision

`HeuristicThreatAssessor` runs on **every** chunk — transparent, instant, auditable, and
the only thing that can afford to look at all the audio. It escalates on its own when the
signals are unambiguous.

An LLM-backed `ThreatAssessor` handles only the **ambiguous band (severity 0.30–0.60)**.
Below it nothing is happening; above it the cheap signals already agree and waiting
seconds for a model would only delay help.

If an LLM is added, **Gemma 3n** is the candidate: its USM audio encoder emits ~6 tokens
per second of audio and it does ASR *and* reasoning in one model.⁶ That is attractive but
it is a 3–4 GB resident model — viable on a flagship, not on the mid-range phone this app
should still protect. Hence it stays on the cold path, over text, not audio.

---

## 5. Tier 2b — acoustic danger signals

Words are not the only evidence, and often not the first. **YAMNet** (MobileNet-v1,
AudioSet, 521 classes) covers screams, glass breaking, gunshots and sirens, is TFLite-
ready and tiny.⁷ It runs through the **LiteRT** path already in the app.

---

## 6. Cloud vs on-device — stated fairly

Cloud wins on raw quality and is cheap: AssemblyAI Universal-3 Pro Streaming at **307 ms
P50 / 8.14% WER**, Deepgram Nova-3 at **516 ms / 9.87%**, roughly **$0.21–0.46/hour**.⁸
That is better than anything that fits on a phone.

**We are still not using it for the live path**, for reasons that are about this product
rather than the technology:

1. The app's entire promise is that audio never leaves the device. Streaming a user's
   surroundings to a third party during an assault is a different threat model, whatever
   the privacy policy says.
2. It needs connectivity exactly when she may have none — a basement, a car park, a dead
   battery on a tethered hotspot.
3. Per-hour cost scales with always-on listening in a way a consumer safety app cannot
   absorb.

**Where cloud is legitimate:** opt-in, after the fact, for re-transcribing a *saved*
session at higher accuracy, with explicit consent per session. Worth building later;
never on the live path, never by default.

---

## 7. What is built vs what is pending

**Built:** the tiered architecture and all its seams — `SpeechTranscriber`,
`AudioTagger`, `ThreatAssessor`, `SituationSnapshot`, `ThreatAssessment`; the mel/FFT
front end; `KeywordSpotter` with a LiteRT implementation; the wake-word engine; the
microphone foreground service; `HeuristicThreatAssessor` with tests pinning both failure
modes (staying quiet when something is happening, and crying wolf when nothing is).

**Pending:** the model files themselves, and the sherpa-onnx-backed implementations of
`SpeechTranscriber`, `AudioTagger` and the diarizer.

No model is committed. Beyond size, it is a licensing decision: openWakeWord's
*pre-trained* weights are CC BY-NC-SA 4.0 — fine for a prototype, not for shipping.

---

## 8. What you need to do

Everything below needs a human: downloads, licence choices, and a device with a real
microphone.

### 8.1 Add the sherpa-onnx Android library

1. Download `sherpa-onnx-<version>-android.aar` from
   <https://github.com/k2-fsa/sherpa-onnx/releases>.
2. Drop it into `app/libs/` — the build already picks up any `.aar` there.
3. Expect roughly 15–25 MB per ABI. Consider an ABI split or Play Feature Delivery before
   shipping.

### 8.2 Download the models

Put these in `app/src/main/assets/` (or, better, download on first run so the APK stays
small):

| Purpose | Model | Where |
|---|---|---|
| Streaming ASR | `sherpa-onnx-moonshine-tiny-en-int8` | [sherpa-onnx models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) |
| VAD | `silero_vad.onnx` | [sherpa-onnx VAD models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) |
| Speaker ID / diarization | 3D-Speaker or WeSpeaker embedding | [speaker models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models) |
| Audio tagging | YAMNet TFLite | [TF Hub / Kaggle Models](https://www.kaggle.com/models/google/yamnet) |
| Wake word embedding | `speech_embedding` → `assets/speech_embedding.tflite` | see §8.3 |

### 8.3 Decide the wake-word model licence

The wake-word path expects a frozen speech-embedding model at
`assets/speech_embedding.tflite`. Three routes:

- **openWakeWord's pre-trained embedding** — easiest, but CC BY-NC-SA 4.0. Fine for the
  hackathon, blocks commercial release.
- **Train your own** on openWakeWord's Apache-2.0 *code* with synthetic data (their Colab
  is about an hour). Clean licence, needs a GPU session.
- **sherpa-onnx keyword spotting** instead of the embedding approach — Apache-2.0
  throughout, at the cost of the few-shot "any phrase the user invents" behaviour.

Tell me which and I will wire it.

### 8.4 Things only you can test

- **Say the wake word on a real device.** Emulators have no usable microphone; false
  accept and false reject rates are meaningless without real speech in real rooms.
- **Walk around for an hour with it armed** and report the battery delta. The 3–5%/10h
  figure is from the literature, not from this app.
- **Try to make it cry wolf** — a loud bar, an argument on TV, a film with screaming.
  False positives are what gets a safety app uninstalled before the night it is needed.

### 8.5 Optional

- A **Play Store declaration** for `RECORD_AUDIO` and foreground-service microphone use;
  Google requires a prominent-disclosure screen for background mic access.
- If you want cloud re-transcription later (§6), an API key — but not for the live path.

---

## Sources

1. [Smartphone Audio Based Distress Detection](https://arxiv.org/html/2608.04176v1) — MFCC + SVM, 95%/1% FA, 3–5% battery per 10 h.
2. [When Do LLMs Replace Fine-Tuned NLU?](https://arxiv.org/pdf/2608.20371) and [MediaPipe on-device LLM guidance](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android) — latency and memory.
3. [Offline Speech Transcription Benchmark: 16 Models Across Android, iOS, macOS, Windows](https://voiceping.net/en/blog/research-offline-speech-transcription-benchmark/) — Android RTF and the sherpa-onnx vs whisper.cpp runtime gap.
4. [Moonshine v2: Ergodic Streaming Encoder ASR](https://arxiv.org/html/2602.12241v1) — architecture, params, WER, latency.
5. [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Apache-2.0; ASR, diarization, speaker ID, VAD, KWS, audio tagging.
6. [Introducing Gemma 3n](https://developers.googleblog.com/en/introducing-gemma-3n-developer-guide/) — USM audio encoder, PLE, 3–4 GB.
7. [YAMNet / AudioSet](https://www.kaggle.com/models/google/yamnet) and [TFLite audio classification codelab](https://developers.google.com/codelabs/tflite-audio-classification-basic-android).
8. [Speech-to-Text APIs in 2026: Benchmarks and Pricing](https://futureagi.com/blog/speech-to-text-apis-in-2026-benchmarks-pricing-developer-s-decision-guide/) — cloud latency, WER, per-hour cost.
