package com.tbutman.tilde

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One person met: when, at which event, what they got, the owner's note, and which of the owner's
 * cards was shared (its label; empty before cards existed). Stored only on the phone.
 */
data class Meeting(val time: Long, val event: String, val shared: String, val note: String, val card: String = "")

/** The "Met" log: who the owner shared with, for following up. Plain Kotlin, so it is unit-tested. */
object MetLog {
    /**
     * A phone often reads a tag more than once per tap. Within one tap (one card-emulation session)
     * the service logs the first read only; a new session this soon after, sharing the same thing,
     * is the same phone coming back into range rather than the next person.
     */
    const val SAME_TAP_MS = 4_000L
    // What `shared` holds for entries that weren't a tap; the Met screen shows them in the app's language.
    const val RECEIVED = "received their contact"
    const val MANUAL = "added by hand"

    /** Newest first. Adds an entry for a tap's first completed read, unless it repeats the latest entry within [SAME_TAP_MS]. */
    fun afterTap(log: List<Meeting>, now: Long, event: String, shared: String, card: String = ""): List<Meeting> {
        val last = log.firstOrNull()
        if (last != null && now - last.time < SAME_TAP_MS && last.event == event && last.shared == shared && last.card == card) return log
        return add(log, Meeting(now, event, shared, "", card))
    }

    /** Newest first, with no limit: only "Delete entries older than" (or the owner) removes anyone. */
    fun add(log: List<Meeting>, meeting: Meeting) = listOf(meeting) + log

    fun withNote(log: List<Meeting>, time: Long, note: String) = log.map { if (it.time == time) it.copy(note = note) else it }

    fun without(log: List<Meeting>, time: Long) = log.filterNot { it.time == time }

    /**
     * Keeps the entries from the last `months` months (0: keep everything), for the "Delete entries
     * older than" setting.
     */
    fun keepMonths(log: List<Meeting>, now: Long, months: Int, zone: ZoneId = ZoneId.systemDefault()): List<Meeting> {
        if (months <= 0) return log
        val cutoff = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).minusMonths(months.toLong()).toInstant().toEpochMilli()
        return log.filter { it.time >= cutoff }
    }

    fun toJson(log: List<Meeting>): String = JSONArray().apply {
        log.forEach { m -> put(JSONObject().put("time", m.time).put("event", m.event).put("shared", m.shared).put("note", m.note).put("card", m.card)) }
    }.toString()

    fun fromJson(json: String?): List<Meeting> = runCatching {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Meeting(o.getLong("time"), o.optString("event"), o.optString("shared"), o.optString("note"), o.optString("card"))
        }
    }.getOrDefault(emptyList())

    /** CSV, oldest first, for pasting into a spreadsheet or CRM. */
    fun csv(log: List<Meeting>, zone: ZoneId = ZoneId.systemDefault(), shared: (String) -> String = { it }): String {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
        val rows = log.reversed().map { m ->
            listOf(format.format(Instant.ofEpochMilli(m.time)), m.card, m.event, shared(m.shared), m.note).joinToString(",") { field(it) }
        }
        return (listOf("date,card,event,shared,note") + rows).joinToString("\n") + "\n"
    }

    private fun field(value: String) =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
