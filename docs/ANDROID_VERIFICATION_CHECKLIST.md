# Android verification checklist

Everything below is **NOT VERIFIED**. Nothing here has been tested on an emulator or device, and none of it has been
checked against current Android documentation in this session (the SDK and docs hosts were unreachable).
Entries are *questions to answer*, with a note on why each matters. Do not tick a box without evidence
(doc link + date, or a device/OS/OEM test result). Record the evidence next to the item when ticking.

How to verify: **D** = read current official docs / SDK source, **E** = emulator, **P** = physical device(s)
(include at least one Samsung, one Xiaomi/Oppo/Vivo, one Pixel/stock, and an Android 14/15+ device if available).

## 1. Scheduling and reliability
- [ ] NOT VERIFIED (D,P) `AlarmManager.setAlarmClock`: fires while idle, locked, in Doze, app swiped away; trigger accuracy.
- [ ] NOT VERIFIED (D) Does `setAlarmClock` need `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` on any API level? Behaviour on each level we support.
- [ ] NOT VERIFIED (D) Play policy for `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` and whether we need either at all.
- [ ] NOT VERIFIED (D,P) Behaviour when exact-alarm / alarm permission is unavailable or revoked: our fallback and user messaging.
- [ ] NOT VERIFIED (P) Alarm survives: app force-stop, swipe from recents, battery saver, "restricted" background state.
- [ ] NOT VERIFIED (P) Battery optimisation / OEM task killers (Samsung, Xiaomi, Oppo, Vivo, Huawei): does the alarm still ring? Needed user guidance.
- [ ] NOT VERIFIED (D) Play policy on `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for an alarm app.
- [ ] NOT VERIFIED (E,P) Boot restoration: which broadcasts arrive (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) and when.
- [ ] NOT VERIFIED (P) Alarm before first unlock after reboot (direct boot): limitation or needs device-protected storage.
- [ ] NOT VERIFIED (E,P) System time change, time zone change, DST change: reschedule needed? which broadcasts?
- [ ] NOT VERIFIED (D) Whether `java.time` use is fine on our minSdk (desugaring needed?).
- [ ] NOT VERIFIED (D) Final minSdk choice and its Play/market coverage.

## 2. Launching the alarm UI
- [ ] NOT VERIFIED (D,P) Full-screen intent: shows over lock screen and turns screen on; behaviour when screen is on and unlocked (heads-up only?).
- [ ] NOT VERIFIED (D) `USE_FULL_SCREEN_INTENT` rules on Android 14+: default grant state, user revocation, `canUseFullScreenIntent()`, settings deep link.
- [ ] NOT VERIFIED (D) Android 15+ and newer: any change to full-screen intents, activity launch from background, foreground service, or alarm behaviour.
- [ ] NOT VERIFIED (D) Play policy / Play Console declaration required for `USE_FULL_SCREEN_INTENT`.
- [ ] NOT VERIFIED (P) `setShowWhenLocked` / `setTurnScreenOn` / keyguard behaviour on secure vs. insecure lock screens.
- [ ] NOT VERIFIED (P) Behaviour with Do Not Disturb, Focus modes, and "alarms only".
- [ ] NOT VERIFIED (D,P) Notification channel settings for the ringing notification (importance, category alarm, user-modified channels, silenced channel).
- [ ] NOT VERIFIED (D,P) `POST_NOTIFICATIONS` on Android 13+: what still works when denied (does the full-screen intent still show?).
- [ ] NOT VERIFIED (P) What the user can do to leave the alarm UI (back, home, recents, power button, notification shade) and how we keep the challenge honest without trapping them.

## 3. Foreground service and audio
- [ ] NOT VERIFIED (D) Foreground service start from an alarm broadcast: allowed on every target level we support? Required `foregroundServiceType` and manifest permissions on Android 14+.
- [ ] NOT VERIFIED (D) Time limits or restrictions on `mediaPlayback` foreground services in newer versions.
- [ ] NOT VERIFIED (D,P) Correct `AudioAttributes` / stream for alarm playback; behaviour with silent/vibrate mode and with alarm volume at 0.
- [ ] NOT VERIFIED (P) Bluetooth headset/speaker connected: where does the alarm play; can it be missed?
- [ ] NOT VERIFIED (D,P) Audio focus handling when another app is playing; calls in progress.
- [ ] NOT VERIFIED (P) Wake lock needs and release.
- [ ] NOT VERIFIED (P) Behaviour if the sound resource fails to load (fallback tone/vibration).

## 4. Microphone and speech recognition
- [ ] NOT VERIFIED (D,P) Microphone access from a lock-screen Activity vs. from a service on each Android version (while-in-use rules).
- [ ] NOT VERIFIED (D) `RECORD_AUDIO`: when to request it (editor/onboarding), rationale UX, "denied permanently" flow, one-time/auto-revoked permissions after app inactivity.
- [ ] NOT VERIFIED (D,P) `SpeechRecognizer` availability: `isRecognitionAvailable`, `<queries>` requirement, devices without Google speech services.
- [ ] NOT VERIFIED (D,P) On-device recognizer APIs and whether an Indonesian offline model is available and how users install it.
- [ ] NOT VERIFIED (P) Indonesian (`id-ID`) recognition quality for "gw udah bangun" and informal variants; what text the recognizer actually returns for "gw" vs "gue".
- [ ] NOT VERIFIED (P) Recognition with the alarm playing: does the recognizer hear the alarm; how much ducking is needed; effect of speakerphone vs. earpiece.
- [ ] NOT VERIFIED (P) Recognizer lifecycle: timeouts, error codes (no match, speech timeout, busy, network, insufficient permissions), restart/back-off strategy, recognizer start/stop beeps.
- [ ] NOT VERIFIED (P) Partial results and continuous speech: how often final results arrive; confidence values (are they ever populated?).
- [ ] NOT VERIFIED (P) Whether `onRmsChanged` is usable as a *relative* loudness heuristic across devices.
- [ ] NOT VERIFIED (D) What the platform/third-party recognition service does with audio (network use, retention) — needed before any privacy claim. The README must not state anything about this that we haven't verified.
- [ ] NOT VERIFIED (P) Behaviour when another app holds the microphone (call, voice recorder).

## 5. Distribution and policy
- [ ] NOT VERIFIED (D) Play's current required target API level for new apps and updates, and the deadline.
- [ ] NOT VERIFIED (D) Play policy regarding alarm apps' special permissions and any declaration forms.
- [ ] NOT VERIFIED (D) Play policy for foreground service type declarations.
- [ ] NOT VERIFIED (D) Data safety form content for a mic-using, no-backend app; privacy policy requirements.
- [ ] NOT VERIFIED (D) Content/safety policy review of "angry" messages and the Emergency Stop design.
- [ ] NOT VERIFIED (D) App signing, Play App Signing, AAB requirements at time of release.

## 6. Core ↔ Android contract (code-level, verify when `:app` exists)
- [ ] NOT VERIFIED Mapping of recognizer callbacks → `AlarmSession` events (partial → `onSpeech(isFinal=false)`, final → `isFinal=true`, silence/timeout → `listenTimeout()`, recoverable errors → `recognizerRestart()`).
- [ ] NOT VERIFIED Session reconstruction after process death while an alarm is ringing.
- [ ] NOT VERIFIED Emergency stop (hold 5 s) works when the recognizer/audio are in any state.
- [ ] NOT VERIFIED Room mapping round-trips every `Alarm` field (incl. repeat days, thresholds).
