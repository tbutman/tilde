package com.tbutman.nfcshare

/** Encodes a one-record NDEF message holding a URI (NFC Forum URI Record Type Definition). */
object Ndef {
    // URI identifier codes, longest prefix first so "https://www." wins over "https://".
    private val prefixes = listOf(
        2 to "https://www.",
        1 to "http://www.",
        4 to "https://",
        3 to "http://",
    )

    fun uriMessage(uri: String): ByteArray {
        val (code, prefix) = prefixes.firstOrNull { uri.startsWith(it.second) } ?: (0 to "")
        val payload = byteArrayOf(code.toByte()) + uri.removePrefix(prefix).toByteArray(Charsets.UTF_8)
        require(payload.size <= 255) { "URI too long for a short NDEF record" }
        return byteArrayOf(
            0xD1.toByte(), // message begin, message end, short record, TNF well-known
            0x01, // type length
            payload.size.toByte(),
            'U'.code.toByte(),
        ) + payload
    }
}
