package com.tbutman.tilde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {
    @Test
    fun theTableHasEveryRegionOnceWithItsCode() {
        assertEquals(245, Countries.all.size)
        assertEquals(Countries.all.size, Countries.all.map { it.iso }.distinct().size)
        assertTrue(Countries.all.all { it.iso.length == 2 && it.dial.isNotEmpty() && it.dial.all(Char::isDigit) })
        assertEquals("351", Countries.find("pt")?.dial)
        assertEquals("1", Countries.find("US")?.dial)
        assertEquals("44", Countries.find("GB")?.dial)
        assertEquals("55", Countries.find("BR")?.dial)
        assertNull(Countries.find("ZZ"))
    }

    @Test
    fun theDefaultIsTheFirstKnownCountry() {
        assertEquals("PT", Countries.default("pt", "us", "US").iso)
        assertEquals("ES", Countries.default("", null, "ES").iso)
        assertEquals("US", Countries.default(null, "", "").iso)
    }

    @Test
    fun flagsAreRegionalIndicators() {
        assertEquals("🇵🇹", Countries.find("PT")!!.flag)
    }

    @Test
    fun searchMatchesNamesCodesAndDiallingCodes() {
        val pt = Countries.find("PT")!!
        assertTrue(Countries.matches(pt, "Portugal", "port"))
        assertTrue(Countries.matches(pt, "Portugal", "pt"))
        assertTrue(Countries.matches(pt, "Portugal", "351"))
        assertTrue(Countries.matches(pt, "Portugal", "+351"))
        assertTrue(Countries.matches(pt, "Portugal", ""))
        assertFalse(Countries.matches(pt, "Portugal", "spain"))
        val us = Countries.find("US")!!
        assertTrue(Countries.matches(us, "United States", "states"))
    }

    @Test
    fun labelsNameTheCountryOnlyWhenNumbersDiffer() {
        assertEquals(listOf("mobile"), Countries.phoneLabels(listOf("PT")))
        assertEquals(listOf("mobile", "mobile"), Countries.phoneLabels(listOf("PT", "PT")))
        assertEquals(listOf("mobile (US)", "mobile (PT)"), Countries.phoneLabels(listOf("US", "PT")))
    }

    @Test
    fun internationalFallbackAddsTheCodeUnlessOneWasTyped() {
        val pt = Countries.find("PT")!!
        assertEquals("+351 912 345 678", Countries.international(pt, " 912 345 678 "))
        assertEquals("+1 555 0100", Countries.international(pt, "+1 555 0100"))
        assertEquals("", Countries.international(pt, "  "))
    }

    @Test
    fun savedNumbersSplitIntoCountryAndNumber() {
        assertEquals("PT" to "912 345 678", Countries.split("+351 912 345 678").let { it.first?.iso to it.second })
        assertEquals("US" to "202-555-0143", Countries.split("+1 202-555-0143").let { it.first?.iso to it.second })
        // +1 is shared: the phone's own country wins when it uses that code.
        assertEquals("CA", Countries.split("+1 416 555 0100", home = "ca").first?.iso)
        assertEquals("GB" to "20 7946 0000", Countries.split("+44 20 7946 0000").let { it.first?.iso to it.second })
        assertEquals("PT" to "912345678", Countries.split("+351912345678").let { it.first?.iso to it.second })
        // No +: no country, the number as it was.
        assertEquals(null to "912 345 678", Countries.split(" 912 345 678 ").let { it.first?.iso to it.second })
    }

    @Test
    fun typedLabelsStayAndTildesOwnAreRedone() {
        assertTrue(Countries.isAutoLabel("mobile"))
        assertTrue(Countries.isAutoLabel("mobile (US)"))
        assertFalse(Countries.isAutoLabel("work"))
        assertEquals(listOf("work", "mobile"), Countries.labelsKeeping(listOf("work", ""), listOf("PT", "PT")))
        assertEquals(listOf("mobile (US)", "mobile (PT)"), Countries.labelsKeeping(listOf("mobile", "mobile"), listOf("US", "PT")))
        assertEquals(listOf("mobile"), Countries.labelsKeeping(emptyList(), listOf("PT")))
    }
}
