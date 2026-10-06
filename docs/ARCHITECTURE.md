# BANGUN WOI! — Requirements analysis & architecture (Phase 1)

> Status: design only. No application code exists yet. See "Build environment blocker" at the end.

## 1. What the app is

An alarm that can only be dismissed by saying a wake phrase ("GW UDAH BANGUN" by default) out loud.
Speech is transcribed by Android's own recognizer; a fuzzy matcher decides whether the phrase was said.
A hold-to-confirm emergency stop prevents the user from ever being trapped.

## 2. Platform decisions

| Topic | Decision | Reason |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose, Material 3 | Alarm screen is one Activity; Compose is fine and lets us share theme/state with the rest of the app. `ComponentActivity` + `setShowWhenLocked/setTurnScreenOn` works the same as with XML. |
| minSdk | 26 (Android 8.0) | Notification channels and adaptive icons are guaranteed; avoids legacy branches in alarm code. |
| compileSdk / targetSdk | Latest stable at implementation time. Google Play requires new/updated apps to target a recent API level (API 35 as of 2025; the requirement rises yearly, **re-verify before release**). | Behaviour-change rules (FGS types, FSI permission) apply by targetSdk. |
| DI | Manual `AppContainer` in `Application` | Small app; Hilt adds build time and a dependency for no gain. |
| Persistence | Room (alarms table) + DataStore Preferences (global settings, e.g. angry messages toggle) | Alarms are a small relational list; settings are key/value. |
| Async | Coroutines + `StateFlow` | Standard; ViewModels expose immutable UI state. |
| Dependencies | Compose BOM, Material 3, Lifecycle/ViewModel, Navigation-Compose, Room (KSP), DataStore, JUnit, kotlinx-coroutines-test, Robolectric only if needed | Nothing else. No networking library, no analytics, no ads SDK. |

## 3. Package layout (single `app` module)

```
com.bangunwoi.app            (applicationId is a placeholder: change before publishing)
  data/        Room entities/DAO, AlarmRepository impl, SettingsStore (DataStore)
  domain/      Alarm, Difficulty, RepeatDays, Challenge + ChallengeEngine, AlarmCalculator
               (pure Kotlin, no Android imports -> plain JUnit tests)
  alarm/       AlarmScheduler (AlarmManager), AlarmReceiver, BootReceiver, AlarmService,
               AlarmRingingActivity
  speech/      SpeechRecognitionManager, WakePhraseMatcher, PhraseNormalizer
  audio/       AlarmAudioManager (MediaPlayer/AudioAttributes USAGE_ALARM), VolumeRamp
  notification/ NotificationFactory (channels, full-screen intent)
  ui/          theme, nav, list screen, edit screen, ringing screen, success screen, onboarding/permissions
  util/        Clock (injectable), PermissionHelpers
```

Rule: `domain` and `speech/WakePhraseMatcher` have zero Android dependencies so the logic that matters most is unit-testable on the JVM.

## 4. Key flows

**Scheduling.** `AlarmCalculator.nextTrigger(alarm, now, zone)` is pure. `AlarmScheduler` calls
`AlarmManager.setAlarmClock(AlarmClockInfo(trigger, showIntent), operationPendingIntent)`.
`setAlarmClock` is exempt from Doze and app-standby restrictions, shows the system alarm icon, and does **not**
require `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`, so we avoid that permission and its Play policy review.
One PendingIntent per alarm id (`requestCode = id`, `FLAG_IMMUTABLE`). Only the *next* occurrence is scheduled;
the receiver reschedules repeating alarms right after firing.

**Firing.** `AlarmReceiver` → starts `AlarmService` as a foreground service (type `mediaPlayback`) with an
ongoing notification carrying a full-screen intent to `AlarmRingingActivity`; the service plays audio on an
alarm audio stream and holds a short wake lock. Receiver finishes fast (no work in `onReceive` beyond start + reschedule).

**Challenge.** `AlarmRingingActivity` (`showWhenLocked`, `turnScreenOn`, keep-screen-on, back/home swallowed
only as far as Android allows) owns the `SpeechRecognizer`, because microphone capture must happen while the app has a
visible UI (see risks). A `ChallengeEngine` state machine (Idle → Listening → Matched/Failed → …) is driven by recognizer
events and `WakePhraseMatcher`. EASY needs 1 match, NORMAL 1 match above a relative loudness gate, HARD N=3 matches.
`Challenge` is an interface so math/QR/shake types can be added later without touching the activity.

**Angry mode.** Failure count → intensity level → audio gain ramp + message from an editable string-array resource.
Defaults are cheeky, never abusive.

**Emergency stop.** Hold-for-5-seconds button, visible always but visually secondary. Also auto-offered after a
sustained recognizer-unavailable state. Stops audio, service, notification. Never blocks the system UI beyond the
activity itself (no device-admin, no kiosk/lock-task, no overlay hacks).

**Reboot / time changes.** `BootReceiver` handles `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET`,
`MY_PACKAGE_REPLACED` → reschedule all enabled alarms. `RECEIVE_BOOT_COMPLETED` is genuinely required because
AlarmManager alarms are cleared on reboot and on app update.

## 5. WakePhraseMatcher design

