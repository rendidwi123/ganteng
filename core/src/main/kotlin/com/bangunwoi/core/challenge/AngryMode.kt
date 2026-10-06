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
)

/**
 * Deterministic failed-attempts -> escalation mapping. Message index = min(failedAttempts, last index).
 * Messages are plain strings so the Android layer can supply them from a string-array resource.
 * When [enabled] is false the state is always level 0.
 */
public class AngryModePolicy(
    public val enabled: Boolean = true,
    private val messages: List<String> = DEFAULT_MESSAGES,
) {
    init {
        require(messages.size >= 2) { "need at least a calm and a maximum message" }
        require(messages.none { it.isBlank() }) { "messages must not be blank" }
    }

    public val maxLevel: Int get() = messages.lastIndex

    public fun stateFor(failedAttempts: Int): AngryState {
        require(failedAttempts >= 0) { "failedAttempts must be >= 0" }
        val level = if (enabled) minOf(failedAttempts, maxLevel) else 0
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
