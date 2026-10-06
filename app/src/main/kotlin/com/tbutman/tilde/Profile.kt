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
    /** An optional second address, such as work and personal; on the contact card only. */
    val email2: String = "",
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

    /**
     * The link a contact card carries for phones that only follow links (iPhones, on a tap): the
     * website, else the first social profile. The label is the network's name, or null for the
     * website. Null when there's no link at all, so an iPhone tap on the card does nothing.
     */
    val contactLink: Pair<String?, String>?
        get() = website.trim().takeIf { it.isNotEmpty() }?.let { null to it }
            ?: socials.firstOrNull()?.let { (label, url) -> label to url }

    /**
     * A handle for the ~/ mark made from the name ("Jane Doe" → "janedoe"): lower-case letters and
     * digits, accents dropped. Empty when the name has none of those (say, written in another script).
     */
    val suggestedHandle: String
        get() = handleChars(name).filter { it in 'a'..'z' || it in '0'..'9' }.take(HANDLE_MAX)

    /** The default WhatsApp text: short and neutral, typed in but never sent for them. */
    val greeting: String get() = if (firstName.isEmpty()) "Hi" else "Hi $firstName"

    val hasWhatsapp: Boolean get() = whatsappUrl(whatsapp, "") != null

    /**
     * The site's root (https://example.com/), for the contact card; the website field may be a page.
     * Null when the "website" is really a LinkedIn, GitHub, Instagram or X profile: its root would
     * be just linkedin.com, so that link goes on the card whole instead (see [vcard]).
     */
    val siteRoot: String?
        get() = if (platformOf(website) != null) null else Regex("^https?://[^/?#]+").find(website.trim())?.value?.plus("/")

    /** The host event tags apply to, without www. Never a social network's. */
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
        for (address in listOf(email, email2).map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            lines += "EMAIL;TYPE=INTERNET:$address"
        }
        for ((label, number) in phones.filter { it.second.isNotBlank() }) {
            item++
            lines += "item$item.TEL;TYPE=CELL:${number.trim()}"
            if (label.isNotBlank()) lines += "item$item.X-ABLabel:${escape(label.trim())}"
        }
        // A social profile entered as the website goes on the card under its own name, once.
        val websiteSocial = platformOf(website)?.let { id -> PLATFORMS.getValue(id).first to website.trim() }
        val links = (listOfNotNull(siteRoot?.let { "Website" to it }, websiteSocial) + socials).distinctBy { it.second.trimEnd('/') }
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
        put("name", name); put("title", title); put("email", email); put("email.2", email2); put("website", website); put("handle", handle)
        put("linkedin", linkedin); put("github", github); put("instagram", instagram); put("x", x); put("whatsapp.number", whatsapp)
        phones.forEachIndexed { i, (label, number) -> put("phone.${i + 1}.label", label); put("phone.${i + 1}.number", number) }
        return StringWriter().also { props.store(it, null) }.toString()
    }

    /**
     * Puts a link where it belongs: a LinkedIn, GitHub, Instagram or X profile in its own field
     * (so the picker and contact card name it properly), anything else as the website. A bare
     * "example.com" gets https:// in front.
     */
    fun withLink(link: String): Profile {
        val url = normalizeUrl(link)
        return when (platformOf(url)) {
            Presets.LINKEDIN -> copy(linkedin = url)
            Presets.GITHUB -> copy(github = url)
            Presets.INSTAGRAM -> copy(instagram = url)
            Presets.X -> copy(x = url)
            else -> copy(website = url)
        }
    }

    companion object {
        /** The longest tilde handle (the name after ~/ on the Share screen). */
        const val HANDLE_MAX = 20

        /**
         * Text as tilde-handle characters: lower case, accents dropped, and only letters, digits,
         * dots, dashes and underscores kept ("João.Doe!" → "joao.doe"). No length limit, so it can
         * also clean what's typed as it's typed.
         */
        fun handleChars(text: String): String =
            java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
                .filter { it in 'a'..'z' || it in '0'..'9' || it in "._-" }

        /** A whole handle, cleaned and cut to [HANDLE_MAX]. */
        fun cleanHandle(text: String): String = handleChars(text.trim()).take(HANDLE_MAX)

        /** Social networks a link can belong to: preset id to (label, hosts). */
        private val PLATFORMS = mapOf(
            Presets.LINKEDIN to ("LinkedIn" to listOf("linkedin.com", "lnkd.in")),
            Presets.GITHUB to ("GitHub" to listOf("github.com")),
            Presets.INSTAGRAM to ("Instagram" to listOf("instagram.com", "instagr.am")),
            Presets.X to ("X" to listOf("x.com", "twitter.com")),
        )

        /** The preset id of the social network a link points to, or null for any other site. */
        fun platformOf(url: String): String? {
            val host = Regex("^(?:https?://)?([^/?#:]+)", RegexOption.IGNORE_CASE).find(url.trim())?.groupValues?.get(1)
                ?.lowercase() ?: return null
            return PLATFORMS.entries.firstOrNull { (_, value) -> value.second.any { host == it || host.endsWith(".$it") } }?.key
        }

        /** Adds https:// to a bare address; leaves full links and empty text alone. */
        fun normalizeUrl(link: String): String {
            val trimmed = link.trim()
            return if (trimmed.isEmpty() || trimmed.contains("://")) trimmed else "https://$trimmed"
        }

        /** Reads profile.local.properties text; unknown keys are ignored, missing ones are blank. */
        fun parse(text: String): Profile {
            val p = Properties().apply { load(StringReader(text)) }
            fun get(key: String) = p.getProperty(key, "").trim()
            val phones = generateSequence(1) { it + 1 }
                .map { get("phone.$it.label") to get("phone.$it.number") }
                .takeWhile { it.second.isNotEmpty() }
                .toList()
            return Profile(
                name = get("name"), title = get("title"), email = get("email"), email2 = get("email.2"), website = get("website"),
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
