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
    HARD(defaultRequiredSuccesses = 3, usesLevelGate = false);

    public companion object {
        /** For reading stored data: null for an unknown name instead of an exception. */
        public fun fromNameOrNull(name: String?): Difficulty? = entries.firstOrNull { it.name == name }
    }
}
