package com.bangunwoi.core.matching

import java.text.Normalizer
import java.util.Locale

/**
 * Turns free text from a speech recognizer (or user input) into canonical tokens.
 *
 * Steps: lowercase -> strip diacritics -> replace anything that is not a letter/digit with a space ->
 * collapse elongated letters ("bangunnn" -> "bangun", only runs of 3+) -> map informal Indonesian
 * spellings to one canonical word ([synonyms]).
 *
 * The synonym table is deliberately small and explicit; extend it through the constructor.
 */
public class PhraseNormalizer(
    synonyms: Map<String, String> = DEFAULT_SYNONYMS,
) {
    private val synonyms: Map<String, String> = synonyms.mapKeys { it.key.lowercase(Locale.ROOT) }

    /** Canonical tokens; empty for blank / punctuation-only input. */
    public fun tokens(text: String): List<String> {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val cleaned = decomposed.replace(DIACRITICS, "").replace(NON_ALNUM, " ")
        return cleaned.split(' ')
            .filter { it.isNotEmpty() }
            .map { collapseElongation(it) }
            .map { synonyms[it] ?: it }
    }

    public fun normalize(text: String): String = tokens(text).joinToString(" ")

    private fun collapseElongation(token: String): String = ELONGATION.replace(token, "$1")

    public companion object {
        private val DIACRITICS = Regex("\\p{M}+")
        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
        private val ELONGATION = Regex("(.)\\1{2,}")

        /** First-person pronouns -> "gue"; "already" -> "sudah"; "wake up" -> "bangun". */
        public val DEFAULT_SYNONYMS: Map<String, String> = buildMap {
            listOf("gw", "gue", "gua", "gwe", "guwe", "gwa", "ane", "aku", "ak", "saya", "sy", "ku")
                .forEach { put(it, "gue") }
            listOf("udah", "sudah", "dah", "udh", "sdh", "udeh", "wes", "uda", "suda")
                .forEach { put(it, "sudah") }
            listOf("bangun", "bgn", "bangu", "bangon")
                .forEach { put(it, "bangun") }
        }
    }
}
