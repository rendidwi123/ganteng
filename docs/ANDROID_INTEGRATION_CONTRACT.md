# Android integration contract

What the future Android `:app` module must implement around `:core`, and exactly how it talks to it.

**Status legend**
- **[CORE — VERIFIED]** exists in `:core` and is covered by JVM unit tests (`./gradlew :core:test`).
- **NOT IMPLEMENTED — LOCAL ANDROID PHASE** nothing exists yet; it needs the Android SDK and real devices.
- Platform behaviour described below is the *intended design*, based on prior knowledge. **None of it has been verified**
  against current documentation or any device. Every platform assumption is tracked in
  [ANDROID_VERIFICATION_CHECKLIST.md](ANDROID_VERIFICATION_CHECKLIST.md); do not treat statements here as facts about Android.

## 0. Division of responsibility

```
 Android platform  ──callbacks──▶  :app (adapters)  ──events──▶  :core AlarmSession
 (AlarmManager, Service,                                          (state, challenge, matching,
  SpeechRecognizer, UI, audio)  ◀──SessionSnapshot.action──        angry mode)  — no Android, no clock
```
Rule: **`:app` contains no phrase matching, no attempt counting, no escalation rules, no state transitions.**
It translates platform events into `AlarmSession` calls and renders `SessionSnapshot`. If you find yourself writing an
`if` about the phrase, the number of attempts or whether the alarm may stop, it belongs in `:core`.

## 1. Alarm scheduling — NOT IMPLEMENTED — LOCAL ANDROID PHASE
- Compute the trigger with `Alarm.nextTriggerAfter(ZonedDateTime.now(zone))` ([CORE — VERIFIED]). It returns `null` for a
  disabled alarm: then cancel any scheduled OS alarm instead of scheduling. The clock is read by `:app`, never by `:core`.
- Schedule only the *next* occurrence; after it fires (or after reboot/time change) compute and schedule the following one.
- Convert the `ZonedDateTime` to epoch millis for the OS. Use one OS alarm per `Alarm.id` (stable request code).
- Planned mechanism: `AlarmManager.setAlarmClock` — **[ ] NOT VERIFIED** (see checklist A, B).
- DST semantics are defined by `AlarmSchedule.nextTriggerAfter` (gap shifts forward; repeated hour rings once).
  Whether the OS alarm agrees across DST changes is **NOT VERIFIED**.

## 2. AlarmManager — NOT IMPLEMENTED — LOCAL ANDROID PHASE
`AlarmScheduler` (app): `schedule(alarm)`, `cancel(alarmId)`, `rescheduleAll(alarms)`. Pure adapters around the call in §1.
Must survive: permission unavailable (fall back and tell the user, never crash), duplicate scheduling (idempotent per id).
Exact-alarm permission requirements: **NOT VERIFIED** (checklist B).

## 3. BroadcastReceiver — NOT IMPLEMENTED — LOCAL ANDROID PHASE
- `AlarmReceiver`: receives the alarm intent (carries `alarmId`), does almost no work, starts the ringing service/UI path (§4–5),
  then schedules the alarm's next occurrence (§1).
- `BootReceiver`: reschedules all enabled alarms on boot, time change, timezone change and app update (§11).
- Receivers must tolerate an alarm id that no longer exists (deleted or disabled in the meantime): do nothing.

## 4. Alarm ringing lifecycle — NOT IMPLEMENTED — LOCAL ANDROID PHASE
One `AlarmSession` per ringing alarm, created from the persisted alarm and kept by something that outlives the Activity
(the service or a process-level holder; the choice is an Android decision):
```kotlin
val session = AlarmSession.forAlarm(alarm)            // wires WakePhraseChallenge + Angry Mode  [CORE — VERIFIED]
session.beginRinging()                                // from IDLE/SCHEDULED/finished; no-op if already ringing
```
`beginRinging()` exists because after a reboot or process death the new process is in `IDLE` and has no history; the platform
must not replay `schedule()`/`fire()` itself. A second broadcast while ringing returns `applied = false`: ignore it.
After each call, read `session.snapshot().action` and obey it:

| `SessionAction` | Platform must |
|---|---|
| `NONE` | nothing is ringing |
| `START_LISTENING` | start the recognizer, then call `session.listenStarted()` |
| `KEEP_LISTENING` | leave the recognizer running; keep feeding results |
| `STOP_ALARM` | stop audio, recognizer, vibration, service, notification; show success screen only if `snapshot.isCompleted` |

If the Activity is recreated (rotation, process restart of the UI) it re-renders from `session.snapshot()`; it never keeps its own copy.
Process death while ringing loses the session; the replacement process calls `beginRinging()` again (progress restarts — accepted limitation).

