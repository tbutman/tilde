package com.tbutman.tilde

import org.json.JSONObject
import java.util.Base64

/**
 * Everything worth keeping, in one file the owner saves where they like: Tilde has no cloud backup,
 * so this is how cards, photos and the Met list reach a new phone. Receive history and the read
 * count are left out. The guest Wi-Fi password is only included when they ask for it.
 * Plain Kotlin apart from org.json, so the format is unit-tested.
 */
data class Backup(
    val created: Long,
    val cards: List<Card>,
    val activeCardId: String,
    /** Each card's photo (JPEG bytes), by card id. */
    val photos: Map<String, ByteArray>,
    val met: List<Meeting>,
    val enabled: Boolean,
    val event: String,
    val wifiSsid: String,
    val wifiPassword: String?,
    val wifiOpen: Boolean,
    /** The switches in Settings (brightness, vibration, Met clean-up…), by their storage key. */
    val settings: Map<String, Any>,
) {
    // ByteArrays compare by identity; compare photos by content so a round trip is equal.
    override fun equals(other: Any?): Boolean = other is Backup &&
        created == other.created && cards == other.cards && activeCardId == other.activeCardId && met == other.met &&
        enabled == other.enabled && event == other.event && wifiSsid == other.wifiSsid &&
        wifiPassword == other.wifiPassword && wifiOpen == other.wifiOpen && settings == other.settings &&
        photos.keys == other.photos.keys && photos.all { (id, bytes) -> bytes.contentEquals(other.photos[id]) }

    override fun hashCode(): Int = listOf(created, cards, activeCardId, met, event).hashCode()
}

object Backups {
    const val KIND = "tilde-backup"
    const val VERSION = 1

    /** Why a file couldn't be restored, worded for the person. */
    class Invalid(message: String) : Exception(message)

    fun toJson(backup: Backup): String = JSONObject().apply {
        put("kind", KIND)
        put("version", VERSION)
        put("created", backup.created)
        put("cards", org.json.JSONArray(Cards.toJson(backup.cards)))
        put("activeCard", backup.activeCardId)
        put("photos", JSONObject().apply { backup.photos.forEach { (id, bytes) -> put(id, Base64.getEncoder().encodeToString(bytes)) } })
        put("met", org.json.JSONArray(MetLog.toJson(backup.met)))
        put("enabled", backup.enabled)
        put("event", backup.event)
        put("wifi", JSONObject().apply {
            put("ssid", backup.wifiSsid)
            backup.wifiPassword?.let { put("password", it) }
            put("open", backup.wifiOpen)
        })
        put("settings", JSONObject(backup.settings))
    }.toString(2)

    /** Reads a backup file; throws [Invalid] for anything that isn't one Tilde can restore. */
    fun fromJson(json: String): Backup {
        val o = runCatching { JSONObject(json) }.getOrElse { throw Invalid("That file isn't a Tilde backup.") }
        if (o.optString("kind") != KIND) throw Invalid("That file isn't a Tilde backup.")
        if (o.optInt("version") > VERSION) throw Invalid("That backup is from a newer version of Tilde. Update Tilde first.")
        val cards = Cards.fromJson(o.optJSONArray("cards")?.toString())
        if (cards.isEmpty()) throw Invalid("That backup has no cards in it.")
        val photos = o.optJSONObject("photos")?.let { p -> p.keys().asSequence().associateWith { Base64.getDecoder().decode(p.getString(it)) } }.orEmpty()
        val wifi = o.optJSONObject("wifi") ?: JSONObject()
        val settings = o.optJSONObject("settings")?.let { s -> s.keys().asSequence().associateWith { s.get(it) } }.orEmpty()
        return Backup(
            created = o.optLong("created"),
            cards = cards,
            activeCardId = o.optString("activeCard").takeIf { id -> cards.any { it.id == id } } ?: cards.first().id,
            photos = photos.filterKeys { id -> cards.any { it.id == id } },
            met = MetLog.fromJson(o.optJSONArray("met")?.toString()),
            enabled = o.optBoolean("enabled", true),
            event = o.optString("event"),
            wifiSsid = wifi.optString("ssid"),
            wifiPassword = wifi.optString("password").takeIf { wifi.has("password") },
            wifiOpen = wifi.optBoolean("open"),
            settings = settings,
        )
    }

    /** "tilde-backup-2026-10-07.json" */
    fun fileName(date: java.time.LocalDate) = "tilde-backup-$date.json"
}

/** The event tag clears itself at the end of the day it was set, unless that's switched off. */
object EventTag {
    fun expired(setOn: String?, today: String, autoClear: Boolean) = autoClear && setOn != null && setOn != today
}
