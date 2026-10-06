package com.bangunwoi.core.session

import com.bangunwoi.core.challenge.AngryModePolicy
import com.bangunwoi.core.challenge.AngryState
import com.bangunwoi.core.challenge.Challenge
import com.bangunwoi.core.challenge.ChallengeProgress
import com.bangunwoi.core.challenge.ChallengeResult
import com.bangunwoi.core.challenge.NoSpeechInput
import com.bangunwoi.core.challenge.SpeechInput
import com.bangunwoi.core.matching.MatchResult

public data class SessionSnapshot(
    val state: AlarmState,
    val progress: ChallengeProgress,
    val failedAttempts: Int,
    val angry: AngryState,
    /** Most recent match (partial or final) for UI display. */
    val lastMatch: MatchResult?,
)

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

    public fun snapshot(): SessionSnapshot = SessionSnapshot(
        state = state,
        progress = challenge.progress,
        failedAttempts = challenge.failedAttempts,
        angry = angryMode.stateFor(challenge.failedAttempts),
        lastMatch = lastMatch,
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
        }
        return SessionUpdate(snapshot(), update.applied)
    }

    /** The listening window ended with nothing heard: a counted failed attempt. */
    public fun listenTimeout(): SessionUpdate {
        val update = apply(AlarmEvent.ListenTimeout)
        if (update.applied) challenge.processInput(NoSpeechInput)
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
                lastMatch = result.match ?: lastMatch
                apply(AlarmEvent.PhraseRejected)
            }
            is ChallengeResult.Progressed -> {
                lastMatch = result.match
                apply(AlarmEvent.PhraseAccepted(challengeComplete = false))
            }
            is ChallengeResult.Completed -> {
                lastMatch = result.match
                apply(AlarmEvent.PhraseAccepted(challengeComplete = true))
            }
            ChallengeResult.Ignored -> SessionUpdate(snapshot(), applied = false)
        }
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
