package com.tbutman.nfcshare

import android.app.Activity
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
import android.text.Editable
import android.text.TextWatcher
import android.provider.ContactsContract
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private lateinit var prefs: Prefs
    private var adapter: NfcAdapter? = null
    private val service by lazy { ComponentName(this, NdefHceService::class.java) }
    private val main = Handler(Looper.getMainLooper())
    private var lastReads = 0
    private var resumed = false

    private lateinit var tabs: RadioGroup
    private lateinit var status: TextView
    private lateinit var sent: TextView
    private lateinit var nfcSettings: Button
    private lateinit var sharePanel: View
    private lateinit var receivePanel: View
    private lateinit var toggle: Switch
    private lateinit var presets: RadioGroup
    private lateinit var shareHint: TextView
    private lateinit var customRow: View
    private lateinit var urlField: EditText
    private lateinit var eventRow: View
    private lateinit var eventField: EditText
    private lateinit var wifiRows: View
    private lateinit var wifiSsid: EditText
    private lateinit var wifiPassword: EditText
    private lateinit var wifiOpen: CheckBox
    private lateinit var qr: ImageView
    private lateinit var qrHint: View
    private lateinit var reads: TextView
    private lateinit var addTile: Button
    private lateinit var receivedEmpty: View
    private lateinit var receivedList: LinearLayout

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

        tabs = findViewById(R.id.tabs)
        status = findViewById(R.id.status)
        sent = findViewById(R.id.sent)
        nfcSettings = findViewById(R.id.nfc_settings)
        sharePanel = findViewById(R.id.share_panel)
        receivePanel = findViewById(R.id.receive_panel)
        toggle = findViewById(R.id.toggle)
        presets = findViewById(R.id.presets)
        shareHint = findViewById(R.id.share_hint)
        customRow = findViewById(R.id.custom_row)
        urlField = findViewById(R.id.url)
        eventRow = findViewById(R.id.event_row)
        eventField = findViewById(R.id.event)
        wifiRows = findViewById(R.id.wifi_rows)
        wifiSsid = findViewById(R.id.wifi_ssid)
        wifiPassword = findViewById(R.id.wifi_password)
        wifiOpen = findViewById(R.id.wifi_open)
        qr = findViewById(R.id.qr)
        qrHint = findViewById(R.id.qr_hint)
        reads = findViewById(R.id.reads)
        addTile = findViewById(R.id.add_tile)
        receivedEmpty = findViewById(R.id.received_empty)
        receivedList = findViewById(R.id.received_list)

        for (preset in Presets.all) {
            presets.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = preset.id
                text = preset.label
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setTextColor(getColor(R.color.text))
            })
        }
        presets.setOnCheckedChangeListener { group, id ->
            group.findViewById<View>(id)?.tag?.let { prefs.share = it as String }
        }
        tabs.setOnCheckedChangeListener { _, id ->
            prefs.tab = if (id == R.id.tab_receive) Prefs.TAB_RECEIVE else Prefs.TAB_SHARE
            applyNfcMode()
        }
        toggle.setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }

        urlField.setText(prefs.customUrl)
        eventField.setText(prefs.event)
        wifiSsid.setText(prefs.wifiSsid)
        wifiPassword.setText(prefs.wifiPassword)
        wifiOpen.isChecked = prefs.wifiOpen
        // Fields save as you type: tapping a preset or scanning straight after typing must not lose
        // the text. The custom link only saves once valid; Done shows why it isn't.
        onChange(urlField) { if (validUrl(it)) prefs.customUrl = it }
        urlField.setOnEditorActionListener { _, _, _ ->
            if (!validUrl(urlField.text.toString().trim())) urlField.error = getString(R.string.url_invalid)
            false
        }
        onChange(eventField) { prefs.event = it }
        // Wi-Fi names and passwords can start or end with a space, so they stay exactly as typed.
        onChange(wifiSsid, trim = false) { prefs.wifiSsid = it }
        onChange(wifiPassword, trim = false) { prefs.wifiPassword = it }
        wifiOpen.setOnCheckedChangeListener { _, checked -> prefs.wifiOpen = checked }

        nfcSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        addTile.setOnClickListener { requestTile() }
        findViewById<Button>(R.id.clear_received).setOnClickListener { prefs.received = emptyList() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // Card emulation only works with the screen on, so keep it on while the app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs.store.registerOnSharedPreferenceChangeListener(prefsListener)
        lastReads = prefs.reads
        applyNfcMode()
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

    /** Share: answer readers as a tag and stop polling. Receive: poll for tags in reader mode. */
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
                buzz()
            }
        }
    }

    /** A reader just read the whole message from this phone. */
    private fun onRead() {
        val now = prefs.reads
        if (now > lastReads) {
            buzz()
            sent.visibility = View.VISIBLE
            main.removeCallbacksAndMessages(SENT_TOKEN)
            main.postAtTime({ sent.visibility = View.GONE }, SENT_TOKEN, SystemClock.uptimeMillis() + 2500)
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

    private fun requestTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, ShareTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { }
    }

    private fun onChange(field: EditText, trim: Boolean = true, save: (String) -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = save(s?.toString().orEmpty().let { if (trim) it.trim() else it })
        })
    }

    private fun validUrl(value: String) = value.contains(':') && runCatching { Ndef.uriMessage(value) }.isSuccess

    private fun render() {
        val nfc = adapter
        val canEmulate = packageManager.hasSystemFeature("android.hardware.nfc.hce")
        val receiving = prefs.tab == Prefs.TAB_RECEIVE
        val share = prefs.share

        val tab = if (receiving) R.id.tab_receive else R.id.tab_share
        if (tabs.checkedRadioButtonId != tab) tabs.check(tab)
        sharePanel.visibility = if (receiving) View.GONE else View.VISIBLE
        receivePanel.visibility = if (receiving) View.VISIBLE else View.GONE

        status.text = getString(
            when {
                nfc == null || !canEmulate -> R.string.status_no_nfc
                !nfc.isEnabled -> R.string.status_nfc_off
                receiving -> R.string.status_receive
                !prefs.enabled -> R.string.status_paused
                share == Presets.WIFI && !prefs.wifiReady -> R.string.status_wifi_incomplete
                else -> R.string.status_ready
            },
        )
        nfcSettings.visibility = if (nfc != null && !nfc.isEnabled) View.VISIBLE else View.GONE

        if (receiving) renderReceived() else renderShare(share)
    }

    private fun renderShare(share: String) {
        toggle.isChecked = prefs.enabled
        for (i in 0 until presets.childCount) {
            val button = presets.getChildAt(i) as RadioButton
            if (button.tag == share && !button.isChecked) button.isChecked = true
        }
        customRow.visibility = if (share == Presets.CUSTOM) View.VISIBLE else View.GONE
        wifiRows.visibility = if (share == Presets.WIFI) View.VISIBLE else View.GONE
        eventRow.visibility = if (share == Presets.WIFI) View.GONE else View.VISIBLE
        wifiPassword.isEnabled = !prefs.wifiOpen
        shareHint.text = when (share) {
            Presets.CONTACT -> resources.getQuantityString(R.plurals.hint_contact, Contact.phones.size, Contact.phones.size)
            Presets.WIFI -> getString(R.string.hint_wifi)
            else -> getString(R.string.hint_link, prefs.url)
        }
        val showQr = share != Presets.WIFI || prefs.wifiReady
        qr.visibility = if (showQr) View.VISIBLE else View.GONE
        qrHint.visibility = qr.visibility
        if (showQr) qr.setImageBitmap(qrBitmap(prefs.qrText()))
        reads.text = resources.getQuantityString(R.plurals.reads, prefs.reads, prefs.reads)
        addTile.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
    }

    private fun renderReceived() {
        val items = prefs.received
        receivedEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        receivedList.removeAllViews()
        val pad = (12 * resources.displayMetrics.density).toInt()
        for (item in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, pad, 0, pad)
            }
            row.addView(TextView(this).apply {
                text = item.title
                setTextColor(getColor(R.color.text))
                textSize = 17f
            })
            row.addView(TextView(this).apply {
                text = item.detail
                setTextColor(getColor(R.color.muted))
                textSize = 14f
            })
            val actions = LinearLayout(this)
            fun action(label: Int, run: () -> Unit) = actions.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = getString(label)
                isAllCaps = false
                setTextColor(getColor(R.color.accent))
                setOnClickListener { run() }
            })
            when (item.kind) {
                Received.LINK -> action(R.string.action_open) { open(item.payload) }
                Received.CONTACT -> action(R.string.action_save_contact) { saveContact(item.payload) }
            }
            if (item.payload.isNotEmpty()) action(R.string.action_copy) { copy(item.payload) }
            row.addView(actions)
            receivedList.addView(row)
        }
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

    companion object {
        private val DARK = Color.parseColor("#0b0d10")
        private val LIGHT = Color.parseColor("#f1efe8")
        private val SENT_TOKEN = Any()
    }
}
