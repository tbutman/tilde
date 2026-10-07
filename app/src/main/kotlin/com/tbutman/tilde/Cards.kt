package com.tbutman.tilde

import org.json.JSONArray
import org.json.JSONObject

/**
 * One card: a complete identity to share. What others see (the profile and photo, the handle and
 * cover colour), what it shares (its own saved links), how (the option a tap shares and its
 * quick-switch stars) and a label only the owner sees ("Work"). See docs/specs/multiple-cards.md.
 *
 * `share`, `pinned` and `greeting` are null until chosen, meaning "the default": the first ready
 * option, the default stars, and "Hi <first name>".
 */
data class Card(
    val id: String,
    val label: String,
    val colour: String = Cards.COLOURS.first().key,
    val profile: Profile = Profile(),
    val links: List<SavedLink> = emptyList(),
    val share: String? = null,
    val pinned: List<String>? = null,
    val greeting: String? = null,
    /** Details this card leaves off its contact card (Profile.CONTACT_FIELDS); everything is on by default. */
    val hidden: List<String> = emptyList(),
)

/** Cards as stored, and the rules for adding, copying, deleting and ordering them. Unit-tested. */
object Cards {
    /** A cover colour: its key (stored) and the colour itself. Amber is Tilde's own. */
    class Colour(val key: String, val argb: Long)

    val COLOURS = listOf(
        Colour("amber", 0xFFFFB547),
        Colour("teal", 0xFF4FD1C5),
        Colour("blue", 0xFF6CA8FF),
        Colour("violet", 0xFFB794F6),
        Colour("coral", 0xFFFF8A7A),
        Colour("green", 0xFF7BD88F),
    )

    fun colour(key: String): Colour = COLOURS.firstOrNull { it.key == key } ?: COLOURS.first()

    /** The longest label, so it fits beside the handle at the top of the Share screen. */
    const val LABEL_MAX = 20

    /** The first card's label, "My card", is a string resource (it's translated); this is the English, for tests. */
    const val FIRST_LABEL = "My card"

    fun toJson(cards: List<Card>): String = JSONArray().apply {
        cards.forEach { card ->
            put(JSONObject().apply {
                put("id", card.id)
                put("label", card.label)
                put("colour", card.colour)
                put("profile", card.profile.toText())
                put("links", JSONArray(SavedLink.toJson(card.links)))
                card.share?.let { put("share", it) }
                card.pinned?.let { put("pinned", JSONArray(it)) }
                card.greeting?.let { put("greeting", it) }
                if (card.hidden.isNotEmpty()) put("hidden", JSONArray(card.hidden))
            })
        }
    }.toString()

    fun fromJson(json: String?): List<Card> = runCatching {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Card(
                id = o.getString("id"),
                label = o.optString("label"),
                colour = o.optString("colour", COLOURS.first().key),
                profile = Profile.parse(o.optString("profile")),
                links = SavedLink.fromJson(o.optJSONArray("links")?.toString()),
                share = o.optString("share").takeIf { o.has("share") },
                pinned = o.optJSONArray("pinned")?.let { a -> (0 until a.length()).map { a.getString(it) } },
                greeting = o.optString("greeting").takeIf { o.has("greeting") },
                hidden = o.optJSONArray("hidden")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Version 1.1 had one profile. It becomes card 1, "My card", with everything that went with it:
     * the share choice, saved links, stars and WhatsApp greeting.
     */
    fun fromSingleProfile(
        id: String,
        profile: Profile,
        share: String?,
        links: List<SavedLink>,
        pinned: List<String>?,
        greeting: String?,
        label: String = FIRST_LABEL,
    ) = Card(id, label, COLOURS.first().key, profile, links, share, pinned, greeting)

    /** A copy of `card` with a new id and label; its links and choices come along, independent from then on. */
    fun copy(card: Card, newId: String, label: String) = card.copy(id = newId, label = label)

    /** A blank card with just a name (and a handle from it, as the welcome does), for "Start blank". */
    fun blank(id: String, label: String, name: String, colour: String) =
        Profile(name = name.trim()).let { Card(id, label, colour, it.copy(handle = it.suggestedHandle)) }

    /** A colour the other cards don't use yet, if there is one, so a new card stands out. */
    fun nextColour(cards: List<Card>): String =
        (COLOURS.firstOrNull { c -> cards.none { it.colour == c.key } } ?: COLOURS[cards.size % COLOURS.size]).key

    /** "Card 2", "Card 3"… (`label` makes them, in the app's language): the first such label not taken. */
    fun nextLabel(cards: List<Card>, label: (Int) -> String = { "Card $it" }): String =
        generateSequence(cards.size + 1) { it + 1 }.map(label).first { label -> cards.none { it.label == label } }

    /**
     * Deletes a card, unless it's the last one. Returns the cards left and the active card's id: the
     * same one, or the first card when the active one was deleted.
     */
    fun delete(cards: List<Card>, id: String, activeId: String): Pair<List<Card>, String> {
        if (cards.size <= 1 || cards.none { it.id == id }) return cards to activeId
        val left = cards.filterNot { it.id == id }
        return left to (if (activeId == id) left.first().id else activeId)
    }

    /** Moves a card one place up (-1) or down (+1); does nothing at either end. */
    fun move(cards: List<Card>, id: String, by: Int): List<Card> {
        val from = cards.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in cards.indices) return cards
        return cards.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** The card a swipe goes to: the next (+1) or previous (-1) one, round and round. */
    fun step(cards: List<Card>, activeId: String, by: Int): Card? {
        if (cards.size < 2) return null
        val from = cards.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        return cards[Math.floorMod(from + by, cards.size)]
    }
}
