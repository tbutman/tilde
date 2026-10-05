package com.tbutman.nfcshare

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Host Card Emulation: while the screen is on, Android routes readers that select the NDEF
 * application (AID D2760000850101, see res/xml/apduservice.xml) to this service, and the phone
 * answers as a Type 4 Tag holding what the app is set to share (see Prefs.message).
 */
class NdefHceService : HostApduService() {
    private var tag: Type4Tag? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val prefs = Prefs(this)
        if (!prefs.enabled || prefs.tab == Prefs.TAB_RECEIVE) return Type4Tag.SW_FILE_NOT_FOUND
        if (prefs.share == Presets.WIFI && !prefs.wifiReady) return Type4Tag.SW_FILE_NOT_FOUND
        if (prefs.share != Presets.WIFI && prefs.share != Presets.CONTACT && prefs.url.isEmpty()) return Type4Tag.SW_FILE_NOT_FOUND
        if (prefs.share == Presets.CONTACT && !prefs.profile.isSet) return Type4Tag.SW_FILE_NOT_FOUND
        // A fresh tag per tap, so a mode or URL changed in the app applies from the next tap.
        val current = tag ?: Type4Tag(prefs.message()) {
            // Log who got what before bumping the count, so the screen's "Sent" banner finds the entry.
            prefs.met = MetLog.afterTap(prefs.met, System.currentTimeMillis(), prefs.event, Presets.find(prefs.share).label)
            prefs.reads += 1
        }.also { tag = it }
        return current.process(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        tag = null
    }
}
