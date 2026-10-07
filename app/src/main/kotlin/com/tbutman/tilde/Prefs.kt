package com.tbutman.tilde

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The app's settings, shared by the screen, the card-emulation service and the tile. */
class Prefs(context: Context) {
    val store: SharedPreferences = context.getSharedPreferences("tilde", Context.MODE_PRIVATE)

    private val filesDir = context.filesDir
    private val cacheDir = context.cacheDir
    private val firstLabel = context.getString(R.string.cards_first_label)

    // ---- Cards ----
    // Everything about an identity lives in a Card (see Cards.kt). `profile`, `links`, `share`,
    // `pinned` and `whatsappGreeting` below read and write the active card, so screens and the tap
    // service work on whichever card is active without knowing about cards.

    private var cachedJson: String? = null
    private var cachedCards: List<Card> = emptyList()

    /** All cards, in the owner's order. Never empty: version 1.1's single profile becomes card 1. */
    var cards: List<Card>
        get() {
            if (!store.contains(KEY_CARDS)) migrateToCards()
            val json = store.getString(KEY_CARDS, null)
            if (json != cachedJson) {
                cachedJson = json
                cachedCards = Cards.fromJson(json)
            }
            return cachedCards.ifEmpty { listOf(Card(SavedLink.newId(emptyList()), firstLabel)).also { cards = it } }
        }
        set(value) = store.edit().putString(KEY_CARDS, Cards.toJson(value)).apply()

    /** The card the Share screen, taps, the code and Write a sticker use. */
    var activeCardId: String
        get() = store.getString(KEY_ACTIVE_CARD, null)?.takeIf { id -> cards.any { it.id == id } } ?: cards.first().id
        set(value) = store.edit().putString(KEY_ACTIVE_CARD, value).apply()

    val activeCard: Card
        get() = cards.let { all -> all.firstOrNull { it.id == activeCardId } ?: all.first() }

    /** Changes the active card. */
    private fun updateActive(change: (Card) -> Card) {
        val id = activeCard.id
        cards = cards.map { if (it.id == id) change(it) else it }
    }

    /** Version 1.1 (and 1.0) kept one profile in separate keys: it becomes card 1, once, photo included. */
    @android.annotation.SuppressLint("ApplySharedPref") // committed before files move, see below
    private fun migrateToCards() {
        migrateLinks()
        val id = SavedLink.newId(emptyList())
        val pinnedJson = store.getString(KEY_PINNED, null)
        val card = Cards.fromSingleProfile(
            id = id,
            profile = store.getString(KEY_PROFILE, null)?.let { Profile.parse(it) } ?: Profile.parse(BuildConfig.PROFILE_SEED),
            share = store.getString(KEY_SHARE, null) ?: Presets.CONTACT.takeIf { store.getString("mode", null) == "contact" },
            links = SavedLink.fromJson(store.getString(KEY_LINKS, null)),
            pinned = pinnedJson?.let { json -> runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull() },
            greeting = store.getString(KEY_WHATSAPP_GREETING, null),
            label = firstLabel,
        )
        // Saved before the photo moves: if Tilde stopped in between, the next start would migrate
        // again under a new id and leave the photo behind.
        store.edit()
            .putString(KEY_CARDS, Cards.toJson(listOf(card)))
            .putString(KEY_ACTIVE_CARD, id)
            .remove(KEY_PROFILE).remove(KEY_SHARE).remove(KEY_LINKS).remove(KEY_PINNED).remove(KEY_WHATSAPP_GREETING).remove("mode")
            .commit()
        java.io.File(filesDir, Photo.LEGACY_FILE).takeIf { it.exists() }?.renameTo(Photo.file(filesDir, id))
    }

    /** Debug builds' "Show the welcome screens": back to one empty card, photos deleted. */
    fun resetCards() {
        cards.forEach { Photo.file(filesDir, it.id).delete() }
        val card = Card(SavedLink.newId(emptyList()), firstLabel)
        cards = listOf(card)
        activeCardId = card.id
    }

    /** What the active card leaves off its contact card (see Profile.CONTACT_FIELDS). */
    var contactHidden: List<String>
        get() = activeCard.hidden
        set(value) = updateActive { it.copy(hidden = value.distinct()) }

    /** The active card's profile as its contact card shows it: without the details it leaves off. */
    val contactProfile: Profile
        get() = activeCard.let { it.profile.forContactCard(it.hidden) }

    /** The active card's profile: what it shows and shares. Edited in Settings. */
    var profile: Profile
        get() = activeCard.profile
        set(value) = updateActive { it.copy(profile = value) }

