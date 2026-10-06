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

class DomainTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")
    private fun at(date: String, time: String, zone: ZoneId = jakarta) =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    @Test fun `difficulty defaults`() {
        assertEquals(1, Difficulty.EASY.defaultRequiredSuccesses)
        assertEquals(1, Difficulty.NORMAL.defaultRequiredSuccesses)
        assertEquals(3, Difficulty.HARD.defaultRequiredSuccesses)
        assertTrue(Difficulty.NORMAL.usesLevelGate)
        assertFalse(Difficulty.EASY.usesLevelGate)
        assertFalse(Difficulty.HARD.usesLevelGate)
    }

    @Test fun `required successes override`() {
        assertEquals(3, ChallengeSettings(Difficulty.HARD).effectiveRequiredSuccesses)
        assertEquals(5, ChallengeSettings(Difficulty.HARD, requiredSuccesses = 5).effectiveRequiredSuccesses)
        assertEquals(2, ChallengeSettings(Difficulty.EASY, requiredSuccesses = 2).effectiveRequiredSuccesses)
        assertFailsWith<IllegalArgumentException> { ChallengeSettings(requiredSuccesses = 0) }
        assertFailsWith<IllegalArgumentException> { ChallengeSettings(requiredSuccesses = 11) }
    }

    @Test fun `settings validation`() {
        assertFailsWith<IllegalArgumentException> { WakePhraseSettings(phrase = " ") }
        assertFailsWith<IllegalArgumentException> { WakePhraseSettings(threshold = 0.0) }
        assertFailsWith<IllegalArgumentException> { ChallengeSettings(minRelativeLevel = 1.5) }
        assertFailsWith<IllegalArgumentException> { AlarmSoundSettings(baseVolume = -0.1) }
        assertEquals("GW UDAH BANGUN", WakePhraseSettings().phrase)
    }

    @Test fun `alarm timestamps and enable toggle`() {
        val a = Alarm(schedule = AlarmSchedule(LocalTime.of(6, 30)), createdAtMillis = 1000)
        assertEquals(1000, a.updatedAtMillis)
        val off = a.withEnabled(false, nowMillis = 5000)
        assertFalse(off.enabled)
        assertEquals(5000, off.updatedAtMillis)
        assertEquals(1000, off.createdAtMillis)
        assertFailsWith<IllegalArgumentException> { a.copy(updatedAtMillis = 1) }
    }

    @Test fun `one shot later today`() {
        val s = AlarmSchedule(LocalTime.of(6, 30))
        assertEquals(at("2026-03-02", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:00")))
    }

    @Test fun `one shot already passed goes to tomorrow`() {
        val s = AlarmSchedule(LocalTime.of(6, 30))
        assertEquals(at("2026-03-03", "06:30"), s.nextTriggerAfter(at("2026-03-02", "07:00")))
    }

    @Test fun `exactly at trigger time moves to next occurrence`() {
        val s = AlarmSchedule(LocalTime.of(6, 30))
        assertEquals(at("2026-03-03", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:30")))
    }

    @Test fun `seconds after the minute still count as passed`() {
        val s = AlarmSchedule(LocalTime.of(6, 30))
        val now = at("2026-03-02", "06:30").plusSeconds(20)
        assertEquals(at("2026-03-03", "06:30"), s.nextTriggerAfter(now))
    }

    @Test fun `weekday repeat skips weekend`() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val s = AlarmSchedule(LocalTime.of(6, 30), weekdays)
        // 2026-03-06 is a Friday; after the Friday alarm the next is Monday 03-09
        assertEquals(DayOfWeek.FRIDAY, LocalDate.parse("2026-03-06").dayOfWeek)
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-06", "07:00")))
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-07", "12:00"))) // Saturday
    }

    @Test fun `single weekday passed today waits a full week`() {
        val s = AlarmSchedule(LocalTime.of(6, 30), setOf(DayOfWeek.MONDAY))
        assertEquals(at("2026-03-09", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:30")))
        assertEquals(at("2026-03-02", "06:30"), s.nextTriggerAfter(at("2026-03-02", "06:29")))
    }

    @Test fun `all days repeat is daily`() {
        val s = AlarmSchedule(LocalTime.of(0, 0), DayOfWeek.values().toSet())
        assertEquals(at("2026-03-03", "00:00"), s.nextTriggerAfter(at("2026-03-02", "23:59")))
    }

    @Test fun `repeating flag`() {
        assertFalse(AlarmSchedule(LocalTime.NOON).isRepeating)
        assertTrue(AlarmSchedule(LocalTime.NOON, setOf(DayOfWeek.SUNDAY)).isRepeating)
    }

    @Test fun `result is evaluated in the callers zone`() {
        val utc = ZoneId.of("UTC")
        val s = AlarmSchedule(LocalTime.of(6, 30))
        val next = s.nextTriggerAfter(at("2026-03-02", "07:00", utc))
        assertEquals(utc, next.zone)
        assertEquals(at("2026-03-03", "06:30", utc), next)
    }

    @Test fun `daylight saving gap moves forward not backwards`() {
        val ny = ZoneId.of("America/New_York")
        val s = AlarmSchedule(LocalTime.of(2, 30)) // does not exist on 2026-03-08
        val next = s.nextTriggerAfter(at("2026-03-08", "00:00", ny))
        assertTrue(next.isAfter(at("2026-03-08", "00:00", ny)))
        assertEquals(LocalDate.parse("2026-03-08"), next.toLocalDate())
    }
}
