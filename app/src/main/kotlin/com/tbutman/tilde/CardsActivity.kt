package com.tbutman.tilde

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu

/**
 * Manage cards: tap a card to rename it or change its colour; its menu uses, duplicates, moves or
 * deletes it. The Share screen picks up any change when it comes back.
 */
class CardsActivity : AppCompatActivity() {
    private val prefs by lazy { Prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cards)
        findViewById<View>(R.id.cards_new).setOnClickListener { CardDialogs.newCard(this, prefs) { _, _ -> render() } }
        findViewById<View>(R.id.cards_done).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.cards_list)
        list.removeAllViews()
        val cards = prefs.cards
        val activeId = prefs.activeCardId
        cards.forEachIndexed { i, card ->
            val row = CardDialogs.row(this, card, active = card.id == activeId)
            row.setOnClickListener { CardDialogs.edit(this, prefs, card) { render() } }
            row.addView(ImageButton(this).apply {
                setImageResource(R.drawable.ic_more)
                imageTintList = getColorStateList(R.color.muted)
                val ripple = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                setBackgroundResource(ripple.resourceId)
                contentDescription = getString(R.string.cards_more_actions, card.label)
                setOnClickListener { view -> menu(view, card, first = i == 0, last = i == cards.lastIndex, only = cards.size == 1) }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(8) })
            list.addView(row)
        }
    }

    private fun menu(anchor: View, card: Card, first: Boolean, last: Boolean, only: Boolean) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.cards_switch).isEnabled = card.id != prefs.activeCardId
            menu.add(0, 2, 1, R.string.cards_edit)
            menu.add(0, 3, 2, R.string.cards_duplicate)
            menu.add(0, 4, 3, R.string.cards_move_up).isEnabled = !first
            menu.add(0, 5, 4, R.string.cards_move_down).isEnabled = !last
            menu.add(0, 6, 5, R.string.cards_delete).isEnabled = !only
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { prefs.activeCardId = card.id; prefs.photoVersion += 1; render() }
                    2 -> CardDialogs.edit(this@CardsActivity, prefs, card) { render() }
                    3 -> { CardDialogs.duplicate(this@CardsActivity, prefs, card); render() }
                    4 -> { prefs.cards = Cards.move(prefs.cards, card.id, -1); render() }
                    5 -> { prefs.cards = Cards.move(prefs.cards, card.id, 1); render() }
                    6 -> CardDialogs.delete(this@CardsActivity, prefs, card) { render() }
                }
                true
            }
            show()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
