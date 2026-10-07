package com.tbutman.tilde

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.telephony.TelephonyManager
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/**
 * Edit card: everything on the active card, grouped into Card (photo, name, title, handle),
 * Contact (emails, phone numbers), Links (website, social profiles, saved links) and WhatsApp.
 * Fields save as they're typed. Opened from Settings, from the card on the Share screen, and after
 * "Start blank" or "Set up" on the contact card.
 */
class CardEditActivity : AppCompatActivity() {
    private val prefs by lazy { Prefs(this) }

    /** Android's photo picker: no permission needed, and the app only sees the photo chosen. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { cropPhoto.launch(Intent(this, CropActivity::class.java).setData(it)) }
    }
    private val cropPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            prefs.photoVersion += 1
            renderPhoto()
        }
    }

    /** One phone number being edited: its country, its field, and the label it had when loaded. */
    private class PhoneRow(var country: Country, val field: TextInputEditText, val oldLabel: String, var formatter: TextWatcher? = null)

    private val phoneRows = mutableListOf<PhoneRow>()

    private val home: Country by lazy {
        val telephony = getSystemService(TelephonyManager::class.java)
        Countries.default(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_edit)
        val profile = prefs.profile
        fun profileField(id: Int, value: String, update: Profile.(String) -> Profile) =
            findViewById<TextInputEditText>(id).saveAsYouType(value) { text -> prefs.profile = prefs.profile.update(text.trim()) }
        profileField(R.id.profile_name, profile.name) { copy(name = it) }
        profileField(R.id.profile_title, profile.title) { copy(title = it) }
        findViewById<TextInputEditText>(R.id.profile_handle).filters = handleFilters()
        profileField(R.id.profile_handle, profile.handle) { copy(handle = it) }
        profileField(R.id.profile_email, profile.email) { copy(email = it) }
        profileField(R.id.profile_email2, profile.email2) { copy(email2 = it) }
        profileField(R.id.profile_website, profile.website) { copy(website = it) }
        profileField(R.id.profile_linkedin, profile.linkedin) { copy(linkedin = it) }
        profileField(R.id.profile_github, profile.github) { copy(github = it) }
        profileField(R.id.profile_instagram, profile.instagram) { copy(instagram = it) }
        profileField(R.id.profile_x, profile.x) { copy(x = it) }
        profileField(R.id.profile_whatsapp, profile.whatsapp) { copy(whatsapp = it) }
        findViewById<TextInputEditText>(R.id.whatsapp_greeting).saveAsYouType(prefs.whatsappGreeting) { prefs.whatsappGreeting = it }

        findViewById<View>(R.id.photo_pick).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<View>(R.id.photo_remove).setOnClickListener {
            Photo.delete(this, prefs.activeCardId)
            prefs.photoVersion += 1
            renderPhoto()
        }
        findViewById<View>(R.id.edit_card_header).setOnClickListener { CardDialogs.edit(this, prefs, prefs.activeCard) { renderHeader() } }
        findViewById<View>(R.id.links_add).setOnClickListener { LinkDialogs.editLink(this, prefs, null) { renderLinks() } }
        findViewById<View>(R.id.phone_add).setOnClickListener { addPhoneRow(home, "", "", focus = true) }
        findViewById<View>(R.id.edit_done).setOnClickListener { finish() }

        // Phone numbers: each saved number back in its country ("+351 912 345 678" → Portugal, 912 345 678).
        for ((label, number) in profile.phones) {
            val (country, rest) = Countries.split(number, home.iso)
            addPhoneRow(country ?: home, if (country == null) number else rest, label, focus = false)
        }
        if (phoneRows.isEmpty()) addPhoneRow(home, "", "", focus = false)
    }

    override fun onResume() {
        super.onResume()
        renderHeader()
        renderPhoto()
        renderLinks()
    }

    private fun renderHeader() {
        val card = prefs.activeCard
        findViewById<TextView>(R.id.edit_card_label).text = card.label
        findViewById<View>(R.id.edit_card_dot).backgroundTintList = ColorStateList.valueOf(Cards.colour(card.colour).argb.toInt())
    }

