# Guardian Angel

**A personal safety agent for Android that runs entirely on your phone.**

Say a wake word and Angel starts recording and transcribing — silently, with no sound and
nothing on screen. She works out from what she hears whether the situation is actually
escalating, and only then texts the people you chose, with your location. Codewords, said
while she is already listening, tell her what to do without anyone noticing you said
anything at all.

She also tries to keep you out of trouble in the first place: a live safety score, a map
of where incidents have actually been reported, and a route home chosen for being safe
rather than short.

No audio ever leaves the device.

Demo video: 

---

## Who it is for

Women who are alone and uneasy — walking home at night, meeting someone from an app for
the first time, stuck somewhere that has started to feel wrong.

The design follows from that one audience, and from one observation: by the time you are
*certain* something is wrong, getting your phone out is the thing you can least afford to
do. So nothing about Angel requires looking at a screen, and nothing she does is visible
to whoever you are with.

Built for women, by women.

---

## How it works

### Staying out of danger

- **Safety score** — a live estimate from reported crime data, time of day, street
  lighting and how far you are from home. 100 at your own front door; it starts falling
  the moment you leave. Presented as a band and a reason, never as a guarantee.
- **The map** — incident clusters drawn from Berkeley PD and UCPD open data in 150 m
  privacy cells, 24/7 safe havens, and your own saved safe places.
- **Safest route** — pick a destination and compare corridors by safety, lighting and how
  many safe havens are on the way, not just by how long they take.

### Walking with you

Choose a route and tap **Walk with Angel**. Only that route stays drawn, and the search
bar is replaced by one instruction at a time. The walk is a stored journey followed by a
foreground service, so it keeps going when you lock the phone, pocket it or switch apps.
When you arrive, Angel can text whoever you picked — with the time you got there — so you
don't have to remember to.

### When something happens

| You do this                                | Angel does this                                 |
|--------------------------------------------|-------------------------------------------------|
| Say your **wake word**                     | Starts recording and transcribing, silently     |
| Say your **Caution** codeword              | Keeps a record, tells nobody                    |
| Say your **Danger** codeword, or hold SOS  | Texts your whole circle with a live map link    |
| Say your **Emergency** codeword            | That, and rings your first guardian             |
| Say your **Safe** codeword                 | Cancels anything pending, sends the all-clear   |

3
### What you get afterwards

A session timeline with the transcript, labelled by speaker — including voices that are
not yours. Sounds that speech can't capture: a scream, an impact, breaking glass. The path
you walked, with the moments worth looking at pinned to where they happened. And a title
and summary written once the recording closes. Yours to export, or to delete.

### The stack

Everything that listens runs locally.

|               |                                                                       |
|---------------|-----------------------------------------------------------------------|
| Wake word     | sherpa-onnx keyword spotting (zipformer) — any phrase, no retraining  |
| Your voice    | CAM++ 512-d voiceprint, AES-GCM encrypted in the Android Keystore     |
| Transcription | Moonshine Tiny + Silero VAD, streaming, on device                     |
| Sound events  | YAMNet via LiteRT — 521 AudioSet classes                              |
| App           | Kotlin · Jetpack Compose · Material 3 · Room (v5, real migrations)    |
| Map           | Google Maps Compose · fused location · BPD/UCPD open data · NWS       |

---

## Running it

```bash
./gradlew :app:assembleDebug              # build
./gradlew :app:testDebugUnitTest          # 206 unit tests
./gradlew :app:connectedDebugAndroidTest  # 58 instrumented tests, needs a device
./gradlew :app:installDebug
```

You need to supply a Google Maps API key — see
[docs/BERKELEY_MAPS_SETUP.md](docs/BERKELEY_MAPS_SETUP.md). With the placeholder key the
map renders nothing at all rather than failing, which looks like a bug and isn't.

Silent SMS needs `SEND_SMS`, which the app asks for when you add a guardian. Without it an
alert opens a pre-filled composer and waits for one tap, and the app tells you which of
the two you have.

**This is a hackathon build.** Transcripts are stored unencrypted, the wake-word
thresholds have not been tuned against real speech in real rooms, and the APK is 269 MB
with the models bundled. Don't put anything you'd mind losing into it yet.

---
