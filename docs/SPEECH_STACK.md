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
| 1 · Wake word | while armed | Should I start recording? | 5 MB int8, few ms/window |
| 2a · Streaming ASR | while recording | What is being said | 119 MB, RTF ≈ 0.05 |
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

Also built and loading on device: `SherpaWakeWordDetector` (tier 1) and
`YamnetAudioTagger` (tier 2b), with `BpeTokenizer` for runtime wake phrases.

Tiers 0–2c are now wired end to end. `GuardianAudioSession` owns a single `AudioRecord`
— Android grants the microphone to one capture at a time, and sharing the stream is what
makes the cascade cheap — and routes each buffer by phase: the wake-word spotter while
waiting, then the transcriber, tagger and speaker identifier once a detection flips it
into recording.

**Pending:** tier 3's LLM assessor for the ambiguous band, persisting sessions, and
real-speech tuning of the detection thresholds.

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

### 8.2 The models — what goes where

All of these live in `app/src/main/assets/`.

| Purpose | File / directory | Size |
|---|---|---|
| Streaming ASR | `sherpa-onnx-moonshine-tiny-en-int8/` | 119 MB |
| Wake word | `sherpa-onnx-kws-zipformer-gigaspeech/` | 13 MB |
| VAD | `silero_vad.onnx` | 0.6 MB |
| Speaker ID | `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx` | 28 MB |
| Audio tagging | `yamnet.tflite` + `yamnet_class_map.csv` | 3.9 MB |

Three things worth knowing, learned the hard way:

- **Extract the archives.** Assets are bundled byte-for-byte, so a `.tar.bz2` left in
  place ships 100 MB of compressed data the app cannot read.
- **Delete `test_wavs/`** from each model directory, and the non-int8 duplicates. The KWS
  download ships both an 11 MB fp32 encoder and a 3.8 MB int8 one; only the int8 is used.
- **YAMNet must be the TFLite build, not the SavedModel.** Kaggle's default download is
  a TensorFlow SavedModel (`saved_model.pb` + `variables/`), which Android cannot load.
  The converted model is at
  `https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/latest/yamnet.tflite`
  — 3.9 MB, no TensorFlow install needed. Keep `yamnet_class_map.csv` from the SavedModel
  download; it maps the 521 output indices to names.
- **Use the fp32 KWS encoder, not int8.** Both int8 conversions — the standard one and
  the `-mobile` variant — abort inside onnxruntime about a second into streaming: a
  Zipformer downsample reshape receives 17 frames where it needs an even 16
  (`Input shape:{17,1,128}, requested shape:{8,2,1,128}`). It is a native `SIGABRT`, so
  no Kotlin `try`/`catch` contains it — the process simply dies. fp32 costs ~8 MB more
  and works. The decoder and joiner are fp32 for the same reason.
- **One speaker model is enough.** CAM++ (28 MB) and WeSpeaker ResNet152 (79 MB) do the
  same job; ResNet152 is far too heavy for a phone. The spare has been moved to
  `model-archive/` outside the build.

### 8.3 Wake word — resolved, nothing further to download

~~Pick between three licence routes.~~ **Settled: sherpa-onnx keyword spotting.**

The original plan was a frozen speech-embedding model with few-shot enrolment, which
would have meant openWakeWord's CC BY-NC-SA weights and no path to a commercial release.
sherpa-onnx's keyword spotter reaches the same place and is strictly better here:

- **Apache-2.0 throughout** — no licence ceiling.
- **3.3 M parameters, ~5 MB int8** — comparable to the embedding model it replaces.
- **Open vocabulary.** Any phrase registers at runtime via
  `KeywordSpotter.createStream(tokens)`. No retraining, no enrolment takes.
- It is in the AAR you already downloaded.

`BpeTokenizer` converts a typed phrase into the tokens the spotter expects
(`"hey angel"` → `"▁HE Y ▁AN GE L"`).

