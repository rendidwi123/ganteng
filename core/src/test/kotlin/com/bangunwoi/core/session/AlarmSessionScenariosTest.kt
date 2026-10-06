package com.bangunwoi.core.session

import com.bangunwoi.core.challenge.AngryModePolicy
import com.bangunwoi.core.challenge.ChallengeProgress
import com.bangunwoi.core.challenge.ChallengeResult
import com.bangunwoi.core.challenge.RejectReason
import com.bangunwoi.core.challenge.SpeechCandidate
import com.bangunwoi.core.challenge.SpeechInput
import com.bangunwoi.core.domain.Alarm
import com.bangunwoi.core.domain.AlarmSchedule
import com.bangunwoi.core.domain.ChallengeSettings
import com.bangunwoi.core.domain.Difficulty
import com.bangunwoi.core.domain.WakePhraseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sessions as the Android layer will drive them: recognizer callbacks in, snapshot/action out. */
class AlarmSessionScenariosTest {
    private fun alarm(
        difficulty: Difficulty = Difficulty.NORMAL,
        angry: Boolean = true,
        phrase: String = "GW UDAH BANGUN",
    ) = Alarm(
        schedule = AlarmSchedule.of(6, 30),
        wakePhrase = WakePhraseSettings(phrase),
        challenge = ChallengeSettings(difficulty, angryMode = angry),
        createdAtMillis = 0,
    )

    private fun final(text: String, level: Double? = null) = SpeechInput(listOf(SpeechCandidate(text)), true, level)
    private fun partial(text: String) = SpeechInput(listOf(SpeechCandidate(text)), false)

    /** Mirrors what the platform does: obey snapshot.action. */
    private fun AlarmSession.startListeningIfAsked() {
        if (snapshot().action == SessionAction.START_LISTENING) listenStarted()
    }

    @Test fun `realistic NORMAL morning - quiet mumble then clear phrase`() {
        val s = AlarmSession.forAlarm(alarm(Difficulty.NORMAL))
        assertEquals(SessionAction.NONE, s.snapshot().action)

        assertTrue(s.beginRinging().applied)
        assertEquals(SessionAction.START_LISTENING, s.snapshot().action)
        s.startListeningIfAsked()
        assertEquals(SessionAction.KEEP_LISTENING, s.snapshot().action)

        s.onSpeech(partial("gw ud"))                                           // partial feedback only
        assertEquals(AlarmState.RECOGNIZING, s.state)
        val quiet = s.onSpeech(final("gw udah bangun", level = 0.1)).snapshot  // too quiet: rejected
        assertEquals(AlarmState.CHALLENGE_FAILED, quiet.state)
        assertEquals(RejectReason.TOO_QUIET, assertIs<ChallengeResult.Rejected>(quiet.lastResult).reason)
        assertEquals("Bangun.", quiet.angry.message)
        assertEquals(SessionAction.START_LISTENING, quiet.action)              // platform restarts the recognizer

        s.startListeningIfAsked()
        val done = s.onSpeech(final("GW UDAH BANGUN!", level = 0.8)).snapshot
        assertTrue(done.isCompleted)
        assertEquals(SessionAction.STOP_ALARM, done.action)
        assertEquals(1, done.failedAttempts)                                   // stats survive completion
        assertEquals(0, done.angry.level)                                      // calm once finished
        assertIs<ChallengeResult.Completed>(done.lastResult)
    }

    @Test fun `realistic HARD morning - three successes with failures between`() {
        val s = AlarmSession.forAlarm(alarm(Difficulty.HARD))
        s.beginRinging(); s.startListeningIfAsked()
        val phrases = listOf("gw udah bangun", "uhh", "gue sudah bangun", "selamat pagi", "gw udah bangun")
        val progress = mutableListOf<ChallengeProgress>()
        for (p in phrases) {
            progress += s.onSpeech(final(p)).snapshot.progress
            if (s.state.isRinging) s.startListeningIfAsked()
        }
        assertEquals(listOf(1, 1, 2, 2, 3), progress.map { it.completed })
        assertEquals(AlarmState.COMPLETED, s.state)
        assertEquals(2, s.snapshot().failedAttempts)
    }

