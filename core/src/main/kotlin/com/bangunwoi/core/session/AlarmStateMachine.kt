package com.bangunwoi.core.session

public enum class AlarmState {
    IDLE, SCHEDULED, TRIGGERED, LISTENING, RECOGNIZING,
    CHALLENGE_FAILED, CHALLENGE_PROGRESS, COMPLETED, EMERGENCY_STOP;

    /** The alarm is ringing and the user has not finished yet. */
    public val isRinging: Boolean
        get() = this in RINGING

    public val isTerminal: Boolean
        get() = this == COMPLETED || this == EMERGENCY_STOP

    private companion object {
        val RINGING = setOf(TRIGGERED, LISTENING, RECOGNIZING, CHALLENGE_FAILED, CHALLENGE_PROGRESS)
    }
}

public sealed interface AlarmEvent {
    public data object Schedule : AlarmEvent
    public data object Cancel : AlarmEvent
    public data object Fire : AlarmEvent
    public data object ListenStarted : AlarmEvent
    public data object SpeechDetected : AlarmEvent
    /** Listening window ended with no speech. */
    public data object ListenTimeout : AlarmEvent
    /** Recognizer error that is not the user's fault: listen again without counting a failure. */
    public data object RecognizerRestart : AlarmEvent
    public data object PhraseRejected : AlarmEvent
    public data class PhraseAccepted(val challengeComplete: Boolean) : AlarmEvent
    public data object EmergencyStop : AlarmEvent
}

public sealed interface TransitionResult {
    public data class Valid(val to: AlarmState) : TransitionResult
    public data class Invalid(val from: AlarmState, val event: AlarmEvent) : TransitionResult
}

/**
 * Pure transition table; see docs/ARCHITECTURE.md for the diagram. Invalid transitions are reported,
 * never thrown: an alarm must not crash because an Android callback arrived late or twice.
 */
public object AlarmStateMachine {
    public fun next(from: AlarmState, event: AlarmEvent): TransitionResult {
        val to = target(from, event)
        return if (to == null) TransitionResult.Invalid(from, event) else TransitionResult.Valid(to)
    }

    private fun target(from: AlarmState, event: AlarmEvent): AlarmState? = when (event) {
        AlarmEvent.Schedule ->
            if (!from.isRinging) AlarmState.SCHEDULED else null
        AlarmEvent.Cancel ->
            if (!from.isRinging) AlarmState.IDLE else null // cannot be cancelled while ringing
        AlarmEvent.Fire ->
            if (from == AlarmState.SCHEDULED) AlarmState.TRIGGERED else null
        AlarmEvent.ListenStarted -> when (from) {
            AlarmState.TRIGGERED, AlarmState.CHALLENGE_FAILED, AlarmState.CHALLENGE_PROGRESS -> AlarmState.LISTENING
            else -> null
        }
        AlarmEvent.SpeechDetected ->
            if (from == AlarmState.LISTENING) AlarmState.RECOGNIZING else null
        AlarmEvent.ListenTimeout ->
            if (from == AlarmState.LISTENING) AlarmState.CHALLENGE_FAILED else null
        AlarmEvent.RecognizerRestart ->
            if (from == AlarmState.LISTENING || from == AlarmState.RECOGNIZING) AlarmState.LISTENING else null
        // A final result may arrive without a preceding partial result, so LISTENING is allowed too.
        AlarmEvent.PhraseRejected ->
            if (from == AlarmState.LISTENING || from == AlarmState.RECOGNIZING) AlarmState.CHALLENGE_FAILED else null
        is AlarmEvent.PhraseAccepted ->
            if (from == AlarmState.LISTENING || from == AlarmState.RECOGNIZING) {
                if (event.challengeComplete) AlarmState.COMPLETED else AlarmState.CHALLENGE_PROGRESS
            } else null
        AlarmEvent.EmergencyStop ->
            if (from.isRinging) AlarmState.EMERGENCY_STOP else null
    }
}
