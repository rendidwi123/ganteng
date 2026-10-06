package com.bangunwoi.core.domain

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepeatDaysTest {
    @Test fun `bit mapping is Monday=0 through Sunday=6`() {
        val expected = mapOf(
            DayOfWeek.MONDAY to 1, DayOfWeek.TUESDAY to 2, DayOfWeek.WEDNESDAY to 4, DayOfWeek.THURSDAY to 8,
            DayOfWeek.FRIDAY to 16, DayOfWeek.SATURDAY to 32, DayOfWeek.SUNDAY to 64,
        )
        for ((day, mask) in expected) assertEquals(mask, RepeatDays.of(day).mask, day.name)
    }

    @Test fun `constants`() {
        assertEquals(0, RepeatDays.NONE.mask)
        assertEquals(0b0011111, RepeatDays.WEEKDAYS.mask)
        assertEquals(0b1100000, RepeatDays.WEEKENDS.mask)
        assertEquals(0b1111111, RepeatDays.EVERY_DAY.mask)
        assertTrue(RepeatDays.NONE.isEmpty)
        assertFalse(RepeatDays.WEEKDAYS.isEmpty)
    }

    @Test fun `weekdays and weekends partition the week`() {
        assertEquals(RepeatDays.EVERY_DAY.mask, RepeatDays.WEEKDAYS.mask or RepeatDays.WEEKENDS.mask)
        assertEquals(0, RepeatDays.WEEKDAYS.mask and RepeatDays.WEEKENDS.mask)
        assertEquals(5, RepeatDays.WEEKDAYS.size)
        assertEquals(2, RepeatDays.WEEKENDS.size)
        assertEquals(7, RepeatDays.EVERY_DAY.size)
    }

    @Test fun `round trip for every possible mask`() {
        for (mask in 0..127) {
            val days = RepeatDays.fromMask(mask)
            assertEquals(mask, days.mask)
            assertEquals(days, RepeatDays.of(days.toList()), "mask=$mask")
        }
    }

    @Test fun `invalid masks are rejected`() {
        for (bad in listOf(-1, 128, 255, 256, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException>("mask=$bad") { RepeatDays.fromMask(bad) }
            assertNull(RepeatDays.fromMaskOrNull(bad), "mask=$bad")
        }
        assertEquals(RepeatDays.WEEKDAYS, RepeatDays.fromMaskOrNull(31))
    }

    @Test fun `contains plus and minus`() {
        val d = RepeatDays.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
        assertTrue(DayOfWeek.MONDAY in d)
        assertTrue(DayOfWeek.SUNDAY in d)
        assertFalse(DayOfWeek.TUESDAY in d)
        assertEquals(RepeatDays.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.SUNDAY), d + DayOfWeek.TUESDAY)
        assertEquals(d, d + DayOfWeek.MONDAY)                       // idempotent
        assertEquals(RepeatDays.of(DayOfWeek.SUNDAY), d - DayOfWeek.MONDAY)
        assertEquals(d, d - DayOfWeek.WEDNESDAY)                    // removing absent day is a no-op
    }

    @Test fun `of is order and duplicate independent`() {
        assertEquals(
            RepeatDays.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
            RepeatDays.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
        )
        assertEquals(RepeatDays.NONE, RepeatDays.of())
        assertEquals(RepeatDays.EVERY_DAY, RepeatDays.of(DayOfWeek.entries))
    }

    @Test fun `toList is deterministic Monday to Sunday`() {
        assertEquals(DayOfWeek.entries, RepeatDays.EVERY_DAY.toList())
        assertEquals(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), RepeatDays.WEEKENDS.toList())
        assertEquals(emptyList(), RepeatDays.NONE.toList())
    }

    @Test fun `toString is readable and stable`() {
        assertEquals("RepeatDays[]", RepeatDays.NONE.toString())
        assertEquals("RepeatDays[MON, TUE, WED, THU, FRI]", RepeatDays.WEEKDAYS.toString())
    }
}
