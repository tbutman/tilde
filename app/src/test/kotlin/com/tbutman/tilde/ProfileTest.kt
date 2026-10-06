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
        // With nothing set up, nothing is ready to share (the picker shows the rest greyed out).
        assertEquals(emptyList<String>(), Presets.available(profile).map { it.id })
    }

    @Test
    fun thePickerOffersOnlyWhatTheProfileHas() {
        val ids = Presets.available(Profile.parse(seed)).map { it.id }
        assertEquals(listOf(Presets.WEBSITE, Presets.CONTACT, Presets.WHATSAPP, Presets.GITHUB), ids)
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

    @Test
    fun linksGoToTheRightField() {
        val empty = Profile(name = "Jane Doe")
        assertEquals("https://www.linkedin.com/in/janedoe", empty.withLink("www.linkedin.com/in/janedoe").linkedin)
        assertEquals("", empty.withLink("www.linkedin.com/in/janedoe").website)
        assertEquals("https://github.com/janedoe", empty.withLink("https://github.com/janedoe").github)
        assertEquals("https://instagram.com/jane", empty.withLink("instagram.com/jane").instagram)
        assertEquals("https://twitter.com/jane", empty.withLink("twitter.com/jane").x)
        assertEquals("https://example.com/hello", empty.withLink("example.com/hello").website)
        assertEquals("https://notlinkedin.com", empty.withLink("notlinkedin.com").website)
        assertEquals(Presets.LINKEDIN, Profile.platformOf("https://pt.linkedin.com/in/jane"))
        assertEquals(null, Profile.platformOf("https://example.com"))
    }

    @Test
    fun aSocialProfileAsTheWebsiteStaysWholeOnTheContactCard() {
        val profile = Profile(name = "Jane Doe", website = "https://www.linkedin.com/in/janedoe", linkedin = "https://www.linkedin.com/in/janedoe/")
        val card = profile.vcard()
        assertTrue(card.contains("URL:https://www.linkedin.com/in/janedoe"))
        assertFalse(card.contains("URL:https://www.linkedin.com/\r"))
        assertEquals(1, Regex("URL:").findAll(card).count())
        assertTrue(card.contains("X-ABLabel:LinkedIn"))
        // No event tags on someone else's site.
        assertEquals(null, profile.siteRoot)
        assertEquals(null, profile.siteHost)
    }

    @Test
    fun aSecondEmailGoesOnTheCardAndRoundTrips() {
        val profile = Profile(name = "Jane Doe", email = "jane@example.com", email2 = "jane@work.example")
        val card = profile.vcard()
        assertTrue(card.contains("EMAIL;TYPE=INTERNET:jane@example.com\r\n"))
        assertTrue(card.contains("EMAIL;TYPE=INTERNET:jane@work.example\r\n"))
        assertEquals(profile, Profile.parse(profile.toText()))
        assertEquals(1, Regex("EMAIL").findAll(profile.copy(email2 = "jane@example.com").vcard()).count())
    }

    @Test
    fun theSuggestedHandleIsTheNameInPlainLetters() {
        assertEquals("janedoe", Profile(name = "Jane Doe").suggestedHandle)
        assertEquals("joaoconceicao", Profile(name = "João Conceição").suggestedHandle)
        assertEquals("maryannobrien", Profile(name = "Mary-Ann O'Brien").suggestedHandle)
        assertEquals("", Profile(name = "李雷").suggestedHandle)
    }

    @Test
    fun aContactCardFallsBackToTheWebsiteThenASocialProfile() {
        val linkedin = "https://www.linkedin.com/in/janedoe"
        assertEquals(null to "https://example.com", Profile(name = "Jane", website = "https://example.com", linkedin = linkedin).contactLink)
        assertEquals("LinkedIn" to linkedin, Profile(name = "Jane", linkedin = linkedin).contactLink)
        assertEquals(null, Profile(name = "Jane").contactLink)
    }

    @Test
    fun handlesAreCleanedToSafeCharacters() {
        assertEquals("joao.doe", Profile.cleanHandle(" João.Doe! "))
        assertEquals("jane_doe-2", Profile.cleanHandle("Jane_Doe-2"))
        assertEquals("a".repeat(Profile.HANDLE_MAX), Profile.cleanHandle("a".repeat(30)))
        assertEquals("", Profile.cleanHandle("~/ "))
        // What's typed is cleaned without the length cut, so the field's own limit applies.
        assertEquals("ab", Profile.handleChars("A B"))
    }
}
