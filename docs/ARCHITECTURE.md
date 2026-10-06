# BANGUN WOI! — Architecture

**Status:** draft. The platform-independent core (section A) is implemented and unit-tested.
Everything Android-specific (section B) is *design only*; the Android behaviours it relies on (section C) are
**assumptions, not facts**, until checked against the real SDK/docs. See
[ANDROID_VERIFICATION_CHECKLIST.md](ANDROID_VERIFICATION_CHECKLIST.md).

Legend used throughout: **[IMPLEMENTED+TESTED]**, **[DESIGN ONLY]**, **[UNVERIFIED]**.

---

## A. Platform-independent logic — `:core` module  [IMPLEMENTED+TESTED]

Pure Kotlin/JVM (`core/`), no Android/AndroidX/Compose dependency; only `java.time` (available on Android API 26+,
which is the planned minSdk — itself an unverified assumption about the support matrix). Builds and tests with plain
Gradle + JUnit 4. When the Android SDK is available, `:app` simply depends on `:core`.

```
core/src/main/kotlin/com/bangunwoi/core/
  domain/     Alarm, AlarmSchedule, WakePhraseSettings, ChallengeSettings, AlarmSoundSettings, Difficulty
  matching/   PhraseNormalizer, WakePhraseMatcher, MatchResult
  challenge/  Challenge, ChallengeInput/Result/Progress, WakePhraseChallenge, AngryModePolicy
  session/    AlarmState/AlarmEvent, AlarmStateMachine, AlarmSession
```

### A1. Domain model
| Type | Fields / role |
|---|---|
| `Alarm` | `id` (0 = unsaved), `schedule`, `enabled`, `label`, `wakePhrase`, `challenge`, `sound`, `createdAtMillis`, `updatedAtMillis` (epoch ms supplied by caller; domain never reads a clock) |
| `AlarmSchedule` | `time: LocalTime`, `repeatDays: Set<DayOfWeek>` (empty = one-shot). `nextTriggerAfter(ZonedDateTime)` is pure and tested (same-day, rollover, weekday skip, exact-time, seconds, DST gap) |
| `WakePhraseSettings` | `phrase` (default "GW UDAH BANGUN"), `threshold` (default 0.8, range (0,1]) |
| `ChallengeSettings` | `difficulty`, optional `requiredSuccesses` override (1..10), `angryMode`, `minConfidence`, `minRelativeLevel` |
| `AlarmSoundSettings` | `BuiltInSound` {DEFAULT, AGGRESSIVE, FUNNY}, `baseVolume`, `vibrate`. The Android layer maps ids to raw resources; custom sounds are a future feature |
| `Difficulty` | EASY (1 success), NORMAL (1 success + optional level/confidence gate), HARD (3 successes) |

All constructors validate their inputs (`require`), so invalid settings cannot be persisted by accident.

### A2. Wake phrase matching
`WakePhraseMatcher(phrase, threshold)` → `match(text): MatchResult(score, threshold, normalizedTranscript, normalizedTarget)`;
`accepted = score >= threshold`; `matchBest(list)` for the recognizer's alternatives.

1. **Normalize** (`PhraseNormalizer`): lowercase → strip diacritics → non-letters/digits to spaces → collapse
   elongation (3+ repeated letters → 1) → map spellings to one canonical word via a small editable table
   (`gw/gue/gua/aku/saya/…` → `gue`; `udah/sudah/dah/udh/sdh/…` → `sudah`; `bangun/bgn/…` → `bangun`).
2. **Align** target words against transcript words (word-level edit distance, semi-global):
   words before/after the phrase are free; a missing target word or an extra word *between* matched words costs 1;
   a substituted word costs `1 - similarity` if similar enough (typos), else 1. Words ≤ 3 letters must match exactly.
3. `score = 1 - cost / targetWordCount`.

Design intent: "gw belum bangun" (negation, 2/3 words) and "gw udah" (missing word) must fail at the default threshold
while "ya gue udah bangun nih" passes.

