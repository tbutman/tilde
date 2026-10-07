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
    /** The day the event tag was set (yyyy-mm-dd), so it still clears itself at the end of that day. */
    val eventDay: String?,
    val wifiSsid: String,
    val wifiPassword: String?,
    val wifiOpen: Boolean,
    /** The switches in Settings (brightness, vibration, Met clean-up…), by their storage key. */
    val settings: Map<String, Any>,
) {
    // ByteArrays compare by identity; compare photos by content so a round trip is equal.
    override fun equals(other: Any?): Boolean = other is Backup &&
        created == other.created && cards == other.cards && activeCardId == other.activeCardId && met == other.met &&
        enabled == other.enabled && event == other.event && eventDay == other.eventDay && wifiSsid == other.wifiSsid &&
        wifiPassword == other.wifiPassword && wifiOpen == other.wifiOpen && settings == other.settings &&
        photos.keys == other.photos.keys && photos.all { (id, bytes) -> bytes.contentEquals(other.photos[id]) }

    override fun hashCode(): Int = listOf(created, cards, activeCardId, met, event).hashCode()
}

object Backups {
    const val KIND = "tilde-backup"
    const val VERSION = 1

    /** Why a file couldn't be restored; the screen words it in the app's language. */
    class Invalid(val reason: Reason) : Exception(reason.name)

    enum class Reason { NOT_A_BACKUP, TOO_NEW, NO_CARDS, DAMAGED }

    /**
     * The settings a backup may restore, with their types. Anything else in a file is ignored, so a
     * damaged or hand-edited backup can't store a value the app then can't read.
     */
    private val SETTINGS: Map<String, (Any) -> Any?> = mapOf(
        Prefs.KEY_EVENT_AUTO_CLEAR to { v -> v as? Boolean },
        Prefs.KEY_FULL_BRIGHTNESS to { v -> v as? Boolean },
        Prefs.KEY_KEEP_SCREEN_ON to { v -> v as? Boolean },
        Prefs.KEY_VIBRATE to { v -> v as? Boolean },
        Prefs.KEY_MET_ASK_NOTE to { v -> v as? Boolean },
        Prefs.KEY_SEND_PHOTO to { v -> v as? Boolean },
        Prefs.KEY_ANSWER_WHEN_CLOSED to { v -> v as? Boolean },
        Prefs.KEY_MET_KEEP_MONTHS to { v -> (v as? Int)?.takeIf { it in 0..120 } },
        Prefs.KEY_THEME to { v -> (v as? String)?.takeIf { it in setOf(Prefs.THEME_DARK, Prefs.THEME_LIGHT, Prefs.THEME_SYSTEM) } },
    )

    /** Card ids become photo file names, so only plain ids (letters, digits, - and _) are accepted. */
    private val CARD_ID = Regex("[0-9A-Za-z_-]{1,32}")

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
        backup.eventDay?.let { put("eventDay", it) }
        put("wifi", JSONObject().apply {
            put("ssid", backup.wifiSsid)
            backup.wifiPassword?.let { put("password", it) }
            put("open", backup.wifiOpen)
        })
        put("settings", JSONObject(backup.settings))
    }.toString(2)

    /** Reads a backup file; throws [Invalid] for anything that isn't one Tilde can restore. */
    fun fromJson(json: String): Backup {
        // A file that says it's a Tilde backup but doesn't parse was cut short or damaged.
        val o = runCatching { JSONObject(json) }.getOrElse {
            throw Invalid(if (Regex("\"kind\"\\s*:\\s*\"$KIND\"").containsMatchIn(json)) Reason.DAMAGED else Reason.NOT_A_BACKUP)
        }
        if (o.optString("kind") != KIND) throw Invalid(Reason.NOT_A_BACKUP)
        if (o.optInt("version") > VERSION) throw Invalid(Reason.TOO_NEW)
        val cards = Cards.fromJson(o.optJSONArray("cards")?.toString())
        if (cards.isEmpty()) throw Invalid(Reason.NO_CARDS)
        if (cards.any { !CARD_ID.matches(it.id) } || cards.map { it.id }.toSet().size != cards.size) throw Invalid(Reason.DAMAGED)
        val photos = try {
            o.optJSONObject("photos")?.let { p -> p.keys().asSequence().associateWith { Base64.getDecoder().decode(p.getString(it)) } }.orEmpty()
        } catch (_: Exception) {
            throw Invalid(Reason.DAMAGED)
        }
        val wifi = o.optJSONObject("wifi") ?: JSONObject()
        val settings = o.optJSONObject("settings")?.let { s ->
            s.keys().asSequence().mapNotNull { key -> SETTINGS[key]?.invoke(s.get(key))?.let { key to it } }.toMap()
        }.orEmpty()
        return Backup(
            created = o.optLong("created"),
            cards = cards,
            activeCardId = o.optString("activeCard").takeIf { id -> cards.any { it.id == id } } ?: cards.first().id,
            photos = photos.filterKeys { id -> cards.any { it.id == id } },
            met = MetLog.fromJson(o.optJSONArray("met")?.toString()),
            enabled = o.optBoolean("enabled", true),
            event = o.optString("event"),
            eventDay = o.optString("eventDay").takeIf { o.has("eventDay") },
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
    /** Dates are yyyy-mm-dd, so they compare as text; an earlier "today" (flying west) doesn't clear it. */
    fun expired(setOn: String?, today: String, autoClear: Boolean) = autoClear && setOn != null && today > setOn
}
