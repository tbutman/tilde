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

    /**
     * The default text typed (never sent) into the chat: short and neutral, so it doesn't speak for
     * them, and it stays as a draft if they leave without sending. Editable in the app.
     */
    const val WHATSAPP_GREETING = "Hi Thomas"

    val hasWhatsapp: Boolean
        get() = whatsappUrl(BuildConfig.WHATSAPP_NUMBER, "") != null

    /**
     * WhatsApp click-to-chat link, or null for a missing number. wa.me wants the international
     * number as digits only (no +, spaces or dashes); a blank greeting opens an empty chat.
     */
    fun whatsappUrl(number: String = BuildConfig.WHATSAPP_NUMBER, greeting: String = WHATSAPP_GREETING): String? {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 8) return null
        if (greeting.isBlank()) return "https://wa.me/$digits"
        val text = java.net.URLEncoder.encode(greeting, "UTF-8").replace("+", "%20")
        return "https://wa.me/$digits?text=$text"
    }

    /**
     * vCard 3.0, which Android and iOS contacts both import. `compact` keeps only the website from
     * the links, for the on-screen QR code: the full card makes a code too dense to scan easily.
     */
    fun vcard(phones: List<Pair<String, String>> = this.phones, compact: Boolean = false): String {
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
        for ((label, href) in if (compact) links.take(1) else links) {
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
