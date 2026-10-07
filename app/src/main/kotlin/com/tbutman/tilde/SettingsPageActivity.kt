package com.tbutman.tilde

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * The settings that apply to every card, one short page each: Sharing (taps on or off, the event
 * tag), Guest Wi-Fi, and About. Fields save as they're typed.
 */
class SettingsPageActivity : AppCompatActivity() {
    private val prefs by lazy { Prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(EXTRA_PAGE)) {
            PAGE_WIFI -> setUpWifi()
            PAGE_ABOUT -> setUpAbout()
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

        fun intent(context: Context, page: String): Intent = Intent(context, SettingsPageActivity::class.java).putExtra(EXTRA_PAGE, page)
    }
}
