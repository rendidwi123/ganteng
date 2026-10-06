package com.bangunwoi.core.matching

import com.bangunwoi.core.domain.WakePhraseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WakePhraseMatcherTest {
    private val matcher = WakePhraseMatcher(WakePhraseTestData.TARGET)

    private fun describe(text: String) = matcher.match(text).let { "'$text' -> score=${it.score} errors=${it.errors} contradicted=${it.contradicted}" }

    @Test fun `accepts every valid variant`() {
        for ((category, phrases) in WakePhraseTestData.VALID) {
            val failures = phrases.filterNot { matcher.match(it).accepted }
            assertTrue(failures.isEmpty(), "[$category] should accept: " + failures.map(::describe))
        }
    }

    @Test fun `rejects every invalid phrase`() {
        for ((category, phrases) in WakePhraseTestData.INVALID) {
            val failures = phrases.filter { matcher.match(it).accepted }
            assertTrue(failures.isEmpty(), "[$category] should reject: " + failures.map(::describe))
        }
    }

    @Test fun `dataset is substantial and has no overlap`() {
        assertTrue(WakePhraseTestData.allValid.size >= 40, "valid=${WakePhraseTestData.allValid.size}")
        assertTrue(WakePhraseTestData.allInvalid.size >= 60, "invalid=${WakePhraseTestData.allInvalid.size}")
        assertTrue(WakePhraseTestData.allValid.intersect(WakePhraseTestData.allInvalid.toSet()).isEmpty())
    }

    @Test fun `documented by-design acceptances`() {
        for (p in WakePhraseTestData.ACCEPTED_BY_DESIGN) assertTrue(matcher.match(p).accepted, p)
    }

    @Test fun `accept and reject decisions are deterministic`() {
        val all = WakePhraseTestData.allValid + WakePhraseTestData.allInvalid
        assertEquals(all.map { matcher.match(it) }, all.map { WakePhraseMatcher(WakePhraseTestData.TARGET).match(it) })
    }

    @Test fun `every word-level single substitution of the target is rejected`() {
        // false-positive sweep: replace each word with unrelated words
        val words = listOf("makan", "pergi", "besok", "kamar", "lagu", "tidurr", "mandi", "nanti")
        val target = listOf("gw", "udah", "bangun")
        for (i in target.indices) for (w in words) {
            val phrase = target.toMutableList().also { it[i] = w }.joinToString(" ")
            // replacing the optional pronoun leaves "udah bangun" which is accepted by design
            if (i == 0) continue
            assertFalse(matcher.match(phrase).accepted, phrase)
        }
    }

    @Test fun `every deletion of a content word is rejected and of the pronoun accepted`() {
        assertFalse(matcher.match("udah").accepted)
        assertFalse(matcher.match("gw bangun").accepted)
        assertTrue(matcher.match("udah bangun").accepted)
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
        val lenient = WakePhraseMatcher(WakePhraseTestData.TARGET, threshold = 0.6, allowedErrors = 1)
        assertTrue(lenient.match("gw udah").accepted)      // one content word missing, explicitly allowed
        assertFalse(matcher.match("gw udah").accepted)
        val strict = WakePhraseMatcher(WakePhraseTestData.TARGET, threshold = 1.0)
        assertTrue(strict.match("gue sudah bangun").accepted)
        assertFalse(strict.match("gw udah bagun").accepted) // typo scores < 1
    }

    @Test fun `typo tolerance is partial credit`() {
        val r = matcher.match("gw udah bagun")
        assertTrue(r.score > 0.9 && r.score < 1.0, "score=${r.score}")
    }

    @Test fun `short words must match exactly`() {
        val m = WakePhraseMatcher("ayo lari pagi ini")
        assertTrue(m.match("ayo lari pagi ini").accepted)
        assertFalse(m.match("ado lari pagi ini").accepted) // 'ayo' vs 'ado': 3 letters, no fuzzy credit
    }

    @Test fun `extra word inside the phrase is penalised`() {
        assertFalse(matcher.match("gw udah banget bangun").accepted)
        assertFalse(matcher.match("gw udah tidur bangun").accepted)
    }

    @Test fun `negation or sleepiness anywhere rejects even if the phrase is present`() {
        val r = matcher.match("gw udah bangun tapi masih ngantuk")
        assertTrue(r.contradicted)
        assertFalse(r.accepted)
        assertEquals(1.0, r.score) // phrase itself is fully present; only the guard rejects
        assertFalse(matcher.match("gw udah bangun belum").accepted)
    }

    @Test fun `blocked words that are part of the target do not block`() {
        val m = WakePhraseMatcher("gw masih bangun")
        assertTrue(m.match("gw masih bangun").accepted)
        assertFalse(m.match("gw masih bangun belum").accepted)
    }

    @Test fun `optional pronoun costs half a word`() {
        val r = matcher.match("udah bangun")
        assertEquals(0.8, r.score, 1e-9)
        assertEquals(0, r.errors)
        assertTrue(r.accepted)
        // a strict threshold turns the optional pronoun off in practice
        assertFalse(WakePhraseMatcher(WakePhraseTestData.TARGET, threshold = 0.9).match("udah bangun").accepted)
    }

    @Test fun `error budget grows with phrase length`() {
        assertEquals(0, WakePhraseMatcher.defaultAllowedErrors(1))
        assertEquals(0, WakePhraseMatcher.defaultAllowedErrors(5))
        assertEquals(1, WakePhraseMatcher.defaultAllowedErrors(6))
        assertEquals(1, WakePhraseMatcher.defaultAllowedErrors(11))
        assertEquals(2, WakePhraseMatcher.defaultAllowedErrors(12))
    }

    @Test fun `five word phrase rejects one wrong word`() {
        // regression: previously scored 0.8 and passed the default threshold
        val m = WakePhraseMatcher("ayo kita mulai hari ini")
        assertTrue(m.match("ayo kita mulai hari ini").accepted)
        val oneWrong = m.match("ayo kita mulai malam ini")
        assertEquals(0.8, oneWrong.score, 1e-9)
        assertEquals(1, oneWrong.errors)
        assertFalse(oneWrong.accepted)
    }

    @Test fun `six word phrase tolerates one wrong word but not two`() {
        val m = WakePhraseMatcher("ayo kita mulai hari baru ini")
        assertTrue(m.match("ayo kita mulai hari baru ini").accepted)
        assertTrue(m.match("ayo kita mulai hari lama ini").accepted)
        assertFalse(m.match("ayo kita mulai malam lama ini").accepted)
    }

    @Test fun `allowed errors can be set explicitly`() {
        val strict = WakePhraseMatcher("ayo kita mulai hari baru ini", allowedErrors = 0)
        assertFalse(strict.match("ayo kita mulai hari lama ini").accepted)
        assertFailsWith<IllegalArgumentException> { WakePhraseMatcher("ayo kita", allowedErrors = -1) }
    }

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
        assertTrue(m.match("siap kerja hari ini").accepted)       // pronoun optional
        assertFalse(m.match("saya siap kerja hari").accepted)     // content word missing
        assertFalse(m.match("saya siap libur hari ini").accepted) // content word wrong
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
        val m = WakePhraseMatcher(WakePhraseSettings("gw bangun", 0.5, allowedErrors = 1))
        assertEquals(0.5, m.threshold)
        assertEquals(1, m.allowedErrors)
        assertTrue(m.match("gue bangun").accepted)
        assertEquals(0, WakePhraseMatcher(WakePhraseSettings("gw udah bangun")).allowedErrors)
    }

    @Test fun `levenshtein basics`() {
        assertEquals(0, WakePhraseMatcher.levenshtein("abc", "abc"))
        assertEquals(1, WakePhraseMatcher.levenshtein("bagun", "bangun"))
        assertEquals(3, WakePhraseMatcher.levenshtein("", "abc"))
    }

    @Test fun `very long transcript is handled quickly and still matches`() {
        val filler = "blah ".repeat(3000)
        val started = System.nanoTime()
        assertTrue(matcher.match(filler + "gw udah bangun " + filler).accepted)
        assertFalse(matcher.match(filler).accepted)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(ms < 2000, "took $ms ms")
    }

    @Test fun `garbage input never throws`() {
        for (g in listOf("\u0000", "😀", "a".repeat(10_000), "   ", "\n\r\t", "\uD800", "gw".repeat(500))) {
            matcher.match(g)
            matcher.matchBest(listOf(g, ""))
        }
    }

    @Test fun `score is within 0 and 1 for any input`() {
        for (t in WakePhraseTestData.allValid + WakePhraseTestData.allInvalid) {
            val s = matcher.match(t).score
            assertTrue(s in 0.0..1.0, "$t -> $s")
        }
    }
}
