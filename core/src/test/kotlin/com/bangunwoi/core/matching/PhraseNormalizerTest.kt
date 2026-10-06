package com.bangunwoi.core.matching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhraseNormalizerTest {
    private val n = PhraseNormalizer()

    @Test fun `lowercases and canonicalizes`() = assertEquals("gue sudah bangun", n.normalize("GW UDAH BANGUN"))
    @Test fun `strips punctuation`() = assertEquals("gue sudah bangun", n.normalize("Gue, udah... bangun!!"))
    @Test fun `collapses whitespace including tabs and newlines`() =
        assertEquals("gue sudah bangun", n.normalize("  gw \t udah\n bangun  "))
    @Test fun `collapses elongation of three or more`() = assertEquals("gue sudah bangun", n.normalize("gueee udahhh bangunnn"))
    @Test fun `double letters survive`() = assertEquals(listOf("aa"), n.tokens("aa"))
    @Test fun `removes diacritics`() = assertEquals("gue", n.normalize("gué"))
    @Test fun `blank and punctuation only give no tokens`() {
        assertTrue(n.tokens("").isEmpty())
        assertTrue(n.tokens("  ").isEmpty())
        assertTrue(n.tokens("?!.,").isEmpty())
    }
    @Test fun `custom synonyms`() =
        assertEquals("x", PhraseNormalizer(mapOf("Y" to "x")).normalize("y"))

    @Test fun `digits are kept and emoji or symbols are dropped`() {
        assertEquals(listOf("5", "menit", "lagi"), n.tokens("5 menit lagi"))
        assertEquals(listOf("gue", "sudah", "bangun"), n.tokens("gw 😀 udah ⏰ bangun"))
    }

    @Test fun `control characters and non-latin scripts do not crash`() {
        assertEquals(listOf("gue"), n.tokens("\u0000gw\u0007"))
        assertTrue(n.tokens("😀😀😀").isEmpty())
        n.tokens("起きた") // letters in other scripts are kept as tokens, never an exception
    }

    @Test fun `normalization is idempotent`() {
        for (t in listOf("GW UDAH BANGUN!", "gueee udahhh", "  a  b  ", "Gué")) {
            assertEquals(n.normalize(t), n.normalize(n.normalize(t)), t)
        }
    }

    @Test fun `empty synonym table leaves words alone`() =
        assertEquals("gw udah", PhraseNormalizer(emptyMap()).normalize("GW UDAH"))
}
