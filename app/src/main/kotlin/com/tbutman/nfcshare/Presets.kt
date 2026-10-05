package com.tbutman.nfcshare

import java.net.URLEncoder

/** What a tap can share. Links carry an optional event tag when they point at tbutman.com. */
object Presets {
    const val HELLO = "hello"
    const val CONTACT = "contact"
    const val CUSTOM = "custom"
    const val WIFI = "wifi"
    const val WHATSAPP = "whatsapp"

    class Preset(val id: String, val label: String, val url: String? = null)

    val all = listOf(
        Preset(HELLO, "tbutman.com/hello", Prefs.DEFAULT_URL),
        Preset(CONTACT, "Contact card"),
        Preset(WHATSAPP, "WhatsApp"),
        Preset("linkedin", "LinkedIn", "https://www.linkedin.com/in/thomasbutman"),
        Preset("github", "GitHub", "https://github.com/tbutman"),
        Preset("instagram", "Instagram", "https://www.instagram.com/t.butman/"),
        Preset("x", "X", "https://x.com/tbutman"),
        Preset(CUSTOM, "Custom link"),
        Preset(WIFI, "Guest Wi-Fi"),
    )

    /** What the screen offers: WhatsApp only when a number is configured. */
    val available: List<Preset>
        get() = all.filter { it.id != WHATSAPP || Contact.hasWhatsapp }

    fun find(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    /**
     * Adds `event=<tag>` to links on tbutman.com, so the site's own access log shows which event a
     * visit came from. Other sites are left alone.
     */
    fun withEvent(url: String, event: String): String {
        val tag = event.trim()
        if (tag.isEmpty() || !Regex("^https?://(www\\.)?tbutman\\.com(/|$)").containsMatchIn(url)) return url
        val separator = if ('?' in url) '&' else '?'
        return "$url${separator}event=${URLEncoder.encode(tag, "UTF-8")}"
    }
}
