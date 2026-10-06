package com.bangunwoi.core.domain

/** The phrase the user must say. [threshold] is the minimum match score in (0, 1]. */
public data class WakePhraseSettings(
    val phrase: String = DEFAULT_PHRASE,
    val threshold: Double = DEFAULT_THRESHOLD,
) {
    init {
        require(threshold > 0.0 && threshold <= 1.0) { "threshold must be in (0, 1]" }
        require(phrase.isNotBlank()) { "phrase must not be blank" }
    }

    public companion object {
        public const val DEFAULT_PHRASE: String = "GW UDAH BANGUN"
        public const val DEFAULT_THRESHOLD: Double = 0.8
    }
}

/**
 * @property requiredSuccesses overrides [Difficulty.defaultRequiredSuccesses] when non-null.
 * @property minConfidence recognizer confidence gate (0 disables). Only applied when the recognizer
 *   reports a confidence AND difficulty uses the level gate. Recognizer confidence is often absent/unreliable.
 * @property minRelativeLevel relative loudness gate in [0, 1] (0 disables). A heuristic, NOT a dB measurement;
 *   only applied when the platform supplies a level and difficulty uses the level gate.
 */
public data class ChallengeSettings(
    val difficulty: Difficulty = Difficulty.NORMAL,
    val requiredSuccesses: Int? = null,
    val angryMode: Boolean = true,
    val minConfidence: Double = 0.0,
    val minRelativeLevel: Double = DEFAULT_MIN_RELATIVE_LEVEL,
) {
    init {
        require(requiredSuccesses == null || requiredSuccesses in 1..MAX_REQUIRED_SUCCESSES) {
            "requiredSuccesses must be in 1..$MAX_REQUIRED_SUCCESSES"
        }
        require(minConfidence in 0.0..1.0) { "minConfidence must be in [0, 1]" }
        require(minRelativeLevel in 0.0..1.0) { "minRelativeLevel must be in [0, 1]" }
    }

    public val effectiveRequiredSuccesses: Int
        get() = requiredSuccesses ?: difficulty.defaultRequiredSuccesses

    public companion object {
        public const val MAX_REQUIRED_SUCCESSES: Int = 10
        public const val DEFAULT_MIN_RELATIVE_LEVEL: Double = 0.4
    }
}

/** Built-in sounds; the Android layer maps each id to a raw resource. Custom sounds are a future feature. */
public enum class BuiltInSound { DEFAULT, AGGRESSIVE, FUNNY }

public data class AlarmSoundSettings(
    val sound: BuiltInSound = BuiltInSound.DEFAULT,
    /** Base volume in [0, 1]; angry mode may ramp above this up to the platform maximum. */
    val baseVolume: Double = 0.8,
    val vibrate: Boolean = true,
) {
    init {
        require(baseVolume in 0.0..1.0) { "baseVolume must be in [0, 1]" }
    }
}