### A3. Challenge architecture
```
Challenge { progress, isComplete, failedAttempts, start(), processInput(ChallengeInput): ChallengeResult, reset() }
ChallengeInput  (open interface)  ← SpeechInput(candidates, isFinal, relativeLevel?), NoSpeechInput
ChallengeResult = Ignored | Preview(match) | Rejected(reason, match?) | Progressed(progress, match) | Completed(match)
```
- `ChallengeInput` is an *open* interface so a future `MathAnswerInput`, `QrScanInput`, … can be added with no change
  to existing code; a challenge returns `Ignored` for inputs it does not understand.
- `WakePhraseChallenge` is the only implementation. Only **final** speech results count; partial results yield
  `Preview` (UI feedback) so one utterance can never be counted twice.
- NORMAL's loudness/confidence gates apply **only when the platform supplies a value**; an unknown value never blocks
  the user. Loudness is a *relative heuristic* supplied by the platform layer, not a dB measurement; no audio code exists yet.
- Failed attempts do not erase earlier successes (HARD keeps 1/3, 2/3).

### A4. State machine
States: `IDLE, SCHEDULED, TRIGGERED, LISTENING, RECOGNIZING, CHALLENGE_FAILED, CHALLENGE_PROGRESS, COMPLETED, EMERGENCY_STOP`.

```
IDLE ──Schedule──▶ SCHEDULED ──Fire──▶ TRIGGERED ──ListenStarted──▶ LISTENING
                                                                    │  ▲
                              SpeechDetected ───────────────────────┘  │ RecognizerRestart (not a failure)
                                      ▼                                │
                                 RECOGNIZING ──────────────────────────┘
  LISTENING / RECOGNIZING ──PhraseRejected──▶ CHALLENGE_FAILED ──ListenStarted──▶ LISTENING
  LISTENING ──ListenTimeout (silence)──────▶ CHALLENGE_FAILED
  LISTENING / RECOGNIZING ──PhraseAccepted(more needed)──▶ CHALLENGE_PROGRESS ──ListenStarted──▶ LISTENING
  LISTENING / RECOGNIZING ──PhraseAccepted(done)──▶ COMPLETED
  any ringing state ──EmergencyStop──▶ EMERGENCY_STOP
  IDLE/SCHEDULED/COMPLETED/EMERGENCY_STOP ──Schedule──▶ SCHEDULED,  ──Cancel──▶ IDLE
```
Rules: "ringing" = TRIGGERED, LISTENING, RECOGNIZING, CHALLENGE_FAILED, CHALLENGE_PROGRESS. A ringing alarm can leave
that group **only** via COMPLETED or EMERGENCY_STOP (no cancel/reschedule). A final result may arrive without a
preceding partial, so LISTENING also accepts PhraseAccepted/Rejected. Invalid transitions are *returned*
(`TransitionResult.Invalid`), never thrown: a late or duplicate Android callback must not crash an alarm.
The test enumerates every state × event pair against an explicit table.

`AlarmSession` composes state machine + `Challenge` + `AngryModePolicy`; it is the one object the Android layer will drive.
It is single-threaded and has no clock.

### A5. Angry mode
`AngryModePolicy.stateFor(failedAttempts)` → `AngryState(level, maxLevel, intensity 0..1, message)`.
0 → "Waktunya bangun.", 1 → "Bangun.", 2 → "Serius masih tidur?", 3 → "GW BILANG BANGUN.", 4+ → "TERIAK YANG JELAS." (max).
Messages are a constructor parameter (Android will feed a string-array). Disabled ⇒ always level 0. `intensity` is
abstract; how it maps to volume is an audio-layer decision (not designed in detail yet).

### A6. Testing strategy
- JUnit 4 + kotlin-test, run with `./gradlew :core:test` (JDK 17+; compiled with whatever JDK is present, targets Java 17).
- `WakePhraseTestData`: 27 valid and 20 invalid phrases (case, punctuation, whitespace, elongation, informal
  spellings, diacritics, negations, partial phrases, surrounding speech) asserted in bulk, plus targeted tests for
  threshold, typos, N-best, custom phrases, validation.
- State machine: exhaustive state × event table, plus invariants (cannot cancel while ringing; emergency stop always reachable).
- Session/challenge: full flows for EASY/NORMAL/HARD, angry escalation, late callbacks after completion.
- Not covered (needs Android): everything in section B.

