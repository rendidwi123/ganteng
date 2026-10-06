package com.bangunwoi.core.domain

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainTest {
    private val schedule = AlarmSchedule.of(6, 30)

    @Test fun `difficulty defaults`() {
        assertEquals(1, Difficulty.EASY.defaultRequiredSuccesses)
        assertEquals(1, Difficulty.NORMAL.defaultRequiredSuccesses)
        assertEquals(3, Difficulty.HARD.defaultRequiredSuccesses)
        assertTrue(Difficulty.NORMAL.usesLevelGate)
        assertFalse(Difficulty.EASY.usesLevelGate)
        assertFalse(Difficulty.HARD.usesLevelGate)
    }

    @Test fun `difficulty from stored name`() {
        for (d in Difficulty.entries) assertEquals(d, Difficulty.fromNameOrNull(d.name))
        assertNull(Difficulty.fromNameOrNull("EXTREME"))
        assertNull(Difficulty.fromNameOrNull("easy")) // names are case-sensitive and stable
        assertNull(Difficulty.fromNameOrNull(""))
        assertNull(Difficulty.fromNameOrNull(null))
    }

    @Test fun `sound from stored name`() {
        for (b in BuiltInSound.entries) assertEquals(b, BuiltInSound.fromNameOrNull(b.name))
        assertNull(BuiltInSound.fromNameOrNull("CUSTOM"))
        assertNull(BuiltInSound.fromNameOrNull(null))
    }

    @Test fun `required successes override and bounds`() {
        assertEquals(3, ChallengeSettings(Difficulty.HARD).effectiveRequiredSuccesses)
        assertEquals(5, ChallengeSettings(Difficulty.HARD, requiredSuccesses = 5).effectiveRequiredSuccesses)
        assertEquals(2, ChallengeSettings(Difficulty.EASY, requiredSuccesses = 2).effectiveRequiredSuccesses)
        assertEquals(1, ChallengeSettings(requiredSuccesses = 1).effectiveRequiredSuccesses)
        assertEquals(10, ChallengeSettings(requiredSuccesses = 10).effectiveRequiredSuccesses)
        assertFailsWith<IllegalArgumentException> { ChallengeSettings(requiredSuccesses = 0) }
        assertFailsWith<IllegalArgumentException> { ChallengeSettings(requiredSuccesses = -1) }
        assertFailsWith<IllegalArgumentException> { ChallengeSettings(requiredSuccesses = 11) }
    }

    @Test fun `challenge gate bounds`() {
        for (v in listOf(-0.01, 1.01)) {
            assertFailsWith<IllegalArgumentException> { ChallengeSettings(minRelativeLevel = v) }
            assertFailsWith<IllegalArgumentException> { ChallengeSettings(minConfidence = v) }
        }
        ChallengeSettings(minRelativeLevel = 0.0, minConfidence = 1.0)
    }

    @Test fun `wake phrase validation`() {
        assertEquals("GW UDAH BANGUN", WakePhraseSettings().phrase)
        for (bad in listOf("", " ", "\t\n", "?!", "...", "--  --")) {
            assertFailsWith<IllegalArgumentException>("'$bad'") { WakePhraseSettings(phrase = bad) }
        }
        assertFailsWith<IllegalArgumentException> { WakePhraseSettings(phrase = "ab") }          // too short to be safe
        assertFailsWith<IllegalArgumentException> { WakePhraseSettings(phrase = "a".repeat(101)) }
        for (t in listOf(0.0, -0.5, 1.01)) assertFailsWith<IllegalArgumentException>("t=$t") { WakePhraseSettings(threshold = t) }
        WakePhraseSettings(phrase = "a".repeat(100), threshold = 1.0)
    }

    @Test fun `sound settings validation`() {
        assertFailsWith<IllegalArgumentException> { AlarmSoundSettings(baseVolume = -0.1) }
        assertFailsWith<IllegalArgumentException> { AlarmSoundSettings(baseVolume = 1.1) }
        AlarmSoundSettings(baseVolume = 0.0); AlarmSoundSettings(baseVolume = 1.0)
    }

    @Test fun `alarm defaults represent the full configuration`() {
        val a = Alarm(schedule = AlarmSchedule.of(6, 30, RepeatDays.WEEKDAYS), createdAtMillis = 1000)
        assertEquals(0L, a.id)
        assertTrue(a.enabled)
        assertEquals(Difficulty.NORMAL, a.challenge.difficulty)
        assertTrue(a.challenge.angryMode)
        assertEquals("GW UDAH BANGUN", a.wakePhrase.phrase)
        assertEquals(BuiltInSound.DEFAULT, a.sound.sound)
        assertEquals(1000, a.updatedAtMillis)
        assertEquals(LocalTime.of(6, 30), a.schedule.time)
    }

    @Test fun `alarm validation`() {
        assertFailsWith<IllegalArgumentException> { Alarm(id = -1, schedule = schedule, createdAtMillis = 0) }
        assertFailsWith<IllegalArgumentException> { Alarm(schedule = schedule, label = "x".repeat(61), createdAtMillis = 0) }
        assertFailsWith<IllegalArgumentException> { Alarm(schedule = schedule, createdAtMillis = 10, updatedAtMillis = 9) }
        Alarm(schedule = schedule, label = "x".repeat(60), createdAtMillis = 10, updatedAtMillis = 10)
    }

    @Test fun `enable toggle updates timestamp but never goes before creation`() {
        val a = Alarm(schedule = schedule, createdAtMillis = 1000)
        val off = a.withEnabled(false, nowMillis = 5000)
        assertFalse(off.enabled)
        assertEquals(5000, off.updatedAtMillis)
        assertEquals(1000, off.createdAtMillis)
        assertEquals(1000, a.withEnabled(false, nowMillis = 1).updatedAtMillis) // clock went backwards
    }

    @Test fun `alarm is a value type`() {
        val a = Alarm(schedule = schedule, createdAtMillis = 1)
        assertEquals(a, a.copy())
        assertTrue(a != a.copy(id = 2))
    }
}
