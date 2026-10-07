package com.tbutman.tilde

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * The settings that apply to every card, one short page each: Sharing (taps, the event tag, the
 * Share screen's brightness, screen and vibration), Guest Wi-Fi, Met, Backup and restore, and
 * About (with Delete all data). Fields save as they're typed.
 */
class SettingsPageActivity : AppCompatActivity() {
    private val prefs by lazy { Prefs(this) }

    // Backup and restore go through Android's own file picker: no storage permission, and the
    // owner chooses where the file lives (Downloads, Drive, a USB stick…).
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        val json = Backups.toJson(prefs.backup(findViewById<MaterialCheckBox>(R.id.backup_wifi_password).isChecked))
        val saved = runCatching { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null }.getOrDefault(false)
        Toast.makeText(this, if (saved) R.string.backup_saved else R.string.backup_failed, Toast.LENGTH_SHORT).show()
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val text = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        val backup = try {
            Backups.fromJson(text ?: throw Backups.Invalid(getString(R.string.backup_unreadable)))
        } catch (e: Backups.Invalid) {
            MaterialAlertDialogBuilder(this).setMessage(e.message).setPositiveButton(R.string.done, null).show()
            return@registerForActivityResult
        }
        val date = android.text.format.DateUtils.formatDateTime(this, backup.created, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_YEAR)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_confirm_title)
            .setMessage(getString(
                R.string.backup_confirm, date,
                resources.getQuantityString(R.plurals.backup_confirm_cards, backup.cards.size, backup.cards.size),
                resources.getQuantityString(R.plurals.backup_confirm_met, backup.met.size, backup.met.size),
            ))
            .setPositiveButton(R.string.backup_restore) { _, _ ->
                prefs.restore(backup)
                restartApp()
            }
            .setNegativeButton(R.string.met_cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(EXTRA_PAGE)) {
            PAGE_WIFI -> setUpWifi()
            PAGE_ABOUT -> setUpAbout()
            PAGE_MET -> setUpMet()
            PAGE_BACKUP -> setUpBackup()
            else -> setUpSharing()
        }
        findViewById<View>(R.id.page_done).setOnClickListener { finish() }
    }

    private fun setUpSharing() {
        setContentView(R.layout.page_sharing)
        findViewById<MaterialSwitch>(R.id.toggle).apply {
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }
        }
        findViewById<TextInputEditText>(R.id.event).saveAsYouType(prefs.event) { prefs.event = it.trim() }
        fun toggle(id: Int, value: Boolean, save: (Boolean) -> Unit) = findViewById<android.widget.CompoundButton>(id).apply {
            isChecked = value
            setOnCheckedChangeListener { _, checked -> save(checked) }
        }
        toggle(R.id.event_auto_clear, prefs.eventAutoClear) { prefs.eventAutoClear = it }
        toggle(R.id.full_brightness, prefs.fullBrightness) { prefs.fullBrightness = it }
        toggle(R.id.keep_screen_on, prefs.keepScreenOn) { prefs.keepScreenOn = it }
        toggle(R.id.vibrate, prefs.vibrate) { prefs.vibrate = it }
        toggle(R.id.send_photo, prefs.sendPhoto) { prefs.sendPhoto = it }
        toggle(R.id.answer_when_closed, prefs.answerWhenClosed) { prefs.answerWhenClosed = it }
    }

    private fun setUpMet() {
        setContentView(R.layout.page_met)
        findViewById<MaterialSwitch>(R.id.met_ask_note).apply {
            isChecked = prefs.metAskNote
            setOnCheckedChangeListener { _, checked -> prefs.metAskNote = checked }
        }
        val group = findViewById<RadioGroup>(R.id.met_keep)
        for (months in MET_KEEP_CHOICES) {
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = if (months == 0) getString(R.string.met_keep_never) else resources.getQuantityString(R.plurals.met_keep_months, months, months)
                setTextColor(getColor(R.color.text))
                isChecked = prefs.metKeepMonths == months
                setOnCheckedChangeListener { _, checked -> if (checked) prefs.metKeepMonths = months }
            })
        }
    }

    private fun setUpBackup() {
        setContentView(R.layout.page_backup)
        findViewById<View>(R.id.backup_export).setOnClickListener { saveBackup.launch(Backups.fileName(java.time.LocalDate.now())) }
        findViewById<View>(R.id.backup_import).setOnClickListener { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
    }

    /** After restoring or deleting everything: start Tilde afresh, so every screen shows the new data. */
    private fun restartApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun setUpWifi() {
        setContentView(R.layout.page_wifi)
        // Network names and passwords can start or end with a space, so they stay exactly as typed.
        findViewById<TextInputEditText>(R.id.wifi_ssid).saveAsYouType(prefs.wifiSsid) { prefs.wifiSsid = it }
        findViewById<TextInputEditText>(R.id.wifi_password).saveAsYouType(prefs.wifiPassword) { prefs.wifiPassword = it }
        val passwordLayout = findViewById<TextInputLayout>(R.id.wifi_password_layout)
        findViewById<MaterialCheckBox>(R.id.wifi_open).apply {
            isChecked = prefs.wifiOpen
            passwordLayout.isEnabled = !isChecked
            setOnCheckedChangeListener { _, checked ->
                prefs.wifiOpen = checked
                passwordLayout.isEnabled = !checked
            }
        }
        // Android's own Wi-Fi screen can show any saved network's password (Share), which apps can't read.
        findViewById<View>(R.id.wifi_settings).setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
    }

    private fun setUpAbout() {
        setContentView(R.layout.page_about)
        findViewById<TextView>(R.id.version).text = getString(R.string.version, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.reads).text = resources.getQuantityString(R.plurals.reads, prefs.reads, prefs.reads)
        findViewById<View>(R.id.about_source).setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_source_url)))) }
        }
        findViewById<View>(R.id.delete_all).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_delete_confirm_title)
                .setMessage(R.string.about_delete_confirm)
                .setPositiveButton(R.string.about_delete) { _, _ ->
                    prefs.deleteEverything()
                    restartApp()
                }
                .setNegativeButton(R.string.met_cancel, null)
                .show()
        }
        // Debug builds only: back to one empty card (photos deleted), and the welcome screens again.
        findViewById<View>(R.id.debug_welcome).apply {
            visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            setOnClickListener {
                prefs.resetCards()
                prefs.photoVersion += 1
                prefs.welcomed = false
                prefs.tab = Prefs.TAB_SHARE
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_PAGE = "page"
        const val PAGE_SHARING = "sharing"
        const val PAGE_WIFI = "wifi"
        const val PAGE_ABOUT = "about"
        const val PAGE_MET = "met"
        const val PAGE_BACKUP = "backup"

        /** "Delete entries older than": never, or this many months. */
        val MET_KEEP_CHOICES = listOf(0, 3, 6, 12)

        fun intent(context: Context, page: String): Intent = Intent(context, SettingsPageActivity::class.java).putExtra(EXTRA_PAGE, page)
    }
}