1. Normalize: lowercase, strip diacritics, drop punctuation/digits, collapse whitespace.
2. Canonicalize tokens through a small, editable synonym table: `gw/gue/gua/ane/aku/saya → <I>`,
   `udah/sudah/dah/udh/sdh → <already>`, `bangun/bgn/bangunn → bangun`.
3. Score = best alignment of the target token sequence inside the transcript tokens (so "ya gue udah bangun nih"
   and continuous speech still match), using per-token Levenshtein similarity averaged over target tokens.
4. Accept if score ≥ threshold (default 0.8, configurable; per-difficulty override possible).
5. Custom phrases get no synonym help beyond normalization + fuzzy similarity; empty/very short phrases are rejected at save time.
6. Evaluate every partial and final result and all N-best alternatives from the recognizer.

## 6. Permissions (planned)

| Permission | Why | When requested |
|---|---|---|
| `RECORD_AUDIO` (runtime) | Wake challenge | In the alarm editor / onboarding, **never** for the first time while an alarm is ringing |
| `POST_NOTIFICATIONS` (13+, runtime) | Alarm notification / full-screen intent | Onboarding; app degrades but still rings via the foreground service if denied |
| `USE_FULL_SCREEN_INTENT` (14+ may be revocable) | Show ringing UI over lock screen | Check `NotificationManager.canUseFullScreenIntent()`; if false, deep-link to its settings page and fall back to a heads-up notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep audio alive | Manifest only |
| `RECEIVE_BOOT_COMPLETED` | Reschedule after reboot | Manifest only |
| `WAKE_LOCK` | Turn screen on / keep CPU awake while ringing | Manifest only |
| `VIBRATE` | Optional vibration | Manifest only |
| **Not used:** `INTERNET`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Not needed / Play-restricted | — |

Also needed: a `<queries>` entry for `android.speech.RecognitionService` so `SpeechRecognizer.isRecognitionAvailable()` is accurate on Android 11+.

## 7. Top technical risks (ranked)

1. **Microphone vs. blaring alarm.** The recognizer will hear our own alarm. Echo cancellation helps but is not
   guaranteed. Plan: duck/pause the alarm while a listening window is open (short bursts), with intensity ramping
   between windows; tune on real devices. Needs hands-on testing; cannot be settled on paper.
2. **Microphone from a locked/background state.** Android 11+ restricts background mic access. We therefore run
   recognition inside the visible full-screen Activity, not the service. If the full-screen intent is denied or the
   Activity cannot launch, there is no visible UI → mic unavailable → emergency path + audible alarm only.
   Must verify on API 29, 33, 34, 35+ devices.
3. **Full-screen intent availability.** From Android 14 the permission can be revoked and Play limits it to
   alarm/calling apps; the app must declare its purpose accurately in Play Console.
4. **Foreground-service start rules.** Starting an FGS (esp. with a type) from a background receiver is restricted;
   alarm-clock alarms are expected to be allowed but I have not verified every case — to be tested on-device.
5. **SpeechRecognizer variability.** Offline Indonesian models may not be installed; the default service may
   use the network (we make no privacy claims about it beyond "handled by the device's speech service"); frequent
   `ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_RECOGNIZER_BUSY`; some devices have no recognizer at all
   (many AOSP/Huawei builds). Needs a restart loop with backoff and a clear "unavailable → emergency stop" UX.
6. **OEM task killers** (Xiaomi, Oppo, Vivo, Samsung — large share of Indonesian users). Mitigate with in-app guidance; do not request battery-optimization exemption (Play policy).
7. **Direct boot.** Alarms before first unlock after reboot would need device-protected storage; out of scope for MVP, documented as a known limitation.
8. **Loudness gating (NORMAL)** is only a relative heuristic (RMS from `onRmsChanged`, calibrated per session). Not a dB measurement; will be documented as such.

## 8. Phases

| # | Phase | Output | Needs SDK build? |
|---|---|---|---|
| 1 | Analysis + architecture | this document | no |
| 2 | Project + basic UI shell | Gradle project, theme, list/edit screens (state hoisted) | yes |
| 3 | Persistence + scheduling | Room, repository, `AlarmCalculator`, `AlarmScheduler`, tests | yes |
| 4 | Triggering + audio | receiver, service, notification, ringing activity, audio | yes (+device) |
| 5–7 | Speech, matcher, integration | recognizer manager, `WakePhraseMatcher`, challenge wiring, emergency stop | yes (+device) |
| 8 | Repeat + reboot | boot/time receivers, repeat logic | yes |
| 9 | Difficulty + angry mode | `Challenge` impls | yes |
| 10–12 | Tests, polish, release | README/privacy/Play assets, release build config | yes |

## 9. Build environment blocker (must be resolved before Phase 2)

This cloud session's network policy returns **403 for `dl.google.com`** (and `maven.google.com`, which redirects
there). That host serves the Android SDK, the Android Gradle Plugin and all AndroidX/Compose artifacts, so an
Android project cannot be resolved or compiled here. Maven Central and the Gradle plugin portal are reachable
but do not carry these artifacts. Consequently I have not written any Android code: I won't commit code that has
never been compiled or tested.

To continue: in the environment's network settings, allow `dl.google.com` and `maven.google.com` (plus
`developer.android.com` if docs lookups are wanted); then the setup script must install JDK 17+ (21 is present),
the Android command-line tools, `platform-tools`, `platforms;android-35`/latest and a matching `build-tools`.
Alternatively, build in Android Studio locally and use this repo only for source.
