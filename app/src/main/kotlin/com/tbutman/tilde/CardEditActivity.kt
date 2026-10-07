package com.tbutman.tilde

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.telephony.PhoneNumberUtils
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
        findViewById<android.widget.ScrollView>(R.id.edit_scroll).keepFocusAboveKeyboard()
        val profile = prefs.profile
        fun profileField(id: Int, value: String, update: Profile.(String) -> Profile) =
            findViewById<TextInputEditText>(id).saveAsYouType(value) { text -> prefs.profile = prefs.profile.update(text.trim()) }
        // A card always has a name (its contact card needs one): clearing the field keeps the last one.
        findViewById<TextInputEditText>(R.id.profile_name).apply {
            val layout = parent.parent as? com.google.android.material.textfield.TextInputLayout
            saveAsYouType(profile.name) { text ->
                val name = text.trim()
                layout?.error = if (name.isEmpty()) getString(R.string.card_name_missing) else null
                if (name.isNotEmpty()) prefs.profile = prefs.profile.copy(name = name)
            }
        }
        profileField(R.id.profile_title, profile.title) { copy(title = it) }
        profileField(R.id.profile_company, profile.company) { copy(company = it) }
        findViewById<TextInputEditText>(R.id.profile_handle).filters = handleFilters()
        profileField(R.id.profile_handle, profile.handle) { copy(handle = it) }
        profileField(R.id.profile_email, profile.email) { copy(email = it) }
        profileField(R.id.profile_email2, profile.email2) { copy(email2 = it) }
        linkField(R.id.profile_website, profile.website, null) { copy(website = it) }
        linkField(R.id.profile_linkedin, profile.linkedin, Presets.LINKEDIN) { copy(linkedin = it) }
        linkField(R.id.profile_github, profile.github, Presets.GITHUB) { copy(github = it) }
        linkField(R.id.profile_instagram, profile.instagram, Presets.INSTAGRAM) { copy(instagram = it) }
        linkField(R.id.profile_x, profile.x, Presets.X) { copy(x = it) }
        setUpWhatsapp(profile)
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
        findViewById<View>(R.id.edit_done).setOnClickListener { if (linksAreValid()) finish() }

        // Phone numbers: each saved number back in its country ("+351 912 345 678" → Portugal, 912 345 678).
        for ((label, number) in profile.phones) {
            val (country, rest) = Countries.split(number, home.iso)
            addPhoneRow(country ?: home, if (country == null) number else rest, label, focus = false)
        }
        if (phoneRows.isEmpty()) addPhoneRow(home, "", "", focus = false)
        showWhatsappShortcut()

        // What the contact card includes: everything, unless this card leaves something out.
        val labels = mapOf(
            Profile.FIELD_TITLE to R.string.contact_field_title, Profile.FIELD_COMPANY to R.string.contact_field_company,
            Profile.FIELD_EMAIL to R.string.contact_field_email, Profile.FIELD_PHONES to R.string.contact_field_phones,
            Profile.FIELD_WEBSITE to R.string.contact_field_website, Profile.FIELD_SOCIALS to R.string.contact_field_socials,
        )
        val fields = findViewById<LinearLayout>(R.id.contact_fields)
        for (key in Profile.CONTACT_FIELDS) {
            fields.addView(MaterialCheckBox(this).apply {
                setText(labels.getValue(key))
                setTextColor(getColor(R.color.text))
                isChecked = key !in prefs.contactHidden
                setOnCheckedChangeListener { _, on ->
                    prefs.contactHidden = if (on) prefs.contactHidden - key else prefs.contactHidden + key
                }
            })
        }
    }

    /** The link fields, with the social network each one is for (null: the website). */
    private val linkFields = mutableListOf<Pair<TextInputEditText, String?>>()

    /**
     * A website or social profile field. What's typed is saved once it's a link ("janedoe.com" is
     * saved as https://janedoe.com, "@janedoe" as the network's profile link). Anything else shows
     * "That doesn't look like a link" when the field is left, and the card keeps the last good one.
     */
    private fun linkField(id: Int, value: String, network: String?, update: Profile.(String) -> Profile) {
        val field = findViewById<TextInputEditText>(id)
        val layout = field.parent.parent as? TextInputLayout
        field.saveAsYouType(value) { text ->
            Profile.linkField(text, network)?.let { link ->
                layout?.error = null
                prefs.profile = prefs.profile.update(link)
            }
        }
        field.setOnFocusChangeListener { _, focused -> if (!focused) checkLink(field, network) }
        linkFields += field to network
    }

    /** Shows the error under a link field that isn't a link; true when it's fine. */
    private fun checkLink(field: TextInputEditText, network: String?): Boolean {
        val ok = Profile.linkField(field.text.toString(), network) != null
        (field.parent.parent as? TextInputLayout)?.error = if (ok) null else getString(R.string.link_invalid)
        return ok
    }

    /**
     * Before closing: a link that isn't a link, or a WhatsApp number that isn't a number, gets its
     * error and the focus, so it's seen.
     */
    private fun linksAreValid(): Boolean {
        val bad = linkFields.filterNot { (field, network) -> checkLink(field, network) }.map { it.first } +
            listOfNotNull(whatsappField.takeUnless { checkWhatsapp() })
        bad.firstOrNull()?.requestFocus()
        return bad.isEmpty()
    }

    private lateinit var whatsappCountry: Country
    private val whatsappField by lazy { findViewById<TextInputEditText>(R.id.profile_whatsapp) }
    private var whatsappFormatter: TextWatcher? = null

    /**
     * WhatsApp: a country button and the number, like the phone numbers. It starts in the first
     * phone number's country, and "Same as my mobile" copies that number. A number is saved in
     * international form once it's a valid one for its country, so wa.me gets the country code.
     */
    private fun setUpWhatsapp(profile: Profile) {
        val firstPhone = profile.phones.firstOrNull()?.second?.let { Countries.split(it, home.iso).first }
        val (country, rest) = Countries.split(profile.whatsapp, home.iso)
        whatsappCountry = country ?: firstPhone ?: home
        whatsappField.isSaveEnabled = false
        whatsappField.setText(if (country == null) profile.whatsapp else rest)
        showWhatsappCountry()
        whatsappField.addTextChangedListener(afterChange { saveWhatsapp() })
        whatsappField.setOnFocusChangeListener { _, focused -> if (!focused) checkWhatsapp() }
        findViewById<View>(R.id.whatsapp_country).setOnClickListener {
            PhoneUi.pickCountry(this, phoneRows.map { it.country } + whatsappCountry + home) { picked ->
                whatsappCountry = picked
                showWhatsappCountry()
                saveWhatsapp()
            }
        }
        findViewById<View>(R.id.whatsapp_same).setOnClickListener {
            val (mobileCountry, number) = Countries.split(prefs.profile.phones.firstOrNull()?.second.orEmpty(), home.iso)
            whatsappCountry = mobileCountry ?: whatsappCountry
            showWhatsappCountry()
            whatsappField.setText(number)
        }
    }

    private fun showWhatsappCountry() {
        findViewById<MaterialButton>(R.id.whatsapp_country).apply {
            text = getString(R.string.welcome_country_code, whatsappCountry.flag, whatsappCountry.dial)
            contentDescription = getString(R.string.welcome_country, PhoneUi.name(whatsappCountry))
        }
        whatsappFormatter?.let(whatsappField::removeTextChangedListener)
        whatsappFormatter = PhoneNumberFormattingTextWatcher(whatsappCountry.iso).also(whatsappField::addTextChangedListener)
    }

    /** The number in international form, "" for none, or null when it isn't a valid number for its country. */
    private fun whatsappNumber(): String? {
        val typed = whatsappField.text.toString().trim()
        if (typed.isEmpty()) return ""
        if (PhoneNumberUtils.formatNumberToE164(typed, whatsappCountry.iso) == null) return null
        return PhoneUi.international(whatsappCountry, typed)
    }

    private fun saveWhatsapp() {
        whatsappNumber()?.let { number ->
            (findViewById<TextInputLayout>(R.id.whatsapp_layout)).error = null
            if (number != prefs.profile.whatsapp) prefs.profile = prefs.profile.copy(whatsapp = number)
        }
        showWhatsappShortcut()
    }

    /** Shows the error under a WhatsApp number that isn't one; true when it's fine. */
    private fun checkWhatsapp(): Boolean {
        val ok = whatsappNumber() != null
        findViewById<TextInputLayout>(R.id.whatsapp_layout).error = if (ok) null else getString(R.string.whatsapp_invalid)
        return ok
    }

    /** "Same as my mobile", while there's a mobile number and WhatsApp isn't already it. */
    private fun showWhatsappShortcut() {
        val mobile = prefs.profile.phones.firstOrNull()?.second
        val same = mobile != null && mobile.filter { it.isDigit() } == prefs.profile.whatsapp.filter { it.isDigit() }
        findViewById<View>(R.id.whatsapp_same).visibility = if (mobile != null && !same) View.VISIBLE else View.GONE
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
            strokeColor = getColorStateList(R.color.field_outline)
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
        showWhatsappShortcut()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_PHONES = 3
    }
}
