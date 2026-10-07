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
 * The settings that apply to every card, one short page each: Sharing (taps, the event name, the
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
        uri?.let { RestoreDialogs.restore(this, prefs, it) }
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
        // Every page is one scrolling column; Sharing (the event name) and Guest Wi-Fi have fields.
        (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as? android.widget.ScrollView)?.keepFocusAboveKeyboard()
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
        val buttons = MET_KEEP_CHOICES.associateWith { months ->
            RadioButton(this).apply {
                id = View.generateViewId()
                text = if (months == 0) getString(R.string.met_keep_never) else resources.getQuantityString(R.plurals.met_keep_months, months, months)
                setTextColor(getColor(R.color.text))
                minHeight = (48 * resources.displayMetrics.density).toInt()
                isChecked = prefs.metKeepMonths == months
            }.also(group::addView)
        }
        for ((months, button) in buttons) {
            button.setOnClickListener {
                val before = prefs.metKeepMonths
                if (months == before) return@setOnClickListener
                // A shorter period deletes people straight away: say how many, and ask first.
                val doomed = prefs.met.size - MetLog.keepMonths(prefs.met, System.currentTimeMillis(), months).size
                if (doomed == 0) {
                    prefs.metKeepMonths = months
                    return@setOnClickListener
                }
                val period = resources.getQuantityString(R.plurals.met_keep_months, months, months)
                MaterialAlertDialogBuilder(this)
                    .setTitle(resources.getQuantityString(R.plurals.met_delete_now_title, doomed, doomed))
                    .setMessage(resources.getQuantityString(R.plurals.met_delete_now_body, doomed, period))
                    .setPositiveButton(R.string.met_delete) { _, _ ->
                        prefs.metKeepMonths = months
                        prefs.met = MetLog.keepMonths(prefs.met, System.currentTimeMillis(), months)
                    }
                    .setNegativeButton(R.string.met_cancel, null)
                    .setOnDismissListener { buttons.getValue(prefs.metKeepMonths).isChecked = true }
                    .show()
            }
        }
    }

    private fun setUpBackup() {
        setContentView(R.layout.page_backup)
        findViewById<View>(R.id.backup_export).setOnClickListener { saveBackup.launch(Backups.fileName(java.time.LocalDate.now())) }
        findViewById<View>(R.id.backup_import).setOnClickListener { openBackup.launch(RestoreDialogs.TYPES) }
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
        // Links open in the browser; Tilde itself still never goes online. The site's pages are in the app's language.
        for ((button, url) in listOf(
            R.id.about_website to R.string.about_website_url, R.id.about_privacy to R.string.about_privacy_url,
            R.id.about_source to R.string.about_source_url, R.id.about_issues to R.string.about_issues_url,
        )) {
            findViewById<View>(button).setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(url)))) } }
        }
        findViewById<View>(R.id.about_licences).setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.about_licences).setMessage(R.string.about_licences_detail).setPositiveButton(R.string.done, null).show()
        }
        findViewById<View>(R.id.delete_all).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_delete_confirm_title)
                .setMessage(R.string.about_delete_confirm)
                .setPositiveButton(R.string.about_delete) { _, _ ->
                    prefs.deleteEverything()
                    TildeApp.applyTheme(prefs.theme)
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
                    RestoreDialogs.restartApp(this)
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
