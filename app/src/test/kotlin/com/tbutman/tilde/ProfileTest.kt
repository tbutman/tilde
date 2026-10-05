package com.tbutman.tilde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    private val seed = """
        # comment lines and unknown keys are ignored
        name=Jane Doe
        title=Product designer
        email=jane@example.com
        website=https://www.example.com/hello
        handle=janedoe
        github=https://github.com/janedoe
        phone.1.label=mobile
        phone.1.number=+1 555 0100
        phone.2.label=work
        phone.2.number=+1 555 0199
        whatsapp.number=+1 555 0100
        colour=blue
    """.trimIndent()

    @Test
    fun parsesTheSeedFileAndRoundTrips() {
        val profile = Profile.parse(seed)
        assertEquals("Jane Doe", profile.name)
        assertEquals("Jane", profile.firstName)
        assertEquals("Doe", profile.lastName)
        assertEquals(listOf("mobile" to "+1 555 0100", "work" to "+1 555 0199"), profile.phones)
        assertEquals("https://www.example.com/", profile.siteRoot)
        assertEquals("example.com", profile.siteHost)
        assertEquals("Hi Jane", profile.greeting)
        assertEquals(profile, Profile.parse(profile.toText()))
    }

    @Test
    fun anEmptySeedIsAnEmptyProfile() {
        val profile = Profile.parse("")
        assertFalse(profile.isSet)
        assertEquals("Hi", profile.greeting)
        assertEquals(null, profile.siteHost)
        // With nothing set up, only the options that need no profile are offered.
        assertEquals(listOf(Presets.CUSTOM, Presets.WIFI), Presets.available(profile).map { it.id })
    }

    @Test
    fun thePickerOffersOnlyWhatTheProfileHas() {
        val ids = Presets.available(Profile.parse(seed)).map { it.id }
        assertEquals(listOf(Presets.WEBSITE, Presets.CONTACT, Presets.WHATSAPP, Presets.GITHUB, Presets.CUSTOM, Presets.WIFI), ids)
    }

    @Test
    fun theContactCardLeavesOutWhatIsMissing() {
        val card = Profile(name = "Prince").vcard()
        assertEquals("BEGIN:VCARD\r\nVERSION:3.0\r\nN:;Prince;;;\r\nFN:Prince\r\nEND:VCARD\r\n", card)
    }

    @Test
    fun theContactCardEscapesAndLabels() {
        val card = Profile(name = "Jane Doe", title = "Design, research; strategy", phones = listOf("" to "+1 555 0100")).vcard()
        assertTrue("TITLE:Design\\, research\\; strategy" in card)
        assertTrue("item1.TEL;TYPE=CELL:+1 555 0100\r\nEND:VCARD" in card) // no label line for an unlabelled number
    }
}
