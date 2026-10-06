package com.bangunwoi.core.domain

/**
 * Persistable alarm configuration. No Android types. Timestamps are epoch milliseconds
 * supplied by the caller (inject a clock; the domain never reads the system time).
 * [id] is 0 for a not-yet-persisted alarm.
 */
public data class Alarm(
    val id: Long = 0L,
    val schedule: AlarmSchedule,
    val enabled: Boolean = true,
    val label: String = "",
    val wakePhrase: WakePhraseSettings = WakePhraseSettings(),
    val challenge: ChallengeSettings = ChallengeSettings(),
    val sound: AlarmSoundSettings = AlarmSoundSettings(),
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
) {
    init {
        require(updatedAtMillis >= createdAtMillis) { "updatedAt must not precede createdAt" }
    }

    public fun withEnabled(enabled: Boolean, nowMillis: Long): Alarm =
        copy(enabled = enabled, updatedAtMillis = maxOf(nowMillis, createdAtMillis))
}