    @Test fun `silence escalates angry mode to maximum then holds`() {
        val s = AlarmSession.forAlarm(alarm(Difficulty.EASY))
        s.beginRinging()
        val messages = mutableListOf<String>()
        repeat(7) {
            s.startListeningIfAsked()
            messages += s.listenTimeout().snapshot.angry.message
        }
        assertEquals(
            listOf("Bangun.", "Serius masih tidur?", "GW BILANG BANGUN.", "TERIAK YANG JELAS.",
                "TERIAK YANG JELAS.", "TERIAK YANG JELAS.", "TERIAK YANG JELAS."),
            messages,
        )
        assertTrue(s.snapshot().angry.isMax)
        assertEquals(AlarmState.CHALLENGE_FAILED, s.state)                      // still ringing
        s.startListeningIfAsked()
        assertTrue(s.onSpeech(final("gue udah bangun")).snapshot.isCompleted)   // can still win at max anger
    }

    @Test fun `recognizer errors do not escalate anger`() {
        val s = AlarmSession.forAlarm(alarm(Difficulty.EASY))
        s.beginRinging(); s.startListeningIfAsked()
        repeat(10) { s.recognizerRestart() }
        assertEquals(0, s.snapshot().failedAttempts)
        assertEquals(0, s.snapshot().angry.level)
        assertEquals(AlarmState.LISTENING, s.state)
    }

    @Test fun `angry mode off keeps the alarm calm through failures`() {
        val s = AlarmSession.forAlarm(alarm(angry = false))
        s.beginRinging(); s.startListeningIfAsked()
        repeat(5) { s.onSpeech(final("zzz")); s.startListeningIfAsked() }
        assertEquals(5, s.snapshot().failedAttempts)
        assertEquals(0, s.snapshot().angry.level)
    }

    @Test fun `custom angry messages flow through forAlarm`() {
        val s = AlarmSession.forAlarm(alarm(), angryMessages = listOf("a", "b", "c"))
        s.beginRinging(); s.startListeningIfAsked()
        s.onSpeech(final("zzz")); s.startListeningIfAsked(); s.onSpeech(final("zzz"))
        assertEquals("c", s.snapshot().angry.message)
        assertEquals(2, s.snapshot().angry.maxLevel)
    }

    @Test fun `custom wake phrase is honoured and the default is not`() {
        val s = AlarmSession.forAlarm(alarm(Difficulty.EASY, phrase = "saya siap menghadapi hari"))
        s.beginRinging(); s.startListeningIfAsked()
        assertEquals(AlarmState.CHALLENGE_FAILED, s.onSpeech(final("gw udah bangun")).snapshot.state)
        s.startListeningIfAsked()
        assertTrue(s.onSpeech(final("saya siap menghadapi hari")).snapshot.isCompleted)
    }

    @Test fun `N-best alternatives are evaluated by the core not the platform`() {
        val s = AlarmSession.forAlarm(alarm(Difficulty.EASY))
        s.beginRinging(); s.startListeningIfAsked()
        val nBest = SpeechInput(listOf(SpeechCandidate("gw belum bangun"), SpeechCandidate("gue udah bangun")), true)
        assertTrue(s.onSpeech(nBest).snapshot.isCompleted)
    }

    @Test fun `partial results alone never complete or fail the alarm`() {
        val s = AlarmSession.forAlarm(alarm(Difficulty.EASY))
        s.beginRinging(); s.startListeningIfAsked()
        repeat(5) { s.onSpeech(partial("gw udah bangun")) }
        assertEquals(AlarmState.RECOGNIZING, s.state)
        assertEquals(0, s.snapshot().failedAttempts)
        assertEquals(0, s.snapshot().progress.completed)
        assertNotNull(s.snapshot().lastMatch)
        s.listenTimeout()                                                       // illegal from RECOGNIZING
        assertEquals(AlarmState.RECOGNIZING, s.state)
        assertEquals(0, s.snapshot().failedAttempts)
    }

    @Test fun `emergency stop from every ringing state`() {
        val reach: List<Pair<AlarmState, (AlarmSession) -> Unit>> = listOf(
            AlarmState.TRIGGERED to { s -> s.beginRinging() },
            AlarmState.LISTENING to { s -> s.beginRinging(); s.listenStarted() },
            AlarmState.RECOGNIZING to { s -> s.beginRinging(); s.listenStarted(); s.onSpeech(partial("gw")) },
            AlarmState.CHALLENGE_FAILED to { s -> s.beginRinging(); s.listenStarted(); s.onSpeech(final("zzz")) },
            AlarmState.CHALLENGE_PROGRESS to { s -> s.beginRinging(); s.listenStarted(); s.onSpeech(final("gw udah bangun")) },
        )
        for ((expected, setup) in reach) {
            val s = AlarmSession.forAlarm(alarm(Difficulty.HARD))
            setup(s)
            assertEquals(expected, s.state)
            val snap = s.emergencyStop().snapshot
            assertEquals(AlarmState.EMERGENCY_STOP, snap.state, "from $expected")
            assertEquals(SessionAction.STOP_ALARM, snap.action)
            assertFalse(snap.isCompleted)                                       // not a real completion
            assertEquals(0, snap.angry.level)
            assertFalse(s.onSpeech(final("gw udah bangun")).applied)            // late callback is harmless
        }
    }

