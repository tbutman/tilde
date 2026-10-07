package com.tbutman.tilde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BackupTest {
    private val jane = Profile(name = "Jane Doe", company = "Acme", phones = listOf("mobile" to "+1 555 0100"))
    private val work = Card("w1", "Work", "teal", jane, listOf(SavedLink("a1", "Tilde", "https://tbutman.com/tilde")), hidden = listOf("phones"))
    private val personal = Card("p2", "Personal", "amber", jane.copy(company = ""))
    private val backup = Backup(
        created = 1_791_331_200_000,
        cards = listOf(work, personal),
        activeCardId = "p2",
        photos = mapOf("w1" to byteArrayOf(1, 2, 3, -1)),
        met = listOf(Meeting(1_791_331_200_000, "web-summit", "Website", "Bob, payments", "Work")),
        enabled = false,
        event = "web-summit",
        eventDay = "2026-10-07",
        wifiSsid = "Jane Guest",
        wifiPassword = "correct horse",
        wifiOpen = false,
        settings = mapOf("full_brightness" to false, "met_keep_months" to 6),
    )

    @Test
    fun everythingRoundTrips() {
        assertEquals(backup, Backups.fromJson(Backups.toJson(backup)))
    }

    @Test
    fun theWifiPasswordIsOnlyThereWhenIncluded() {
        val without = backup.copy(wifiPassword = null)
        val json = Backups.toJson(without)
        assertFalse(json.contains("correct horse"))
        assertNull(Backups.fromJson(json).wifiPassword)
        assertEquals("Jane Guest", Backups.fromJson(json).wifiSsid)
    }

    @Test
    fun otherFilesAreRefusedClearly() {
        assertThrows(Backups.Invalid::class.java) { Backups.fromJson("hello") }
        assertThrows(Backups.Invalid::class.java) { Backups.fromJson("""{"kind":"something-else"}""") }
        assertThrows(Backups.Invalid::class.java) { Backups.fromJson("""{"kind":"tilde-backup","version":99,"cards":[]}""") }
        assertThrows(Backups.Invalid::class.java) { Backups.fromJson("""{"kind":"tilde-backup","version":1,"cards":[]}""") }
    }

    @Test
    fun aDamagedOrHandEditedFileCantStoreBadData() {
        val json = org.json.JSONObject(Backups.toJson(backup))
        // Settings: unknown keys and wrong types are dropped, so the app never reads a value it can't use.
        json.put("settings", org.json.JSONObject("""{"theme":true,"met_keep_months":"6","cards":"x","vibrate":false,"full_brightness":"yes"}"""))
        assertEquals(mapOf("vibrate" to false), Backups.fromJson(json.toString()).settings)
        // A photo that isn't base64.
        val badPhoto = org.json.JSONObject(Backups.toJson(backup)).put("photos", org.json.JSONObject("""{"w1":"not base64!"}"""))
        assertEquals(Backups.Reason.DAMAGED, assertThrows(Backups.Invalid::class.java) { Backups.fromJson(badPhoto.toString()) }.reason)
        // Cut short (a half-copied file): it says it's a backup, so it's damaged, not "not a backup".
        // (Android writes "kind" first; the JVM's org.json may not, so cut after it either way.)
        val truncated = Backups.toJson(backup).let { it.take(maxOf(it.length / 2, it.indexOf(Backups.KIND) + 20)) }
        assertEquals(Backups.Reason.DAMAGED, assertThrows(Backups.Invalid::class.java) { Backups.fromJson(truncated) }.reason)
        assertEquals(Backups.Reason.NOT_A_BACKUP, assertThrows(Backups.Invalid::class.java) { Backups.fromJson("{\"kind\": \"other\"") }.reason)
        // A card id that would be a path.
        val badId = Backups.toJson(backup.copy(cards = listOf(work.copy(id = "../x")), activeCardId = "../x", photos = emptyMap()))
        assertEquals(Backups.Reason.DAMAGED, assertThrows(Backups.Invalid::class.java) { Backups.fromJson(badId) }.reason)
    }

    @Test
    fun aMissingActiveCardFallsBackToTheFirst() {
        val json = Backups.toJson(backup.copy(activeCardId = "gone"))
        assertEquals("w1", Backups.fromJson(json).activeCardId)
        assertEquals("tilde-backup-2026-10-07.json", Backups.fileName(java.time.LocalDate.of(2026, 10, 7)))
    }

    @Test
    fun theEventTagExpiresTheNextDayUnlessKept() {
        assertTrue(EventTag.expired("2026-10-07", "2026-10-08", autoClear = true))
        assertFalse(EventTag.expired("2026-10-07", "2026-10-07", autoClear = true))
        assertFalse(EventTag.expired("2026-10-07", "2026-10-08", autoClear = false))
        assertFalse(EventTag.expired(null, "2026-10-08", autoClear = true))
        // Flying west after midnight: today is earlier than the day it was set.
        assertFalse(EventTag.expired("2026-10-08", "2026-10-07", autoClear = true))
    }

    @Test
    fun oldMetEntriesCanBeDropped() {
        val zone = ZoneId.of("Europe/Lisbon")
        val now = java.time.ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val day = 86_400_000L
        val log = listOf(Meeting(now - 10 * day, "", "x", ""), Meeting(now - 100 * day, "", "y", ""), Meeting(now - 400 * day, "", "z", ""))
        assertEquals(listOf("x"), MetLog.keepMonths(log, now, 3, zone).map { it.shared })
        assertEquals(listOf("x", "y"), MetLog.keepMonths(log, now, 12, zone).map { it.shared })
        assertEquals(log, MetLog.keepMonths(log, now, 0, zone))
    }
}
