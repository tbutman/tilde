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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
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

    private var photoLoaded = ""

    /** Android's photo picker: no permission needed, and the app only sees the photo chosen. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { cropPhoto.launch(Intent(this, CropActivity::class.java).setData(it)) }
    }
    private val cropPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) prefs.photoVersion += 1
    }

    /** The welcome's "Restore a backup": moving from another phone without making a card first. */
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { RestoreDialogs.restore(this, prefs, it) }
    }

    private lateinit var nav: BottomNavigationView

    // Welcome: the step showing, each number's country, whether the second number is open, and
    // whether a printed card or sticker comes too. Kept across rotation in onSaveInstanceState.
    private var welcomeStep = 0
    /** Set once this screen has shown the tabs; if the welcome is needed after that, it starts afresh. */
    private var shownTabs = false
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
            // Leaving Share ends a Guest Wi-Fi share (it's for one person).
            if (prefs.tab != Prefs.TAB_SHARE) prefs.endWifi()
            applyNfcMode()
            applyBrightness()
            true
        }

        setUpShare()
        setUpReceive()
        setUpMet()
        setUpSettings()
        setUpWelcome(savedInstanceState)
        // Reopened from Recents, the launch intent is the widget's again: don't switch back to its card.
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) openCardFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openCardFrom(intent)
    }

    /** A widget for one card opens Tilde on that card's Share screen. */
    private fun openCardFrom(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_CARD) ?: return
        if (welcoming || prefs.cards.none { it.id == id }) return
        prefs.tab = Prefs.TAB_SHARE
        if (::nav.isInitialized) nav.selectedItemId = R.id.nav_share
        switchCard(id)
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
        val handle = findViewById<TextInputEditText>(R.id.welcome_handle).apply { filters = handleFilters() }
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
        val websiteLayout = findViewById<TextInputLayout>(R.id.welcome_website_layout)
        findViewById<TextInputEditText>(R.id.welcome_website).apply {
            addTextChangedListener(afterChange { websiteLayout.error = null })
            onDone { welcomeNext() }
        }

        // Step 4: phone only, or a card or sticker too (which opens Write a card afterwards).
        val modes = listOf(findViewById<MaterialCardView>(R.id.welcome_mode_phone), findViewById(R.id.welcome_mode_card))
        fun chooseMode(card: Boolean) {
            withCard = card
            modes[0].isChecked = !card
            modes[1].isChecked = card
        }
        modes.forEachIndexed { i, mode -> mode.setOnClickListener { chooseMode(i == 1) } }
        findViewById<View>(R.id.welcome_get_card).setOnClickListener { open(getString(R.string.welcome_get_card_url)) }
        findViewById<View>(R.id.welcome_restore).setOnClickListener { openBackup.launch(RestoreDialogs.TYPES) }

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
                // The link a tap and the code open, so it has to be one ("janedoe.com" is fine).
                if (Profile.linkField(findViewById<TextInputEditText>(R.id.welcome_website).text.toString()) == null) {
                    findViewById<TextInputLayout>(R.id.welcome_website_layout).error = getString(R.string.link_invalid)
                    findViewById<View>(R.id.welcome_website).requestFocus()
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
            contentDescription = getString(R.string.welcome_country, PhoneUi.name(country))
        }
        val field = findViewById<TextInputEditText>(if (index == 0) R.id.welcome_phone1 else R.id.welcome_phone2)
        phoneFormatters[index]?.let(field::removeTextChangedListener)
        phoneFormatters[index] = PhoneNumberFormattingTextWatcher(country.iso).also(field::addTextChangedListener)
    }

    /** The welcome's country buttons: a searchable list, with the countries already in use first. */
    private fun pickCountry(index: Int) = PhoneUi.pickCountry(this, phoneCountries.toList()) { setPhoneCountry(index, it) }

    /** A number as typed, in international form ("+351 912 345 678"), or null when it's empty. */
    private fun welcomePhone(index: Int): Pair<String, String>? {
        val typed = findViewById<TextInputEditText>(if (index == 0) R.id.welcome_phone1 else R.id.welcome_phone2).text.toString().trim()
        if (typed.isEmpty()) return null
        val country = phoneCountries[index]
        return PhoneUi.international(country, typed)?.let { country.iso to it }
    }

    private fun finishWelcome() {
        fun text(id: Int) = findViewById<TextInputEditText>(id).text.toString().trim()
        val numbers = listOfNotNull(welcomePhone(0), if (secondPhone) welcomePhone(1) else null)
        val labels = Countries.phoneLabels(numbers.map { it.first })
        // Checked on Next: "janedoe.com" comes back as https://janedoe.com.
        val link = Profile.linkField(text(R.id.welcome_website)).orEmpty()
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
        // Unless switched off in Settings → Sharing.
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // "Delete entries older than…" (Settings → Met).
        prefs.metKeepMonths.takeIf { it > 0 }?.let { months ->
            val kept = MetLog.keepMonths(prefs.met, System.currentTimeMillis(), months)
            if (kept.size != prefs.met.size) prefs.met = kept
        }
        prefs.store.registerOnSharedPreferenceChangeListener(prefsListener)
        lastReads = prefs.reads
        applyNfcMode()
        applyBrightness()
        // Back from Edit card, a settings page or the Cards screen: show what changed there.
        if (welcoming && shownTabs) {
            // Debug "Show the welcome screens" (About) reset the cards: start the welcome afresh, as
            // a new screen, so nothing from the last welcome (its step, what was typed) comes back.
            startActivity(Intent(this, MainActivity::class.java).putExtra("demo", demo))
            finish()
            return
        }
        render()
        // Only here: a redraw just before the welcome screens' recreate() would show it on the old screen.
        if (!welcoming && prefs.ready.isNotEmpty()) showReady()
    }

    override fun onPause() {
        resumed = false
        // Guest Wi-Fi is a one-off: leaving the screen goes back to the card's usual option.
        prefs.endWifi()
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
            screenBrightness = if (resumed && prefs.tab == Prefs.TAB_SHARE && prefs.fullBrightness) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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
                if (prefs.metAskNote) {
                    // "Ask for a note after each tap": a moment of Sent for both of you, then who was it?
                    main.postAtTime({
                        sent.visibility = View.GONE
                        // The screen may have been rotated or closed in the meantime.
                        if (!isFinishing && !isDestroyed) prefs.met.firstOrNull()?.let { editNote(it) }
                    }, SENT_TOKEN, SystemClock.uptimeMillis() + 1500)
                } else {
                    // Long enough to reach for "Add a note".
                    main.postAtTime({ sent.visibility = View.GONE }, SENT_TOKEN, SystemClock.uptimeMillis() + 8000)
                }
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
        // About 70% of the screen's width: big enough to scan, small enough for the rows under it.
        val qrCard = findViewById<View>(R.id.qr_card)
        qrCard.layoutParams.width = (minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.7).toInt()
        qrCard.setOnClickListener { enlargeQr() }
        qrCard.setOnTouchListener(Swipe(qrCard, { prefs.quickSwitch().size >= 2 }, ::stepQuickSwitch))

        // The profile card: swipe to the next card (who), as the code swipes what's shared; tap to choose.
        val profileCard = findViewById<View>(R.id.profile_card)
        profileCard.setOnClickListener { showCardSwitcher() }
        profileCard.setOnLongClickListener { editCard(); true }
        findViewById<View>(R.id.card_edit).setOnClickListener { editCard() }
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
        private var longPressed = false
        // The touch listener takes every event, so a long press (Edit card) is detected here.
        private val longPress = Runnable {
            if (!moved && !swiping) {
                longPressed = true
                card.performLongClick()
            }
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val dx = event.rawX - startX
            val dy = event.rawY - startY
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    swiping = false
                    moved = false
                    longPressed = false
                    card.postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                        moved = true
                        card.removeCallbacks(longPress)
                    }
                    // Sideways more than up or down: keep the touch from the scrolling page.
                    if (!swiping && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) && canSwipe()) {
                        swiping = true
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (swiping) card.translationX = dx * 0.4f
                }
                MotionEvent.ACTION_UP -> {
                    card.removeCallbacks(longPress)
                    if (swiping && kotlin.math.abs(dx) > card.width / 5f) onSwipe(if (dx < 0) 1 else -1)
                    else if (!moved && !longPressed) view.performClick()
                    settle()
                }
                MotionEvent.ACTION_CANCEL -> {
                    card.removeCallbacks(longPress)
                    settle()
                }
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
            R.string.state_ready -> dialog.setMessage(if (prefs.answerWhenClosed) R.string.state_ready_detail_closed else R.string.state_ready_detail)
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
            if (!copied) editCard()
            val message = getString(if (copied) R.string.cards_created_copy else R.string.cards_created_blank, card.label)
            Snackbar.make(nav, message, Snackbar.LENGTH_LONG).setAnchorView(nav).show()
        }
    }

    /** The code full screen on its light background, for scanning from further away. Tap to close. */
    private fun enlargeQr() {
        val code = prefs.qrText().takeIf { it.isNotEmpty() } ?: return
        val bitmap = qrBitmap(code) ?: return
        val opens = findViewById<TextView>(R.id.qr_opens).text
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(getColor(R.color.qr_light))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setOnClickListener { dialog.dismiss() }
            addView(ImageView(context).apply {
                setImageBitmap(bitmap)
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
            val photo = if (prefs.sendPhoto) Photo.smallJpeg(this, prefs.activeCardId) else null
            file.writeText(prefs.contactProfile.vcard(photoJpeg = photo))
            Intent(Intent.ACTION_SEND)
                .setType("text/x-vcard")
                .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, "$packageName.files", file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prefs.urlFor(preset.id, event = ""))
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_send_title)))
    }

    /** "Set up" in the picker: a quick dialog for a missing link or number; Wi-Fi and the contact card open their pages. */
    private fun setUpOption(preset: Presets.Preset) {
        val asked = LinkDialogs.setUpOption(this, prefs, preset) { prefs.share = preset.id }
        if (asked) return
        if (preset.id == Presets.WIFI) openPage(SettingsPageActivity.PAGE_WIFI) else editCard()
    }

    private fun setUpReceive() {
        findViewById<View>(R.id.clear_received).setOnClickListener { prefs.received = emptyList() }
        findViewById<View>(R.id.receive_nfc_settings).setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } }
    }

    private fun setUpMet() {
        findViewById<View>(R.id.met_add).setOnClickListener {
            editNote(Meeting(System.currentTimeMillis(), prefs.event, MetLog.MANUAL, "", prefs.activeCard.label), isNew = true)
        }
        findViewById<View>(R.id.met_export).setOnClickListener { export() }
    }

    /** Settings: the active card (opens Edit card) and short rows, each to its own page. */
    private fun setUpSettings() {
        findViewById<View>(R.id.settings_card).setOnClickListener { editCard() }
        fun row(id: Int, icon: Int, title: Int, open: () -> Unit) = findViewById<View>(id).apply {
            findViewById<ImageView>(R.id.setting_icon).setImageResource(icon)
            findViewById<TextView>(R.id.setting_title).setText(title)
            setOnClickListener { open() }
        }
        row(R.id.row_cards, R.drawable.ic_feature_card, R.string.row_cards) { showCardSwitcher() }
        row(R.id.row_sharing, R.drawable.ic_feature_tap, R.string.settings_sharing) { openPage(SettingsPageActivity.PAGE_SHARING) }
        row(R.id.row_wifi, R.drawable.ic_opt_wifi, R.string.settings_wifi) { openPage(SettingsPageActivity.PAGE_WIFI) }
        row(R.id.row_met, R.drawable.ic_nav_met, R.string.tab_met) { openPage(SettingsPageActivity.PAGE_MET) }
        row(R.id.row_backup, R.drawable.ic_backup, R.string.settings_backup) { openPage(SettingsPageActivity.PAGE_BACKUP) }
        row(R.id.row_write, R.drawable.ic_edit, R.string.write_open) {
            startActivity(Intent(this, WriteActivity::class.java).putExtra("demo", demo))
        }
        row(R.id.row_tile, R.drawable.ic_nav_settings, R.string.add_tile) { requestTile() }.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        row(R.id.row_theme, R.drawable.ic_theme, R.string.settings_theme) { chooseTheme() }
        row(R.id.row_language, R.drawable.ic_language, R.string.settings_language) { chooseLanguage() }
        row(R.id.row_about, R.drawable.ic_info, R.string.settings_about) { openPage(SettingsPageActivity.PAGE_ABOUT) }
    }

    private fun openPage(page: String) = startActivity(SettingsPageActivity.intent(this, page))

    /** Dark (Tilde's default), light, or following the phone's setting. Applies straight away. */
    private fun chooseTheme() {
        val themes = listOf(Prefs.THEME_DARK, Prefs.THEME_LIGHT, Prefs.THEME_SYSTEM)
        val names = arrayOf(getString(R.string.theme_dark), getString(R.string.theme_light), getString(R.string.theme_system))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(names, themes.indexOf(prefs.theme)) { dialog, which ->
                prefs.theme = themes[which]
                dialog.dismiss()
                TildeApp.applyTheme(themes[which])
            }
            .show()
    }

    /**
     * The interface language: the phone's, or English or Portuguese whatever the phone uses. AppCompat
     * keeps the choice (Android 13 and later also show it in the system's per-app language setting).
     */
    private fun chooseLanguage() {
        val tags = listOf("", "en", "pt-PT")
        val names = arrayOf(getString(R.string.language_system), getString(R.string.language_en), getString(R.string.language_pt))
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(names, tags.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
            }
            .show()
    }

    private fun languageName(): String = when (AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')) {
        "en" -> getString(R.string.language_en)
        "pt" -> getString(R.string.language_pt)
        else -> getString(R.string.language_system)
    }

    private fun themeName(theme: String) = getString(
        when (theme) {
            Prefs.THEME_LIGHT -> R.string.theme_light
            Prefs.THEME_SYSTEM -> R.string.theme_system
            else -> R.string.theme_dark
        },
    )

    /** Edit card: everything on the active card. */
    private fun editCard() = startActivity(Intent(this, CardEditActivity::class.java))

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
        shownTabs = true
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
            val suffix = SpannableStringBuilder(if (cards.size > 1) "  ·  ${active.label}  ▾" else "  ▾").apply {
                setSpan(ForegroundColorSpan(getColor(R.color.dim)), 0, length, 0)
            }
            fitBrand(this, handleLine(profile.handle), suffix)
            // Again once it's laid out, and whenever its width changes (rotation, text size).
            if (getTag(R.id.brand) == null) {
                setTag(R.id.brand, true)
                addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
                    if (right - left != oldRight - oldLeft) view.post { renderShare() }
                }
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

        // Too much for a QR code (very long details or links): say so rather than show nothing.
        val code = if (wifiMissing) null else prefs.qrText().takeIf { it.isNotEmpty() }?.let(::qrBitmap)
        val tooLong = !wifiMissing && prefs.qrText().isNotEmpty() && code == null
        findViewById<View>(R.id.qr_card).visibility = if (wifiMissing || tooLong) View.GONE else View.VISIBLE
        findViewById<View>(R.id.qr_too_long).visibility = if (tooLong) View.VISIBLE else View.GONE
        findViewById<View>(R.id.wifi_missing).visibility = if (wifiMissing) View.VISIBLE else View.GONE
        findViewById<ImageView>(R.id.qr).setImageBitmap(code)
        findViewById<TextView>(R.id.iphone_hint).apply {
            visibility = if (tapping && !preset.iphoneTap && !wifiMissing) View.VISIBLE else View.GONE
            val opens = PresetRows.iphoneOpens(this@MainActivity, prefs.contactProfile)
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
        // What's being shared always has a chip, starred or not (Guest Wi-Fi usually isn't).
        val quick = prefs.quickSwitch().let { starred ->
            if (starred.any { it.id == preset.id }) starred
            else (starred.map { it.id } + preset.id).toSet().let { ids -> prefs.available().filter { it.id in ids } }
        }
        val chips = findViewById<ChipGroup>(R.id.quick_switch)
        findViewById<View>(R.id.quick_switch_scroll).visibility = if (quick.size >= 2) View.VISIBLE else View.GONE
        chips.removeAllViews()
        for (option in quick) {
            val chip = layoutInflater.inflate(R.layout.chip_option, chips, false) as Chip
            chip.text = labelOf(option)
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
        val today = prefs.met.count { it.time >= startOfDay && it.shared != MetLog.RECEIVED && it.shared != MetLog.MANUAL }
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

    /**
     * The Share screen's top line in the room it has: a long handle is shortened first
     * ("~/maximilianaal… · Web Summit ▾"), then the label, so the ▾ always shows.
     */
    private fun fitBrand(view: TextView, handle: CharSequence, suffix: CharSequence) {
        val room = (view.width - view.paddingStart - view.paddingEnd).toFloat()
        if (room <= 0) {
            view.text = SpannableStringBuilder(handle).append(suffix)
            return
        }
        val paint = view.paint
        val suffixWidth = paint.measureText(suffix, 0, suffix.length)
        val minHandle = paint.measureText("~/…")
        view.text = if (room - suffixWidth >= minHandle) {
            SpannableStringBuilder(android.text.TextUtils.ellipsize(handle, paint, room - suffixWidth, android.text.TextUtils.TruncateAt.END)).append(suffix)
        } else {
            // Not even room for the label: keep "~/…" and the ▾, shorten the label between them.
            val caret = suffix.subSequence(suffix.length - 3, suffix.length)
            val label = suffix.subSequence(0, suffix.length - 3)
            val labelRoom = room - minHandle - paint.measureText(caret, 0, caret.length)
            SpannableStringBuilder(android.text.TextUtils.ellipsize(handle, paint, minHandle, android.text.TextUtils.TruncateAt.END))
                .append(android.text.TextUtils.ellipsize(label, paint, labelRoom.coerceAtLeast(0f), android.text.TextUtils.TruncateAt.END))
                .append(caret)
        }
    }

    /** The ~/handle line, with "tilde" standing in for an empty handle. */
    private fun handleLine(handle: String) = SpannableStringBuilder(getString(R.string.brand_prefix)).apply {
        setSpan(ForegroundColorSpan(getColor(R.color.accent)), 0, length, 0)
        append(handle.ifBlank { getString(R.string.brand_name) })
    }

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
            text = profile.titleLine
            visibility = if (profile.titleLine.isBlank()) View.GONE else View.VISIBLE
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

    /** The Settings list shows what each row is set to now, so most things need no tap to check. */
    private fun renderSettings() {
        val card = prefs.activeCard
        val profile = card.profile
        bindAvatar(findViewById(R.id.settings_avatar), profile)
        findViewById<TextView>(R.id.settings_card_label).text = card.label
        findViewById<View>(R.id.settings_card_dot).backgroundTintList =
            android.content.res.ColorStateList.valueOf(Cards.colour(card.colour).argb.toInt())
        findViewById<TextView>(R.id.settings_card_name).text = profile.name.ifBlank { getString(R.string.welcome_name) }
        findViewById<TextView>(R.id.settings_card_summary).text = listOfNotNull(
            profile.titleLine.takeIf { it.isNotBlank() },
            card.links.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.row_card_links, it, it) },
        ).joinToString(" · ")
        fun summary(id: Int, text: String) { findViewById<View>(id).findViewById<TextView>(R.id.setting_summary).text = text }
        val cards = prefs.cards
        summary(R.id.row_cards, resources.getQuantityString(R.plurals.row_cards_summary, cards.size, cards.size))
        summary(R.id.row_sharing, listOf(
            getString(if (prefs.enabled) R.string.row_sharing_on else R.string.row_sharing_off),
            prefs.event.takeIf { it.isNotBlank() }?.let { getString(R.string.row_event, it) } ?: getString(R.string.row_event_none),
        ).joinToString(" · "))
        summary(R.id.row_wifi, prefs.wifiSsid.takeIf { prefs.wifiReady } ?: getString(R.string.row_wifi_none))
        summary(R.id.row_met, listOfNotNull(
            prefs.metKeepMonths.takeIf { it > 0 }?.let { getString(R.string.row_met_keep, resources.getQuantityString(R.plurals.met_keep_months, it, it)) }
                ?: getString(R.string.row_met_keep_all),
            getString(R.string.row_met_note).takeIf { prefs.metAskNote },
        ).joinToString(" · "))
        summary(R.id.row_backup, getString(R.string.row_backup_summary))
        summary(R.id.row_write, getString(R.string.row_write_summary))
        summary(R.id.row_tile, getString(R.string.row_tile_summary))
        summary(R.id.row_theme, themeName(prefs.theme))
        summary(R.id.row_language, languageName())
        summary(R.id.row_about, getString(R.string.version, BuildConfig.VERSION_NAME))
    }

    private fun renderReceived() {
        // Receive needs NFC: without it, say what works instead; with it off, offer to turn it on.
        val nfc = adapter
        val (title, detail) = when {
            nfc == null && !demo -> R.string.receive_no_nfc to null
            nfc != null && !nfc.isEnabled && !demo -> R.string.receive_nfc_off to null
            else -> R.string.receive_title to R.string.receive_detail
        }
        findViewById<TextView>(R.id.receive_title).setText(title)
        findViewById<TextView>(R.id.receive_detail).apply {
            visibility = if (detail == null) View.GONE else View.VISIBLE
            detail?.let(::setText)
        }
        findViewById<View>(R.id.receive_nfc_settings).visibility = if (title == R.string.receive_nfc_off) View.VISIBLE else View.GONE
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
            val (title, detail) = receivedText(item)
            list.addView(listRow(title, detail, actions))
        }
    }

    /** A received item's title and detail in the app's language. Items saved before 1.2 already have English text. */
    private fun receivedText(item: Received): Pair<String, String> = when (item.kind) {
        Received.WIFI -> getString(R.string.received_wifi, item.title.removePrefix("Wi-Fi: ")) to
            if (item.payload.isEmpty()) getString(R.string.received_wifi_open) else getString(R.string.received_wifi_password, item.payload)
        Received.CONTACT -> item.title.takeIf { it.isNotEmpty() && it != "Contact card" }.orEmpty().ifEmpty { getString(R.string.preset_contact) } to item.detail
        Received.OTHER -> getString(R.string.received_unsupported) to item.detail
        else -> item.title to item.detail
    }

    /** Met's "shared" column: an option's name as saved, or "received their contact" / "added by hand" in the app's language. */
    private fun sharedName(shared: String) = when (shared) {
        MetLog.RECEIVED -> getString(R.string.met_received)
        MetLog.MANUAL -> getString(R.string.met_manual)
        else -> shared
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
            val detail = listOf(whenText, card, entry.event, sharedName(entry.shared)).filter { it.isNotEmpty() }.joinToString(" · ")
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
            filters = arrayOf(InputFilter.LengthFilter(MAX_NOTE))
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

    /**
     * Hands the log to email, Drive or a spreadsheet app as a CSV file (tilde-met-2026-10-07.csv),
     * written where Send writes the contact card, so Sheets and Excel open it as a spreadsheet.
     */
    private fun export() {
        val file = File(cacheDir, "shared").apply { mkdirs() }.resolve("tilde-met-${java.time.LocalDate.now()}.csv")
        file.writeText(MetLog.csv(prefs.met, shared = ::sharedName))
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.met_export_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    private fun qrBitmap(text: String): Bitmap? = QrCode.bitmap(this, text)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val DARK = Color.parseColor("#0b0d10")
        private val SENT_TOKEN = Any()
        /** From a widget for one card: open Tilde on that card. */
        const val EXTRA_CARD = "card"
        /** The longest Met note. */
        private const val MAX_NOTE = 500
        private const val STATE_STEP = "welcome.step"
        private const val STATE_COUNTRIES = "welcome.countries"
        private const val STATE_SECOND_PHONE = "welcome.secondPhone"
        private const val STATE_WITH_CARD = "welcome.withCard"
        private const val STATE_HANDLE_EDITED = "welcome.handleEdited"
    }
}
