package com.tbutman.tilde

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Creating, editing and deleting cards, shared by the Share screen's card switcher and the Cards
 * screen, and the row both use to show a card.
 */
object CardDialogs {
    /**
     * "New card": a label, then a copy of the active card (the default: most details stay the same)
     * or a blank card with just a name. The new card becomes active; `onCreated` says whether it
     * was a copy.
     */
    fun newCard(context: Context, prefs: Prefs, onCreated: (Card, copied: Boolean) -> Unit) {
        val cards = prefs.cards
        val active = prefs.activeCard
        val (labelLayout, label) = field(context, R.string.cards_new_label, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        label.setText(Cards.nextLabel(cards))
        // Typing replaces the suggested "Card 2".
        label.setSelectAllOnFocus(true)
        val (nameLayout, name) = field(context, R.string.cards_new_name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        nameLayout.visibility = View.GONE
        val copy = RadioButton(context).apply { id = View.generateViewId(); text = context.getString(R.string.cards_new_copy, active.label); isChecked = true }
        val blank = RadioButton(context).apply { id = View.generateViewId(); setText(R.string.cards_new_blank) }
        val copyHint = hint(context, R.string.cards_new_copy_detail)
        val choice = RadioGroup(context).apply {
            addView(copy)
            addView(copyHint)
            addView(blank)
            setOnCheckedChangeListener { _, checked ->
                nameLayout.visibility = if (checked == blank.id) View.VISIBLE else View.GONE
                copyHint.visibility = if (checked == copy.id) View.VISIBLE else View.GONE
            }
        }
        name.doAfterTextChanged { nameLayout.error = null }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cards_new)
            .setView(column(context, labelLayout, choice, nameLayout))
            .setPositiveButton(R.string.cards_create, null)
            .setNegativeButton(R.string.met_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val copied = copy.isChecked
                if (!copied && name.text.isNullOrBlank()) {
                    nameLayout.error = context.getString(R.string.welcome_name_missing)
                    return@setOnClickListener
                }
                val id = SavedLink.newId(cards.map { it.id })
                val title = label.text.toString().trim().ifEmpty { Cards.nextLabel(cards) }
                val colour = Cards.nextColour(cards)
                val card = if (copied) Cards.copy(active, id, title).copy(colour = colour) else Cards.blank(id, title, name.text.toString(), colour)
                if (copied) Photo.copy(context, active.id, id)
                prefs.cards = cards + card
                prefs.activeCardId = id
                prefs.photoVersion += 1
                dialog.dismiss()
                onCreated(card, copied)
            }
        }
        dialog.show()
        label.requestFocus()
    }

    /** Rename a card and choose its cover colour. */
    fun edit(context: Context, prefs: Prefs, card: Card, onSaved: () -> Unit) {
        val (labelLayout, label) = field(context, R.string.cards_new_label, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        label.setText(card.label)
        label.setSelectAllOnFocus(true)
        var colour = card.colour
        val swatches = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun showSwatches() {
            swatches.removeAllViews()
            for (c in Cards.COLOURS) {
                swatches.addView(TextView(context).apply {
                    setBackgroundResource(R.drawable.bg_dot)
                    backgroundTintList = ColorStateList.valueOf(c.argb.toInt())
                    gravity = Gravity.CENTER
                    text = if (c.key == colour) "✓" else ""
                    setTextColor(context.getColor(R.color.bg))
                    textSize = 18f
                    contentDescription = c.key
                    isSelected = c.key == colour
                    setOnClickListener { colour = c.key; showSwatches() }
                }, LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)).apply { marginEnd = dp(context, 12) })
            }
        }
        showSwatches()
        val colourLabel = TextView(context).apply {
            setText(R.string.cards_colour)
            setTextColor(context.getColor(R.color.dim))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cards_edit)
            .setView(column(context, labelLayout, colourLabel, swatches))
            .setPositiveButton(R.string.met_save) { _, _ ->
                val title = label.text.toString().trim().ifEmpty { card.label }
                prefs.cards = prefs.cards.map { if (it.id == card.id) it.copy(label = title, colour = colour) else it }
                onSaved()
            }
            .setNegativeButton(R.string.met_cancel, null)
            .show()
    }

    /** A copy of `card` placed straight after it, with its own photo file. Doesn't change the active card. */
    fun duplicate(context: Context, prefs: Prefs, card: Card) {
        val cards = prefs.cards
        val id = SavedLink.newId(cards.map { it.id })
        val copy = Cards.copy(card, id, context.getString(R.string.cards_copy_label, card.label)).copy(colour = Cards.nextColour(cards))
        Photo.copy(context, card.id, id)
        val at = cards.indexOfFirst { it.id == card.id } + 1
        prefs.cards = cards.toMutableList().apply { add(at, copy) }
    }

    /** Asks first; the last card can't be deleted (the menu doesn't offer it). */
    fun delete(context: Context, prefs: Prefs, card: Card, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.cards_delete_title, card.label))
            .setMessage(R.string.cards_delete_body)
            .setPositiveButton(R.string.cards_delete) { _, _ ->
                val (left, active) = Cards.delete(prefs.cards, card.id, prefs.activeCardId)
                if (left.size < prefs.cards.size) Photo.delete(context, card.id)
                prefs.cards = left
                prefs.activeCardId = active
                prefs.photoVersion += 1
                onDeleted()
            }
            .setNegativeButton(R.string.met_cancel, null)
            .show()
    }

    /**
     * A card as a row: its colour, label, and the name and title on it, with a check when it's the
     * active one (`active` null: no end mark).
     */
    fun row(context: Context, card: Card, active: Boolean?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, 64)
        setPadding(dp(context, 20), dp(context, 10), dp(context, 16), dp(context, 10))
        val ripple = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        addView(View(context).apply {
            setBackgroundResource(R.drawable.bg_dot)
            backgroundTintList = ColorStateList.valueOf(Cards.colour(card.colour).argb.toInt())
        }, LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = card.label
                setTextColor(context.getColor(if (active == true) R.color.accent else R.color.text))
                textSize = 16f
            })
            addView(TextView(context).apply {
                text = listOf(card.profile.name, card.profile.title).filter { it.isNotBlank() }.joinToString(" · ")
                setTextColor(context.getColor(R.color.muted))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 16) })
        if (active == true) {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_check)
                imageTintList = context.getColorStateList(R.color.accent)
                contentDescription = context.getString(R.string.cards_active)
            }, LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)))
        }
    }

    private fun field(context: Context, hint: Int, type: Int): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = context.getString(hint)
        }
        val input = TextInputEditText(layout.context).apply {
            inputType = type
            isSingleLine = true
        }
        layout.addView(input)
        return layout to input
    }

    private fun hint(context: Context, text: Int) = TextView(context).apply {
        setText(text)
        setTextColor(context.getColor(R.color.muted))
        textSize = 13f
        setPadding(dp(context, 32), 0, 0, dp(context, 8))
    }

    private fun column(context: Context, vararg views: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(context, 20)
        setPadding(pad, pad / 2, pad, 0)
        views.forEachIndexed { i, view ->
            addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = pad / 2
            })
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
