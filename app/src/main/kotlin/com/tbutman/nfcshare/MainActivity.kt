package com.tbutman.nfcshare

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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
import java.util.Calendar

/**
 * One screen, four tabs. Share is what the other person looks at (name, QR code, what a tap
 * shares), so everything only Thomas needs lives in Settings, Met and the picker sheet.
 */
class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private lateinit var prefs: Prefs
    private var adapter: NfcAdapter? = null
    private val service by lazy { ComponentName(this, NdefHceService::class.java) }
    private val main = Handler(Looper.getMainLooper())
    private var lastReads = 0
    private var resumed = false

    private lateinit var nav: BottomNavigationView
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
        render()
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
                    prefs.met = MetLog.add(prefs.met, Meeting(System.currentTimeMillis(), prefs.event, MetLog.RECEIVED, card.title))
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

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1)
            },
        )
    }

    // ---- Set-up ----

    private fun setUpShare() {
        val brand = SpannableStringBuilder(getString(R.string.brand_prefix)).apply {
            setSpan(ForegroundColorSpan(getColor(R.color.accent)), 0, length, 0)
            append(getString(R.string.brand_name))
        }
        findViewById<TextView>(R.id.brand).text = brand
        findViewById<View>(R.id.share_row).setOnClickListener { showPicker() }
        findViewById<View>(R.id.open_settings).setOnClickListener { nav.selectedItemId = R.id.nav_settings }
        findViewById<View>(R.id.nfc_settings).setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        findViewById<View>(R.id.sent_done).setOnClickListener { sent.visibility = View.GONE }
        findViewById<View>(R.id.sent_note).setOnClickListener {
            sent.visibility = View.GONE
            prefs.met.firstOrNull()?.let { editNote(it) }
        }
    }

    private fun setUpReceive() {
        findViewById<View>(R.id.clear_received).setOnClickListener { prefs.received = emptyList() }
    }

    private fun setUpMet() {
        findViewById<View>(R.id.met_add).setOnClickListener {
            editNote(Meeting(System.currentTimeMillis(), prefs.event, getString(R.string.met_manual), ""), isNew = true)
        }
        findViewById<View>(R.id.met_export).setOnClickListener { export() }
    }

    private fun setUpSettings() {
        findViewById<MaterialSwitch>(R.id.toggle).apply {
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }
        }
        field(R.id.event, prefs.event) { prefs.event = it.trim() }
        field(R.id.whatsapp_greeting, prefs.whatsappGreeting) { prefs.whatsappGreeting = it }
        val urlLayout = findViewById<TextInputLayout>(R.id.url_layout)
        field(R.id.url, prefs.customUrl) {
            val value = it.trim()
            val valid = value.contains(':') && runCatching { Ndef.uriMessage(value) }.isSuccess
            urlLayout.error = if (valid || value.isEmpty()) null else getString(R.string.url_invalid)
            if (valid) prefs.customUrl = value
        }
        // Wi-Fi names and passwords can start or end with a space, so they stay exactly as typed.
        field(R.id.wifi_ssid, prefs.wifiSsid) { prefs.wifiSsid = it }
        field(R.id.wifi_password, prefs.wifiPassword) { prefs.wifiPassword = it }
        findViewById<MaterialCheckBox>(R.id.wifi_open).apply {
            isChecked = prefs.wifiOpen
            setOnCheckedChangeListener { _, checked -> prefs.wifiOpen = checked }
        }
        // Android's own Wi-Fi screen can show any saved network's password (Share), which apps can't read.
        findViewById<View>(R.id.wifi_settings).setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
        findViewById<View>(R.id.whatsapp_section).visibility = if (Contact.hasWhatsapp) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tile_section).visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        findViewById<View>(R.id.add_tile).setOnClickListener { requestTile() }
        findViewById<TextView>(R.id.version).text = getString(R.string.version, BuildConfig.VERSION_NAME)
    }

    /** Fields save as you type: switching tabs or scanning straight after typing must not lose text. */
    private fun field(id: Int, value: String, save: (String) -> Unit) {
        findViewById<TextInputEditText>(id).apply {
            setText(value)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = save(s?.toString().orEmpty())
            })
        }
    }

    // ---- Rendering ----

    private fun render() {
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
        val nfc = adapter
        val canEmulate = nfc != null && packageManager.hasSystemFeature("android.hardware.nfc.hce")
        val tapping = canEmulate && nfc!!.isEnabled && prefs.enabled
        val preset = Presets.find(prefs.share)
        val wifiMissing = preset.id == Presets.WIFI && !prefs.wifiReady

        findViewById<TextView>(R.id.state).apply {
            val (label, colour) = when {
                !canEmulate -> R.string.state_no_nfc to R.color.muted
                !nfc!!.isEnabled -> R.string.state_nfc_off to R.color.accent
                !prefs.enabled -> R.string.state_paused to R.color.muted
                else -> R.string.state_ready to R.color.ok
            }
            text = getString(label)
            setTextColor(getColor(colour))
        }
        findViewById<TextView>(R.id.tagline).setText(if (tapping) R.string.tagline_tap else R.string.tagline_scan)
        findViewById<View>(R.id.nfc_settings).visibility = if (nfc != null && !nfc.isEnabled) View.VISIBLE else View.GONE

        findViewById<View>(R.id.qr_card).visibility = if (wifiMissing) View.GONE else View.VISIBLE
        findViewById<View>(R.id.wifi_missing).visibility = if (wifiMissing) View.VISIBLE else View.GONE
        if (!wifiMissing) findViewById<ImageView>(R.id.qr).setImageBitmap(qrBitmap(prefs.qrText()))
        findViewById<View>(R.id.iphone_hint).visibility = if (tapping && !preset.iphoneTap && !wifiMissing) View.VISIBLE else View.GONE

        bindPreset(findViewById(R.id.share_row_content), preset, selected = false)

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

    private fun bindPreset(row: View, preset: Presets.Preset, selected: Boolean) {
        val logo = logoFor(preset.id)
        row.findViewById<ImageView>(R.id.logo).apply {
            visibility = if (logo != null) View.VISIBLE else View.GONE
            logo?.let { setImageResource(it) }
        }
        row.findViewById<TextView>(R.id.monogram).apply {
            visibility = if (logo == null) View.VISIBLE else View.GONE
            text = preset.monogram
        }
        row.findViewById<TextView>(R.id.title).apply {
            text = preset.label
            setTextColor(getColor(if (selected) R.color.accent else R.color.text))
        }
        row.findViewById<TextView>(R.id.subtitle).text = detail(preset)
        // Everything works by tap or scan everywhere, so only the exceptions get a note.
        row.findViewById<View>(R.id.note).visibility = if (preset.iphoneTap) View.GONE else View.VISIBLE
        row.findViewById<ImageView>(R.id.end).apply {
            setImageResource(if (selected) R.drawable.ic_check else R.drawable.ic_chevron)
            imageTintList = getColorStateList(if (selected) R.color.accent else R.color.muted)
        }
    }

    /** The services' own logos, simple icons for the generic options; tbutman.com keeps its ~/ mark. */
    private fun logoFor(id: String): Int? = when (id) {
        Presets.WHATSAPP -> R.drawable.ic_brand_whatsapp
        "linkedin" -> R.drawable.ic_brand_linkedin
        "github" -> R.drawable.ic_brand_github
        "instagram" -> R.drawable.ic_brand_instagram
        "x" -> R.drawable.ic_brand_x
        Presets.CONTACT -> R.drawable.ic_opt_contact
        Presets.CUSTOM -> R.drawable.ic_opt_link
        Presets.WIFI -> R.drawable.ic_opt_wifi
        else -> null
    }

    /** What an option opens, in a few words. Never a phone number or the Wi-Fi password. */
    private fun detail(preset: Presets.Preset): String = when (preset.id) {
        Presets.CONTACT -> getString(R.string.detail_contact)
        Presets.WHATSAPP -> getString(R.string.detail_whatsapp)
        Presets.WIFI -> prefs.wifiSsid.ifBlank { getString(R.string.detail_wifi_missing) }
        Presets.CUSTOM -> bare(prefs.customUrl)
        else -> bare(Presets.withEvent(preset.url ?: Prefs.DEFAULT_URL, prefs.event))
    }

    private fun bare(url: String) = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")

    /** The picker: what a tap shares, with a note under the options iPhones can only scan. */
    private fun showPicker() {
        val sheet = BottomSheetDialog(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        list.addView(TextView(this).apply {
            setText(R.string.picker_title)
            setTextColor(getColor(R.color.text))
            textSize = 18f
            setPadding(dp(20), dp(8), dp(20), dp(12))
        })
        for (preset in Presets.available) {
            val row = LayoutInflater.from(this).inflate(R.layout.row_preset, list, false)
            bindPreset(row, preset, selected = preset.id == prefs.share)
            row.setOnClickListener {
                prefs.share = preset.id
                sheet.dismiss()
                val needsSetup = preset.id == Presets.WIFI && !prefs.wifiReady
                if (needsSetup) {
                    Snackbar.make(nav, R.string.picker_needs_setup, Snackbar.LENGTH_LONG)
                        .setAnchorView(nav)
                        .setAction(R.string.open_settings) { nav.selectedItemId = R.id.nav_settings }
                        .show()
                }
            }
            list.addView(row)
        }
        sheet.setContentView(list)
        sheet.show()
    }

    private fun renderSettings() {
        findViewById<MaterialSwitch>(R.id.toggle).let { if (it.isChecked != prefs.enabled) it.isChecked = prefs.enabled }
        findViewById<TextInputLayout>(R.id.wifi_password_layout).isEnabled = !prefs.wifiOpen
        findViewById<TextView>(R.id.reads).text = resources.getQuantityString(R.plurals.reads, prefs.reads, prefs.reads)
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
            val detail = listOf(whenText, entry.event, entry.shared).filter { it.isNotEmpty() }.joinToString(" · ")
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
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tap to share", text))
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
    }
}
