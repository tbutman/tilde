package com.tbutman.nfcshare

/**
 * The contact card for "Share contact" mode: the same public details as the site's vCard
 * (tbutman-site/src/lib/vcard.ts), plus phone numbers, which are only ever shared in person.
 * The numbers come from contact.local.properties through BuildConfig and are not in the repo.
 */
object Contact {
    const val GIVEN_NAME = "Thomas"
    const val FAMILY_NAME = "Butman"
    const val TITLE = "Senior product engineer"
    const val EMAIL = "tbutman@gmail.com"
    val links = listOf(
        "Website" to "https://tbutman.com/",
        "Instagram" to "https://www.instagram.com/t.butman/",
        "X" to "https://x.com/tbutman",
        "LinkedIn" to "https://www.linkedin.com/in/thomasbutman",
    )

    val phones: List<Pair<String, String>>
        get() = BuildConfig.PHONE_LABELS.zip(BuildConfig.PHONE_NUMBERS)

    /** vCard 3.0, which Android and iOS contacts both import. */
    fun vcard(phones: List<Pair<String, String>> = this.phones): String {
        var item = 0
        val lines = mutableListOf(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "N:${escape(FAMILY_NAME)};${escape(GIVEN_NAME)};;;",
            "FN:${escape("$GIVEN_NAME $FAMILY_NAME")}",
            "TITLE:${escape(TITLE)}",
            "EMAIL;TYPE=INTERNET:$EMAIL",
        )
        for ((label, number) in phones) {
            item++
            lines += "item$item.TEL;TYPE=CELL:$number"
            lines += "item$item.X-ABLabel:${escape(label)}"
        }
        for ((label, href) in links) {
            item++
            lines += "item$item.URL:$href"
            lines += "item$item.X-ABLabel:${escape(label)}"
        }
        lines += "END:VCARD"
        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun escape(value: String) =
        value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
}
