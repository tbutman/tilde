package com.tbutman.tilde

import java.net.URLEncoder

/**
 * What a tap can share: the fixed options (links from the Profile, the contact card, Wi-Fi) plus
 * each saved link. Event names go on links to the profile's own website only.
 */
object Presets {
    const val WEBSITE = "hello" // kept as "hello" so settings saved by earlier versions still match
    const val CONTACT = "contact"
    const val WHATSAPP = "whatsapp"
    const val LINKEDIN = "linkedin"
    const val GITHUB = "github"
    const val INSTAGRAM = "instagram"
    const val X = "x"
    /** Version 1.0's single custom link; now migrated to a saved link (see [SavedLink.migrate]). */
    const val CUSTOM = "custom"
    const val WIFI = "wifi"

    /**
     * `iphoneTap`: whether an iPhone acts on a tap. iPhones only act on links when they read a tag
     * in the background, so a contact card or Wi-Fi network reaches them by scanning the QR code.
     * Android phones handle every preset by tap or scan.
     */
    class Preset(val id: String, val label: String, val monogram: String, val iphoneTap: Boolean = true, val labelRes: Int = 0)

    val all = listOf(
        Preset(WEBSITE, "Website", "~/", labelRes = R.string.preset_website),
        Preset(CONTACT, "Contact card", "+", iphoneTap = false, labelRes = R.string.preset_contact),
        Preset(WHATSAPP, "WhatsApp", "wa"),
        Preset(LINKEDIN, "LinkedIn", "in"),
        Preset(GITHUB, "GitHub", "gh"),
        Preset(INSTAGRAM, "Instagram", "ig"),
        Preset(X, "X", "X"),
        Preset(WIFI, "Guest Wi-Fi", "wi", iphoneTap = false, labelRes = R.string.preset_wifi),
    )

    /** Every option, in the picker's order: the fixed ones, then the saved links, then Guest Wi-Fi last. */
    fun options(links: List<SavedLink>): List<Preset> =
        all.filter { it.id != WIFI } + links.map { Preset(it.presetId, it.name, monogramFor(it.name)) } + all.filter { it.id == WIFI }

    /** The option with this id, or the first one when it's gone (say, a deleted link). */
    fun find(id: String, links: List<SavedLink> = emptyList()) = options(links).firstOrNull { it.id == id } ?: all.first()

    /** A saved link's badge: its name's first letter or digit. */
    fun monogramFor(name: String) = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "↗"

    /** Whether an option has what it needs to share; the picker greys out the ones that don't. */
    fun isReady(id: String, profile: Profile, links: List<SavedLink>, wifiReady: Boolean): Boolean = when {
        SavedLink.idOf(id) != null -> links.any { it.presetId == id && it.url.isNotBlank() }
        id == CONTACT -> profile.isSet
        id == WHATSAPP -> profile.hasWhatsapp
        id == WIFI -> wifiReady
        else -> profileUrl(id, profile) != null
    }

    /** The profile's own link for a preset, or null when the profile doesn't have one. */
    fun profileUrl(id: String, profile: Profile): String? = when (id) {
        WEBSITE -> profile.website
        LINKEDIN -> profile.linkedin
        GITHUB -> profile.github
        INSTAGRAM -> profile.instagram
        X -> profile.x
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** The options ready to share right now, in order. */
    fun available(profile: Profile, links: List<SavedLink> = emptyList(), wifiReady: Boolean = false): List<Preset> =
        options(links).filter { isReady(it.id, profile, links, wifiReady) }

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

/** An option's name in the app's language: the fixed options' names are translated, link names are the owner's own. */
fun android.content.Context.labelOf(preset: Presets.Preset): String =
    if (preset.labelRes != 0) getString(preset.labelRes) else preset.label
