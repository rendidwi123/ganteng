# BANGUN WOI! — Architecture

**Status:** draft. The platform-independent core (section A) is implemented and unit-tested.
Everything Android-specific (section B) is *design only*; the Android behaviours it relies on (section C) are
**assumptions, not facts**, until checked against the real SDK/docs. See
[ANDROID_VERIFICATION_CHECKLIST.md](ANDROID_VERIFICATION_CHECKLIST.md).

Legend used throughout: **[IMPLEMENTED+TESTED]**, **[DESIGN ONLY]**, **[UNVERIFIED]**.

---

## A. Platform-independent logic — `:core` module  [IMPLEMENTED+TESTED]

Pure Kotlin/JVM (`core/`), no Android/AndroidX/Compose/Room dependency; only `java.time` (available on Android API 26+,
the planned minSdk — whether desugaring is needed is **unverified**). Builds and tests with plain Gradle + JUnit 4:
`./gradlew clean :core:test` (150 tests). When the Android SDK is available, `:app` depends on `:core`.
How `:app` must use it: [ANDROID_INTEGRATION_CONTRACT.md](ANDROID_INTEGRATION_CONTRACT.md).

```
core/src/main/kotlin/com/bangunwoi/core/
  domain/     Alarm, AlarmSchedule, RepeatDays, WakePhraseSettings, ChallengeSettings, AlarmSoundSettings, Difficulty, BuiltInSound
  matching/   PhraseNormalizer, WakePhraseMatcher, MatchResult
  challenge/  Challenge, ChallengeInput/Result/Progress, WakePhraseChallenge, AngryModePolicy
  session/    AlarmState/AlarmEvent, AlarmStateMachine, AlarmSession, SessionSnapshot, SessionAction
```
Separation of concerns (each layer only knows the one below):
`MatchResult` (does this text match the phrase?) → `ChallengeResult`/`ChallengeProgress` (does it count, how far are we?)
→ `AlarmState` + `AlarmSession` (what is the alarm doing, what should the platform do next?) → completion (`SessionAction.STOP_ALARM`).

### A1. Domain model
| Type | Fields / rules |
|---|---|
| `Alarm` | `id` (≥ 0; 0 = unsaved), `schedule`, `enabled`, `label` (≤ 60), `wakePhrase`, `challenge`, `sound`, `createdAtMillis`, `updatedAtMillis` (epoch ms supplied by caller; the domain never reads a clock). `nextTriggerAfter(zdt)` returns null when disabled |
| `AlarmSchedule` | `time: LocalTime` (minute precision enforced), `repeatDays: RepeatDays`; `AlarmSchedule.of(hour, minute, days)` validates 0..23 / 0..59. `nextTriggerAfter(ZonedDateTime)` is pure |
| `RepeatDays` | value class over a 7-bit mask, Mon = bit 0 … Sun = bit 6. `NONE`(0) `WEEKDAYS`(31) `WEEKENDS`(96) `EVERY_DAY`(127); `of(...)`, `fromMask` (throws on <0 or >127), `fromMaskOrNull`, `contains/plus/minus`, `toList()` in Mon..Sun order. Empty = one-shot |
| `WakePhraseSettings` | `phrase` (default "GW UDAH BANGUN"; ≥ 4 letters/digits, ≤ 100 chars), `threshold` (default 0.8, in (0,1]), `allowedErrors` (null = automatic, else 0..3) |
| `ChallengeSettings` | `difficulty`, optional `requiredSuccesses` (1..10), `angryMode`, `minConfidence`, `minRelativeLevel` (both 0..1) |
| `AlarmSoundSettings` | `BuiltInSound` {DEFAULT, AGGRESSIVE, FUNNY}, `baseVolume` (0..1), `vibrate` |
| `Difficulty` | EASY (1 success), NORMAL (1 success + optional level/confidence gate), HARD (3 successes); `fromNameOrNull` for stored data |

