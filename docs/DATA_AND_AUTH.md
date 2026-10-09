# Data & accounts

How Guardian Angel stores things, who the user is, and what reaches a server.

---

## 1. Storage at a glance

Room, twelve tables, all hanging off one `users` row.

| Table | Holds |
|---|---|
| `users` | profile, auth provider, onboarding progress |
| `voice_profiles` | **encrypted** voiceprint embedding, clarity, listening prefs |
| `wake_words` | the phrase **and its pre-computed BPE tokens** |
| `codewords` + `codeword_contacts` | the four tiers, and who each alerts |
| `guardians` | the trusted circle, with contacts lookup keys |
| `sessions` | one monitored session: summary, peak dB, lowest score |
| `transcript_entries` | diarized lines with timestamps and speaker tags |
| `audio_events` | screams, glass, shouting, with confidence |
| `location_points` | breadcrumbs tied to a session, with per-point safety score |
| `safe_places` | home and other havens, with geofence radii |
| `disarm_pins` | **encrypted** salted hashes of the PIN and its decoy |

Schemas export to `app/schemas/` and are checked in, so a migration is a reviewable diff
and `MigrationTestHelper` can replay old databases. `fallbackToDestructiveMigration` is
deliberately **not** set: wiping on a bad migration would silently destroy the guardians
someone is relying on, and they would find out the night they needed them.

### Decisions worth knowing

**No raw audio, ever.** A voiceprint is a 512-float embedding, a transcript is text.
The recordings a user would most want destroyed are the ones never written down.

**Wake-word tokens are stored, not recomputed.** If they were derived on load, a change
to the tokeniser would silently alter what an already-set wake word responds to, and the
user would have no way of finding out until it failed to wake her.

**Location is session-scoped.** There is no continuous location history. The app has no
reason to keep a permanent record of everywhere its user has been.

**`allowBackup` is off.** It was on, which would have put transcripts and guardian
details into Google cloud backup — outside anything this app controls.

### Encryption

The voiceprint and the disarm PIN are AES-GCM encrypted with a hardware-backed Android
Keystore key (`KeystoreCrypto`). They get that treatment because they are *credentials*;
everything else is a record of something that happened.

Not Jetpack Security — `androidx.security:security-crypto` was deprecated in 2025 with
no replacement releases, after main-thread strict-mode violations and keyset corruption
on some OEM devices. Google now points at the Keystore directly, which is what this does.

The database itself is **not** encrypted. Android's file-based encryption covers a lost
or stolen phone; it does not cover a rooted one. Full-database encryption means SQLCipher
and another native dependency — a deliberate follow-up, in the roadmap rather than
quietly skipped.

---

## 2. Accounts

Every flow starts **anonymous**. A woman downloading a safety app at 11pm should be
protected before she is asked for an email address, so sign-in happens silently on first
launch with no screen at all.

Real credentials are *linked onto that same account* later, which is why
`linkEmailPassword` and `linkPhone` exist separately from the sign-in calls: upgrading
has to preserve the guardians and codewords already set up, not start a fresh account
beside them.

| Provider | Status |
|---|---|
| Anonymous | Works now, local or Firebase |
| Email + password | Implemented; needs a Firebase project |
| Phone (SMS) | Implemented; needs a Firebase project **and** SHA fingerprints |

**Without `google-services.json` the app still works.** `LocalAuthRepository` keeps a
real account on-device; what it cannot do is restore onto a second phone, and it says so
rather than pretending. The google-services Gradle plugin is applied only when the file
exists, so the project builds for anyone who has not set Firebase up.

Auth error messages are deliberately vague about whether an account exists — Firebase's
own messages distinguish "no such user" from "wrong password", which hands out an
account-enumeration hint.

---

## 3. Cloud sync

Opt-in, from Settings → *Back up to the cloud*.

**Uploaded:** guardians, codewords, safe places, session *metadata*.

**Never uploaded:** the voiceprint, the disarm PIN, and transcript text. The first two
are credentials. Transcripts are the most sensitive thing here and the product promise is
that they stay on the phone — so `CloudSync` is written so it *cannot* break that: no
code path reads the transcript tables, and uploaded shapes are hand-written maps rather
than reflection, so adding a column to an entity can never silently start syncing it.

Incremental by construction: rows carry `syncedAt`, so a push is `WHERE syncedAt IS NULL
OR syncedAt < updatedAt`. No separate queue to fall out of step with the data, and a sync
killed halfway just leaves the unsent rows pending.

---

## 4. What you need to do for Firebase

The app works without any of this. Do it when you want multi-device restore.

### 4.1 Create the project

1. <https://console.firebase.google.com> → **Add project**.
2. **Add app** → Android → package name **`com.example.guardianangel`**.
3. Download **`google-services.json`** into **`app/`**. It is already gitignored.
4. Rebuild. You should see `FirebaseAvailability: Firebase configured` instead of
   `No Firebase config`.

### 4.2 Turn on the sign-in methods

Console → **Authentication → Sign-in method**, enable:

- **Anonymous** — required; it is the first thing the app does.
- **Email/Password**
- **Phone** — see below.

### 4.3 Phone auth needs more

- Add your **SHA-1 and SHA-256** fingerprints (Project settings → Your apps):
  ```
  ./gradlew signingReport
  ```
  Debug and release are different keys — add both, and the Play App Signing one too if
  you ever publish.
- Phone sign-in verifies the device with **Play Integrity**, falling back to reCAPTCHA.
- Set an **SMS region policy** — the default allows *no* regions, so verification fails
  silently until you allow yours.
- Add **test phone numbers** (Authentication → Sign-in method → Phone → Advanced) so you
  can develop without burning real SMS.
- Check the current **free SMS tier** against your expected volume; phone auth is the one
  provider with a per-message cost.

### 4.4 Firestore

1. Console → **Firestore Database** → Create, production mode.
2. Rules — the app writes only under its own user document:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```

   Worth being strict here: a wildcard rule on a safety app would expose one user's
   guardians to another.

### 4.5 Verify it worked

```
adb logcat -s FirebaseAvailability:I DatabaseContainer:I CloudSync:I
```

Expect `Firebase configured`, then `Signed in as <uid> (anonymous)` with a Firebase uid
rather than a `local-…` one. Settings → *Back up to the cloud* should then report how
many items it pushed.

---

## 5. Verified working

On a Pixel 7a and an emulator, with no Firebase configured:

```
FirebaseAvailability: No Firebase config — local accounts only
DatabaseContainer:    Signed in as local-fc1f9c69-… (anonymous)
SherpaWakeWord:       Listening for "watch over me" as [▁WA T CH ▁OVER ▁ME]
```

Round-trip tested: typed a wake word → stored with tokens → force-stopped the app →
relaunched → **same** user id restored, phrase read back, listening service picked it up.
Codewords seed on account creation and survive restarts.
