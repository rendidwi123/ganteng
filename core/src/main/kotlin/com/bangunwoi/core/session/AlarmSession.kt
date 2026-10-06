package com.bangunwoi.core.session

import com.bangunwoi.core.challenge.AngryModePolicy
import com.bangunwoi.core.challenge.AngryState
import com.bangunwoi.core.challenge.Challenge
import com.bangunwoi.core.challenge.ChallengeProgress
import com.bangunwoi.core.challenge.ChallengeResult
import com.bangunwoi.core.challenge.NoSpeechInput
import com.bangunwoi.core.challenge.SpeechInput
import com.bangunwoi.core.challenge.WakePhraseChallenge
import com.bangunwoi.core.domain.Alarm
import com.bangunwoi.core.matching.MatchResult

/** What the platform layer should do next; derived purely from the state so it never has to guess. */
public enum class SessionAction {
    /** Nothing is ringing. */
    NONE,
    /** Ringing and the recognizer is not running: start it, then call [AlarmSession.listenStarted]. */
    START_LISTENING,
    /** Recognizer is running; keep feeding results. */
    KEEP_LISTENING,
    /** Finished (completed or emergency stop): stop audio, recognizer, service and notification. */
    STOP_ALARM,
}

public data class SessionSnapshot(
    val state: AlarmState,
    val progress: ChallengeProgress,
    val failedAttempts: Int,
    /** Escalation for the ringing alarm; calm (level 0) whenever the alarm is not ringing. */
    val angry: AngryState,
    /** Most recent match (partial or final) for UI display. */
    val lastMatch: MatchResult?,
    /** Result of the most recent challenge evaluation (e.g. why it was rejected), for UI feedback. */
    val lastResult: ChallengeResult?,
) {
    public val action: SessionAction
        get() = when (state) {
            AlarmState.IDLE, AlarmState.SCHEDULED -> SessionAction.NONE
            AlarmState.TRIGGERED, AlarmState.CHALLENGE_FAILED, AlarmState.CHALLENGE_PROGRESS -> SessionAction.START_LISTENING
            AlarmState.LISTENING, AlarmState.RECOGNIZING -> SessionAction.KEEP_LISTENING
            AlarmState.COMPLETED, AlarmState.EMERGENCY_STOP -> SessionAction.STOP_ALARM
        }

    /** True only for a genuine completion (not for an emergency stop). */
    public val isCompleted: Boolean get() = state == AlarmState.COMPLETED
}

/** [applied] is false when the event was invalid in the current state and was ignored. */
public data class SessionUpdate(val snapshot: SessionSnapshot, val applied: Boolean)

/**
 * One ringing alarm: owns the [AlarmState], a [Challenge] and the Angry Mode escalation.
 * This is the single object the Android layer (Activity/Service) will drive; it has no threads,
 * no clock and no Android types, so all of it is unit-testable. Not thread-safe: call from one thread
 * (the Android main thread).
 */
public class AlarmSession(
    private val challenge: Challenge,
    private val angryMode: AngryModePolicy = AngryModePolicy(),
) {
    public var state: AlarmState = AlarmState.IDLE
        private set
    private var lastMatch: MatchResult? = null
    private var lastResult: ChallengeResult? = null

    public fun snapshot(): SessionSnapshot = SessionSnapshot(
        state = state,
        progress = challenge.progress,
        failedAttempts = challenge.failedAttempts,
        angry = angryMode.stateFor(if (state.isRinging) challenge.failedAttempts else 0),
        lastMatch = lastMatch,
        lastResult = lastResult,
    )

    public fun schedule(): SessionUpdate = apply(AlarmEvent.Schedule)
    public fun cancel(): SessionUpdate = apply(AlarmEvent.Cancel)
    public fun emergencyStop(): SessionUpdate = apply(AlarmEvent.EmergencyStop)
    public fun listenStarted(): SessionUpdate = apply(AlarmEvent.ListenStarted)
    public fun recognizerRestart(): SessionUpdate = apply(AlarmEvent.RecognizerRestart)

    public fun fire(): SessionUpdate {
        val update = apply(AlarmEvent.Fire)
        if (update.applied) {
            challenge.start()
            lastMatch = null
            lastResult = null
        }
        return SessionUpdate(snapshot(), update.applied)
    }

    /**
     * Start ringing from whatever idle state we are in. A fresh process (after reboot or process death) has no
     * memory of having scheduled the alarm and sits in IDLE, so the platform calls this when the alarm
     * broadcast arrives instead of replaying schedule()/fire() itself. No-op (applied = false) if already ringing.
     */
    public fun beginRinging(): SessionUpdate {
        if (state.isRinging) return SessionUpdate(snapshot(), applied = false)
        if (state != AlarmState.SCHEDULED) apply(AlarmEvent.Schedule)
        return fire()
    }

    /** The listening window ended with nothing heard: a counted failed attempt. */
    public fun listenTimeout(): SessionUpdate {
        val update = apply(AlarmEvent.ListenTimeout)
        if (update.applied) lastResult = challenge.processInput(NoSpeechInput)
        return SessionUpdate(snapshot(), update.applied)
    }

    /** Feed a partial or final recognizer result. Ignored unless the session is listening/recognizing. */
    public fun onSpeech(input: SpeechInput): SessionUpdate {
        if (state != AlarmState.LISTENING && state != AlarmState.RECOGNIZING) {
            return SessionUpdate(snapshot(), applied = false)
        }
        return when (val result = challenge.processInput(input)) {
            is ChallengeResult.Preview -> {
                lastMatch = result.match
                if (state == AlarmState.LISTENING && result.match.normalizedTranscript.isNotEmpty()) {
                    apply(AlarmEvent.SpeechDetected)
                } else SessionUpdate(snapshot(), applied = true)
            }
            is ChallengeResult.Rejected -> {
                lastResult = result
                lastMatch = result.match ?: lastMatch
                apply(AlarmEvent.PhraseRejected)
            }
            is ChallengeResult.Progressed -> {
                lastResult = result
                lastMatch = result.match
                apply(AlarmEvent.PhraseAccepted(challengeComplete = false))
            }
            is ChallengeResult.Completed -> {
                lastResult = result
                lastMatch = result.match
                apply(AlarmEvent.PhraseAccepted(challengeComplete = true))
            }
            ChallengeResult.Ignored -> SessionUpdate(snapshot(), applied = false)
        }
    }

    public companion object {
        /** Wires a [WakePhraseChallenge] and Angry Mode from the persisted alarm; no other setup is needed. */
        public fun forAlarm(
            alarm: Alarm,
            angryMessages: List<String> = AngryModePolicy.DEFAULT_MESSAGES,
        ): AlarmSession = AlarmSession(
            WakePhraseChallenge(alarm.wakePhrase, alarm.challenge),
            AngryModePolicy(alarm.challenge.angryMode, angryMessages),
        )
    }

    private fun apply(event: AlarmEvent): SessionUpdate =
        when (val t = AlarmStateMachine.next(state, event)) {
            is TransitionResult.Valid -> {
                state = t.to
                SessionUpdate(snapshot(), applied = true)
            }
            is TransitionResult.Invalid -> SessionUpdate(snapshot(), applied = false)
        }
}