    /** Bumped whenever the profile photo changes, so screens know to reload it. */
    var photoVersion: Int
        get() = store.getInt(KEY_PHOTO_VERSION, 0)
        set(value) = store.edit().putInt(KEY_PHOTO_VERSION, value).apply()

    /** Set once the welcome screen has created a card, so it never comes back. */
    var welcomed: Boolean
        get() = store.getBoolean(KEY_WELCOMED, false)
        set(value) = store.edit().putBoolean(KEY_WELCOMED, value).apply()

    /**
     * Set when the welcome screens finish, so the Share screen shows "You're all set" once:
     * [READY_CARD] when they chose a card or sticker too, [READY_PHONE] otherwise, "" once shown.
     */
    var ready: String
        get() = store.getString(KEY_READY, "") ?: ""
        set(value) = store.edit().putString(KEY_READY, value).apply()

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.edit().putBoolean(KEY_ENABLED, value).apply()

    /** "share" and "met" answer taps as a tag; "receive" reads other tags and phones. */
    var tab: String
        get() = store.getString(KEY_TAB, TAB_SHARE) ?: TAB_SHARE
        set(value) = store.edit().putString(KEY_TAB, value).apply()

    /** Which option a tap shares on the active card (see Presets): a fixed option's id, or "link:<id>". */
    var share: String
        get() = activeCard.share ?: Presets.WEBSITE
        set(value) = updateActive { it.copy(share = value) }

    /** The active card's saved links, each its own option, in the order they were added. */
    var links: List<SavedLink>
        get() = activeCard.links
        set(value) = updateActive { it.copy(links = value) }

    /** Version 1.0's single custom link becomes the first saved link, once (before cards existed). */
    private fun migrateLinks() {
        if (store.contains(KEY_LINKS)) return
        val (links, share) = SavedLink.migrate(store.getString(KEY_URL, "") ?: "", store.getString(KEY_SHARE, null), SavedLink.newId(emptyList()))
        store.edit().putString(KEY_LINKS, SavedLink.toJson(links)).remove(KEY_URL)
            .apply { if (share == null) remove(KEY_SHARE) else putString(KEY_SHARE, share) }.apply()
    }

    /** The active card's quick-switch row (starred in the picker, ticked in Settings), in order. */
    var pinned: List<String>
        get() = activeCard.pinned
            ?: listOf(Presets.WEBSITE, Presets.CONTACT) + links.map { it.presetId } + listOf(Presets.LINKEDIN, Presets.WHATSAPP)
        set(value) = updateActive { it.copy(pinned = value.distinct()) }

    /** Every option in the picker's order, including saved links. */
    fun options(): List<Presets.Preset> = Presets.options(links)

    fun find(id: String): Presets.Preset = Presets.find(id, links)

    /** Whether an option can share right now (it has its link, number or network). */
    fun isReady(id: String): Boolean = Presets.isReady(id, profile, links, wifiReady)

    /** The options ready to share, in order. */
    fun available(): List<Presets.Preset> = Presets.available(profile, links, wifiReady)

    /** The quick-switch row: the pinned options that are ready, in the picker's order. */
    fun quickSwitch(): List<Presets.Preset> = pinned.toSet().let { pins -> available().filter { it.id in pins } }

    /** Optional tag added to links on the profile's website as ?event=, e.g. the meetup's name. */
    /** Cleared at the end of the day it was set (see [eventAutoClear]), so yesterday's tag doesn't linger. */
    var event: String
        get() {
            val tag = store.getString(KEY_EVENT, "") ?: ""
            val day = store.getString(KEY_EVENT_DAY, null)
            // A tag set before 1.2 has no day: it counts from today.
            if (tag.isNotEmpty() && day == null) store.edit().putString(KEY_EVENT_DAY, today()).apply()
            if (tag.isNotEmpty() && EventTag.expired(day, today(), eventAutoClear)) {
                store.edit().remove(KEY_EVENT).remove(KEY_EVENT_DAY).apply()
                return ""
            }
            return tag
        }
        set(value) = store.edit().putString(KEY_EVENT, value).putString(KEY_EVENT_DAY, today()).apply()

    private fun today() = java.time.LocalDate.now().toString()

    /** Whether the event tag clears itself at the end of the day. On unless switched off. */
    var eventAutoClear: Boolean
        get() = store.getBoolean(KEY_EVENT_AUTO_CLEAR, true)
        set(value) = store.edit().putBoolean(KEY_EVENT_AUTO_CLEAR, value).putString(KEY_EVENT_DAY, today()).apply()

