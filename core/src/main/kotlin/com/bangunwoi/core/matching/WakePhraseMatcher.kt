package com.bangunwoi.core.matching

import com.bangunwoi.core.domain.WakePhraseSettings

/**
 * @property score 0.0 (nothing in common) .. 1.0 (all target words present in order); useful for UI feedback.
 * @property errors hard errors on the best alignment: a missing/wrong content word or a word interleaved
 *   inside the phrase. Typos and a dropped optional pronoun are not hard errors.
 * @property allowedErrors how many [errors] are tolerated.
 * @property contradicted the transcript contains a blocked word (negation or "still sleeping") that the target does not.
 * @property accepted all of: `score >= threshold`, `errors <= allowedErrors`, not [contradicted].
 */
public data class MatchResult(
    val score: Double,
    val threshold: Double,
    val normalizedTranscript: String,
    val normalizedTarget: String,
    val errors: Int = 0,
    val allowedErrors: Int = 0,
    val contradicted: Boolean = false,
) {
    public val accepted: Boolean get() = score >= threshold && errors <= allowedErrors && !contradicted

    public companion object {
        public fun empty(threshold: Double, target: String): MatchResult =
            MatchResult(0.0, threshold, "", target)
    }
}

/**
 * Deterministic fuzzy matcher between a recognized transcript and the wake phrase.
 *
 * Both sides are normalized with [PhraseNormalizer], then aligned at word level:
 * - words before/after the phrase inside the transcript are free ("ya gue udah bangun nih" matches);
 * - a missing target word costs its weight; [optionalWords] (default: the pronoun "gue") weigh
 *   [OPTIONAL_WEIGHT], all others 1, so "udah bangun" passes but "bangun" or "gw udah" do not;
 * - a replaced word costs 1 if it is a different word, or `weight * (1 - similarity)` if it is a typo of it
 *   (similarity >= [MIN_WORD_SIMILARITY]; words of <= [SHORT_WORD_LENGTH] letters must match exactly);
 * - a word interleaved between matched words costs 1.
 * score = 1 - totalCost / totalWeight.
 *
 * Acceptance additionally needs hard `errors <= allowedErrors` (default `words / 6`, i.e. 0 up to 5 words,
 * so a single wrong word can never trigger a short phrase) and no unexpected blocked word ("belum", "gak", "masih", "tidur", ...).
 *
 * **False positives vs false negatives.** The error budget and negation guard bias the matcher toward
 * false negatives: a user who is genuinely awake but mis-recognized simply speaks again (and the emergency stop
 * exists for broken recognizers). A false positive would let someone sleep through the alarm, which is the
 * failure this app exists to prevent. Loosen via [threshold] / [allowedErrors] per alarm if needed.
 *
 * Limits: text matching, not phonetic; only as good as the recognizer output. When several alignments cost
 * the same, the one with fewer hard errors wins; an alignment with slightly lower cost but more errors is not
 * searched for separately.
 */
