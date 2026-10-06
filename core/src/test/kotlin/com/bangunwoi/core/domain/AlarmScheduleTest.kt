package com.bangunwoi.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reference week used below (2026-03-02 is a Monday):
 * 02 Mon, 03 Tue, 04 Wed, 05 Thu, 06 Fri, 07 Sat, 08 Sun, 09 Mon.
 * All inputs are fixed instants; nothing reads the system clock.
 */
class AlarmScheduleTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")
    private fun at(date: String, time: String, zone: ZoneId = jakarta): ZonedDateTime =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    private fun oneShot(h: Int = 6, m: Int = 30) = AlarmSchedule.of(h, m)
    private fun repeat(days: RepeatDays, h: Int = 6, m: Int = 30) = AlarmSchedule.of(h, m, days)

    // ---- construction / validation
    @Test fun `of validates hour and minute`() {
        for (h in listOf(-1, 24, 99)) assertFailsWith<IllegalArgumentException>("hour=$h") { AlarmSchedule.of(h, 0) }
        for (m in listOf(-1, 60, 99)) assertFailsWith<IllegalArgumentException>("minute=$m") { AlarmSchedule.of(0, m) }
        AlarmSchedule.of(0, 0); AlarmSchedule.of(23, 59)
    }

    @Test fun `time must have minute precision`() {
        assertFailsWith<IllegalArgumentException> { AlarmSchedule(LocalTime.of(6, 30, 15)) }
        assertFailsWith<IllegalArgumentException> { AlarmSchedule(LocalTime.of(6, 30).withNano(1)) }
    }

    @Test fun `hour minute and repeating accessors`() {
        val s = repeat(RepeatDays.WEEKDAYS, 7, 5)
        assertEquals(7, s.hour); assertEquals(5, s.minute)
        assertTrue(s.isRepeating)
        assertFalse(oneShot().isRepeating)
    }

    // ---- one-shot
    @Test fun `one shot later today`() = assertEquals(at("2026-03-02", "06:30"), oneShot().nextTriggerAfter(at("2026-03-02", "06:00")))
    @Test fun `one shot passed goes to tomorrow`() = assertEquals(at("2026-03-03", "06:30"), oneShot().nextTriggerAfter(at("2026-03-02", "07:00")))
    @Test fun `one minute before`() = assertEquals(at("2026-03-02", "06:30"), oneShot().nextTriggerAfter(at("2026-03-02", "06:29")))
    @Test fun `exactly at trigger time is not returned again`() = assertEquals(at("2026-03-03", "06:30"), oneShot().nextTriggerAfter(at("2026-03-02", "06:30")))
    @Test fun `one minute after`() = assertEquals(at("2026-03-03", "06:30"), oneShot().nextTriggerAfter(at("2026-03-02", "06:31")))
    @Test fun `one nanosecond before`() =
        assertEquals(at("2026-03-02", "06:30"), oneShot().nextTriggerAfter(at("2026-03-02", "06:30").minusNanos(1)))
    @Test fun `seconds after the minute still count as passed`() =
        assertEquals(at("2026-03-03", "06:30"), oneShot().nextTriggerAfter(at("2026-03-02", "06:30").plusSeconds(20)))

    // ---- midnight / month / year boundaries
    @Test fun `crossing midnight for an early alarm`() =
        assertEquals(at("2026-03-03", "00:05"), oneShot(0, 5).nextTriggerAfter(at("2026-03-02", "23:59")))
    @Test fun `alarm at 23_59 queried at 23_58 and 23_59`() {
        assertEquals(at("2026-03-02", "23:59"), oneShot(23, 59).nextTriggerAfter(at("2026-03-02", "23:58")))
        assertEquals(at("2026-03-03", "23:59"), oneShot(23, 59).nextTriggerAfter(at("2026-03-02", "23:59")))
    }
    @Test fun `midnight alarm`() {
        assertEquals(at("2026-03-03", "00:00"), oneShot(0, 0).nextTriggerAfter(at("2026-03-02", "00:00")))
        assertEquals(at("2026-03-03", "00:00"), oneShot(0, 0).nextTriggerAfter(at("2026-03-02", "23:59")))
    }
    @Test fun `month and year rollover`() {
        assertEquals(at("2026-03-01", "06:30"), oneShot().nextTriggerAfter(at("2026-02-28", "07:00")))
        assertEquals(at("2027-01-01", "06:30"), oneShot().nextTriggerAfter(at("2026-12-31", "07:00")))
        assertEquals(at("2028-02-29", "06:30"), oneShot().nextTriggerAfter(at("2028-02-28", "07:00"))) // leap day
    }

    // ---- daily
    @Test fun `every day behaves like daily`() {
        val s = repeat(RepeatDays.EVERY_DAY)
        assertEquals(at("2026-03-02", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:29")))
        assertEquals(at("2026-03-03", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:30")))
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-08", "06:30"))) // Sunday -> Monday
    }

    // ---- weekdays / weekends / specific days
    @Test fun `weekday alarm skips the weekend`() {
        val s = repeat(RepeatDays.WEEKDAYS)
        assertEquals(DayOfWeek.FRIDAY, LocalDate.parse("2026-03-06").dayOfWeek)
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-06", "07:00"))) // Fri after ring
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-07", "12:00"))) // Saturday
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-08", "23:59"))) // Sunday late
        assertEquals(at("2026-03-06", "06:30"), s.nextTriggerAfter(at("2026-03-05", "06:31"))) // Thu -> Fri
        assertEquals(at("2026-03-06", "06:30"), s.nextTriggerAfter(at("2026-03-06", "06:29"))) // Fri one minute before
    }

    @Test fun `weekend alarm`() {
        val s = repeat(RepeatDays.WEEKENDS)
        assertEquals(at("2026-03-07", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:30"))) // Monday -> Saturday
        assertEquals(at("2026-03-08", "06:30"), s.nextTriggerAfter(at("2026-03-07", "06:30"))) // Sat -> Sun
        assertEquals(at("2026-03-14", "06:30"), s.nextTriggerAfter(at("2026-03-08", "06:30"))) // Sun -> next Sat
    }

    @Test fun `Sunday to Monday`() {
        val s = repeat(RepeatDays.of(DayOfWeek.MONDAY))
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-08", "20:00")))
        val sun = repeat(RepeatDays.of(DayOfWeek.SUNDAY))
        assertEquals(at("2026-03-15", "06:30"), sun.nextTriggerAfter(at("2026-03-08", "06:30"))) // full week
    }

    @Test fun `specific days pick the nearest following one`() {
        val s = repeat(RepeatDays.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals(at("2026-03-03", "06:30"), s.nextTriggerAfter(at("2026-03-02", "10:00"))) // Mon -> Tue
        assertEquals(at("2026-03-05", "06:30"), s.nextTriggerAfter(at("2026-03-03", "06:30"))) // Tue -> Thu
        assertEquals(at("2026-03-10", "06:30"), s.nextTriggerAfter(at("2026-03-05", "06:30"))) // Thu -> next Tue
    }

    @Test fun `single weekday passed today waits a full week`() {
        val s = repeat(RepeatDays.of(DayOfWeek.MONDAY))
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:30")))
        assertEquals(at("2026-03-02", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:29")))
    }

    @Test fun `result is always strictly after input and within 7 days plus a day`() {
        // exhaustive: every day-set x a spread of query instants across 3 weeks, hourly
        val masks = (0..127).filter { it % 3 == 0 || it == 127 || it == 1 || it == 64 }
        var q = at("2026-03-01", "00:00")
        val end = at("2026-03-22", "00:00")
        while (q.isBefore(end)) {
            for (mask in masks) {
                val s = repeat(RepeatDays.fromMask(mask), 6, 30)
                val n = s.nextTriggerAfter(q)
                assertTrue(n.isAfter(q), "mask=$mask q=$q n=$n")
                assertTrue(n.isBefore(q.plusDays(8)), "mask=$mask q=$q n=$n")
                assertEquals(LocalTime.of(6, 30), n.toLocalTime())
                if (mask != 0) assertTrue(n.dayOfWeek in RepeatDays.fromMask(mask), "mask=$mask n=$n")
            }
            q = q.plusMinutes(97) // odd step so minutes vary
        }
    }

    // ---- zones / DST
    @Test fun `result uses the callers zone`() {
        val utc = ZoneId.of("UTC")
        val next = oneShot().nextTriggerAfter(at("2026-03-02", "07:00", utc))
        assertEquals(utc, next.zone)
        assertEquals(at("2026-03-03", "06:30", utc), next)
    }

    @Test fun `DST gap moves forward not backwards`() {
        val ny = ZoneId.of("America/New_York")
        val next = oneShot(2, 30).nextTriggerAfter(at("2026-03-08", "00:00", ny)) // 02:30 does not exist
        assertEquals(LocalDate.parse("2026-03-08"), next.toLocalDate())
        assertTrue(next.isAfter(at("2026-03-08", "00:00", ny)))
    }

    @Test fun `DST overlap rings once`() {
        val ny = ZoneId.of("America/New_York") // 2026-11-01 01:00-02:00 occurs twice
        val first = oneShot(1, 30).nextTriggerAfter(at("2026-11-01", "00:00", ny))
        assertEquals(LocalDate.parse("2026-11-01"), first.toLocalDate())
        // asked again after the first 01:30: next ring is the following day, not the repeated 01:30
        val after = oneShot(1, 30).nextTriggerAfter(first)
        assertEquals(LocalDate.parse("2026-11-02"), after.toLocalDate())
    }

    // ---- Alarm.enabled
    @Test fun `disabled alarm has no next trigger`() {
        val alarm = Alarm(schedule = repeat(RepeatDays.EVERY_DAY), enabled = false, createdAtMillis = 0)
        assertEquals(null, alarm.nextTriggerAfter(at("2026-03-02", "00:00")))
        assertEquals(at("2026-03-02", "06:30"), alarm.withEnabled(true, 1).nextTriggerAfter(at("2026-03-02", "00:00")))
    }
}