    /** Full brightness on the Share screen, so the code scans in dim rooms. */
    var fullBrightness: Boolean
        get() = store.getBoolean(KEY_FULL_BRIGHTNESS, true)
        set(value) = store.edit().putBoolean(KEY_FULL_BRIGHTNESS, value).apply()

    /** Keep the screen on while Tilde is open: taps only work with the screen on. */
    var keepScreenOn: Boolean
        get() = store.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = store.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    /** A short vibration when a tap is read, or a sticker written. */
    var vibrate: Boolean
        get() = store.getBoolean(KEY_VIBRATE, true)
        set(value) = store.edit().putBoolean(KEY_VIBRATE, value).apply()

    /**
     * Answer taps with Tilde closed (the screen on and the phone unlocked). Off by default: then a
     * tap only shares while a Tilde screen is open, so nothing goes out from a pocket by accident.
     */
    var answerWhenClosed: Boolean
        get() = store.getBoolean(KEY_ANSWER_WHEN_CLOSED, false)
        set(value) = store.edit().putBoolean(KEY_ANSWER_WHEN_CLOSED, value).apply()

    /** The card a home-screen widget shows, or null for "the active card". */
    fun widgetCard(widgetId: Int): String? = store.getString("$KEY_WIDGET$widgetId", null)

    fun setWidgetCard(widgetId: Int, cardId: String?) =
        store.edit().apply { if (cardId == null) remove("$KEY_WIDGET$widgetId") else putString("$KEY_WIDGET$widgetId", cardId) }.apply()

    /** [THEME_DARK] (the default), [THEME_LIGHT], or [THEME_SYSTEM] to follow the phone. */
    var theme: String
        get() = store.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
        set(value) = store.edit().putString(KEY_THEME, value).apply()

    /** Send (not a tap) puts the card's photo in the contact card file. Off unless switched on. */
    var sendPhoto: Boolean
        get() = store.getBoolean(KEY_SEND_PHOTO, false)
        set(value) = store.edit().putBoolean(KEY_SEND_PHOTO, value).apply()

    /** After each tap, ask who it was (the note) straight away. */
    var metAskNote: Boolean
        get() = store.getBoolean(KEY_MET_ASK_NOTE, false)
        set(value) = store.edit().putBoolean(KEY_MET_ASK_NOTE, value).apply()

    /** Met entries older than this many months are deleted; 0 keeps everything. */
    var metKeepMonths: Int
        get() = store.getInt(KEY_MET_KEEP_MONTHS, 0)
        set(value) = store.edit().putInt(KEY_MET_KEEP_MONTHS, value).apply()

    // ---- Backup, restore and deleting everything ----

    /** Everything worth keeping, for a backup file. The Wi-Fi password only when asked for. */
    fun backup(includeWifiPassword: Boolean): Backup = Backup(
        created = System.currentTimeMillis(),
        cards = cards,
        activeCardId = activeCardId,
        photos = cards.mapNotNull { card -> Photo.file(filesDir, card.id).takeIf { it.exists() }?.let { card.id to it.readBytes() } }.toMap(),
        met = met,
        enabled = enabled,
        event = event,
        eventDay = store.getString(KEY_EVENT_DAY, null).takeIf { event.isNotEmpty() },
        wifiSsid = wifiSsid,
        wifiPassword = wifiPassword.takeIf { includeWifiPassword },
        wifiOpen = wifiOpen,
        settings = mapOf(
            KEY_EVENT_AUTO_CLEAR to eventAutoClear, KEY_FULL_BRIGHTNESS to fullBrightness, KEY_KEEP_SCREEN_ON to keepScreenOn,
            KEY_VIBRATE to vibrate, KEY_MET_ASK_NOTE to metAskNote, KEY_MET_KEEP_MONTHS to metKeepMonths, KEY_SEND_PHOTO to sendPhoto, KEY_THEME to theme,
            KEY_ANSWER_WHEN_CLOSED to answerWhenClosed,
        ),
    )

