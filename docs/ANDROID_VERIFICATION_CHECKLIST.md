# Android verification checklist

Every item below is **unverified**. Nothing here has been tested on an emulator or device, and none of it has been checked
against current Android documentation (the Cloud session has no Android SDK and cannot reach Google hosts).
Items are *questions to answer*, not claims. Do not tick a box without evidence: a documentation link with date, or a
device/OS/OEM test result written next to the item.

Evidence key: **D** = read current official docs/SDK source, **E** = emulator, **P** = physical device
(include at least one Samsung, one Xiaomi/Oppo/Vivo and one stock/Pixel device, and the newest OS available).

Related: [ANDROID_INTEGRATION_CONTRACT.md](ANDROID_INTEGRATION_CONTRACT.md) · [ARCHITECTURE.md](ARCHITECTURE.md)


## A. Alarm scheduling

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — `AlarmManager.setAlarmClock`: does it fire while idle, locked, in Doze, after the app is swiped away? Trigger accuracy in minutes/seconds.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Does `setAlarmClock` show a system alarm indicator, and does its `showIntent` behave as intended (opens the app's alarm list)?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — One-shot and repeating alarms: reschedule-after-fire path produces the right next time (compare with `Alarm.nextTriggerAfter`).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Multiple alarms with different ids do not overwrite each other (request code / PendingIntent identity and flags).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Behaviour of the OS alarm across a DST change vs. the core's DST semantics (gap shifts forward; repeated hour rings once).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — System time and time-zone changes: which broadcasts arrive, is rescheduling required.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Alarm survives app force-stop, swipe from recents, 'restricted' app standby bucket.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — `java.time` usage on minSdk 26: is core library desugaring needed? Final minSdk decision and its market coverage.

## B. Exact alarms

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Does `setAlarmClock` require `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` on any API level we support?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Default grant state and user revocability of exact-alarm permissions on each API level; `canScheduleExactAlarms()` behaviour.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Fallback when exact alarms are unavailable: what do we schedule instead and what do we tell the user?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Play policy for `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` for alarm apps, and whether we need either.

## C. Full-screen intent

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Full-screen intent shows the ringing Activity over the lock screen and turns the screen on.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Behaviour when the screen is already on and unlocked (heads-up notification only?).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — `USE_FULL_SCREEN_INTENT`: default grant state, revocation, `canUseFullScreenIntent()`, settings deep link.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Fallback when full-screen intent is denied: what does the user see/hear, can the challenge still be completed?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Play Console declaration/policy for `USE_FULL_SCREEN_INTENT`.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Activity launch restrictions from the background: does our design (notification full-screen intent) avoid them?

## D. Lock screen

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — `setShowWhenLocked` / `setTurnScreenOn` / keyguard dismissal behaviour on secure (PIN/biometric) and insecure lock screens.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — What the user can do to leave the alarm UI (back, home, recents, power, notification shade) and how the challenge stays honest without trapping the user.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Behaviour with Do Not Disturb, focus modes, 'alarms only', and when the device is in a call.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Screen-off / screen-timeout while ringing: keep-screen-on and wake-lock needs and release.

## E. Foreground service

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — May a foreground service be started from an alarm broadcast on every API level we support, including with a media-playback type?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Required `foregroundServiceType` and manifest permissions per API level; Play declaration requirements.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Any time limits or restart behaviour of the chosen service type; behaviour when the system stops the service.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Correct `AudioAttributes`/stream for alarm playback; behaviour in silent/vibrate mode and with alarm volume 0.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Audio focus handling when another app is playing; incoming call during an alarm.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Bluetooth headset/speaker connected: where does the alarm play, can it be missed?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Sound resource fails to load: fallback tone/vibration actually works.

## F. Microphone

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Can the visible lock-screen Activity capture audio on each API level? Can a service? (while-in-use rules)
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — `RECORD_AUDIO` rationale UX, 'denied permanently' flow, one-time grants, auto-revocation after inactivity.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Behaviour when another app holds the microphone (call, voice recorder).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Does `onRmsChanged` (or another source) give a usable *relative* loudness across devices for the NORMAL gate, and how should it be normalized to 0..1?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Microphone privacy indicator / system UI effects while ringing.

## G. Speech recognition

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — `SpeechRecognizer` availability: `isRecognitionAvailable`, package-visibility `<queries>` requirement, devices without a recognition service.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — On-device recognition APIs; is an Indonesian (`id-ID`) offline model available, and how does the user install it?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Real Indonesian recognition output for 'gw udah bangun' and informal variants ('gw' vs 'gue', 'udah' vs 'sudah'): add observed outputs to `WakePhraseTestData`.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Recognizer hearing the alarm's own sound: how much ducking/pausing is needed; effect of speaker vs. earpiece.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Error codes and lifecycle: no match, speech timeout, busy, network, insufficient permissions; correct mapping to `listenTimeout()` / `recognizerRestart()`.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Partial results behaviour; how often final results arrive; whether confidences are ever populated.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Recognizer start/stop sounds and restart latency.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — What the recognition service does with audio (network use, retention). Required before any privacy statement in the README or store listing.

## H. Notifications

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — `POST_NOTIFICATIONS` runtime permission: what still works when denied (does the full-screen intent still show, does the foreground service notification still appear)?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Notification channel settings for the alarm (importance, alarm category, user-modified/silenced channels); re-creating channels.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Behaviour of ongoing/non-dismissable notification while the alarm rings.

## I. Doze

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Alarm delivery on an idle, unplugged device in deep Doze for several hours (overnight test).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Behaviour in battery-saver mode and in 'adaptive battery' standby buckets.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Wake-lock duration needed between receiver and Activity start.

## J. Battery optimization

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — OEM task killers (Samsung, Xiaomi, Oppo, Vivo, Huawei): does the alarm still ring with default settings? What guidance must the app give?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Play policy on `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for an alarm app (we currently plan not to request it).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Behaviour after the app is unused for a long time (app hibernation / permission auto-reset).

## K. Reboot

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Which broadcasts arrive and when: `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, time/timezone changes.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Alarm before first unlock after reboot (direct boot): limitation or does it need device-protected storage?
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Alarms are cleared on reboot and on app update — confirm, and confirm `rescheduleAll` restores them.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Alarm that should have rung while the phone was off: what do we do on next boot (ignore vs. ring late)?

## L. Android 14+

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Foreground-service type requirements and permissions introduced in Android 14.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Full-screen-intent permission behaviour on Android 14 (see C).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Exact-alarm permission defaults on Android 14 (see B).
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Any restrictions on starting activities or services from broadcast receivers.

## M. Android 15+

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Release notes / behaviour changes for Android 15 affecting alarms, foreground services, full-screen intents, microphone access or boot receivers.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Any change to foreground-service timeouts or start-from-boot rules relevant to our design.

## N. Android 16+

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Release notes / behaviour changes for Android 16 (and later at implementation time) affecting the same areas as M.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Re-test the full manual alarm checklist on the newest available OS version.

## O. Google Play requirements

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Play's current required target API level for new apps and updates, and its deadline.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Policy declarations for alarm-related special permissions and foreground-service types.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Data-safety form content for a microphone-using app with no backend; privacy-policy requirements.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Content/safety review of Angry Mode messages and the emergency-stop design.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Signing, Play App Signing and app-bundle requirements at release time.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Store listing claims about offline recognition and privacy — only after G (service behaviour) is verified.

## P. Core ↔ Android contract (code-level; verify when `:app` exists)

- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Recognizer callbacks map to `AlarmSession` calls exactly as in ANDROID_INTEGRATION_CONTRACT §6.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Session reconstruction after process death while ringing (`beginRinging()` path) behaves acceptably.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Emergency stop (hold) works in every recognizer/audio state on a real device.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Room mapping round-trips every `Alarm` field and survives a corrupted row without crashing.
- [ ] NOT VERIFIED — REQUIRES LOCAL ANDROID DEVICE/SDK — Angry intensity → volume/vibration mapping is audible but safe (no sudden hearing-damaging jump).

## Z. Already verified (JVM unit tests only — NOT Android verification)

These are properties of `:core`, checked by `./gradlew :core:test`. They say nothing about Android behaviour.

- [x] `AlarmSchedule.nextTriggerAfter`: one-shot, daily, weekday, weekend, specific days, exact-time, ±1 minute, midnight, Sunday→Monday, month/year/leap-day rollover, DST gap/overlap (fixed inputs, no system clock).
- [x] Disabled alarm has no next trigger (`Alarm.nextTriggerAfter` returns null).
- [x] `RepeatDays` bitmask: Mon=bit0..Sun=bit6, all 128 masks round-trip, invalid masks rejected.
- [x] Domain validation: hour/minute, phrase, threshold, label, ids, timestamps, required successes, volume.
- [x] `WakePhraseMatcher`: accepts/rejects the shared dataset (`WakePhraseTestData`), error budget, blocked words, optional pronoun.
- [x] Challenge: EASY/NORMAL/HARD flows, partial results never counted, progress kept across failures, reset.
- [x] `AlarmStateMachine`: exhaustive state × event table; emergency stop reachable from every ringing state.
- [x] `AlarmSession`: realistic sessions, late/duplicate callbacks ignored, `beginRinging()`, derived `SessionAction`.
- [x] Angry Mode: deterministic escalation, cap, disabled mode, calm after completion/emergency stop.
