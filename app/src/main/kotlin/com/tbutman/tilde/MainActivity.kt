package com.tbutman.tilde

import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.CardEmulation
import android.nfc.tech.Ndef as NdefTech
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberFormattingTextWatcher
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.util.Calendar
import java.util.Locale

/**
 * One screen, four tabs. Share is what the other person looks at (name, QR code, what a tap
 * shares), so everything only the owner needs lives in Settings, Met and the picker sheet.
 */
class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private lateinit var prefs: Prefs
    private var adapter: NfcAdapter? = null
    private val rows by lazy { PresetRows(this, prefs) }

    /**
     * Debug builds only: draw the Share screen as on a phone with NFC switched on, for README
     * screenshots taken on the emulator (which has no NFC). `adb shell am start -n
     * com.tbutman.tilde/.MainActivity --ez demo true`
     */
    private val demo by lazy { BuildConfig.DEBUG && intent.getBooleanExtra("demo", false) }
    private val service by lazy { ComponentName(this, NdefHceService::class.java) }
    private val main = Handler(Looper.getMainLooper())
    private var lastReads = 0
    private var resumed = false

    private var photo: Bitmap? = null

    /** Settings' "This card" fields show this card; `filling` is true while they're being refilled. */
    private var filledCardId = ""
    private var filling = false
    private var photoLoaded = ""

    /** Android's photo picker: no permission needed, and the app only sees the photo chosen. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { cropPhoto.launch(Intent(this, CropActivity::class.java).setData(it)) }
    }
    private val cropPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) prefs.photoVersion += 1
    }

    private lateinit var nav: BottomNavigationView

    // Welcome: the step showing, each number's country, whether the second number is open, and
    // whether a printed card or sticker comes too. Kept across rotation in onSaveInstanceState.
    private var welcomeStep = 0
    private lateinit var phoneCountries: Array<Country>
    private val phoneFormatters = arrayOfNulls<TextWatcher>(2)
    private var secondPhone = false
    private var withCard = false
    /** The handle follows the name until it's edited; `syncingHandle` marks the name's own updates. */
    private var handleEdited = false
    private var syncingHandle = false
    private val welcomeBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = showWelcomeStep(welcomeStep - 1)
    }
    private lateinit var panels: Map<String, View>
    private lateinit var sent: View

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // The card-emulation service writes the read count; everything else is this screen's own.
        if (key == Prefs.KEY_READS) main.post { onRead() } else main.post { render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        adapter = NfcAdapter.getDefaultAdapter(this)
        lastReads = prefs.reads

        nav = findViewById(R.id.nav)
        sent = findViewById(R.id.sent)
        panels = mapOf(
            Prefs.TAB_SHARE to findViewById(R.id.share_panel),
            Prefs.TAB_RECEIVE to findViewById(R.id.receive_panel),
            Prefs.TAB_MET to findViewById(R.id.met_panel),
            Prefs.TAB_SETTINGS to findViewById(R.id.settings_panel),
        )
        nav.selectedItemId = navId(prefs.tab)
        nav.setOnItemSelectedListener { item ->
            prefs.tab = when (item.itemId) {
                R.id.nav_receive -> Prefs.TAB_RECEIVE
                R.id.nav_met -> Prefs.TAB_MET
                R.id.nav_settings -> Prefs.TAB_SETTINGS
                else -> Prefs.TAB_SHARE
            }
            applyNfcMode()
            applyBrightness()
            true
        }

        setUpShare()
        setUpReceive()
        setUpMet()
        setUpSettings()
        setUpWelcome(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, welcomeStep)
        outState.putStringArray(STATE_COUNTRIES, phoneCountries.map { it.iso }.toTypedArray())
        outState.putBoolean(STATE_SECOND_PHONE, secondPhone)
        outState.putBoolean(STATE_WITH_CARD, withCard)
        outState.putBoolean(STATE_HANDLE_EDITED, handleEdited)
    }

    /** The welcome screen shows on a first launch with nothing in the profile. */
    private val welcoming: Boolean
        get() = !prefs.welcomed && !prefs.profile.isSet

    private fun setUpWelcome(saved: Bundle?) {
        onBackPressedDispatcher.addCallback(this, welcomeBack)
        findViewById<TextView>(R.id.welcome_brand).text = SpannableStringBuilder(getString(R.string.brand_prefix)).apply {
            setSpan(ForegroundColorSpan(getColor(R.color.accent)), 0, length, 0)
            append(getString(R.string.brand_name))
        }
        bindCard(
            findViewById(R.id.welcome_sample_card),
            Profile(name = getString(R.string.welcome_sample_name), title = getString(R.string.welcome_sample_title)),
            usePhoto = false,
        )

        // Step 2: the live preview follows the name, title and handle as they're typed, and the
        // handle follows the name ("Jane Doe" → janedoe) until it's edited.
        val nameLayout = findViewById<TextInputLayout>(R.id.welcome_name_layout)
        val handle = findViewById<TextInputEditText>(R.id.welcome_handle).apply { filters = handleFilters }
        handleEdited = saved?.getBoolean(STATE_HANDLE_EDITED) == true
        findViewById<TextInputEditText>(R.id.welcome_name).addTextChangedListener(afterChange { name ->
            nameLayout.error = null
            if (!handleEdited) {
                syncingHandle = true
                handle.setText(Profile(name = name).suggestedHandle)
                syncingHandle = false
            }
            bindWelcomePreview()
        })
        findViewById<TextInputEditText>(R.id.welcome_job).addTextChangedListener(afterChange { bindWelcomePreview() })
        handle.addTextChangedListener(afterChange { text ->
            // Clearing it hands it back to the name.
            if (!syncingHandle) handleEdited = text.isNotEmpty()
            bindWelcomePreview()
        })
        findViewById<View>(R.id.welcome_photo).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        handle.onDone { welcomeNext() }

        // Step 3: numbers start in the phone's own country; the second one is added on request.
        val home = Countries.default(
            getSystemService(TelephonyManager::class.java)?.simCountryIso,
            getSystemService(TelephonyManager::class.java)?.networkCountryIso,
            Locale.getDefault().country,
        )
        val savedIsos = saved?.getStringArray(STATE_COUNTRIES)
        phoneCountries = Array(2) { i -> Countries.find(savedIsos?.getOrNull(i)) ?: home }
        listOf(R.id.welcome_country1, R.id.welcome_country2).forEachIndexed { i, id ->
            findViewById<View>(id).setOnClickListener { pickCountry(i) }
            setPhoneCountry(i, phoneCountries[i])
        }
        findViewById<TextInputEditText>(R.id.welcome_email).addTextChangedListener(afterChange {
            findViewById<TextInputLayout>(R.id.welcome_email_layout).error = null
        })
        findViewById<View>(R.id.welcome_phone_add).setOnClickListener { showSecondPhone(true) }
        findViewById<View>(R.id.welcome_phone2_remove).setOnClickListener {
            findViewById<TextInputEditText>(R.id.welcome_phone2).text = null
            showSecondPhone(false)
        }
        findViewById<TextInputEditText>(R.id.welcome_website).onDone { welcomeNext() }

        // Step 4: phone only, or a card or sticker too (which opens Write a card afterwards).
        val modes = listOf(findViewById<MaterialCardView>(R.id.welcome_mode_phone), findViewById(R.id.welcome_mode_card))
        fun chooseMode(card: Boolean) {
            withCard = card
            modes[0].isChecked = !card
            modes[1].isChecked = card
        }
        modes.forEachIndexed { i, mode -> mode.setOnClickListener { chooseMode(i == 1) } }
        findViewById<View>(R.id.welcome_get_card).setOnClickListener { open(getString(R.string.welcome_get_card_url)) }

        // Enter (Next) goes field by field in reading order. Set here because a hardware keyboard's
        // Enter otherwise moves by position on screen, and can land on a button instead.
        for ((from, to) in listOf(
            R.id.welcome_name to R.id.welcome_job, R.id.welcome_job to R.id.welcome_handle,
            R.id.welcome_email to R.id.welcome_phone1, R.id.welcome_phone2 to R.id.welcome_website,
        )) {
            findViewById<View>(from).apply { nextFocusDownId = to; nextFocusForwardId = to }
        }

        findViewById<View>(R.id.welcome_next).setOnClickListener { welcomeNext() }
        findViewById<View>(R.id.welcome_back).setOnClickListener { showWelcomeStep(welcomeStep - 1) }

        showSecondPhone(saved?.getBoolean(STATE_SECOND_PHONE) == true)
        chooseMode(saved?.getBoolean(STATE_WITH_CARD) == true)
        showWelcomeStep(saved?.getInt(STATE_STEP) ?: 0)
    }

    private val welcomeSteps by lazy {
        listOf(R.id.welcome_step_intro, R.id.welcome_step_card, R.id.welcome_step_contact, R.id.welcome_step_share).map { findViewById<View>(it) }
    }

    private fun showWelcomeStep(step: Int) {
        hideKeyboard()
        welcomeStep = step.coerceIn(0, welcomeSteps.lastIndex)
        welcomeSteps.forEachIndexed { i, view -> view.visibility = if (i == welcomeStep) View.VISIBLE else View.GONE }
        findViewById<View>(R.id.welcome_back).visibility = if (welcomeStep > 0) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.welcome_next).setText(
            when (welcomeStep) {
                0 -> R.string.welcome_start
                welcomeSteps.lastIndex -> R.string.welcome_done
                else -> R.string.welcome_next
            },
        )
        // Progress dots: the current step is a wider amber pill.
        val dots = findViewById<LinearLayout>(R.id.welcome_dots)
        dots.removeAllViews()
        dots.contentDescription = getString(R.string.welcome_step, welcomeStep + 1, welcomeSteps.size)
        for (i in welcomeSteps.indices) {
            dots.addView(View(this).apply {
                setBackgroundResource(R.drawable.bg_dot)
                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(if (i == welcomeStep) R.color.accent else R.color.line_strong))
                layoutParams = LinearLayout.LayoutParams(dp(if (i == welcomeStep) 20 else 8), dp(8)).apply { marginStart = dp(6) }
            })
        }
        welcomeBack.isEnabled = welcoming && welcomeStep > 0
        bindWelcomePreview()
        // A form step starts in its first field, keyboard open, unless it's been filled in already.
        val first = when (welcomeStep) {
            1 -> R.id.welcome_name
            2 -> R.id.welcome_email
            else -> null
        }?.let { findViewById<TextInputEditText>(it) }
        if (welcoming && first != null && first.text.isNullOrEmpty()) showKeyboard(first)
    }

    private fun welcomeNext() {
        when (welcomeStep) {
            1 -> if (findViewById<TextInputEditText>(R.id.welcome_name).text.isNullOrBlank()) {
                findViewById<TextInputLayout>(R.id.welcome_name_layout).error = getString(R.string.welcome_name_missing)
                findViewById<View>(R.id.welcome_name).requestFocus()
                return
            }
            2 -> {
                val email = findViewById<TextInputEditText>(R.id.welcome_email).text.toString().trim()
                if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    findViewById<TextInputLayout>(R.id.welcome_email_layout).error = getString(R.string.welcome_email_invalid)
                    findViewById<View>(R.id.welcome_email).requestFocus()
                    return
                }
            }
            welcomeSteps.lastIndex -> return finishWelcome()
        }
        showWelcomeStep(welcomeStep + 1)
    }

    /** The preview on step 2: what's typed so far, with "Full name" and "Job title" standing in until then. */
    private fun bindWelcomePreview() {
        val name = findViewById<TextInputEditText>(R.id.welcome_name).text.toString().trim()
        val title = findViewById<TextInputEditText>(R.id.welcome_job).text.toString().trim()
        val card = findViewById<View>(R.id.welcome_card)
        bindCard(card, Profile(name = name, title = title))
        // An empty handle shows a dim "yourname" here, so the preview doesn't repeat the ~/tilde header.
        val handle = Profile.cleanHandle(findViewById<TextInputEditText>(R.id.welcome_handle).text.toString())
        findViewById<TextView>(R.id.welcome_preview_handle).text = if (handle.isNotEmpty()) handleLine(handle) else
            SpannableStringBuilder(getString(R.string.brand_prefix)).apply {
                setSpan(ForegroundColorSpan(getColor(R.color.accent)), 0, length, 0)
                val start = length
                append(getString(R.string.welcome_handle_placeholder))
                setSpan(ForegroundColorSpan(getColor(R.color.dim)), start, length, 0)
            }
        card.findViewById<TextView>(R.id.name).apply {
            text = name.ifEmpty { getString(R.string.welcome_name) }
            setTextColor(getColor(if (name.isEmpty()) R.color.dim else R.color.text))
        }
        card.findViewById<TextView>(R.id.card_title).apply {
            text = title.ifEmpty { getString(R.string.welcome_title_field) }
            setTextColor(getColor(if (title.isEmpty()) R.color.dim else R.color.muted))
            visibility = View.VISIBLE
        }
        findViewById<MaterialButton>(R.id.welcome_photo).setText(if (photo != null) R.string.photo_change else R.string.photo_add)
    }

    private fun showSecondPhone(show: Boolean) {
        secondPhone = show
        findViewById<View>(R.id.welcome_phone2_row).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.welcome_phone_add).visibility = if (show) View.GONE else View.VISIBLE
        // Enter on the first number goes to the second when it's open, otherwise on to the link.
        findViewById<View>(R.id.welcome_phone1).apply {
            nextFocusDownId = if (show) R.id.welcome_phone2 else R.id.welcome_website
            nextFocusForwardId = nextFocusDownId
        }
        if (show) findViewById<View>(R.id.welcome_phone2).requestFocus()
    }

    /** Sets a number's country: the button shows its flag and code, and typing formats for it. */
    private fun setPhoneCountry(index: Int, country: Country) {
        phoneCountries[index] = country
        findViewById<MaterialButton>(if (index == 0) R.id.welcome_country1 else R.id.welcome_country2).apply {
            text = getString(R.string.welcome_country_code, country.flag, country.dial)
            contentDescription = getString(R.string.welcome_country, countryName(country))
        }
        val field = findViewById<TextInputEditText>(if (index == 0) R.id.welcome_phone1 else R.id.welcome_phone2)
        phoneFormatters[index]?.let(field::removeTextChangedListener)
        phoneFormatters[index] = PhoneNumberFormattingTextWatcher(country.iso).also(field::addTextChangedListener)
    }

    private fun countryName(country: Country): String =
        runCatching { Locale.Builder().setRegion(country.iso).build().displayCountry }.getOrNull()?.takeIf { it.isNotBlank() } ?: country.iso

    /** A searchable list of countries; the ones already in use come first. */
    private fun pickCountry(index: Int) {
        val dialog = BottomSheetDialog(this)
        val named = Countries.all.map { it to countryName(it) }.sortedBy { it.second.lowercase() }
        val pinned = phoneCountries.distinct().map { it to countryName(it) }
        val adapter = object : ArrayAdapter<Pair<Country, String>>(this, android.R.layout.simple_list_item_1) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    val (country, name) = getItem(position)!!
                    text = getString(R.string.welcome_country_row, country.flag, name, country.dial)
                    setTextColor(getColor(R.color.text))
                }
        }
        fun show(query: String) {
            adapter.clear()
            adapter.addAll(if (query.isBlank()) pinned + named.filterNot { it in pinned } else named.filter { (c, n) -> Countries.matches(c, n, query) })
        }
        val search = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.welcome_country_search)
        }
        val query = TextInputEditText(search.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            addTextChangedListener(afterChange { show(it) })
        }
        search.addView(query)
        val list = ListView(this).apply {
            this.adapter = adapter
            divider = null
            setOnItemClickListener { _, _, position, _ ->
                adapter.getItem(position)?.let { setPhoneCountry(index, it.first) }
                dialog.dismiss()
            }
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(TextView(context).apply {
                setText(R.string.welcome_country_title)
                setTextColor(getColor(R.color.text))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.6).toInt()))
        }
        show("")
        dialog.setContentView(sheet)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    /** A number as typed, in international form ("+351 912 345 678"), or null when it's empty. */
    private fun welcomePhone(index: Int): Pair<String, String>? {
        val typed = findViewById<TextInputEditText>(if (index == 0) R.id.welcome_phone1 else R.id.welcome_phone2).text.toString().trim()
        if (typed.isEmpty()) return null
        val country = phoneCountries[index]
        val e164 = PhoneNumberUtils.formatNumberToE164(typed, country.iso)
        // Formatting for a country with a different code gives the international layout, with spaces.
        val other = if (country.dial == "44") "US" else "GB"
        val pretty = e164?.let { PhoneNumberUtils.formatNumber(it, other) } ?: e164 ?: Countries.international(country, typed)
        return country.iso to pretty
    }

    private fun finishWelcome() {
        fun text(id: Int) = findViewById<TextInputEditText>(id).text.toString().trim()
        val numbers = listOfNotNull(welcomePhone(0), if (secondPhone) welcomePhone(1) else null)
        val labels = Countries.phoneLabels(numbers.map { it.first })
        val link = text(R.id.welcome_website)
        prefs.profile = prefs.profile.copy(
            name = text(R.id.welcome_name),
            title = text(R.id.welcome_job),
            email = text(R.id.welcome_email),
            phones = numbers.mapIndexed { i, (_, number) -> labels[i] to number },
            handle = Profile.cleanHandle(text(R.id.welcome_handle)),
        ).let { if (link.isEmpty()) it else it.withLink(link) }
            .let { if (it.handle.isBlank()) it.copy(handle = it.suggestedHandle) else it }
        // A tap shares the link they gave (as LinkedIn, say, if it's a LinkedIn profile), otherwise the contact card.
        prefs.share = when {
            link.isEmpty() -> Presets.CONTACT
            else -> Profile.platformOf(link) ?: Presets.WEBSITE
        }
        prefs.tab = Prefs.TAB_SHARE
        prefs.welcomed = true
        welcomeBack.isEnabled = false
        hideKeyboard()
        // The Share screen says "You're all set" once, and offers Write a card if they chose a card.
        prefs.ready = if (withCard) Prefs.READY_CARD else Prefs.READY_PHONE
        // Settings' fields were filled when the screen opened; show the new profile there too.
        recreate()
    }

    private fun showKeyboard(field: View) {
        field.requestFocus()
        // After the step has laid out, or the keyboard ignores the request.
        field.post { getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
    }

    /** Closes the keyboard. Uses the window, not the focused field: hiding a step clears that focus first. */
    private fun hideKeyboard() {
        currentFocus?.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun afterChange(run: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = run(s?.toString().orEmpty())
    }

    /**
     * Enter on a step's last field goes on to the next step: the on-screen keyboard's Done key, or
     * Enter on a hardware keyboard (which arrives as IME_NULL with a key event).
     */
    private fun TextInputEditText.onDone(run: () -> Unit) = setOnEditorActionListener { _, action, event ->
        val enter = action == EditorInfo.IME_ACTION_DONE ||
            action == EditorInfo.IME_NULL && event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER
        // A hardware Enter acts on release: acting on the press would hand its release to the next
        // step's first field, which would then skip ahead a field.
        if (enter && (event == null || event.action == android.view.KeyEvent.ACTION_UP)) run()
        enter
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // Card emulation only works with the screen on, so keep it on while the app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs.store.registerOnSharedPreferenceChangeListener(prefsListener)
        lastReads = prefs.reads
        applyNfcMode()
        applyBrightness()
        // The Cards screen may have switched or deleted the active card.
        if (!welcoming && filledCardId != prefs.activeCardId) fillCardFields()
        render()
        // Only here: a redraw just before the welcome screens' recreate() would show it on the old screen.
        if (!welcoming && prefs.ready.isNotEmpty()) showReady()
    }

    override fun onPause() {
        resumed = false
        adapter?.let { nfc ->
            nfc.disableReaderMode(this)
            CardEmulation.getInstance(nfc).unsetPreferredService(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) runCatching { nfc.resetDiscoveryTechnology(this) }
        }
        prefs.store.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun navId(tab: String) = when (tab) {
        Prefs.TAB_RECEIVE -> R.id.nav_receive
        Prefs.TAB_MET -> R.id.nav_met
        Prefs.TAB_SETTINGS -> R.id.nav_settings
        else -> R.id.nav_share
    }

    // ---- NFC ----

    /** Receive polls for tags in reader mode; every other tab answers readers as a tag. */
    private fun applyNfcMode() {
        val nfc = adapter ?: return
        if (!resumed) return
        if (prefs.tab == Prefs.TAB_RECEIVE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) runCatching { nfc.resetDiscoveryTechnology(this) }
            CardEmulation.getInstance(nfc).unsetPreferredService(this)
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
            nfc.enableReaderMode(this, this, flags, null)
        } else {
            nfc.disableReaderMode(this)
            // Answer readers ahead of any other app registered for the same AID.
            CardEmulation.getInstance(nfc).setPreferredService(this, service)
            // Two phones back to back can both act as readers. Stop polling so this one only listens.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching { nfc.setDiscoveryTechnology(this, NfcAdapter.FLAG_READER_DISABLE, NfcAdapter.FLAG_LISTEN_KEEP) }
            }
        }
    }

    /** Full brightness on the Share tab, so the QR code scans in dim rooms; the system's level elsewhere. */
    private fun applyBrightness() {
        window.attributes = window.attributes.apply {
            screenBrightness = if (resumed && prefs.tab == Prefs.TAB_SHARE) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    /** Receive mode: called on a binder thread for each tag or phone that comes into range. */
    override fun onTagDiscovered(tag: Tag) {
        val bytes = runCatching {
            NdefTech.get(tag)?.use { ndef ->
                ndef.connect()
                (ndef.ndefMessage ?: ndef.cachedNdefMessage)?.toByteArray()
            }
        }.getOrNull()
        val items = bytes?.let { Received.fromNdef(it) }.orEmpty()
        main.post {
            if (items.isEmpty()) {
                Toast.makeText(this, R.string.received_nothing, Toast.LENGTH_SHORT).show()
            } else {
                prefs.received = items + prefs.received
                // Someone handed over their card: that's a person met too.
                items.firstOrNull { it.kind == Received.CONTACT }?.let { card ->
                    prefs.met = MetLog.add(prefs.met, Meeting(System.currentTimeMillis(), prefs.event, MetLog.RECEIVED, card.title, prefs.activeCard.label))
                }
                buzz()
            }
        }
    }

    /** A reader just read the whole message from this phone. */
    private fun onRead() {
        val now = prefs.reads
        if (now > lastReads) {
            buzz()
            if (prefs.tab != Prefs.TAB_RECEIVE) {
                sent.visibility = View.VISIBLE
                main.removeCallbacksAndMessages(SENT_TOKEN)
                // Long enough to reach for "Add a note".
                main.postAtTime({ sent.visibility = View.GONE }, SENT_TOKEN, SystemClock.uptimeMillis() + 8000)
            }
        }
        lastReads = now
        render()
    }

    private fun buzz() = Haptics.buzz(this)

    // ---- Set-up ----

    private fun setUpShare() {
        findViewById<View>(R.id.share_row).setOnClickListener { showPicker() }
        findViewById<View>(R.id.set_up_profile).setOnClickListener { nav.selectedItemId = R.id.nav_settings }
        findViewById<View>(R.id.open_settings).setOnClickListener { nav.selectedItemId = R.id.nav_settings }
        findViewById<View>(R.id.nfc_settings).setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        findViewById<View>(R.id.sent_done).setOnClickListener { sent.visibility = View.GONE }
        findViewById<View>(R.id.sent_note).setOnClickListener {
            sent.visibility = View.GONE
            prefs.met.firstOrNull()?.let { editNote(it) }
        }
        findViewById<View>(R.id.share_send).setOnClickListener { sendCurrent() }
        findViewById<View>(R.id.share_copy).setOnClickListener { copy(prefs.urlFor(prefs.share, event = "")) }

        // The code: tap to show it bigger; swipe sideways to step through the quick-switch row.
        val qrCard = findViewById<View>(R.id.qr_card)
        qrCard.setOnClickListener { enlargeQr() }
        qrCard.setOnTouchListener(Swipe(qrCard, { prefs.quickSwitch().size >= 2 }, ::stepQuickSwitch))

        // The profile card: swipe to the next card (who), as the code swipes what's shared; tap to choose.
        val profileCard = findViewById<View>(R.id.profile_card)
        profileCard.setOnClickListener { showCardSwitcher() }
        profileCard.setOnTouchListener(Swipe(profileCard, { prefs.cards.size >= 2 }, ::stepCard))
        findViewById<View>(R.id.brand).setOnClickListener { showCardSwitcher() }
    }

    /**
     * Sideways swipes on the code (next option) and the profile card (next card). The Share screen
     * scrolls vertically, and its ScrollView takes over a touch as soon as it moves a little up or
     * down, so a slightly diagonal swipe used to be lost. Once a touch is clearly sideways, the view
     * keeps it (requestDisallowInterceptTouchEvent). A swipe counts by distance (a fifth of the
     * view's width), not speed, so slow drags work too; the view follows the finger a little and
     * springs back. A touch that barely moves is a tap (performClick).
     */
    private inner class Swipe(
        private val card: View,
        private val canSwipe: () -> Boolean,
        private val onSwipe: (Int) -> Unit,
    ) : View.OnTouchListener {
        private val slop = android.view.ViewConfiguration.get(this@MainActivity).scaledTouchSlop
        private var startX = 0f
        private var startY = 0f
        private var swiping = false
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val dx = event.rawX - startX
            val dy = event.rawY - startY
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    swiping = false
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    // Sideways more than up or down: keep the touch from the scrolling page.
                    if (!swiping && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) && canSwipe()) {
                        swiping = true
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (swiping) card.translationX = dx * 0.4f
                }
                MotionEvent.ACTION_UP -> {
                    if (swiping && kotlin.math.abs(dx) > card.width / 5f) onSwipe(if (dx < 0) 1 else -1)
                    else if (!moved) view.performClick()
                    settle()
                }
                MotionEvent.ACTION_CANCEL -> settle()
            }
            return true
        }

        private fun settle() {
            swiping = false
            card.animate().translationX(0f).setDuration(180).start()
        }
    }

    /** Swiping the code: the next (or previous) option in the quick-switch row, round and round. */
    private fun stepQuickSwitch(direction: Int) {
        val options = prefs.quickSwitch()
        if (options.size < 2) return
        val current = options.indexOfFirst { it.id == prefs.share }
        val next = if (current < 0) 0 else Math.floorMod(current + direction, options.size)
        prefs.share = options[next].id
        findViewById<View>(R.id.qr_card).performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Tapping the status badge: what the state means, with the fix one tap away: Android's NFC
     * settings when NFC is off, resuming when taps are paused, pausing when ready.
     */
    private fun explainState(state: Int) {
        val dialog = MaterialAlertDialogBuilder(this).setTitle(state)
        when (state) {
            R.string.state_ready -> dialog.setMessage(R.string.state_ready_detail)
                .setNeutralButton(R.string.state_pause) { _, _ -> prefs.enabled = false }
            R.string.state_nfc_off -> dialog.setMessage(R.string.state_nfc_off_detail)
                .setNeutralButton(R.string.open_nfc_settings) { _, _ -> runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } }
            R.string.state_paused -> dialog.setMessage(R.string.state_paused_detail)
                .setNeutralButton(R.string.state_resume) { _, _ -> prefs.enabled = true }
            else -> dialog.setMessage(R.string.state_no_nfc_detail)
        }
        dialog.setPositiveButton(R.string.done, null).show()
    }

    /** Swiping the profile card: the next (or previous) card, round and round. */
    private fun stepCard(direction: Int) {
        val next = Cards.step(prefs.cards, prefs.activeCardId, direction) ?: return
        switchCard(next.id)
        findViewById<View>(R.id.profile_card).performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Makes a card active: the Share screen, taps and Settings' "This card" fields all follow. */
    private fun switchCard(id: String) {
        if (id == prefs.activeCardId) return
        prefs.activeCardId = id
        fillCardFields()
        render()
        Snackbar.make(nav, getString(R.string.cards_switched, prefs.activeCard.label), Snackbar.LENGTH_SHORT).setAnchorView(nav).show()
    }

    /** The card switcher: every card with a check on the active one, then New card and Manage cards. */
    private fun showCardSwitcher() {
        val sheet = BottomSheetDialog(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        list.addView(TextView(this).apply {
            setText(R.string.cards_title)
            setTextColor(getColor(R.color.text))
            textSize = 18f
            setPadding(dp(20), dp(8), dp(20), dp(12))
        })
        val activeId = prefs.activeCardId
        for (card in prefs.cards) {
            list.addView(CardDialogs.row(this, card, active = card.id == activeId).apply {
                setOnClickListener {
                    sheet.dismiss()
                    switchCard(card.id)
                }
            })
        }
        fun action(text: Int, icon: Int, run: () -> Unit) = list.addView(
            MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                setText(text)
                setIconResource(icon)
                iconTint = getColorStateList(R.color.accent)
                setTextColor(getColor(R.color.accent))
                setPadding(dp(20), 0, dp(20), 0)
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setOnClickListener { sheet.dismiss(); run() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)),
        )
        action(R.string.cards_new, R.drawable.ic_add) { newCard() }
        action(R.string.cards_manage, R.drawable.ic_nav_settings) { startActivity(Intent(this, CardsActivity::class.java)) }
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
        sheet.show()
    }

    /** New card, from the switcher or Settings. A blank card goes straight to Settings to be filled in. */
    private fun newCard() {
        CardDialogs.newCard(this, prefs) { card, copied ->
            fillCardFields()
            if (!copied) nav.selectedItemId = R.id.nav_settings
            val message = getString(if (copied) R.string.cards_created_copy else R.string.cards_created_blank, card.label)
            Snackbar.make(nav, message, Snackbar.LENGTH_LONG).setAnchorView(nav).show()
        }
    }

    /**
     * Puts the active card's values into Settings' "This card" fields: after switching, creating or
     * deleting a card. `filling` stops the fields' save-as-you-type from writing them straight back.
     */
    private fun fillCardFields() {
        val profile = prefs.profile
        val values = mapOf(
            R.id.profile_name to profile.name, R.id.profile_title to profile.title,
            R.id.profile_email to profile.email, R.id.profile_email2 to profile.email2,
            R.id.profile_website to profile.website, R.id.profile_handle to profile.handle,
            R.id.profile_linkedin to profile.linkedin, R.id.profile_github to profile.github,
            R.id.profile_instagram to profile.instagram, R.id.profile_x to profile.x,
            R.id.profile_whatsapp to profile.whatsapp, R.id.whatsapp_greeting to prefs.whatsappGreeting,
            R.id.phone1_label to profile.phones.getOrNull(0)?.first.orEmpty(), R.id.phone1_number to profile.phones.getOrNull(0)?.second.orEmpty(),
            R.id.phone2_label to profile.phones.getOrNull(1)?.first.orEmpty(), R.id.phone2_number to profile.phones.getOrNull(1)?.second.orEmpty(),
        )
        filling = true
        for ((id, value) in values) findViewById<TextInputEditText>(id).setText(value)
        filling = false
        filledCardId = prefs.activeCardId
    }

    /** The code full screen on its light background, for scanning from further away. Tap to close. */
    private fun enlargeQr() {
        val code = prefs.qrText().takeIf { it.isNotEmpty() } ?: return
        val opens = findViewById<TextView>(R.id.qr_opens).text
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(LIGHT)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setOnClickListener { dialog.dismiss() }
            addView(ImageView(context).apply {
                setImageBitmap(qrBitmap(code))
                adjustViewBounds = true
                contentDescription = opens
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = opens
                setTextColor(DARK)
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
            })
            addView(TextView(context).apply {
                setText(R.string.qr_close)
                setTextColor(getColor(R.color.qr_hint))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }
        dialog.setContentView(page)
        dialog.window?.attributes = dialog.window?.attributes?.apply { screenBrightness = 1f }
        dialog.show()
    }

    /**
     * Send: the current link as text, or the contact card as a .vcf file, handed to whichever app
     * they choose (WhatsApp, email, messages). Tilde itself still never goes online.
     */
    private fun sendCurrent() {
        val preset = prefs.find(prefs.share)
        val send = if (preset.id == Presets.CONTACT) {
            val profile = prefs.profile
            val name = profile.name.filter { it.isLetterOrDigit() || it == ' ' }.trim().ifEmpty { "contact" }
            val file = File(cacheDir, "shared").apply { mkdirs() }.resolve("$name.vcf")
            file.writeText(profile.vcard())
            Intent(Intent.ACTION_SEND)
                .setType("text/x-vcard")
                .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, "$packageName.files", file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prefs.urlFor(preset.id, event = ""))
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_send_title)))
    }

    /** "Set up" in the picker: a quick dialog for a missing link or number, otherwise Settings. */
    private fun setUpOption(preset: Presets.Preset) {
        val asked = LinkDialogs.setUpOption(this, prefs, preset) { prefs.share = preset.id }
        if (asked) return
        nav.selectedItemId = R.id.nav_settings
        val target = findViewById<View>(if (preset.id == Presets.WIFI) R.id.wifi_ssid else R.id.profile_name)
        val scroll = findViewById<ScrollView>(R.id.settings_panel)
        // Settings was hidden until now: measure once it has been laid out, just before it draws.
        androidx.core.view.OneShotPreDrawListener.add(scroll) {
            var top = 0
            var view: View? = target
            while (view != null && view != scroll) { top += view.top; view = view.parent as? View }
            scroll.smoothScrollTo(0, (top - dp(96)).coerceAtLeast(0))
        }
        Snackbar.make(nav, R.string.setup_in_settings, Snackbar.LENGTH_LONG).setAnchorView(nav).show()
    }

    private fun setUpReceive() {
        findViewById<View>(R.id.clear_received).setOnClickListener { prefs.received = emptyList() }
    }

    private fun setUpMet() {
        findViewById<View>(R.id.met_add).setOnClickListener {
            editNote(Meeting(System.currentTimeMillis(), prefs.event, getString(R.string.met_manual), "", prefs.activeCard.label), isNew = true)
        }
        findViewById<View>(R.id.met_export).setOnClickListener { export() }
    }

    private fun setUpSettings() {
        // Profile: each field rewrites the stored profile as you type.
        val profile = prefs.profile
        fun profileField(id: Int, value: String, update: Profile.(String) -> Profile) =
            field(id, value) { text -> prefs.profile = prefs.profile.update(text.trim()) }
        profileField(R.id.profile_name, profile.name) { copy(name = it) }
        profileField(R.id.profile_title, profile.title) { copy(title = it) }
        profileField(R.id.profile_email, profile.email) { copy(email = it) }
        profileField(R.id.profile_email2, profile.email2) { copy(email2 = it) }
        profileField(R.id.profile_website, profile.website) { copy(website = it) }
        profileField(R.id.profile_handle, profile.handle) { copy(handle = it) }
        findViewById<TextInputEditText>(R.id.profile_handle).filters = handleFilters
        profileField(R.id.profile_linkedin, profile.linkedin) { copy(linkedin = it) }
        profileField(R.id.profile_github, profile.github) { copy(github = it) }
        profileField(R.id.profile_instagram, profile.instagram) { copy(instagram = it) }
        profileField(R.id.profile_x, profile.x) { copy(x = it) }
        profileField(R.id.profile_whatsapp, profile.whatsapp) { copy(whatsapp = it) }
        findViewById<View>(R.id.photo_pick).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<View>(R.id.photo_remove).setOnClickListener {
            Photo.delete(this, prefs.activeCardId)
            prefs.photoVersion += 1
        }
        val phoneFields = listOf(R.id.phone1_label, R.id.phone1_number, R.id.phone2_label, R.id.phone2_number)
            .map { findViewById<TextInputEditText>(it) }
        fun phonesFromFields() = phoneFields.chunked(2)
            .map { (label, number) -> label.text.toString().trim() to number.text.toString().trim() }
            .filter { it.second.isNotEmpty() }
        phoneFields.forEachIndexed { i, view ->
            val phone = profile.phones.getOrNull(i / 2)
            field(view.id, if (i % 2 == 0) phone?.first.orEmpty() else phone?.second.orEmpty()) {
                prefs.profile = prefs.profile.copy(phones = phonesFromFields())
            }
        }

        findViewById<MaterialSwitch>(R.id.toggle).apply {
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }
        }
        field(R.id.event, prefs.event) { prefs.event = it.trim() }
        field(R.id.whatsapp_greeting, prefs.whatsappGreeting) { prefs.whatsappGreeting = it }
        findViewById<View>(R.id.links_add).setOnClickListener { LinkDialogs.editLink(this, prefs, null) {} }
        // Wi-Fi names and passwords can start or end with a space, so they stay exactly as typed.
        field(R.id.wifi_ssid, prefs.wifiSsid) { prefs.wifiSsid = it }
        field(R.id.wifi_password, prefs.wifiPassword) { prefs.wifiPassword = it }
        findViewById<MaterialCheckBox>(R.id.wifi_open).apply {
            isChecked = prefs.wifiOpen
            setOnCheckedChangeListener { _, checked -> prefs.wifiOpen = checked }
        }
        // Android's own Wi-Fi screen can show any saved network's password (Share), which apps can't read.
        findViewById<View>(R.id.wifi_settings).setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
        findViewById<View>(R.id.tile_section).visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        findViewById<View>(R.id.write_card).setOnClickListener { startActivity(Intent(this, WriteActivity::class.java).putExtra("demo", demo)) }
        findViewById<View>(R.id.add_tile).setOnClickListener { requestTile() }
        findViewById<TextView>(R.id.version).text = getString(R.string.version, BuildConfig.VERSION_NAME)
        findViewById<View>(R.id.settings_card_switch).setOnClickListener { showCardSwitcher() }
        filledCardId = prefs.activeCardId
        // Debug builds only: back to one empty card (photos deleted), and start the welcome screens again.
        findViewById<View>(R.id.debug_welcome).apply {
            visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            setOnClickListener {
                prefs.resetCards()
                prefs.photoVersion += 1
                prefs.welcomed = false
                recreate()
            }
        }
    }

    /** Fields save as you type: switching tabs or scanning straight after typing must not lose text. */
    private fun field(id: Int, value: String, save: (String) -> Unit) {
        findViewById<TextInputEditText>(id).apply {
            // The stored value is the truth. Restoring the view's own copy after a recreate would fire
            // the watcher below with stale text and overwrite newer settings.
            isSaveEnabled = false
            setText(value)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!filling) save(s?.toString().orEmpty())
                }
            })
        }
    }

    // ---- Rendering ----

    private fun render() {
        val welcome = welcoming
        findViewById<View>(R.id.welcome_panel).visibility = if (welcome) View.VISIBLE else View.GONE
        nav.visibility = if (welcome) View.GONE else View.VISIBLE
        if (welcome) {
            for (panel in panels.values) panel.visibility = View.GONE
            bindWelcomePreview()
            return
        }
        val tab = prefs.tab
        for ((name, panel) in panels) panel.visibility = if (name == tab) View.VISIBLE else View.GONE
        if (nav.selectedItemId != navId(tab)) nav.selectedItemId = navId(tab)
        when (tab) {
            Prefs.TAB_RECEIVE -> renderReceived()
            Prefs.TAB_MET -> renderMet()
            Prefs.TAB_SETTINGS -> renderSettings()
            else -> renderShare()
        }
    }

    private fun renderShare() {
        val profile = prefs.profile
        val cards = prefs.cards
        val active = prefs.activeCard
        findViewById<TextView>(R.id.brand).apply {
            // With more than one card, the card's label follows the handle: "~/janedoe · Work ▾".
            text = handleLine(profile.handle).apply {
                val start = length
                append(if (cards.size > 1) "  ·  ${active.label}  ▾" else "  ▾")
                setSpan(ForegroundColorSpan(getColor(R.color.dim)), start, length, 0)
            }
            contentDescription = getString(R.string.cards_switch_description, active.label)
        }
        findViewById<View>(R.id.profile_card).apply {
            visibility = if (profile.isSet) View.VISIBLE else View.GONE
            bindCard(this, profile, colour = active.colour)
        }
        findViewById<View>(R.id.set_up_profile).visibility = if (profile.isSet) View.GONE else View.VISIBLE
        // If what was selected can't share any more (say, its link was deleted), fall back.
        if (!prefs.isReady(prefs.share)) prefs.available().firstOrNull()?.let { prefs.share = it.id }
        val nfc = adapter
        val canEmulate = demo || nfc != null && packageManager.hasSystemFeature("android.hardware.nfc.hce")
        val nfcOn = demo || nfc?.isEnabled == true
        val tapping = canEmulate && nfcOn && prefs.enabled
        val preset = prefs.find(prefs.share)
        val wifiMissing = preset.id == Presets.WIFI && !prefs.wifiReady

        findViewById<TextView>(R.id.state).apply {
            val (label, colour) = when {
                !canEmulate -> R.string.state_no_nfc to R.color.muted
                !nfcOn -> R.string.state_nfc_off to R.color.accent
                !prefs.enabled -> R.string.state_paused to R.color.muted
                else -> R.string.state_ready to R.color.ok
            }
            text = getString(label)
            setTextColor(getColor(colour))
            contentDescription = getString(R.string.state_description, getString(label))
            setOnClickListener { explainState(label) }
        }
        findViewById<TextView>(R.id.tagline).setText(if (tapping) R.string.tagline_tap else R.string.tagline_scan)
        findViewById<View>(R.id.nfc_settings).visibility = if (nfc != null && !nfcOn) View.VISIBLE else View.GONE

        findViewById<View>(R.id.qr_card).visibility = if (wifiMissing) View.GONE else View.VISIBLE
        findViewById<View>(R.id.wifi_missing).visibility = if (wifiMissing) View.VISIBLE else View.GONE
        if (!wifiMissing) findViewById<ImageView>(R.id.qr).setImageBitmap(prefs.qrText().takeIf { it.isNotEmpty() }?.let(::qrBitmap))
        findViewById<TextView>(R.id.iphone_hint).apply {
            visibility = if (tapping && !preset.iphoneTap && !wifiMissing) View.VISIBLE else View.GONE
            val opens = PresetRows.iphoneOpens(this@MainActivity, profile)
            text = when {
                preset.id == Presets.WIFI -> getString(R.string.iphone_hint_wifi)
                opens != null -> getString(R.string.iphone_hint_contact_link, opens)
                else -> getString(R.string.iphone_hint_contact)
            }
        }

        rows.bind(findViewById(R.id.share_row_content), preset, selected = false)

        // What the code opens, for the other person, and as the code's description for screen readers.
        val opens = when (preset.id) {
            Presets.CONTACT -> getString(R.string.opens_contact)
            Presets.WHATSAPP -> getString(R.string.opens_whatsapp)
            Presets.WIFI -> getString(R.string.opens_wifi, prefs.wifiSsid)
            else -> PresetRows.bare(prefs.urlFor(preset.id, event = "")).takeIf { it.isNotEmpty() }?.let { getString(R.string.opens_link, it) }
        }
        findViewById<TextView>(R.id.qr_opens).apply {
            text = opens.orEmpty()
            visibility = if (opens != null && !wifiMissing) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.qr_card).contentDescription =
            listOfNotNull(getString(R.string.qr_description), opens, getString(R.string.qr_enlarge)).joinToString(". ")

        // The quick-switch row: shown once there are two or more starred options ready to share.
        val quick = prefs.quickSwitch()
        val chips = findViewById<ChipGroup>(R.id.quick_switch)
        findViewById<View>(R.id.quick_switch_scroll).visibility = if (quick.size >= 2) View.VISIBLE else View.GONE
        chips.removeAllViews()
        for (option in quick) {
            val chip = layoutInflater.inflate(R.layout.chip_option, chips, false) as Chip
            chip.text = option.label
            chip.isChecked = option.id == preset.id
            chip.setOnClickListener { prefs.share = option.id }
            chips.addView(chip)
            if (chip.isChecked) chips.post {
                findViewById<android.widget.HorizontalScrollView>(R.id.quick_switch_scroll).smoothScrollTo((chip.left - dp(24)).coerceAtLeast(0), 0)
            }
        }

        // Send and Copy: not for Wi-Fi (its password stays off other apps); Copy is for links only.
        findViewById<View>(R.id.share_actions).visibility = if (preset.id == Presets.WIFI) View.GONE else View.VISIBLE
        findViewById<View>(R.id.share_copy).visibility =
            if (preset.id == Presets.CONTACT || prefs.urlFor(preset.id, event = "").isEmpty()) View.GONE else View.VISIBLE

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = prefs.met.count { it.time >= startOfDay && it.shared != MetLog.RECEIVED && it.shared != getString(R.string.met_manual) }
        val meta = listOfNotNull(
            prefs.event.takeIf { it.isNotBlank() && preset.id != Presets.WIFI && preset.id != Presets.WHATSAPP }?.let { getString(R.string.meta_event, it) },
            today.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.meta_sent_today, it, it) },
        )
        findViewById<TextView>(R.id.meta).apply {
            text = meta.joinToString(" · ")
            visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** The picker: what a tap shares, with a note under the options iPhones can only scan. */
    private fun showPicker() {
        rows.showPicker(R.string.picker_title, prefs.share, onSetUp = ::setUpOption) { preset -> prefs.share = preset.id }
    }

    /** The ~/handle line, with "tilde" standing in for an empty handle. */
    private fun handleLine(handle: String) = SpannableStringBuilder(getString(R.string.brand_prefix)).apply {
        setSpan(ForegroundColorSpan(getColor(R.color.accent)), 0, length, 0)
        append(handle.ifBlank { getString(R.string.brand_name) })
    }

    /** Typing in a handle field: lower case and the allowed characters only, up to the maximum length. */
    private val handleFilters = arrayOf(
        InputFilter { source, start, end, _, _, _ ->
            val typed = source.subSequence(start, end).toString()
            Profile.handleChars(typed).takeIf { it != typed }
        },
        InputFilter.LengthFilter(Profile.HANDLE_MAX),
    )

    /** "Your card is ready": once, after the welcome screens, with Write a card for those who chose a card. */
    private fun showReady() {
        val card = prefs.ready == Prefs.READY_CARD
        prefs.ready = ""
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.sheet_ready)
        dialog.findViewById<MaterialButton>(R.id.ready_primary)?.apply {
            setText(if (card) R.string.ready_write else R.string.ready_done)
            setOnClickListener {
                dialog.dismiss()
                if (card) startActivity(Intent(this@MainActivity, WriteActivity::class.java).putExtra("demo", demo).putExtra(WriteActivity.EXTRA_WELCOME, true))
            }
        }
        dialog.findViewById<View>(R.id.ready_later)?.apply {
            visibility = if (card) View.VISIBLE else View.GONE
            setOnClickListener { dialog.dismiss() }
        }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    /** Fills a business card (view_card): photo or initials, name, and title when there is one. */
    private fun bindCard(card: View, profile: Profile, usePhoto: Boolean = true, colour: String? = null) {
        card.findViewById<View>(R.id.card_band).setBackgroundColor(Cards.colour(colour ?: Cards.COLOURS.first().key).argb.toInt())
        card.findViewById<TextView>(R.id.name).text = profile.name
        card.findViewById<TextView>(R.id.card_title).apply {
            text = profile.title
            visibility = if (profile.title.isBlank()) View.GONE else View.VISIBLE
        }
        bindAvatar(card.findViewById(R.id.card_avatar), profile, usePhoto)
    }

    /** The photo if there is one (and `usePhoto`), otherwise the initials, in a circle. */
    private fun bindAvatar(avatar: View, profile: Profile, usePhoto: Boolean = true) {
        // Reload when the photo changed or another card became active.
        val photoKey = "${prefs.activeCardId}:${prefs.photoVersion}"
        if (photoLoaded != photoKey) {
            photo = Photo.load(this, prefs.activeCardId)
            photoLoaded = photoKey
        }
        val shown = photo.takeIf { usePhoto }
        val image = avatar.findViewById<ImageView>(R.id.avatar_photo)
        val initials = avatar.findViewById<TextView>(R.id.avatar_initials)
        image.setImageBitmap(shown)
        image.visibility = if (shown != null) View.VISIBLE else View.GONE
        initials.visibility = if (shown != null) View.GONE else View.VISIBLE
        initials.text = listOf(profile.firstName, profile.lastName)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "~" }
    }

    private fun renderSettings() {
        prefs.activeCard.let { card ->
            findViewById<TextView>(R.id.settings_card_label).text = card.label
            findViewById<View>(R.id.settings_card_dot).backgroundTintList =
                android.content.res.ColorStateList.valueOf(Cards.colour(card.colour).argb.toInt())
        }
        bindAvatar(findViewById(R.id.settings_avatar), prefs.profile)
        findViewById<MaterialButton>(R.id.photo_pick).setText(if (photo != null) R.string.photo_change else R.string.photo_add)
        findViewById<View>(R.id.photo_remove).visibility = if (photo != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.whatsapp_section).visibility = if (prefs.profile.hasWhatsapp) View.VISIBLE else View.GONE
        findViewById<MaterialSwitch>(R.id.toggle).let { if (it.isChecked != prefs.enabled) it.isChecked = prefs.enabled }
        findViewById<TextInputLayout>(R.id.wifi_password_layout).isEnabled = !prefs.wifiOpen
        findViewById<TextView>(R.id.reads).text = resources.getQuantityString(R.plurals.reads, prefs.reads, prefs.reads)
        // Saved links: tap to edit; the checkbox puts it on the Share screen (the same as its star).
        findViewById<LinearLayout>(R.id.links_list).apply {
            removeAllViews()
            for (link in prefs.links) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                row.addView(listRow(link.name, PresetRows.bare(link.url), emptyList()).apply {
                    setOnClickListener { LinkDialogs.editLink(this@MainActivity, prefs, link) {} }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(MaterialCheckBox(context).apply {
                    isChecked = link.presetId in prefs.pinned
                    contentDescription = getString(R.string.link_on_share, link.name)
                    setOnCheckedChangeListener { _, on ->
                        prefs.pinned = if (on) prefs.pinned + link.presetId else prefs.pinned - link.presetId
                    }
                })
                addView(row)
            }
        }
    }

    private fun renderReceived() {
        val items = prefs.received
        findViewById<View>(R.id.received_empty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.clear_received).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        val list = findViewById<LinearLayout>(R.id.received_list)
        list.removeAllViews()
        for (item in items) {
            val actions = buildList {
                when (item.kind) {
                    Received.LINK -> add(R.string.action_open to { open(item.payload) })
                    Received.CONTACT -> add(R.string.action_save_contact to { saveContact(item.payload) })
                }
                if (item.payload.isNotEmpty()) add(R.string.action_copy to { copy(item.payload) })
            }
            list.addView(listRow(item.title, item.detail, actions))
        }
    }

    private fun renderMet() {
        val log = prefs.met
        findViewById<View>(R.id.met_empty).visibility = if (log.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.met_export).visibility = if (log.isEmpty()) View.GONE else View.VISIBLE
        val list = findViewById<LinearLayout>(R.id.met_list)
        list.removeAllViews()
        for (entry in log) {
            val whenText = DateUtils.formatDateTime(
                this, entry.time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            )
            // Which card was shared, once there's more than one.
            val card = entry.card.takeIf { prefs.cards.size > 1 }.orEmpty()
            val detail = listOf(whenText, card, entry.event, entry.shared).filter { it.isNotEmpty() }.joinToString(" · ")
            val row = listRow(entry.note.ifEmpty { getString(R.string.met_no_note) }, detail, emptyList(), muted = entry.note.isEmpty())
            row.setOnClickListener { editNote(entry) }
            list.addView(row)
        }
    }

    /** A list row like the site's bordered lists: title, monospace detail, text-button actions, hairline. */
    private fun listRow(title: String, detail: String, actions: List<Pair<Int, () -> Unit>>, muted: Boolean = false): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
            isClickable = true
            isFocusable = true
            val ripple = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
        }
        row.addView(TextView(this).apply {
            text = title
            setTextColor(getColor(if (muted) R.color.muted else R.color.text))
            textSize = 16f
        })
        row.addView(TextView(this).apply {
            text = detail
            setTextColor(getColor(R.color.dim))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
        })
        if (actions.isNotEmpty()) {
            val bar = LinearLayout(this)
            for ((label, run) in actions) {
                bar.addView(MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                    setText(label)
                    setOnClickListener { run() }
                })
            }
            row.addView(bar)
        }
        row.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.line))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(14) }
        })
        return row
    }

    // ---- Actions ----

    private fun requestTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, ShareTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { }
    }

    /** Edit a note, or with `isNew` add a person by hand (QR scans can't be detected). */
    private fun editNote(entry: Meeting, isNew: Boolean = false) {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.met_note_hint)
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val field = TextInputEditText(layout.context).apply {
            setText(entry.note)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        layout.addView(field)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.met_note_title)
            .setView(layout)
            .setPositiveButton(R.string.met_save) { _, _ ->
                val note = field.text.toString().trim()
                prefs.met = if (isNew) MetLog.add(prefs.met, entry.copy(note = note)) else MetLog.withNote(prefs.met, entry.time, note)
            }
            .setNegativeButton(R.string.met_cancel, null)
        if (!isNew) dialog.setNeutralButton(R.string.met_delete) { _, _ -> prefs.met = MetLog.without(prefs.met, entry.time) }
        dialog.show()
        field.requestFocus()
        field.setSelection(field.text?.length ?: 0)
    }

    /** Hands the log to email, notes or a spreadsheet app as CSV text. */
    private fun export() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.met_export_subject))
            putExtra(Intent.EXTRA_TEXT, MetLog.csv(prefs.met))
        }
        startActivity(Intent.createChooser(send, getString(R.string.met_export)))
    }

    private fun open(uri: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    }

    /** Fills Android's "new contact" screen; nothing is saved until the user confirms there. */
    private fun saveContact(card: String) {
        val fields = VCard.parse(card)
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, fields.name)
            if (fields.title.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, fields.title)
            if (fields.org.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.COMPANY, fields.org)
            fields.emails.getOrNull(0)?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            fields.emails.getOrNull(1)?.let { putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it) }
            fields.phones.getOrNull(0)?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            fields.phones.getOrNull(1)?.let { putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
            fields.phones.getOrNull(2)?.let { putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, it) }
            val websites = ArrayList(fields.urls.map { url ->
                ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Website.URL, url)
                }
            })
            if (websites.isNotEmpty()) putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, websites)
        }
        runCatching { startActivity(intent) }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tilde", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun qrBitmap(text: String): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val scale = 16
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = IntArray(width * height) { i -> if (matrix[(i % width) / scale, (i / width) / scale]) DARK else LIGHT }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val DARK = Color.parseColor("#0b0d10")
        private val LIGHT = Color.parseColor("#f1efe8")
        private val SENT_TOKEN = Any()
        private const val STATE_STEP = "welcome.step"
        private const val STATE_COUNTRIES = "welcome.countries"
        private const val STATE_SECOND_PHONE = "welcome.secondPhone"
        private const val STATE_WITH_CARD = "welcome.withCard"
        private const val STATE_HANDLE_EDITED = "welcome.handleEdited"
    }
}
