package com.tbutman.nfcshare

/**
 * A read-only NFC Forum Type 4 Tag (mapping version 2.0) holding one NDEF message.
 *
 * A reader talks to it in ISO 7816-4 APDUs: select the NDEF application, select and read the
 * capability container, then select and read the NDEF file (a 2-byte length, then the message).
 * Plain Kotlin with no Android classes, so the whole exchange runs in JVM unit tests.
 */
class Type4Tag(ndefMessage: ByteArray, private val onMessageRead: () -> Unit = {}) {
    private val ndefFile = u16(ndefMessage.size) + ndefMessage

    private val capabilityContainer = u16(15) + // CCLEN
        byteArrayOf(0x20) + // mapping version 2.0
        u16(MAX_READ) + // MLe: most bytes a READ BINARY response may carry
        u16(0x34) + // MLc: most bytes an UPDATE BINARY may carry (unused: read-only)
        byteArrayOf(0x04, 0x06) + // NDEF File Control TLV
        NDEF_FILE_ID +
        u16(ndefFile.size) + // maximum NDEF file size
        byteArrayOf(0x00, 0xFF.toByte()) // read access granted, write access denied

    private var applicationSelected = false
    private var selectedFile: ByteArray? = null

    init {
        require(ndefMessage.size <= 0xFFFE - 2) { "NDEF message too large for a Type 4 Tag" }
    }

    fun process(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_WRONG_LENGTH
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        return when (apdu[1]) {
            INS_SELECT -> select(p1, apdu)
            INS_READ_BINARY -> readBinary(p1, p2, apdu)
            INS_UPDATE_BINARY -> SW_SECURITY_NOT_SATISFIED
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(p1: Int, apdu: ByteArray): ByteArray {
        if (apdu.size < 5) return SW_WRONG_LENGTH
        val length = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + length) return SW_WRONG_LENGTH
        val data = apdu.copyOfRange(5, 5 + length)
        return when {
            p1 == 0x04 && data.contentEquals(NDEF_APPLICATION_ID) -> {
                applicationSelected = true
                selectedFile = null
                SW_OK
            }
            p1 == 0x04 -> {
                applicationSelected = false
                selectedFile = null
                SW_FILE_NOT_FOUND
            }
            p1 == 0x00 && applicationSelected && data.contentEquals(CC_FILE_ID) -> {
                selectedFile = capabilityContainer
                SW_OK
            }
            p1 == 0x00 && applicationSelected && data.contentEquals(NDEF_FILE_ID) -> {
                selectedFile = ndefFile
                SW_OK
            }
            else -> SW_FILE_NOT_FOUND
        }
    }

    private fun readBinary(p1: Int, p2: Int, apdu: ByteArray): ByteArray {
        val file = selectedFile ?: return SW_NO_CURRENT_FILE
        val offset = (p1 shl 8) or p2
        if (offset > file.size) return SW_WRONG_OFFSET
        // Le of 0 means 256 in a short APDU; a missing Le asks for as much as fits.
        val requested = if (apdu.size > 4) (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        val end = minOf(offset + minOf(requested, MAX_READ), file.size)
        // Count a read when the reader reaches the end of the message itself.
        if (file === ndefFile && end == file.size && end > 2) onMessageRead()
        return file.copyOfRange(offset, end) + SW_OK
    }

    companion object {
        const val MAX_READ = 0x3B

        private const val INS_SELECT: Byte = 0xA4.toByte()
        private const val INS_READ_BINARY: Byte = 0xB0.toByte()
        private const val INS_UPDATE_BINARY: Byte = 0xD6.toByte()

        val NDEF_APPLICATION_ID = hex("D2760000850101")
        val CC_FILE_ID = hex("E103")
        val NDEF_FILE_ID = hex("E104")

        val SW_OK = hex("9000")
        val SW_WRONG_LENGTH = hex("6700")
        val SW_SECURITY_NOT_SATISFIED = hex("6982")
        val SW_NO_CURRENT_FILE = hex("6986")
        val SW_FILE_NOT_FOUND = hex("6A82")
        val SW_WRONG_OFFSET = hex("6B00")
        val SW_INS_NOT_SUPPORTED = hex("6D00")

        fun hex(value: String): ByteArray =
            value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
    }
}
