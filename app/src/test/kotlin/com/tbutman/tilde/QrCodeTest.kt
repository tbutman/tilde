package com.tbutman.tilde

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodeTest {
    @Test
    fun contentOverCapacityGivesNoCodeInsteadOfCrashing() {
        // About 2.3 kB fits at level M; a backup restored with very long values can go past it.
        assertNull(QrCode.matrix("https://example.com/" + "a".repeat(3_000)))
        assertNotNull(QrCode.matrix("https://example.com/" + "a".repeat(1_000)))
    }

    @Test
    fun aFullContactCardAtTheFieldLimitsStillFits() {
        val long = "x".repeat(100)
        val profile = Profile(
            name = long, title = long, company = long, email = "jane@example.com", email2 = "jane.doe@example.org",
            website = "https://example.com/" + "w".repeat(480), phones = List(3) { "mobile" to "+351 912 345 67$it" },
        )
        assertNotNull(QrCode.matrix(profile.vcard(compact = true)))
    }
}
