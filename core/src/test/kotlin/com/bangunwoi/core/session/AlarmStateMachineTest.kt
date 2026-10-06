package com.bangunwoi.core.session

import com.bangunwoi.core.session.AlarmEvent.*
import com.bangunwoi.core.session.AlarmState.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmStateMachineTest {
    private val allEvents: List<AlarmEvent> = listOf(
        Schedule, Cancel, Fire, ListenStarted, SpeechDetected, ListenTimeout, RecognizerRestart,
        PhraseRejected, PhraseAccepted(false), PhraseAccepted(true), EmergencyStop,
    )

    /** The complete set of valid transitions. Everything else must be Invalid. */
    private val valid: Map<Pair<AlarmState, AlarmEvent>, AlarmState> = buildMap {
        for (s in listOf(IDLE, SCHEDULED, COMPLETED, EMERGENCY_STOP)) {
            put(s to Schedule, SCHEDULED)
            put(s to Cancel, IDLE)
        }
        put(SCHEDULED to Fire, TRIGGERED)
        for (s in listOf(TRIGGERED, CHALLENGE_FAILED, CHALLENGE_PROGRESS)) put(s to ListenStarted, LISTENING)
        put(LISTENING to SpeechDetected, RECOGNIZING)
        put(LISTENING to ListenTimeout, CHALLENGE_FAILED)
        for (s in listOf(LISTENING, RECOGNIZING)) {
            put(s to RecognizerRestart, LISTENING)
            put(s to PhraseRejected, CHALLENGE_FAILED)
            put(s to PhraseAccepted(false), CHALLENGE_PROGRESS)
            put(s to PhraseAccepted(true), COMPLETED)
        }
        for (s in listOf(TRIGGERED, LISTENING, RECOGNIZING, CHALLENGE_FAILED, CHALLENGE_PROGRESS)) {
            put(s to EmergencyStop, EMERGENCY_STOP)
        }
    }

    @Test fun `every state-event pair matches the transition table exactly`() {
        for (state in AlarmState.values()) for (event in allEvents) {
            val expected = valid[state to event]
            val actual = AlarmStateMachine.next(state, event)
            if (expected != null) assertEquals(TransitionResult.Valid(expected), actual, "$state + $event")
            else assertEquals(TransitionResult.Invalid(state, event), actual, "$state + $event")
        }
    }

    @Test fun `cannot cancel or reschedule while ringing`() {
        for (s in AlarmState.values().filter { it.isRinging }) {
            assertTrue(AlarmStateMachine.next(s, Cancel) is TransitionResult.Invalid, "$s")
            assertTrue(AlarmStateMachine.next(s, Schedule) is TransitionResult.Invalid, "$s")
        }
    }

    @Test fun `only COMPLETED or EMERGENCY_STOP lead out of the ringing states`() {
        val exits = valid.filter { (k, v) -> k.first.isRinging && !v.isRinging }
        assertTrue(exits.values.all { it == COMPLETED || it == EMERGENCY_STOP })
    }

    @Test fun `emergency stop is reachable from every ringing state`() {
        for (s in AlarmState.values().filter { it.isRinging }) {
            assertEquals(TransitionResult.Valid(EMERGENCY_STOP), AlarmStateMachine.next(s, EmergencyStop), "$s")
        }
    }

    @Test fun `terminal and ringing classification`() {
        assertEquals(setOf(COMPLETED, EMERGENCY_STOP), AlarmState.values().filter { it.isTerminal }.toSet())
        assertEquals(
            setOf(TRIGGERED, LISTENING, RECOGNIZING, CHALLENGE_FAILED, CHALLENGE_PROGRESS),
            AlarmState.values().filter { it.isRinging }.toSet(),
        )
    }
}
