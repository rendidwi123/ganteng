package com.bangunwoi.core.challenge

import com.bangunwoi.core.matching.MatchResult

/**
 * Marker for anything a platform layer can feed into a [Challenge]. It is an open interface (not sealed)
 * so future challenges (math, QR, shake, ...) can define their own input types without touching this file.
 */
public interface ChallengeInput

/** One recognizer hypothesis. [confidence] is 0..1 when the recognizer provides one, else null. */
public data class SpeechCandidate(val text: String, val confidence: Double? = null)

/**
 * Speech recognizer output. Only [isFinal] inputs count as attempts; partial ones give UI feedback only,
 * so one spoken utterance can never be counted twice.
 * [relativeLevel] is an optional loudness heuristic in [0, 1] computed by the platform layer
 * (null = unknown). It is relative to the device/session, never an absolute dB value.
 */
public data class SpeechInput(
    val candidates: List<SpeechCandidate>,
    val isFinal: Boolean,
    val relativeLevel: Double? = null,
) : ChallengeInput

/** The listening window ended without any speech. Counts as a failed attempt. */
public object NoSpeechInput : ChallengeInput

public data class ChallengeProgress(val completed: Int, val required: Int) {
    init {
        require(required >= 1) { "required must be >= 1" }
        require(completed in 0..required) { "completed must be in 0..required" }
    }

    public val isDone: Boolean get() = completed >= required
    public val fraction: Double get() = completed.toDouble() / required
}

public enum class RejectReason { EMPTY_SPEECH, NO_MATCH, TOO_QUIET, LOW_CONFIDENCE }

public sealed interface ChallengeResult {
    /** Input of a kind this challenge does not handle, or challenge not running / already complete. */
    public data object Ignored : ChallengeResult

    /** Partial result: feedback only, nothing counted. */
    public data class Preview(val match: MatchResult) : ChallengeResult

    /** A counted failed attempt. [match] is null when nothing was heard. */
    public data class Rejected(val reason: RejectReason, val match: MatchResult?) : ChallengeResult

    public data class Progressed(val progress: ChallengeProgress, val match: MatchResult) : ChallengeResult

    public data class Completed(val match: MatchResult) : ChallengeResult
}

/**
 * Something the user must do to stop an alarm. Implementations are pure state holders:
 * no threads, no Android, no clocks.
 */
public interface Challenge {
    public val progress: ChallengeProgress
    public val isComplete: Boolean get() = progress.isDone

    /** Counted failed attempts since [start]. Drives Angry Mode. */
    public val failedAttempts: Int

    /** Begin (or restart) the challenge from zero progress. */
    public fun start()

    public fun processInput(input: ChallengeInput): ChallengeResult

    /** Back to the not-started state with zero progress. */
    public fun reset()
}
