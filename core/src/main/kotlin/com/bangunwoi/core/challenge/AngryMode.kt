package com.bangunwoi.core.challenge

/**
 * @property level 0 = calm .. [maxLevel].
 * @property intensity 0.0..1.0; the audio layer decides how that maps to volume/vibration.
 */
public data class AngryState(
    val level: Int,
    val maxLevel: Int,
    val intensity: Double,
    val message: String,
) {
    public val isMax: Boolean get() = level == maxLevel
}

/**
 * Deterministic failed-attempts -> escalation mapping, no randomness and no clock.
 * `level = min(failedAttempts / failuresPerLevel, maxLevel)`; the message is `messages[level]`.
 *
 * What counts as a failed attempt is decided by the challenge (wrong phrase, quiet speech, a listening
 * window that ended in silence); successful attempts never lower the level while the alarm keeps ringing.
 * [AlarmSession] reports level 0 again once the alarm is no longer ringing.
 * Messages are plain strings so the Android layer can supply them from a string-array resource.
 * When [enabled] is false the state is always level 0.
 */
public class AngryModePolicy(
    public val enabled: Boolean = true,
    private val messages: List<String> = DEFAULT_MESSAGES,
    private val failuresPerLevel: Int = 1,
) {
    init {
        require(failuresPerLevel >= 1) { "failuresPerLevel must be >= 1" }
        require(messages.size >= 2) { "need at least a calm and a maximum message" }
        require(messages.none { it.isBlank() }) { "messages must not be blank" }
    }

    public val maxLevel: Int get() = messages.lastIndex

    public fun stateFor(failedAttempts: Int): AngryState {
        require(failedAttempts >= 0) { "failedAttempts must be >= 0" }
        val level = if (enabled) minOf(failedAttempts / failuresPerLevel, maxLevel) else 0
        return AngryState(level, maxLevel, level.toDouble() / maxLevel, messages[level])
    }

    public companion object {
        public val DEFAULT_MESSAGES: List<String> = listOf(
            "Waktunya bangun.",
            "Bangun.",
            "Serius masih tidur?",
            "GW BILANG BANGUN.",
            "TERIAK YANG JELAS.",
        )
    }
}
