package com.tbutman.tilde

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/** Small pieces shared by the welcome screens, the card editor and the settings pages. */

/** Calls `run` with the text after every change. */
fun afterChange(run: (String) -> Unit) = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: Editable?) = run(s?.toString().orEmpty())
}

/**
 * Fills a field and saves it as it's typed: leaving the screen straight after typing must not lose
 * text. The stored value is the truth, so the view doesn't restore its own (possibly stale) copy.
 */
fun TextInputEditText.saveAsYouType(value: String, save: (String) -> Unit) {
    isSaveEnabled = false
    setText(value)
    addTextChangedListener(afterChange(save))
}

/** Typing in a Tilde handle field: lower case and the allowed characters only, up to the maximum length. */
fun handleFilters(): Array<InputFilter> = arrayOf(
    InputFilter { source, start, end, _, _, _ ->
        val typed = source.subSequence(start, end).toString()
        Profile.handleChars(typed).takeIf { it != typed }
    },
    InputFilter.LengthFilter(Profile.HANDLE_MAX),
)

/** Phone numbers: international form and the country list. */
object PhoneUi {
    /**
     * A number as typed for a country, in international form ("+351 912 345 678"), or null when
     * it's empty. Android formats valid numbers; anything else keeps what was typed after the code.
     */
    fun international(country: Country, typed: String): String? {
        val number = typed.trim()
        if (number.isEmpty()) return null
        val e164 = PhoneNumberUtils.formatNumberToE164(number, country.iso)
        // Formatting for a country with a different code gives the international layout, with spaces.
        val other = if (country.dial == "44") "US" else "GB"
        return e164?.let { PhoneNumberUtils.formatNumber(it, other) } ?: e164 ?: Countries.international(country, number)
    }

    /** The country's name in the phone's language ("Portugal"), or its code when there isn't one. */
    fun name(country: Country): String =
        runCatching { Locale.Builder().setRegion(country.iso).build().displayCountry }.getOrNull()?.takeIf { it.isNotBlank() } ?: country.iso

    /** A searchable list of countries; `pinned` (the ones already in use) come first. */
    fun pickCountry(context: Context, pinned: List<Country>, onPick: (Country) -> Unit) {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val dialog = BottomSheetDialog(context)
        val named = Countries.all.map { it to name(it) }.sortedBy { it.second.lowercase() }
        val first = pinned.distinct().map { it to name(it) }
        val adapter = object : ArrayAdapter<Pair<Country, String>>(context, android.R.layout.simple_list_item_1) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    val (country, name) = getItem(position)!!
                    text = context.getString(R.string.welcome_country_row, country.flag, name, country.dial)
                    setTextColor(context.getColor(R.color.text))
                }
        }
        fun show(query: String) {
            adapter.clear()
            adapter.addAll(if (query.isBlank()) first + named.filterNot { it in first } else named.filter { (c, n) -> Countries.matches(c, n, query) })
        }
        val search = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = context.getString(R.string.welcome_country_search)
        }
        search.addView(TextInputEditText(search.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            addTextChangedListener(afterChange { show(it) })
        })
        val list = ListView(context).apply {
            this.adapter = adapter
            divider = null
            setOnItemClickListener { _, _, position, _ ->
                adapter.getItem(position)?.let { onPick(it.first) }
                dialog.dismiss()
            }
        }
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(TextView(context).apply {
                setText(R.string.welcome_country_title)
                setTextColor(context.getColor(R.color.text))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.6).toInt()))
        }
        show("")
        dialog.setContentView(sheet)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }
}
