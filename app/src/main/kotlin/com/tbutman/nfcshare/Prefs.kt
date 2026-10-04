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

    /** How many times a reader has read the whole message from this phone. */
    var reads: Int
        get() = store.getInt(KEY_READS, 0)
        set(value) = store.edit().putInt(KEY_READS, value).apply()

    companion object {
        const val DEFAULT_URL = "https://tbutman.com/hello"
        const val KEY_ENABLED = "enabled"
        const val KEY_URL = "url"
        const val KEY_READS = "reads"
    }
}
