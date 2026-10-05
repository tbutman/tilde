package com.tbutman.nfcshare

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
    fun notesAttachToTheRightEntryAndEntriesCanBeRemoved() {
        var log = MetLog.afterTap(emptyList(), t0, "", "tbutman.com/hello")
        log = MetLog.afterTap(log, t0 + 60_000, "", "tbutman.com/hello")
        log = MetLog.withNote(log, t0, "Jane, React dev at Acme")
        assertEquals(listOf("", "Jane, React dev at Acme"), log.map { it.note })
        assertEquals(listOf(t0 + 60_000), MetLog.without(log, t0).map { it.time })
    }

    @Test
    fun theLogIsCapped() {
        var log = emptyList<Meeting>()
        repeat(MetLog.MAX_ENTRIES + 5) { log = MetLog.add(log, Meeting(t0 + it, "", "x", "")) }
        assertEquals(MetLog.MAX_ENTRIES, log.size)
        assertEquals(t0 + MetLog.MAX_ENTRIES + 4, log.first().time)
    }

    @Test
    fun csvIsOldestFirstAndQuotesWhereNeeded() {
        val log = listOf(
            Meeting(t0 + 3_600_000, "lisbon-js", "Contact card", "Jane, \"React\" dev\nfollow up"),
            Meeting(t0, "", "tbutman.com/hello", "Bob"),
        )
        val csv = MetLog.csv(log, ZoneId.of("Europe/Lisbon"))
        assertEquals(
            "date,event,shared,note\n" +
                "2026-10-03 05:00,,tbutman.com/hello,Bob\n" +
                "2026-10-03 06:00,lisbon-js,Contact card,\"Jane, \"\"React\"\" dev\nfollow up\"\n",
            csv,
        )
    }
}
