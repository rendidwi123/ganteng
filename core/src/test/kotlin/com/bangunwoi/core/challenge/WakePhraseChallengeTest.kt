package com.bangunwoi.core.challenge

import com.bangunwoi.core.domain.ChallengeSettings
import com.bangunwoi.core.domain.Difficulty
import com.bangunwoi.core.domain.WakePhraseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WakePhraseChallengeTest {
    private fun challenge(d: Difficulty, required: Int? = null, minLevel: Double = 0.4, minConf: Double = 0.0) =
        WakePhraseChallenge(
            WakePhraseSettings(),
            ChallengeSettings(d, required, minRelativeLevel = minLevel, minConfidence = minConf),
        )

    private fun say(text: String, final: Boolean = true, level: Double? = null, conf: Double? = null) =
        SpeechInput(listOf(SpeechCandidate(text, conf)), final, level)

    @Test fun `ignores input until started`() {
        val c = challenge(Difficulty.EASY)
        assertEquals(ChallengeResult.Ignored, c.processInput(say("gw udah bangun")))
        assertFalse(c.isComplete)
    }

    @Test fun `easy completes on one match`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        assertIs<ChallengeResult.Completed>(c.processInput(say("gue udah bangun")))
        assertTrue(c.isComplete)
        assertEquals(1.0, c.progress.fraction)
    }

    @Test fun `wrong phrase is a counted failure`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        val r = assertIs<ChallengeResult.Rejected>(c.processInput(say("selamat pagi")))
        assertEquals(RejectReason.NO_MATCH, r.reason)
        assertEquals(1, c.failedAttempts)
        assertFalse(c.isComplete)
    }

    @Test fun `empty final speech is a failure with EMPTY_SPEECH`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        assertEquals(RejectReason.EMPTY_SPEECH, assertIs<ChallengeResult.Rejected>(c.processInput(say(""))).reason)
        assertEquals(RejectReason.EMPTY_SPEECH, assertIs<ChallengeResult.Rejected>(c.processInput(SpeechInput(emptyList(), true))).reason)
        assertEquals(2, c.failedAttempts)
    }

    @Test fun `no speech input counts as a failure`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        assertIs<ChallengeResult.Rejected>(c.processInput(NoSpeechInput))
        assertEquals(1, c.failedAttempts)
    }

    @Test fun `partial results never count`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        val r = assertIs<ChallengeResult.Preview>(c.processInput(say("gw udah bangun", final = false)))
        assertTrue(r.match.accepted)
        assertFalse(c.isComplete)
        assertEquals(0, c.failedAttempts)
        assertIs<ChallengeResult.Preview>(c.processInput(say("selamat", final = false)))
        assertEquals(0, c.failedAttempts)
        assertEquals(ChallengeResult.Ignored, c.processInput(SpeechInput(emptyList(), false)))
    }

    @Test fun `hard needs three and keeps progress across failures`() {
        val c = challenge(Difficulty.HARD).also { it.start() }
        val p1 = assertIs<ChallengeResult.Progressed>(c.processInput(say("gw udah bangun")))
        assertEquals(ChallengeProgress(1, 3), p1.progress)
        assertIs<ChallengeResult.Rejected>(c.processInput(say("hmm")))
        assertEquals(ChallengeProgress(1, 3), c.progress)
        assertEquals(ChallengeProgress(2, 3), assertIs<ChallengeResult.Progressed>(c.processInput(say("gue sudah bangun"))).progress)
        assertIs<ChallengeResult.Completed>(c.processInput(say("GW UDAH BANGUN!")))
        assertTrue(c.isComplete)
    }

    @Test fun `required successes is configurable`() {
        val c = challenge(Difficulty.EASY, required = 2).also { it.start() }
        assertIs<ChallengeResult.Progressed>(c.processInput(say("gw udah bangun")))
        assertIs<ChallengeResult.Completed>(c.processInput(say("gw udah bangun")))
    }

    @Test fun `input after completion is ignored`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        c.processInput(say("gw udah bangun"))
        assertEquals(ChallengeResult.Ignored, c.processInput(say("gw udah bangun")))
        assertEquals(ChallengeResult.Ignored, c.processInput(NoSpeechInput))
    }

    @Test fun `normal rejects quiet speech when level known`() {
        val c = challenge(Difficulty.NORMAL).also { it.start() }
        assertEquals(RejectReason.TOO_QUIET, assertIs<ChallengeResult.Rejected>(c.processInput(say("gw udah bangun", level = 0.1))).reason)
        assertEquals(1, c.failedAttempts)
        assertIs<ChallengeResult.Completed>(c.processInput(say("gw udah bangun", level = 0.7)))
    }

    @Test fun `normal accepts when level unknown`() {
        val c = challenge(Difficulty.NORMAL).also { it.start() }
        assertIs<ChallengeResult.Completed>(c.processInput(say("gw udah bangun", level = null)))
    }

    @Test fun `normal level gate can be disabled`() {
        val c = challenge(Difficulty.NORMAL, minLevel = 0.0).also { it.start() }
        assertIs<ChallengeResult.Completed>(c.processInput(say("gw udah bangun", level = 0.0)))
    }

    @Test fun `normal confidence gate only when reported`() {
        val c = challenge(Difficulty.NORMAL, minLevel = 0.0, minConf = 0.6).also { it.start() }
        assertEquals(RejectReason.LOW_CONFIDENCE, assertIs<ChallengeResult.Rejected>(c.processInput(say("gw udah bangun", conf = 0.2))).reason)
        assertIs<ChallengeResult.Completed>(c.processInput(say("gw udah bangun", conf = null)))
    }

    @Test fun `easy and hard ignore level gate`() {
        for (d in listOf(Difficulty.EASY, Difficulty.HARD)) {
            val c = challenge(d).also { it.start() }
            assertFalse(c.processInput(say("gw udah bangun", level = 0.0)) is ChallengeResult.Rejected, d.name)
        }
    }

    @Test fun `best alternative wins`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        val input = SpeechInput(listOf(SpeechCandidate("gw belum bangun"), SpeechCandidate("gue udah bangun")), true)
        assertIs<ChallengeResult.Completed>(c.processInput(input))
    }

    @Test fun `accepted alternative beats a higher scoring rejected one`() {
        // "...belum" scores 1.0 but is contradicted; "udah bangun" scores 0.8 and is accepted
        val c = challenge(Difficulty.EASY).also { it.start() }
        val input = SpeechInput(listOf(SpeechCandidate("gw udah bangun belum"), SpeechCandidate("udah bangun")), true)
        assertIs<ChallengeResult.Completed>(c.processInput(input))
    }

    @Test fun `rejected reason carries the best match for feedback`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        val r = assertIs<ChallengeResult.Rejected>(c.processInput(say("gw belum bangun")))
        assertEquals(RejectReason.NO_MATCH, r.reason)
        assertTrue(r.match!!.contradicted)
    }

    @Test fun `unknown input type is ignored`() {
        val c = challenge(Difficulty.EASY).also { it.start() }
        assertEquals(ChallengeResult.Ignored, c.processInput(object : ChallengeInput {}))
        assertEquals(0, c.failedAttempts)
    }

    @Test fun `start and reset clear state`() {
        val c = challenge(Difficulty.HARD).also { it.start() }
        c.processInput(say("gw udah bangun")); c.processInput(say("x"))
        c.start()
        assertEquals(ChallengeProgress(0, 3), c.progress)
        assertEquals(0, c.failedAttempts)
        c.processInput(say("gw udah bangun"))
        c.reset()
        assertEquals(ChallengeProgress(0, 3), c.progress)
        assertEquals(ChallengeResult.Ignored, c.processInput(say("gw udah bangun")))
    }

    @Test fun `progress validation`() {
        assertFailsWith<IllegalArgumentException> { ChallengeProgress(4, 3) }
        assertFailsWith<IllegalArgumentException> { ChallengeProgress(0, 0) }
    }
}
