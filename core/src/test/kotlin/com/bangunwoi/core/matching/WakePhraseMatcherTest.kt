package com.bangunwoi.core.matching

import com.bangunwoi.core.domain.WakePhraseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WakePhraseMatcherTest {
    private val matcher = WakePhraseMatcher(WakePhraseTestData.TARGET)

    @Test fun `accepts every valid variant`() {
        val failures = WakePhraseTestData.VALID.filterNot { matcher.match(it).accepted }
        assertTrue(failures.isEmpty(), "should accept: " + failures.map { "'$it' -> ${matcher.match(it).score}" })
    }

    @Test fun `rejects every invalid phrase`() {
        val failures = WakePhraseTestData.INVALID.filter { matcher.match(it).accepted }
        assertTrue(failures.isEmpty(), "should reject: " + failures.map { "'$it' -> ${matcher.match(it).score}" })
    }

    @Test fun `exact phrase scores 1`() = assertEquals(1.0, matcher.match("gw udah bangun").score)

    @Test fun `empty speech gives zero score and empty transcript`() {
        val r = matcher.match("")
        assertEquals(0.0, r.score)
        assertEquals("", r.normalizedTranscript)
        assertFalse(r.accepted)
    }

    @Test fun `result carries normalized forms and threshold`() {
        val r = matcher.match("Gue UDAH bangun!")
        assertEquals("gue sudah bangun", r.normalizedTranscript)
        assertEquals("gue sudah bangun", r.normalizedTarget)
        assertEquals(0.8, r.threshold)
    }

    @Test fun `negation is not accepted despite sharing two of three words`() {
        val r = matcher.match("gw belum bangun")
        assertFalse(r.accepted)
        assertTrue(r.score > 0.0 && r.score < 0.8)
    }

    @Test fun `threshold is configurable`() {
        val lenient = WakePhraseMatcher(WakePhraseTestData.TARGET, threshold = 0.6)
        assertTrue(lenient.match("gw udah").accepted)      // 2/3 words
        assertFalse(matcher.match("gw udah").accepted)
        val strict = WakePhraseMatcher(WakePhraseTestData.TARGET, threshold = 1.0)
        assertTrue(strict.match("gue sudah bangun").accepted)
        assertFalse(strict.match("gw udah bagun").accepted) // typo scores < 1
    }

    @Test fun `typo tolerance is partial credit`() {
        val r = matcher.match("gw udah bagun")
        assertTrue(r.score > 0.9 && r.score < 1.0, "score=${r.score}")
    }

    @Test fun `short words must match exactly`() = assertFalse(matcher.match("lu udah bangun").accepted)

    @Test fun `extra word inside the phrase is penalised`() = assertFalse(matcher.match("gw udah tidur bangun").accepted)

    @Test fun `surrounding words are free`() = assertTrue(matcher.match("halo halo gw udah bangun ya").accepted)

    @Test fun `matchBest picks the best alternative`() {
        val r = matcher.matchBest(listOf("gw belum bangun", "gue udah bangun", "selamat pagi"))
        assertTrue(r.accepted)
        assertEquals(1.0, r.score)
    }

    @Test fun `matchBest on empty list is empty result`() {
        val r = matcher.matchBest(emptyList())
        assertEquals(0.0, r.score)
        assertFalse(r.accepted)
    }

    @Test fun `custom phrase`() {
        val m = WakePhraseMatcher("saya siap kerja hari ini")
        assertTrue(m.match("Saya siap kerja hari ini!").accepted)
        assertTrue(m.match("saya siap kerja hari").accepted)      // 4/5 = 0.8
        // Known property: in a 5-word phrase one wrong word scores 0.8 and passes the default threshold.
        assertTrue(m.match("saya siap tidur hari ini").accepted)
        assertFalse(WakePhraseMatcher("saya siap kerja hari ini", threshold = 0.9).match("saya siap tidur hari ini").accepted)
        assertFalse(m.match("saya siap tidur hari").accepted) // two words off
        assertFalse(m.match("gw udah bangun").accepted)
    }

    @Test fun `single word phrase`() {
        val m = WakePhraseMatcher("bangun", threshold = 0.9)
        assertTrue(m.match("bangun").accepted)
        assertFalse(m.match("tidur").accepted)
    }

    @Test fun `unusable phrase is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { WakePhraseMatcher("?!") }
        assertFailsWith<IllegalArgumentException> { WakePhraseMatcher("abc", threshold = 0.0) }
        assertFailsWith<IllegalArgumentException> { WakePhraseMatcher("abc", threshold = 1.5) }
        assertFalse(WakePhraseMatcher.isUsablePhrase("..."))
        assertTrue(WakePhraseMatcher.isUsablePhrase("gw bangun"))
    }

    @Test fun `built from settings`() {
        val m = WakePhraseMatcher(WakePhraseSettings("gw bangun", 0.5))
        assertEquals(0.5, m.threshold)
        assertTrue(m.match("gue bangun").accepted)
    }

    @Test fun `levenshtein basics`() {
        assertEquals(0, WakePhraseMatcher.levenshtein("abc", "abc"))
        assertEquals(1, WakePhraseMatcher.levenshtein("bagun", "bangun"))
        assertEquals(3, WakePhraseMatcher.levenshtein("", "abc"))
    }
}
