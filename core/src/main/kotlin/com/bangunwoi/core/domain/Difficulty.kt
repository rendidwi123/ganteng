package com.bangunwoi.core.domain

/**
 * Challenge difficulty presets.
 *
 * [defaultRequiredSuccesses] is only a default; [ChallengeSettings.requiredSuccesses] can override it.
 * [usesLevelGate] says whether the relative-loudness heuristic applies at this difficulty.
 */
public enum class Difficulty(
    public val defaultRequiredSuccesses: Int,
    public val usesLevelGate: Boolean,
) {
    EASY(defaultRequiredSuccesses = 1, usesLevelGate = false),
    NORMAL(defaultRequiredSuccesses = 1, usesLevelGate = true),
    HARD(defaultRequiredSuccesses = 3, usesLevelGate = false),
}
