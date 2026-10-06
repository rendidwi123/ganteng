package com.bangunwoi.core.challenge

import com.bangunwoi.core.domain.ChallengeSettings
import com.bangunwoi.core.domain.WakePhraseSettings
import com.bangunwoi.core.matching.MatchResult
import com.bangunwoi.core.matching.WakePhraseMatcher

/**
 * Say the wake phrase [ChallengeSettings.effectiveRequiredSuccesses] times.
 *
 * - EASY/HARD: only the phrase match matters.
 * - NORMAL ([com.bangunwoi.core.domain.Difficulty.usesLevelGate]): additionally the optional relative-level and
 *   recognizer-confidence gates apply, but only when the platform actually supplied those values; an unknown
 *   value never blocks the user (they could otherwise be stuck on devices that report neither).
 * - Failed attempts do not reset earlier successes (HARD keeps its 1/3, 2/3 progress).
 */
public class WakePhraseChallenge(
    wakePhrase: WakePhraseSettings,
    private val settings: ChallengeSettings,
) : Challenge {
    private val matcher = WakePhraseMatcher(wakePhrase)
    private val required = settings.effectiveRequiredSuccesses
    private var started = false
    private var completed = 0

    override var failedAttempts: Int = 0
        private set

    override val progress: ChallengeProgress get() = ChallengeProgress(completed, required)

    override fun start() {
        completed = 0
        failedAttempts = 0
        started = true
    }

    override fun reset() {
        completed = 0
        failedAttempts = 0
        started = false
    }

    override fun processInput(input: ChallengeInput): ChallengeResult {
        if (!started || isComplete) return ChallengeResult.Ignored
        return when (input) {
            is SpeechInput -> processSpeech(input)
            is NoSpeechInput -> fail(ChallengeResult.Rejected(RejectReason.EMPTY_SPEECH, null))
            else -> ChallengeResult.Ignored
        }
    }

    private fun processSpeech(input: SpeechInput): ChallengeResult {
        val best = input.candidates
            .map { it to matcher.match(it.text) }
            // an accepted alternative always beats a rejected one, whatever the scores
            .maxWithOrNull(compareBy({ it.second.accepted }, { it.second.score }))
        if (!input.isFinal) {
            return if (best == null) ChallengeResult.Ignored else ChallengeResult.Preview(best.second)
        }
        if (best == null || best.second.normalizedTranscript.isEmpty()) {
            return fail(ChallengeResult.Rejected(RejectReason.EMPTY_SPEECH, best?.second))
        }
        val (candidate, match) = best
        if (!match.accepted) return fail(ChallengeResult.Rejected(RejectReason.NO_MATCH, match))
        gateFailure(candidate, input, match)?.let { return fail(it) }

        completed++
        return if (isComplete) ChallengeResult.Completed(match)
        else ChallengeResult.Progressed(progress, match)
    }

    private fun gateFailure(candidate: SpeechCandidate, input: SpeechInput, match: MatchResult): ChallengeResult.Rejected? {
        if (!settings.difficulty.usesLevelGate) return null
        val level = input.relativeLevel
        if (level != null && settings.minRelativeLevel > 0.0 && level < settings.minRelativeLevel) {
            return ChallengeResult.Rejected(RejectReason.TOO_QUIET, match)
        }
        val confidence = candidate.confidence
        if (confidence != null && settings.minConfidence > 0.0 && confidence < settings.minConfidence) {
            return ChallengeResult.Rejected(RejectReason.LOW_CONFIDENCE, match)
        }
        return null
    }

    private fun fail(result: ChallengeResult.Rejected): ChallengeResult {
        failedAttempts++
        return result
    }
}