    private fun renderPhoto() {
        val photo = Photo.load(this, prefs.activeCardId)
        val avatar = findViewById<View>(R.id.edit_avatar)
        avatar.findViewById<ImageView>(R.id.avatar_photo).apply {
            setImageBitmap(photo)
            visibility = if (photo != null) View.VISIBLE else View.GONE
        }
        avatar.findViewById<TextView>(R.id.avatar_initials).apply {
            visibility = if (photo != null) View.GONE else View.VISIBLE
            val profile = prefs.profile
            text = listOf(profile.firstName, profile.lastName).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "~" }
        }
        findViewById<MaterialButton>(R.id.photo_pick).setText(if (photo != null) R.string.photo_change else R.string.photo_add)
        findViewById<View>(R.id.photo_remove).visibility = if (photo != null) View.VISIBLE else View.GONE
    }

    /** Saved links: tap to edit; the checkbox puts it on the Share screen (the same as its star). */
    private fun renderLinks() {
        val list = findViewById<LinearLayout>(R.id.links_list)
        list.removeAllViews()
        for (link in prefs.links) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                val ripple = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
                addView(TextView(context).apply {
                    text = link.name
                    setTextColor(getColor(R.color.text))
                    textSize = 16f
                })
                addView(TextView(context).apply {
                    text = PresetRows.bare(link.url)
                    setTextColor(getColor(R.color.dim))
                    typeface = android.graphics.Typeface.MONOSPACE
                    textSize = 12f
                })
                setOnClickListener { LinkDialogs.editLink(this@CardEditActivity, prefs, link) { renderLinks() } }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(MaterialCheckBox(this).apply {
                isChecked = link.presetId in prefs.pinned
                contentDescription = getString(R.string.link_on_share, link.name)
                setOnCheckedChangeListener { _, on ->
                    prefs.pinned = if (on) prefs.pinned + link.presetId else prefs.pinned - link.presetId
                }
            })
            list.addView(row)
        }
    }

    /** A phone row: country button, number (formatted for the country as it's typed), remove. */
    private fun addPhoneRow(country: Country, number: String, label: String, focus: Boolean) {
        val list = findViewById<LinearLayout>(R.id.phones_list)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        val countryButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            insetTop = 0
            insetBottom = 0
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(getColor(R.color.text))
            cornerRadius = dp(4)
            strokeColor = getColorStateList(R.color.line_strong)
        }
        view.addView(countryButton, LinearLayout.LayoutParams(dp(96), dp(56)).apply { topMargin = dp(14) })
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.welcome_phone)
            boxStrokeColor = getColor(R.color.accent)
        }
        val field = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            isSingleLine = true
            setText(number)
        }
        layout.addView(field)
        view.addView(layout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
            topMargin = dp(8)
        })
        val row = PhoneRow(country, field, label)
        view.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            setIconResource(R.drawable.ic_close)
            iconTint = getColorStateList(R.color.muted)
            contentDescription = getString(R.string.welcome_phone_remove)
            setOnClickListener {
                phoneRows.remove(row)
                list.removeView(view)
                savePhones()
                showAddPhone()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { topMargin = dp(18) })

        fun showCountry() {
            countryButton.text = getString(R.string.welcome_country_code, row.country.flag, row.country.dial)
            countryButton.contentDescription = getString(R.string.welcome_country, PhoneUi.name(row.country))
            row.formatter?.let(field::removeTextChangedListener)
            row.formatter = PhoneNumberFormattingTextWatcher(row.country.iso).also(field::addTextChangedListener)
        }
        showCountry()
        countryButton.setOnClickListener {
            PhoneUi.pickCountry(this, phoneRows.map { it.country } + home) { picked ->
                row.country = picked
                showCountry()
                savePhones()
            }
        }
        field.addTextChangedListener(afterChange { savePhones() })
        phoneRows += row
        list.addView(view)
        showAddPhone()
        if (focus) field.requestFocus()
    }

    /** Up to three numbers. */
    private fun showAddPhone() {
        findViewById<View>(R.id.phone_add).visibility = if (phoneRows.size < MAX_PHONES) View.VISIBLE else View.GONE
    }

    /**
     * Saves every row with a number, in international form. Labels typed by hand stay; Tilde's own
     * ("mobile", "mobile (US)") are redone, so a second country shows up when it differs.
     */
    private fun savePhones() {
        val filled = phoneRows.mapNotNull { row -> PhoneUi.international(row.country, row.field.text.toString())?.let { row to it } }
        val labels = Countries.labelsKeeping(filled.map { it.first.oldLabel }, filled.map { it.first.country.iso })
        prefs.profile = prefs.profile.copy(phones = filled.mapIndexed { i, (_, number) -> labels[i] to number })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_PHONES = 3
    }
}
