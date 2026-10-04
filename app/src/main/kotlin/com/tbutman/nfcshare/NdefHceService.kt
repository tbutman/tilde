package com.tbutman.nfcshare

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Host Card Emulation: while the screen is on, Android routes readers that select the NDEF
 * application (AID D2760000850101, see res/xml/apduservice.xml) to this service, and the phone
 * answers as a Type 4 Tag holding the URL.
 */
class NdefHceService : HostApduService() {
    private var tag: Type4Tag? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val prefs = Prefs(this)
        if (!prefs.enabled) return Type4Tag.SW_FILE_NOT_FOUND
        // A fresh tag per tap, so a URL edited in the app applies from the next tap.
        val current = tag ?: Type4Tag(Ndef.uriMessage(prefs.url)) { prefs.reads += 1 }.also { tag = it }
        return current.process(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        tag = null
    }
}
