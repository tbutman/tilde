package com.tbutman.nfcshare

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The app's settings, shared by the screen, the card-emulation service and the tile. */
class Prefs(context: Context) {
    val store: SharedPreferences = context.getSharedPreferences("nfc-share", Context.MODE_PRIVATE)

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
            ?: if (store.getString("mode", null) == "contact") Presets.CONTACT else Presets.HELLO // 1.1 setting
        set(value) = store.edit().putString(KEY_SHARE, value).apply()

    /** The custom link, used when `share` is CUSTOM. */
    var customUrl: String
        get() = store.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = store.edit().putString(KEY_URL, value).apply()

    /** Optional tag added to tbutman.com links as ?event=, e.g. the meetup's name. */
    var event: String
        get() = store.getString(KEY_EVENT, "") ?: ""
        set(value) = store.edit().putString(KEY_EVENT, value).apply()

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

    /** The link a tap carries in link and contact modes, with the event tag applied. */
    val url: String
        get() {
            val preset = Presets.find(share)
            val base = when (preset.id) {
                Presets.CUSTOM -> customUrl
                Presets.WHATSAPP -> Contact.whatsappUrl ?: DEFAULT_URL
                Presets.CONTACT, Presets.WIFI -> DEFAULT_URL
                else -> preset.url ?: DEFAULT_URL
            }
            return Presets.withEvent(base, event)
        }

    val wifiReady: Boolean
        get() = wifiSsid.isNotBlank() && (wifiOpen || wifiPassword.length >= 8)

    /** The NDEF message a tap reads. */
    fun message(): ByteArray = when (share) {
        // Android dispatches on the first record, so the card comes first; the link is a fallback
        // for readers that only act on URLs.
        Presets.CONTACT -> Ndef.message(
            listOf(Ndef.mimeRecord("text/vcard", Contact.vcard().toByteArray(Charsets.UTF_8)), Ndef.uriRecord(url)),
        )
        Presets.WIFI -> Ndef.message(listOf(Wifi.record(wifiSsid, wifiPassword, wifiOpen)))
        else -> Ndef.uriMessage(url)
    }

    /** What the on-screen QR code encodes, for phones without NFC. */
    fun qrText(): String = when (share) {
        Presets.CONTACT -> Contact.vcard(compact = true)
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
        const val DEFAULT_URL = "https://tbutman.com/hello"
        const val TAB_SHARE = "share"
        const val TAB_RECEIVE = "receive"
        const val TAB_MET = "met"
        const val HISTORY_SIZE = 20

        const val KEY_ENABLED = "enabled"
        const val KEY_TAB = "tab"
        const val KEY_SHARE = "share"
        const val KEY_URL = "url"
        const val KEY_EVENT = "event"
        const val KEY_WIFI_SSID = "wifi_ssid"
        const val KEY_WIFI_PASSWORD = "wifi_password"
        const val KEY_WIFI_OPEN = "wifi_open"
        const val KEY_READS = "reads"
        const val KEY_RECEIVED = "received"
        const val KEY_MET = "met"
    }
}
