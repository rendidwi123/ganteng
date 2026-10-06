package com.bangunwoi.core.domain

import java.time.ZonedDateTime

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
        require(id >= 0) { "id must not be negative" }
        require(label.length <= MAX_LABEL_LENGTH) { "label must be at most $MAX_LABEL_LENGTH characters" }
        require(updatedAtMillis >= createdAtMillis) { "updatedAt must not precede createdAt" }
    }

    /** Next ring after [after], or null while the alarm is disabled. */
    public fun nextTriggerAfter(after: ZonedDateTime): ZonedDateTime? =
        if (enabled) schedule.nextTriggerAfter(after) else null

    public fun withEnabled(enabled: Boolean, nowMillis: Long): Alarm =
        copy(enabled = enabled, updatedAtMillis = maxOf(nowMillis, createdAtMillis))

    public companion object {
        public const val MAX_LABEL_LENGTH: Int = 60
    }
}
