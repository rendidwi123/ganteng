package com.bangunwoi.core.matching

/**
 * Shared dataset for the default target "GW UDAH BANGUN" (default threshold 0.8, auto error budget = 0).
 * Add a case here whenever a real-device recognizer output is observed.
 */
object WakePhraseTestData {
    const val TARGET = "GW UDAH BANGUN"

    /** Must be accepted. */
    val VALID: Map<String, List<String>> = mapOf(
        "canonical variants" to listOf(
            "gw udah bangun", "gue udah bangun", "gw sudah bangun", "gue sudah bangun",
            "gua udah bangun", "gue udh bangun", "gw dah bangun", "gue sdh bangun", "gw udeh bangun",
        ),
        "case, punctuation, whitespace" to listOf(
            "GW UDAH BANGUN", "GW UDAH BANGUN!", "Gue Udah Bangun.", "  gw   udah   bangun  ",
            "gw, udah, bangun!!!", "\"gue sudah bangun\"", "gw\tudah\nbangun", "GW... UDAH... BANGUN...",
            "gw-udah-bangun", "(gw udah bangun)", "gw udah bangun?!", "Gw   Udah   Bangun",
        ),
        "pronoun omitted (optional word)" to listOf(
            "udah bangun", "sudah bangun", "UDAH BANGUN!", "udh bangun", "dah bangun", " sudah   bangun. ",
        ),
        "other pronouns / spellings" to listOf(
            "aku udah bangun", "saya sudah bangun", "gw udah bgn", "ane udah bangun",
        ),
        "elongation and small typos" to listOf(
            "gueee udah bangunnn", "gw udahhh bangun", "gw udah bagun", "gue sudah banggun", "gw udah bangunn",
        ),
        "surrounding speech" to listOf(
            "ya gue udah bangun nih", "oke gw udah bangun gw udah bangun", "gw udah bangun woi",
            "iya iya gue sudah bangun", "gw udah bangun gw udah bangun gw udah bangun", "halo halo gw udah bangun ya",
        ),
        "diacritics" to listOf("gué udah bangun", "GW ÚDAH BANGUN"),
    )

    /** Must be rejected. */
    val INVALID: Map<String, List<String>> = mapOf(
        "empty / noise" to listOf("", "   ", "...", "!!!", "\n\t", "hmm", "eh", "ya", "oke", "iya iya"),
        "negation" to listOf(
            "gw belum bangun", "gue belum bangun", "gw blm bangun", "gw gak bangun", "gue ga bangun",
            "gw nggak udah bangun", "gue tidak sudah bangun", "gw udah bangun belum", "belum bangun", "gw bukan udah bangun",
        ),
        "contradiction / sleepy" to listOf(
            "gw masih tidur", "aku masih tidur", "gue sudah tidur", "gw udah tidur", "gw belum tidur",
            "aku mau tidur lagi", "ngantuk banget", "gw masih tidur udah bangun", "gw udah bangun tapi masih ngantuk",
        ),
        "unrelated" to listOf(
            "selamat pagi", "halo", "selamat siang", "5 menit lagi", "tolong matikan alarm", "matikan alarm",
            "stop", "alarm mati dong", "gw udah makan", "gue sudah mandi", "gw udah sampai", "terima kasih",
            "apa kabar", "jam berapa sekarang", "lagi dimana", "kenapa pagi pagi",
        ),
        "partial phrase" to listOf(
            "bangun", "sudah", "udah", "gw", "gue", "gw udah", "gue sudah", "gw bangun", "gue bangun",
            "bangun gw udah", "bangun udah gw", "udah gw",
        ),
        "wrong middle word" to listOf("gw mau bangun", "gue akan bangun", "gw baru bangun lagi tidur"),
        "interleaved filler" to listOf("gw udah banget bangun", "gue udah eh bangun"),
    )

    /**
     * Accepted on purpose; documented trade-offs rather than bugs.
     * (Other leading words are ignored because the pronoun is optional.)
     */
    val ACCEPTED_BY_DESIGN: List<String> = listOf("lu udah bangun", "dia udah bangun", "kamu sudah bangun")

    val allValid: List<String> get() = VALID.values.flatten()
    val allInvalid: List<String> get() = INVALID.values.flatten()
}
