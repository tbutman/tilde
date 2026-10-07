package com.tbutman.tilde

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MetLogTest {
    private val t0 = 1_791_000_000_000L // 2026-10-03 04:00 UTC, 05:00 in Lisbon (summer time)

    @Test
    fun repeatedReadsOfOneTapAreOneEntry() {
        var log = MetLog.afterTap(emptyList(), t0, "lisbon-js", "Contact card")
        log = MetLog.afterTap(log, t0 + 2_000, "lisbon-js", "Contact card")
        assertEquals(1, log.size)
        log = MetLog.afterTap(log, t0 + MetLog.SAME_TAP_MS + 1, "lisbon-js", "Contact card")
        assertEquals(2, log.size)
        log = MetLog.afterTap(log, t0 + MetLog.SAME_TAP_MS + 2, "lisbon-js", "LinkedIn")
        assertEquals("a different share is a different person", 3, log.size)
    }

    @Test
    fun twoTapsTenSecondsApartAreTwoPeople() {
        // Two visitors tap the same card 10 s apart: two HCE sessions, two entries (and two notes).
        var log = MetLog.afterTap(emptyList(), t0, "websummit", "Contact card", "Work")
        log = MetLog.afterTap(log, t0 + 10_000, "websummit", "Contact card", "Work")
        assertEquals(2, log.size)
        // A phone that drops out of range and reads again a moment later is still one person.
        log = MetLog.afterTap(log, t0 + 13_000, "websummit", "Contact card", "Work")
        assertEquals(2, log.size)
    }

    @Test
    fun notesAttachToTheRightEntryAndEntriesCanBeRemoved() {
        var log = MetLog.afterTap(emptyList(), t0, "", "tbutman.com/hello")
        log = MetLog.afterTap(log, t0 + 60_000, "", "tbutman.com/hello")
        log = MetLog.withNote(log, t0, "Jane, React dev at Acme")
        assertEquals(listOf("", "Jane, React dev at Acme"), log.map { it.note })
        assertEquals(listOf(t0 + 60_000), MetLog.without(log, t0).map { it.time })
    }

    @Test
    fun moreThan500EntriesAreAllKept() {
        var log = emptyList<Meeting>()
        repeat(1_200) { log = MetLog.add(log, Meeting(t0 + it, "", "x", "")) }
        assertEquals(1_200, log.size)
        assertEquals(t0 + 1_199, log.first().time)
        assertEquals("the oldest is still there", t0, log.last().time)
        assertEquals(1_200, MetLog.fromJson(MetLog.toJson(log)).size)
    }

    @Test
    fun csvIsOldestFirstAndQuotesWhereNeeded() {
        val log = listOf(
            Meeting(t0 + 3_600_000, "lisbon-js", "Contact card", "Jane, \"React\" dev\nfollow up", "Work"),
            Meeting(t0, "", "tbutman.com/hello", "Bob"),
        )
        val csv = MetLog.csv(log, ZoneId.of("Europe/Lisbon"))
        // Entries from before cards have an empty card column.
        assertEquals(
            "date,card,event,shared,note\n" +
                "2026-10-03 05:00,,,tbutman.com/hello,Bob\n" +
                "2026-10-03 06:00,Work,lisbon-js,Contact card,\"Jane, \"\"React\"\" dev\nfollow up\"\n",
            csv,
        )
    }

    @Test
    fun aTapWithAnotherCardIsAnotherPerson() {
        var log = MetLog.afterTap(emptyList(), t0, "", "Website", "Work")
        log = MetLog.afterTap(log, t0 + 1_000, "", "Website", "Work")
        assertEquals(1, log.size)
        log = MetLog.afterTap(log, t0 + 2_000, "", "Website", "Personal")
        assertEquals(listOf("Personal", "Work"), log.map { it.card })
    }
}