## 5. Foreground service — NOT IMPLEMENTED — LOCAL ANDROID PHASE
Owns audio and the ongoing notification while ringing; stops itself when the snapshot says `STOP_ALARM`.
Required service type, start-from-background rules and time limits: **NOT VERIFIED** (checklist E, L–N).
Audio intensity: map `snapshot.angry.intensity` (0.0..1.0) and `snapshot.angry.level` to volume/vibration. The core defines
the *level*; the volume curve is an audio-layer decision and is **not designed yet**.

## 6. SpeechRecognizer — NOT IMPLEMENTED — LOCAL ANDROID PHASE
Adapter `SpeechRecognitionManager` converts recognizer callbacks into core calls. Intended mapping (all recognizer behaviour
**NOT VERIFIED**; error code names must be checked against the SDK):

| Platform event | Core call |
|---|---|
| recognizer ready / listening started | `session.listenStarted()` |
| first partial result with text | `session.onSpeech(SpeechInput(candidates, isFinal = false))` |
| final results (N-best list + optional confidences) | `session.onSpeech(SpeechInput(candidates, isFinal = true, relativeLevel))` |
| no speech / speech timeout (user stayed silent) | `session.listenTimeout()` — counts as a failed attempt |
| no match | `session.onSpeech(SpeechInput(emptyList(), isFinal = true))` — counts as a failed attempt |
| busy / service unavailable / client error / network / permission error | `session.recognizerRestart()` after a back-off — **not** a failed attempt |
| recognizer permanently unavailable | keep the alarm ringing, show the emergency-stop path prominently (§18) |

Always pass **all** alternatives in `candidates`; the core picks the best. Pass `confidence = null` when the platform gives none.
`relativeLevel` is optional (0..1, relative to the device/session, **not** dB); pass `null` when unknown — an unknown value never blocks the user.
Only the NORMAL difficulty uses it. Do not compute or interpret anything about the phrase in `:app`.

## 7. Microphone lifecycle — NOT IMPLEMENTED — LOCAL ANDROID PHASE
Acquire the microphone only while `action` is `START_LISTENING`/`KEEP_LISTENING`; release it on `STOP_ALARM`, when the Activity
is destroyed, and on emergency stop. Design assumption (**NOT VERIFIED**): recognition runs inside the visible alarm Activity.
Listening vs. loud alarm audio (self-hearing, ducking): **NOT VERIFIED** (checklist F, G).
Never record to disk; never send audio anywhere from our code.

## 8. Full-screen alarm UI — NOT IMPLEMENTED — LOCAL ANDROID PHASE
Render only from `SessionSnapshot`:
- required phrase: `alarm.wakePhrase.phrase`
- heard text: `snapshot.lastMatch?.normalizedTranscript` (or raw text kept by the adapter)
- attempts: `snapshot.failedAttempts`; progress: `snapshot.progress` (`completed/required`, `fraction`)
- feedback: `snapshot.lastResult` — `Rejected(reason)` with `RejectReason` {EMPTY_SPEECH, NO_MATCH, TOO_QUIET, LOW_CONFIDENCE}
- anger text/intensity: `snapshot.angry.message`, `.level`, `.intensity`, `.isMax`
- no dismiss button; a hold-to-confirm emergency control (§18).
Launch mechanism over the lock screen: **NOT VERIFIED** (checklist C, D).

## 9. Notification — NOT IMPLEMENTED — LOCAL ANDROID PHASE
High-priority alarm notification with full-screen intent to the ringing Activity and an ongoing foreground-service notification.
Channel configuration, behaviour with notification permission denied: **NOT VERIFIED** (checklist H).

## 10. Lock screen — NOT IMPLEMENTED — LOCAL ANDROID PHASE
Show over the keyguard and turn the screen on while ringing. Behaviour on secure vs. insecure lock screens: **NOT VERIFIED** (checklist D).
Do not use anything that could lock the user out of the phone (no kiosk/lock-task, no device admin).

## 11. Reboot restoration — NOT IMPLEMENTED — LOCAL ANDROID PHASE
On boot/time/timezone/package-replaced: load enabled alarms, `rescheduleAll`. Which broadcasts are delivered and when, and the
before-first-unlock case: **NOT VERIFIED** (checklist K).

## 12. Persistence — NOT IMPLEMENTED — LOCAL ANDROID PHASE
`:core` has no persistence. The app maps `Alarm` ⇄ storage (Room planned). Mapping table:

