package com.tbutman.nfcshare

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private var adapter: NfcAdapter? = null
    private val service by lazy { ComponentName(this, NdefHceService::class.java) }

    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private lateinit var urlField: EditText
    private lateinit var qr: ImageView
    private lateinit var reads: TextView
    private lateinit var nfcSettings: Button

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        adapter = NfcAdapter.getDefaultAdapter(this)

        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle)
        urlField = findViewById(R.id.url)
        qr = findViewById(R.id.qr)
        reads = findViewById(R.id.reads)
        nfcSettings = findViewById(R.id.nfc_settings)

        toggle.setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }
        urlField.setText(prefs.url)
        urlField.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) saveUrl()
            false
        }
        urlField.setOnFocusChangeListener { _, focused -> if (!focused) saveUrl() }
        nfcSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        findViewById<Button>(R.id.reset).setOnClickListener {
            urlField.setText(Prefs.DEFAULT_URL)
            saveUrl()
        }
    }

    override fun onResume() {
        super.onResume()
        // Card emulation only works with the screen on, so keep it on while the app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs.store.registerOnSharedPreferenceChangeListener(prefsListener)
        adapter?.let { nfc ->
            // Answer readers ahead of any other app registered for the same AID.
            CardEmulation.getInstance(nfc).setPreferredService(this, service)
            // Two phones back to back can both act as readers. Stop polling while the app is
            // open so this phone only listens, as a tag would.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching {
                    nfc.setDiscoveryTechnology(this, NfcAdapter.FLAG_READER_DISABLE, NfcAdapter.FLAG_LISTEN_KEEP)
                }
            }
        }
        render()
    }

    override fun onPause() {
        adapter?.let { nfc ->
            CardEmulation.getInstance(nfc).unsetPreferredService(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching { nfc.resetDiscoveryTechnology(this) }
            }
        }
        prefs.store.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun saveUrl() {
        val value = urlField.text.toString().trim()
        val valid = runCatching { Ndef.uriMessage(value) }.isSuccess && value.contains(':')
        if (valid) prefs.url = value else urlField.error = getString(R.string.url_invalid)
    }

    private fun render() {
        val nfc = adapter
        val canEmulate = packageManager.hasSystemFeature("android.hardware.nfc.hce")
        toggle.isChecked = prefs.enabled
        status.text = getString(
            when {
                nfc == null || !canEmulate -> R.string.status_no_nfc
                !nfc.isEnabled -> R.string.status_nfc_off
                !prefs.enabled -> R.string.status_paused
                else -> R.string.status_ready
            },
        )
        nfcSettings.visibility = if (nfc != null && !nfc.isEnabled) View.VISIBLE else View.GONE
        reads.text = resources.getQuantityString(R.plurals.reads, prefs.reads, prefs.reads)
        qr.setImageBitmap(qrBitmap(prefs.url))
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
    }
}