    @Test fun `emergency stop is not available when nothing rings`() {
        val s = AlarmSession.forAlarm(alarm())
        assertFalse(s.emergencyStop().applied)
        s.schedule()
        assertFalse(s.emergencyStop().applied)
    }

    @Test fun `beginRinging works from IDLE SCHEDULED and finished states and is a no-op while ringing`() {
        val fromIdle = AlarmSession.forAlarm(alarm(Difficulty.EASY))
        assertTrue(fromIdle.beginRinging().applied)
        assertEquals(AlarmState.TRIGGERED, fromIdle.state)
        assertFalse(fromIdle.beginRinging().applied)                            // duplicate broadcast

        val fromScheduled = AlarmSession.forAlarm(alarm(Difficulty.EASY)).also { it.schedule() }
        assertTrue(fromScheduled.beginRinging().applied)

        val repeat = AlarmSession.forAlarm(alarm(Difficulty.EASY))
        repeat.beginRinging(); repeat.startListeningIfAsked(); repeat.onSpeech(final("gw udah bangun"))
        assertEquals(AlarmState.COMPLETED, repeat.state)
        assertTrue(repeat.beginRinging().applied)                               // next day's ring, same session object
        val snap = repeat.snapshot()
        assertEquals(0, snap.failedAttempts)
        assertEquals(0, snap.progress.completed)
        assertNull(snap.lastMatch); assertNull(snap.lastResult)
    }

    @Test fun `action is derived from state for every state`() {
        val expected = mapOf(
            AlarmState.IDLE to SessionAction.NONE, AlarmState.SCHEDULED to SessionAction.NONE,
            AlarmState.TRIGGERED to SessionAction.START_LISTENING, AlarmState.LISTENING to SessionAction.KEEP_LISTENING,
            AlarmState.RECOGNIZING to SessionAction.KEEP_LISTENING, AlarmState.CHALLENGE_FAILED to SessionAction.START_LISTENING,
            AlarmState.CHALLENGE_PROGRESS to SessionAction.START_LISTENING, AlarmState.COMPLETED to SessionAction.STOP_ALARM,
            AlarmState.EMERGENCY_STOP to SessionAction.STOP_ALARM,
        )
        assertEquals(AlarmState.entries.toSet(), expected.keys)
        // STOP_ALARM exactly for the terminal states; ringing states are never NONE
        for ((state, action) in expected) {
            assertEquals(state.isTerminal, action == SessionAction.STOP_ALARM, "$state")
            if (state.isRinging) assertTrue(action != SessionAction.NONE && action != SessionAction.STOP_ALARM, "$state")
        }
    }

    @Test fun `challenge settings required successes override reaches the session`() {
        val a = alarm(Difficulty.EASY).copy(challenge = ChallengeSettings(Difficulty.EASY, requiredSuccesses = 2))
        val s = AlarmSession.forAlarm(a)
        s.beginRinging(); s.startListeningIfAsked()
        assertEquals(ChallengeProgress(1, 2), s.onSpeech(final("gw udah bangun")).snapshot.progress)
        s.startListeningIfAsked()
        assertTrue(s.onSpeech(final("gw udah bangun")).snapshot.isCompleted)
    }

    @Test fun `session behaviour is deterministic`() {
        fun run(): List<SessionSnapshot> {
            val s = AlarmSession(
                com.bangunwoi.core.challenge.WakePhraseChallenge(WakePhraseSettings(), ChallengeSettings(Difficulty.HARD)),
                AngryModePolicy(),
            )
            val snaps = mutableListOf<SessionSnapshot>()
            snaps += s.beginRinging().snapshot; snaps += s.listenStarted().snapshot
            for (t in listOf("a", "gw udah bangun", "b", "gw udah bangun", "gw udah bangun")) {
                snaps += s.onSpeech(final(t)).snapshot
                if (s.state.isRinging) snaps += s.listenStarted().snapshot
            }
            return snaps
        }
        assertEquals(run(), run())
    }
}
