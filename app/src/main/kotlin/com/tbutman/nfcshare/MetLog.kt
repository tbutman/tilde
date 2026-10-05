package com.tbutman.nfcshare

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One person met: when, at which event, what they got, and the owner's note. Stored only on the phone. */
data class Meeting(val time: Long, val event: String, val shared: String, val note: String)

/** The "Met" log: who the owner shared with, for following up. Plain Kotlin, so it is unit-tested. */
object MetLog {
    /** A phone often reads a tag more than once per tap; reads this close together are one person. */
    const val SAME_TAP_MS = 30_000L
    const val MAX_ENTRIES = 500
    const val RECEIVED = "received their contact"

    /** Newest first. Adds an entry for a completed read unless it repeats the latest one. */
    fun afterTap(log: List<Meeting>, now: Long, event: String, shared: String): List<Meeting> {
        val last = log.firstOrNull()
        if (last != null && now - last.time < SAME_TAP_MS && last.event == event && last.shared == shared) return log
        return add(log, Meeting(now, event, shared, ""))
    }

    fun add(log: List<Meeting>, meeting: Meeting) = (listOf(meeting) + log).take(MAX_ENTRIES)

    fun withNote(log: List<Meeting>, time: Long, note: String) = log.map { if (it.time == time) it.copy(note = note) else it }

    fun without(log: List<Meeting>, time: Long) = log.filterNot { it.time == time }

    /** CSV, oldest first, for pasting into a spreadsheet or CRM. */
    fun csv(log: List<Meeting>, zone: ZoneId = ZoneId.systemDefault()): String {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
        val rows = log.reversed().map { m ->
            listOf(format.format(Instant.ofEpochMilli(m.time)), m.event, m.shared, m.note).joinToString(",") { field(it) }
        }
        return (listOf("date,event,shared,note") + rows).joinToString("\n") + "\n"
    }

    private fun field(value: String) =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
