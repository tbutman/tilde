package com.tbutman.tilde

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Small dialogs for adding what a sharing option needs without going to Settings: a saved link
 * (name and address), or the one missing link or number for a fixed option.
 */
object LinkDialogs {
    /**
     * Add a saved link, or edit `existing` (which also gets Delete). A new link is starred, so it
     * appears in the Share screen's quick-switch row. `onSaved` gets the link; deleting calls it with null.
     */
    fun editLink(context: Context, prefs: Prefs, existing: SavedLink?, onSaved: (SavedLink?) -> Unit) {
        val (nameLayout, name) = field(context, R.string.link_name_hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val (urlLayout, url) = field(context, R.string.link_url_hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        name.setText(existing?.name)
        url.setText(existing?.url)
        url.doAfterTextChanged { urlLayout.error = null }
        // A new link: offer the ones on your other cards that this card doesn't have yet. Picking one
        // fills the fields; saving makes this card's own copy.
        val have = prefs.links.map { it.url.trimEnd('/') }.toSet()
        val suggestions = if (existing != null) emptyList() else prefs.cards.filter { it.id != prefs.activeCardId }
            .flatMap { card -> card.links.map { card to it } }
            .filter { (_, link) -> link.url.trimEnd('/') !in have }
            .distinctBy { (_, link) -> link.url.trimEnd('/') }
            .take(5)
        val views = mutableListOf<View>(urlLayout, nameLayout)
        if (suggestions.isNotEmpty()) {
            views += android.widget.TextView(context).apply {
                setText(R.string.link_from_other_cards)
                setTextColor(context.getColor(R.color.dim))
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            for ((card, link) in suggestions) {
                views += com.google.android.material.button.MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                    text = context.getString(R.string.link_suggestion, link.name, PresetRows.bare(link.url), card.label)
                    isAllCaps = false
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    setTextColor(context.getColor(R.color.accent))
                    setOnClickListener {
                        url.setText(link.url)
                        name.setText(link.name)
                    }
                }
            }
        }
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.link_add_title else R.string.link_edit_title)
            .setView(column(context, *views.toTypedArray()))
            .setPositiveButton(R.string.met_save, null)
            .setNegativeButton(R.string.met_cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.met_delete) { _, _ ->
                prefs.links = prefs.links.filterNot { it.id == existing.id }
                prefs.pinned = prefs.pinned - existing.presetId
                onSaved(null)
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            // Validate before closing: a bad link keeps the dialog open with an error.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val link = SavedLink.validUrl(url.text.toString())
                if (link == null) {
                    urlLayout.error = context.getString(R.string.link_invalid)
                    return@setOnClickListener
                }
                val label = name.text.toString().trim().ifEmpty { SavedLink.defaultName(link) }
                val saved = SavedLink(existing?.id ?: SavedLink.newId(prefs.links.map { it.id }), label, link)
                prefs.links = if (existing == null) prefs.links + saved else prefs.links.map { if (it.id == saved.id) saved else it }
                if (existing == null) prefs.pinned = prefs.pinned + saved.presetId
                dialog.dismiss()
                onSaved(saved)
            }
        }
        dialog.show()
        url.requestFocus()
    }

    /**
     * Asks for what a fixed option is missing: the website, a social profile's link or the WhatsApp
     * number. Returns false for the options that need more than one field (Wi-Fi, the contact card),
     * which are set up in Settings instead.
     */
    fun setUpOption(context: Context, prefs: Prefs, preset: Presets.Preset, onSaved: () -> Unit): Boolean {
        val whatsapp = preset.id == Presets.WHATSAPP
        if (preset.id == Presets.WIFI || preset.id == Presets.CONTACT || SavedLink.idOf(preset.id) != null) return false
        val (layout, input) = field(
            context,
            if (whatsapp) R.string.setup_whatsapp_hint else R.string.setup_link_hint,
            if (whatsapp) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )
        input.doAfterTextChanged { layout.error = null }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.setup_title, context.labelOf(preset)))
            .setView(column(context, layout))
            .setPositiveButton(R.string.met_save, null)
            .setNegativeButton(R.string.met_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                val profile = prefs.profile
                val updated = when (preset.id) {
                    Presets.WHATSAPP -> profile.copy(whatsapp = text).takeIf { it.hasWhatsapp }
                    else -> SavedLink.validUrl(text)?.let { link ->
                        when (preset.id) {
                            Presets.WEBSITE -> profile.copy(website = link)
                            Presets.LINKEDIN -> profile.copy(linkedin = link)
                            Presets.GITHUB -> profile.copy(github = link)
                            Presets.INSTAGRAM -> profile.copy(instagram = link)
                            else -> profile.copy(x = link)
                        }
                    }
                }
                if (updated == null) {
                    layout.error = context.getString(if (whatsapp) R.string.setup_whatsapp_invalid else R.string.link_invalid)
                    return@setOnClickListener
                }
                prefs.profile = updated
                dialog.dismiss()
                onSaved()
            }
        }
        dialog.show()
        input.requestFocus()
        return true
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

    private fun column(context: Context, vararg views: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * context.resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        views.forEachIndexed { i, view ->
            addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = pad / 2
            })
        }
    }
}
