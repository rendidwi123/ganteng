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
}