| `Alarm` field | Storage | Notes |
|---|---|---|
| `id` | primary key, auto-generated | `0` = not yet stored |
| `enabled` | boolean | |
| `schedule.hour`, `schedule.minute` | two ints | rebuild with `AlarmSchedule.of(h, m, days)` |
| `schedule.repeatDays` | int `mask` (0..127) | Mon=bit0..Sun=bit6; rebuild with `RepeatDays.fromMask` / `fromMaskOrNull` |
| `label` | text (≤ 60 chars) | |
| `wakePhrase.phrase` / `.threshold` / `.allowedErrors` | text / real / nullable int | `null` allowedErrors = automatic |
| `challenge.difficulty` | text = enum `name` | read with `Difficulty.fromNameOrNull`, fall back to a default |
| `challenge.requiredSuccesses` | nullable int | |
| `challenge.angryMode`, `minConfidence`, `minRelativeLevel` | boolean, real, real | |
| `sound.sound` | text = enum `name` | read with `BuiltInSound.fromNameOrNull` |
| `sound.baseVolume`, `sound.vibrate` | real, boolean | |
| `createdAtMillis`, `updatedAtMillis` | long | supplied by `:app`'s clock |

**Reading stored data must not crash.** All domain constructors throw `IllegalArgumentException` on invalid values (e.g. a
corrupted row). The mapper must catch that per row and skip/repair the row rather than fail the whole list or the alarm.
Use the `*OrNull` parsers to choose a documented fallback. Never store enum ordinals (order may change); store names.

## 13. Battery optimization — NOT IMPLEMENTED — LOCAL ANDROID PHASE
User-facing guidance only; no core involvement. Policy for requesting exemptions and OEM behaviour: **NOT VERIFIED** (checklist J).

## 14. Android permissions — NOT IMPLEMENTED — LOCAL ANDROID PHASE
Planned set (to be confirmed against the SDK and Play policy — checklist B, C, F, H, L–O): microphone, notifications,
full-screen intent, foreground service (+ type), boot completed, wake lock, vibrate. Not planned: internet.
Request microphone permission *before* an alarm can ring (editor/onboarding), never for the first time while ringing.
If a permission is missing at ring time the alarm still rings and the emergency path is available.

## 15. Interaction with `AlarmSession`  [CORE — VERIFIED]
Complete public surface the adapters use:

| Call | When |
|---|---|
| `AlarmSession.forAlarm(alarm, angryMessages?)` | create from the persisted alarm; pass localized messages from resources |
| `beginRinging()` | alarm broadcast received |
| `listenStarted()` | recognizer started |
| `onSpeech(SpeechInput)` | any partial/final recognition result |
| `listenTimeout()` | listening window ended in silence |
| `recognizerRestart()` | recoverable recognizer error |
| `emergencyStop()` | hold-to-confirm completed |
| `snapshot()` / `SessionUpdate.snapshot` | render + `action` |
| `schedule()` / `cancel()` | optional bookkeeping when not ringing; not required for correct ringing |

Each call returns `SessionUpdate(snapshot, applied)`. `applied = false` means the event was invalid in the current state
(late/duplicate callback) and was ignored — safe to drop. The core never throws for out-of-order events.
The session is single-threaded: call it from one thread (the main thread).

## 16. How speech results reach `:core`
See §6. Shape: `SpeechInput(candidates: List<SpeechCandidate(text, confidence?)>, isFinal: Boolean, relativeLevel: Double?)`.
Only final inputs count as attempts; partial inputs only update `lastMatch` (and move `LISTENING → RECOGNIZING`).
Matching details: `WakePhraseMatcher` (normalization, optional pronoun, error budget, blocked words) — see `ARCHITECTURE.md` A2.

## 17. How challenge completion is reported back to Android
Not via callbacks: the platform polls the return value. After every call, `snapshot.action == STOP_ALARM` means finished;
`snapshot.isCompleted` distinguishes real completion (success screen, record as completed) from emergency stop (no success screen).
Afterwards: compute the next occurrence and reschedule if the alarm repeats (§1); for a one-shot alarm disable it and persist.

## 18. Emergency stop — NOT IMPLEMENTED — LOCAL ANDROID PHASE
UI: a deliberately inconvenient control (hold ~5 seconds, always reachable, visually secondary). On completion call
`session.emergencyStop()`; it is valid from every ringing state ([CORE — VERIFIED], exhaustive test), after which
`action == STOP_ALARM`. It does not mark the challenge completed. The hold duration is a UI constant owned by `:app`.
Must work even if the recognizer, audio or permissions are broken.

## 19. Failure recovery — NOT IMPLEMENTED — LOCAL ANDROID PHASE
| Failure | Required behaviour |
|---|---|
| Microphone permission denied/revoked | alarm keeps ringing; show why; emergency stop available; never crash |
| Recognizer unavailable / repeated errors | `recognizerRestart()` with back-off; after a sustained failure surface the emergency path |
| Sound cannot be loaded or played | fall back to another tone/vibration; the alarm must not be silent by accident |
| Notification permission denied | still ring via the service; degrade the notification |
| Exact-alarm / alarm permission unavailable | fall back; tell the user the alarm may be late |
| Corrupt stored alarm | skip the row (§12) |
| Process killed while ringing | next alarm broadcast/boot → `beginRinging()`; progress restarts |
| Late/duplicate callbacks | rely on `applied = false`; do not add guards in `:app` |