Validation is domain-level: every constructor `require`s its invariants, so an invalid alarm cannot exist in memory.
Decision: **no snooze configuration** — the product has no snooze bypass by design (emergency stop is the only escape).

### A2. Wake phrase matching
`WakePhraseMatcher(phrase, threshold, allowedErrors?)` → `match(text): MatchResult(score, errors, allowedErrors, contradicted, …)`;
`accepted = score >= threshold && errors <= allowedErrors && !contradicted`. `matchBest(list)` ranks recognizer alternatives
(accepted first, then score).

1. **Normalize** (`PhraseNormalizer`): lowercase → strip diacritics → non-letters/digits to spaces → collapse elongation
   (3+ repeated letters) → canonical words (`gw/gue/gua/aku/saya/…`→`gue`, `udah/sudah/dah/udh/sdh/…`→`sudah`, `bangun/bgn/…`→`bangun`).
2. **Align** target words with transcript words (word-level edit distance; words before/after the phrase are free):
   missing word costs its weight (optional pronoun `gue` = 0.5, others 1); a different word costs 1; a typo of the word
   (similarity ≥ 0.7, words ≤ 3 letters must be exact) costs `weight × (1 − similarity)`; an interleaved word costs 1.
   `score = 1 − cost / totalWeight`.
3. **Hard errors**: a missing content word, a different word, or an interleaved word. Allowed per phrase: `words / 6`
   (0 for 1–5 words, 1 for 6–11, …), overridable per alarm.
4. **Blocked words**: if the transcript contains a negation ("belum", "gak", "tidak", …) or "masih/tidur/ngantuk" that is not
   part of the target, the result is `contradicted` and rejected even when the phrase itself is present.

**The 5-word / 1-wrong-word issue (fixed).** The old model scored `1 − cost/words`, so one wrong word in a 5-word phrase
scored exactly 0.8 and passed the default threshold — one unrelated word could trigger the alarm. The error budget closes
this: phrases up to 5 words tolerate no hard error; a 6-word phrase tolerates one.

**False positives vs false negatives.** For a wake-up challenge a false positive (alarm stops although the user is not
awake) defeats the product, while a false negative costs one more attempt. The matcher is therefore biased toward false
negatives. Known costs of that bias: a user who says the phrase plus an unrelated "tidur"/"masih" is rejected; recognizer
mistakes in one word of a short custom phrase force a retry; interleaved filler ("gw udah, eh, bangun") fails.
Known deliberate acceptances: because the pronoun is optional and leading words are free, "lu/dia/kamu udah bangun" passes
(listed in `WakePhraseTestData.ACCEPTED_BY_DESIGN`). Per-alarm `threshold` and `allowedErrors` are the tuning knobs.

### A3. Challenge architecture
```
Challenge { progress, isComplete, failedAttempts, start(), processInput(ChallengeInput): ChallengeResult, reset() }
ChallengeInput  (open interface)  ← SpeechInput(candidates, isFinal, relativeLevel?), NoSpeechInput
ChallengeResult = Ignored | Preview(match) | Rejected(reason, match?) | Progressed(progress, match) | Completed(match)
RejectReason    = EMPTY_SPEECH | NO_MATCH | TOO_QUIET | LOW_CONFIDENCE
```
- `ChallengeInput` is an *open* interface so a future math/QR/shake input can be added without touching existing code; a challenge returns `Ignored` for inputs it does not understand.
- `WakePhraseChallenge` is the only implementation. Only **final** speech counts; partial results give `Preview` feedback, so one utterance is never counted twice.
- Among recognizer alternatives an accepted one always wins over a rejected one with a higher score.
- NORMAL's loudness/confidence gates apply **only when the platform supplies a value**; unknown never blocks the user. Loudness is a *relative heuristic* supplied by the platform layer (not dB); no audio code exists.
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
"Ringing" = TRIGGERED, LISTENING, RECOGNIZING, CHALLENGE_FAILED, CHALLENGE_PROGRESS. A ringing alarm leaves that group
**only** via COMPLETED or EMERGENCY_STOP (no cancel/reschedule). A final result may arrive without a preceding partial, so
LISTENING also accepts PhraseAccepted/Rejected. Invalid transitions are *returned* (`TransitionResult.Invalid`), never thrown.
Tests enumerate every state × event pair against an explicit table.

