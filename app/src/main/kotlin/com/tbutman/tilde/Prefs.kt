package com.tbutman.tilde

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The app's settings, shared by the screen, the card-emulation service and the tile. */
class Prefs(context: Context) {
    val store: SharedPreferences = context.getSharedPreferences("tilde", Context.MODE_PRIVATE)

    /**
     * Whose card this is. The first launch seeds it from the build (profile.local.properties, empty
     * in a build without one); after that it lives only here and is edited in Settings.
     */
    var profile: Profile
        get() = store.getString(KEY_PROFILE, null)?.let { Profile.parse(it) }
            ?: Profile.parse(BuildConfig.PROFILE_SEED).also { profile = it }
        set(value) = store.edit().putString(KEY_PROFILE, value.toText()).apply()

    /** Bumped whenever the profile photo changes, so screens know to reload it. */
    var photoVersion: Int
        get() = store.getInt(KEY_PHOTO_VERSION, 0)
        set(value) = store.edit().putInt(KEY_PHOTO_VERSION, value).apply()

    /** Set once the welcome screen has created a card, so it never comes back. */
    var welcomed: Boolean
        get() = store.getBoolean(KEY_WELCOMED, false)
        set(value) = store.edit().putBoolean(KEY_WELCOMED, value).apply()

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.edit().putBoolean(KEY_ENABLED, value).apply()

    /** "share" and "met" answer taps as a tag; "receive" reads other tags and phones. */
    var tab: String
        get() = store.getString(KEY_TAB, TAB_SHARE) ?: TAB_SHARE
        set(value) = store.edit().putString(KEY_TAB, value).apply()

    /** Which preset a tap shares (see Presets). */
    var share: String
        get() = store.getString(KEY_SHARE, null)
            ?: if (store.getString("mode", null) == "contact") Presets.CONTACT else Presets.WEBSITE // 1.1 setting
        set(value) = store.edit().putString(KEY_SHARE, value).apply()

    /** The custom link, used when `share` is CUSTOM. */
    var customUrl: String
        get() = store.getString(KEY_URL, "") ?: ""
        set(value) = store.edit().putString(KEY_URL, value).apply()

    /** Optional tag added to links on the profile's website as ?event=, e.g. the meetup's name. */
    var event: String
        get() = store.getString(KEY_EVENT, "") ?: ""
        set(value) = store.edit().putString(KEY_EVENT, value).apply()

    /** Text typed into the WhatsApp chat for them to send or not; blank for an empty chat. */
    var whatsappGreeting: String
        get() = store.getString(KEY_WHATSAPP_GREETING, null) ?: profile.greeting
        set(value) = store.edit().putString(KEY_WHATSAPP_GREETING, value).apply()

    var wifiSsid: String
        get() = store.getString(KEY_WIFI_SSID, "") ?: ""
        set(value) = store.edit().putString(KEY_WIFI_SSID, value).apply()

    var wifiPassword: String
        get() = store.getString(KEY_WIFI_PASSWORD, "") ?: ""
        set(value) = store.edit().putString(KEY_WIFI_PASSWORD, value).apply()

    var wifiOpen: Boolean
        get() = store.getBoolean(KEY_WIFI_OPEN, false)
        set(value) = store.edit().putBoolean(KEY_WIFI_OPEN, value).apply()

    /** How many times a reader has read the whole message from this phone. */
    var reads: Int
        get() = store.getInt(KEY_READS, 0)
        set(value) = store.edit().putInt(KEY_READS, value).apply()

    /** The link a preset carries, with the event tag applied; "" when there's nothing to link to. */
    fun urlFor(id: String, profile: Profile = this.profile): String {
        val base = when (id) {
            Presets.CUSTOM -> customUrl
            Presets.WHATSAPP -> Profile.whatsappUrl(profile.whatsapp, whatsappGreeting) ?: ""
            // The contact card links to the website too, for readers that only act on links.
            Presets.CONTACT, Presets.WIFI -> profile.website
            else -> Presets.profileUrl(id, profile) ?: ""
        }.trim()
        return if (base.isEmpty()) "" else Presets.withEvent(base, event, profile.siteHost)
    }

    /** The link a tap carries for the current preset. */
    val url: String
        get() = urlFor(share)

    val wifiReady: Boolean
        get() = wifiSsid.isNotBlank() && (wifiOpen || wifiPassword.length >= 8)

    /** The NDEF message a tap reads. */
    fun message(): ByteArray = when (share) {
        // Android dispatches on the first record, so the card comes first; the link is a fallback
        // for readers that only act on URLs.
        Presets.CONTACT -> Ndef.message(
            listOfNotNull(
                Ndef.mimeRecord("text/vcard", profile.vcard().toByteArray(Charsets.UTF_8)),
                url.takeIf { it.isNotEmpty() }?.let { Ndef.uriRecord(it) },
            ),
        )
        Presets.WIFI -> Ndef.message(listOf(Wifi.record(wifiSsid, wifiPassword, wifiOpen)))
        else -> Ndef.uriMessage(url)
    }

    /** What the on-screen QR code encodes, for phones without NFC. */
    fun qrText(): String = when (share) {
        Presets.CONTACT -> profile.vcard(compact = true)
        Presets.WIFI -> Wifi.qrText(wifiSsid, wifiPassword, wifiOpen)
        else -> url
    }

    /** People met: one entry per completed tap or manual entry, newest first. Only on this phone. */
    var met: List<Meeting>
        get() = runCatching {
            val array = JSONArray(store.getString(KEY_MET, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Meeting(o.getLong("time"), o.optString("event"), o.optString("shared"), o.optString("note"))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = JSONArray()
            value.forEach { m -> array.put(JSONObject().put("time", m.time).put("event", m.event).put("shared", m.shared).put("note", m.note)) }
            store.edit().putString(KEY_MET, array.toString()).apply()
        }

    /** Things Receive mode has read, newest first; at most HISTORY_SIZE. */
    var received: List<Received>
        get() = runCatching {
            val array = JSONArray(store.getString(KEY_RECEIVED, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Received(o.getString("kind"), o.getString("title"), o.getString("detail"), o.getString("payload"))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = JSONArray()
            value.take(HISTORY_SIZE).forEach { r ->
                array.put(JSONObject().put("kind", r.kind).put("title", r.title).put("detail", r.detail).put("payload", r.payload))
            }
            store.edit().putString(KEY_RECEIVED, array.toString()).apply()
        }

    companion object {
        const val TAB_SHARE = "share"
        const val TAB_RECEIVE = "receive"
        const val TAB_MET = "met"
        const val TAB_SETTINGS = "settings"
        const val HISTORY_SIZE = 20

        const val KEY_PROFILE = "profile"
        const val KEY_PHOTO_VERSION = "photo_version"
        const val KEY_WELCOMED = "welcomed"
        const val KEY_ENABLED = "enabled"
        const val KEY_TAB = "tab"
        const val KEY_SHARE = "share"
        const val KEY_URL = "url"
        const val KEY_EVENT = "event"
        const val KEY_WHATSAPP_GREETING = "whatsapp_greeting"
        const val KEY_WIFI_SSID = "wifi_ssid"
        const val KEY_WIFI_PASSWORD = "wifi_password"
        const val KEY_WIFI_OPEN = "wifi_open"
        const val KEY_READS = "reads"
        const val KEY_RECEIVED = "received"
        const val KEY_MET = "met"
    }
}
