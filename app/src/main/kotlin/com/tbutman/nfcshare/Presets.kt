package com.tbutman.nfcshare

import java.net.URLEncoder

/** What a tap can share. The links come from the Profile; event tags go on the profile's website only. */
object Presets {
    const val WEBSITE = "hello" // kept as "hello" so settings saved by earlier versions still match
    const val CONTACT = "contact"
    const val WHATSAPP = "whatsapp"
    const val LINKEDIN = "linkedin"
    const val GITHUB = "github"
    const val INSTAGRAM = "instagram"
    const val X = "x"
    const val CUSTOM = "custom"
    const val WIFI = "wifi"

    /**
     * `iphoneTap`: whether an iPhone acts on a tap. iPhones only act on links when they read a tag
     * in the background, so a contact card or Wi-Fi network reaches them by scanning the QR code.
     * Android phones handle every preset by tap or scan.
     */
    class Preset(val id: String, val label: String, val monogram: String, val iphoneTap: Boolean = true)

    val all = listOf(
        Preset(WEBSITE, "Website", "~/"),
        Preset(CONTACT, "Contact card", "+", iphoneTap = false),
        Preset(WHATSAPP, "WhatsApp", "wa"),
        Preset(LINKEDIN, "LinkedIn", "in"),
        Preset(GITHUB, "GitHub", "gh"),
        Preset(INSTAGRAM, "Instagram", "ig"),
        Preset(X, "X", "X"),
        Preset(CUSTOM, "Custom link", "↗"),
        Preset(WIFI, "Guest Wi-Fi", "wi", iphoneTap = false),
    )

    fun find(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    /** The profile's own link for a preset, or null when the profile doesn't have one. */
    fun profileUrl(id: String, profile: Profile): String? = when (id) {
        WEBSITE -> profile.website
        LINKEDIN -> profile.linkedin
        GITHUB -> profile.github
        INSTAGRAM -> profile.instagram
        X -> profile.x
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** What the picker offers: only what this profile can actually share. */
    fun available(profile: Profile): List<Preset> = all.filter {
        when (it.id) {
            CONTACT -> profile.isSet
            WHATSAPP -> profile.hasWhatsapp
            CUSTOM, WIFI -> true
            else -> profileUrl(it.id, profile) != null
        }
    }

    /**
     * Adds `event=<tag>` to links on the profile's own site (`siteHost`), so its access log shows
     * which event a visit came from. Other sites are left alone.
     */
    fun withEvent(url: String, event: String, siteHost: String?): String {
        val tag = event.trim()
        if (tag.isEmpty() || siteHost.isNullOrEmpty()) return url
        val host = Regex("^https?://([^/?#]+)").find(url)?.groupValues?.get(1)?.lowercase()?.removePrefix("www.") ?: return url
        if (host != siteHost) return url
        val separator = if ('?' in url) '&' else '?'
        return "$url${separator}event=${URLEncoder.encode(tag, "UTF-8")}"
    }
}
