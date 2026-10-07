package com.tbutman.tilde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagWriterTest {
    @Test
    fun writesWhenTheMessageFits() {
        assertNull(TagWriter.check(size = 20, capacity = 496, writable = true))
        assertNull(TagWriter.check(size = 496, capacity = 496, writable = true))
    }

    @Test
    fun refusesLockedTagsAndPhones() {
        assertEquals(TagWriter.Result.Locked, TagWriter.check(size = 20, capacity = 496, writable = false))
    }

    @Test
    fun lockingOnlyWhenAskedAndSaysWhenItCant() {
        assertEquals(TagWriter.Result.Written, TagWriter.locked(lockAsked = false, lockWorked = false))
        assertEquals(TagWriter.Result.WrittenLocked, TagWriter.locked(lockAsked = true, lockWorked = true))
        assertEquals(TagWriter.Result.WrittenNotLockable, TagWriter.locked(lockAsked = true, lockWorked = false))
        assertEquals(true, TagWriter.wrote(TagWriter.Result.WrittenNotLockable))
        assertEquals(false, TagWriter.wrote(TagWriter.Result.Failed))
    }

    @Test
    fun saysHowMuchRoomIsMissing() {
        // A contact card on an NTAG213, which has 137 bytes for the message.
        assertEquals(TagWriter.Result.TooSmall(needed = 300, capacity = 137), TagWriter.check(size = 300, capacity = 137, writable = true))
    }
}
