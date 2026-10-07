package com.tbutman.tilde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardsTest {
    private val jane = Profile(name = "Jane Doe", title = "Product designer", website = "https://example.com", handle = "janedoe")
    private val tilde = SavedLink("a1", "Tilde", "https://tbutman.com/tilde")
    private val work = Card("w1", "Work", "amber", jane, listOf(tilde), share = "link:a1", pinned = listOf("hello", "link:a1"), greeting = "Hello", hidden = listOf("phones"))
    private val personal = Card("p2", "Personal", "teal", jane.copy(title = ""))
    private val event = Card("e3", "Web Summit", "blue", jane)

    @Test
    fun cardsRoundTripThroughJson() {
        val cards = listOf(work, personal)
        assertEquals(cards, Cards.fromJson(Cards.toJson(cards)))
        // Unset choices stay unset (they mean "the default"), rather than becoming empty.
        val back = Cards.fromJson(Cards.toJson(listOf(personal))).single()
        assertNull(back.share)
        assertNull(back.pinned)
        assertNull(back.greeting)
        assertEquals(emptyList<Card>(), Cards.fromJson("not json"))
    }

    @Test
    fun theSingleProfileBecomesCardOne() {
        val card = Cards.fromSingleProfile("c1", jane, "link:a1", listOf(tilde), listOf("hello"), null)
        assertEquals("My card", card.label)
        assertEquals("amber", card.colour)
        assertEquals(jane, card.profile)
        assertEquals(listOf(tilde), card.links)
        assertEquals("link:a1", card.share)
        assertEquals(listOf("hello"), card.pinned)
        assertNull(card.greeting)
    }

    @Test
    fun aCopyIsIndependentWithItsOwnIdAndLabel() {
        val copy = Cards.copy(work, "w9", "Work (events)")
        assertEquals("w9", copy.id)
        assertEquals("Work (events)", copy.label)
        assertEquals(work.links, copy.links)
        assertEquals(work.copy(id = "w9", label = "Work (events)"), copy)
    }

    @Test
    fun deletingKeepsAtLeastOneCardAndMovesTheActiveOne() {
        val all = listOf(work, personal, event)
        assertEquals(listOf(work, event) to "w1", Cards.delete(all, "p2", "w1"))
        // Deleting the active card makes the first remaining card active.
        assertEquals(listOf(personal, event) to "p2", Cards.delete(all, "w1", "w1"))
        // The last card can't be deleted; an unknown id changes nothing.
        assertEquals(listOf(work) to "w1", Cards.delete(listOf(work), "w1", "w1"))
        assertEquals(all to "w1", Cards.delete(all, "zz", "w1"))
    }

    @Test
    fun movingStopsAtTheEnds() {
        val all = listOf(work, personal, event)
        assertEquals(listOf(personal, work, event), Cards.move(all, "w1", 1))
        assertEquals(listOf(work, event, personal), Cards.move(all, "e3", -1))
        assertEquals(all, Cards.move(all, "w1", -1))
        assertEquals(all, Cards.move(all, "e3", 1))
    }

    @Test
    fun swipingGoesRoundAndRound() {
        val all = listOf(work, personal, event)
        assertEquals(personal, Cards.step(all, "w1", 1))
        assertEquals(event, Cards.step(all, "w1", -1))
        assertEquals(work, Cards.step(all, "e3", 1))
        assertNull(Cards.step(listOf(work), "w1", 1))
    }

    @Test
    fun newCardsGetAFreeColourAndLabel() {
        assertEquals("blue", Cards.nextColour(listOf(work, personal)))
        assertEquals("Card 3", Cards.nextLabel(listOf(work, personal)))
        assertEquals("Card 4", Cards.nextLabel(listOf(work, personal, Card("x", "Card 3"))))
        assertEquals("amber", Cards.colour("nope").key)
        val blank = Cards.blank("b1", "Side project", "  Jane Doe ", "violet")
        assertEquals(Profile(name = "Jane Doe", handle = "janedoe"), blank.profile)
        assertEquals(emptyList<SavedLink>(), blank.links)
    }
}