### A5. AlarmSession — the object the Android layer drives
`AlarmSession.forAlarm(alarm)` wires challenge + Angry Mode. Calls: `beginRinging()`, `listenStarted()`, `onSpeech(SpeechInput)`,
`listenTimeout()`, `recognizerRestart()`, `emergencyStop()`, `snapshot()`; each returns `SessionUpdate(snapshot, applied)`.
`SessionSnapshot` carries `state`, `progress`, `failedAttempts`, `angry`, `lastMatch`, `lastResult`, and the derived
`action` (`NONE | START_LISTENING | KEEP_LISTENING | STOP_ALARM`) and `isCompleted` — the platform obeys `action` and never
decides anything about the phrase or the attempts. Single-threaded, no clock, no Android types.
`beginRinging()` starts from IDLE/SCHEDULED/finished states so a fresh process after reboot/process death needs no replay.

### A6. Angry mode
`AngryModePolicy(enabled, messages, failuresPerLevel = 1).stateFor(failedAttempts)` → `AngryState(level, maxLevel, intensity 0..1, message, isMax)`.
Defaults: 0 "Waktunya bangun.", 1 "Bangun.", 2 "Serius masih tidur?", 3 "GW BILANG BANGUN.", 4+ "TERIAK YANG JELAS." (max).
Rules: a failed attempt is a wrong phrase, a too-quiet utterance, or a listening window that ended in silence (challenge timeout);
recognizer errors are *not* failures; successes never lower the level while the alarm rings; the session reports level 0 once
the alarm is completed or emergency-stopped; disabled ⇒ always 0. No randomness, no clock. `intensity` is abstract: how it maps to
volume is an audio-layer decision (not designed yet). There is no time-based escalation: repeated silent listening windows already escalate.

### A7. Testing strategy
- JUnit 4 + kotlin-test; `./gradlew clean :core:test` → **150 tests, 0 failures**.
- `WakePhraseTestData`: 44 valid and 62 invalid phrases in named categories (case/punctuation/whitespace, optional pronoun, typos, surrounding
  speech, negation, sleepy/contradiction, unrelated, partial phrases, wrong/interleaved words), plus by-design acceptances; sweep tests for substitutions and long/garbage input.
- Time logic: fixed `ZonedDateTime` inputs only (reference week 2026-03-02 Mon…), plus an exhaustive sweep over day-sets × hourly instants.
- State machine: exhaustive state × event table plus invariants. Sessions: realistic EASY/NORMAL/HARD mornings, silence escalation, emergency stop from every ringing state, late callbacks.
- Mutation spot-check performed once (error budget, blocked words, trigger boundary, cancel-while-ringing, alternative ranking): each mutation was caught by at least 2 tests.
- Not covered (needs Android): everything in section B.

### A8. Known limitations of the core
- Text-based matching only; quality is bounded by the recognizer output; no phonetic matching; the synonym table is hand-written Jakarta-style Indonesian.
- The matcher ranks alignments by cost then errors; it does not search for a slightly costlier alignment with fewer errors.
- Process death while ringing loses session progress (the platform restarts with `beginRinging()`).
- Wall-clock/DST semantics are defined by `java.time`; agreement with the OS alarm is unverified.
- Angry intensity-to-volume mapping and the loudness heuristic's calibration are not defined in the core.

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
| 1.5 | Platform-independent core, tests, verification checklist | done |
| Core hardening | RepeatDays, schedule tests, matcher error budget, session API, integration contract | done (this change) |
| 2 | Android project + UI shell | blocked on SDK/Maven access |
| 3–12 | Persistence/scheduling, alarm firing + audio, speech integration, reboot, polish, release | blocked |
