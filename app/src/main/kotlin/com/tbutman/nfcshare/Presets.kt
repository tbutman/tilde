package com.tbutman.nfcshare

import java.net.URLEncoder

/** What a tap can share. Links carry an optional event tag when they point at tbutman.com. */
object Presets {
    const val HELLO = "hello"
    const val CONTACT = "contact"
    const val CUSTOM = "custom"
    const val WIFI = "wifi"
    const val WHATSAPP = "whatsapp"

    /**
     * `iphoneTap`: whether an iPhone acts on a tap. iPhones only act on links when they read a tag
     * in the background, so a contact card or Wi-Fi network reaches them by scanning the QR code.
     * Android phones handle every preset by tap or scan.
     */
    class Preset(val id: String, val label: String, val url: String? = null, val monogram: String, val iphoneTap: Boolean = true)

    val all = listOf(
        Preset(HELLO, "tbutman.com/hello", Prefs.DEFAULT_URL, "~/"),
        Preset(CONTACT, "Contact card", monogram = "+", iphoneTap = false),
        Preset(WHATSAPP, "WhatsApp", monogram = "wa"),
        Preset("linkedin", "LinkedIn", "https://www.linkedin.com/in/thomasbutman", "in"),
        Preset("github", "GitHub", "https://github.com/tbutman", "gh"),
        Preset("instagram", "Instagram", "https://www.instagram.com/t.butman/", "ig"),
        Preset("x", "X", "https://x.com/tbutman", "X"),
        Preset(CUSTOM, "Custom link", monogram = "↗"),
        Preset(WIFI, "Guest Wi-Fi", monogram = "wi", iphoneTap = false),
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
