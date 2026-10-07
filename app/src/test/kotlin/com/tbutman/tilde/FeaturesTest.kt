package com.tbutman.tilde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    @Test
    fun eventTagsOnlyGoOnTbutmanLinks() {
        val site = Profile(website = "https://example.com/hello").siteHost
        assertEquals("example.com", site)
        assertEquals("https://example.com/hello?event=lisbon-js", Presets.withEvent("https://example.com/hello", "lisbon-js", site))
        assertEquals("https://www.example.com/x?a=1&event=web+summit", Presets.withEvent("https://www.example.com/x?a=1", " web summit ", site))
        assertEquals("https://github.com/janedoe", Presets.withEvent("https://github.com/janedoe", "lisbon-js", site))
        assertEquals("https://example.com.evil.test/", Presets.withEvent("https://example.com.evil.test/", "x", site))
        assertEquals("https://example.com/hello", Presets.withEvent("https://example.com/hello", "  ", site))
        assertEquals("https://example.com/hello", Presets.withEvent("https://example.com/hello", "x", null))
    }

    @Test
    fun wifiRecordsRoundTrip() {
        val record = Wifi.record("Home Guest", "correct horse", open = false)
        assertEquals(Wifi.MIME, record.type.toString(Charsets.US_ASCII))
        assertEquals("Home Guest" to "correct horse", Wifi.parse(record.payload))
        assertEquals("Cafe" to "", Wifi.parse(Wifi.record("Cafe", "ignored", open = true).payload))
    }

    @Test
    fun wifiQrCodesEscapeSpecialCharacters() {
        assertEquals("""WIFI:T:WPA;S:My\;Net;P:pa\:ss\\word;;""", Wifi.qrText("My;Net", """pa:ss\word""", open = false))
        assertEquals("WIFI:T:nopass;S:Cafe;;", Wifi.qrText("Cafe", "", open = true))
    }

    @Test
    fun receiveModeDecodesWhatShareModeSends() {
        val link = Received.fromNdef(Ndef.uriMessage("https://tbutman.com/hello"))
        assertEquals(1, link.size)
        assertEquals(Received.LINK, link[0].kind)
        assertEquals("https://tbutman.com/hello", link[0].payload)

        val profile = Profile(name = "Jane Doe", phones = listOf("mobile (US)" to "+1 555 0100"))
        val contact = Received.fromNdef(
            Ndef.message(listOf(Ndef.mimeRecord("text/vcard", profile.vcard().toByteArray()), Ndef.uriRecord("https://example.com/hello"))),
        )
        assertEquals(listOf(Received.CONTACT, Received.LINK), contact.map { it.kind })
        assertEquals("Jane Doe", contact[0].title)
        assertTrue("+1 555 0100" in contact[0].detail)

        val wifi = Received.fromNdef(Ndef.message(listOf(Wifi.record("Home Guest", "correct horse", open = false))))
        assertEquals("Home Guest", wifi.single().title)
        assertEquals("correct horse", wifi.single().payload)
    }

    @Test
    fun receiveModeHandlesOtherTagsToo() {
        // A text record (lang "en"), a tel: URI (prefix code 5) and a record type this app ignores.
        val text = Ndef.Record(1, "T".toByteArray(), byteArrayOf(2) + "en".toByteArray() + "hello there".toByteArray())
        val tel = Ndef.Record(1, "U".toByteArray(), byteArrayOf(5) + "+15550100".toByteArray())
        val other = Ndef.mimeRecord("application/x-example", byteArrayOf(1, 2, 3))
        val items = Received.fromNdef(Ndef.message(listOf(text, tel, other)))
        assertEquals(listOf(Received.TEXT, Received.LINK, Received.OTHER), items.map { it.kind })
        assertEquals("hello there", items[0].payload)
        assertEquals("tel:+15550100", items[1].payload)
    }

    @Test
    fun vcardParsingHandlesFoldedLinesAndEscapes() {
        val card = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Doe;Jane;;;\r\nORG:Acme\\, Inc.;R&D\r\nTEL;TYPE=CELL:+1 555\r\n 0100\r\nEND:VCARD\r\n"
        val fields = VCard.parse(card)
        assertEquals("Jane Doe", fields.name)
        assertEquals("Acme, Inc.", fields.org)
        assertEquals(listOf("+1 5550100"), fields.phones)
    }

    @Test
    fun whatsappLinksUseDigitsOnlyAndAPrefilledGreeting() {
        // A made-up number: real ones live only in the profile on the phone.
        assertEquals("https://wa.me/351900000000?text=Hi%20Jane", Profile.whatsappUrl("+351 900 000 000", "Hi Jane"))
        assertEquals("https://wa.me/351900000000?text=Ol%C3%A1%2C%20Jane%21", Profile.whatsappUrl("+351 900 000 000", "Olá, Jane!"))
        assertEquals("https://wa.me/351900000000", Profile.whatsappUrl("+351 900 000 000", "  "))
        assertEquals(null, Profile.whatsappUrl("", "Hi"))
        assertEquals(null, Profile.whatsappUrl("+351", "Hi"))
    }
}
