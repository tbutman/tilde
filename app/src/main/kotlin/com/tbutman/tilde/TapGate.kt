package com.tbutman.tilde

/**
 * Whether the tap service answers a reader. Android routes NFC readers to Tilde whenever the screen
 * is on, even with Tilde closed, so the rule is Tilde's own: sharing must be on, Receive (which
 * reads instead) must not be on screen, and either a Tilde screen is open or the owner allowed answering
 * while closed (Settings → Sharing), in which case the phone must also be unlocked. Unit-tested.
 */
object TapGate {
    fun answers(enabled: Boolean, receiving: Boolean, tildeOpen: Boolean, answerWhenClosed: Boolean, locked: Boolean): Boolean = when {
        // Receive only matters while it's on screen: the tab is remembered after Tilde closes.
        !enabled || (receiving && tildeOpen) -> false
        tildeOpen -> true
        else -> answerWhenClosed && !locked
    }
}
