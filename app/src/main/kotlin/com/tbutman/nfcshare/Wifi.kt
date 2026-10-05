package com.tbutman.nfcshare

/**
 * Guest Wi-Fi as an NFC record (the Wi-Fi Alliance's WSC credential, which Android offers to join)
 * and as the WIFI: QR format that phone cameras, iPhones included, recognise.
 */
object Wifi {
    const val MIME = "application/vnd.wfa.wsc"

    private const val CREDENTIAL = 0x100E
    private const val NETWORK_INDEX = 0x1026
    private const val SSID = 0x1045
    private const val AUTH_TYPE = 0x1003
    private const val ENCRYPTION_TYPE = 0x100F
    private const val NETWORK_KEY = 0x1027
    private const val MAC_ADDRESS = 0x1020

    private const val AUTH_OPEN = 0x0001
    private const val AUTH_WPA2_PERSONAL = 0x0020
    private const val ENCRYPTION_NONE = 0x0001
    private const val ENCRYPTION_AES = 0x0008

    fun record(ssid: String, password: String, open: Boolean): Ndef.Record {
        val credential = tlv(NETWORK_INDEX, byteArrayOf(1)) +
            tlv(SSID, ssid.toByteArray(Charsets.UTF_8)) +
            tlv(AUTH_TYPE, u16(if (open) AUTH_OPEN else AUTH_WPA2_PERSONAL)) +
            tlv(ENCRYPTION_TYPE, u16(if (open) ENCRYPTION_NONE else ENCRYPTION_AES)) +
            tlv(NETWORK_KEY, if (open) ByteArray(0) else password.toByteArray(Charsets.UTF_8)) +
            tlv(MAC_ADDRESS, ByteArray(6) { 0xFF.toByte() }) // broadcast: any access point
        return Ndef.mimeRecord(MIME, tlv(CREDENTIAL, credential))
    }

    /** `WIFI:T:WPA;S:<ssid>;P:<password>;;`, with \ ; , : " escaped. */
    fun qrText(ssid: String, password: String, open: Boolean): String {
        fun esc(s: String) = s.replace(Regex("""([\\;,:"])"""), """\\$1""")
        return if (open) "WIFI:T:nopass;S:${esc(ssid)};;" else "WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};;"
    }

    /** Reads the SSID and key back out of a WSC payload, for received tags. */
    fun parse(payload: ByteArray): Pair<String, String>? {
        val outer = tlvs(payload)[CREDENTIAL] ?: return null
        val fields = tlvs(outer)
        val ssid = fields[SSID]?.toString(Charsets.UTF_8) ?: return null
        return ssid to (fields[NETWORK_KEY]?.toString(Charsets.UTF_8) ?: "")
    }

    private fun tlvs(bytes: ByteArray): Map<Int, ByteArray> {
        val out = mutableMapOf<Int, ByteArray>()
        var i = 0
        while (i + 4 <= bytes.size) {
            val type = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            if (i + 4 + length > bytes.size) break
            out.putIfAbsent(type, bytes.copyOfRange(i + 4, i + 4 + length))
            i += 4 + length
        }
        return out
    }

    private fun tlv(type: Int, value: ByteArray) = u16(type) + u16(value.size) + value

    private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
}
