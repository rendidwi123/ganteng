package com.bangunwoi.core.session

import com.bangunwoi.core.challenge.AngryModePolicy
import com.bangunwoi.core.challenge.ChallengeProgress
import com.bangunwoi.core.challenge.SpeechCandidate
import com.bangunwoi.core.challenge.SpeechInput
import com.bangunwoi.core.challenge.WakePhraseChallenge
import com.bangunwoi.core.domain.ChallengeSettings
import com.bangunwoi.core.domain.Difficulty
import com.bangunwoi.core.domain.WakePhraseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmSessionTest {
    private fun session(d: Difficulty = Difficulty.EASY, angry: Boolean = true) = AlarmSession(
        WakePhraseChallenge(WakePhraseSettings(), ChallengeSettings(d, angryMode = angry)),
        AngryModePolicy(enabled = angry),
    )

    private fun ringing(s: AlarmSession) { s.schedule(); s.fire(); s.listenStarted() }
    private fun say(text: String, final: Boolean = true) = SpeechInput(listOf(SpeechCandidate(text)), final)

    @Test fun `happy path easy`() {
        val s = session()
        assertEquals(AlarmState.IDLE, s.state)
        assertTrue(s.schedule().applied)
        assertEquals(AlarmState.TRIGGERED, s.fire().snapshot.state)
        assertEquals(AlarmState.LISTENING, s.listenStarted().snapshot.state)
        assertEquals(AlarmState.RECOGNIZING, s.onSpeech(say("gw udah", final = false)).snapshot.state)
        val done = s.onSpeech(say("gw udah bangun")).snapshot
        assertEquals(AlarmState.COMPLETED, done.state)
        assertEquals(ChallengeProgress(1, 1), done.progress)
        assertNotNull(done.lastMatch)
    }

    @Test fun `final result without partial works from LISTENING`() {
        val s = session(); ringing(s)
        assertEquals(AlarmState.COMPLETED, s.onSpeech(say("gue sudah bangun")).snapshot.state)
    }

    @Test fun `failures escalate angry mode and keep ringing`() {
        val s = session(); ringing(s)
        val first = s.onSpeech(say("zzz")).snapshot
        assertEquals(AlarmState.CHALLENGE_FAILED, first.state)
        assertEquals(1, first.failedAttempts)
        assertEquals("Bangun.", first.angry.message)
        s.listenStarted()
        s.listenTimeout()                       // silence counts as failed attempt #2
        assertEquals("Serius masih tidur?", s.snapshot().angry.message)
        s.listenStarted(); s.onSpeech(say("hmm"))
        assertEquals("GW BILANG BANGUN.", s.snapshot().angry.message)
        assertTrue(s.state.isRinging)
    }

    @Test fun `hard flow with progress`() {
        val s = session(Difficulty.HARD); ringing(s)
        assertEquals(AlarmState.CHALLENGE_PROGRESS, s.onSpeech(say("gw udah bangun")).snapshot.state)
        assertEquals(ChallengeProgress(1, 3), s.snapshot().progress)
        s.listenStarted()
        assertEquals(AlarmState.CHALLENGE_PROGRESS, s.onSpeech(say("gw udah bangun")).snapshot.state)
        s.listenStarted()
        assertEquals(AlarmState.COMPLETED, s.onSpeech(say("gw udah bangun")).snapshot.state)
    }

    @Test fun `recognizer restart does not count as a failure`() {
        val s = session(); ringing(s)
        s.onSpeech(say("gw", final = false))          // -> RECOGNIZING
        assertEquals(AlarmState.LISTENING, s.recognizerRestart().snapshot.state)
        assertEquals(0, s.snapshot().failedAttempts)
    }

    @Test fun `speech outside listening is ignored and does not touch the challenge`() {
        val s = session()
        assertFalse(s.onSpeech(say("gw udah bangun")).applied)
        s.schedule(); s.fire()                         // TRIGGERED, not listening yet
        assertFalse(s.onSpeech(say("zzz")).applied)
        assertEquals(0, s.snapshot().failedAttempts)
        s.listenStarted()
        assertEquals(AlarmState.COMPLETED, s.onSpeech(say("gw udah bangun")).snapshot.state)
    }

    @Test fun `late callbacks after completion are harmless`() {
        val s = session(); ringing(s); s.onSpeech(say("gw udah bangun"))
        assertFalse(s.onSpeech(say("zzz")).applied)
        assertFalse(s.listenTimeout().applied)
        assertFalse(s.fire().applied)
        assertEquals(0, s.snapshot().failedAttempts)
        assertEquals(AlarmState.COMPLETED, s.state)
    }

    @Test fun `emergency stop from any ringing state and cannot cancel while ringing`() {
        val s = session(); ringing(s)
        assertFalse(s.cancel().applied)
        assertFalse(s.schedule().applied)
        assertTrue(s.emergencyStop().applied)
        assertEquals(AlarmState.EMERGENCY_STOP, s.state)
        assertFalse(s.emergencyStop().applied)
    }

    @Test fun `rescheduling after completion allows the next ring with fresh challenge`() {
        val s = session(Difficulty.HARD); ringing(s)
        s.onSpeech(say("gw udah bangun")); s.onSpeech(say("zzz")) // 2nd call is ignored: state is CHALLENGE_PROGRESS
        s.emergencyStop()
        assertTrue(s.schedule().applied)
        assertTrue(s.fire().applied)
        assertEquals(ChallengeProgress(0, 3), s.snapshot().progress)
        assertEquals(0, s.snapshot().failedAttempts)
        assertNull(s.snapshot().lastMatch)
    }

    @Test fun `angry mode disabled never escalates`() {
        val s = session(angry = false); ringing(s)
        repeat(3) { s.onSpeech(say("zzz")); s.listenStarted() }
        assertEquals(0, s.snapshot().angry.level)
        assertEquals(3, s.snapshot().failedAttempts)
    }

    @Test fun `invalid events never throw`() {
        val s = session()
        assertFalse(s.listenStarted().applied)
        assertFalse(s.listenTimeout().applied)
        assertFalse(s.recognizerRestart().applied)
        assertFalse(s.emergencyStop().applied)
        assertFalse(s.fire().applied)
        assertEquals(AlarmState.IDLE, s.state)
    }
}
