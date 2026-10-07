package com.tbutman.tilde

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGateTest {
    private fun answers(enabled: Boolean = true, receiving: Boolean = false, open: Boolean = false, whenClosed: Boolean = false, locked: Boolean = false) =
        TapGate.answers(enabled, receiving, open, whenClosed, locked)

    @Test
    fun withTildeOpenItAnswersUnlessPausedOrReceiving() {
        assertTrue(answers(open = true))
        assertTrue(answers(open = true, locked = true))
        assertFalse(answers(open = true, enabled = false))
        assertFalse(answers(open = true, receiving = true))
    }

    @Test
    fun withTildeClosedItOnlyAnswersWhenAllowedAndUnlocked() {
        // The default: closed means no answer, so nothing is shared from a pocket.
        assertFalse(answers())
        assertTrue(answers(whenClosed = true))
        assertFalse(answers(whenClosed = true, locked = true))
        assertFalse(answers(whenClosed = true, enabled = false))
    }
}