### A7. Known limitations of the core
- Matching is text-based, so quality is bounded by the recognizer's output; there is no phonetic matching.
- Slack for long phrases: in a 5-word phrase, one wrong word scores 0.8 and passes the default threshold. Raise
  the threshold (or restrict custom phrase length/validate in the UI) if that is undesirable.
- The synonym table is hand-written Jakarta-style Indonesian; regional spellings are not covered.
- Interior filler ("gw udah, eh, bangun") costs 1 per extra word and may fail a 3-word phrase.
- A repeated alarm that fires while the process was killed starts from `IDLE`; the Android layer must reconstruct
  the session as SCHEDULED before calling `fire()` (the machine intentionally rejects `Fire` from IDLE).
- Timestamps/zone handling use wall-clock semantics via `java.time`; DST behaviour of the actual OS alarm is not verified.

---

## B. Android-specific implementation  [DESIGN ONLY — nothing written]

Unchanged in intent from the Phase 1 draft, but **no Android API behaviour below is considered settled**.

```
app/ (future)  com.bangunwoi.app  (applicationId placeholder)
  data/          Room entities/DAO ⇄ core.domain.Alarm mappers, AlarmRepository, DataStore settings
  alarm/         AlarmScheduler (AlarmManager), AlarmReceiver, BootReceiver, AlarmService, AlarmRingingActivity
  speech/        SpeechRecognitionManager (SpeechRecognizer → core.challenge.SpeechInput)
  audio/         AlarmAudioManager (alarm audio attributes), volume mapping of AngryState.intensity
  notification/  channels + full-screen intent
  ui/            theme, list, editor, ringing, success, permissions/onboarding  (Compose + Material 3)
```
Intended shape: Kotlin, Compose, Room + DataStore, coroutines/StateFlow, manual DI, `minSdk 26`, compile/target SDK = latest
required by Play at build time. Planned scheduling: `setAlarmClock` + receiver → foreground service for audio +
ringing Activity for the challenge, recognizer owned by the visible Activity; emergency stop = hold 5 s;
reschedule on boot/time/timezone/package-replaced. Planned permissions: `RECORD_AUDIO`, `POST_NOTIFICATIONS`,
`USE_FULL_SCREEN_INTENT`, foreground-service permissions, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`; deliberately
no `INTERNET`. Mapping: Room entity ⇄ `Alarm` (repeat days as a bitmask), `AlarmSession` driven by recognizer callbacks.

## C. Assumptions that still need Android SDK/documentation verification  [UNVERIFIED]

I believe the following from prior knowledge, but have **not** verified them against current docs or devices:

1. `setAlarmClock` is treated as an exact, Doze-exempt alarm and needs no exact-alarm permission.
2. Starting a foreground service from an alarm broadcast is allowed, including with the `mediaPlayback` type, on all target levels.
3. Microphone capture needs a visible foreground UI on recent Android versions, so recognition must live in the Activity.
4. `USE_FULL_SCREEN_INTENT` may be revocable on Android 14+ and Play restricts it to certain app categories.
5. The `<queries>` entry for `RecognitionService` is needed for `SpeechRecognizer.isRecognitionAvailable()`.
6. Alarms are lost on reboot and app update, so `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` rescheduling is required.
7. The recognizer will pick up the alarm's own sound; ducking the alarm during listening windows will be needed.
8. Offline Indonesian recognition may or may not exist on a given device; the default service may use the network.
9. Play's current target-API requirement and alarm-app policy declarations.

Each is itemised, with a `[ ] NOT VERIFIED` marker, in [ANDROID_VERIFICATION_CHECKLIST.md](ANDROID_VERIFICATION_CHECKLIST.md).

## Build environment note
The cloud session's network policy blocks `dl.google.com` (Android SDK, Google Maven). The `:core` module needs only
Maven Central and the Gradle plugin portal, which are reachable. `./gradlew :core:test` is the only build verified so far.
Android modules cannot be built here.

## Phases (revised)
| Phase | Scope | Status |
|---|---|---|
| 1 | Analysis + architecture | done |
| 1.5 | Platform-independent core, tests, verification checklist | done (this change) |
| 2 | Android project + UI shell | blocked on SDK/Maven access |
| 3–12 | Persistence/scheduling, alarm firing + audio, speech integration, reboot, polish, release | blocked |
