package com.tbutman.nfcshare

import android.content.Context
import android.content.SharedPreferences

/** The few settings the app keeps, shared by the screen and the card-emulation service. */
class Prefs(context: Context) {
    val store: SharedPreferences = context.getSharedPreferences("nfc-share", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.edit().putBoolean(KEY_ENABLED, value).apply()

    var url: String
        get() = store.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = store.edit().putString(KEY_URL, value).apply()

    /** "link" shares the URL; "contact" shares the contact card, then the URL. */
    var mode: String
        get() = store.getString(KEY_MODE, MODE_LINK) ?: MODE_LINK
        set(value) = store.edit().putString(KEY_MODE, value).apply()

    /** The NDEF message a tap reads in the current mode. */
    fun message(): ByteArray = when (mode) {
        // Android dispatches on the first record, so the card comes first; the link is a fallback
        // for readers that only act on URLs.
        MODE_CONTACT -> Ndef.message(
            listOf(Ndef.mimeRecord("text/vcard", Contact.vcard().toByteArray(Charsets.UTF_8)), Ndef.uriRecord(url)),
        )
        else -> Ndef.uriMessage(url)
    }

    /** What the on-screen QR code encodes, for phones without NFC: a compact card, or the link. */
    fun qrText(): String = if (mode == MODE_CONTACT) Contact.vcard(compact = true) else url

    /** How many times a reader has read the whole message from this phone. */
    var reads: Int
        get() = store.getInt(KEY_READS, 0)
        set(value) = store.edit().putInt(KEY_READS, value).apply()

    companion object {
        const val DEFAULT_URL = "https://tbutman.com/hello"
        const val KEY_ENABLED = "enabled"
        const val KEY_URL = "url"
        const val KEY_READS = "reads"
        const val KEY_MODE = "mode"
        const val MODE_LINK = "link"
        const val MODE_CONTACT = "contact"
    }
}