    /**
     * Replaces everything with a backup: cards and their photos, Met, sharing, Wi-Fi (its password
     * only if the backup has one) and the switches. Receive history and the read count stay as
     * they are.
     */
    @android.annotation.SuppressLint("ApplySharedPref") // committed before files move, see below
    fun restore(backup: Backup) {
        // The new photos go to temporary files first: if writing fails (a full disk), nothing has changed yet.
        val incoming = backup.photos.map { (id, bytes) ->
            val temp = java.io.File(filesDir, "restore-$id.tmp")
            temp.writeBytes(bytes)
            temp to Photo.file(filesDir, id)
        }
        val oldPhotos = cards.map { Photo.file(filesDir, it.id) }
        // An event tag only comes back with the day it was set, so it can still clear itself.
        val event = backup.event.takeIf { backup.eventDay != null }.orEmpty()
        val edit = store.edit()
            .putString(KEY_CARDS, Cards.toJson(backup.cards))
            .putString(KEY_ACTIVE_CARD, backup.activeCardId)
            .putString(KEY_MET, MetLog.toJson(backup.met))
            .putBoolean(KEY_ENABLED, backup.enabled)
            .putString(KEY_EVENT, event)
            .putString(KEY_WIFI_SSID, backup.wifiSsid)
            .putBoolean(KEY_WIFI_OPEN, backup.wifiOpen)
            .putBoolean(KEY_WELCOMED, true)
            .putInt(KEY_PHOTO_VERSION, photoVersion + 1)
        if (event.isEmpty()) edit.remove(KEY_EVENT_DAY) else edit.putString(KEY_EVENT_DAY, backup.eventDay)
        // A backup without the password keeps this phone's, but only for the same network.
        when {
            backup.wifiPassword != null -> edit.putString(KEY_WIFI_PASSWORD, backup.wifiPassword)
            backup.wifiSsid != wifiSsid -> edit.remove(KEY_WIFI_PASSWORD)
        }
        backup.settings.forEach { (key, value) ->
            when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is String -> edit.putString(key, value)
            }
        }
        edit.commit()
        cachedJson = null
        oldPhotos.forEach { it.delete() }
        incoming.forEach { (temp, photo) -> temp.renameTo(photo) }
    }

    /** Delete all data: every card, photo, Met entry and setting. Tilde starts again at the welcome. */
    fun deleteEverything() {
        cards.forEach { Photo.file(filesDir, it.id).delete() }
        java.io.File(filesDir, Photo.LEGACY_FILE).delete()
        // Contact cards made for Send (they can hold the photo).
        java.io.File(cacheDir, "shared").deleteRecursively()
        store.edit().clear().apply()
    }

    /** Text the active card types into a WhatsApp chat for them to send or not; blank for an empty chat. */
    var whatsappGreeting: String
        get() = activeCard.greeting ?: profile.greeting
        set(value) = updateActive { it.copy(greeting = value) }

    var wifiSsid: String
        get() = store.getString(KEY_WIFI_SSID, "") ?: ""
        set(value) = store.edit().putString(KEY_WIFI_SSID, value).apply()

    var wifiPassword: String
        get() = store.getString(KEY_WIFI_PASSWORD, "") ?: ""
        set(value) = store.edit().putString(KEY_WIFI_PASSWORD, value).apply()

    var wifiOpen: Boolean
        get() = store.getBoolean(KEY_WIFI_OPEN, false)
        set(value) = store.edit().putBoolean(KEY_WIFI_OPEN, value).apply()

    /** How many times a reader has read the whole message from this phone. */
    var reads: Int
        get() = store.getInt(KEY_READS, 0)
        set(value) = store.edit().putInt(KEY_READS, value).apply()

    /** The link a preset carries, with the event tag applied; "" when there's nothing to link to. */
    fun urlFor(id: String, card: Card = activeCard, event: String = this.event): String {
        val profile = card.profile
        val base = when (id) {
            Presets.CUSTOM -> card.links.firstOrNull()?.url.orEmpty()
            Presets.WHATSAPP -> Profile.whatsappUrl(profile.whatsapp, card.greeting ?: profile.greeting) ?: ""
            // The contact card carries a link too, for readers that only act on links (iPhones).
            Presets.CONTACT -> profile.forContactCard(card.hidden).contactLink?.second.orEmpty()
            Presets.WIFI -> profile.website
            else -> SavedLink.idOf(id)?.let { linkId -> card.links.firstOrNull { it.id == linkId }?.url }
                ?: Presets.profileUrl(id, profile) ?: ""
        }.trim()
        return if (base.isEmpty()) "" else Presets.withEvent(base, event, profile.siteHost)
    }

    /**
     * What a card's code shows, for any card (the home-screen widget can show one that isn't
     * active): its chosen option, or its first ready one when that can't share.
     */
    /**
     * What a card actually shares: its chosen option when that's ready, otherwise its first ready
     * one (as the Share screen shows), or null when nothing is. Taps, the tile, the widget and the
     * code all use this, so they always agree.
     */
    fun sharing(card: Card = activeCard): String? {
        val chosen = card.share ?: Presets.WEBSITE
        if (Presets.isReady(chosen, card.profile, card.links, wifiReady)) return chosen
        return Presets.available(card.profile, card.links, wifiReady).firstOrNull()?.id
    }

    fun qrTextFor(card: Card): String {
        val share = sharing(card) ?: return ""
        return when (share) {
            Presets.CONTACT -> card.profile.forContactCard(card.hidden).vcard(compact = true)
            Presets.WIFI -> Wifi.qrText(wifiSsid, wifiPassword, wifiOpen)
            else -> urlFor(share, card)
        }
    }

    /** The link a tap carries for the current preset. */
    val url: String
        get() = urlFor(share)

    val wifiReady: Boolean
        get() = wifiSsid.isNotBlank() && (wifiOpen || wifiPassword.length >= 8)

    /** The NDEF message a tap reads. */
    fun message(): ByteArray = messageFor(sharing() ?: share)

    /** The NDEF message for any option. Written cards pass no event: they outlive it. */
    fun messageFor(id: String, event: String = this.event): ByteArray {
        val url = urlFor(id, event = event)
        return when (id) {
            // Android dispatches on the first record, so the card comes first; the link is a
            // fallback for readers that only act on URLs.
            Presets.CONTACT -> Ndef.message(
                listOfNotNull(
                    Ndef.mimeRecord("text/vcard", contactProfile.vcard().toByteArray(Charsets.UTF_8)),
                    url.takeIf { it.isNotEmpty() }?.let { Ndef.uriRecord(it) },
                ),
            )
            Presets.WIFI -> Ndef.message(listOf(Wifi.record(wifiSsid, wifiPassword, wifiOpen)))
            else -> Ndef.uriMessage(url)
        }
    }

    /** What the on-screen QR code encodes, for phones without NFC. */
    fun qrText(): String = when (share) {
        Presets.CONTACT -> contactProfile.vcard(compact = true)
        Presets.WIFI -> Wifi.qrText(wifiSsid, wifiPassword, wifiOpen)
        else -> url
    }

    /** People met: one entry per completed tap or manual entry, newest first. Only on this phone. */
    var met: List<Meeting>
        get() = MetLog.fromJson(store.getString(KEY_MET, "[]"))
        set(value) = store.edit().putString(KEY_MET, MetLog.toJson(value)).apply()

    /** Things Receive mode has read, newest first; at most HISTORY_SIZE. */
    var received: List<Received>
        get() = runCatching {
            val array = JSONArray(store.getString(KEY_RECEIVED, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Received(o.getString("kind"), o.getString("title"), o.getString("detail"), o.getString("payload"))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = JSONArray()
            value.take(HISTORY_SIZE).forEach { r ->
                array.put(JSONObject().put("kind", r.kind).put("title", r.title).put("detail", r.detail).put("payload", r.payload))
            }
            store.edit().putString(KEY_RECEIVED, array.toString()).apply()
        }

    companion object {
        const val TAB_SHARE = "share"
        const val TAB_RECEIVE = "receive"
        const val TAB_MET = "met"
        const val TAB_SETTINGS = "settings"
        const val HISTORY_SIZE = 20

        const val KEY_CARDS = "cards"
        const val KEY_ACTIVE_CARD = "active_card"
        const val KEY_PROFILE = "profile" // before cards (1.1 and earlier); migrated into KEY_CARDS
        const val KEY_PHOTO_VERSION = "photo_version"
        const val KEY_WELCOMED = "welcomed"
        const val KEY_READY = "ready"
        const val READY_PHONE = "phone"
        const val READY_CARD = "card"
        const val KEY_ENABLED = "enabled"
        const val KEY_TAB = "tab"
        const val KEY_SHARE = "share"
        const val KEY_URL = "url" // version 1.0's custom link, migrated to KEY_LINKS
        const val KEY_LINKS = "links"
        const val KEY_PINNED = "pinned"
        const val KEY_EVENT = "event"
        const val KEY_EVENT_DAY = "event_day"
        const val KEY_EVENT_AUTO_CLEAR = "event_auto_clear"
        const val KEY_FULL_BRIGHTNESS = "full_brightness"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_SEND_PHOTO = "send_photo"
        const val KEY_THEME = "theme"
        const val KEY_WIDGET = "widget."
        const val KEY_ANSWER_WHEN_CLOSED = "answer_when_closed"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
        const val KEY_MET_ASK_NOTE = "met_ask_note"
        const val KEY_MET_KEEP_MONTHS = "met_keep_months"
        const val KEY_WHATSAPP_GREETING = "whatsapp_greeting"
        const val KEY_WIFI_SSID = "wifi_ssid"
        const val KEY_WIFI_PASSWORD = "wifi_password"
        const val KEY_WIFI_OPEN = "wifi_open"
        const val KEY_READS = "reads"
        const val KEY_RECEIVED = "received"
        const val KEY_MET = "met"
    }
}
