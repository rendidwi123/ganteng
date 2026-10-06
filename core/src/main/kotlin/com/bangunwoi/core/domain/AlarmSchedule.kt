package com.bangunwoi.core.domain

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When an alarm rings: a wall-clock [time] (minute precision) and the weekdays it repeats on.
 * Empty [repeatDays] means one-shot: the next occurrence of [time].
 * Uses java.time only (available on Android API 26+, our planned minSdk).
 */
public data class AlarmSchedule(
    val time: LocalTime,
    val repeatDays: RepeatDays = RepeatDays.NONE,
) {
    init {
        require(time.second == 0 && time.nano == 0) { "alarm time must have minute precision, was $time" }
    }

    public val hour: Int get() = time.hour
    public val minute: Int get() = time.minute
    public val isRepeating: Boolean get() = !repeatDays.isEmpty

    /**
     * First trigger strictly after [after], in [after]'s zone. Pure function of its arguments.
     *
     * Wall-clock semantics: the alarm rings at [time] local time on each matching date.
     * - DST gap (time does not exist): java.time shifts it forward by the gap length.
     * - DST overlap (time occurs twice): the first occurrence is used; if [after] is already past it
     *   on that date, the next matching date is returned (the alarm is not rung a second time).
     */
    public fun nextTriggerAfter(after: ZonedDateTime): ZonedDateTime {
        // A matching date always exists within 7 days after today, so offsets 0..7 suffice.
        for (offset in 0L..7L) {
            val date = after.toLocalDate().plusDays(offset)
            if (isRepeating && date.dayOfWeek !in repeatDays) continue
            val candidate = ZonedDateTime.of(date, time, after.zone)
            if (candidate.isAfter(after)) return candidate
        }
        error("unreachable: a candidate always exists within 8 days")
    }

    public companion object {
        /** Domain-level construction from raw hour/minute (e.g. from a UI picker or storage). */
        public fun of(hour: Int, minute: Int, repeatDays: RepeatDays = RepeatDays.NONE): AlarmSchedule {
            require(hour in 0..23) { "hour must be in 0..23, was $hour" }
            require(minute in 0..59) { "minute must be in 0..59, was $minute" }
            return AlarmSchedule(LocalTime.of(hour, minute), repeatDays)
        }
    }
}