**One honest caveat.** That tokeniser is greedy longest-match over the model's 500-token
vocabulary, not a faithful re-implementation of SentencePiece's merge ordering. Against
the nine reference phrases sherpa ships it reproduces seven exactly; the other two give a
different but still valid segmentation. Detection still works — every token is in the
vocabulary — but the threshold for those phrases may sit slightly differently. Hence
`BpeTokenizer.CURATED_PHRASES`: five verified, acoustically distinct suggestions the UI
can lead with while still accepting anything.

#### What changed in the app

#### One embedding window, everywhere

A constraint worth stating loudly, because it is invisible and it broke the voice gate
completely. **CAM++ embeddings are only comparable between inputs of similar duration.**
Measured on a Pixel 7a, one speaker, one sentence:

| Comparison | Cosine similarity |
| --- | --- |
| Fixed 1.5 s windows, same speaker | 0.79 mean, 0.61 worst |
| Fixed 3 s windows, same speaker | 0.92 |
| Same speech at 1 s vs 2 s | **-0.03** |
| Same speech at 1.5 s vs 3 s | **0.24** |
| Silence vs speech | -0.02 |

The first implementation trimmed each take to a variable length and enrolled on 3 s
segments, while the wake-word gate verified against a 1.5 s pre-roll. Every component
passed its own tests, and the gate would have rejected the enrolled user on every wake.

So `SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES` (1.5 s) is the one window length used
by enrolment, wake-word verification and diarization, and `speechWindow()` is the only
place that picks it — sliding a fixed window to the densest speech rather than trimming
to whatever survives. 1.5 s because the gate has the tightest constraint: the pre-roll
must hold a two or three syllable phrase plus the decoder's lag, and cannot grow without
delaying the wake.

`SherpaWakeWordDetector` replaces the embedding path. Phrase detection and speaker
identity are now two independent checks rather than one model doing both: the spotter
decides *the phrase was said*, and the CAM++ voiceprint decides *she said it* when
"only wake for my voice" is on. Easier to tune, since a missed wake and a wrong-speaker
wake have very different costs.

Verified loading on a device:

```
I SherpaWakeWord: Keyword spotter loaded
I SherpaWakeWord: Listening for "hey angel" as [▁HE Y ▁AN GE L]
I SherpaTranscriber: Transcriber loaded
I SpeakerId:      Speaker extractor loaded: 512-d embeddings
I YamnetTagger:   YAMNet loaded: 15600 samples in, 521 classes out
I GuardianAudio:  Capture started
```

The foreground service reports `isForeground=true types=0x00000080` — `0x80` is
`FOREGROUND_SERVICE_TYPE_MICROPHONE` — started from `PROC_STATE_TOP`, which is the only
state Android permits.

### 8.3b APK size — a decision you will have to make

With all models bundled the debug APK is **220 MB**. That installs and runs fine for
testing, but it is over Play's 150 MB APK ceiling, and Moonshine alone is 119 MB of it.

Already done: ABI filtering to `arm64-v8a` and `x86_64`, which cut 57 MB of native
libraries that only reached 32-bit ARM and emulator-x86 targets.

Before shipping, pick one:

- **Download models on first run** into `filesDir` instead of bundling. Keeps the APK
  small, needs network during setup, and is what most on-device-AI apps do.
- **Play Asset Delivery**, which is the sanctioned route for large model files.
- **A smaller ASR.** Moonshine Tiny was chosen for latency, not size; a streaming
  Zipformer is roughly a third the size at somewhat worse RTF.

### 8.4 Things only you can test

- **Say the wake word out loud, armed, phone in pocket.** Detection itself is proven on
  device — recorded speech fed through the real capture path fires it, and unrelated
  speech does not — but no instrumented test can tell you how it behaves with *your*
  voice, at arm's length, through fabric, in a room with a television on. Try the curated
  phrases first, then your own.
- **Enrol your voice, then have someone else say your wake word.** Enrolment and the
  speaker gate are tested with one recorded speaker, which proves the gate accepts its
  owner and rejects noise and silence. It cannot prove the threshold is right for two
  real people, which is the number that matters.
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
