package com.bangunwoi.core.domain

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When an alarm rings. An empty [repeatDays] means a one-shot alarm (next occurrence of [time]).
 * Uses java.time only (available on Android API 26+, our planned minSdk).
 */
public data class AlarmSchedule(
    val time: LocalTime,
    val repeatDays: Set<DayOfWeek> = emptySet(),
) {
    public val isRepeating: Boolean get() = repeatDays.isNotEmpty()

    /**
     * First trigger strictly after [after], in [after]'s zone, or the next day/matching weekday.
     * Seconds/nanos of [time] are always zero. Wall-clock semantics: across a DST gap java.time
     * moves the time forward; this is documented behaviour, not verified against AlarmManager.
     */
    public fun nextTriggerAfter(after: ZonedDateTime): ZonedDateTime {
        val zone: ZoneId = after.zone
        val t = time.withSecond(0).withNano(0)
        // At most 8 candidate dates are ever needed (today .. today+7).
        for (offset in 0L..7L) {
            val date = after.toLocalDate().plusDays(offset)
            if (isRepeating && date.dayOfWeek !in repeatDays) continue
            val candidate = ZonedDateTime.of(date, t, zone)
            if (candidate.isAfter(after)) return candidate
        }
        error("unreachable: a candidate always exists within 8 days")
    }
}
