package com.tbutman.tilde

import java.io.StringReader
import java.io.StringWriter
import java.net.URLEncoder
import java.util.Properties

/**
 * Whose card this is: everything personal the app shares. Edited in Settings and stored only on
 * the phone; a build can seed it from the git-ignored profile.local.properties, so nothing personal
 * lives in the source. Plain Kotlin, so the contact card and links are unit-tested.
 */
data class Profile(
    val name: String = "",
    val title: String = "",
    val email: String = "",
    /** What the first sharing option opens, e.g. https://example.com/hello. */
    val website: String = "",
    /** Shown after the ~/ mark on the Share screen. */
    val handle: String = "",
    val linkedin: String = "",
    val github: String = "",
    val instagram: String = "",
    val x: String = "",
    /** (label, number) pairs, e.g. ("mobile (US)", "+1 555 0100"). Only shared in person. */
    val phones: List<Pair<String, String>> = emptyList(),
    val whatsapp: String = "",
) {
    val isSet: Boolean get() = name.isNotBlank()
    val firstName: String get() = name.trim().substringBefore(' ')
    val lastName: String get() = name.trim().substringAfter(' ', "")

    /** The default WhatsApp text: short and neutral, typed in but never sent for them. */
    val greeting: String get() = if (firstName.isEmpty()) "Hi" else "Hi $firstName"

    val hasWhatsapp: Boolean get() = whatsappUrl(whatsapp, "") != null

    /** The site's root (https://example.com/), for the contact card; the website field may be a page. */
    val siteRoot: String? get() = Regex("^https?://[^/?#]+").find(website.trim())?.value?.plus("/")

    /** The host event tags apply to, without www. */
    val siteHost: String? get() = siteRoot?.substringAfter("://")?.removeSuffix("/")?.removePrefix("www.")?.lowercase()

    val socials: List<Pair<String, String>>
        get() = listOf("LinkedIn" to linkedin, "GitHub" to github, "Instagram" to instagram, "X" to x)
            .map { (label, url) -> label to url.trim() }
            .filter { it.second.isNotEmpty() }

    /**
     * vCard 3.0, which Android and iOS contacts both import. `compact` keeps only the website, for
     * the on-screen QR code: the full card makes a code too dense to scan easily.
     */
    fun vcard(compact: Boolean = false): String {
        var item = 0
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0", "N:${escape(lastName)};${escape(firstName)};;;", "FN:${escape(name.trim())}")
        if (title.isNotBlank()) lines += "TITLE:${escape(title.trim())}"
        if (email.isNotBlank()) lines += "EMAIL;TYPE=INTERNET:${email.trim()}"
        for ((label, number) in phones.filter { it.second.isNotBlank() }) {
            item++
            lines += "item$item.TEL;TYPE=CELL:${number.trim()}"
            if (label.isNotBlank()) lines += "item$item.X-ABLabel:${escape(label.trim())}"
        }
        val links = listOfNotNull(siteRoot?.let { "Website" to it }) + socials
        for ((label, href) in if (compact) links.take(1) else links) {
            item++
            lines += "item$item.URL:$href"
            lines += "item$item.X-ABLabel:${escape(label)}"
        }
        lines += "END:VCARD"
        return lines.joinToString("\r\n") + "\r\n"
    }

    /** The same `key=value` format as profile.local.properties. */
    fun toText(): String {
        val props = Properties()
        fun put(key: String, value: String) { if (value.isNotBlank()) props.setProperty(key, value) }
        put("name", name); put("title", title); put("email", email); put("website", website); put("handle", handle)
        put("linkedin", linkedin); put("github", github); put("instagram", instagram); put("x", x); put("whatsapp.number", whatsapp)
        phones.forEachIndexed { i, (label, number) -> put("phone.${i + 1}.label", label); put("phone.${i + 1}.number", number) }
        return StringWriter().also { props.store(it, null) }.toString()
    }

    companion object {
        /** Reads profile.local.properties text; unknown keys are ignored, missing ones are blank. */
        fun parse(text: String): Profile {
            val p = Properties().apply { load(StringReader(text)) }
            fun get(key: String) = p.getProperty(key, "").trim()
            val phones = generateSequence(1) { it + 1 }
                .map { get("phone.$it.label") to get("phone.$it.number") }
                .takeWhile { it.second.isNotEmpty() }
                .toList()
            return Profile(
                name = get("name"), title = get("title"), email = get("email"), website = get("website"),
                handle = get("handle"), linkedin = get("linkedin"), github = get("github"),
                instagram = get("instagram"), x = get("x"), phones = phones, whatsapp = get("whatsapp.number"),
            )
        }

        /**
         * WhatsApp click-to-chat link, or null for a missing number. wa.me wants the international
         * number as digits only (no +, spaces or dashes); a blank greeting opens an empty chat.
         */
        fun whatsappUrl(number: String, greeting: String): String? {
            val digits = number.filter { it.isDigit() }
            if (digits.length < 8) return null
            if (greeting.isBlank()) return "https://wa.me/$digits"
            val text = URLEncoder.encode(greeting, "UTF-8").replace("+", "%20")
            return "https://wa.me/$digits?text=$text"
        }

        private fun escape(value: String) =
            value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
    }
}
