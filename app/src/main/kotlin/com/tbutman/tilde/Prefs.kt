package com.tbutman.tilde

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The app's settings, shared by the screen, the card-emulation service and the tile. */
class Prefs(context: Context) {
    val store: SharedPreferences = context.getSharedPreferences("tilde", Context.MODE_PRIVATE)

    private val filesDir = context.filesDir

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
            return cachedCards.ifEmpty { listOf(Card(SavedLink.newId(emptyList()), Cards.FIRST_LABEL)).also { cards = it } }
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
        )
        java.io.File(filesDir, Photo.LEGACY_FILE).takeIf { it.exists() }?.renameTo(Photo.file(filesDir, id))
        store.edit()
            .putString(KEY_CARDS, Cards.toJson(listOf(card)))
            .putString(KEY_ACTIVE_CARD, id)
            .remove(KEY_PROFILE).remove(KEY_SHARE).remove(KEY_LINKS).remove(KEY_PINNED).remove(KEY_WHATSAPP_GREETING).remove("mode")
            .apply()
    }

    /** Debug builds' "Show the welcome screens": back to one empty card, photos deleted. */
    fun resetCards() {
        cards.forEach { Photo.file(filesDir, it.id).delete() }
        val card = Card(SavedLink.newId(emptyList()), Cards.FIRST_LABEL)
        cards = listOf(card)
        activeCardId = card.id
    }

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
    var event: String
        get() = store.getString(KEY_EVENT, "") ?: ""
        set(value) = store.edit().putString(KEY_EVENT, value).apply()

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
    fun urlFor(id: String, profile: Profile = this.profile, event: String = this.event): String {
        val base = when (id) {
            Presets.CUSTOM -> links.firstOrNull()?.url.orEmpty()
            Presets.WHATSAPP -> Profile.whatsappUrl(profile.whatsapp, whatsappGreeting) ?: ""
            // The contact card carries a link too, for readers that only act on links (iPhones).
            Presets.CONTACT -> profile.contactLink?.second.orEmpty()
            Presets.WIFI -> profile.website
            else -> SavedLink.idOf(id)?.let { linkId -> links.firstOrNull { it.id == linkId }?.url }
                ?: Presets.profileUrl(id, profile) ?: ""
        }.trim()
        return if (base.isEmpty()) "" else Presets.withEvent(base, event, profile.siteHost)
    }

    /** The link a tap carries for the current preset. */
    val url: String
        get() = urlFor(share)

    val wifiReady: Boolean
        get() = wifiSsid.isNotBlank() && (wifiOpen || wifiPassword.length >= 8)

    /** The NDEF message a tap reads. */
    fun message(): ByteArray = messageFor(share)

    /** The NDEF message for any option. Written cards pass no event: they outlive it. */
    fun messageFor(id: String, event: String = this.event): ByteArray {
        val url = urlFor(id, event = event)
        return when (id) {
            // Android dispatches on the first record, so the card comes first; the link is a
            // fallback for readers that only act on URLs.
            Presets.CONTACT -> Ndef.message(
                listOfNotNull(
                    Ndef.mimeRecord("text/vcard", profile.vcard().toByteArray(Charsets.UTF_8)),
                    url.takeIf { it.isNotEmpty() }?.let { Ndef.uriRecord(it) },
                ),
            )
            Presets.WIFI -> Ndef.message(listOf(Wifi.record(wifiSsid, wifiPassword, wifiOpen)))
            else -> Ndef.uriMessage(url)
        }
    }

    /** What the on-screen QR code encodes, for phones without NFC. */
    fun qrText(): String = when (share) {
        Presets.CONTACT -> profile.vcard(compact = true)
        Presets.WIFI -> Wifi.qrText(wifiSsid, wifiPassword, wifiOpen)
        else -> url
    }

    /** People met: one entry per completed tap or manual entry, newest first. Only on this phone. */
    var met: List<Meeting>
        get() = runCatching {
            val array = JSONArray(store.getString(KEY_MET, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Meeting(o.getLong("time"), o.optString("event"), o.optString("shared"), o.optString("note"), o.optString("card"))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = JSONArray()
            value.forEach { m ->
                array.put(JSONObject().put("time", m.time).put("event", m.event).put("shared", m.shared).put("note", m.note).put("card", m.card))
            }
            store.edit().putString(KEY_MET, array.toString()).apply()
        }

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
        const val KEY_WHATSAPP_GREETING = "whatsapp_greeting"
        const val KEY_WIFI_SSID = "wifi_ssid"
        const val KEY_WIFI_PASSWORD = "wifi_password"
        const val KEY_WIFI_OPEN = "wifi_open"
        const val KEY_READS = "reads"
        const val KEY_RECEIVED = "received"
        const val KEY_MET = "met"
    }
}
