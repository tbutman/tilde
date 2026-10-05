package com.tbutman.nfcshare

/**
 * What Receive mode read from someone else's tag or phone, decoded from raw NDEF bytes. Plain
 * Kotlin so the decoding runs in JVM unit tests.
 */
class Received(val kind: String, val title: String, val detail: String, val payload: String) {
    companion object {
        const val LINK = "link"
        const val CONTACT = "contact"
        const val WIFI = "wifi"
        const val TEXT = "text"
        const val OTHER = "other"

        // NFC Forum URI RTD identifier codes 0x00-0x23.
        private val URI_PREFIXES = listOf(
            "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
            "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://",
            "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:",
            "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://",
            "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:",
            "urn:nfc:",
        )

        /** One entry per record worth showing; the first is what the sender meant most. */
        fun fromNdef(message: ByteArray): List<Received> = records(message).mapNotNull { (tnf, type, payload) ->
            val typeText = type.toString(Charsets.US_ASCII)
            when {
                tnf == 1 && typeText == "U" && payload.isNotEmpty() -> {
                    val prefix = URI_PREFIXES.getOrElse(payload[0].toInt() and 0xFF) { "" }
                    val uri = prefix + payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
                    Received(LINK, uri.removePrefix("https://").removePrefix("http://"), uri, uri)
                }
                tnf == 3 -> {
                    val uri = typeText
                    Received(LINK, uri, uri, uri)
                }
                tnf == 1 && typeText == "T" && payload.isNotEmpty() -> {
                    val utf16 = payload[0].toInt() and 0x80 != 0
                    val langLength = payload[0].toInt() and 0x3F
                    val text = payload.copyOfRange(1 + langLength, payload.size)
                        .toString(if (utf16) Charsets.UTF_16 else Charsets.UTF_8)
                    Received(TEXT, text.take(60), text, text)
                }
                tnf == 2 && typeText.lowercase() in setOf("text/vcard", "text/x-vcard") -> {
                    val card = payload.toString(Charsets.UTF_8)
                    val fields = VCard.parse(card)
                    Received(CONTACT, fields.name.ifEmpty { "Contact card" }, fields.summary(), card)
                }
                tnf == 2 && typeText.lowercase() == Wifi.MIME -> {
                    val (ssid, key) = Wifi.parse(payload) ?: return@mapNotNull null
                    Received(WIFI, "Wi-Fi: $ssid", if (key.isEmpty()) "Open network" else "Password: $key", key)
                }
                tnf == 0 -> null
                else -> Received(OTHER, "Unsupported record", "$typeText (${payload.size} bytes)", "")
            }
        }

        /** (TNF, type, payload) for each record; chunked records are skipped. */
        fun records(message: ByteArray): List<Triple<Int, ByteArray, ByteArray>> {
            val out = mutableListOf<Triple<Int, ByteArray, ByteArray>>()
            var i = 0
            while (i < message.size) {
                val header = message[i].toInt() and 0xFF
                val short = header and 0x10 != 0
                val hasId = header and 0x08 != 0
                val typeLength = message.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: break
                var j = i + 2
                val payloadLength = if (short) {
                    (message.getOrNull(j)?.toInt()?.and(0xFF) ?: break).also { j += 1 }
                } else {
                    if (j + 4 > message.size) break
                    ((0 until 4).fold(0) { n, k -> (n shl 8) or (message[j + k].toInt() and 0xFF) }).also { j += 4 }
                }
                val idLength = if (hasId) (message.getOrNull(j)?.toInt()?.and(0xFF) ?: break).also { j += 1 } else 0
                val end = j + typeLength + idLength + payloadLength
                if (end > message.size || payloadLength < 0) break
                val type = message.copyOfRange(j, j + typeLength)
                val payload = message.copyOfRange(j + typeLength + idLength, end)
                if (header and 0x20 == 0) out += Triple(header and 0x07, type, payload) // not a chunk
                i = end
                if (header and 0x40 != 0) break // message end
            }
            return out
        }
    }
}

/** Just enough vCard parsing to fill Android's "new contact" screen. */
object VCard {
    class Fields(
        val name: String,
        val title: String,
        val org: String,
        val emails: List<String>,
        val phones: List<String>,
        val urls: List<String>,
    ) {
        fun summary() = listOf(title, org, emails.firstOrNull() ?: "", phones.firstOrNull() ?: "")
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
    }

    fun parse(card: String): Fields {
        val lines = card.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").split("\n")
        val props = lines.mapNotNull { line ->
            val colon = line.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
            val name = line.substring(0, colon).substringBefore(';').substringAfter('.').uppercase()
            name to unescape(line.substring(colon + 1).trim())
        }
        fun all(key: String) = props.filter { it.first == key }.map { it.second }.filter { it.isNotEmpty() }
        val name = all("FN").firstOrNull()
            ?: all("N").firstOrNull()?.split(';')?.let { listOf(it.getOrElse(1) { "" }, it[0]) }?.joinToString(" ")?.trim()
            ?: ""
        return Fields(
            name = name,
            title = all("TITLE").firstOrNull() ?: "",
            org = all("ORG").firstOrNull()?.substringBefore(';') ?: "",
            emails = all("EMAIL"),
            phones = all("TEL"),
            urls = all("URL"),
        )
    }

    private fun unescape(value: String) =
        value.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
