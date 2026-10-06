package com.bangunwoi.core.matching

/** Shared dataset for the default target "GW UDAH BANGUN". */
object WakePhraseTestData {
    const val TARGET = "GW UDAH BANGUN"

    val VALID: List<String> = listOf(
        // the four canonical variants
        "gw udah bangun", "gue udah bangun", "gw sudah bangun", "gue sudah bangun",
        // case, punctuation, whitespace
        "GW UDAH BANGUN!", "Gue Udah Bangun.", "  gw   udah   bangun  ", "gw, udah, bangun!!!",
        "\"gue sudah bangun\"", "gw\tudah\nbangun", "GW... UDAH... BANGUN...",
        // informal spellings and pronouns
        "gua udah bangun", "gue udh bangun", "gw dah bangun", "gue sdh bangun", "gw udeh bangun",
        "aku udah bangun", "saya sudah bangun", "gw udah bgn",
        // elongation and small recognizer typos
        "gueee udah bangunnn", "gw udahhh bangun", "gw udah bagun", "gue sudah banggun",
        // continuous / surrounding speech
        "ya gue udah bangun nih", "oke gw udah bangun gw udah bangun", "gw udah bangun woi",
        // diacritics
        "gué udah bangun",
    )

    val INVALID: List<String> = listOf(
        "", "   ", "...", "!!!",
        "gw belum bangun", "aku masih tidur", "selamat pagi", "bangun", "gw udah", "gue sudah tidur",
        "aku mau tidur lagi", "udah bangun", "gw bangun", "bangun gw udah", "lu udah bangun",
        "gw udah tidur", "gw belum tidur", "ngantuk banget", "5 menit lagi", "gw udah makan",
    )
}