public class WakePhraseMatcher(
    phrase: String,
    public val threshold: Double = WakePhraseSettings.DEFAULT_THRESHOLD,
    allowedErrors: Int? = null,
    private val normalizer: PhraseNormalizer = PhraseNormalizer(),
    private val optionalWords: Set<String> = DEFAULT_OPTIONAL_WORDS,
    private val blockedWords: Set<String> = DEFAULT_BLOCKED_WORDS,
) {
    private val targetTokens: List<String> = normalizer.tokens(phrase)
    private val weights: List<Double> = targetTokens.map { if (it in optionalWords) OPTIONAL_WEIGHT else 1.0 }
    private val totalWeight: Double = weights.sum()
    public val normalizedTarget: String = targetTokens.joinToString(" ")
    public val allowedErrors: Int = allowedErrors ?: defaultAllowedErrors(targetTokens.size)

    init {
        require(threshold > 0.0 && threshold <= 1.0) { "threshold must be in (0, 1]" }
        require(targetTokens.isNotEmpty()) { "phrase has no usable words" }
        require(this.allowedErrors >= 0) { "allowedErrors must not be negative" }
    }

    public constructor(settings: WakePhraseSettings, normalizer: PhraseNormalizer = PhraseNormalizer()) :
        this(settings.phrase, settings.threshold, settings.allowedErrors, normalizer)

    public fun match(transcript: String): MatchResult {
        val tokens = normalizer.tokens(transcript)
        if (tokens.isEmpty()) return MatchResult.empty(threshold, normalizedTarget)
        val best = align(tokens)
        return MatchResult(
            score = (1.0 - best.cost / totalWeight).coerceIn(0.0, 1.0),
            threshold = threshold,
            normalizedTranscript = tokens.joinToString(" "),
            normalizedTarget = normalizedTarget,
            errors = best.errors,
            allowedErrors = allowedErrors,
            contradicted = tokens.any { it in blockedWords && it !in targetTokens },
        )
    }

    /** Best result across recognizer alternatives: an accepted one beats a rejected one, then higher score. */
    public fun matchBest(candidates: List<String>): MatchResult =
        candidates.map(::match).maxWithOrNull(compareBy<MatchResult>({ it.accepted }, { it.score }))
            ?: MatchResult.empty(threshold, normalizedTarget)

    private class Cell(val cost: Double, val errors: Int) {
        fun plus(cost: Double, error: Boolean) = Cell(this.cost + cost, errors + if (error) 1 else 0)
    }

    private fun better(a: Cell, b: Cell): Cell = when {
        a.cost < b.cost - EPS -> a
        b.cost < a.cost - EPS -> b
        else -> if (a.errors <= b.errors) a else b
    }

    /** Semi-global alignment: matching may start/end anywhere in [input]; cheapest (cost, errors) wins. */
    private fun align(input: List<String>): Cell {
        val n = targetTokens.size
        val m = input.size
        var prev: List<Cell> = List(m + 1) { Cell(0.0, 0) } // row 0: nothing of the target consumed yet
        for (i in 1..n) {
            val w = weights[i - 1]
            val missingIsError = w >= 1.0
            val cur = ArrayList<Cell>(m + 1)
            cur.add(prev[0].plus(w, missingIsError))
            for (j in 1..m) {
                val (subCost, subError) = substitution(targetTokens[i - 1], w, input[j - 1])
                val viaSub = prev[j - 1].plus(subCost, subError)
                val viaMissing = prev[j].plus(w, missingIsError)
                val viaExtra = cur[j - 1].plus(1.0, true)
                cur.add(better(better(viaSub, viaMissing), viaExtra))
            }
            prev = cur
        }
        return prev.reduce(::better) // trailing transcript words are free
    }

    private fun substitution(target: String, weight: Double, heard: String): Pair<Double, Boolean> {
        if (target == heard) return 0.0 to false
        val longest = maxOf(target.length, heard.length)
        if (longest > SHORT_WORD_LENGTH) {
            val similarity = 1.0 - levenshtein(target, heard).toDouble() / longest
            if (similarity >= MIN_WORD_SIMILARITY) return weight * (1.0 - similarity) to false
        }
        return 1.0 to true
    }

    public companion object {
        public const val OPTIONAL_WEIGHT: Double = 0.5
        public const val SHORT_WORD_LENGTH: Int = 3
        public const val MIN_WORD_SIMILARITY: Double = 0.7
        private const val EPS = 1e-9

        public val DEFAULT_OPTIONAL_WORDS: Set<String> = setOf("gue")

        /**
         * Words that contradict "I am awake": negations plus "still"/"sleep"/"sleepy". A transcript containing
         * one (that is not part of the target phrase) is rejected even if the phrase is also present.
         */
        public val DEFAULT_BLOCKED_WORDS: Set<String> = setOf(
            "belum", "blm", "blom", "ga", "gak", "gk", "nggak", "ngga", "enggak", "engga",
            "tidak", "tak", "bukan", "jangan", "masih", "tidur", "ngantuk",
        )

        /** One hard error is tolerated per 6 words: 0 for 1..5 words, 1 for 6..11, ... */
        public fun defaultAllowedErrors(wordCount: Int): Int = wordCount / 6

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
