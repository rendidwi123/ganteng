package com.bangunwoi.core.challenge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AngryModePolicyTest {
    private val p = AngryModePolicy()

    @Test fun `escalates per failed attempt`() {
        assertEquals("Waktunya bangun.", p.stateFor(0).message)
        assertEquals("Bangun.", p.stateFor(1).message)
        assertEquals("Serius masih tidur?", p.stateFor(2).message)
        assertEquals("GW BILANG BANGUN.", p.stateFor(3).message)
        assertEquals("TERIAK YANG JELAS.", p.stateFor(4).message)
    }

    @Test fun `caps at maximum`() {
        val s = p.stateFor(500)
        assertEquals(4, s.level)
        assertEquals(1.0, s.intensity)
        assertEquals(p.stateFor(4), s)
    }

    @Test fun `intensity grows monotonically from 0 to 1`() {
        val values = (0..6).map { p.stateFor(it).intensity }
        assertEquals(0.0, values.first())
        assertEquals(1.0, values.last())
        assertEquals(values.sorted(), values)
    }

    @Test fun `disabled stays calm`() {
        val d = AngryModePolicy(enabled = false)
        assertEquals(0, d.stateFor(10).level)
        assertEquals(0.0, d.stateFor(10).intensity)
        assertEquals("Waktunya bangun.", d.stateFor(10).message)
    }

    @Test fun `custom messages`() {
        val c = AngryModePolicy(messages = listOf("a", "b", "c"))
        assertEquals(2, c.maxLevel)
        assertEquals("c", c.stateFor(9).message)
        assertEquals(0.5, c.stateFor(1).intensity)
    }

    @Test fun `validation`() {
        assertFailsWith<IllegalArgumentException> { AngryModePolicy(messages = listOf("only")) }
        assertFailsWith<IllegalArgumentException> { AngryModePolicy(messages = listOf("a", " ")) }
        assertFailsWith<IllegalArgumentException> { p.stateFor(-1) }
    }

    @Test fun `failures per level slows escalation`() {
        val slow = AngryModePolicy(failuresPerLevel = 2)
        val levels = (0..10).map { slow.stateFor(it).level }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 4), levels)
        assertFailsWith<IllegalArgumentException> { AngryModePolicy(failuresPerLevel = 0) }
    }

    @Test fun `isMax only at the top level`() {
        assertEquals(listOf(false, false, false, false, true), (0..4).map { p.stateFor(it).isMax })
    }

    @Test fun `same input always gives the same state`() {
        for (n in 0..20) assertEquals(p.stateFor(n), AngryModePolicy().stateFor(n))
    }
}
