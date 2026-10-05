package com.tbutman.tilde

import com.tbutman.tilde.Type4Tag.Companion.hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefTest {
    // A made-up person: the real profile lives only on the phone and in profile.local.properties.
    private val profile = Profile(
        name = "Jane Doe", title = "Product designer", email = "jane@example.com",
        website = "https://example.com/hello", instagram = "https://www.instagram.com/janedoe/",
        phones = listOf("mobile (US)" to "+1 555 0100", "mobile (Portugal)" to "+351 900 000 000"),
    )

    @Test
    fun aContactMessageIsALongVcardRecordThenTheLink() {
        val vcard = profile.vcard().toByteArray()
        assertTrue("the card needs a long record", vcard.size > 255)
        val message = Ndef.message(listOf(Ndef.mimeRecord("text/vcard", vcard), Ndef.uriRecord("https://tbutman.com/hello")))

        // First record: MB, not ME, not SR, TNF 2 (MIME); type length; 4-byte payload length.
        assertEquals(0x82, message[0].toInt() and 0xFF)
        assertEquals("text/vcard".length, message[1].toInt())
        val length = (0 until 4).fold(0) { n, i -> (n shl 8) or (message[2 + i].toInt() and 0xFF) }
        assertEquals(vcard.size, length)
        assertEquals("text/vcard", String(message, 6, 10))
        assertArrayEquals(vcard, message.copyOfRange(16, 16 + vcard.size))

        // Second record: ME and SR, TNF 1, the same URI record as link mode.
        val second = message.copyOfRange(16 + vcard.size, message.size)
        assertEquals(0x51, second[0].toInt() and 0xFF)
        assertArrayEquals(Ndef.uriMessage("https://tbutman.com/hello").copyOfRange(1, 22), second.copyOfRange(1, 22))
    }

    @Test
    fun theVcardCarriesLabelledNumbersAndLinks() {
        val card = profile.vcard()
        assertTrue(card.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(card.endsWith("END:VCARD\r\n"))
        assertTrue("item1.TEL;TYPE=CELL:+1 555 0100\r\nitem1.X-ABLabel:mobile (US)" in card)
        assertTrue("item2.TEL;TYPE=CELL:+351 900 000 000\r\nitem2.X-ABLabel:mobile (Portugal)" in card)
        assertTrue("item3.URL:https://example.com/\r\nitem3.X-ABLabel:Website" in card)
        assertTrue("item4.URL:https://www.instagram.com/janedoe/\r\nitem4.X-ABLabel:Instagram" in card)
        assertTrue("N:Doe;Jane;;;\r\nFN:Jane Doe\r\nTITLE:Product designer" in card)
        assertTrue("EMAIL;TYPE=INTERNET:jane@example.com" in card)
    }

    @Test
    fun theCompactCardKeepsOnlyTheWebsiteLink() {
        val card = profile.vcard(compact = true)
        assertTrue("item3.URL:https://example.com/" in card)
        assertTrue("instagram" !in card && "linkedin" !in card)
        assertTrue("+351 900 000 000" in card)
    }

    @Test
    fun aReaderGetsTheWholeContactMessageInChunks() {
        val message = Ndef.message(listOf(Ndef.mimeRecord("text/vcard", profile.vcard().toByteArray()), Ndef.uriRecord("https://tbutman.com/hello")))
        var reads = 0
        val tag = Type4Tag(message) { reads++ }
        tag.process(hex("00A4040007D276000085010100"))
        tag.process(hex("00A4000C02E104"))
        val file = mutableListOf<Byte>()
        while (file.size < message.size + 2) {
            val offset = file.size
            val response = tag.process(byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), 0x00))
            file += response.copyOfRange(0, response.size - 2).toList()
        }
        assertEquals(message.size, ((file[0].toInt() and 0xFF) shl 8) or (file[1].toInt() and 0xFF))
        assertArrayEquals(message, file.drop(2).toByteArray())
        assertEquals(1, reads)
    }
}
