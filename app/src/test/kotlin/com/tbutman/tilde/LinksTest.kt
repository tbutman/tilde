package com.tbutman.tilde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    private val tilde = SavedLink("a1", "Tilde", "https://tbutman.com/tilde")
    private val labs = SavedLink("b2", "LabTrails", "https://labtrails.app")

    @Test
    fun savedLinksAreOptionsBeforeWifi() {
        val ids = Presets.options(listOf(tilde, labs)).map { it.id }
        assertEquals(listOf("link:a1", "link:b2", Presets.WIFI), ids.takeLast(3))
        assertEquals(Presets.WEBSITE, ids.first())
        assertEquals("Tilde", Presets.find("link:a1", listOf(tilde)).label)
        assertEquals("T", Presets.find("link:a1", listOf(tilde)).monogram)
        // A deleted link falls back to the first option rather than failing.
        assertEquals(Presets.WEBSITE, Presets.find("link:gone", listOf(tilde)).id)
    }

    @Test
    fun readinessFollowsWhatEachOptionNeeds() {
        val profile = Profile(name = "Jane Doe", website = "https://example.com")
        assertTrue(Presets.isReady("link:a1", profile, listOf(tilde), wifiReady = false))
        assertFalse(Presets.isReady("link:zz", profile, listOf(tilde), wifiReady = false))
        assertTrue(Presets.isReady(Presets.CONTACT, profile, emptyList(), wifiReady = false))
        assertFalse(Presets.isReady(Presets.LINKEDIN, profile, emptyList(), wifiReady = false))
        assertFalse(Presets.isReady(Presets.WIFI, profile, emptyList(), wifiReady = false))
        assertTrue(Presets.isReady(Presets.WIFI, profile, emptyList(), wifiReady = true))
        assertEquals(listOf(Presets.WEBSITE, Presets.CONTACT, "link:a1"), Presets.available(profile, listOf(tilde)).map { it.id })
    }

    @Test
    fun linksRoundTripThroughJson() {
        val links = listOf(tilde, labs)
        assertEquals(links, SavedLink.fromJson(SavedLink.toJson(links)))
        assertEquals(emptyList<SavedLink>(), SavedLink.fromJson("not json"))
        assertEquals(emptyList<SavedLink>(), SavedLink.fromJson(null))
    }

    @Test
    fun theOldCustomLinkBecomesTheFirstSavedLink() {
        val (links, share) = SavedLink.migrate(" https://www.labtrails.app/ ", Presets.CUSTOM, "c3")
        assertEquals(listOf(SavedLink("c3", "labtrails.app", "https://www.labtrails.app/")), links)
        assertEquals("link:c3", share)
        assertEquals(Presets.CONTACT, SavedLink.migrate("https://x.test", Presets.CONTACT, "c3").second)
        // An empty custom link that was selected falls back to the default.
        assertEquals(emptyList<SavedLink>() to null, SavedLink.migrate("", Presets.CUSTOM, "c3"))
    }

    @Test
    fun linksAreCheckedAndCompleted() {
        assertEquals("https://labtrails.app", SavedLink.validUrl(" labtrails.app "))
        assertEquals("https://tbutman.com/tilde", SavedLink.validUrl("https://tbutman.com/tilde"))
        assertEquals("mailto:jane@example.com", SavedLink.validUrl("mailto:jane@example.com"))
        assertNull(SavedLink.validUrl("labtrails"))
        assertNull(SavedLink.validUrl("two words.com"))
        assertNull(SavedLink.validUrl(""))
        assertNull(SavedLink.validUrl("https://.com"))
    }

    @Test
    fun idsAndNames() {
        assertEquals("a1", SavedLink.idOf("link:a1"))
        assertNull(SavedLink.idOf(Presets.WEBSITE))
        assertEquals("labtrails.app/demo", SavedLink.defaultName("https://www.labtrails.app/demo/"))
        var n = 0
        assertEquals("000002", SavedLink.newId(listOf("000001")) { ++n })
    }
}
