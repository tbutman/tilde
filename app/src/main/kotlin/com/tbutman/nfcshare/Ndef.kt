package com.tbutman.nfcshare

/** Encodes NDEF messages: URI records (NFC Forum URI RTD) and MIME records such as a vCard. */
object Ndef {
    private const val TNF_WELL_KNOWN = 0x01
    private const val TNF_MIME = 0x02

    // URI identifier codes, longest prefix first so "https://www." wins over "https://".
    private val prefixes = listOf(
        2 to "https://www.",
        1 to "http://www.",
        4 to "https://",
        3 to "http://",
    )

    class Record(val tnf: Int, val type: ByteArray, val payload: ByteArray)

    fun uriRecord(uri: String): Record {
        val (code, prefix) = prefixes.firstOrNull { uri.startsWith(it.second) } ?: (0 to "")
        val payload = byteArrayOf(code.toByte()) + uri.removePrefix(prefix).toByteArray(Charsets.UTF_8)
        return Record(TNF_WELL_KNOWN, byteArrayOf('U'.code.toByte()), payload)
    }

    fun mimeRecord(mimeType: String, payload: ByteArray) =
        Record(TNF_MIME, mimeType.toByteArray(Charsets.US_ASCII), payload)

    fun uriMessage(uri: String): ByteArray = message(listOf(uriRecord(uri)))

    /** Records in order; the first is what Android dispatches on. Short records when they fit. */
    fun message(records: List<Record>): ByteArray {
        require(records.isNotEmpty())
        return records.mapIndexed { i, record ->
            val short = record.payload.size <= 255
            var header = record.tnf
            if (i == 0) header = header or 0x80 // message begin
            if (i == records.lastIndex) header = header or 0x40 // message end
            if (short) header = header or 0x10 // short record: 1-byte payload length
            val length = if (short) {
                byteArrayOf(record.payload.size.toByte())
            } else {
                val n = record.payload.size
                byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
            }
            byteArrayOf(header.toByte(), record.type.size.toByte()) + length + record.type + record.payload
        }.reduce { a, b -> a + b }
    }
}
