package com.bangunwoi.core.matching

import com.bangunwoi.core.domain.WakePhraseSettings

/**
 * @property score 0.0 (nothing in common) .. 1.0 (all target words present in order).
 * @property accepted `score >= threshold`.
 */
public data class MatchResult(
    val score: Double,
    val threshold: Double,
    val normalizedTranscript: String,
    val normalizedTarget: String,
) {
    public val accepted: Boolean get() = score >= threshold

    public companion object {
        public fun empty(threshold: Double, target: String): MatchResult =
            MatchResult(0.0, threshold, "", target)
    }
}

/**
 * Fuzzy matcher between a recognized transcript and the wake phrase.
 *
 * Both sides are normalized with [PhraseNormalizer], then aligned at the word level:
 * - words before/after the target inside the transcript are free ("ya gue udah bangun nih" still matches);
 * - a missing target word costs 1, an extra word *between* matched words costs 1;
 * - a replaced word costs `1 - similarity` when the words are alike (typos like "bagun"),
 *   otherwise 1. Words of 3 letters or fewer must match exactly after canonicalization, so
 *   "lu" never passes for "gue".
 * score = 1 - totalCost / targetWordCount, clamped to [0, 1].
 *
 * Limits: this is text matching, not phonetic matching; it is only as good as the recognizer's output.
 * For very short phrases a single wrong word always fails, for long ones the threshold allows slack.
 */
public class WakePhraseMatcher(
    phrase: String,
    public val threshold: Double = WakePhraseSettings.DEFAULT_THRESHOLD,
    private val normalizer: PhraseNormalizer = PhraseNormalizer(),
) {
    private val targetTokens: List<String> = normalizer.tokens(phrase)
    public val normalizedTarget: String = targetTokens.joinToString(" ")

    init {
        require(threshold > 0.0 && threshold <= 1.0) { "threshold must be in (0, 1]" }
        require(targetTokens.isNotEmpty()) { "phrase has no usable words" }
    }

    public constructor(settings: WakePhraseSettings, normalizer: PhraseNormalizer = PhraseNormalizer()) :
        this(settings.phrase, settings.threshold, normalizer)

    public fun match(transcript: String): MatchResult {
        val tokens = normalizer.tokens(transcript)
        if (tokens.isEmpty()) return MatchResult.empty(threshold, normalizedTarget)
        return MatchResult(
            score = score(tokens),
            threshold = threshold,
            normalizedTranscript = tokens.joinToString(" "),
            normalizedTarget = normalizedTarget,
        )
    }

    /** Best result across recognizer alternatives (N-best list). Empty list -> empty result. */
    public fun matchBest(candidates: List<String>): MatchResult =
        candidates.map(::match).maxByOrNull { it.score } ?: MatchResult.empty(threshold, normalizedTarget)

    private fun score(input: List<String>): Double {
        val n = targetTokens.size
        val m = input.size
        // cost[i][j]: cheapest alignment of the first i target words ending at input position j.
        // Row 0 is free for every j (matching may start anywhere in the transcript).
        var prev = DoubleArray(m + 1) { 0.0 }
        for (i in 1..n) {
            val cur = DoubleArray(m + 1)
            cur[0] = i.toDouble() // all target words so far missing
            for (j in 1..m) {
                val sub = prev[j - 1] + substitutionCost(targetTokens[i - 1], input[j - 1])
                val missing = prev[j] + 1.0          // target word not spoken
                val extra = cur[j - 1] + 1.0         // extra transcript word inside the match
                cur[j] = minOf(sub, missing, extra)
            }
            prev = cur
        }
        // Trailing transcript words are free: take the best end position.
        val best = prev.min()
        return (1.0 - best / n).coerceIn(0.0, 1.0)
    }

    private fun substitutionCost(target: String, heard: String): Double {
        if (target == heard) return 0.0
        val longest = maxOf(target.length, heard.length)
        if (longest <= SHORT_WORD_LENGTH) return 1.0
        val similarity = 1.0 - levenshtein(target, heard).toDouble() / longest
        return if (similarity >= MIN_WORD_SIMILARITY) 1.0 - similarity else 1.0
    }

    public companion object {
        private const val SHORT_WORD_LENGTH = 3
        private const val MIN_WORD_SIMILARITY = 0.7

        /** True if [phrase] contains at least one usable word; use to validate user-entered phrases. */
        public fun isUsablePhrase(phrase: String, normalizer: PhraseNormalizer = PhraseNormalizer()): Boolean =
            normalizer.tokens(phrase).isNotEmpty()

        internal fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                }
                prev = cur
            }
            return prev[b.length]
        }
    }
}
