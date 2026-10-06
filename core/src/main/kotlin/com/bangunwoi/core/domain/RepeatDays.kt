package com.bangunwoi.core.domain

import java.time.DayOfWeek

/**
 * A set of weekdays stored as a 7-bit mask: Monday = bit 0 ... Sunday = bit 6
 * (`DayOfWeek.value - 1`). The mask is the persistence format: stable, compact and deterministic.
 * An empty set means "one-shot alarm" (see [AlarmSchedule]).
 *
 * Instances can only be created through the factories, so every instance holds a valid mask.
 */
@JvmInline
public value class RepeatDays private constructor(public val mask: Int) {
    public val isEmpty: Boolean get() = mask == 0
    public val size: Int get() = Integer.bitCount(mask)

    public operator fun contains(day: DayOfWeek): Boolean = mask and bit(day) != 0
    public operator fun plus(day: DayOfWeek): RepeatDays = RepeatDays(mask or bit(day))
    public operator fun minus(day: DayOfWeek): RepeatDays = RepeatDays(mask and bit(day).inv())

    /** Days in Monday..Sunday order. */
    public fun toList(): List<DayOfWeek> = DayOfWeek.entries.filter { it in this }

    override fun toString(): String = toList().joinToString(prefix = "RepeatDays[", postfix = "]") { it.name.take(3) }

    public companion object {
        private const val ALL_BITS: Int = 0b111_1111

        public val NONE: RepeatDays = RepeatDays(0)
        public val WEEKDAYS: RepeatDays = RepeatDays(0b001_1111)
        public val WEEKENDS: RepeatDays = RepeatDays(0b110_0000)
        public val EVERY_DAY: RepeatDays = RepeatDays(ALL_BITS)

        public fun of(vararg days: DayOfWeek): RepeatDays = of(days.asIterable())

        public fun of(days: Iterable<DayOfWeek>): RepeatDays = RepeatDays(days.fold(0) { acc, d -> acc or bit(d) })

        /** @throws IllegalArgumentException if [mask] has bits outside 0..6 or is negative. */
        public fun fromMask(mask: Int): RepeatDays {
            require(mask in 0..ALL_BITS) { "repeat-day mask must be in 0..$ALL_BITS, was $mask" }
            return RepeatDays(mask)
        }

        /** Lenient variant for reading stored data: null instead of an exception. */
        public fun fromMaskOrNull(mask: Int): RepeatDays? = if (mask in 0..ALL_BITS) RepeatDays(mask) else null

        private fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}
