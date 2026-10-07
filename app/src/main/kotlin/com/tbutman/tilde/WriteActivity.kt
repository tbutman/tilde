package com.tbutman.tilde

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Write a sticker: puts the active card's link (or contact card, or guest Wi-Fi) on an NFC sticker or a printed
 * card, so it works without this phone. Stays ready after each write, for a stack of cards.
 */
class WriteActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private val prefs by lazy { Prefs(this) }
    private val rows by lazy { PresetRows(this, prefs, writing = true) }
    private var adapter: NfcAdapter? = null
    /** Debug builds only: draw the screen as with NFC switched on, for emulator screenshots. */
    private val demo by lazy { BuildConfig.DEBUG && intent.getBooleanExtra("demo", false) }
    /** Opened straight after the welcome screens: the person may not have a card yet, so they can skip. */
    private val fromWelcome by lazy { intent.getBooleanExtra(EXTRA_WELCOME, false) }
    private var choice = ""
    private var written = 0
    /** "Lock it after writing": permanent, so it's confirmed when ticked and never on by default. */
    private var lock = false
    /** The last outcome, shown until the next tag or until the choice changes. */
    private var result: TagWriter.Result? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write)
        adapter = NfcAdapter.getDefaultAdapter(this)
        val available = prefs.available()
        choice = prefs.share.takeIf { id -> available.any { it.id == id } } ?: available.firstOrNull()?.id ?: Presets.CONTACT
        findViewById<View>(R.id.write_choice).setOnClickListener {
            rows.showPicker(R.string.write_picker_title, choice) { preset ->
                choice = preset.id
                result = null
                render()
            }
        }
        findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.write_lock).setOnCheckedChangeListener { box, checked ->
            if (!checked) { lock = false; return@setOnCheckedChangeListener }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.write_lock_confirm_title)
                .setMessage(R.string.write_lock_confirm)
                .setPositiveButton(R.string.write_lock_yes) { _, _ -> lock = true }
                .setNegativeButton(R.string.met_cancel) { _, _ -> box.isChecked = false }
                .setOnCancelListener { box.isChecked = false }
                .show()
        }
        findViewById<View>(R.id.write_nfc_settings).setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        findViewById<View>(R.id.write_close).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        adapter?.takeIf { it.isEnabled }?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
        render()
    }

    override fun onPause() {
        adapter?.disableReaderMode(this)
        super.onPause()
    }

    /** What would be written, or null when the choice has nothing to write yet. */
    private fun message(): ByteArray? = when {
        choice == Presets.WIFI && !prefs.wifiReady -> null
        choice != Presets.WIFI && choice != Presets.CONTACT && prefs.urlFor(choice, event = "").isEmpty() -> null
        else -> prefs.messageFor(choice, event = "")
    }

    override fun onTagDiscovered(tag: Tag) {
        val message = message() ?: return
        val outcome = TagWriter.write(tag, message, lock = lock)
        runOnUiThread {
            result = outcome
            if (TagWriter.wrote(outcome)) {
                written += 1
                Haptics.buzz(this)
            }
            render()
        }
    }

    private fun render() {
        findViewById<TextView>(R.id.write_from).apply {
            text = getString(R.string.write_from, prefs.activeCard.label)
            visibility = if (prefs.cards.size > 1) View.VISIBLE else View.GONE
        }
        rows.bind(findViewById(R.id.write_choice_content), prefs.find(choice), selected = false)
        val nfc = adapter
        val nfcOn = demo || nfc?.isEnabled == true
        val (icon, title, detail) = when {
            nfc == null && !demo -> Triple(R.drawable.ic_tile, getString(R.string.write_no_nfc), getString(R.string.write_no_nfc_detail))
            !nfcOn -> Triple(R.drawable.ic_tile, getString(R.string.write_nfc_off), getString(R.string.write_nfc_off_detail))
            message() == null -> Triple(R.drawable.ic_tile, getString(R.string.write_nothing), getString(
                if (choice == Presets.WIFI) R.string.write_nothing_wifi else R.string.write_nothing_link,
            ))
            else -> when (val r = result) {
                TagWriter.Result.Written -> Triple(
                    R.drawable.ic_check, getString(R.string.write_done),
                    resources.getQuantityString(R.plurals.write_done_detail, written, written),
                )
                TagWriter.Result.WrittenLocked -> Triple(R.drawable.ic_check, getString(R.string.write_done_locked), getString(R.string.write_done_locked_detail))
                TagWriter.Result.WrittenNotLockable -> Triple(R.drawable.ic_check, getString(R.string.write_done), getString(R.string.write_not_lockable))
                TagWriter.Result.Locked -> Triple(R.drawable.ic_tile, getString(R.string.write_locked), getString(R.string.write_locked_detail))
                is TagWriter.Result.TooSmall -> Triple(
                    R.drawable.ic_tile, getString(R.string.write_too_small),
                    getString(R.string.write_too_small_detail, r.capacity, r.needed),
                )
                TagWriter.Result.Unsupported -> Triple(R.drawable.ic_tile, getString(R.string.write_unsupported), getString(R.string.write_unsupported_detail))
                TagWriter.Result.Failed -> Triple(R.drawable.ic_tile, getString(R.string.write_failed), getString(R.string.write_failed_detail))
                null -> Triple(R.drawable.ic_tile, getString(R.string.write_ready), getString(R.string.write_ready_detail))
            }
        }
        val good = TagWriter.wrote(result) && nfcOn && message() != null
        findViewById<ImageView>(R.id.write_icon).apply {
            setImageResource(icon)
            imageTintList = getColorStateList(if (good) R.color.ok else R.color.accent)
        }
        findViewById<TextView>(R.id.write_status).text = title
        findViewById<TextView>(R.id.write_detail).text = detail
        findViewById<View>(R.id.write_nfc_settings).visibility = if (nfc != null && !nfcOn) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.write_close).setText(if (fromWelcome && written == 0) R.string.write_skip else R.string.write_close)
        findViewById<View>(R.id.write_later).visibility = if (fromWelcome && written == 0) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_WELCOME = "welcome"
    }
}
