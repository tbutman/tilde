package com.tbutman.tilde

import com.tbutman.tilde.Type4Tag.Companion.hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Type4TagTest {
    private val url = "https://tbutman.com/hello"

    @Test
    fun encodesTheUrlAsAShortUriRecord() {
        // D1: MB+ME+SR, TNF 1; type "U"; payload: 0x04 ("https://") + "tbutman.com/hello".
        val expected = hex("D1011255" + "04") + "tbutman.com/hello".toByteArray()
        assertArrayEquals(expected, Ndef.uriMessage(url))
    }

    @Test
    fun usesTheLongestMatchingPrefix() {
        assertEquals(2, Ndef.uriMessage("https://www.example.com")[4].toInt())
        assertEquals(0, Ndef.uriMessage("mailto:someone@example.com")[4].toInt())
    }

    @Test
    fun aReaderFollowingTheType4ProcedureGetsTheMessage() {
        val message = Ndef.uriMessage(url)
        var reads = 0
        val tag = Type4Tag(message) { reads++ }

        // NDEF Tag Application Select, then Capability Container Select and read.
        assertArrayEquals(Type4Tag.SW_OK, tag.process(hex("00A4040007D276000085010100")))
        assertArrayEquals(Type4Tag.SW_OK, tag.process(hex("00A4000C02E103")))
        val cc = tag.process(hex("00B000000F"))
        assertEquals(17, cc.size)
        assertArrayEquals(hex("000F20003B003404 06E104".replace(" ", "")), cc.copyOfRange(0, 11))
        assertArrayEquals(hex("00FF9000"), cc.copyOfRange(13, 17))

        // NDEF Select, read NLEN, then read the message.
        assertArrayEquals(Type4Tag.SW_OK, tag.process(hex("00A4000C02E104")))
        val nlen = tag.process(hex("00B0000002"))
        assertArrayEquals(byteArrayOf(0, message.size.toByte()) + Type4Tag.SW_OK, nlen)
        assertEquals(0, reads)
        val body = tag.process(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, message.size.toByte()))
        assertArrayEquals(message + Type4Tag.SW_OK, body)
        assertEquals(1, reads)
    }

    @Test
    fun longMessagesAreReadInChunksNoLargerThanMle() {
        val message = Ndef.uriMessage("https://tbutman.com/" + "x".repeat(150))
        val tag = Type4Tag(message)
        tag.process(hex("00A4040007D276000085010100"))
        tag.process(hex("00A4000C02E104"))
        val file = mutableListOf<Byte>()
        while (file.size < message.size + 2) {
            val offset = file.size
            val response = tag.process(byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), 0x00))
            assertArrayEquals(Type4Tag.SW_OK, response.copyOfRange(response.size - 2, response.size))
            val chunk = response.copyOfRange(0, response.size - 2)
            assert(chunk.size in 1..Type4Tag.MAX_READ)
            file += chunk.toList()
        }
        assertArrayEquals(message, file.drop(2).toByteArray())
    }

    @Test
    fun refusesFilesBeforeTheApplicationIsSelectedAndWrites() {
        val tag = Type4Tag(Ndef.uriMessage(url))
        assertArrayEquals(Type4Tag.SW_FILE_NOT_FOUND, tag.process(hex("00A4000C02E104")))
        assertArrayEquals(Type4Tag.SW_NO_CURRENT_FILE, tag.process(hex("00B0000002")))
        assertArrayEquals(Type4Tag.SW_FILE_NOT_FOUND, tag.process(hex("00A4040007A000000003101000")))
        tag.process(hex("00A4040007D276000085010100"))
        tag.process(hex("00A4000C02E104"))
        assertArrayEquals(Type4Tag.SW_SECURITY_NOT_SATISFIED, tag.process(hex("00D60000020000")))
        assertArrayEquals(Type4Tag.SW_INS_NOT_SUPPORTED, tag.process(hex("00CA000000")))
    }
}
